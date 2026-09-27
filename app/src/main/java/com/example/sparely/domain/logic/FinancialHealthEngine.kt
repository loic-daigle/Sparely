package com.example.sparely.domain.logic

import com.example.sparely.domain.model.*
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import java.time.LocalDate

/**
 * Engine for calculating financial health scores.
 */
object FinancialHealthEngine {

    /**
     * Calculate comprehensive financial health score.
     */
    fun calculateHealthScore(
        expenses: List<Expense>,
        transfers: List<SavingsTransfer>,
        goals: List<Goal>,
        budgetSummary: BudgetSummary?,
        settings: SparelySettings,
        analytics: AnalyticsSnapshot
    ): FinancialHealthScore {
        val isNewUser = settings.isNewUser
        val hasMinimalData = expenses.size < 5
        
        val savingsRateScore = calculateSavingsRateScore(analytics, settings, isNewUser || hasMinimalData)
        val emergencyFundScore = calculateEmergencyFundScore(analytics, settings, isNewUser || hasMinimalData)
        val budgetAdherenceScore = calculateBudgetAdherenceScore(budgetSummary, isNewUser || hasMinimalData)
        val goalProgressScore = calculateGoalProgressScore(goals, isNewUser || hasMinimalData)
        val debtRatioScore = 100 // Placeholder - would need debt data

        // Weighted average
        val overallScore = (
            savingsRateScore * 0.30 +
            emergencyFundScore * 0.25 +
            budgetAdherenceScore * 0.20 +
            goalProgressScore * 0.15 +
            debtRatioScore * 0.10
        ).toInt().coerceIn(0, 100)

        val healthLevel = HealthLevel.fromScore(overallScore)

        val topStrengths = buildTopStrengths(
            savingsRateScore,
            emergencyFundScore,
            budgetAdherenceScore,
            goalProgressScore,
            debtRatioScore,
            isNewUser || hasMinimalData
        )

        val improvementAreas = buildImprovementTips(
            savingsRateScore,
            emergencyFundScore,
            budgetAdherenceScore,
            goalProgressScore,
            debtRatioScore,
            analytics,
            settings,
            isNewUser || hasMinimalData
        )

        return FinancialHealthScore(
            overallScore = overallScore,
            savingsRateScore = savingsRateScore,
            emergencyFundScore = emergencyFundScore,
            budgetAdherenceScore = budgetAdherenceScore,
            goalProgressScore = goalProgressScore,
            debtRatioScore = debtRatioScore,
            healthLevel = healthLevel,
            topStrengths = topStrengths,
            improvementAreas = improvementAreas
        )
    }

    private fun calculateSavingsRateScore(analytics: AnalyticsSnapshot, settings: SparelySettings, isNewUser: Boolean): Int {
        val totalSpent = analytics.totalSpent
        val totalSaved = analytics.totalReserved
        
        val totalIncomeProxy = totalSpent + totalSaved
        if (totalIncomeProxy == 0.0) return if (isNewUser) 75 else 50

        val savingsRate = totalSaved / totalIncomeProxy
        
        val baseScore = when {
            savingsRate >= 0.30 -> 100
            savingsRate >= 0.25 -> 95
            savingsRate >= 0.20 -> 85
            savingsRate >= 0.15 -> 75
            savingsRate >= 0.10 -> 60
            savingsRate >= 0.05 -> 40
            else -> 20
        }
        
        return if (isNewUser) (baseScore + 15).coerceIn(65, 100) else baseScore.coerceIn(0, 100)
    }

    private fun calculateEmergencyFundScore(analytics: AnalyticsSnapshot, settings: SparelySettings, isNewUser: Boolean): Int {
        // If living with parents, user considers themselves covered. 
        // We grant full score to avoid penalizing them for "0 emergency fund".
        if (settings.livingSituation == LivingSituation.WITH_PARENTS) {
            return 100
        }

        val emergencyFund = analytics.totalEmergency
        val monthlyExpenses = settings.monthlyIncome * 0.7 // Default assumption
        
        val monthsCovered = if (monthlyExpenses > 0) emergencyFund / monthlyExpenses else 0.0

        val targetMonths = 6.0 // Standard requirement

        // Normalize actual coverage against the target (cap at 1.0 for 100% score logic)
        val scoreRatio = (monthsCovered / targetMonths).coerceIn(0.0, 1.2)
        
        val baseScore = (scoreRatio * 100).toInt()
        
        return if (isNewUser) (baseScore + 20).coerceIn(60, 100) else baseScore.coerceIn(0, 100)
    }

    private fun calculateBudgetAdherenceScore(budgetSummary: BudgetSummary?, isNewUser: Boolean): Int {
        if (budgetSummary == null) return if (isNewUser) 80 else 70 // Higher neutral for new users

        val percentageUsed = budgetSummary.percentageUsed
        
        return when (budgetSummary.overallHealth) {
            BudgetHealthStatus.HEALTHY -> {
                when {
                    percentageUsed <= 0.7 -> 100
                    percentageUsed <= 0.8 -> 90
                    else -> 80
                }
            }
            BudgetHealthStatus.WARNING -> if (isNewUser) 75 else 65
            BudgetHealthStatus.CRITICAL -> if (isNewUser) 60 else 45
            BudgetHealthStatus.OVER_BUDGET -> {
                val overageRatio = percentageUsed - 1.0
                val base = (30 - (overageRatio * 50)).toInt().coerceAtLeast(0)
                if (isNewUser) (base + 15).coerceIn(50, 100) else base
            }
        }
    }

    private fun calculateGoalProgressScore(goals: List<Goal>, isNewUser: Boolean): Int {
        if (goals.isEmpty()) return if (isNewUser) 75 else 60 // Higher neutral for new users

        val activeGoals = goals.filter { !it.archived }
        if (activeGoals.isEmpty()) return if (isNewUser) 75 else 60

        val averageProgress = activeGoals.map { it.progressPercent }.average()
        val completedGoals = goals.count { it.progressPercent >= 1.0 }

        val baseScore = when {
            averageProgress >= 0.75 -> 95
            averageProgress >= 0.50 -> 80
            averageProgress >= 0.25 -> 65
            averageProgress >= 0.10 -> 50
            else -> 35
        }

        val completionBonus = (completedGoals * 5).coerceAtMost(15)
        val adjustedScore = baseScore + completionBonus
        
        return if (isNewUser) (adjustedScore + 10).coerceIn(65, 100) else adjustedScore.coerceIn(0, 100)
    }

    private fun buildTopStrengths(
        savingsRate: Int,
        emergencyFund: Int,
        budgetAdherence: Int,
        goalProgress: Int,
        debtRatio: Int,
        isNewUser: Boolean
    ): List<String> {
        val scores = mutableMapOf(
            "Excellent savings rate" to savingsRate,
            "Strong emergency fund" to emergencyFund,
            "Great budget discipline" to budgetAdherence,
            "Solid goal progress" to goalProgress,
            "Good debt management" to debtRatio
        )
        
        if (isNewUser) {
            scores["Great start on your financial journey"] = 90
        }

        return scores
            .filter { it.value >= 80 }
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }
    }

    private fun buildImprovementTips(
        savingsRate: Int,
        emergencyFund: Int,
        budgetAdherence: Int,
        goalProgress: Int,
        debtRatio: Int,
        analytics: AnalyticsSnapshot,
        settings: SparelySettings,
        isNewUser: Boolean
    ): List<ImprovementTip> {
        val tips = mutableListOf<ImprovementTip>()

        if (savingsRate < 70) {
            val (title, description, priority, actionable) = if (isNewUser) {
                Quadruple(
                    "Grow Your Savings Habit",
                    "You're off to a great start! As you continue using Sparely, aim to save at least 20% of your spending.",
                    Priority.MEDIUM,
                    "Keep tracking your expenses and gradually increase your emergency fund allocation"
                )
            } else {
                Quadruple(
                    "Increase Your Savings Rate",
                    "Your current savings rate could be higher. Aim to save at least 20% of your spending.",
                    Priority.HIGH,
                    "Try increasing your emergency fund allocation by 2-3%"
                )
            }
            tips.add(
                ImprovementTip(
                    title = title,
                    description = description,
                    priority = priority,
                    potentialScoreGain = 15,
                    actionable = actionable
                )
            )
        }

        if (emergencyFund < 75) {
            val monthsCovered = analytics.totalEmergency / (settings.monthlyIncome * 0.7)
            val (title, description, priority) = if (isNewUser) {
                Triple(
                    "Start Building Your Emergency Fund",
                    "It's early days, but aim to gradually build 3-6 months of expenses in your emergency fund.",
                    Priority.MEDIUM
                )
            } else {
                Triple(
                    "Build Your Emergency Fund",
                    "You currently have ${monthsCovered.formatPercent()} months of expenses saved. Aim for 3-6 months.",
                    Priority.HIGH
                )
            }
            tips.add(
                ImprovementTip(
                    title = title,
                    description = description,
                    priority = priority,
                    potentialScoreGain = 20,
                    actionable = "Set aside ${(settings.monthlyIncome * 0.1).formatCurrency("", 0)} per month to emergency savings"
                )
            )
        }

        if (budgetAdherence < 70) {
            val (title, description, actionable) = if (isNewUser) {
                Triple(
                    "Set Up Your First Budgets",
                    "Creating budgets will help you track spending and reach your goals faster.",
                    "Start by setting budgets for your top spending categories"
                )
            } else {
                Triple(
                    "Improve Budget Discipline",
                    "You're frequently exceeding your budgets. Review and adjust your limits.",
                    "Set up budget alerts and review spending weekly"
                )
            }
            tips.add(
                ImprovementTip(
                    title = title,
                    description = description,
                    priority = Priority.MEDIUM,
                    potentialScoreGain = 12,
                    actionable = actionable
                )
            )
        }

        if (goalProgress < 70) {
            val (title, description, actionable) = if (isNewUser) {
                Triple(
                    "Create Your First Savings Goal",
                    "Set a goal to stay motivated on your savings journey!",
                    "Start with a small achievable goal to build momentum"
                )
            } else {
                Triple(
                    "Accelerate Goal Progress",
                    "Your savings goals need more attention to stay on track.",
                    "Review goals monthly and increase allocations by 1-2%"
                )
            }
            tips.add(
                ImprovementTip(
                    title = title,
                    description = description,
                    priority = Priority.MEDIUM,
                    potentialScoreGain = 10,
                    actionable = actionable
                )
            )
        }

        return tips.sortedByDescending { it.priority }
    }
}

// Helper data class for destructuring
private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

