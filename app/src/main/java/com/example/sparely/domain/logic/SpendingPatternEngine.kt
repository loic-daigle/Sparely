package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.SuggestionConfidence
import com.example.sparely.ui.utils.roundToTwoDecimals
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln

/**
 * Engine for analyzing spending patterns with trend detection, anomaly identification,
 * and predictive insights.
 */
object SpendingPatternEngine {

    // === Data Classes ===

    enum class SpendingTrend { INCREASING, STABLE, DECREASING }

    data class SpendingPatternResult(
        val weeklyAverageExpense: Double,
        val monthlyAverageExpense: Double,
        val trend: SpendingTrend,
        val trendPercentage: Double, // Month-over-month change
        val topGrowingCategory: ExpenseCategory?,
        val topGrowingCategoryChange: Double,
        val highSpendingDays: List<DayOfWeek>,
        val anomalies: List<SpendingAnomaly>,
        val categoryVelocity: Map<ExpenseCategory, CategoryVelocity>,
        val predictedMonthEndSpending: Double,
        val predictedMonthEndConfidence: SuggestionConfidence, // How much of the month has elapsed
        val runwayDays: Int // Days until predicted balance hits zero at current rate
    )

    data class SpendingAnomaly(
        val expense: Expense,
        /** Robust z-score within the expense's category (median/MAD based, so outliers can't hide each other). */
        val zScore: Double,
        /** Median amount for this category; the UI phrases the anomaly as a multiple of it. */
        val typicalAmount: Double
    ) {
        val timesTypical: Double get() = if (typicalAmount > 0) expense.amount / typicalAmount else 0.0
    }

    data class CategoryVelocity(
        val category: ExpenseCategory,
        val dailyRate: Double, // Average daily spending in this category
        val projectedMonthlyTotal: Double,
        val budgetUtilizationRate: Double?, // If budget exists, how fast it's being consumed
        val daysUntilBudgetExhausted: Int?
    )

    data class DayOfWeekSpending(
        val dayOfWeek: DayOfWeek,
        val averageSpending: Double,
        val totalSpending: Double,
        val transactionCount: Int
    )

    // === Main Analysis Function ===

    /**
     * [expenses] should already have one-off outliers filtered out (it feeds averages and
     * projections). [anomalySource] is the unfiltered list, since the outliers are exactly
     * what anomaly detection is looking for.
     */
    fun analyze(
        expenses: List<Expense>,
        mainAccountBalance: Double = 0.0,
        categoryBudgets: Map<ExpenseCategory, Double> = emptyMap(),
        today: LocalDate = LocalDate.now(),
        anomalySource: List<Expense> = expenses
    ): SpendingPatternResult {
        if (expenses.isEmpty()) {
            return SpendingPatternResult(
                weeklyAverageExpense = 0.0,
                monthlyAverageExpense = 0.0,
                trend = SpendingTrend.STABLE,
                trendPercentage = 0.0,
                topGrowingCategory = null,
                topGrowingCategoryChange = 0.0,
                highSpendingDays = emptyList(),
                anomalies = emptyList(),
                categoryVelocity = emptyMap(),
                predictedMonthEndSpending = 0.0,
                predictedMonthEndConfidence = SuggestionConfidence.LOW,
                runwayDays = Int.MAX_VALUE
            )
        }

        val weeklyAvg = computeWeeklyAverage(expenses, today).roundToTwoDecimals()
        val monthlyAvg = computeMonthlyAverage(expenses, today).roundToTwoDecimals()
        val (trend, trendPct) = computeTrend(expenses, today)
        val (topCategory, topCategoryChange) = findTopGrowingCategory(expenses, today)
        val highSpendDays = findHighSpendingDays(expenses)
        val anomalies = detectAnomalies(anomalySource.filter { !it.isIgnored }, today)
        val velocity = computeCategoryVelocity(expenses, categoryBudgets, today)
        val monthEndProjection = ProjectionMath.monthEndProjection(expenses, today)
        val predictedMonthEnd = monthEndProjection.expected.roundToTwoDecimals()
        val predictedMonthEndConfidence = monthEndProjection.confidence
        val runway = computeRunwayDays(expenses, mainAccountBalance, today)

        return SpendingPatternResult(
            weeklyAverageExpense = weeklyAvg,
            monthlyAverageExpense = monthlyAvg,
            trend = trend,
            trendPercentage = trendPct,
            topGrowingCategory = topCategory,
            topGrowingCategoryChange = topCategoryChange,
            highSpendingDays = highSpendDays,
            anomalies = anomalies,
            categoryVelocity = velocity,
            predictedMonthEndSpending = predictedMonthEnd,
            predictedMonthEndConfidence = predictedMonthEndConfidence,
            runwayDays = runway
        )
    }

    // === Helper Functions ===

    /**
     * Last 28 days spread over 4 weeks, so quiet weeks count as low weeks instead of being skipped.
     * A newer user's total is spread over the weeks they've actually tracked.
     */
    private fun computeWeeklyAverage(expenses: List<Expense>, today: LocalDate): Double =
        ProjectionMath.recentDailyRate(expenses, today, windowDays = 28) * 7

    /** Average over completed months since tracking began, quiet months included; the current partial month is left out. */
    private fun computeMonthlyAverage(expenses: List<Expense>, today: LocalDate): Double {
        val currentMonth = YearMonth.from(today)
        val firstMonth = expenses.minOfOrNull { YearMonth.from(it.date) } ?: return 0.0
        if (firstMonth >= currentMonth) {
            // Nothing completed yet: the month-to-date pace is the best there is.
            return ProjectionMath.monthEndProjection(expenses, today).expected
        }
        val byMonth = expenses.groupBy { YearMonth.from(it.date) }
        val totals = generateSequence(firstMonth) { it.plusMonths(1) }
            .takeWhile { it < currentMonth }
            .map { m -> byMonth[m].orEmpty().sumOf { it.amount } }
            .toList()
        return totals.average()
    }

    /**
     * Day-to-day spending this month (projected to month-end) against the median of up to the last
     * 3 completed months. Bills are left out: a quarterly bill landing this month isn't a trend.
     * Early in the month with no history model to lean on, the projection is too noisy to call a trend.
     */
    private fun computeTrend(expenses: List<Expense>, today: LocalDate): Pair<SpendingTrend, Double> {
        val discretionary = expenses.filter { !it.countsAsRecurring }
        val currentMonth = YearMonth.from(today)
        val firstMonth = discretionary.minOfOrNull { YearMonth.from(it.date) }
            ?: return SpendingTrend.STABLE to 0.0

        val previousTotals = (1L..3L)
            .map { currentMonth.minusMonths(it) }
            .filter { it >= firstMonth }
            .map { m -> discretionary.filter { YearMonth.from(it.date) == m }.sumOf { it.amount } }
        val comparison = median(previousTotals)

        val projection = ProjectionMath.monthEndProjection(discretionary, today)
        val trustworthy = projection.usedHistory || projection.confidence != SuggestionConfidence.LOW
        val percentChange = if (comparison > 0 && trustworthy) {
            ((projection.expected - comparison) / comparison) * 100
        } else {
            0.0
        }

        val trend = when {
            percentChange > 10 -> SpendingTrend.INCREASING
            percentChange < -10 -> SpendingTrend.DECREASING
            else -> SpendingTrend.STABLE
        }

        return Pair(trend, percentChange)
    }

    private fun findTopGrowingCategory(
        expenses: List<Expense>,
        today: LocalDate
    ): Pair<ExpenseCategory?, Double> {
        val currentMonth = YearMonth.from(today)
        val lastMonth = currentMonth.minusMonths(1)

        val currentByCategory = expenses
            .filter { YearMonth.from(it.date) == currentMonth }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val lastByCategory = expenses
            .filter { YearMonth.from(it.date) == lastMonth }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        var topCategory: ExpenseCategory? = null
        var topChangePercent = 0.0
        var topCategoryAmount = 0.0

        for (category in ExpenseCategory.entries) {
            val current = if ((currentByCategory[category] ?: 0.0) > 0) {
                ProjectionMath.monthEndProjection(expenses.filter { it.category == category }, today).expected
            } else {
                0.0
            }
            val last = lastByCategory[category] ?: 0.0

            // changePercent and current live in different unit spaces (percent vs dollars) -
            // never compare one against the other directly.
            val changePercent = when {
                last > 0 -> ((current - last) / last) * 100
                current > 0 -> Double.MAX_VALUE // brand-new category this month = unbounded growth
                else -> continue
            }

            val isNewTopCategory = changePercent > topChangePercent ||
                (changePercent == topChangePercent && current > topCategoryAmount)
            if (isNewTopCategory) {
                topChangePercent = changePercent
                topCategoryAmount = current
                topCategory = category
            }
        }

        // Report unbounded (brand-new category) growth as a large finite percentage so callers
        // don't need to special-case Double.MAX_VALUE.
        val reportedChange = if (topChangePercent == Double.MAX_VALUE) 100.0 else topChangePercent
        return Pair(topCategory, reportedChange)
    }

    private fun findHighSpendingDays(expenses: List<Expense>): List<DayOfWeek> {
        val byDayOfWeek = expenses.groupBy { it.date.dayOfWeek }
            .mapValues { (_, list) -> list.sumOf { it.amount } / list.size.coerceAtLeast(1) }

        if (byDayOfWeek.isEmpty()) return emptyList()

        val avgAllDays = byDayOfWeek.values.average()
        val threshold = avgAllDays * 1.25 // 25% above average

        return byDayOfWeek
            .filter { it.value > threshold }
            .keys
            .sortedByDescending { byDayOfWeek[it] }
    }

    /**
     * Flags recent expenses that are unusually high *for their own category*. Rent can't make every
     * grocery run look normal, and one huge purchase can't inflate the spread enough to hide the next.
     *
     * Spending is skewed (many small purchases, a few big ones), so amounts are compared on a log
     * scale using the median and median absolute deviation (MAD): "6x the usual" means the same
     * thing at any price level. Three guards keep it quiet, because a false alarm is worse than a miss:
     *  - the spread has a floor, so a category of near-identical purchases doesn't make small bumps look extreme;
     *  - the amount must be at least [ANOMALY_MIN_MULTIPLE] times the category's usual amount;
     *  - the amount must be large for this user overall: above their top purchases and a real share of
     *    a month's everyday spending, so a normal meal out never shows up next to real surprises.
     */
    private fun detectAnomalies(expenses: List<Expense>, today: LocalDate): List<SpendingAnomaly> {
        val reportCutoff = today.minusDays(ANOMALY_REPORT_DAYS)
        val baselineCutoff = today.minusDays(ANOMALY_BASELINE_DAYS)

        val baseline = expenses.filter { !it.date.isBefore(baselineCutoff) && !it.date.isAfter(today) && it.amount > 0 }
        if (baseline.size < ANOMALY_MIN_SAMPLES) return emptyList()
        // Bills are large but expected; counting them would push the bar above every real surprise.
        val everyday = baseline.filter { !it.countsAsRecurring }.ifEmpty { return emptyList() }
        val trackedMonths = (ChronoUnit.DAYS.between(everyday.minOf { it.date }, today) + 1) / 30.4
        val monthlySpend = everyday.sumOf { it.amount } / trackedMonths.coerceAtLeast(1.0)
        val materialAmount = maxOf(
            percentile(everyday.map { it.amount }, ANOMALY_MATERIAL_PERCENTILE) * 1.5,
            monthlySpend * ANOMALY_MATERIAL_MONTH_SHARE
        )

        return baseline
            .groupBy { it.category }
            .values
            .filter { it.size >= ANOMALY_MIN_SAMPLES }
            .flatMap { categoryExpenses ->
                val logs = categoryExpenses.map { ln(it.amount) }
                val typicalLog = median(logs)
                val typical = exp(typicalLog)
                // 1.4826 * MAD estimates the std dev for normal data.
                val spread = (1.4826 * median(logs.map { abs(it - typicalLog) })).coerceAtLeast(ANOMALY_MIN_LOG_SPREAD)

                categoryExpenses
                    .filter { !it.date.isBefore(reportCutoff) }
                    .mapNotNull { expense ->
                        val z = (ln(expense.amount) - typicalLog) / spread
                        val isAnomaly = z > ANOMALY_Z_THRESHOLD &&
                            expense.amount >= typical * ANOMALY_MIN_MULTIPLE &&
                            expense.amount >= materialAmount
                        if (isAnomaly) SpendingAnomaly(expense, z, typical) else null
                    }
            }
            .sortedByDescending { it.zScore }
            .take(5)
    }

    private const val ANOMALY_REPORT_DAYS = 60L
    private const val ANOMALY_BASELINE_DAYS = 365L
    private const val ANOMALY_MIN_SAMPLES = 8
    /** Iglewicz-Hoaglin cut-off for robust z-scores. */
    private const val ANOMALY_Z_THRESHOLD = 3.5
    /** Log-scale spread floor: with it, a purchase must be roughly 5.7x the usual amount (e^(3.5 * 0.5)) to qualify. */
    private const val ANOMALY_MIN_LOG_SPREAD = 0.5
    private const val ANOMALY_MIN_MULTIPLE = 3.0
    private const val ANOMALY_MATERIAL_PERCENTILE = 0.95
    /** A single purchase must also be at least this share of a typical month of everyday spending. */
    private const val ANOMALY_MATERIAL_MONTH_SHARE = 0.35

    /** Nearest-rank percentile. */
    private fun percentile(values: List<Double>, p: Double): Double {
        val sorted = values.sorted()
        val rank = ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    private fun computeCategoryVelocity(
        expenses: List<Expense>,
        budgets: Map<ExpenseCategory, Double>,
        today: LocalDate
    ): Map<ExpenseCategory, CategoryVelocity> {
        val currentMonth = YearMonth.from(today)
        val dayOfMonth = today.dayOfMonth.coerceAtLeast(1)
        val daysInMonth = currentMonth.lengthOfMonth()

        val thisMonthExpenses = expenses.filter { YearMonth.from(it.date) == currentMonth }
        val byCategory = thisMonthExpenses.groupBy { it.category }

        return byCategory.mapValues { (category, categoryExpenses) ->
            val totalSpent = categoryExpenses.sumOf { it.amount }.roundToTwoDecimals()
            val dailyRate = (totalSpent / dayOfMonth).roundToTwoDecimals()
            val projection = ProjectionMath.monthEndProjection(expenses.filter { it.category == category }, today)
            val projectedTotal = projection.expected.roundToTwoDecimals()

            val budget = budgets[category]
            val utilizationRate = budget?.let { if (it > 0) totalSpent / it else null }
            val daysUntilExhausted = budget?.let {
                if (dailyRate > 0 && it > totalSpent) {
                    ((it - totalSpent) / dailyRate).toInt()
                } else if (totalSpent >= it) {
                    0
                } else {
                    null
                }
            }

            CategoryVelocity(
                category = category,
                dailyRate = dailyRate,
                projectedMonthlyTotal = projectedTotal,
                budgetUtilizationRate = utilizationRate,
                daysUntilBudgetExhausted = daysUntilExhausted
            )
        }
    }

    private fun computeRunwayDays(
        expenses: List<Expense>,
        balance: Double,
        today: LocalDate
    ): Int {
        if (balance <= 0) return 0

        // Long history: typical discretionary pace plus recurring bills spread across the month
        val baseline = ProjectionMath.historyBaseline(expenses, today)
        if (baseline.hasEnoughHistory && ProjectionMath.historyBeatsLinear(expenses, today)) {
            val dailyBurn = baseline.discretionaryDailyRate + baseline.recurringMonthlyTotal / 30.4
            return if (dailyBurn > 0) (balance / dailyBurn).toInt() else Int.MAX_VALUE
        }

        // Not enough history yet: daily burn rate from last 30 days
        val dailyBurn = ProjectionMath.recentDailyRate(expenses, today)

        return if (dailyBurn > 0) {
            (balance / dailyBurn).toInt()
        } else {
            Int.MAX_VALUE
        }
    }

    // === Utility Functions ===

    /**
     * Get detailed day-of-week spending analysis
     */
    fun analyzeDayOfWeekSpending(expenses: List<Expense>): List<DayOfWeekSpending> {
        return DayOfWeek.entries.map { day ->
            val dayExpenses = expenses.filter { it.date.dayOfWeek == day }
            DayOfWeekSpending(
                dayOfWeek = day,
                averageSpending = if (dayExpenses.isNotEmpty()) dayExpenses.sumOf { it.amount } / dayExpenses.size else 0.0,
                totalSpending = dayExpenses.sumOf { it.amount },
                transactionCount = dayExpenses.size
            )
        }
    }

    /**
     * Detect seasonal spending patterns (higher spending in certain months)
     */
    fun detectSeasonalPatterns(expenses: List<Expense>): Map<Int, Double> {
        val byMonth = expenses.groupBy { it.date.monthValue }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        if (byMonth.isEmpty()) return emptyMap()

        val avgMonthlySpending = byMonth.values.average()

        // Return deviation from average for each month
        return byMonth.mapValues { (_, total) ->
            if (avgMonthlySpending > 0) {
                ((total - avgMonthlySpending) / avgMonthlySpending) * 100
            } else {
                0.0
            }
        }
    }
}
