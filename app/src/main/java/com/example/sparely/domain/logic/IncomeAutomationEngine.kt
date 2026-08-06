package com.example.sparely.domain.logic

import com.example.sparely.domain.model.AnalyticsSnapshot
import com.example.sparely.domain.model.PayInterval
import com.example.sparely.domain.model.PayScheduleSettings
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.SparelySettings
import kotlin.math.max
import kotlin.math.min

object IncomeAutomationEngine {
    data class Input(
        val currentPayAmount: Double,
        val schedule: PayScheduleSettings,
        val settings: SparelySettings,
        val analytics: AnalyticsSnapshot,
        val recurringExpenses: List<RecurringExpense>
    )

    data class Recommendation(
        val saveRate: Double,
        val savingTaxRate: Double,
        val rationale: List<String>
    )

    fun evaluate(input: Input): Recommendation {
        val sanitizedPay = input.currentPayAmount.coerceAtLeast(0.0)
        if (sanitizedPay <= 0.0) {
            return Recommendation(
                saveRate = input.schedule.defaultSaveRate.coerceIn(0.0, 1.0),
                savingTaxRate = input.settings.savingTaxRate.coerceIn(0.0, 1.0),
                rationale = listOf("Pay amount unavailable; keeping existing rates.")
            )
        }

        val intervalFactor = paychecksPerMonth(input.schedule)
        
        // Use predicted pay amount if available, otherwise use current
        val effectivePay = input.schedule.predictedNextAmount?.takeIf { it > 0 }
            ?: input.schedule.predictNextPaycheck().takeIf { it > 0 }
            ?: sanitizedPay
        
        // Use average for more stable projections
        val averagePay = input.schedule.averagePayAmount().takeIf { it > 0 } ?: sanitizedPay
        val projectedMonthlyIncome = (averagePay * intervalFactor).coerceAtLeast(input.settings.monthlyIncome)

        val monthlyExpenses = deriveMonthlyExpense(input.analytics, input.recurringExpenses)
        val bufferFloor = (monthlyExpenses * 0.15) + 150.0
        val residual = (projectedMonthlyIncome - monthlyExpenses).coerceAtLeast(0.0)

        val expenseCoverageRatio = if (projectedMonthlyIncome <= 0.0) 0.0 else (monthlyExpenses / projectedMonthlyIncome).coerceIn(0.0, 1.0)
        val residualRatio = if (projectedMonthlyIncome <= 0.0) 0.0 else (residual / projectedMonthlyIncome).coerceIn(0.0, 1.0)

        // Get income stability and variance
        val incomeStability = input.schedule.determineStability()
        val incomeVariance = input.schedule.calculateVariance()

        // Base save rate calculation
        var recommendedSaveRate = input.settings.targetSavingsRate
        recommendedSaveRate += residualRatio * 0.35
        recommendedSaveRate -= expenseCoverageRatio * 0.1
        
        // Adjust for income stability - more conservative for variable income
        val stabilityAdjustment = when (incomeStability) {
            com.example.sparely.domain.model.IncomeStability.VERY_STABLE -> 0.05  // Can save more aggressively
            com.example.sparely.domain.model.IncomeStability.STABLE -> 0.0
            com.example.sparely.domain.model.IncomeStability.MODERATE -> -0.05  // More conservative
            com.example.sparely.domain.model.IncomeStability.VARIABLE -> -0.10  // Much more conservative
        }
        recommendedSaveRate += stabilityAdjustment
        recommendedSaveRate = recommendedSaveRate.coerceIn(0.05, 0.65)

        val baselineExpensePerPay = if (intervalFactor <= 0.0) monthlyExpenses else monthlyExpenses / intervalFactor
        val maxAffordableRate = if (sanitizedPay <= 0.0) 0.0 else 1.0 - ((baselineExpensePerPay + bufferFloor / intervalFactor) / sanitizedPay)
        recommendedSaveRate = recommendedSaveRate.coerceAtMost(maxAffordableRate.coerceIn(0.1, 0.8))
        recommendedSaveRate = recommendedSaveRate.coerceIn(0.05, 0.6)

        val recommendedSavingTaxRate = buildSavingTaxRate(recommendedSaveRate, residualRatio, expenseCoverageRatio, incomeStability)

        val rationale = mutableListOf<String>()
        rationale += "Monthly income baseline: ${"%.0f".format(projectedMonthlyIncome)}"
        rationale += "Average monthly expenses: ${"%.0f".format(monthlyExpenses)}"
        rationale += "Residual buffer: ${"%.0f".format(residual)}"
        rationale += "Income stability: ${incomeStability.name.lowercase().replace("_", " ")}"
        if (incomeVariance > 0) {
            rationale += "Income variance: ${incomeVariance.formatPercent()}"
        }
        rationale += "Applied save rate: ${recommendedSaveRate.formatPercent()}"
        rationale += "Saving tax skim: ${recommendedSavingTaxRate.formatPercent()}"

        val result = Recommendation(
            saveRate = recommendedSaveRate.coerceIn(0.0, 1.0),
            savingTaxRate = recommendedSavingTaxRate.coerceIn(0.0, 1.0),
            rationale = rationale
        )
        
        return result
    }

    private fun paychecksPerMonth(schedule: PayScheduleSettings): Double {
        return when (schedule.interval) {
            PayInterval.WEEKLY -> 52.0 / 12.0
            PayInterval.BIWEEKLY -> 26.0 / 12.0
            PayInterval.SEMI_MONTHLY -> 2.0
            PayInterval.MONTHLY -> 1.0
            PayInterval.CUSTOM -> schedule.customDaysBetween?.let { 30.0 / it.coerceAtLeast(1) } ?: 1.0
        }
    }

    private fun deriveMonthlyExpense(analytics: AnalyticsSnapshot, recurringExpenses: List<RecurringExpense>): Double {
        val analyticsAverage = analytics.averageMonthlyExpense.takeIf { it > 0.0 }
        val recurringTotal = recurringExpenses.filter { it.isActive }
            .sumOf { expense ->
                when (expense.frequency) {
                    com.example.sparely.domain.model.RecurringFrequency.DAILY -> expense.amount * 30
                    com.example.sparely.domain.model.RecurringFrequency.WEEKLY -> expense.amount * 4.33
                    com.example.sparely.domain.model.RecurringFrequency.BIWEEKLY -> expense.amount * 2.17
                    com.example.sparely.domain.model.RecurringFrequency.MONTHLY -> expense.amount
                    com.example.sparely.domain.model.RecurringFrequency.QUARTERLY -> expense.amount / 3.0
                    com.example.sparely.domain.model.RecurringFrequency.YEARLY -> expense.amount / 12.0
                }
            }
        return when {
            analyticsAverage != null -> max(analyticsAverage, recurringTotal)
            recurringTotal > 0.0 -> recurringTotal
            else -> 0.0
        }
    }

    private fun buildSavingTaxRate(
        saveRate: Double,
        residualRatio: Double,
        expenseCoverageRatio: Double,
        incomeStability: com.example.sparely.domain.model.IncomeStability
    ): Double {
        val baseline = saveRate * 0.25
        val demandBoost = residualRatio * 0.1
        val safetyPenalty = expenseCoverageRatio * 0.05
        
        // Reduce saving tax for variable income to build more cushion
        val stabilityAdjustment = when (incomeStability) {
            com.example.sparely.domain.model.IncomeStability.VERY_STABLE -> 0.02
            com.example.sparely.domain.model.IncomeStability.STABLE -> 0.0
            com.example.sparely.domain.model.IncomeStability.MODERATE -> -0.02
            com.example.sparely.domain.model.IncomeStability.VARIABLE -> -0.05
        }
        
        return min(0.25, max(0.0, baseline + demandBoost - safetyPenalty + stabilityAdjustment))
    }
}
