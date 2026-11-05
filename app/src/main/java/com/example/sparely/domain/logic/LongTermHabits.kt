package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Big purchases that come back on a long rhythm: a computer every two years in November, tires
 * every fall. The repeating-payment detection only looks at the last six months and needs three
 * occurrences, so these never show there; this looks at the whole history.
 *
 * A habit is two or more big expenses in the same category, of about the same amount, nine months
 * or more apart at a steady gap (or in the same month of the year), with the next one expected one
 * gap after the last. Expenses a recurring payment logged, and ignored ones, don't count.
 */
object LongTermHabits {

    data class Habit(
        /** The most common description among its expenses. */
        val label: String,
        val category: ExpenseCategory,
        val averageAmount: Double,
        /** Months between two occurrences, e.g. 24. */
        val intervalMonths: Int,
        /** The month of the year it falls in (1–12), when every occurrence was in about the same one. */
        val month: Int?,
        val lastDate: LocalDate,
        val nextExpected: YearMonth,
        val occurrences: Int
    ) {
        /** Months from [today]'s month to the next one (0 = this month, negative = overdue). */
        fun monthsUntil(today: LocalDate): Long = ChronoUnit.MONTHS.between(YearMonth.from(today), nextExpected)
    }

    private const val MIN_AMOUNT = 300.0
    private const val SIZE_FACTOR = 5.0
    private const val AMOUNT_TOLERANCE = 0.4
    private const val MIN_GAP_MONTHS = 9

    fun detect(expenses: List<Expense>, today: LocalDate = LocalDate.now()): List<Habit> {
        val pool = expenses.filter { !it.isIgnored && !it.countsAsRecurring && it.recurringExpenseId == null && !it.date.isAfter(today) }
        if (pool.size < 2) return emptyList()
        val median = pool.map { it.amount }.sorted().let { it[it.size / 2] }
        val threshold = maxOf(MIN_AMOUNT, median * SIZE_FACTOR)
        val big = pool.filter { it.amount >= threshold }.sortedBy { it.date }

        // Same category, about the same amount
        val clusters = mutableListOf<MutableList<Expense>>()
        for (expense in big) {
            val home = clusters.firstOrNull { cluster ->
                val mean = cluster.map { it.amount }.average()
                cluster.first().category == expense.category && abs(expense.amount - mean) <= mean * AMOUNT_TOLERANCE
            }
            if (home != null) home += expense else clusters += mutableListOf(expense)
        }

        return clusters.mapNotNull { cluster -> habitOf(cluster, today) }
            .sortedBy { it.nextExpected }
    }

    private fun habitOf(cluster: List<Expense>, today: LocalDate): Habit? {
        // Several big buys in the same month are one occurrence
        val byMonth = cluster.groupBy { YearMonth.from(it.date) }.toSortedMap()
        if (byMonth.size < 2) return null
        val months = byMonth.keys.toList()
        val gaps = months.zipWithNext { a, b -> ChronoUnit.MONTHS.between(a, b).toInt() }
        if (gaps.any { it < MIN_GAP_MONTHS }) return null
        val meanGap = gaps.average()
        // A steady rhythm: every gap close to the average one
        if (gaps.any { abs(it - meanGap) > maxOf(3.0, meanGap * 0.3) }) return null

        val calendarMonths = months.map { it.monthValue }
        val usualMonth = calendarMonths.sorted()[calendarMonths.size / 2]
        val sameMonth = calendarMonths.all { monthDistance(it, usualMonth) <= 1 }
        // A rhythm in whole years when it keeps to a month of the year
        val interval = if (sameMonth) (meanGap / 12).roundToInt().coerceAtLeast(1) * 12 else meanGap.roundToInt()

        var next = months.last().plusMonths(interval.toLong())
        if (sameMonth) next = next.withMonth(usualMonth)
        // Long overdue: the habit has stopped
        if (next.isBefore(YearMonth.from(today).minusMonths(6))) return null

        val label = cluster.groupingBy { it.description.trim() }.eachCount().maxByOrNull { it.value }?.key ?: cluster.last().description
        val perOccurrence = byMonth.values.map { list -> list.sumOf { it.amount } }
        return Habit(
            label = label,
            category = cluster.first().category,
            averageAmount = perOccurrence.average(),
            intervalMonths = interval,
            month = if (sameMonth) usualMonth else null,
            lastDate = cluster.maxOf { it.date },
            nextExpected = next,
            occurrences = byMonth.size
        )
    }

    private fun monthDistance(a: Int, b: Int): Int {
        val d = abs(a - b)
        return minOf(d, 12 - d)
    }
}
