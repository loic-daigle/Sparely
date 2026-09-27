package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Asset
import com.example.sparely.domain.model.AssetCostProjection
import com.example.sparely.domain.model.CostVsTargetPrice
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.MonthlyProjection
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.RecurringFrequency
import com.example.sparely.domain.model.predictNextAmount
import java.time.LocalDate
import java.time.YearMonth

/**
 * Engine for calculating asset cost projections over time.
 * Combines recurring expense predictions with historical spending patterns
 * to provide users with realistic cost estimates.
 */
object AssetProjectionEngine {

    /**
     * Calculate comprehensive cost projections for an asset.
     *
     * @param asset The asset to project costs for
     * @param linkedExpenses All one-time expenses linked to the asset with their allocation percentages
     * @param linkedRecurringExpenses All recurring expenses linked to the asset with their allocation percentages
     * @param months Number of months to project (default 12)
     * @return AssetCostProjection with detailed breakdown
     */
    fun calculateAssetProjections(
        asset: Asset,
        linkedExpenses: List<Pair<Expense, Double>>,  // Pair<Expense, percentage>
        linkedRecurringExpenses: List<Pair<RecurringExpense, Double>>,  // Pair<RecurringExpense, percentage>
        months: Int = 12
    ): AssetCostProjection {
        val today = LocalDate.now()
        val currentMonth = YearMonth.from(today)

        // Calculate monthly breakdown for recurring expenses
        val recurringMonthlyContributions = calculateRecurringContributions(linkedRecurringExpenses, months)

        // Calculate historical monthly averages from one-time expenses
        val historicalMonthlyAverages = calculateHistoricalAverages(linkedExpenses, months)

        // Combine to create monthly projections
        val monthlyProjections = (0 until months).map { offset ->
            val month = currentMonth.plusMonths(offset.toLong())
            val recurringAmount = recurringMonthlyContributions[month] ?: 0.0
            val historicalAmount = historicalMonthlyAverages[month] ?: 0.0
            val total = (recurringAmount + historicalAmount).coerceAtLeast(0.0)

            MonthlyProjection(
                month = month,
                projectedAmount = total,
                recurringContribution = recurringAmount,
                historicalAverage = historicalAmount
            )
        }

        // Calculate totals for different time horizons
        val projected3Months = monthlyProjections.take(3).sumOf { it.projectedAmount }
        val projected6Months = monthlyProjections.take(6).sumOf { it.projectedAmount }
        val projected12Months = monthlyProjections.take(12).sumOf { it.projectedAmount }
        val averageMonthly = if (monthlyProjections.isNotEmpty()) {
            monthlyProjections.sumOf { it.projectedAmount } / monthlyProjections.size
        } else {
            0.0
        }

        // Calculate cost vs target price
        val costVsPrice = if (asset.assetPrice > 0) {
            val percentageOfTarget = (projected12Months / asset.assetPrice) * 100
            CostVsTargetPrice(
                targetPrice = asset.assetPrice,
                projectedIn12Months = projected12Months,
                percentageOfTarget = percentageOfTarget,
                isOverBudget = projected12Months >= asset.assetPrice,
                remainingBudget = (asset.assetPrice - projected12Months).coerceAtLeast(0.0)
            )
        } else {
            null
        }

        return AssetCostProjection(
            assetId = asset.id,
            assetName = asset.name,
            assetPrice = asset.assetPrice.takeIf { it > 0 },
            monthlyProjections = monthlyProjections,
            totalProjected3Months = projected3Months,
            totalProjected6Months = projected6Months,
            totalProjected12Months = projected12Months,
            averageMonthly = averageMonthly,
            costVsAssetPrice = costVsPrice
        )
    }

    /**
     * Calculate monthly contribution from recurring expenses.
     * Uses frequency to determine how many times per month an expense occurs.
     */
    private fun calculateRecurringContributions(
        linkedRecurringExpenses: List<Pair<RecurringExpense, Double>>,
        months: Int
    ): Map<YearMonth, Double> {
        val contributions = mutableMapOf<YearMonth, Double>()

        linkedRecurringExpenses.forEach { (recurring, percentage) ->
            val today = LocalDate.now()
            val currentMonth = YearMonth.from(today)
            val occurrencesPerMonth = when (recurring.frequency) {
                RecurringFrequency.DAILY -> 30.0 / recurring.frequency.daysInterval
                RecurringFrequency.WEEKLY -> 30.0 / recurring.frequency.daysInterval
                RecurringFrequency.BIWEEKLY -> 30.0 / recurring.frequency.daysInterval
                RecurringFrequency.MONTHLY -> 1.0
                RecurringFrequency.QUARTERLY -> 1.0 / 3.0
                RecurringFrequency.YEARLY -> 1.0 / 12.0
            }

            val monthlyAmount = recurring.predictNextAmount() * percentage * occurrencesPerMonth

            // Distribute across all projection months
            for (offset in 0 until months) {
                val month = currentMonth.plusMonths(offset.toLong())
                contributions[month] = (contributions[month] ?: 0.0) + monthlyAmount
            }
        }

        return contributions
    }

    /**
     * Calculate historical monthly averages from one-time expenses.
     * Groups expenses by month and calculates average spending per month.
     */
    private fun calculateHistoricalAverages(
        linkedExpenses: List<Pair<Expense, Double>>,
        months: Int
    ): Map<YearMonth, Double> {
        if (linkedExpenses.isEmpty()) return emptyMap()

        // Group expenses by month and sum allocated amounts
        val expensesByMonth = linkedExpenses.groupBy { (expense, _) ->
            YearMonth.from(expense.date)
        }.mapValues { (_, expenses) ->
            expenses.sumOf { (expense, percentage) ->
                expense.amount * percentage
            }
        }

        if (expensesByMonth.isEmpty()) return emptyMap()

        // Calculate months with data
        val monthsWithData = expensesByMonth.size
        val totalSpending = expensesByMonth.values.sum()
        val monthlyAverage = totalSpending / monthsWithData

        // Return average for all projected months
        val today = LocalDate.now()
        val currentMonth = YearMonth.from(today)
        val averages = mutableMapOf<YearMonth, Double>()
        for (offset in 0 until months) {
            val month = currentMonth.plusMonths(offset.toLong())
            averages[month] = monthlyAverage
        }
        return averages
    }
}
