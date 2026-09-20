package com.example.sparely.domain.model

import java.time.LocalDate

/**
 * Domain representation of an expense with computed savings allocation.
 */
data class Expense(
    val id: Long,
    val description: String,
    val amount: Double,
    val category: ExpenseCategory,
    val date: LocalDate,
    val includesTax: Boolean,
    val allocation: AllocationBreakdown,
    val appliedPercentages: SavingsPercentages,
    val autoRecommended: Boolean,
    val riskLevelUsed: RiskLevel,
    val deductedFromVaultId: Long? = null,
    val storeId: Long? = null,
    val paymentMethodId: Long? = null,
    val isRecurring: Boolean = false,
    val notes: String? = null,
    val refundedAmount: Double = 0.0,
    val isRefunded: Boolean = false,
    val orderNumber: String? = null,
    val items: List<ExpenseItem> = emptyList(),
    val type: ExpenseType = ExpenseType.PRODUCT,
    val isIgnored: Boolean = false,
    // Derived, never stored: set by RecurringInference for bills that weren't logged via a recurring entry
    val looksRecurring: Boolean = false
) {
    /** True for expenses logged from a recurring entry and for ones that clearly behave like a bill. */
    val countsAsRecurring: Boolean get() = isRecurring || looksRecurring
}

/**
 * User input payload when creating a new expense entry.
 */
data class ExpenseInput(
    val id: Long? = null,
    val description: String,
    val amount: Double,
    val category: ExpenseCategory,
    val date: LocalDate,
    val includesTax: Boolean,
    val manualPercentages: SavingsPercentages? = null,
    val deductFromMainAccount: Boolean = false,
    val deductFromVaultId: Long? = null,
    val storeId: Long? = null,
    val paymentMethodId: Long? = null,
    val isRecurring: Boolean = false,
    val notes: String? = null,
    val orderNumber: String? = null,
    val items: List<ExpenseItem> = emptyList(),
    val assetAllocations: Map<Long, Double> = emptyMap(), // assetId -> percentageAllocated
    val type: ExpenseType = ExpenseType.PRODUCT,
    val isIgnored: Boolean = false
)

/**
 * Supported quick filters for history screens.
 */
enum class DateRangeFilter {
    LAST_7_DAYS,
    LAST_30_DAYS,
    THIS_MONTH,
    LAST_90_DAYS,
    YEAR_TO_DATE,
    ALL_TIME,
    CUSTOM
}

/**
 * Represents a single line item within an expense (e.g., a product in a receipt).
 */
data class ExpenseItem(
    val id: Long = 0L,
    val expenseId: Long,
    val name: String,
    val quantity: Int = 1,
    val unitPrice: Double,
    val totalPrice: Double = quantity * unitPrice,
    val type: ExpenseType = ExpenseType.PRODUCT
)
