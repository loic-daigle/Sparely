package com.example.sparely.domain.logic

import com.example.sparely.domain.model.EmergencyFundGoal
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.RecommendationResult
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsCategory
import com.example.sparely.domain.model.SavingsPlan
import com.example.sparely.domain.model.SavingsPlanEntry
import com.example.sparely.domain.model.SavingsPercentages
import com.example.sparely.domain.model.SavingsTransfer
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.roundToTwoDecimals
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

class RecommendationEngine {
    fun generate(
        expenses: List<Expense>,
        transfers: List<SavingsTransfer>,
        settings: SparelySettings,
        autoTune: Boolean = true,
        emergencyGoal: EmergencyFundGoal? = null
    ): RecommendationResult {
        val now = LocalDate.now()
        val windowStart = now.minusDays(30)
        val recentExpenses = expenses.filter { !it.date.isBefore(windowStart) }
        val recentTransfers = transfers.filter { !it.date.isBefore(windowStart) }

        val monthlySpending = recentExpenses.sumOf { it.amount }
        val monthlyReservedFromExpenses = recentExpenses.sumOf { it.allocation.totalSetAside }
        val monthlyReservedFromTransfers = recentTransfers.sumOf { it.amount }
        val monthlyReserved = monthlyReservedFromExpenses + monthlyReservedFromTransfers
        val monthlyIncome = settings.monthlyIncome.coerceAtLeast(1.0)
        val spendToIncome = (monthlySpending / monthlyIncome).coerceIn(0.0, 1.5)
        val observedSavingsRate = when {
            monthlySpending > 0.0 -> (monthlyReserved / (monthlyReserved + monthlySpending)).coerceIn(0.0, 1.0)
            monthlyReserved > 0.0 -> (monthlyReserved / monthlyIncome).coerceIn(0.0, 1.0)
            else -> settings.defaultPercentages.total
        }
        val age = settings.age.coerceIn(13, 100)

        val recommended = if (autoTune) {
            computeAutoPercentages(settings, spendToIncome, observedSavingsRate, age, emergencyGoal)
        } else {
            settings.defaultPercentages.adjustWithinBudget()
        }.adjustWithinBudget(0.45)

        val safeShare = recommended.safeInvestmentSplit
        val highRiskShare = 1.0 - safeShare

        val plan = buildPlan(
            percentages = recommended,
            settings = settings,
            recentExpenses = recentExpenses,
            recentTransfers = recentTransfers,
            safeShare = safeShare,
            highRiskShare = highRiskShare
        )

        val rationale = buildString {
            append("Spending is ")
            append(String.format("%.0f", spendToIncome * 100))
            append("% of income; savings rate observed at ")
            append(String.format("%.0f", observedSavingsRate * 100))
            append("%. ")
            if (autoTune) {
                append("Focus on ")
                append(String.format("%.0f", recommended.emergency * 100))
                append("% emergency, ")
                append(String.format("%.0f", recommended.invest * 100))
                append("% investing, with ")
                append(String.format("%.0f", recommended.`fun` * 100))
                append("% fun buffer. ")
            } else {
                append("Using your custom mix to guide this month's targets. ")
            }
            append("Investment mix aims for ")
            append(String.format("%.0f", safeShare * 100))
            append("% in broad ETFs/bonds and ")
            append(String.format("%.0f", highRiskShare * 100))
            append("% in higher-volatility assets.")
            if (age < 20 && autoTune) {
                append(" Being $age lets Sparely prioritise education goals over emergency padding.")
            }
            emergencyGoal?.let {
                if (it.shortfallAmount > 0.0) {
                    append(" Emergency fund is about ")
                    append(it.shortfallAmount.formatCurrency())
                    append(" short of the new target; allocations lean into closing that gap.")
                } else {
                    append(" Emergency fund target reached—shifting more focus to growth goals.")
                }
            }
            if (plan.totalRemaining > 0.0) {
                append(" Set aside roughly ${plan.totalRemaining.formatCurrency()} more this month to stay on track.")
            }
        }

        return RecommendationResult(
            recommendedPercentages = recommended,
            safeInvestmentRatio = safeShare,
            highRiskInvestmentRatio = highRiskShare,
            rationale = rationale,
            savingsPlan = plan,
            autoAdjusted = autoTune
        )
    }

    private fun computeSafeSplit(
        riskLevel: RiskLevel,
        spendToIncome: Double,
        observedSavingsRate: Double
    ): Double {
        val base = when (riskLevel) {
            RiskLevel.CONSERVATIVE -> 0.82
            RiskLevel.BALANCED -> 0.68
            RiskLevel.AGGRESSIVE -> 0.48
        }

        val spendingPressure = when {
            spendToIncome > 1.1 -> 0.06
            spendToIncome > 0.9 -> 0.03
            spendToIncome < 0.6 -> -0.04
            else -> 0.0
        }

        val savingsConfidence = when {
            observedSavingsRate > 0.28 -> -0.05
            observedSavingsRate > 0.20 -> -0.02
            observedSavingsRate < 0.12 -> 0.04
            else -> 0.0
        }

        return min(0.95, max(0.3, base + spendingPressure + savingsConfidence))
    }

    private fun computeAutoPercentages(
        settings: SparelySettings,
        spendToIncome: Double,
        observedSavingsRate: Double,
        age: Int,
        emergencyGoal: EmergencyFundGoal?
    ): SavingsPercentages {
        val baseEmergency = when (settings.riskLevel) {
            RiskLevel.CONSERVATIVE -> 0.22
            RiskLevel.BALANCED -> 0.18
            RiskLevel.AGGRESSIVE -> 0.14
        }

        val baseInvest = when (settings.riskLevel) {
            RiskLevel.CONSERVATIVE -> 0.06
            RiskLevel.BALANCED -> 0.09
            RiskLevel.AGGRESSIVE -> 0.13
        }

        val ageEmergencyBias = when {
            age < 18 -> -0.1
            age < 25 -> -0.03
            age > 55 -> 0.04
            else -> 0.0
        }

        val ageInvestBias = when {
            age < 18 -> 0.05
            age < 25 -> 0.02
            age > 55 -> -0.03
            else -> 0.0
        }

        val emergencyAdjustment = when {
            spendToIncome > 1.05 -> 0.04
            spendToIncome > 0.9 -> 0.02
            spendToIncome < 0.6 -> -0.02
            else -> 0.0
        } + when {
            observedSavingsRate < 0.15 -> 0.02
            observedSavingsRate > 0.25 -> -0.02
            else -> 0.0
        }

        val investAdjustment = when {
            spendToIncome < 0.7 -> 0.02
            spendToIncome > 1.0 -> -0.015
            else -> 0.0
        } + when (settings.riskLevel) {
            RiskLevel.CONSERVATIVE -> -0.01
            RiskLevel.BALANCED -> 0.0
            RiskLevel.AGGRESSIVE -> 0.02
        } + ageInvestBias

        val emergencyFloor = if (age < 18) 0.0 else 0.1
        val emergencyBoost = emergencyGoal?.let { goal ->
            if (goal.targetAmount <= 0.0) {
                0.0
            } else {
                val shortfallRatio = (goal.shortfallAmount / goal.targetAmount).coerceIn(0.0, 1.5)
                when {
                    shortfallRatio > 1.0 -> 0.06
                    shortfallRatio > 0.6 -> 0.04
                    shortfallRatio > 0.3 -> 0.02
                    shortfallRatio < 0.05 -> -0.015
                    else -> 0.0
                }
            }
        } ?: 0.0
        val emergencyPercent = (baseEmergency + emergencyAdjustment + ageEmergencyBias + emergencyBoost).coerceIn(emergencyFloor, 0.35)
        val investPercent = (baseInvest + investAdjustment).coerceIn(0.05, 0.18)
        val tentativeFunPercent = max(0.05, 0.32 - (emergencyPercent + investPercent))

        return SavingsPercentages(
            emergency = emergencyPercent,
            invest = investPercent,
            `fun` = tentativeFunPercent,
            safeInvestmentSplit = computeSafeSplit(settings.riskLevel, spendToIncome, observedSavingsRate)
        )
    }

    private fun buildPlan(
        percentages: SavingsPercentages,
        settings: SparelySettings,
        recentExpenses: List<Expense>,
        recentTransfers: List<SavingsTransfer>,
        safeShare: Double,
        highRiskShare: Double
    ): SavingsPlan {
        val emergencyFromExpenses = recentExpenses.sumOf { it.allocation.emergencyAmount }
        val investmentFromExpenses = recentExpenses.sumOf { it.allocation.investmentAmount }
        val funFromExpenses = recentExpenses.sumOf { it.allocation.funAmount }

        val emergencyFromTransfers = recentTransfers.filter { it.category == SavingsCategory.EMERGENCY }.sumOf { it.amount }
        val investmentFromTransfers = recentTransfers.filter { it.category == SavingsCategory.INVESTMENT }.sumOf { it.amount }
        val funFromTransfers = recentTransfers.filter { it.category == SavingsCategory.FUN }.sumOf { it.amount }

        val emergencyReserved = emergencyFromExpenses + emergencyFromTransfers
        val investmentReserved = investmentFromExpenses + investmentFromTransfers
        val funReserved = funFromExpenses + funFromTransfers

        val emergencyTarget = (settings.monthlyIncome * percentages.emergency).roundToTwoDecimals()
        val investmentTarget = (settings.monthlyIncome * percentages.invest).roundToTwoDecimals()
        val funTarget = (settings.monthlyIncome * percentages.`fun`).roundToTwoDecimals()

        val safeTarget = (investmentTarget * safeShare).roundToTwoDecimals()
        val highRiskTarget = (investmentTarget * highRiskShare).roundToTwoDecimals()

        val entries = listOf(
            SavingsPlanEntry(
                category = SavingsCategory.EMERGENCY,
                targetAmount = emergencyTarget,
                alreadySetAside = emergencyReserved.roundToTwoDecimals()
            ),
            SavingsPlanEntry(
                category = SavingsCategory.INVESTMENT,
                targetAmount = investmentTarget,
                alreadySetAside = investmentReserved.roundToTwoDecimals(),
                recommendedSafeAmount = safeTarget,
                recommendedHighRiskAmount = highRiskTarget
            ),
            SavingsPlanEntry(
                category = SavingsCategory.FUN,
                targetAmount = funTarget,
                alreadySetAside = funReserved.roundToTwoDecimals()
            )
        )

        return SavingsPlan(entries)
    }

}
