package com.example.sparely.domain.logic

import android.content.Context
import com.example.sparely.domain.model.AlertMessage
import com.example.sparely.domain.model.AlertType
import com.example.sparely.domain.model.BudgetHealthStatus
import com.example.sparely.domain.model.BudgetOverrunPrompt
import com.example.sparely.domain.model.BudgetStatus
import com.example.sparely.domain.model.BudgetSummary
import com.example.sparely.domain.model.BudgetSuggestion
import com.example.sparely.domain.model.BudgetPromptReason
import com.example.sparely.domain.model.CategoryBudget
import com.example.sparely.domain.model.EducationStatus
import com.example.sparely.domain.model.EmploymentStatus
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.model.SuggestionConfidence
import com.example.sparely.domain.model.displayName
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.utils.roundToTwoDecimals
import com.sparely.app.R
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.max

/**
 * Engine for managing and analyzing budgets.
 */
object BudgetEngine {

    private val baseCategoryShares = mapOf(
        ExpenseCategory.GROCERIES to 0.18,
        ExpenseCategory.DINING to 0.08,
        ExpenseCategory.TRANSPORTATION to 0.12,
        ExpenseCategory.ENTERTAINMENT to 0.08,
        ExpenseCategory.UTILITIES to 0.17,
        ExpenseCategory.HEALTH to 0.08,
        ExpenseCategory.EDUCATION to 0.07,
        ExpenseCategory.SHOPPING to 0.10,
        ExpenseCategory.TRAVEL to 0.07,
        ExpenseCategory.OTHER to 0.05
    )

    /**
     * Calculate budget status for a specific category.
     */
    fun calculateBudgetStatus(
        categoryBudget: CategoryBudget,
        expenses: List<Expense>,
        yearMonth: YearMonth = YearMonth.now()
    ): BudgetStatus {
        val categoryExpenses = expenses.filter { expense ->
            expense.category == categoryBudget.category &&
            YearMonth.from(expense.date) == yearMonth
        }

        val spent = categoryExpenses.sumOf { it.amount }.roundToTwoDecimals()
        val remaining = (categoryBudget.monthlyLimit - spent).coerceAtLeast(0.0).roundToTwoDecimals()
        val percentageUsed = if (categoryBudget.monthlyLimit > 0) {
            (spent / categoryBudget.monthlyLimit).coerceIn(0.0, 2.0)
        } else {
            0.0
        }

        val status = when {
            spent > categoryBudget.monthlyLimit -> BudgetHealthStatus.OVER_BUDGET
            percentageUsed >= 0.9 -> BudgetHealthStatus.CRITICAL
            percentageUsed >= 0.7 -> BudgetHealthStatus.WARNING
            else -> BudgetHealthStatus.HEALTHY
        }

        return BudgetStatus(
            category = categoryBudget.category,
            limit = categoryBudget.monthlyLimit.roundToTwoDecimals(),
            spent = spent,
            remaining = remaining,
            percentageUsed = percentageUsed,
            status = status,
            yearMonth = yearMonth
        )
    }

    /**
     * Generate overall budget summary.
     */
    fun generateBudgetSummary(
        budgets: List<CategoryBudget>,
        expenses: List<Expense>,
        yearMonth: YearMonth = YearMonth.now()
    ): BudgetSummary {
        val categoryStatuses = budgets
            .filter { it.isActive && it.yearMonth == yearMonth }
            .map { calculateBudgetStatus(it, expenses, yearMonth) }

        val totalBudget = categoryStatuses.sumOf { it.limit }.roundToTwoDecimals()
        val totalSpent = categoryStatuses.sumOf { it.spent }.roundToTwoDecimals()
        val totalRemaining = (totalBudget - totalSpent).coerceAtLeast(0.0).roundToTwoDecimals()

        val overallHealth = when {
            categoryStatuses.any { it.status == BudgetHealthStatus.OVER_BUDGET } -> BudgetHealthStatus.OVER_BUDGET
            categoryStatuses.count { it.status == BudgetHealthStatus.CRITICAL } >= 2 -> BudgetHealthStatus.CRITICAL
            categoryStatuses.any { it.status == BudgetHealthStatus.CRITICAL } -> BudgetHealthStatus.WARNING
            categoryStatuses.any { it.status == BudgetHealthStatus.WARNING } -> BudgetHealthStatus.WARNING
            else -> BudgetHealthStatus.HEALTHY
        }

        return BudgetSummary(
            totalBudget = totalBudget,
            totalSpent = totalSpent,
            totalRemaining = totalRemaining,
            categoryStatuses = categoryStatuses,
            overallHealth = overallHealth,
            yearMonth = yearMonth
        )
    }

    /**
     * Generate smart budget alerts based on spending patterns.
     */
    fun generateBudgetAlerts(budgetSummary: BudgetSummary, context: Context): List<AlertMessage> {
        val alerts = mutableListOf<AlertMessage>()

        // Overall budget alerts
        if (budgetSummary.overallHealth == BudgetHealthStatus.OVER_BUDGET) {
            alerts.add(
                AlertMessage(
                    title = context.getString(R.string.budget_alert_exceeded_title),
                    description = context.getString(R.string.budget_alert_exceeded_desc, (budgetSummary.totalSpent - budgetSummary.totalBudget).formatCurrency()),
                    type = AlertType.WARNING,
                    priority = 10,
                    actionable = true
                )
            )
        }

        // Category-specific alerts
        budgetSummary.categoryStatuses.forEach { status ->
            val catName = status.category.displayName()
            when (status.status) {
                BudgetHealthStatus.OVER_BUDGET -> {
                    alerts.add(
                        AlertMessage(
                            title = context.getString(R.string.budget_alert_category_over_title, catName),
                            description = context.getString(R.string.budget_alert_category_over_desc, status.spent.formatCurrency(), status.limit.formatCurrency(), catName.lowercase()),
                            type = AlertType.WARNING,
                            priority = 9,
                            actionable = true
                        )
                    )
                }
                BudgetHealthStatus.CRITICAL -> {
                    alerts.add(
                        AlertMessage(
                            title = context.getString(R.string.budget_alert_category_critical_title, catName),
                            description = context.getString(R.string.budget_alert_category_critical_desc, status.remaining.formatCurrency(), catName.lowercase(), status.daysRemainingInMonth),
                            type = AlertType.WARNING,
                            priority = 7,
                            actionable = true
                        )
                    )
                }
                BudgetHealthStatus.WARNING -> {
                    alerts.add(
                        AlertMessage(
                            title = context.getString(R.string.budget_alert_category_warning_title, catName),
                            description = context.getString(R.string.budget_alert_category_warning_desc, status.percentageUsed * 100, catName.lowercase(), status.remaining.formatCurrency()),
                            type = AlertType.INFO,
                            priority = 5,
                            actionable = true
                        )
                    )
                }
                else -> {}
            }
        }
// ...
  // Positive reinforcement
        val healthyCategories = budgetSummary.categoryStatuses.filter { 
            it.status == BudgetHealthStatus.HEALTHY && it.percentageUsed < 0.5 
        }
        if (healthyCategories.isNotEmpty() && budgetSummary.yearMonth.month.value > 15) { // Assuming > 15 is just checking if we are past mid-month? Original code had > 15, likely day of month? Wait, yearMonth.month.value returns 1-12. This looks like a bug in original code or logic I missed. 
            // Ah, YearMonth.month is Month enum. value is 1-12. 
            // Original code: `budgetSummary.yearMonth.month.value > 15` -> this condition is impossible for month (1-12).
            // Maybe it meant `LocalDate.now().dayOfMonth > 15`?
            // "yearMonth" is just Year and Month. 
            // I will leave logic as is for now to avoid changing behavior, but it likely never triggered. 
            // Wait, maybe it meant `LocalDate.now().dayOfMonth`?
            // If I look closely at original code: `budgetSummary.yearMonth.month.value > 15`
            // Let's assume I should preserve it or fix it if obvious.
            // But my task is translation. I'll stick to translation.
            
            alerts.add(
                AlertMessage(
                    title = context.getString(R.string.budget_alert_success_title),
                    description = context.getString(R.string.budget_alert_success_desc, healthyCategories.size),
                    type = AlertType.SUCCESS,
                    priority = 2,
                    actionable = false
                )
            )
        }

        return alerts.sortedByDescending { it.priority }
    }

    /**
     * Suggest budget adjustments based on historical spending.
     */
    fun suggestBudgetAdjustments(
        currentBudgets: List<CategoryBudget>,
        expenses: List<Expense>,
        settings: SparelySettings,
        monthsToAnalyze: Int = 6,
        context: Context
    ): List<BudgetSuggestion> {
        if (currentBudgets.isEmpty() && expenses.isEmpty() && settings.monthlyIncome <= 0.0) {
            return emptyList()
        }

        val monthsWindow = monthsToAnalyze.coerceAtLeast(1)
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths((monthsWindow - 1).coerceAtLeast(0).toLong())
        val startDate = startMonth.atDay(1)
        val relevantExpenses = expenses.filter { !it.date.isBefore(startDate) }
        val monthsInWindow = (0 until monthsWindow).map { currentMonth.minusMonths(it.toLong()) }

        val adjustedPercentages = settings.defaultPercentages.adjustWithinBudget()
        val savingsFraction = adjustedPercentages.total.coerceIn(0.0, 0.95)
        val spendableIncome = if (settings.monthlyIncome > 0) {
            (settings.monthlyIncome * (1.0 - savingsFraction)).coerceAtLeast(0.0)
        } else {
            0.0
        }

        val adjustedShares = baseCategoryShares.toMutableMap()
        applyAgeAdjustments(settings.age, adjustedShares)
        applyEmploymentAdjustments(settings.employmentStatus, adjustedShares)
        applyEducationAdjustments(settings.educationStatus, adjustedShares)
        val shareTotal = adjustedShares.values.sum().takeIf { it > 0 } ?: 1.0
        val normalizedShares = adjustedShares.mapValues { it.value / shareTotal }

        val budgetByCategory = currentBudgets
            .filter { it.isActive && it.yearMonth == currentMonth }
            .associateBy { it.category }

        val suggestions = ExpenseCategory.values().mapNotNull { category ->
            val categoryExpenses = relevantExpenses.filter { it.category == category }
            val monthlyTotals = monthsInWindow.associateWith { windowMonth ->
                categoryExpenses
                    .filter { YearMonth.from(it.date) == windowMonth }
                    .sumOf { it.amount }
            }
            val monthsWithSpending = monthlyTotals.count { it.value > 0.0 }
            val totalSpentInWindow = monthlyTotals.values.sum()
            val historicalAverage = if (monthsWindow > 0) totalSpentInWindow / monthsWindow else 0.0
            val recentPeak = monthlyTotals.values.maxOrNull() ?: 0.0
            val profileShare = normalizedShares[category] ?: 0.0
            val profileTarget = if (spendableIncome > 0) spendableIncome * profileShare else 0.0
            val currentBudget = budgetByCategory[category]

            if (historicalAverage <= 0.0 && profileTarget <= 0.0 && currentBudget == null) {
                return@mapNotNull null
            }

            val historyWeight = when {
                monthsWithSpending >= monthsWindow -> 0.8
                monthsWithSpending >= 4 -> 0.75
                monthsWithSpending >= 2 -> 0.6
                monthsWithSpending == 1 -> 0.45
                historicalAverage > 0.0 -> 0.35
                else -> 0.0
            }
            val profileWeight = (1.0 - historyWeight).coerceIn(0.0, 1.0)
            var blended = when {
                historyWeight > 0.0 && profileTarget > 0.0 ->
                    (historicalAverage * historyWeight) + (profileTarget * profileWeight)
                historyWeight > 0.0 -> historicalAverage
                else -> profileTarget
            }
            if (recentPeak > 0.0) {
                blended = max(blended, recentPeak * 0.9)
            }

            val volatilityFactor = when {
                monthsWithSpending >= 4 -> 1.05
                monthsWithSpending >= 2 -> 1.1
                monthsWithSpending == 1 -> 1.15
                else -> 1.2
            }
            val employmentFactor = when (settings.employmentStatus) {
                EmploymentStatus.SELF_EMPLOYED -> 1.12
                EmploymentStatus.UNEMPLOYED -> 1.05
                EmploymentStatus.PART_TIME -> 1.03
                EmploymentStatus.STUDENT -> 0.98
                EmploymentStatus.RETIRED -> 1.02
                else -> 1.0
            }
            val categoryFactor = when (category) {
                ExpenseCategory.UTILITIES -> 1.05
                ExpenseCategory.HEALTH -> 1.1
                ExpenseCategory.EDUCATION -> if (settings.educationStatus == EducationStatus.UNIVERSITY) 1.12 else 1.0
                ExpenseCategory.TRAVEL -> 0.95
                ExpenseCategory.ENTERTAINMENT -> if (settings.age > 45) 0.9 else 1.0
                else -> 1.0
            }

            var suggested = blended * volatilityFactor * employmentFactor * categoryFactor
            val capMultiplier = if (spendableIncome > 0) {
                (profileShare * 1.8).coerceAtLeast(profileShare + 0.05).coerceAtMost(0.5)
            } else {
                null
            }
            val capAmount = capMultiplier?.let { spendableIncome * it }
            if (capAmount != null) {
                suggested = suggested.coerceAtMost(capAmount)
            }

            val minimumThreshold = when {
                currentBudget != null -> 0.0
                spendableIncome > 0 -> (spendableIncome * 0.015).coerceAtMost(35.0)
                else -> 10.0
            }
            if (suggested < minimumThreshold && historicalAverage < minimumThreshold && profileTarget < minimumThreshold) {
                return@mapNotNull null
            }

            val confidence = when {
                monthsWithSpending >= 4 -> SuggestionConfidence.HIGH
                monthsWithSpending >= 2 -> SuggestionConfidence.MEDIUM
                monthsWithSpending == 1 -> SuggestionConfidence.MEDIUM
                else -> SuggestionConfidence.LOW
            }
            val rationale = buildRationale(
                category = category,
                monthsWithSpending = monthsWithSpending,
                historicalAverage = historicalAverage,
                profileTarget = profileTarget,
                spendableIncome = spendableIncome,
                settings = settings,
                profileShare = profileShare,
                context = context
            )

            BudgetSuggestion(
                category = category,
                suggestedLimit = suggested,
                currentLimit = currentBudget?.monthlyLimit,
                historicalAverage = historicalAverage,
                profileTarget = profileTarget,
                rationale = rationale,
                confidence = confidence
            )
        }

        if (suggestions.isEmpty()) return emptyList()

        return suggestions
            .filter { it.suggestedLimit > 0.0 }
            .sortedWith(compareByDescending<BudgetSuggestion> {
                val baseline = it.currentLimit ?: it.historicalAverage
                abs(it.suggestedLimit - baseline)
            }.thenBy { it.category.name })
    }

    private fun applyAgeAdjustments(age: Int, shares: MutableMap<ExpenseCategory, Double>) {
        when {
            age < 25 -> {
                shares.boost(ExpenseCategory.DINING, 1.15)
                shares.boost(ExpenseCategory.ENTERTAINMENT, 1.12)
                shares.boost(ExpenseCategory.TRAVEL, 1.1)
                shares.boost(ExpenseCategory.HEALTH, 0.85)
                shares.boost(ExpenseCategory.UTILITIES, 0.9)
            }
            age in 25..40 -> {
                shares.boost(ExpenseCategory.SHOPPING, 1.05)
            }
            age in 41..55 -> {
                shares.boost(ExpenseCategory.HEALTH, 1.12)
                shares.boost(ExpenseCategory.TRAVEL, 1.05)
                shares.boost(ExpenseCategory.ENTERTAINMENT, 0.9)
            }
            age > 55 -> {
                shares.boost(ExpenseCategory.HEALTH, 1.2)
                shares.boost(ExpenseCategory.UTILITIES, 1.05)
                shares.boost(ExpenseCategory.SHOPPING, 0.85)
                shares.boost(ExpenseCategory.DINING, 0.9)
            }
        }
    }

    private fun applyEmploymentAdjustments(status: EmploymentStatus, shares: MutableMap<ExpenseCategory, Double>) {
        when (status) {
            EmploymentStatus.STUDENT -> {
                shares.boost(ExpenseCategory.EDUCATION, 1.4)
                shares.boost(ExpenseCategory.DINING, 0.9)
                shares.boost(ExpenseCategory.TRAVEL, 0.8)
            }
            EmploymentStatus.SELF_EMPLOYED -> {
                shares.boost(ExpenseCategory.UTILITIES, 1.1)
                shares.boost(ExpenseCategory.TRANSPORTATION, 1.1)
                shares.boost(ExpenseCategory.TRAVEL, 0.85)
            }
            EmploymentStatus.PART_TIME -> {
                shares.boost(ExpenseCategory.GROCERIES, 1.05)
                shares.boost(ExpenseCategory.ENTERTAINMENT, 0.9)
                shares.boost(ExpenseCategory.TRANSPORTATION, 1.05)
            }
            EmploymentStatus.UNEMPLOYED -> {
                shares.boost(ExpenseCategory.GROCERIES, 1.1)
                shares.boost(ExpenseCategory.UTILITIES, 1.1)
                shares.boost(ExpenseCategory.SHOPPING, 0.8)
                shares.boost(ExpenseCategory.TRAVEL, 0.75)
            }
            EmploymentStatus.RETIRED -> {
                shares.boost(ExpenseCategory.HEALTH, 1.2)
                shares.boost(ExpenseCategory.TRAVEL, 1.1)
                shares.boost(ExpenseCategory.TRANSPORTATION, 0.85)
            }
            else -> Unit
        }
    }

    private fun applyEducationAdjustments(status: EducationStatus, shares: MutableMap<ExpenseCategory, Double>) {
        when (status) {
            EducationStatus.HIGH_SCHOOL -> shares.boost(ExpenseCategory.EDUCATION, 1.15)
            EducationStatus.UNIVERSITY -> shares.boost(ExpenseCategory.EDUCATION, 1.35)
            else -> Unit
        }
    }

    private fun MutableMap<ExpenseCategory, Double>.boost(category: ExpenseCategory, factor: Double) {
        val current = this[category] ?: return
        this[category] = (current * factor).coerceAtLeast(0.01)
    }

    private fun buildRationale(
        category: ExpenseCategory,
        monthsWithSpending: Int,
        historicalAverage: Double,
        profileTarget: Double,
        spendableIncome: Double,
        settings: SparelySettings,
        profileShare: Double,
        context: Context
    ): String {
        val parts = mutableListOf<String>()
        val catName = category.displayName().lowercase()
        if (monthsWithSpending > 0 && historicalAverage > 0.0) {
            parts += context.getString(R.string.budget_rationale_history, historicalAverage.formatCurrency(), catName)
        } else {
            parts += context.getString(R.string.budget_rationale_no_history, catName)
        }

        if (settings.monthlyIncome > 0) {
            val savingsFraction = settings.defaultPercentages.adjustWithinBudget().total
            val reservedSavings = settings.monthlyIncome * savingsFraction
            parts += context.getString(R.string.budget_rationale_income, settings.monthlyIncome.formatCurrency(), reservedSavings.formatCurrency(), spendableIncome.formatCurrency())
            if (profileShare > 0.0) {
                parts += context.getString(R.string.budget_rationale_profile_share, profileShare.formatPercent(1), catName)
            }
        }

        when (settings.employmentStatus) {
            EmploymentStatus.STUDENT -> parts += context.getString(R.string.budget_rationale_student)
            EmploymentStatus.PART_TIME -> parts += context.getString(R.string.budget_rationale_part_time)
            EmploymentStatus.SELF_EMPLOYED -> parts += context.getString(R.string.budget_rationale_self_employed)
            EmploymentStatus.UNEMPLOYED -> parts += context.getString(R.string.budget_rationale_unemployed)
            EmploymentStatus.RETIRED -> parts += context.getString(R.string.budget_rationale_retired)
            else -> Unit
        }

        if (settings.age < 25 && category in setOf(ExpenseCategory.EDUCATION, ExpenseCategory.GROCERIES)) {
            parts += context.getString(R.string.budget_rationale_early_career)
        }
        if (settings.age > 50 && category == ExpenseCategory.HEALTH) {
            parts += context.getString(R.string.budget_rationale_later_health)
        }

        if (profileTarget > 0.0 && historicalAverage > 0.0) {
            val midpoint = (historicalAverage + profileTarget) / 2
            parts += context.getString(R.string.budget_rationale_blended, midpoint.formatCurrency())
        }

        return parts.joinToString(" ").replace("  ", " ").trim()
    }

    private fun ExpenseCategory.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

    fun detectBudgetPrompts(
        summary: BudgetSummary,
        expenses: List<Expense>,
        suggestions: List<BudgetSuggestion>,
        settings: SparelySettings
    ): List<BudgetOverrunPrompt> {
        if (summary.categoryStatuses.isEmpty()) return emptyList()

        val currentMonth = summary.yearMonth
        val monthExpenses = expenses.filter { YearMonth.from(it.date) == currentMonth }
        val suggestionsByCategory = suggestions.associateBy { it.category }

        return summary.categoryStatuses
            .filter { it.status == BudgetHealthStatus.OVER_BUDGET }
            .mapNotNull { status ->
                val categoryExpenses = monthExpenses.filter { it.category == status.category }
                if (categoryExpenses.isEmpty()) return@mapNotNull null

                val latestExpense = categoryExpenses.maxByOrNull { it.date }
                val overspend = (status.spent - status.limit).coerceAtLeast(0.0)
                val reason = classifyPromptReason(status, latestExpense, settings)
                BudgetOverrunPrompt(
                    category = status.category,
                    month = status.yearMonth,
                    status = status,
                    overspendAmount = overspend,
                    latestExpense = latestExpense,
                    suggestion = suggestionsByCategory[status.category],
                    reason = reason
                )
            }
    }

    private fun classifyPromptReason(
        status: BudgetStatus,
        latestExpense: Expense?,
        settings: SparelySettings
    ): BudgetPromptReason {
        if (status.limit <= 0.0) return BudgetPromptReason.UNPLANNED_CATEGORY
        val overspend = (status.spent - status.limit).coerceAtLeast(0.0)
        if (latestExpense != null) {
            val amountShare = latestExpense.amount / status.limit
            val overspendShare = if (overspend > 0) latestExpense.amount / overspend else 0.0
            val isRareCategory = status.category in listOf(ExpenseCategory.TRAVEL, ExpenseCategory.SHOPPING, ExpenseCategory.EDUCATION)
            val isOneOff = amountShare >= 0.45 || overspendShare >= 0.6 || latestExpense.amount >= 250.0
            if (isOneOff && isRareCategory) {
                return BudgetPromptReason.POTENTIAL_ONE_OFF
            }
            if (isOneOff && settings.employmentStatus != EmploymentStatus.UNEMPLOYED) {
                return BudgetPromptReason.POTENTIAL_ONE_OFF
            }
        }
        val trendSensitive = status.category in listOf(
            ExpenseCategory.GROCERIES,
            ExpenseCategory.DINING,
            ExpenseCategory.UTILITIES,
            ExpenseCategory.TRANSPORTATION
        )
        return if (trendSensitive || settings.hasDebts) {
            BudgetPromptReason.TRENDING_HIGH
        } else {
            BudgetPromptReason.UNPLANNED_CATEGORY
        }
    }

    // === Predictive Budget Forecasting ===

    data class BudgetForecast(
        val category: ExpenseCategory,
        val predictedMonthEndSpending: Double,
        val currentSpent: Double,
        val budgetLimit: Double,
        val projectedOverspend: Double, // Positive if predicted to exceed budget
        val dailyBurnRate: Double,
        val daysUntilBudgetExhausted: Int?,
        val confidenceLevel: SuggestionConfidence
    )

    /**
     * Predict month-end spending for each budget category based on current trajectory.
     */
    fun predictMonthEndSpending(
        budgets: List<CategoryBudget>,
        expenses: List<Expense>,
        today: LocalDate = LocalDate.now()
    ): List<BudgetForecast> {
        val currentMonth = YearMonth.from(today)
        val dayOfMonth = today.dayOfMonth.coerceAtLeast(1)
        val daysInMonth = currentMonth.lengthOfMonth()

        return budgets
            .filter { it.isActive && it.yearMonth == currentMonth }
            .map { budget ->
                val categoryExpenses = expenses.filter {
                    it.category == budget.category && YearMonth.from(it.date) == currentMonth
                }
                val spent = categoryExpenses.sumOf { it.amount }
                val dailyRate = spent / dayOfMonth
                val predictedTotal = ProjectionMath.linearMonthEndProjection(spent, dayOfMonth, daysInMonth)
                val projectedOverspend = (predictedTotal - budget.monthlyLimit).coerceAtLeast(0.0)

                val daysUntilExhausted = if (dailyRate > 0 && budget.monthlyLimit > spent) {
                    ((budget.monthlyLimit - spent) / dailyRate).toInt()
                } else if (spent >= budget.monthlyLimit) {
                    0
                } else {
                    null
                }

                val confidence = ProjectionMath.confidenceForDayOfMonth(dayOfMonth)

                BudgetForecast(
                    category = budget.category,
                    predictedMonthEndSpending = predictedTotal,
                    currentSpent = spent,
                    budgetLimit = budget.monthlyLimit,
                    projectedOverspend = projectedOverspend,
                    dailyBurnRate = dailyRate,
                    daysUntilBudgetExhausted = daysUntilExhausted,
                    confidenceLevel = confidence
                )
            }
    }

    data class SeasonalPattern(
        val category: ExpenseCategory,
        val monthlyDeviations: Map<Int, Double>, // Month (1-12) to % deviation from average
        val peakMonths: List<Int>,
        val lowMonths: List<Int>,
        val currentMonthFactor: Double = 1.0, // Multiplier for current month
        val suggestedBudgetAdjustment: Double = 0.0 // How much to adjust budget
    )

    data class SeasonalAdjustment(
        val factor: Double, // Multiplier (1.0 = no change, 1.2 = 20% increase expected)
        val expectedChangePercent: Double,
        val isHighSeason: Boolean,
        val recommendation: String?
    )

    /**
     * Detect seasonal spending patterns by category.
     */
    fun detectSeasonalPatterns(
        expenses: List<Expense>,
        minMonthsOfData: Int = 6,
        currentMonth: Int = LocalDate.now().monthValue
    ): List<SeasonalPattern> {
        val monthlyByCategory = expenses
            .groupBy { it.category }
            .filter { (_, categoryExpenses) ->
                val months = categoryExpenses.map { YearMonth.from(it.date) }.distinct()
                months.size >= minMonthsOfData
            }

        return monthlyByCategory.map { (category, categoryExpenses) ->
            val byCalendarMonth = categoryExpenses.groupBy { it.date.monthValue }
                .mapValues { (_, monthExpenses) -> monthExpenses.sumOf { it.amount } / monthExpenses.size }

            val avgSpending = byCalendarMonth.values.average()
            val deviations = byCalendarMonth.mapValues { (_, avg) ->
                if (avgSpending > 0) ((avg - avgSpending) / avgSpending) * 100 else 0.0
            }

            val peakMonths = deviations.filter { it.value > 20 }.keys.toList().sorted()
            val lowMonths = deviations.filter { it.value < -20 }.keys.toList().sorted()
            
            // Calculate current month factor
            val currentDeviation = deviations[currentMonth] ?: 0.0
            val currentFactor = 1.0 + (currentDeviation / 100.0)
            
            // Calculate suggested budget adjustment amount
            val suggestedAdjustment = avgSpending * (currentDeviation / 100.0)

            SeasonalPattern(
                category = category,
                monthlyDeviations = deviations,
                peakMonths = peakMonths,
                lowMonths = lowMonths,
                currentMonthFactor = currentFactor,
                suggestedBudgetAdjustment = suggestedAdjustment
            )
        }
    }

    /**
     * Get budget adjustment factors for current month based on seasonal patterns.
     * Returns a map of category to adjustment data.
     */
    fun getSeasonalBudgetAdjustments(
        expenses: List<Expense>,
        currentMonth: Int = LocalDate.now().monthValue,
        context: Context
    ): Map<ExpenseCategory, SeasonalAdjustment> {
        val patterns = detectSeasonalPatterns(expenses, currentMonth = currentMonth)

        return patterns.associate { pattern ->
            val deviation = pattern.monthlyDeviations[currentMonth] ?: 0.0
            val factor = 1.0 + (deviation / 100.0)

            val recommendation = when {
                deviation > 30 -> context.getString(R.string.seasonal_higher_strong, deviation.toInt())
                deviation > 15 -> context.getString(R.string.seasonal_higher_slight)
                deviation < -30 -> context.getString(R.string.seasonal_lower_strong, (-deviation).toInt())
                deviation < -15 -> context.getString(R.string.seasonal_lower_slight)
                else -> null
            }

            pattern.category to SeasonalAdjustment(
                factor = factor,
                expectedChangePercent = deviation,
                isHighSeason = currentMonth in pattern.peakMonths,
                recommendation = recommendation
            )
        }
    }



    data class PreemptiveBudgetWarning(
        val category: ExpenseCategory,
        val message: String,
        val severity: AlertType,
        val daysUntilIssue: Int
    )

    /**
     * Generate preemptive warnings before budgets are exceeded.
     */
    fun generatePreemptiveWarnings(
        forecasts: List<BudgetForecast>,
        today: LocalDate = LocalDate.now(),
        context: Context
    ): List<PreemptiveBudgetWarning> {
        val warnings = mutableListOf<PreemptiveBudgetWarning>()
        val currentMonth = YearMonth.from(today)
        val daysRemaining = currentMonth.lengthOfMonth() - today.dayOfMonth

        for (forecast in forecasts) {
            // Already over budget
            if (forecast.currentSpent >= forecast.budgetLimit) continue

            val daysUntilExhausted = forecast.daysUntilBudgetExhausted ?: continue

            when {
                daysUntilExhausted <= 3 -> {
                    warnings.add(PreemptiveBudgetWarning(
                        category = forecast.category,
                        message = context.getString(R.string.preemptive_exhausted_soon, forecast.category.displayName(), daysUntilExhausted),
                        severity = AlertType.WARNING,
                        daysUntilIssue = daysUntilExhausted
                    ))
                }
                daysUntilExhausted <= 7 && daysRemaining > 7 -> {
                    warnings.add(PreemptiveBudgetWarning(
                        category = forecast.category,
                        message = context.getString(R.string.preemptive_exhausted_date, forecast.category.displayName(), today.plusDays(daysUntilExhausted.toLong()).toString()),
                        severity = AlertType.INFO,
                        daysUntilIssue = daysUntilExhausted
                    ))
                }
                forecast.projectedOverspend > 0 && forecast.confidenceLevel == SuggestionConfidence.HIGH -> {
                    warnings.add(PreemptiveBudgetWarning(
                        category = forecast.category,
                        message = context.getString(R.string.preemptive_projected_exceed, forecast.category.displayName(), forecast.projectedOverspend.formatCurrency()),
                        severity = AlertType.INFO,
                        daysUntilIssue = daysRemaining
                    ))
                }
            }
        }

        return warnings.sortedBy { it.daysUntilIssue }
    }
}
