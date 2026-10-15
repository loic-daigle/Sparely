package com.example.sparely.domain.model

import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.SavingsAccount
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.model.SavingsChallenge
import com.example.sparely.domain.model.Achievement
import com.example.sparely.domain.model.SavingsTransfer
import com.example.sparely.domain.model.VaultContribution
import com.example.sparely.domain.model.VaultBalanceAdjustment

/**
 * Data Transfer Object for Full Backup & Restore.
 * Fields added later are nullable to support backwards compatibility with older backup files.
 */
data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val settings: SparelySettings,
    val vaults: List<SmartVault> = emptyList(),
    val budgets: List<com.example.sparely.domain.model.CategoryBudget> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val savingsAccounts: List<SavingsAccount> = emptyList(),
    val recurringExpenses: List<RecurringExpense> = emptyList(),
    val transactions: List<com.example.sparely.domain.model.MainAccountTransaction> = emptyList(),
    val frozenFunds: List<com.example.sparely.data.local.FrozenFundEntity> = emptyList(),
    // Nullable fields for backwards compatibility with older backups
    val challenges: List<SavingsChallenge>? = null,
    val achievements: List<Achievement>? = null,
    val transfers: List<SavingsTransfer>? = null,
    val vaultContributions: List<VaultContribution>? = null,
    val vaultAdjustments: List<VaultBalanceAdjustment>? = null,
    val allocationHistory: List<com.example.sparely.data.local.AllocationHistoryEntity>? = null,
    val stores: List<Store>? = null,
    val mainAccountBalance: Double? = null,
    val paymentMethods: List<PaymentMethod>? = null,
    val creditCardPayments: List<CreditCardPayment>? = null,
    val expenseItems: List<ExpenseItem>? = null,
    // New fields for assets, wishlists, and refunds
    val assets: List<com.example.sparely.domain.model.Asset>? = null,
    val assetExpenseLinks: List<com.example.sparely.domain.model.AssetExpenseLink>? = null,
    val wishlists: List<com.example.sparely.domain.model.Wishlist>? = null,
    val wishlistSavings: List<com.example.sparely.data.local.WishlistSavingsEntity>? = null,
    val expenseRefunds: List<com.example.sparely.domain.model.ExpenseRefund>? = null,
    // Full-fidelity additions: the remaining tables, and every setting as stored
    val savingsAccountTransactions: List<com.example.sparely.data.local.SavingsAccountTransactionEntity>? = null,
    val recurringExpensePayments: List<com.example.sparely.data.local.RecurringExpensePaidEntity>? = null,
    val pendingVariableRecurringExpenses: List<com.example.sparely.data.local.PendingVariableRecurringExpenseEntity>? = null,
    val assistantActions: List<com.example.sparely.data.local.AssistantActionEntity>? = null,
    val preferences: List<BackupPreference>? = null
)

/**
 * One stored setting. Values are kept as text so JSON number handling can't change their type
 * (Gson would read a Long back as a Double); [values] holds string sets.
 */
data class BackupPreference(
    val key: String,
    val type: String,
    val value: String? = null,
    val values: List<String>? = null
) {
    companion object {
        const val BOOLEAN = "boolean"
        const val INT = "int"
        const val LONG = "long"
        const val FLOAT = "float"
        const val DOUBLE = "double"
        const val STRING = "string"
        const val STRING_SET = "string_set"
    }
}

