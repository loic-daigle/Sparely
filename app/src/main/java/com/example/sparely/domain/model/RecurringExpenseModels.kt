package com.example.sparely.domain.model

import java.time.LocalDate

/**
 * Represents a recurring expense (subscription, rent, etc.)
 * Contains the template for creating actual expenses when processed.
 */
data class RecurringExpense(
    val id: Long = 0,
    val description: String,
    val amount: Double,
    val category: ExpenseCategory,
    val frequency: RecurringFrequency,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val lastProcessedDate: LocalDate? = null,
    val isActive: Boolean = true,
    val autoLog: Boolean = true,
    // Controls whether the scheduled payment is executed automatically or created as pending
    val executeAutomatically: Boolean = false,
    val reminderDaysBefore: Int = 2,
    val merchantName: String? = null,
    val notes: String? = null,
    // Store/merchant for this recurring expense
    val storeId: Long? = null,
    // Payment method for this recurring expense
    val paymentMethodId: Long? = null,
    // Expense-related fields (same as ExpenseInput)
    val includesTax: Boolean = false,
    val deductFromMainAccount: Boolean = false,
    val deductedFromVaultId: Long? = null,
    val manualPercentages: SavingsPercentages? = null,
    // Variable amount support for bills like electricity, water, etc.
    val isVariableAmount: Boolean = false,
    val amountHistory: List<AmountHistoryEntry> = emptyList(),
    val estimatedAmount: Double? = null, // Predicted amount for next occurrence
    val nextRunAt: java.time.LocalDateTime? = null,
    val type: ExpenseType = ExpenseType.PRODUCT, // PRODUCT or SERVICE
    // Line items support (new for feature parity with expenses)
    val items: List<ExpenseItem> = emptyList(),
    // Asset allocations support (new for feature parity with expenses)
    val assetAllocations: Map<Long, Double> = emptyMap(), // assetId -> percentageAllocated
    // Explicit user choice; null means "derive from category"
    val necessityOverride: Necessity? = null
) {
    val necessity: Necessity get() = necessityOverride ?: category.defaultNecessity()
}

/**
 * Tracks historical amounts for variable recurring expenses.
 */
data class AmountHistoryEntry(
    val date: LocalDate,
    val amount: Double
)

/**
 * Frequency options for recurring expenses.
 */
enum class RecurringFrequency(val daysInterval: Int) {
    DAILY(1),
    WEEKLY(7),
    BIWEEKLY(14),
    MONTHLY(30),
    QUARTERLY(90),
    YEARLY(365)
}

/**
 * Input for creating recurring expense.
 */
data class RecurringExpenseInput(
    val description: String,
    val amount: Double,
    val category: ExpenseCategory,
    val frequency: RecurringFrequency,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val autoLog: Boolean = true,
    val executeAutomatically: Boolean = false,
    val reminderDaysBefore: Int = 2,
    val notes: String? = null,
    // Store/merchant for this recurring expense
    val storeId: Long? = null,
    // Payment method for this recurring expense
    val paymentMethodId: Long? = null,
    // Expense-related fields (same as ExpenseInput)
    val includesTax: Boolean = false,
    val deductFromMainAccount: Boolean = false,
    val deductedFromVaultId: Long? = null,
    val manualPercentages: SavingsPercentages? = null,
    // Variable amount support
    val isVariableAmount: Boolean = false,
    val nextRunAt: java.time.LocalDateTime? = null,
    val type: ExpenseType = ExpenseType.PRODUCT, // PRODUCT or SERVICE
    // Line items support (new for feature parity with expenses)
    val items: List<ExpenseItem> = emptyList(),
    // Asset allocations support (new for feature parity with expenses)
    val assetAllocations: Map<Long, Double> = emptyMap(), // assetId -> percentageAllocated
    val necessityOverride: Necessity? = null
)

/**
 * Upcoming recurring expense notification.
 */
data class UpcomingRecurringExpense(
    val recurringExpense: RecurringExpense,
    val dueDate: LocalDate,
    val daysUntilDue: Int,
    val predictedAmount: Double? = null // For variable expenses
)

// === Extension Functions for Variable Amount Prediction ===

/**
 * Predict next amount for variable recurring expenses using weighted moving average.
 * More recent entries have higher weight.
 */
fun RecurringExpense.predictNextAmount(): Double {
    if (!isVariableAmount || amountHistory.isEmpty()) return amount
    if (amountHistory.size == 1) return amountHistory.first().amount

    // Sort by date (most recent last) and use weighted average
    val sorted = amountHistory.sortedBy { it.date }
    val weights = sorted.indices.map { (it + 1).toDouble() }
    val weightedSum = sorted.zip(weights) { entry, weight -> entry.amount * weight }.sum()
    return weightedSum / weights.sum()
}

/**
 * Get seasonal adjustment factor for this expense based on historical data.
 * Returns multiplier (1.0 = no change, 1.25 = 25% higher expected).
 */
fun RecurringExpense.getSeasonalFactor(month: Int = LocalDate.now().monthValue): Double {
    if (amountHistory.size < 6) return 1.0 // Need sufficient history

    // Group amounts by calendar month
    val monthlyAverages = amountHistory.groupBy { it.date.monthValue }
        .mapValues { (_, entries) -> entries.map { it.amount }.average() }

    if (monthlyAverages.size < 4) return 1.0 // Need data for multiple months

    val overallAverage = amountHistory.map { it.amount }.average()
    val monthAverage = monthlyAverages[month] ?: return 1.0

    return if (overallAverage > 0) monthAverage / overallAverage else 1.0
}

/**
 * Get seasonally-adjusted predicted amount.
 */
fun RecurringExpense.getSeasonallyAdjustedPrediction(month: Int = LocalDate.now().monthValue): Double {
    val basePrediction = predictNextAmount()
    val seasonalFactor = getSeasonalFactor(month)
    return basePrediction * seasonalFactor
}

/**
 * Calculate the average amount from history.
 */
fun RecurringExpense.averageHistoricalAmount(): Double {
    if (amountHistory.isEmpty()) return amount
    return amountHistory.map { it.amount }.average()
}

/**
 * Calculate the amount variance (standard deviation as percentage of mean).
 */
fun RecurringExpense.amountVariancePercent(): Double {
    if (amountHistory.size < 2) return 0.0
    val amounts = amountHistory.map { it.amount }
    val mean = amounts.average()
    if (mean <= 0) return 0.0
    val variance = amounts.map { (it - mean) * (it - mean) }.average()
    val stdDev = kotlin.math.sqrt(variance)
    return (stdDev / mean) * 100
}

