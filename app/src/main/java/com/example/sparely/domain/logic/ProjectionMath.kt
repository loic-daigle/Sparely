package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.SuggestionConfidence
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Shared math for "how much will this total by month-end" projections, used by every engine that
 * projects spending (SpendingPatternEngine, BudgetEngine, CashflowEngine).
 */
object ProjectionMath {

    private const val MAX_HISTORY_MONTHS = 12
    private const val MIN_MONTHS_FOR_HISTORY_MODEL = 2
    private const val MAX_BACKTEST_MONTHS = 6

    /**
     * Linearly extrapolates an amount accumulated so far this month to a full-month total,
     * assuming spending continues at today's average daily pace.
     */
    fun linearMonthEndProjection(amountSoFar: Double, dayOfMonth: Int, daysInMonth: Int): Double {
        val safeDayOfMonth = dayOfMonth.coerceAtLeast(1)
        val dailyRate = amountSoFar / safeDayOfMonth
        return dailyRate * daysInMonth
    }

    /**
     * How much to trust a day-of-month linear projection: it's noisy early in the month
     * (a single transaction can dominate) and settles down as more of the month has elapsed.
     */
    fun confidenceForDayOfMonth(dayOfMonth: Int): SuggestionConfidence = when {
        dayOfMonth >= 20 -> SuggestionConfidence.HIGH
        dayOfMonth >= 10 -> SuggestionConfidence.MEDIUM
        else -> SuggestionConfidence.LOW
    }

    /** Spending that repeats on its own (rent, subscriptions) vs. spending the user chooses day to day. */
    data class HistoryBaseline(
        /** Recency-weighted discretionary spend per day, from completed months. */
        val discretionaryDailyRate: Double,
        /** Spread (std dev) of the per-day discretionary rate across months. */
        val discretionaryDailyStdDev: Double,
        /** Recency-weighted total of recurring-logged spend in a typical month. */
        val recurringMonthlyTotal: Double,
        val monthsOfHistory: Int
    ) {
        val hasEnoughHistory: Boolean get() = monthsOfHistory >= MIN_MONTHS_FOR_HISTORY_MODEL
    }

    data class MonthEndProjection(
        val expected: Double,
        val low: Double,
        val high: Double,
        val confidence: SuggestionConfidence,
        val usedHistory: Boolean
    )

    /**
     * Builds a baseline from every completed month (up to [MAX_HISTORY_MONTHS]) since the first
     * recorded expense. Newer months weigh more; months are clamped to twice the median so one
     * huge purchase can't skew the baseline.
     */
    fun historyBaseline(expenses: List<Expense>, today: LocalDate): HistoryBaseline {
        val currentMonth = YearMonth.from(today)
        val usable = expenses.filter { !it.isIgnored && YearMonth.from(it.date) < currentMonth }
        val firstMonth = usable.minOfOrNull { YearMonth.from(it.date) }
            ?: return HistoryBaseline(0.0, 0.0, 0.0, 0)

        val earliest = maxOf(firstMonth, currentMonth.minusMonths(MAX_HISTORY_MONTHS.toLong()))
        val months = generateSequence(earliest) { it.plusMonths(1) }
            .takeWhile { it < currentMonth }
            .toList()
        if (months.isEmpty()) return HistoryBaseline(0.0, 0.0, 0.0, 0)

        val byMonth = usable.groupBy { YearMonth.from(it.date) }
        val discretionaryDaily = months.map { m ->
            val total = byMonth[m].orEmpty().filter { !it.countsAsRecurring }.sumOf { it.amount }
            total / m.lengthOfMonth()
        }
        val recurringTotals = months.map { m ->
            byMonth[m].orEmpty().filter { it.countsAsRecurring }.sumOf { it.amount }
        }

        val cap = median(discretionaryDaily) * 2
        val clamped = if (cap > 0) discretionaryDaily.map { min(it, cap) } else discretionaryDaily
        val weights = months.indices.map { (it + 1).toDouble() }

        val meanDaily = weightedMean(clamped, weights)
        val stdDaily = sqrt(weightedMean(clamped.map { (it - meanDaily) * (it - meanDaily) }, weights))

        return HistoryBaseline(
            discretionaryDailyRate = meanDaily,
            discretionaryDailyStdDev = stdDaily,
            recurringMonthlyTotal = weightedMean(recurringTotals, weights),
            monthsOfHistory = months.size
        )
    }

    /**
     * Month-end projection that is only allowed to use the long-history model if that model
     * would have beaten plain linear extrapolation on this user's own past months (see
     * [historyBeatsLinear]). Otherwise it returns the linear estimate, so history can never
     * make the projection worse than the old method did on the user's own data.
     */
    fun monthEndProjection(expenses: List<Expense>, today: LocalDate): MonthEndProjection {
        val history = historyProjection(expenses, today)
        return if (history != null && historyBeatsLinear(expenses, today)) {
            history
        } else {
            linearProjection(expenses, today)
        }
    }

    /**
     * Walk-forward backtest: for each of the last [MAX_BACKTEST_MONTHS] completed months (that have
     * at least [MIN_MONTHS_FOR_HISTORY_MODEL] months of data before them), project that month
     * as of the same day-of-month as [today] using only data known then, and compare to the real
     * total. True only when the history model's total absolute error is no worse than linear's.
     * With no month to test on, it returns false (no proof it helps, so it isn't used).
     */
    fun historyBeatsLinear(expenses: List<Expense>, today: LocalDate): Boolean {
        val usable = expenses.filter { !it.isIgnored }
        val firstMonth = usable.minOfOrNull { YearMonth.from(it.date) } ?: return false
        val currentMonth = YearMonth.from(today)
        val testMonths = (1..MAX_BACKTEST_MONTHS)
            .map { currentMonth.minusMonths(it.toLong()) }
            .filter { it >= firstMonth.plusMonths(MIN_MONTHS_FOR_HISTORY_MODEL.toLong()) }
        if (testMonths.isEmpty()) return false

        var historyError = 0.0
        var linearError = 0.0
        for (month in testMonths) {
            val asOf = month.atDay(min(today.dayOfMonth, month.lengthOfMonth()))
            val known = usable.filter { !it.date.isAfter(asOf) }
            val actual = usable.filter { YearMonth.from(it.date) == month }.sumOf { it.amount }
            val history = historyProjection(known, asOf) ?: return false
            historyError += kotlin.math.abs(history.expected - actual)
            linearError += kotlin.math.abs(linearProjection(known, asOf).expected - actual)
        }
        return historyError <= linearError
    }

    private fun linearProjection(expenses: List<Expense>, today: LocalDate): MonthEndProjection {
        val currentMonth = YearMonth.from(today)
        val dayOfMonth = today.dayOfMonth.coerceAtLeast(1)
        val spentSoFar = expenses
            .filter { !it.isIgnored && YearMonth.from(it.date) == currentMonth }
            .sumOf { it.amount }
        val linear = linearMonthEndProjection(spentSoFar, dayOfMonth, currentMonth.lengthOfMonth())
        return MonthEndProjection(
            expected = linear,
            low = spentSoFar,
            high = linear * 1.25,
            confidence = confidenceForDayOfMonth(dayOfMonth),
            usedHistory = false
        )
    }

    /**
     * Month-end total = what's already spent + typical discretionary pace for the remaining days
     * + recurring bills still expected this month. Null with under two months of history.
     */
    private fun historyProjection(expenses: List<Expense>, today: LocalDate): MonthEndProjection? {
        val currentMonth = YearMonth.from(today)
        val daysInMonth = currentMonth.lengthOfMonth()
        val dayOfMonth = today.dayOfMonth.coerceAtLeast(1)
        val thisMonth = expenses.filter { !it.isIgnored && YearMonth.from(it.date) == currentMonth }
        val spentSoFar = thisMonth.sumOf { it.amount }
        val baseline = historyBaseline(expenses, today)
        if (!baseline.hasEnoughHistory) return null

        val remainingDays = daysInMonth - dayOfMonth
        val recurringSoFar = thisMonth.filter { it.countsAsRecurring }.sumOf { it.amount }
        val recurringRemaining = max(0.0, baseline.recurringMonthlyTotal - recurringSoFar)
        val discretionaryRemaining = baseline.discretionaryDailyRate * remainingDays
        val expected = spentSoFar + recurringRemaining + discretionaryRemaining
        val spread = baseline.discretionaryDailyStdDev * remainingDays

        val confidence = when {
            baseline.monthsOfHistory >= 6 -> SuggestionConfidence.HIGH
            baseline.monthsOfHistory >= 3 -> SuggestionConfidence.MEDIUM
            else -> SuggestionConfidence.LOW
        }
        return MonthEndProjection(
            expected = expected,
            low = max(spentSoFar, expected - spread),
            high = expected + spread,
            confidence = confidence,
            usedHistory = true
        )
    }

    private fun weightedMean(values: List<Double>, weights: List<Double>): Double {
        val totalWeight = weights.sum()
        if (totalWeight <= 0) return 0.0
        return values.zip(weights) { v, w -> v * w }.sum() / totalWeight
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }
}
