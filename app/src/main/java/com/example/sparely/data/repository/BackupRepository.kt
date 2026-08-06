package com.example.sparely.data.repository

import com.example.sparely.data.preferences.UserPreferencesRepository
import com.example.sparely.domain.model.BackupData
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.example.sparely.data.local.toDomain
import com.example.sparely.data.local.toEntity

import com.google.gson.GsonBuilder
import com.example.sparely.data.utils.LocalDateAdapter
import com.example.sparely.data.utils.LocalDateTimeAdapter
import com.example.sparely.data.utils.InstantAdapter
import com.example.sparely.data.utils.YearMonthAdapter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.YearMonth

class BackupRepository(
    private val savingsRepository: SavingsRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val gson: Gson = GsonBuilder()
        .registerTypeHierarchyAdapter(LocalDate::class.java, LocalDateAdapter())
        .registerTypeHierarchyAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        .registerTypeHierarchyAdapter(Instant::class.java, InstantAdapter())
        .registerTypeHierarchyAdapter(YearMonth::class.java, YearMonthAdapter())
        .serializeNulls() // Ensure we don't skip null fields which might be meaningful or needed for structural integrity
        .create()
) {

    suspend fun exportData(): String = withContext(Dispatchers.IO) {
        val settings = preferencesRepository.settingsFlow.first()
        val vaults = savingsRepository.observeSmartVaults().first()
        val budgets = savingsRepository.observeBudgets().first()
        val expenses = savingsRepository.observeExpenses().first()
        val savingsAccounts = savingsRepository.observeSavingsAccounts().first()
        val recurring = savingsRepository.observeRecurringExpenses().first()
        val transactions = savingsRepository.observeMainAccountTransactions().first()
        val frozenFunds = savingsRepository.observeFrozenFunds().first()

        // Ensure we fetch lists, defaulting to empty if null/error (though repository methods shouldn't return null)
        val challenges = savingsRepository.getAllChallenges()
        val achievements = savingsRepository.getAllAchievements()
        val transfers = savingsRepository.getAllTransfers()
        val vaultContributions = savingsRepository.getAllVaultContributions()
        val vaultAdjustments = savingsRepository.getAllVaultAdjustments()
        val allocationHistory = savingsRepository.getAllAllocationHistory()
        val mainAccountBalance = savingsRepository.getLatestMainAccountBalance()
        val stores = savingsRepository.observeStores().first()
        val paymentMethods = savingsRepository.observePaymentMethods().first()
        val creditCardPayments = savingsRepository.getAllCreditCardPayments()
        val expenseItems = savingsRepository.getAllExpenseItems()

        // New: Assets, wishlists, and refunds
        val assets = savingsRepository.getActiveAssets()
        val assetExpenseLinks = savingsRepository.getAllAssetExpenseLinks()
        val wishlists = savingsRepository.getActiveWishlists()
        val wishlistSavings = savingsRepository.getAllWishlistSavings()
        val expenseRefunds = savingsRepository.getAllExpenseRefunds()

        val backup = BackupData(
            settings = settings,
            vaults = vaults,
            budgets = budgets,
            expenses = expenses,
            savingsAccounts = savingsAccounts,
            recurringExpenses = recurring,
            transactions = transactions,
            frozenFunds = frozenFunds,
            challenges = challenges,
            achievements = achievements,
            transfers = transfers,
            vaultContributions = vaultContributions,
            vaultAdjustments = vaultAdjustments,
            allocationHistory = allocationHistory,
            stores = stores,
            mainAccountBalance = mainAccountBalance,
            paymentMethods = paymentMethods,
            creditCardPayments = creditCardPayments,
            expenseItems = expenseItems,
            assets = assets,
            assetExpenseLinks = assetExpenseLinks,
            wishlists = wishlists,
            wishlistSavings = wishlistSavings,
            expenseRefunds = expenseRefunds
        )

        gson.toJson(backup)
    }

    suspend fun restoreData(json: String) = withContext(Dispatchers.IO) {
        android.util.Log.d("BackupRepository", "Starting restore...")
        
        val backup = try {
            gson.fromJson(json, BackupData::class.java)
        } catch (e: Exception) {
            android.util.Log.e("BackupRepository", "Failed to parse backup JSON", e)
            throw e
        }
        
        android.util.Log.d("BackupRepository", "Parsed backup: ${backup.expenses.size} expenses, ${backup.vaults.size} vaults, ${backup.transactions.size} transactions")
        
        savingsRepository.runInTransaction {
            // 1. Clear existing data
            try {
                savingsRepository.clearExpenses()
                savingsRepository.clearSmartVaults()
                savingsRepository.clearBudgets()
                savingsRepository.clearTransfers()
                savingsRepository.clearRecurringExpenses()
                savingsRepository.clearMainAccountTransactions()
                savingsRepository.clearFrozenFunds()
                savingsRepository.clearChallenges()
                savingsRepository.clearAchievements()
                savingsRepository.clearVaultData()
                savingsRepository.clearAllocationHistory()
                savingsRepository.clearStores()
                savingsRepository.clearPaymentMethods()
                savingsRepository.clearCreditCardPayments()
                savingsRepository.clearExpenseItems()
                // New: Clear assets, wishlists, and refunds
                savingsRepository.clearAssets()
                savingsRepository.clearAssetExpenseLinks()
                savingsRepository.clearWishlists()
                savingsRepository.clearWishlistSavings()
                savingsRepository.clearExpenseRefunds()
                android.util.Log.d("BackupRepository", "Cleared existing data")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to clear existing data", e)
                throw e
            }
            
            // 2. Restore Data
            // Restore Settings
            try {
                val s = backup.settings
                preferencesRepository.updateMonthlyIncome(s.monthlyIncome)
                preferencesRepository.updateAge(s.age)
                preferencesRepository.updateRiskLevel(s.riskLevel)
                preferencesRepository.updateEducationStatus(s.educationStatus)
                preferencesRepository.updateEmploymentStatus(s.employmentStatus)
                preferencesRepository.updateLivingSituation(s.livingSituation)
                preferencesRepository.updateOccupation(s.occupation)
                
                val balanceToRestore = backup.mainAccountBalance ?: s.mainAccountBalance
                preferencesRepository.updateMainAccountBalance(balanceToRestore)
                
                preferencesRepository.updateSavingsAccountBalance(s.savingsAccountBalance)
                preferencesRepository.updateHasDebts(s.hasDebts)
                preferencesRepository.updateEmergencyFund(s.currentEmergencyFund)
                preferencesRepository.updateSubscriptionTotal(s.subscriptionTotal)
                preferencesRepository.updatePrimaryGoal(s.primaryGoal)
                preferencesRepository.updateDisplayName(s.displayName)
                preferencesRepository.updateBirthday(s.birthday)
                s.joinedDate?.let { preferencesRepository.setJoinedDate(it) }
                s.brandfetchClientId?.let { preferencesRepository.updateBrandfetchClientId(it) }
                preferencesRepository.updateExpenseHistoryRetention(s.expenseHistoryRetention)
                preferencesRepository.setOnboardingCompleted(true)
                android.util.Log.d("BackupRepository", "Restored settings")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore settings", e)
                throw e
            }
            
            // Restore Entities in order
            // 1. Accounts & Vaults (parents)
            try {
                backup.savingsAccounts.forEach { savingsRepository.upsertSavingsAccount(it) }
                backup.vaults.forEach { savingsRepository.upsertSmartVault(it) }
                android.util.Log.d("BackupRepository", "Restored ${backup.vaults.size} vaults")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore vaults", e)
                throw e
            }
            
            // 2. Budgets & Recurring
            try {
                backup.budgets.forEach { savingsRepository.upsertBudget(it) }
                backup.recurringExpenses.forEach { savingsRepository.upsertRecurringExpense(it) }
                android.util.Log.d("BackupRepository", "Restored budgets and recurring")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore budgets/recurring", e)
                throw e
            }
            
            // 3. Transactions & History
            try {
                backup.transactions.forEach { savingsRepository.insertMainAccountTransaction(it) }
                android.util.Log.d("BackupRepository", "Restored ${backup.transactions.size} transactions")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore transactions", e)
                throw e
            }
            
            try {
                backup.frozenFunds.forEach { savingsRepository.upsertFrozenFund(it) }
                android.util.Log.d("BackupRepository", "Restored frozen funds")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore frozen funds", e)
                throw e
            }
            
            // Restore expenses
            // Handle corrupted dates from older backups (date: {} becomes 1970-01-01)
            val backupDate = java.time.Instant.ofEpochMilli(backup.timestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            val epochDate = LocalDate.of(1970, 1, 1)
            
            try {
                android.util.Log.d("BackupRepository", "Attempting to restore ${backup.expenses.size} expenses... (backup date: $backupDate)")
                backup.expenses.forEachIndexed { index, domainExpense ->
                    try {
                        // Fix corrupted epoch dates by using the backup timestamp
                        val fixedExpense = if (domainExpense.date == epochDate) {
                            domainExpense.copy(date = backupDate)
                        } else {
                            domainExpense
                        }
                        val entity = fixedExpense.toEntity()
                        savingsRepository.upsertExpense(entity)
                    } catch (e: Exception) {
                        android.util.Log.e("BackupRepository", "Failed to restore expense $index: ${domainExpense.description}", e)
                    }
                }
                android.util.Log.d("BackupRepository", "Restored expenses")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed in expense restoration block", e)
                throw e
            }
            
            // 4. Missing pieces (these fields may be null in older backups)
            try {
                savingsRepository.insertTransfers(backup.transfers.orEmpty())
                backup.challenges.orEmpty().forEach { savingsRepository.upsertSavingsChallenge(it) }
                savingsRepository.insertAchievements(backup.achievements.orEmpty())
                android.util.Log.d("BackupRepository", "Restored transfers, challenges, achievements")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore transfers/challenges/achievements", e)
                throw e
            }
            
            // 5. Vault History (may be null in older backups)
            try {
                savingsRepository.insertVaultContributions(backup.vaultContributions.orEmpty())
                savingsRepository.insertVaultAdjustments(backup.vaultAdjustments.orEmpty())
                savingsRepository.insertAllocationHistory(backup.allocationHistory.orEmpty())
                android.util.Log.d("BackupRepository", "Restored vault history")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore vault history", e)
                throw e
            }
            
            // 6. Stores (may be null in older backups)
            try {
                backup.stores.orEmpty().forEach { savingsRepository.insertStore(it) }
                android.util.Log.d("BackupRepository", "Restored ${backup.stores?.size ?: 0} stores")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore stores", e)
                throw e
            }
            
            // 7. Payment Methods and Credit Card Payments (may be null in older backups)
            try {
                backup.paymentMethods.orEmpty().forEach { savingsRepository.insertPaymentMethod(it) }
                backup.creditCardPayments.orEmpty().forEach { savingsRepository.insertCreditCardPayment(it) }
                android.util.Log.d("BackupRepository", "Restored ${backup.paymentMethods?.size ?: 0} payment methods, ${backup.creditCardPayments?.size ?: 0} credit card payments")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore payment methods/credit card payments", e)
                throw e
            }
            
            // 8. Expense Items (may be null in older backups)
            try {
                savingsRepository.insertExpenseItems(backup.expenseItems.orEmpty())
                android.util.Log.d("BackupRepository", "Restored ${backup.expenseItems?.size ?: 0} expense items")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore expense items", e)
                throw e
            }

            // 9. Assets & Asset Links (may be null in older backups)
            try {
                backup.assets.orEmpty().forEach { savingsRepository.upsertAsset(it) }
                backup.assetExpenseLinks.orEmpty().forEach { savingsRepository.insertAssetExpenseLink(it) }
                android.util.Log.d("BackupRepository", "Restored ${backup.assets?.size ?: 0} assets, ${backup.assetExpenseLinks?.size ?: 0} asset links")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore assets/links", e)
                throw e
            }

            // 10. Wishlists & Wishlist Savings (may be null in older backups)
            try {
                backup.wishlists.orEmpty().forEach { savingsRepository.upsertWishlist(it) }
                backup.wishlistSavings.orEmpty().forEach { savingsRepository.insertWishlistSavings(it) }
                android.util.Log.d("BackupRepository", "Restored ${backup.wishlists?.size ?: 0} wishlists, ${backup.wishlistSavings?.size ?: 0} wishlist savings")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore wishlists", e)
                throw e
            }

            // 11. Expense Refunds (may be null in older backups)
            try {
                backup.expenseRefunds.orEmpty().forEach { savingsRepository.recordRefund(it.expenseId, it.refundedAmount, it.refundDate, it.refundMethod, it.reason, it.refundedItemIds) }
                android.util.Log.d("BackupRepository", "Restored ${backup.expenseRefunds?.size ?: 0} expense refunds")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore expense refunds", e)
                throw e
            }

            android.util.Log.d("BackupRepository", "Restore complete!")
        }
    }
}
