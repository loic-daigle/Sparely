package com.example.sparely.domain.logic

import com.example.sparely.domain.model.AnalyticsSnapshot
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.SavingsCategory
import com.example.sparely.domain.model.SavingsTransfer
import com.example.sparely.domain.model.SpendingTrendType
import com.example.sparely.domain.model.TrendPoint
import com.example.sparely.ui.utils.roundToTwoDecimals
import java.time.LocalDate
import java.time.YearMonth

object AnalyticsEngine {
    fun build(
        expenses: List<Expense>,
        transfers: List<SavingsTransfer> = emptyList(),
        mainAccountBalance: Double = 0.0,
        categoryBudgets: Map<ExpenseCategory, Double> = emptyMap()
    ): AnalyticsSnapshot {
        if (expenses.isEmpty() && transfers.isEmpty()) return AnalyticsSnapshot()

        val emergencyTransfers = transfers.filter { it.category == SavingsCategory.EMERGENCY }.sumOf { it.amount }
        val investmentTransfers = transfers.filter { it.category == SavingsCategory.INVESTMENT }.sumOf { it.amount }
        val funTransfers = transfers.filter { it.category == SavingsCategory.FUN }.sumOf { it.amount }

        val totalEmergency = (expenses.sumOf { it.allocation.emergencyAmount } + emergencyTransfers).roundToTwoDecimals()
        val totalInvested = (expenses.sumOf { it.allocation.investmentAmount } + investmentTransfers).roundToTwoDecimals()
        val totalSafe = (expenses.sumOf { it.allocation.safeInvestmentAmount } + investmentTransfers).roundToTwoDecimals()
        val totalHighRisk = expenses.sumOf { it.allocation.highRiskInvestmentAmount }.roundToTwoDecimals()
        val totalFun = (expenses.sumOf { it.allocation.funAmount } + funTransfers).roundToTwoDecimals()
        val totalSpent = expenses.sumOf { it.amount }.roundToTwoDecimals()

        val predictionExpenses = SmartInsightEngine.filterPredictionExpenses(expenses)
        val monthlyExpenseAverage = computeAverageMonthlyExpense(predictionExpenses).roundToTwoDecimals()

        val chartPoints = buildTrend(expenses)
        val categoryBreakdown = buildCategoryBreakdown(expenses)
        val (averageMonthlyReserve, projectedSix, projectedTwelve) = buildProjections(expenses, transfers)

        // Run spending pattern analysis
        val patternResult = SpendingPatternEngine.analyze(
            expenses = predictionExpenses,
            mainAccountBalance = mainAccountBalance,
            categoryBudgets = categoryBudgets,
            today = LocalDate.now()
        )

        // Map SpendingPatternEngine trend to domain model
        val spendingTrend = when (patternResult.trend) {
            SpendingPatternEngine.SpendingTrend.INCREASING -> SpendingTrendType.INCREASING
            SpendingPatternEngine.SpendingTrend.DECREASING -> SpendingTrendType.DECREASING
            SpendingPatternEngine.SpendingTrend.STABLE -> SpendingTrendType.STABLE
        }

        return AnalyticsSnapshot(
            totalEmergency = totalEmergency,
            totalInvested = totalInvested,
            totalSafeInvested = totalSafe,
            totalHighRiskInvested = totalHighRisk,
            totalFun = totalFun,
            totalSpent = totalSpent,
            chartPoints = chartPoints,
            categoryBreakdown = categoryBreakdown,
            averageMonthlyReserve = averageMonthlyReserve.roundToTwoDecimals(),
            averageMonthlyExpense = monthlyExpenseAverage,
            projectedReserveSixMonths = projectedSix.roundToTwoDecimals(),
            projectedReserveTwelveMonths = projectedTwelve.roundToTwoDecimals(),
            // New spending pattern fields
            spendingTrend = spendingTrend,
            weeklyAverageExpense = patternResult.weeklyAverageExpense.roundToTwoDecimals(),
            monthOverMonthChange = patternResult.trendPercentage,
            topGrowingCategory = patternResult.topGrowingCategory,
            predictedMonthEndSpending = patternResult.predictedMonthEndSpending.roundToTwoDecimals(),
            runwayDays = patternResult.runwayDays
        )
    }

    private fun computeAverageMonthlyExpense(expenses: List<Expense>): Double {
        if (expenses.isEmpty()) return 0.0
        val grouped = expenses.groupBy { YearMonth.from(it.date) }
        if (grouped.isEmpty()) return 0.0
        val totals = grouped.values.map { monthExpenses -> monthExpenses.sumOf { it.amount } }
        return totals.average()
    }

    private fun buildTrend(expenses: List<Expense>): List<TrendPoint> {
        val sorted = expenses.sortedBy { it.date }
        var cumulativeSaved = 0.0
        var cumulativeInvested = 0.0
        return sorted.map { expense ->
            cumulativeSaved += expense.allocation.emergencyAmount + expense.allocation.funAmount
            cumulativeInvested += expense.allocation.investmentAmount
            TrendPoint(
                date = expense.date,
                cumulativeSaved = cumulativeSaved.roundToTwoDecimals(),
                cumulativeInvested = cumulativeInvested.roundToTwoDecimals()
            )
        }
    }

    private fun buildCategoryBreakdown(expenses: List<Expense>): Map<ExpenseCategory, Double> {
        return expenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount }.roundToTwoDecimals() }
            .toSortedMap(compareBy { it.name })
    }

    private fun buildProjections(expenses: List<Expense>, transfers: List<SavingsTransfer>): Triple<Double, Double, Double> {
        if (expenses.isEmpty() && transfers.isEmpty()) return Triple(0.0, 0.0, 0.0)

        val monthlyTotals = mutableMapOf<YearMonth, Double>()

        expenses.groupBy { YearMonth.from(it.date) }
            .forEach { (month, list) ->
                val totalSetAside = list.sumOf { it.allocation.totalSetAside }
                monthlyTotals[month] = (monthlyTotals[month] ?: 0.0) + totalSetAside
            }

        transfers.groupBy { YearMonth.from(it.date) }
            .forEach { (month, list) ->
                val totalTransfers = list.sumOf { it.amount }
                monthlyTotals[month] = (monthlyTotals[month] ?: 0.0) + totalTransfers
            }

        if (monthlyTotals.isEmpty()) return Triple(0.0, 0.0, 0.0)

        val averageMonthly = monthlyTotals.values.average()
        val projectedSix = averageMonthly * 6
        val projectedTwelve = averageMonthly * 12
        return Triple(averageMonthly, projectedSix, projectedTwelve)
    }
}
