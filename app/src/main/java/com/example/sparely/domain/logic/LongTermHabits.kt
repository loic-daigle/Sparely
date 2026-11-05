package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Purchases that come back on a long rhythm, whatever they are: a computer every two years in
 * November, tires every fall, the car registration each spring. The repeating-payment detection
 * looks at the last six months and needs three occurrences, so these never show there; this looks
 * at the whole history and finds as many as there are.
 *
 * A habit is two or more occurrences, nine months or more apart at a steady gap (or in the same
 * month of the year), of the same thing: the same store or name, or the same category at about the
 * same amount. Purchases a couple of months apart count as one occurrence (a computer, then its
 * screen). When the latest occurrence is this cycle's, the habit says so and expects the next one
 * a full gap later. It only reads the history: nothing it finds changes a balance, a budget or a
 * forecast.
 */
object LongTermHabits {

    data class Habit(
        /** The most common description among its expenses. */
        val label: String,
        val category: ExpenseCategory,
        /** About how much one occurrence costs. */
        val averageAmount: Double,
        /** Months between two occurrences, e.g. 24. */
        val intervalMonths: Int,
        /** The month of the year it falls in (1–12), when every occurrence was in about the same one. */
        val month: Int?,
        /** When the latest occurrence was. */
        val lastDate: LocalDate,
        /** The latest occurrence's amount. */
        val lastAmount: Double,
        val nextExpected: YearMonth,
        val occurrences: Int,
        /** The latest occurrence is this cycle's: it's already been made, the next is a gap away. */
        val doneThisCycle: Boolean
    ) {
        /** Months from [today]'s month to the next one (0 = this month, negative = overdue). */
        fun monthsUntil(today: LocalDate): Long = ChronoUnit.MONTHS.between(YearMonth.from(today), nextExpected)

        /** Coming up this month or next (or a month or two late), and not made yet. */
        fun isDueSoon(today: LocalDate): Boolean = !doneThisCycle && monthsUntil(today) in -2..1
    }

    private const val MIN_AMOUNT = 150.0
    private const val SIZE_FACTOR = 3.0
    private const val AMOUNT_TOLERANCE = 0.5
    private const val MIN_GAP_MONTHS = 9
    /** Purchases this close belong to one occurrence. */
    private const val SAME_OCCURRENCE_MONTHS = 2

    fun detect(expenses: List<Expense>, today: LocalDate = LocalDate.now()): List<Habit> {
        val pool = expenses.filter { !it.isIgnored && !it.countsAsRecurring && it.recurringExpenseId == null && !it.date.isAfter(today) }
        if (pool.size < 2) return emptyList()
        val median = pool.map { it.amount }.sorted().let { it[it.size / 2] }
        val threshold = maxOf(MIN_AMOUNT, median * SIZE_FACTOR)
        val notable = pool.filter { it.amount >= threshold }.sortedBy { it.date }

        val clusters = mutableListOf<MutableList<Expense>>()
        for (expense in notable) {
            val home = clusters.firstOrNull { cluster -> cluster.any { sameThing(it, expense) } }
            if (home != null) home += expense else clusters += mutableListOf(expense)
        }
        return clusters.mapNotNull { habitOf(it, today) }.sortedBy { it.nextExpected }
    }

    /** The same purchase again: the same store or name, or the same category at about the same amount. */
    internal fun sameThing(a: Expense, b: Expense): Boolean {
        val closeAmount = abs(a.amount - b.amount) <= maxOf(a.amount, b.amount) * AMOUNT_TOLERANCE
        val sameStore = a.storeId != null && a.storeId == b.storeId
        val sameName = DetectedRecurringMatch.namesMatch(a.description, b.description)
        // Same store or name: the same purchase whatever it cost this time (both are big ones already)
        return sameStore || sameName || (a.category == b.category && closeAmount)
    }

    private fun habitOf(cluster: List<Expense>, today: LocalDate): Habit? {
        // Occurrences: purchases a couple of months apart are one
        val occurrences = mutableListOf<MutableList<Expense>>()
        for (expense in cluster.sortedBy { it.date }) {
            val last = occurrences.lastOrNull()
            val gap = last?.let { ChronoUnit.MONTHS.between(YearMonth.from(it.first().date), YearMonth.from(expense.date)) }
            if (gap != null && gap <= SAME_OCCURRENCE_MONTHS) last += expense else occurrences += mutableListOf(expense)
        }
        if (occurrences.size < 2) return null
        val starts = occurrences.map { YearMonth.from(it.first().date) }
        val gaps = starts.zipWithNext { a, b -> ChronoUnit.MONTHS.between(a, b).toInt() }
        if (gaps.any { it < MIN_GAP_MONTHS }) return null
        val meanGap = gaps.average()
        // A steady rhythm: every gap close to the average one
        if (gaps.any { abs(it - meanGap) > maxOf(3.0, meanGap * 0.3) }) return null

        val calendarMonths = starts.map { it.monthValue }
        val usualMonth = calendarMonths.sorted()[calendarMonths.size / 2]
        val sameMonth = calendarMonths.all { monthDistance(it, usualMonth) <= 1 }
        // A rhythm in whole years when it keeps to a month of the year
        val interval = if (sameMonth) (meanGap / 12).roundToInt().coerceAtLeast(1) * 12 else meanGap.roundToInt()

        val thisMonth = YearMonth.from(today)
        val lastStart = starts.last()
        var next = lastStart.plusMonths(interval.toLong())
        if (sameMonth) next = next.withMonth(usualMonth)
        // Long overdue: the habit has stopped
        if (next.isBefore(thisMonth.minusMonths(6))) return null
        // Made within the last half gap: this cycle's is done, even if it came early or late
        val doneThisCycle = ChronoUnit.MONTHS.between(lastStart, thisMonth) < interval / 2.0

        val label = cluster.groupingBy { it.description.trim() }.eachCount().maxByOrNull { it.value }?.key ?: cluster.last().description
        val category = cluster.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key ?: cluster.last().category
        val perOccurrence = occurrences.map { list -> list.sumOf { it.amount } }
        return Habit(
            label = label,
            category = category,
            averageAmount = perOccurrence.average(),
            intervalMonths = interval,
            month = if (sameMonth) usualMonth else null,
            lastDate = occurrences.last().maxOf { it.date },
            lastAmount = perOccurrence.last(),
            nextExpected = next,
            occurrences = occurrences.size,
            doneThisCycle = doneThisCycle
        )
    }

    private fun monthDistance(a: Int, b: Int): Int {
        val d = abs(a - b)
        return minOf(d, 12 - d)
    }
}
