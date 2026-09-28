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
import com.example.sparely.domain.model.EducationStatus
import com.example.sparely.domain.model.EmploymentStatus
import com.example.sparely.domain.model.ExpenseHistoryRetention
import com.example.sparely.domain.model.LivingSituation
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SparelySettings

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

    companion object {
        // Bump alongside BackupData.version when the backup schema changes in a way older
        // app versions can't restore; restoreData() rejects any backup newer than this.
        const val CURRENT_BACKUP_VERSION = 1
    }

    suspend fun exportData(): String = withContext(Dispatchers.IO) {
        val settings = preferencesRepository.settingsFlow.first()
        val vaults = savingsRepository.observeAllSmartVaults().first()
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
        val assets = savingsRepository.getAllAssetsIncludingArchived()
        val assetExpenseLinks = savingsRepository.getAllAssetExpenseLinks()
        val wishlists = savingsRepository.getAllWishlistsIncludingArchived()
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
        
        val parsed: BackupData? = try {
            gson.fromJson(json, BackupData::class.java)
        } catch (e: Exception) {
            android.util.Log.e("BackupRepository", "Failed to parse backup JSON", e)
            throw IllegalArgumentException("The selected file is not a valid Sparely backup.", e)
        }
        // Gson bypasses Kotlin constructors, so fields missing from the file come back as null
        // even when declared non-null. Normalise everything before touching the database.
        val backup = sanitizeBackup(parsed)
        
        android.util.Log.d("BackupRepository", "Parsed backup: ${backup.expenses.size} expenses, ${backup.vaults.size} vaults, ${backup.transactions.size} transactions")

        if (backup.version > CURRENT_BACKUP_VERSION) {
            val message = "Backup file is from a newer app version (backup v${backup.version}, this app supports up to v$CURRENT_BACKUP_VERSION). Update the app before restoring."
            android.util.Log.e("BackupRepository", message)
            throw IllegalStateException(message)
        }

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
            
            // 2. Restore Entities in order (settings are restored last - see below - so a
            // failure partway through entity restoration rolls back cleanly without having
            // already overwritten DataStore settings, which Room's transaction can't undo)
            // 1. Accounts & Vaults (parents)
            try {
                backup.savingsAccounts.orEmpty().restoreEach("savingsAccounts") { savingsRepository.upsertSavingsAccount(it) }
                backup.vaults.orEmpty().restoreEach("vaults") { savingsRepository.upsertSmartVault(it) }
                android.util.Log.d("BackupRepository", "Restored ${backup.vaults.size} vaults")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore vaults", e)
                throw e
            }
            
            // 2. Budgets & Recurring
            try {
                backup.budgets.orEmpty().restoreEach("budgets") { savingsRepository.upsertBudget(it) }
                backup.recurringExpenses.orEmpty().restoreEach("recurringExpenses") { savingsRepository.upsertRecurringExpense(it) }
                android.util.Log.d("BackupRepository", "Restored budgets and recurring")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore budgets/recurring", e)
                throw e
            }
            
            // 3. Transactions & History
            try {
                backup.transactions.orEmpty().restoreEach("transactions") { savingsRepository.insertMainAccountTransaction(it) }
                android.util.Log.d("BackupRepository", "Restored ${backup.transactions.size} transactions")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore transactions", e)
                throw e
            }
            
            try {
                backup.frozenFunds.orEmpty().restoreEach("frozenFunds") { savingsRepository.upsertFrozenFund(it) }
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
                backup.challenges.orEmpty().restoreEach("challenges") { savingsRepository.upsertSavingsChallenge(it) }
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
                backup.stores.orEmpty().restoreEach("stores") { savingsRepository.insertStore(it) }
                android.util.Log.d("BackupRepository", "Restored ${backup.stores?.size ?: 0} stores")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore stores", e)
                throw e
            }
            
            // 7. Payment Methods and Credit Card Payments (may be null in older backups)
            try {
                backup.paymentMethods.orEmpty().restoreEach("paymentMethods") { savingsRepository.insertPaymentMethod(it) }
                backup.creditCardPayments.orEmpty().restoreEach("creditCardPayments") { savingsRepository.insertCreditCardPayment(it) }
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
                backup.assets.orEmpty().restoreEach("assets") { savingsRepository.upsertAsset(it) }
                backup.assetExpenseLinks.orEmpty().restoreEach("assetExpenseLinks") { savingsRepository.insertAssetExpenseLink(it) }
                android.util.Log.d("BackupRepository", "Restored ${backup.assets?.size ?: 0} assets, ${backup.assetExpenseLinks?.size ?: 0} asset links")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore assets/links", e)
                throw e
            }

            // 10. Wishlists & Wishlist Savings (may be null in older backups)
            try {
                backup.wishlists.orEmpty().restoreEach("wishlists") { savingsRepository.upsertWishlist(it) }
                backup.wishlistSavings.orEmpty().restoreEach("wishlistSavings") { savingsRepository.insertWishlistSavings(it) }
                android.util.Log.d("BackupRepository", "Restored ${backup.wishlists?.size ?: 0} wishlists, ${backup.wishlistSavings?.size ?: 0} wishlist savings")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore wishlists", e)
                throw e
            }

            // 11. Expense Refunds (may be null in older backups)
            try {
                backup.expenseRefunds.orEmpty().restoreEach("expenseRefunds") { savingsRepository.recordRefund(it.expenseId, it.refundedAmount, it.refundDate, it.refundMethod, it.reason, it.refundedItemIds) }
                android.util.Log.d("BackupRepository", "Restored ${backup.expenseRefunds?.size ?: 0} expense refunds")
            } catch (e: Exception) {
                android.util.Log.e("BackupRepository", "Failed to restore expense refunds", e)
                throw e
            }

            // 12. Restore Settings - deliberately last: this is the only part of the restore
            // that touches DataStore rather than Room, so it isn't covered by this transaction's
            // rollback. Running it after every Room write succeeds means a failure anywhere above
            // leaves settings untouched instead of ending up out of sync with reverted entities.
            try {
                val s = sanitizeSettings(backup.settings)
                preferencesRepository.updateMonthlyIncome(s.monthlyIncome)
                preferencesRepository.updateAge(s.age)
                preferencesRepository.updateRiskLevel(s.riskLevel)
                preferencesRepository.updateEducationStatus(s.educationStatus)
                preferencesRepository.updateEmploymentStatus(s.employmentStatus)
                preferencesRepository.updateLivingSituation(s.livingSituation)
                preferencesRepository.updateOccupation(s.occupation)

                val balanceToRestore = backup.mainAccountBalance?.takeIf { it.isFinite() } ?: s.mainAccountBalance
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

            android.util.Log.d("BackupRepository", "Restore complete!")
        }
    }

    /**
     * Restores items one by one so a single malformed record (e.g. a row written by an older
     * app version) is skipped and logged instead of aborting the whole restore.
     */
    private inline fun <T> List<T>.restoreEach(label: String, block: (T) -> Unit) {
        var failures = 0
        forEach { item ->
            try {
                block(item)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                android.util.Log.w("BackupRepository", "Skipping invalid $label record", e)
            }
        }
        if (failures > 0) {
            android.util.Log.w("BackupRepository", "Skipped $failures of $size $label records")
        }
    }

    /**
     * Replaces every null collection / element produced by Gson with safe defaults and rejects
     * files that are clearly not Sparely backups.
     */
    @Suppress("USELESS_CAST", "SENSELESS_COMPARISON")
    private fun sanitizeBackup(parsed: BackupData?): BackupData {
        if (parsed == null || (parsed.settings as SparelySettings?) == null) {
            throw IllegalArgumentException("The selected file is not a valid Sparely backup.")
        }
        fun <T> List<T>?.clean(): List<T> = (this as List<T?>?).orEmpty().filterNotNull()
        return parsed.copy(
            vaults = parsed.vaults.clean(),
            budgets = parsed.budgets.clean(),
            expenses = parsed.expenses.clean().filter { (it.date as LocalDate?) != null && it.amount.isFinite() },
            savingsAccounts = parsed.savingsAccounts.clean(),
            recurringExpenses = parsed.recurringExpenses.clean(),
            transactions = parsed.transactions.clean(),
            frozenFunds = parsed.frozenFunds.clean(),
            challenges = parsed.challenges.clean(),
            achievements = parsed.achievements.clean(),
            transfers = parsed.transfers.clean(),
            vaultContributions = parsed.vaultContributions.clean(),
            vaultAdjustments = parsed.vaultAdjustments.clean(),
            allocationHistory = parsed.allocationHistory.clean(),
            stores = parsed.stores.clean(),
            paymentMethods = parsed.paymentMethods.clean(),
            creditCardPayments = parsed.creditCardPayments.clean(),
            expenseItems = parsed.expenseItems.clean(),
            assets = parsed.assets.clean(),
            assetExpenseLinks = parsed.assetExpenseLinks.clean(),
            wishlists = parsed.wishlists.clean(),
            wishlistSavings = parsed.wishlistSavings.clean(),
            expenseRefunds = parsed.expenseRefunds.clean()
        )
    }

    /** Fills in defaults for settings fields that are missing or invalid in the backup file. */
    @Suppress("USELESS_CAST", "USELESS_ELVIS")
    private fun sanitizeSettings(settings: SparelySettings): SparelySettings {
        val d = SparelySettings()
        fun Double?.finiteOr(default: Double): Double = this?.takeIf { it.isFinite() } ?: default
        return settings.copy(
            monthlyIncome = (settings.monthlyIncome as Double?).finiteOr(d.monthlyIncome).coerceAtLeast(0.0),
            age = (settings.age as Int?)?.takeIf { it in 1..130 } ?: d.age,
            riskLevel = (settings.riskLevel as RiskLevel?) ?: d.riskLevel,
            educationStatus = (settings.educationStatus as EducationStatus?) ?: d.educationStatus,
            employmentStatus = (settings.employmentStatus as EmploymentStatus?) ?: d.employmentStatus,
            livingSituation = (settings.livingSituation as LivingSituation?) ?: d.livingSituation,
            mainAccountBalance = (settings.mainAccountBalance as Double?).finiteOr(d.mainAccountBalance),
            savingsAccountBalance = (settings.savingsAccountBalance as Double?).finiteOr(d.savingsAccountBalance),
            currentEmergencyFund = (settings.currentEmergencyFund as Double?).finiteOr(d.currentEmergencyFund).coerceAtLeast(0.0),
            subscriptionTotal = (settings.subscriptionTotal as Double?).finiteOr(d.subscriptionTotal).coerceAtLeast(0.0),
            hasDebts = (settings.hasDebts as Boolean?) ?: d.hasDebts,
            expenseHistoryRetention = (settings.expenseHistoryRetention as ExpenseHistoryRetention?) ?: d.expenseHistoryRetention
        )
    }
}
