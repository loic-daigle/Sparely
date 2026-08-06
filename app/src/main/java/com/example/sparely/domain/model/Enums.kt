package com.example.sparely.domain.model

/**
 * Enumerations used across the Sparely domain layer.
 */
enum class RiskLevel {
    CONSERVATIVE,
    BALANCED,
    AGGRESSIVE
}

enum class SavingsCategory {
    EMERGENCY,
    INVESTMENT,
    FUN
}

enum class BankSyncProvider {
    PLAID_SANDBOX,
    MOCK
}

enum class SmartAllocationMode {
    MANUAL,
    GUIDED,
    AUTOMATIC
}

enum class ExpenseCategory {
    GROCERIES,
    DINING,
    TRANSPORTATION,
    ENTERTAINMENT,
    UTILITIES,
    HEALTH,
    EDUCATION,
    SHOPPING,
    TRAVEL,
    OTHER
}

enum class ExpenseType {
    PRODUCT,
    SERVICE
}

enum class IncomeCategory {
    SALARY,
    FREELANCE,
    GIFT,
    INVESTMENT,
    OTHER
}

enum class AlertType {
    INFO,
    WARNING,
    SUCCESS
}

enum class VaultPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class VaultType {
    GOAL,           // Specific savings goals (car, house, vacation, etc.)
    EMERGENCY,      // Emergency fund
    INVESTMENT,     // Investment vault
    SHORT_TERM,     // Legacy: short-term savings
    LONG_TERM,      // Legacy: long-term savings  
    PASSIVE_INVESTMENT,  // Legacy: passive investment
    HIGH_YIELD_SAVINGS   // High-yield savings account for earning interest
}


enum class VaultAllocationMode {
    DYNAMIC_AUTO,
    MANUAL
}

enum class AutoDepositFrequency {
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    QUARTERLY
}

enum class IncomeTrackingMode {
    MANUAL_PER_PAYCHECK,
    SCHEDULED,
    HYBRID
}

enum class PayInterval {
    WEEKLY,
    BIWEEKLY,
    SEMI_MONTHLY,
    MONTHLY,
    CUSTOM
}

enum class ExpenseHistoryRetention(val label: String, val months: Int?) {
    ONE_MONTH("1 Month", 1),
    SIX_MONTHS("6 Months", 6),
    ONE_YEAR("1 Year", 12),
    INDEFINITELY("Indefinitely", null);

    fun displayText(): String = label
}

fun VaultType.displayName(): String = when (this) {
    VaultType.GOAL -> "Goal"
    VaultType.EMERGENCY -> "Emergency Fund"
    VaultType.INVESTMENT -> "Investment"
    VaultType.SHORT_TERM -> "Short-Term"
    VaultType.LONG_TERM -> "Long-Term"
    VaultType.PASSIVE_INVESTMENT -> "Passive Investment"
    VaultType.HIGH_YIELD_SAVINGS -> "High-Yield Savings"
}


fun VaultPriority.displayName(): String = when (this) {
    VaultPriority.LOW -> "Low"
    VaultPriority.MEDIUM -> "Medium"
    VaultPriority.HIGH -> "High"
    VaultPriority.CRITICAL -> "Critical"
}

fun ExpenseCategory.displayName(): String = when (this) {
    ExpenseCategory.GROCERIES -> "Groceries"
    ExpenseCategory.DINING -> "Dining"
    ExpenseCategory.TRANSPORTATION -> "Transportation"
    ExpenseCategory.ENTERTAINMENT -> "Entertainment"
    ExpenseCategory.UTILITIES -> "Utilities"
    ExpenseCategory.HEALTH -> "Health"
    ExpenseCategory.EDUCATION -> "Education"
    ExpenseCategory.SHOPPING -> "Shopping"
    ExpenseCategory.TRAVEL -> "Travel"
    ExpenseCategory.OTHER -> "Other"
}

fun IncomeCategory.displayName(): String = when (this) {
    IncomeCategory.SALARY -> "Salary"
    IncomeCategory.FREELANCE -> "Freelance"
    IncomeCategory.GIFT -> "Gift"
    IncomeCategory.INVESTMENT -> "Investment"
    IncomeCategory.OTHER -> "Other"
}

fun ExpenseType.displayName(): String = when (this) {
    ExpenseType.PRODUCT -> "Product"
    ExpenseType.SERVICE -> "Service"
}
