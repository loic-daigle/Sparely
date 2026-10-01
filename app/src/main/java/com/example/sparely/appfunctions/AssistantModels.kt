package com.example.sparely.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/*
 * Data returned to AI assistants. Amounts are in the user's currency
 * (see each response's currencyCode); dates are ISO-8601 strings (YYYY-MM-DD).
 */

/** Total spending over a date range, broken down by category. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class SpendingSummary(
    /** First day of the range, inclusive (YYYY-MM-DD). */
    val startDate: String,
    /** Last day of the range, inclusive (YYYY-MM-DD). */
    val endDate: String,
    /** ISO 4217 code of the currency all amounts are in, e.g. "CAD". */
    val currencyCode: String,
    /** Total spent in the range, net of refunds. */
    val totalSpent: Double,
    /** Number of expenses recorded in the range. */
    val expenseCount: Int,
    /** Spending per category, largest first. Categories with no spending are omitted. */
    val categories: List<CategorySpending>,
)

/** Spending in one category. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CategorySpending(
    /** Expense category, e.g. "GROCERIES". */
    val category: String,
    /** Amount spent in this category, net of refunds. */
    val totalSpent: Double,
    /** Number of expenses in this category. */
    val expenseCount: Int,
)

/** One recorded expense. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ExpenseRecord(
    /** Unique ID of the expense in Sparely. */
    val id: Long,
    /** What the expense was for, as entered by the user. */
    val description: String,
    /** Amount originally paid. */
    val amount: Double,
    /** Amount refunded so far; 0 when nothing was refunded. */
    val refundedAmount: Double,
    /** Expense category, e.g. "DINING". */
    val category: String,
    /** Date of the expense (YYYY-MM-DD). */
    val date: String,
    /** True when the expense was logged from a recurring bill or subscription. */
    val isRecurring: Boolean,
    /** Free-form notes the user attached, if any. */
    val notes: String?,
)

/** Expenses matching a search. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ExpenseSearchResult(
    /** ISO 4217 code of the currency all amounts are in. */
    val currencyCode: String,
    /** Matching expenses, most recent first. */
    val expenses: List<ExpenseRecord>,
    /** True when more expenses matched than were returned; narrow the search to see the rest. */
    val hasMore: Boolean,
)

/** Budget status for one month. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class BudgetOverview(
    /** The month these budgets apply to (YYYY-MM). */
    val month: String,
    /** ISO 4217 code of the currency all amounts are in. */
    val currencyCode: String,
    /** Sum of all category limits. */
    val totalBudget: Double,
    /** Sum spent across budgeted categories. */
    val totalSpent: Double,
    /** Overall health: HEALTHY, WARNING, CRITICAL or OVER_BUDGET. */
    val overallStatus: String,
    /** One entry per budgeted category. Empty when the user has no budgets for this month. */
    val categories: List<BudgetCategoryStatus>,
)

/** Budget status of one category. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class BudgetCategoryStatus(
    /** Expense category, e.g. "DINING". */
    val category: String,
    /** Monthly limit the user set. */
    val limit: Double,
    /** Amount spent this month in the category. */
    val spent: Double,
    /** Amount left before reaching the limit; 0 when over budget. */
    val remaining: Double,
    /** Share of the limit used, where 1.0 means 100%. */
    val fractionUsed: Double,
    /** HEALTHY (under 70%), WARNING (70-90%), CRITICAL (90-100%) or OVER_BUDGET. */
    val status: String,
)

/** A savings vault (a goal-based pot of money). */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class VaultSummary(
    /** Unique ID of the vault in Sparely. */
    val id: Long,
    /** Name the user gave the vault, e.g. "Summer trip". */
    val name: String,
    /** Money currently in the vault. */
    val currentBalance: Double,
    /** Savings goal for the vault. */
    val targetAmount: Double,
    /** Progress toward the goal, from 0.0 to 1.0. */
    val progress: Double,
    /** Date the user wants to reach the goal by (YYYY-MM-DD), if set. */
    val targetDate: String?,
    /** Monthly amount needed to reach the goal on time, if known. */
    val monthlyNeed: Double?,
)

/** Active savings vaults. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class VaultList(
    /** ISO 4217 code of the currency all amounts are in. */
    val currencyCode: String,
    /** Active (non-archived) vaults. */
    val vaults: List<VaultSummary>,
)

/** Where the user's money currently sits. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AccountBalances(
    /** ISO 4217 code of the currency all amounts are in. */
    val currencyCode: String,
    /** Balance of the main (everyday) account. Can be negative when overdrawn. */
    val mainAccountBalance: Double,
    /** Combined balance of all active vaults. */
    val totalInVaults: Double,
    /** Number of active vaults included in totalInVaults. */
    val vaultCount: Int,
)

/** A bill or subscription that is coming due. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class UpcomingBill(
    /** Unique ID of the recurring expense in Sparely. */
    val id: Long,
    /** Name of the bill or subscription. */
    val description: String,
    /** Expected amount. For bills whose amount varies, this is a prediction. */
    val amount: Double,
    /** True when the amount varies from one payment to the next. */
    val isVariableAmount: Boolean,
    /** Expense category, e.g. "UTILITIES". */
    val category: String,
    /** How often it repeats, e.g. "MONTHLY". */
    val frequency: String,
    /** Next due date (YYYY-MM-DD). */
    val dueDate: String,
    /** Days from today until it is due; 0 means today. */
    val daysUntilDue: Int,
)

/** Bills and subscriptions coming due. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class UpcomingBillList(
    /** ISO 4217 code of the currency all amounts are in. */
    val currencyCode: String,
    /** Total expected across all listed bills. */
    val totalDue: Double,
    /** Upcoming bills, soonest first. */
    val bills: List<UpcomingBill>,
)

/** An item the user wants to buy and is saving for. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class WishlistItem(
    /** Unique ID of the wishlist item in Sparely. */
    val id: Long,
    /** What the user wants. */
    val description: String,
    /** Price or savings goal for the item. */
    val targetAmount: Double,
    /** Amount saved toward it so far. */
    val currentSavings: Double,
    /**
     * True while the item is in its cooling-off period. Sparely asks users to wait a few days
     * before buying, to avoid impulse purchases.
     */
    val isOnCooldown: Boolean,
    /** Date the cooling-off period ends (YYYY-MM-DD), if one was set. */
    val cooldownEndsDate: String?,
    /** Category, e.g. "GADGET", if set. */
    val category: String?,
)

/** The user's active wishlist. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class WishlistOverview(
    /** ISO 4217 code of the currency all amounts are in. */
    val currencyCode: String,
    /** Active wishlist items, highest priority first. */
    val items: List<WishlistItem>,
)

/** Confirmation of an entry an assistant added. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class RecordedEntry(
    /** ID of the new entry in Sparely (for an expense, usable with searchExpenses). */
    val id: Long,
    /** ISO 4217 code of the currency the amount was recorded in. */
    val currencyCode: String,
    /** What was recorded, to confirm back to the user. */
    val message: String,
)
