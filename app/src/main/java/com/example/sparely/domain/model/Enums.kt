package com.example.sparely.domain.model

import com.sparely.app.R

import com.example.sparely.AppStrings

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

/** How much an expense can be cut when money gets tight. Lower ordinal = harder to cut. */
enum class Necessity {
    ESSENTIAL,
    IMPORTANT,
    DISCRETIONARY
}

fun ExpenseCategory.defaultNecessity(): Necessity = when (this) {
    ExpenseCategory.GROCERIES,
    ExpenseCategory.UTILITIES,
    ExpenseCategory.HEALTH -> Necessity.ESSENTIAL
    ExpenseCategory.TRANSPORTATION,
    ExpenseCategory.EDUCATION -> Necessity.IMPORTANT
    ExpenseCategory.DINING,
    ExpenseCategory.ENTERTAINMENT,
    ExpenseCategory.SHOPPING,
    ExpenseCategory.TRAVEL,
    ExpenseCategory.OTHER -> Necessity.DISCRETIONARY
}

fun Necessity.displayName(): String = when (this) {
    Necessity.ESSENTIAL -> AppStrings.get(R.string.necessity_essential, "Essential")
    Necessity.IMPORTANT -> AppStrings.get(R.string.necessity_important, "Important")
    Necessity.DISCRETIONARY -> AppStrings.get(R.string.necessity_discretionary, "Nice to have")
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
    VaultType.GOAL -> AppStrings.get(R.string.vault_type_goal, "Goal")
    VaultType.EMERGENCY -> AppStrings.get(R.string.vault_type_emergency, "Emergency Fund")
    VaultType.INVESTMENT -> AppStrings.get(R.string.vault_type_investment, "Investment")
    VaultType.SHORT_TERM -> AppStrings.get(R.string.vault_type_short_term, "Short-Term")
    VaultType.LONG_TERM -> AppStrings.get(R.string.vault_type_long_term, "Long-Term")
    VaultType.PASSIVE_INVESTMENT -> AppStrings.get(R.string.vault_type_passive_investment, "Passive Investment")
    VaultType.HIGH_YIELD_SAVINGS -> AppStrings.get(R.string.vault_type_high_yield, "High-Yield Savings")
}


fun VaultPriority.displayName(): String = when (this) {
    VaultPriority.LOW -> AppStrings.get(R.string.vault_priority_low, "Low")
    VaultPriority.MEDIUM -> AppStrings.get(R.string.vault_priority_medium, "Medium")
    VaultPriority.HIGH -> AppStrings.get(R.string.vault_priority_high, "High")
    VaultPriority.CRITICAL -> AppStrings.get(R.string.vault_priority_critical, "Critical")
}

fun ExpenseCategory.displayName(): String = when (this) {
    ExpenseCategory.GROCERIES -> AppStrings.get(R.string.category_groceries, "Groceries")
    ExpenseCategory.DINING -> AppStrings.get(R.string.category_dining, "Dining")
    ExpenseCategory.TRANSPORTATION -> AppStrings.get(R.string.category_transportation, "Transportation")
    ExpenseCategory.ENTERTAINMENT -> AppStrings.get(R.string.category_entertainment, "Entertainment")
    ExpenseCategory.UTILITIES -> AppStrings.get(R.string.category_utilities, "Utilities")
    ExpenseCategory.HEALTH -> AppStrings.get(R.string.category_health, "Health")
    ExpenseCategory.EDUCATION -> AppStrings.get(R.string.category_education, "Education")
    ExpenseCategory.SHOPPING -> AppStrings.get(R.string.category_shopping, "Shopping")
    ExpenseCategory.TRAVEL -> AppStrings.get(R.string.category_travel, "Travel")
    ExpenseCategory.OTHER -> AppStrings.get(R.string.category_other, "Other")
}

fun IncomeCategory.displayName(): String = when (this) {
    IncomeCategory.SALARY -> AppStrings.get(R.string.income_category_salary, "Salary")
    IncomeCategory.FREELANCE -> AppStrings.get(R.string.income_category_freelance, "Freelance")
    IncomeCategory.GIFT -> AppStrings.get(R.string.income_category_gift, "Gift")
    IncomeCategory.INVESTMENT -> AppStrings.get(R.string.income_category_investment, "Investment")
    IncomeCategory.OTHER -> AppStrings.get(R.string.income_category_other, "Other")
}

fun ExpenseType.displayName(): String = when (this) {
    ExpenseType.PRODUCT -> AppStrings.get(R.string.expense_type_product, "Product")
    ExpenseType.SERVICE -> AppStrings.get(R.string.expense_type_service, "Service")
}
