package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.ui.utils.roundToTwoDecimals
import com.example.sparely.ui.utils.formatPercent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sqrt

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
        val runwayDays: Int // Days until predicted balance hits zero at current rate
    )

    data class SpendingAnomaly(
        val expense: Expense,
        val zScore: Double, // How many standard deviations from mean
        val reason: String
    )

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

    fun analyze(
        expenses: List<Expense>,
        mainAccountBalance: Double = 0.0,
        categoryBudgets: Map<ExpenseCategory, Double> = emptyMap(),
        today: LocalDate = LocalDate.now()
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
                runwayDays = Int.MAX_VALUE
            )
        }

        val weeklyAvg = computeWeeklyAverage(expenses, today).roundToTwoDecimals()
        val monthlyAvg = computeMonthlyAverage(expenses).roundToTwoDecimals()
        val (trend, trendPct) = computeTrend(expenses, today)
        val (topCategory, topCategoryChange) = findTopGrowingCategory(expenses, today)
        val highSpendDays = findHighSpendingDays(expenses)
        val anomalies = detectAnomalies(expenses, today)
        val velocity = computeCategoryVelocity(expenses, categoryBudgets, today)
        val predictedMonthEnd = predictMonthEndSpending(expenses, today).roundToTwoDecimals()
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
            runwayDays = runway
        )
    }

    // === Helper Functions ===

    private fun computeWeeklyAverage(expenses: List<Expense>, today: LocalDate): Double {
        val cutoff = today.minusDays(28) // 4 weeks
        val recentExpenses = expenses.filter { !it.date.isBefore(cutoff) }
        if (recentExpenses.isEmpty()) return 0.0

        val weeklyTotals = recentExpenses.groupBy { weekNumber(it.date) }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        return if (weeklyTotals.isNotEmpty()) weeklyTotals.values.average() else 0.0
    }

    private fun weekNumber(date: LocalDate): Int {
        return date.dayOfYear / 7
    }

    private fun computeMonthlyAverage(expenses: List<Expense>): Double {
        val grouped = expenses.groupBy { YearMonth.from(it.date) }
        if (grouped.isEmpty()) return 0.0
        val totals = grouped.values.map { monthExpenses -> monthExpenses.sumOf { it.amount } }
        return totals.average()
    }

    private fun computeTrend(expenses: List<Expense>, today: LocalDate): Pair<SpendingTrend, Double> {
        val currentMonth = YearMonth.from(today)
        val lastMonth = currentMonth.minusMonths(1)
        val twoMonthsAgo = currentMonth.minusMonths(2)

        val currentMonthTotal = expenses.filter { YearMonth.from(it.date) == currentMonth }.sumOf { it.amount }
        val lastMonthTotal = expenses.filter { YearMonth.from(it.date) == lastMonth }.sumOf { it.amount }
        val twoMonthsAgoTotal = expenses.filter { YearMonth.from(it.date) == twoMonthsAgo }.sumOf { it.amount }

        // Project current month total based on days elapsed
        val dayOfMonth = today.dayOfMonth
        val daysInMonth = currentMonth.lengthOfMonth()
        val projectedCurrentMonth = if (dayOfMonth > 0) {
            (currentMonthTotal / dayOfMonth) * daysInMonth
        } else {
            currentMonthTotal
        }

        // Calculate month-over-month change
        val comparison = if (lastMonthTotal > 0) lastMonthTotal else twoMonthsAgoTotal
        val percentChange = if (comparison > 0) {
            ((projectedCurrentMonth - comparison) / comparison) * 100
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

        // Project current month totals
        val dayOfMonth = today.dayOfMonth
        val daysInMonth = currentMonth.lengthOfMonth()
        val projectionFactor = if (dayOfMonth > 0) daysInMonth.toDouble() / dayOfMonth else 1.0

        var topCategory: ExpenseCategory? = null
        var topChange = 0.0

        for (category in ExpenseCategory.entries) {
            val current = (currentByCategory[category] ?: 0.0) * projectionFactor
            val last = lastByCategory[category] ?: 0.0
            if (last > 0) {
                val change = ((current - last) / last) * 100
                if (change > topChange) {
                    topChange = change
                    topCategory = category
                }
            } else if (current > 0) {
                // New category this month
                if (current > topChange) {
                    topChange = current
                    topCategory = category
                }
            }
        }

        return Pair(topCategory, topChange)
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

    private fun detectAnomalies(expenses: List<Expense>, today: LocalDate): List<SpendingAnomaly> {
        if (expenses.size < 10) return emptyList() // Need sufficient data
        
        // Filter to last 60 days for anomaly *candidacy*
        // However, we should calculate Mean/StdDev based on a longer history (e.g., 6 months) for accuracy
        // BUT, the user wants to not be bothered by old stuff.
        // Let's take expenses from last 60 days to report AS anomalies, 
        // but perhaps calculate stats on everything?
        // Actually, if we use everything for stats store, an old high expense increases the StDev, 
        // making new regular expenses look NORMAL, which is good.
        // But if that old expense WAS an anomaly, we shouldn't show it anymore.
        
        val anomalies = mutableListOf<SpendingAnomaly>()
        val zScoreThreshold = 2.5 // 2.5 standard deviations
        val cutoffDate = today.minusDays(60)

        // Use ALL expenses for statistical baseline to have a robust mean/stdev
        val amounts = expenses.map { it.amount }
        val mean = amounts.average()
        val stdDev = calculateStdDev(amounts, mean)

        if (stdDev == 0.0) return emptyList()

        // Only scan recent expenses for *reporting* anomalies
        val recentExpenses = expenses.filter { !it.date.isBefore(cutoffDate) }

        for (expense in recentExpenses) {
            val zScore = (expense.amount - mean) / stdDev
            if (abs(zScore) > zScoreThreshold) {
                val reason = if (zScore > 0) {
                    "Unusually high spending (${zScore.formatPercent(1).removeSuffix("%")}σ above average)"
                } else {
                    "Unusually low spending (${abs(zScore).formatPercent(1).removeSuffix("%")}σ below average)"
                }
                anomalies.add(SpendingAnomaly(expense, zScore, reason))
            }
        }

        // Prioritize more recent anomalies slightly by date in sorting if needed, 
        // but Z-score is usually best. Let's just return what we have.
        return anomalies.sortedByDescending { abs(it.zScore) }.take(5)
    }

    private fun calculateStdDev(values: List<Double>, mean: Double): Double {
        if (values.size < 2) return 0.0
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

    private fun computeCategoryVelocity(
        expenses: List<Expense>,
        budgets: Map<ExpenseCategory, Double>,
        today: LocalDate
    ): Map<ExpenseCategory, CategoryVelocity> {
        val currentMonth = YearMonth.from(today)
        val dayOfMonth = today.dayOfMonth.coerceAtLeast(1)
        val daysInMonth = currentMonth.lengthOfMonth()
        val daysRemaining = daysInMonth - dayOfMonth

        val thisMonthExpenses = expenses.filter { YearMonth.from(it.date) == currentMonth }
        val byCategory = thisMonthExpenses.groupBy { it.category }

        return byCategory.mapValues { (category, categoryExpenses) ->
            val totalSpent = categoryExpenses.sumOf { it.amount }.roundToTwoDecimals()
            val dailyRate = (totalSpent / dayOfMonth).roundToTwoDecimals()
            val projectedTotal = (totalSpent + (dailyRate * daysRemaining)).roundToTwoDecimals()

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

    private fun predictMonthEndSpending(expenses: List<Expense>, today: LocalDate): Double {
        val currentMonth = YearMonth.from(today)
        val dayOfMonth = today.dayOfMonth.coerceAtLeast(1)
        val daysInMonth = currentMonth.lengthOfMonth()

        val thisMonthTotal = expenses
            .filter { YearMonth.from(it.date) == currentMonth }
            .sumOf { it.amount }

        val dailyRate = thisMonthTotal / dayOfMonth
        return dailyRate * daysInMonth
    }

    private fun computeRunwayDays(
        expenses: List<Expense>,
        balance: Double,
        today: LocalDate
    ): Int {
        if (balance <= 0) return 0

        // Calculate daily burn rate from last 30 days
        val cutoff = today.minusDays(30)
        val recentExpenses = expenses.filter { !it.date.isBefore(cutoff) }

        if (recentExpenses.isEmpty()) return Int.MAX_VALUE

        val totalSpent = recentExpenses.sumOf { it.amount }
        val daysOfData = ChronoUnit.DAYS.between(
            recentExpenses.minOf { it.date },
            today
        ).toInt().coerceAtLeast(1)

        val dailyBurn = totalSpent / daysOfData

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
