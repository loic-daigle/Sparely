package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Purchases that come back on a long rhythm. Two kinds:
 *
 * - [Kind.SAME_PURCHASE]: the same thing again (the same store or name, or the same category at
 *   about the same amount): a computer every two years, winter tires every fall.
 * - [Kind.BIG_PURCHASE]: one of the very biggest purchases, whatever it is: a 3D printer, then a
 *   laptop, then a CNC, each about two years apart in the fall. Needs three occurrences, since any
 *   two big purchases are always some time apart.
 *
 * Occurrences are nine months or more apart at a steady gap. When they keep to a season of the
 * year (up to four months wide, e.g. August to November), the rhythm is in whole years and the
 * next one is expected in that season. Purchases a couple of months apart are one occurrence.
 * When the latest occurrence is this cycle's, the habit says so and expects the next a full gap
 * later. Expenses a recurring payment logged, and ignored ones, don't count. It only reads the
 * history: nothing it finds changes a balance, a budget or a forecast.
 */
object LongTermHabits {

    enum class Kind { SAME_PURCHASE, BIG_PURCHASE }

    data class Habit(
        val kind: Kind,
        /** What it is: the purchase's name, or null when it's a different thing each time. */
        val label: String?,
        /** What each occurrence was, oldest first ("Imprimante 3D", "Laptop", "CNC"). */
        val items: List<String>,
        val category: ExpenseCategory,
        /** About how much one occurrence costs, and the range seen. */
        val averageAmount: Double,
        val minAmount: Double,
        val maxAmount: Double,
        /** Months between two occurrences, e.g. 24. */
        val intervalMonths: Int,
        /** The season it keeps to (months 1–12, first to last, may wrap past December), if any. */
        val seasonStart: Int?,
        val seasonEnd: Int?,
        /** When the latest occurrence was, what and how much. */
        val lastDate: LocalDate,
        val lastItem: String,
        val lastAmount: Double,
        /** The month the next one is expected from (the start of its season when it has one). */
        val nextExpected: YearMonth,
        /** The last month it's still expected in. */
        val nextExpectedEnd: YearMonth,
        val occurrences: Int,
        /** The latest occurrence is this cycle's: it's already been made, the next is a gap away. */
        val doneThisCycle: Boolean
    ) {
        /** Months from [today]'s month to the next one (0 = this month, negative = overdue). */
        fun monthsUntil(today: LocalDate): Long = ChronoUnit.MONTHS.between(YearMonth.from(today), nextExpected)

        /** Not made yet, and its time is from a month away to a couple of months past its season. */
        fun isDueSoon(today: LocalDate): Boolean {
            if (doneThisCycle) return false
            val now = YearMonth.from(today)
            return !now.isBefore(nextExpected.minusMonths(1)) && !now.isAfter(nextExpectedEnd.plusMonths(2))
        }
    }

    private const val NOTABLE_MIN = 150.0
    private const val NOTABLE_FACTOR = 3.0
    private const val BIG_MIN = 1000.0
    private const val BIG_FACTOR = 10.0
    private const val AMOUNT_TOLERANCE = 0.5
    private const val MIN_GAP_MONTHS = 9
    /** Purchases this close belong to one occurrence. */
    private const val SAME_OCCURRENCE_MONTHS = 2
    /** A season is at most this many months past its first (four months wide). */
    private const val SEASON_SPAN = 3

    fun detect(expenses: List<Expense>, today: LocalDate = LocalDate.now()): List<Habit> {
        val pool = expenses.filter { !it.isIgnored && !it.countsAsRecurring && it.recurringExpenseId == null && !it.date.isAfter(today) }
        if (pool.size < 2) return emptyList()
        val median = pool.map { it.amount }.sorted().let { it[it.size / 2] }

        // The same purchase coming back
        val notable = pool.filter { it.amount >= maxOf(NOTABLE_MIN, median * NOTABLE_FACTOR) }.sortedBy { it.date }
        val clusters = mutableListOf<MutableList<Expense>>()
        for (expense in notable) {
            val home = clusters.firstOrNull { cluster -> cluster.any { sameThing(it, expense) } }
            if (home != null) home += expense else clusters += mutableListOf(expense)
        }
        val sameWithExpenses = clusters.mapNotNull { cluster ->
            // Tied by a name or a store that repeats, two times make a habit; tied only by category
            // and amount, it takes three
            val strong = cluster.groupingBy { it.description.trim().lowercase() }.eachCount().values.any { it > 1 } ||
                cluster.mapNotNull { it.storeId }.groupingBy { it }.eachCount().values.any { it > 1 }
            habitOf(cluster, Kind.SAME_PURCHASE, minOccurrences = if (strong) 2 else 3, today = today, named = strong)
                ?.let { it to cluster }
        }
        val same = sameWithExpenses.map { it.first }

        // The biggest purchases, whatever they are, on a rhythm of their own. The ones already in a
        // same-purchase habit are left out, so nothing is counted twice
        val inSame = sameWithExpenses.flatMap { it.second }.map { it.id }.toSet()
        val biggest = pool.filter { it.amount >= maxOf(BIG_MIN, median * BIG_FACTOR) && it.id !in inSame }
        val big = habitOf(biggest, Kind.BIG_PURCHASE, minOccurrences = 3, today = today, named = false)

        return (same + listOfNotNull(big)).sortedBy { it.nextExpected }
    }

    /** The same purchase again: the same store or name, or the same category at about the same amount. */
    internal fun sameThing(a: Expense, b: Expense): Boolean {
        val closeAmount = abs(a.amount - b.amount) <= maxOf(a.amount, b.amount) * AMOUNT_TOLERANCE
        val sameStore = a.storeId != null && a.storeId == b.storeId
        val sameName = DetectedRecurringMatch.namesMatch(a.description, b.description)
        // Same store or name: the same purchase whatever it cost this time (both are big ones already)
        return sameStore || sameName || (a.category == b.category && closeAmount)
    }

    private fun habitOf(expenses: List<Expense>, kind: Kind, minOccurrences: Int, today: LocalDate, named: Boolean): Habit? {
        // Occurrences: purchases a couple of months apart are one
        val occurrences = mutableListOf<MutableList<Expense>>()
        for (expense in expenses.sortedBy { it.date }) {
            val last = occurrences.lastOrNull()
            val gap = last?.let { ChronoUnit.MONTHS.between(YearMonth.from(it.first().date), YearMonth.from(expense.date)) }
            if (gap != null && gap <= SAME_OCCURRENCE_MONTHS) last += expense else occurrences += mutableListOf(expense)
        }
        if (occurrences.size < minOccurrences) return null
        val starts = occurrences.map { YearMonth.from(it.first().date) }
        val gaps = starts.zipWithNext { a, b -> ChronoUnit.MONTHS.between(a, b).toInt() }
        if (gaps.any { it < MIN_GAP_MONTHS }) return null
        val meanGap = gaps.average()
        // A steady rhythm: every gap close to the average one
        if (gaps.any { abs(it - meanGap) > maxOf(3.0, meanGap * 0.3) }) return null

        val season = seasonOf(starts.map { it.monthValue })
        // In a season, the rhythm is in whole years
        val interval = if (season != null) (meanGap / 12).roundToInt().coerceAtLeast(1) * 12 else meanGap.roundToInt()

        val thisMonth = YearMonth.from(today)
        val lastStart = starts.last()
        val next: YearMonth
        val nextEnd: YearMonth
        if (season != null) {
            val (first, last) = season
            // The season of the year one rhythm after the last occurrence's
            val anchor = lastStart.plusMonths(interval.toLong())
            var start = YearMonth.of(anchor.year, first)
            if (ChronoUnit.MONTHS.between(start, anchor) > 6) start = start.plusYears(1)
            if (ChronoUnit.MONTHS.between(anchor, start) > 6) start = start.minusYears(1)
            next = start
            nextEnd = start.plusMonths(((last - first + 12) % 12).toLong())
        } else {
            next = lastStart.plusMonths(interval.toLong())
            nextEnd = next
        }
        // Long overdue: the habit has stopped
        if (nextEnd.isBefore(thisMonth.minusMonths(6))) return null
        // Made within the last half gap: this cycle's is done, even if it came early or late
        val doneThisCycle = ChronoUnit.MONTHS.between(lastStart, thisMonth) < interval / 2.0

        val perOccurrence = occurrences.map { list -> list.sumOf { it.amount } }
        // What each occurrence was: its biggest purchase
        val items = occurrences.map { list -> list.maxBy { it.amount }.description.trim() }
        // A name only when it's the same purchase each time; otherwise it's a different thing each time
        val label = if (named) expenses.groupingBy { it.description.trim() }.eachCount().maxByOrNull { it.value }?.key else null
        val category = expenses.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key ?: expenses.last().category
        val lastOccurrence = occurrences.last()
        return Habit(
            kind = kind,
            label = label,
            items = items,
            category = category,
            averageAmount = perOccurrence.average(),
            minAmount = perOccurrence.min(),
            maxAmount = perOccurrence.max(),
            intervalMonths = interval,
            seasonStart = season?.first,
            seasonEnd = season?.second,
            lastDate = lastOccurrence.maxOf { it.date },
            lastItem = lastOccurrence.maxBy { it.amount }.description.trim(),
            lastAmount = perOccurrence.last(),
            nextExpected = next,
            nextExpectedEnd = nextEnd,
            occurrences = occurrences.size,
            doneThisCycle = doneThisCycle
        )
    }

    /**
     * The shortest stretch of the year (first and last month) that holds every month in [months],
     * if it's at most [SEASON_SPAN] months past its first. It may wrap past December.
     */
    internal fun seasonOf(months: List<Int>): Pair<Int, Int>? {
        val distinct = months.distinct().sorted()
        if (distinct.isEmpty()) return null
        // The biggest gap between consecutive months (around the year) is where the season isn't
        var best: Pair<Int, Int>? = null
        var bestSpan = Int.MAX_VALUE
        for (i in distinct.indices) {
            val first = distinct[i]
            val last = distinct[(i - 1 + distinct.size) % distinct.size]
            val span = (last - first + 12) % 12
            if (span < bestSpan) {
                bestSpan = span
                best = first to last
            }
        }
        return best?.takeIf { bestSpan <= SEASON_SPAN }
    }
}
