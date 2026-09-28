package com.example.sparely.data.repository

import com.example.sparely.domain.model.nextOccurrenceAfter
import androidx.room.withTransaction
import com.example.sparely.data.local.AchievementDao
import com.example.sparely.data.local.BudgetDao
import com.example.sparely.data.local.ChallengeDao
import com.example.sparely.data.local.ExpenseDao
import com.example.sparely.data.local.ExpenseEntity
import com.example.sparely.data.local.RecurringExpenseDao
import com.example.sparely.data.local.SavingsAccountDao
import com.example.sparely.data.local.SavingsAccountTransactionDao
import com.example.sparely.data.local.SavingsAccountTransactionEntity
import com.example.sparely.data.local.SavingsAccountTransactionType
import com.example.sparely.data.local.SavingsTransferDao
import com.example.sparely.data.local.SavingsTransferEntity
import com.example.sparely.data.local.SmartVaultDao
import com.example.sparely.data.local.StoreDao
import com.example.sparely.data.local.PaymentMethodDao
import com.example.sparely.data.local.CreditCardPaymentDao
import com.example.sparely.data.local.MainAccountTransactionEntity
import com.example.sparely.data.local.MainAccountTransactionType
import com.example.sparely.data.local.toDomain
import com.example.sparely.data.local.toEntity
import com.example.sparely.data.local.PendingVariableRecurringExpenseDao
import com.example.sparely.domain.model.SavingsAccount
import com.example.sparely.domain.model.SavingsAccountTransaction
import com.example.sparely.domain.model.SavingsAccountTransactionDisplayType
import com.example.sparely.domain.model.Achievement
import com.example.sparely.domain.model.SavingsTransfer
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.Store
import com.example.sparely.domain.model.PaymentMethod
import com.example.sparely.domain.model.VaultBalanceAdjustment
import com.example.sparely.domain.model.VaultContribution
import com.example.sparely.domain.model.VaultContributionSource
import com.example.sparely.domain.model.VaultAdjustmentType
import com.example.sparely.domain.model.VaultSchedule
import com.example.sparely.domain.model.VaultScheduleType
import com.example.sparely.domain.model.VaultTransferDirection
import com.example.sparely.domain.model.CategoryBudget
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.RecurringFrequency
import com.example.sparely.domain.model.SavingsChallenge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class SavingsRepository(
    private val expenseDao: ExpenseDao,
    private val transferDao: SavingsTransferDao,
    private val budgetDao: BudgetDao,
    private val recurringExpenseDao: RecurringExpenseDao,
    private val challengeDao: ChallengeDao,
    private val achievementDao: AchievementDao,
    private val savingsAccountDao: SavingsAccountDao,
    private val savingsAccountTransactionDao: SavingsAccountTransactionDao,
    private val smartVaultDao: SmartVaultDao,
    private val mainAccountDao: com.example.sparely.data.local.MainAccountDao,
    private val frozenFundDao: com.example.sparely.data.local.FrozenFundDao,
    private val allocationHistoryDao: com.example.sparely.data.local.AllocationHistoryDao,
    private val storeDao: StoreDao,
    private val paymentMethodDao: PaymentMethodDao,
    private val creditCardPaymentDao: CreditCardPaymentDao,
    private val expenseItemDao: com.example.sparely.data.local.ExpenseItemDao,
    private val expenseRefundDao: com.example.sparely.data.local.ExpenseRefundDao,
    private val assetDao: com.example.sparely.data.local.AssetDao,
    private val assetExpenseLinkDao: com.example.sparely.data.local.AssetExpenseLinkDao,
    private val wishlistDao: com.example.sparely.data.local.WishlistDao,
    private val wishlistSavingsDao: com.example.sparely.data.local.WishlistSavingsDao,
    private val pendingVariableRecurringExpenseDao: PendingVariableRecurringExpenseDao,
    private val recurringExpensePaidDao: com.example.sparely.data.local.RecurringExpensePaidDao,
    private val preferencesRepository: com.example.sparely.data.preferences.UserPreferencesRepository,
    private val database: com.example.sparely.data.local.SparelyDatabase
) {

    suspend fun runInTransaction(block: suspend () -> Unit) {
        database.withTransaction {
            block()
        }
    }

    // Guards every read-modify-write of the main-account balance (and vault balances that feed
    // it) so two concurrent callers - e.g. a user action and the auto-deposit WorkManager job -
    // can't race and silently drop one update. Reentrant: a call already holding the lock (from
    // an outer withMainAccountLock further up the same coroutine's call chain) just runs inline
    // instead of deadlocking on itself.
    private val mainAccountMutex = Mutex()
    private object MainAccountLockKey : CoroutineContext.Key<MainAccountLockMarker>
    private class MainAccountLockMarker : CoroutineContext.Element {
        override val key: CoroutineContext.Key<*> get() = MainAccountLockKey
    }

    /** True for amounts that can safely move money: finite and strictly positive. */
    private fun Double.isValidAmount(): Boolean = isFinite() && this > 0.0

    suspend fun <T> withMainAccountLock(block: suspend () -> T): T {
        if (coroutineContext[MainAccountLockKey] != null) {
            return block()
        }
        return mainAccountMutex.withLock {
            withContext(MainAccountLockMarker()) {
                block()
            }
        }
    }

    fun observeExpenses(): Flow<List<com.example.sparely.domain.model.Expense>> =
        expenseDao.observeExpenses().map { entities ->
            entities.map { relation -> 
                val expense = relation.expense.toDomain()
                expense.copy(items = relation.items.map { it.toDomain() })
            }
        }

    suspend fun getExpensesPaged(limit: Int, offset: Int): List<com.example.sparely.domain.model.Expense> =
        expenseDao.getExpensesPaged(limit, offset).map { relation ->
            val expense = relation.expense.toDomain()
            expense.copy(items = relation.items.map { it.toDomain() })
        }

    suspend fun countTotalExpenses(): Int = expenseDao.countTotalExpenses()

    suspend fun getExpensesForStore(storeId: Long): List<com.example.sparely.domain.model.Expense> =
        expenseDao.getExpensesForStore(storeId).map { relation ->
            val expense = relation.expense.toDomain()
            expense.copy(items = relation.items.map { it.toDomain() })
        }

    fun observeExpensesBetween(from: LocalDate, to: LocalDate): Flow<List<ExpenseEntity>> =
        expenseDao.observeExpensesBetween(from, to)

    suspend fun upsertExpense(entity: ExpenseEntity): Long = database.withTransaction {
        // Handle updates to existing expense
        if (entity.id != 0L) {
            val existing = expenseDao.findExpenseById(entity.id)
            if (existing != null) {
                processExpenseResult(existing, reverse = true)
            }
        }
        // Apply new impact
        processExpenseResult(entity, reverse = false)
        expenseDao.upsertExpense(entity)
    }

    suspend fun deleteExpense(entity: ExpenseEntity) {
        database.withTransaction {
            // 1. Handle vault contributions associated with this expense
            val contributions = smartVaultDao.getContributionsForExpense(entity.id)
            contributions.forEach { contribution ->
                if (contribution.reconciled && contribution.vaultId != null) {
                    // Reverse the vault balance for reconciled contributions
                    smartVaultDao.incrementVaultBalance(contribution.vaultId, -contribution.amount, null)
                } else {
                    // Remove frozen funds for pending contributions
                    removeFrozenForPending("VAULT_CONTRIBUTION", contribution.id)
                }
                // Delete the contribution record
                smartVaultDao.deleteContribution(contribution.id)
            }

            // 2. Delete pending tax contributions (non-reconciled)
            deletePendingContributionsForExpense(entity.id)

            // 3. Process expense result (payment method balance)
            processExpenseResult(entity, reverse = true)

            // 4. Delete the expense
            expenseDao.deleteExpense(entity)
        }
    }

    private suspend fun processExpenseResult(expense: ExpenseEntity, reverse: Boolean) {
        val methodId = expense.paymentMethodId ?: return
        val method = paymentMethodDao.getPaymentMethodById(methodId) ?: return
        
        if (method.isCreditCard) {
            // Impact is amount - refundedAmount
            // If paying $100, balance goes UP by 100.
            // If refunded $50, effective cost is $50, so balance goes UP by 50.
            // So we take (amount - refundedAmount).
            val impact = expense.amount - expense.refundedAmount
            val delta = if (reverse) -impact else impact
            paymentMethodDao.addToBalance(methodId, delta)
        }
    }

    suspend fun deleteExpensesBefore(date: LocalDate): Int = database.withTransaction {
        val expenses = expenseDao.getExpensesBetween(LocalDate.MIN, date.minusDays(1))
        batchCorrectBalances(expenses, reverse = true)
        expenseDao.deleteExpensesBefore(date)
    }

    suspend fun countExpensesBefore(date: LocalDate): Int {
        return expenseDao.countExpensesBefore(date)
    }

    suspend fun findExpenseById(id: Long): ExpenseEntity? = expenseDao.findExpenseById(id)

    suspend fun duplicateExpense(expenseId: Long): Long = database.withTransaction {
        val expenseEntity = expenseDao.findExpenseById(expenseId) ?: return@withTransaction 0L
        val items = expenseItemDao.getItemsForExpense(expenseId)

        val newExpense = expenseEntity.copy(
            id = 0L,  // Reset ID for new expense
            date = LocalDate.now()  // Use today as the date
        )

        val newExpenseId = upsertExpense(newExpense)

        // Duplicate items with new expense ID
        if (items.isNotEmpty()) {
            val newItems = items.map { item ->
                item.copy(id = 0L, expenseId = newExpenseId)
            }
            expenseItemDao.insertItems(newItems)
        }

        newExpenseId
    }

    // Refund management methods
    suspend fun recordRefund(
        expenseId: Long,
        refundedAmount: Double,
        refundDate: LocalDate = LocalDate.now(),
        refundMethod: String? = null,
        reason: String? = null,
        refundedItemIds: List<Long> = emptyList()
    ): Long {
        val refund = com.example.sparely.domain.model.ExpenseRefund(
            expenseId = expenseId,
            refundedAmount = refundedAmount,
            refundDate = refundDate,
            refundMethod = refundMethod,
            reason = reason,
            refundedItemIds = refundedItemIds
        )
        return expenseRefundDao.insertRefund(refund.toEntity())
    }

    fun observeRefundsForExpense(expenseId: Long): Flow<List<com.example.sparely.domain.model.ExpenseRefund>> =
        expenseRefundDao.observeRefundsForExpense(expenseId).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getRefundsForExpense(expenseId: Long): List<com.example.sparely.domain.model.ExpenseRefund> =
        expenseRefundDao.getRefundsForExpense(expenseId).map { it.toDomain() }

    suspend fun deleteRefund(refundId: Long) {
        expenseRefundDao.deleteRefundById(refundId)
    }

    suspend fun getTotalRefundedForExpense(expenseId: Long): Double =
        expenseRefundDao.getTotalRefundedForExpense(expenseId)

    // Asset management methods
    suspend fun upsertAsset(asset: com.example.sparely.domain.model.Asset): Long =
        assetDao.insertAsset(asset.toEntity())

    fun observeActiveAssets(): Flow<List<com.example.sparely.domain.model.Asset>> =
        assetDao.observeActiveAssets().transformLatest { entities ->
            val assetsWithSpending = entities.map { entity ->
                val totalSpending = assetExpenseLinkDao.getTotalSpendingForAsset(entity.id)
                entity.toDomain().copy(totalSpending = totalSpending)
            }
            emit(assetsWithSpending)
        }

    suspend fun getActiveAssets(): List<com.example.sparely.domain.model.Asset> {
        val entities = assetDao.getActiveAssets()
        return entities.map { entity ->
            val totalSpending = assetExpenseLinkDao.getTotalSpendingForAsset(entity.id)
            entity.toDomain().copy(totalSpending = totalSpending)
        }
    }

    /** All assets including archived ones (used for backups so nothing is lost). */
    suspend fun getAllAssetsIncludingArchived(): List<com.example.sparely.domain.model.Asset> {
        val entities = assetDao.getAllAssets()
        return entities.map { entity ->
            val totalSpending = assetExpenseLinkDao.getTotalSpendingForAsset(entity.id)
            entity.toDomain().copy(totalSpending = totalSpending)
        }
    }

    suspend fun deleteAsset(assetId: Long) = database.withTransaction {
        // Delete all links associated with this asset
        assetExpenseLinkDao.deleteLinksForAsset(assetId)
        assetDao.deleteAssetById(assetId)
    }

    suspend fun linkExpenseToAsset(expenseId: Long, assetId: Long, percentageAllocated: Double = 1.0): Long {
        val link = com.example.sparely.domain.model.AssetExpenseLink(
            assetId = assetId,
            expenseId = expenseId,
            percentageAllocated = percentageAllocated * 100.0,
            linkedAt = java.time.LocalDateTime.now()
        )
        return assetExpenseLinkDao.insertLink(link.toEntity())
    }

    suspend fun unlinkExpenseFromAsset(expenseId: Long, assetId: Long) {
        assetExpenseLinkDao.deleteLink(assetId, expenseId)
    }

    suspend fun getLinksForExpense(expenseId: Long): List<com.example.sparely.domain.model.AssetExpenseLink> =
        assetExpenseLinkDao.getLinksForExpense(expenseId).map { it.toDomain() }

    suspend fun getTotalSpendingForAsset(assetId: Long): Double =
        assetExpenseLinkDao.getTotalSpendingForAsset(assetId)

    suspend fun getExpensesLinkedToAsset(assetId: Long): List<Pair<com.example.sparely.domain.model.Expense, Double>> {
        val links = assetExpenseLinkDao.getLinksForAsset(assetId)
        return links.mapNotNull { link ->
            val expenseEntity = expenseDao.findExpenseById(link.expenseId)
            if (expenseEntity != null) {
                expenseEntity.toDomain() to (link.percentageAllocated / 100.0)
            } else {
                null
            }
        }
    }

    suspend fun createAssetFromExpense(
        name: String,
        category: com.example.sparely.domain.model.AssetCategory,
        description: String?,
        assetPrice: Double,
        creatorExpenseId: Long
    ): Long {
        // Create asset with price set to expense amount and creatorExpenseId
        val asset = com.example.sparely.domain.model.Asset(
            id = 0L,
            name = name,
            description = description,
            category = category,
            assetPrice = assetPrice,
            createdAt = java.time.LocalDateTime.now(),
            creatorExpenseId = creatorExpenseId
        )
        val assetId = upsertAsset(asset)

        // Link the expense to the asset with 100% allocation
        linkExpenseToAsset(creatorExpenseId, assetId, 1.0)

        return assetId
    }

    suspend fun linkCreatorExpenseToAsset(assetId: Long, expenseId: Long, updateAssetPrice: Boolean = true) {
        // Link the expense as creator
        val asset = assetDao.getAssetById(assetId)?.toDomain()
        if (asset != null) {
            // Fetch the expense to get its amount
            val expenseEntity = expenseDao.findExpenseById(expenseId)
            if (expenseEntity != null) {
                val expense = expenseEntity.toDomain()
                // Update asset's creatorExpenseId and optionally its price
                val updatedAsset = asset.copy(
                    creatorExpenseId = expenseId,
                    assetPrice = if (updateAssetPrice) expense.amount else asset.assetPrice
                )
                upsertAsset(updatedAsset)

                // Link expense to asset if not already linked
                try {
                    linkExpenseToAsset(expenseId, assetId, 1.0)
                } catch (e: Exception) {
                    // Link already exists, that's fine
                }
            }
        }
    }

    // Wishlist management methods
    suspend fun addWishlist(wishlist: com.example.sparely.domain.model.Wishlist): Long {
        val entity = wishlist.toEntity().copy(createdAt = java.time.LocalDateTime.now())
        return wishlistDao.insertWishlist(entity)
    }

    suspend fun updateWishlist(wishlist: com.example.sparely.domain.model.Wishlist) {
        wishlistDao.insertWishlist(wishlist.toEntity())
    }

    suspend fun deleteWishlist(wishlistId: Long) = database.withTransaction {
        wishlistSavingsDao.deleteSavingsForWishlist(wishlistId)
        wishlistDao.deleteWishlistById(wishlistId)
    }

    fun observeActiveWishlists(): Flow<List<com.example.sparely.domain.model.Wishlist>> =
        wishlistDao.observeActiveWishlists().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getActiveWishlists(): List<com.example.sparely.domain.model.Wishlist> =
        wishlistDao.getActiveWishlists().map { it.toDomain() }

    /** All wishlists including archived ones (used for backups so nothing is lost). */
    suspend fun getAllWishlistsIncludingArchived(): List<com.example.sparely.domain.model.Wishlist> =
        wishlistDao.getAllWishlists().map { it.toDomain() }

    suspend fun getWishlistById(wishlistId: Long): com.example.sparely.domain.model.Wishlist? =
        wishlistDao.getWishlistById(wishlistId)?.toDomain()

    suspend fun allocateSavingsToWishlist(
        wishlistId: Long,
        amount: Double,
        source: String = "MANUAL_ALLOCATION"
    ) {
        if (!amount.isValidAmount()) return
        database.withTransaction {
            wishlistDao.incrementWishlistSavings(wishlistId, amount)
            val savingsRecord = com.example.sparely.data.local.WishlistSavingsEntity(
                wishlistId = wishlistId,
                amount = amount,
                date = java.time.LocalDateTime.now(),
                source = source
            )
            wishlistSavingsDao.insertSavings(savingsRecord)
        }
    }

    suspend fun setSavingsForWishlist(wishlistId: Long, amount: Double) {
        if (!amount.isFinite() || amount < 0.0) return
        wishlistDao.updateWishlistSavings(wishlistId, amount)
    }

    fun observeSavingsForWishlist(wishlistId: Long): Flow<List<com.example.sparely.domain.model.WishlistSavings>> =
        wishlistSavingsDao.observeSavingsForWishlist(wishlistId).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getSavingsForWishlist(wishlistId: Long): List<com.example.sparely.domain.model.WishlistSavings> =
        wishlistSavingsDao.getSavingsForWishlist(wishlistId).map { it.toDomain() }

    suspend fun archiveWishlist(wishlistId: Long, archived: Boolean = true) {
        wishlistDao.updateWishlistArchived(wishlistId, archived)
    }

    suspend fun setCooldownExpiry(
        wishlistId: Long,
        daysFromNow: Long = 3
    ) {
        val expiryTime = java.time.LocalDateTime.now().plusDays(daysFromNow)
        wishlistDao.updateCooldownExpiry(wishlistId, expiryTime)
    }

    suspend fun getExpiredCooldownWishlists(): List<com.example.sparely.domain.model.Wishlist> =
        wishlistDao.getExpiredCooldownWishlists(java.time.LocalDateTime.now()).map { it.toDomain() }

    suspend fun getTotalSavingsForWishlist(wishlistId: Long): Double =
        wishlistSavingsDao.getTotalSavingsForWishlist(wishlistId)

    suspend fun clearExpenses() = database.withTransaction {
        // Fetch all expenses to reverse impacts
        val expenses = expenseDao.getExpensesBetween(LocalDate.MIN, LocalDate.MAX)
        batchCorrectBalances(expenses, reverse = true)
        expenseDao.clearAll()
    }

    private suspend fun batchCorrectBalances(expenses: List<ExpenseEntity>, reverse: Boolean) {
        if (expenses.isEmpty()) return
        
        // 1. Identify all payment methods involved
        val methodIds = expenses.mapNotNull { it.paymentMethodId }.distinct()
        if (methodIds.isEmpty()) return
        
        // 2. Fetch credit cards among them
        // We can't query by list easily without a new DAO method, so we fetch all payment methods and filter.
        // Or iterate. Since method types are few (usually < 10), fetching all is cheap.
        val allMethods = paymentMethodDao.getAllPaymentMethods().first() // Flow -> List
        val creditCardMap = allMethods.filter { it.isCreditCard }.associateBy { it.id }
        
        // 3. Aggregate impacts
        val impactMap = mutableMapOf<Long, Double>()
        
        expenses.forEach { expense ->
            val methodId = expense.paymentMethodId
            if (methodId != null && creditCardMap.containsKey(methodId)) {
                val impact = expense.amount - expense.refundedAmount
                val delta = if (reverse) -impact else impact
                impactMap[methodId] = (impactMap[methodId] ?: 0.0) + delta
            }
        }
        
        // 4. Apply updates
        impactMap.forEach { (methodId, delta) ->
            if (delta != 0.0) {
                paymentMethodDao.addToBalance(methodId, delta)
            }
        }
    }

    fun observeTransfers(): Flow<List<SavingsTransferEntity>> = transferDao.observeTransfers()

    suspend fun logTransfer(entity: SavingsTransferEntity) {
        database.withTransaction {
            transferDao.upsert(entity)
            reconcileAccountsForTransfer(entity)
        }
    }

    suspend fun getTransfersForAccount(accountId: Long): List<SavingsTransfer> =
        transferDao.getTransfersForAccount(accountId).map { it.toDomain() }

    suspend fun clearTransfers() {
        transferDao.clearAll()
    }

    suspend fun clearSmartVaults() {
        smartVaultDao.clearAllVaults()
    }

    fun observeSavingsAccounts(): Flow<List<SavingsAccount>> =
        savingsAccountDao.observeAccounts().map { accounts ->
            accounts.map { it.toDomain() }
        }

    fun observeSmartVaults(): Flow<List<SmartVault>> =
        smartVaultDao.observeActiveVaults().map { rows -> rows.map { it.toDomain() } }

    /** Active and archived vaults (used for backups so archived vault history keeps its parent). */
    fun observeAllSmartVaults(): Flow<List<SmartVault>> =
        smartVaultDao.observeAllVaults().map { rows -> rows.map { it.toDomain() } }


    

    suspend fun upsertSavingsAccount(account: SavingsAccount) {
        val assignedId = savingsAccountDao.upsert(account.toEntity())
        val resolvedId = if (account.id == 0L) assignedId else account.id
        // Ensure only one account is designated as main overflow
        if (account.isMainOverflowAccount && resolvedId != 0L) {
            savingsAccountDao.setAsMainOverflowAccount(resolvedId)
        }
    }

    suspend fun getSavingsAccountById(id: Long): SavingsAccount? {
        return savingsAccountDao.getAccountById(id)?.toDomain()
    }

    suspend fun deleteSavingsAccount(id: Long) {
        savingsAccountDao.deleteById(id)
    }

    suspend fun recordInterestEarned(accountId: Long, amount: Double, entryDate: LocalDate = LocalDate.now()): Unit = withMainAccountLock { database.withTransaction {
        if (!amount.isValidAmount()) return@withTransaction
        savingsAccountDao.recordInterestEarned(accountId, amount, entryDate)
        
        // Log as a contribution for history/analytics (Vault logic)
        logVaultContribution(
            com.example.sparely.domain.model.VaultContribution(
                savingsAccountId = accountId,
                vaultId = null,
                amount = amount,
                date = entryDate,
                source = com.example.sparely.domain.model.VaultContributionSource.INTEREST,
                note = "Manual interest entry",
                reconciled = true
            )
        )

        // Log to HISA transaction history
        val transaction = SavingsAccountTransactionEntity(
            accountId = accountId,
            type = SavingsAccountTransactionType.INTEREST,
            amount = amount,
            balanceAfter = (savingsAccountDao.getAccountById(accountId)?.currentBalance ?: 0.0),
            timestamp = LocalDateTime.now(),
            description = "Interest Earned"
        )
        savingsAccountTransactionDao.insert(transaction)
    } }




    /**
     * Get the main overflow account for receiving excess funds.
     */
    suspend fun getMainOverflowAccount(): SavingsAccount? {
        return savingsAccountDao.getMainOverflowAccount()?.toDomain()
    }

    /**
     * Set a savings account as the main overflow account.
     */
    suspend fun setMainOverflowAccount(accountId: Long) {
        savingsAccountDao.setAsMainOverflowAccount(accountId)
    }

    /**
     * Increment savings account balance (for overflow deposits).
     */
    suspend fun incrementSavingsAccountBalance(accountId: Long, amount: Double) {
        if (amount == 0.0 || !amount.isFinite()) return
        savingsAccountDao.incrementBalance(accountId, amount)
    }

    /**
     * Archive a savings account (soft delete).
     */
    suspend fun archiveSavingsAccount(accountId: Long) {
        savingsAccountDao.archiveAccount(accountId)
    }

    suspend fun upsertSmartVault(vault: SmartVault) = database.withTransaction {
        val assignedId = smartVaultDao.upsertVault(vault.toEntity())
        val resolvedId = if (vault.id == 0L) assignedId else vault.id
        syncVaultSchedules(resolvedId, vault.schedules)
    }

    suspend fun seedSmartVaults(vaults: List<SmartVault>) {
        if (smartVaultDao.observeActiveVaults().first().isNotEmpty()) return
        if (vaults.isEmpty()) return
        vaults.forEach { vault ->
            upsertSmartVault(vault.copy(id = 0L))
        }
    }

    suspend fun deleteSmartVault(id: Long): Unit = withMainAccountLock { database.withTransaction {
        val vault = smartVaultDao.getVaultById(id) ?: return@withTransaction
        if (vault.currentBalance > 0) {
            deductFromVault(
                vaultId = id,
                amount = vault.currentBalance,
                reason = "Vault closure: ${vault.name}",
                creditMainAccount = true
            )
        }
        smartVaultDao.deleteVault(id)
    } }

    suspend fun logVaultContribution(contribution: VaultContribution): Long = withMainAccountLock { database.withTransaction {
        require(contribution.amount.isFinite()) { "Contribution amount must be a finite number" }
        val entity = contribution.toEntity()
        val id = smartVaultDao.upsertContribution(entity)
        if (contribution.reconciled) {
            val contributionWithId = contribution.copy(id = id)
            if (contribution.isHisaTransfer) {
                contribution.savingsAccountId?.let { savingsAccountDao.incrementBalance(it, contribution.amount) }
            } else if (contribution.vaultId != null) {
                smartVaultDao.incrementVaultBalance(contribution.vaultId, contribution.amount, contribution.date)
            }
            deductFromMainAccountForContribution(contributionWithId)
        }
        id
    } }

    suspend fun logVaultContributions(contributions: List<VaultContribution>): List<Long> {
        if (contributions.isEmpty()) return emptyList()
        return contributions.map { logVaultContribution(it) }
    }
    
    suspend fun getPendingVaultContributions(): List<VaultContribution> =
        smartVaultDao.getPendingContributions().map { it.toDomain() }

    fun observePendingVaultContributions(): Flow<List<VaultContribution>> =
        smartVaultDao.observePendingContributions().map { list -> list.map { it.toDomain() } }

    suspend fun getVaultContributionById(contributionId: Long): VaultContribution? =
        smartVaultDao.getContributionById(contributionId)?.toDomain()
    

    suspend fun reconcileVaultContribution(contributionId: Long): Unit = withMainAccountLock { database.withTransaction {
        val contribution = smartVaultDao.getContributionById(contributionId)
        if (contribution != null && !contribution.reconciled) {
            smartVaultDao.markContributionReconciled(contributionId)
            if (contribution.savingsAccountId != null && contribution.vaultId == null) {
                savingsAccountDao.incrementBalance(contribution.savingsAccountId, contribution.amount)
            } else if (contribution.vaultId != null) {
                smartVaultDao.incrementVaultBalance(contribution.vaultId, contribution.amount, contribution.date)
            }
            deductFromMainAccountForContribution(contribution.toDomain())
        }
    } }

    private suspend fun deductFromMainAccountForContribution(contribution: VaultContribution) {
        val shouldDeduct = when (contribution.source) {
            com.example.sparely.domain.model.VaultContributionSource.TRANSFER,
            com.example.sparely.domain.model.VaultContributionSource.SAVING_TAX,
            com.example.sparely.domain.model.VaultContributionSource.AUTO_DEPOSIT,
            com.example.sparely.domain.model.VaultContributionSource.INCOME -> true
            else -> false
        }

        // Saving-tax contributions are debited from the main account when they are logged (the
        // debit transaction is linked through the cross-ref table). Don't debit them a second
        // time when the pending contribution is approved.
        val alreadyDebited = contribution.id != 0L &&
            mainAccountDao.countTransactionsForContribution(contribution.id) > 0

        if (shouldDeduct && contribution.amount > 0 && !alreadyDebited) {
            val currentBalance = getLatestMainAccountBalance()
            val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                type = com.example.sparely.data.local.MainAccountTransactionType.VAULT_CONTRIBUTION,
                amount = contribution.amount,
                balanceAfter = (currentBalance - contribution.amount).coerceAtLeast(0.0),
                timestamp = java.time.LocalDateTime.now(),
                description = contribution.note?.takeIf { it.isNotBlank() } ?: "Transfer to ${if(contribution.isHisaTransfer) "HISA" else "Vault"}",
                relatedVaultContributionIds = listOf(contribution.id)
            )
            insertMainAccountTransaction(transaction)
            preferencesRepository.updateMainAccountBalance(transaction.balanceAfter)
        }
    }

    suspend fun reconcileVaultContributions(contributionIds: List<Long>) {
        contributionIds.forEach { id ->
            reconcileVaultContribution(id)
        }
    }

    /**
     * Approve a pending contribution: reconcile it and remove any frozen funds associated with it.
     */
    suspend fun approvePendingContribution(contributionId: Long): Unit = withMainAccountLock { database.withTransaction {
        reconcileVaultContribution(contributionId)
        removeFrozenForPending("VAULT_CONTRIBUTION", contributionId)
    } }

    /**
     * Cancel a pending contribution: delete the pending contribution record and remove frozen funds.
     */
    suspend fun cancelPendingContribution(contributionId: Long) = database.withTransaction {
        val contribution = smartVaultDao.getContributionById(contributionId) ?: return@withTransaction
        // Only pending rows may be cancelled; a reconciled one has already moved money.
        if (contribution.reconciled) return@withTransaction
        // delete the pending contribution row
        smartVaultDao.deleteContribution(contributionId)
        // remove the frozen record(s) associated with this pending contribution
        removeFrozenForPending("VAULT_CONTRIBUTION", contributionId)
    }

    suspend fun getVaultContributions(vaultId: Long): List<VaultContribution> =
        smartVaultDao.getContributionsForVault(vaultId).map { it.toDomain() }

    suspend fun getReconciledVaultContributions(vaultId: Long): List<VaultContribution> =
        smartVaultDao.getReconciledContributionsForVault(vaultId).map { it.toDomain() }

    suspend fun getVaultAdjustments(vaultId: Long): List<VaultBalanceAdjustment> =
        smartVaultDao.getAdjustmentsForVault(vaultId).map { it.toDomain() }

    suspend fun depositToVault(vaultId: Long, amount: Double, reason: String?, adjustMainAccount: Boolean): Unit = withMainAccountLock { database.withTransaction {
        if (!amount.isValidAmount()) return@withTransaction
        val vault = smartVaultDao.getVaultById(vaultId) ?: return@withTransaction
        val sanitizedAmount = amount.coerceAtLeast(0.0)
        val newBalance = vault.currentBalance + sanitizedAmount
        recordVaultBalanceAdjustment(
            vaultId = vaultId,
            previousBalance = vault.currentBalance,
            newBalance = newBalance,
            type = VaultAdjustmentType.MANUAL_DEPOSIT,
            reason = reason
        )

        if (adjustMainAccount) {
            val currentBalance = getLatestMainAccountBalance()
            val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                type = com.example.sparely.data.local.MainAccountTransactionType.WITHDRAWAL,
                amount = sanitizedAmount,
                balanceAfter = (currentBalance - sanitizedAmount).coerceAtLeast(0.0),
                timestamp = java.time.LocalDateTime.now(),
                description = reason?.take(100) ?: "Manual deposit to ${vault.name}"
            )
            insertMainAccountTransaction(transaction)
            preferencesRepository.updateMainAccountBalance(transaction.balanceAfter)
        }
    } }

    suspend fun deductFromVault(vaultId: Long, amount: Double, reason: String?, creditMainAccount: Boolean): Unit = withMainAccountLock { database.withTransaction {
        if (!amount.isValidAmount()) return@withTransaction
        val vault = smartVaultDao.getVaultById(vaultId) ?: return@withTransaction
        // A vault can't go below zero, so never move (or credit) more than it actually holds;
        // otherwise withdrawing $500 from a $100 vault would create $400 in the main account.
        val sanitizedAmount = amount.coerceAtMost(vault.currentBalance.coerceAtLeast(0.0))
        if (sanitizedAmount <= 0.0) return@withTransaction
        val newBalance = (vault.currentBalance - sanitizedAmount).coerceAtLeast(0.0)
        recordVaultBalanceAdjustment(
            vaultId = vaultId,
            previousBalance = vault.currentBalance,
            newBalance = newBalance,
            type = VaultAdjustmentType.MANUAL_DEDUCTION,
            reason = reason
        )

        if (creditMainAccount) {
            val currentBalance = getLatestMainAccountBalance()
            val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                type = com.example.sparely.data.local.MainAccountTransactionType.DEPOSIT,
                amount = sanitizedAmount,
                balanceAfter = currentBalance + sanitizedAmount,
                timestamp = java.time.LocalDateTime.now(),
                description = reason?.take(100) ?: "Manual withdrawal from ${vault.name}"
            )
            insertMainAccountTransaction(transaction)
            preferencesRepository.updateMainAccountBalance(transaction.balanceAfter)
        }
    } }

    suspend fun overrideVaultBalance(vaultId: Long, newBalance: Double, reason: String?): Unit = withMainAccountLock { database.withTransaction {
        if (!newBalance.isFinite() || newBalance < 0.0) return@withTransaction
        val vault = smartVaultDao.getVaultById(vaultId) ?: return@withTransaction
        val sanitized = newBalance.coerceAtLeast(0.0)
        recordVaultBalanceAdjustment(
            vaultId = vaultId,
            previousBalance = vault.currentBalance,
            newBalance = sanitized,
            type = VaultAdjustmentType.MANUAL_EDIT,
            reason = reason
        )
    } }

    suspend fun updateVaultArchived(vaultId: Long, archived: Boolean) {
        smartVaultDao.updateVaultArchived(vaultId, archived)
    }

    suspend fun recordVaultBalanceAdjustment(
        vaultId: Long,
        previousBalance: Double,
        newBalance: Double,
        type: VaultAdjustmentType,
        reason: String?
    ) {
        if (newBalance == previousBalance || !newBalance.isFinite()) return
        val delta = newBalance - previousBalance
        val timestamp = Instant.now()
        smartVaultDao.setVaultBalance(vaultId, newBalance, LocalDate.now())
        val adjustment = VaultBalanceAdjustment(
            vaultId = vaultId,
            type = type,
            delta = delta,
            resultingBalance = newBalance,
            createdAt = timestamp,
            reason = reason?.takeIf { it.isNotBlank() }
        )
        smartVaultDao.insertAdjustment(adjustment.toEntity())
    }

    // REMOVED: Legacy bank-sync refresh method (no longer using linked accounts)

    fun observeBudgets(): Flow<List<CategoryBudget>> =
        budgetDao.observeBudgets().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getBudgetsForMonth(year: Int, month: Int): List<CategoryBudget> =
        budgetDao.getBudgetsForMonth(year, month).map { it.toDomain() }

    suspend fun upsertBudget(budget: CategoryBudget) {
        budgetDao.upsert(budget.toEntity())
    }

    suspend fun deleteBudget(id: Long) {
        budgetDao.deleteById(id)
    }

    suspend fun clearBudgets() {
        budgetDao.clear()
    }

    fun observeRecurringExpenses(): Flow<List<RecurringExpense>> =
        recurringExpenseDao.observeRecurringExpenses().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun upsertRecurringExpense(expense: RecurringExpense) {
        val toPersist = if (expense.nextRunAt == null) {
            // First time or update without nextRunAt: calculate the first run date.
            // If startDate is today or in the past, advance to the next occurrence
            // instead of firing immediately - otherwise a recurring expense created
            // today (the default startDate) gets auto-processed within the hour and
            // creates a duplicate expense on the day it was added.
            val today = LocalDate.now()
            val initialRunDate = if (expense.startDate.isAfter(today)) {
                expense.startDate
            } else {
                expense.nextOccurrenceAfter(today)
            }
            val initialRun = initialRunDate.atTime(9, 0)
            expense.copy(nextRunAt = initialRun)
        } else {
            expense
        }
        recurringExpenseDao.upsert(toPersist.toEntity())
    }

    suspend fun deleteRecurringExpense(id: Long) {
        recurringExpenseDao.deleteById(id)
    }

    suspend fun updateRecurringExpenseProcessed(id: Long, processedDate: LocalDate) {
        val entity = recurringExpenseDao.getById(id) ?: return
        val nextRun = computeNextRunForRecurring(entity.toDomain(), processedDate)
        
        // Update both lastProcessedDate and nextRunAt
        val updated = entity.copy(
            lastProcessedDate = processedDate,
            nextRunAt = nextRun
        )
        recurringExpenseDao.upsert(updated)
    }

    private fun computeNextRunForRecurring(expense: RecurringExpense, lastRun: LocalDate): java.time.LocalDateTime {
        val time = expense.nextRunAt?.toLocalTime() ?: java.time.LocalTime.of(9, 0)
        // The occurrence just handled is the scheduled one (nextRunAt), even when it was paid
        // early or processed late. Schedule the next one after both the due date and the
        // processing date, keeping at least half an interval after the due date so a date that
        // drifted under the old "add one month to the previous run" logic (e.g. the 28th for a
        // schedule anchored on the 31st) can't be charged twice in the same period.
        val due = expense.nextRunAt?.toLocalDate() ?: lastRun
        val minimumGapDays = (expense.frequency.daysInterval / 2).toLong()
        val afterDue = expense.nextOccurrenceAfter(due.plusDays(minimumGapDays))
        val afterProcessing = expense.nextOccurrenceAfter(lastRun)
        val nextDate = if (afterDue.isAfter(afterProcessing)) afterDue else afterProcessing
        return nextDate.atTime(time)
    }


    suspend fun clearRecurringExpenses() {
        recurringExpenseDao.clear()
    }

    // ===== Early Recurring Expense Payment Management =====

    /**
     * Record an early payment for a recurring expense before the due date.
     * Creates a payment record and optionally creates an actual expense entry.
     */
    suspend fun recordRecurringExpensePaidEarly(
        recurringExpenseId: Long,
        dueDate: LocalDate,
        amountPaid: Double,
        paidDate: LocalDate = LocalDate.now(),
        notes: String? = null
    ): Long {
        val paidRecord = com.example.sparely.data.local.RecurringExpensePaidEntity(
            recurringExpenseId = recurringExpenseId,
            dueDate = dueDate,
            amountPaid = amountPaid,
            paidDate = paidDate,
            notes = notes
        )
        return recurringExpensePaidDao.insertPaid(paidRecord)
    }

    /**
     * Get the payment history for a recurring expense.
     */
    suspend fun getRecurringExpensePaidHistory(recurringExpenseId: Long): List<com.example.sparely.data.local.RecurringExpensePaidEntity> {
        return recurringExpensePaidDao.getPaidHistoryForRecurring(recurringExpenseId)
    }

    /**
     * Observe the payment history for a recurring expense reactively.
     */
    fun observeRecurringExpensePaidHistory(recurringExpenseId: Long): Flow<List<com.example.sparely.data.local.RecurringExpensePaidEntity>> {
        return recurringExpensePaidDao.observePaidHistoryForRecurring(recurringExpenseId)
    }

    fun observeRecurringExpensePaidRecords(): Flow<List<com.example.sparely.data.local.RecurringExpensePaidEntity>> {
        return recurringExpensePaidDao.observeAllPaidRecords()
    }

    /**
     * Check if a recurring expense has been paid for a specific due date.
     */
    suspend fun isRecurringExpensePaidForDate(recurringExpenseId: Long, dueDate: LocalDate): Boolean {
        return recurringExpensePaidDao.getPaidRecordForDate(recurringExpenseId, dueDate) != null
    }

    /**
     * Get the payment record for a specific recurring expense and due date.
     */
    suspend fun getRecurringExpensePaidForDate(recurringExpenseId: Long, dueDate: LocalDate): com.example.sparely.data.local.RecurringExpensePaidEntity? {
        return recurringExpensePaidDao.getPaidRecordForDate(recurringExpenseId, dueDate)
    }

    /**
     * Link an actual expense to a recurring expense early payment record.
     */
    suspend fun linkExpenseToRecurringPayment(paidRecordId: Long, expenseId: Long) {
        recurringExpensePaidDao.linkExpenseToPayment(paidRecordId, expenseId)
    }

    /**
     * Get total amount paid for a recurring expense in a date range.
     */
    suspend fun getTotalRecurringExpensePaid(recurringExpenseId: Long, fromDate: LocalDate, toDate: LocalDate): Double {
        return recurringExpensePaidDao.getTotalPaidInRange(recurringExpenseId, fromDate, toDate) ?: 0.0
    }

    /**
     * Delete a recorded early payment for a recurring expense.
     */
    suspend fun deleteRecurringExpensePaidRecord(paidRecordId: Long) {
        // Previously looked the record up under recurringExpenseId = 0, so nothing was ever deleted.
        recurringExpensePaidDao.deletePaidById(paidRecordId)
    }

    /**
     * Clean up old paid records (before a certain date) to keep database lean.
     */
    suspend fun cleanupOldRecurringExpensePaidRecords(beforeDate: LocalDate) {
        recurringExpensePaidDao.deleteOldPaidRecords(beforeDate)
    }

    fun observeChallenges(): Flow<List<SavingsChallenge>> =
        challengeDao.observeChallenges().map { rows ->
            rows.map { it.toDomain() }
        }

    suspend fun upsertSavingsChallenge(challenge: SavingsChallenge) {
        val (entity, milestoneEntities) = challenge.toEntity()
        val challengeId = if (entity.id == 0L) {
            challengeDao.upsertChallenge(entity.copy(id = 0))
        } else {
            challengeDao.upsertChallenge(entity)
        }
        val resolvedId = if (entity.id == 0L) challengeId else entity.id
        challengeDao.deleteMilestonesForChallenge(resolvedId)
        if (milestoneEntities.isNotEmpty()) {
            val adjusted = milestoneEntities.map { it.copy(challengeId = resolvedId) }
            challengeDao.upsertMilestones(adjusted)
        }
    }

    suspend fun deleteSavingsChallenge(id: Long) = database.withTransaction {
        challengeDao.deleteMilestonesForChallenge(id)
        challengeDao.deleteChallengeById(id)
    }

    fun observeAchievements(): Flow<List<Achievement>> =
        achievementDao.observeAchievements().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun upsertAchievement(achievement: Achievement) {
        achievementDao.upsert(achievement.toEntity())
    }

    suspend fun upsertAchievements(achievements: List<Achievement>) {
        if (achievements.isEmpty()) return
        achievementDao.upsertAll(achievements.map { it.toEntity() })
    }

    suspend fun clearAchievements() {
        achievementDao.clear()
    }

    private suspend fun reconcileAccountsForTransfer(entity: SavingsTransferEntity) {
        if (!entity.amount.isValidAmount()) return
        entity.sourceAccountId?.let { sourceId ->
            savingsAccountDao.incrementBalance(sourceId, -entity.amount)
        }
        val destinationId = entity.destinationAccountId
        if (destinationId != null) {
            savingsAccountDao.incrementBalance(destinationId, entity.amount)
        }
        // Note: Transfers to main account (destinationId == null) are handled by the caller
        // via insertMainAccountTransaction to maintain proper audit trail
    }

    // Main Account Transaction Methods
    fun observeMainAccountTransactions(): Flow<List<com.example.sparely.domain.model.MainAccountTransaction>> =
        mainAccountDao.observeAllTransactions().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getRecentMainAccountTransactions(limit: Int = 50): List<com.example.sparely.domain.model.MainAccountTransaction> =
        mainAccountDao.getRecentTransactions(limit).map { it.toDomain() }

    suspend fun insertMainAccountTransaction(transaction: com.example.sparely.domain.model.MainAccountTransaction): Long {
        // A NaN/Infinity here would poison every later balance computed from the latest row.
        require(transaction.amount.isFinite() && transaction.balanceAfter.isFinite()) {
            "Refusing to record a main account transaction with an invalid amount"
        }
        val transactionId = mainAccountDao.insertTransaction(transaction.toEntity())
        transaction.relatedVaultContributionIds?.forEach { contributionId ->
            mainAccountDao.insertTransactionVaultCrossRef(
                com.example.sparely.data.local.TransactionVaultContributionCrossRef(
                    transactionId = transactionId,
                    contributionId = contributionId
                )
            )
        }
        return transactionId
    }

    suspend fun getLatestMainAccountBalance(): Double {
        val transactionBalance = mainAccountDao.getLatestTransaction()?.transaction?.balanceAfter
        // Fall back to preferences if no transactions exist yet
        // This fixes the bug where the first deduction would use 0.0 instead of the actual balance
        return transactionBalance ?: preferencesRepository.getSettingsSnapshot().mainAccountBalance
    }

    /**
     * How much the main account actually paid for [expense] (net of refunds already credited
     * back), based on the main-account transactions linked to it. Expenses paid from a vault,
     * by credit card, or not deducted at all return 0.
     *
     * Recurring expenses logged by older versions weren't linked to their transaction; for
     * those the legacy assumption (amount minus refunds) is kept.
     */
    suspend fun getMainAccountDebitForExpense(expense: ExpenseEntity): Double {
        if (mainAccountDao.countTransactionsForExpense(expense.id) == 0) {
            val isLegacyUnlinkedRecurring = expense.isRecurring && expense.deductedFromVaultId == null &&
                expense.paymentMethodId?.let { paymentMethodDao.getPaymentMethodById(it)?.isCreditCard } != true
            return if (isLegacyUnlinkedRecurring) (expense.amount - expense.refundedAmount).coerceAtLeast(0.0) else 0.0
        }
        return mainAccountDao.netDebitForExpense(expense.id).coerceAtLeast(0.0)
    }

    suspend fun calculateMainAccountBalance(): Double =
        mainAccountDao.calculateBalanceFromTransactions()

    suspend fun getAvailableMainAccountBalance(): Double {
        val canonical = getLatestMainAccountBalance()
        val frozen = getTotalFrozenAmount()
        return (canonical - frozen).coerceAtLeast(0.0)
    }

    suspend fun clearMainAccountTransactions() {
        mainAccountDao.deleteAllTransactions()
    }

    suspend fun flagExpenseAsRefunded(expenseId: Long, refundedAmount: Double, totalRefunded: Double) {
        val expense = expenseDao.findExpenseById(expenseId) ?: return
        val isFullyRefunded = totalRefunded >= expense.amount
        val updated = expense.copy(
            refundedAmount = totalRefunded,
            isRefunded = isFullyRefunded
        )
        upsertExpense(updated)
    }

    /**
     * Processes a refund end-to-end: updates the expense's refunded amount (which drives the
     * credit card balance down via [processExpenseResult], allowing it to go negative if the
     * card was already paid off), records the audit-trail entry, credits the main account when
     * the expense wasn't paid by a credit card, and clears any pending tax contributions.
     *
     * Returns the actual amount refunded (clamped to what's left to refund), or null if there
     * was nothing to refund (expense not found, or already fully refunded).
     */
    suspend fun processExpenseRefund(
        expenseId: Long,
        requestedAmount: Double,
        refundMethod: String? = null,
        reason: String? = null,
        refundedItemIds: List<Long> = emptyList()
    ): Double? {
        if (!requestedAmount.isValidAmount()) return null

        return withMainAccountLock { database.withTransaction {
            // Read inside the lock/transaction so two quick refunds can't both see the same
            // "already refunded" total and over-refund the expense.
            val expense = expenseDao.findExpenseById(expenseId) ?: return@withTransaction null

            val maxRefundable = expense.amount - expense.refundedAmount
            // coerceIn would throw if maxRefundable were negative (expense edited below its refunds).
            if (maxRefundable <= 0.0) return@withTransaction null
            val actualRefund = requestedAmount.coerceAtMost(maxRefundable)
            if (actualRefund <= 0.0) return@withTransaction null

            val newTotalRefunded = expense.refundedAmount + actualRefund

            flagExpenseAsRefunded(expenseId, actualRefund, newTotalRefunded)

            recordRefund(
                expenseId = expenseId,
                refundedAmount = actualRefund,
                refundMethod = refundMethod,
                reason = reason,
                refundedItemIds = refundedItemIds
            )

            val paymentMethod = expense.paymentMethodId?.let { paymentMethodDao.getPaymentMethodById(it) }
            if (paymentMethod?.isCreditCard != true) {
                val currentBalance = getLatestMainAccountBalance()
                val newBalance = currentBalance + actualRefund
                val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                    type = MainAccountTransactionType.DEPOSIT,
                    amount = actualRefund,
                    balanceAfter = newBalance,
                    timestamp = LocalDateTime.now(),
                    description = "Refund for: ${expense.description}",
                    relatedExpenseId = expenseId
                )
                insertMainAccountTransaction(transaction)
                preferencesRepository.updateMainAccountBalance(newBalance)
            }

            deletePendingContributionsForExpense(expenseId)

            actualRefund
        } }
    }

    suspend fun deletePendingContributionsForExpense(expenseId: Long) {
        // Find them first to remove frozen funds
        val contributions = smartVaultDao.getContributionsForExpense(expenseId).filter { !it.reconciled }
        contributions.forEach { c ->
             removeFrozenForPending("VAULT_CONTRIBUTION", c.id)
        }
        smartVaultDao.deletePendingContributionsForExpense(expenseId)
    }

    suspend fun getContributionsForExpense(expenseId: Long): List<VaultContribution> = 
        smartVaultDao.getContributionsForExpense(expenseId).map { it.toDomain() }

    // Frozen funds methods
    suspend fun insertFrozenFund(pendingType: String, pendingId: Long, amount: Double, description: String? = null): Long {
        val frozen = com.example.sparely.data.local.FrozenFundEntity(
            pendingType = pendingType,
            pendingId = pendingId,
            amount = amount,
            createdAt = java.time.LocalDateTime.now(),
            description = description
        )
        return frozenFundDao.insert(frozen)
    }

    suspend fun removeFrozenForPending(pendingType: String, pendingId: Long) {
        frozenFundDao.deleteForPending(pendingType, pendingId)
    }

    suspend fun getTotalFrozenAmount(): Double {
        return frozenFundDao.totalFrozen()
    }

    fun observeFrozenFunds(): Flow<List<com.example.sparely.data.local.FrozenFundEntity>> = frozenFundDao.observeAll()

    suspend fun clearFrozenFunds() {
        frozenFundDao.deleteAll()
    }

    suspend fun upsertFrozenFund(entity: com.example.sparely.data.local.FrozenFundEntity) {
        frozenFundDao.insert(entity)
    }

    private suspend fun syncVaultSchedules(vaultId: Long, schedules: List<VaultSchedule>) {
        val existing = smartVaultDao.getSchedulesForVault(vaultId)
        val now = Instant.now()
        val incoming = schedules.map { schedule ->
            val created = if (schedule.id == 0L) now else schedule.createdAt
            schedule.copy(
                id = schedule.id,
                vaultId = vaultId,
                createdAt = created,
                updatedAt = now
            )
        }

        val incomingById = incoming.associateBy { it.id }

        // Update or delete existing
        val existingIds = existing.map { it.id }.toSet()

        incoming.forEach { schedule ->
            val entity = schedule.toEntity()
            if (schedule.id == 0L) {
                smartVaultDao.upsertSchedule(entity)
            } else {
                smartVaultDao.updateSchedule(entity)
            }
        }

        existing.filter { it.id !in incomingById.keys }.forEach { obsolete ->
            smartVaultDao.deleteSchedule(obsolete)
        }
    }

    suspend fun addVaultSchedule(vaultId: Long, schedule: VaultSchedule): Long {
        val now = Instant.now()
        val toPersist = schedule.copy(
            id = 0L,
            vaultId = vaultId,
            createdAt = now,
            updatedAt = now
        )
        return smartVaultDao.upsertSchedule(toPersist.toEntity())
    }

    suspend fun updateVaultSchedule(schedule: VaultSchedule) {
        if (schedule.id == 0L) {
            addVaultSchedule(schedule.vaultId, schedule)
        } else {
            smartVaultDao.updateSchedule(
                schedule.copy(updatedAt = Instant.now()).toEntity()
            )
        }
    }

    suspend fun deleteVaultSchedule(scheduleId: Long) {
        smartVaultDao.deleteSchedule(scheduleId)
    }

    suspend fun getSchedulesForVault(vaultId: Long): List<VaultSchedule> {
        return smartVaultDao.getSchedulesForVault(vaultId).map { it.toDomain() }
    }

    suspend fun getSmartVaultById(vaultId: Long): SmartVault? {
        val entity = smartVaultDao.getVaultById(vaultId) ?: return null
        val schedules = smartVaultDao.getSchedulesForVault(vaultId).map { it.toDomain() }
        return entity.toDomain(schedules)
    }

    suspend fun recordScheduleExecution(
        scheduleId: Long,
        vaultId: Long,
        amount: Double,
        runTimestamp: LocalDateTime,
        nextRunAt: LocalDateTime?
    ) {
        val scheduleEntity = smartVaultDao.getSchedulesForVault(vaultId).firstOrNull { it.id == scheduleId }
            ?: return

        smartVaultDao.updateSchedule(
            scheduleEntity.copy(
                lastRunAt = runTimestamp,
                nextRunAt = nextRunAt,
                updatedAt = Instant.now(),
                enabled = if (nextRunAt == null) false else scheduleEntity.enabled
            )
        )

        val contribution = VaultContribution(
            vaultId = vaultId,
            amount = amount,
            date = runTimestamp.toLocalDate(),
            source = VaultContributionSource.AUTO_DEPOSIT,
            note = "Scheduled transfer",
            reconciled = true
        )
        smartVaultDao.upsertContribution(contribution.toEntity())
    }

    suspend fun executeVaultTransfer(
        schedule: VaultSchedule,
        amount: Double,
        runTimestamp: LocalDateTime,
        notes: String?
    ): Boolean {
        if (!amount.isValidAmount()) return false
        return withMainAccountLock { database.withTransaction {
            // Read the vault inside the lock so the balance can't change underneath us.
            val vault = smartVaultDao.getVaultById(schedule.vaultId) ?: return@withTransaction false
            when (schedule.direction) {
            VaultTransferDirection.MAIN_TO_VAULT -> {
                val available = if (schedule.onlyIfBalanceAvailable) getAvailableMainAccountBalance() else Double.MAX_VALUE
                if (available + 1e-6 < amount) {
                    false
                } else {
                    val newBalance = vault.currentBalance + amount
                    recordVaultBalanceAdjustment(
                        vaultId = vault.id,
                        previousBalance = vault.currentBalance,
                        newBalance = newBalance,
                        type = VaultAdjustmentType.AUTOMATIC_RECURRING_TRANSFER,
                        reason = notes
                    )
                    val currentMain = getLatestMainAccountBalance()
                    val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                        type = com.example.sparely.data.local.MainAccountTransactionType.VAULT_CONTRIBUTION,
                        amount = amount,
                        balanceAfter = (currentMain - amount).coerceAtLeast(0.0),
                        timestamp = runTimestamp,
                        description = notes ?: "Scheduled transfer to vault ${vault.name}"
                    )
                    insertMainAccountTransaction(transaction)
                    preferencesRepository.updateMainAccountBalance(transaction.balanceAfter)
                    true
                }
            }
            VaultTransferDirection.VAULT_TO_MAIN -> {
                val available = vault.currentBalance
                // Never credit the main account with more than the vault actually held.
                val moved = amount.coerceAtMost(available.coerceAtLeast(0.0))
                if ((schedule.onlyIfBalanceAvailable && available + 1e-6 < amount) || moved <= 0.0) {
                    false
                } else {
                    val newBalance = (vault.currentBalance - moved).coerceAtLeast(0.0)
                    recordVaultBalanceAdjustment(
                        vaultId = vault.id,
                        previousBalance = vault.currentBalance,
                        newBalance = newBalance,
                        type = VaultAdjustmentType.AUTOMATIC_RECURRING_TRANSFER,
                        reason = notes
                    )
                    val currentMain = getLatestMainAccountBalance()
                    val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                        type = com.example.sparely.data.local.MainAccountTransactionType.DEPOSIT,
                        amount = moved,
                        balanceAfter = currentMain + moved,
                        timestamp = runTimestamp,
                        description = notes ?: "Scheduled transfer from vault ${vault.name}"
                    )
                    insertMainAccountTransaction(transaction)
                    preferencesRepository.updateMainAccountBalance(transaction.balanceAfter)
                    true
                }
            }
        } } }
    }

    // Backup & Restore methods
    suspend fun getAllChallenges(): List<SavingsChallenge> =
        challengeDao.observeChallenges().first().map { it.toDomain() }

    suspend fun getAllAchievements(): List<Achievement> =
        achievementDao.observeAchievements().first().map { it.toDomain() }

    suspend fun getAllTransfers(): List<SavingsTransfer> =
        transferDao.observeTransfers().first().map { it.toDomain() }

    suspend fun getAllVaultContributions(): List<VaultContribution> =
        smartVaultDao.getAllContributions().map { it.toDomain() }

    suspend fun getAllVaultAdjustments(): List<VaultBalanceAdjustment> =
        smartVaultDao.getAllAdjustments().map { it.toDomain() }

    suspend fun getAllAllocationHistory(): List<com.example.sparely.data.local.AllocationHistoryEntity> =
        allocationHistoryDao.observeAll().first()

    suspend fun clearChallenges() {
        challengeDao.deleteAllMilestones()
        challengeDao.deleteAllChallenges()
    }

    suspend fun clearVaultData() {
        smartVaultDao.clearAllContributions()
        smartVaultDao.clearAllAdjustments()
        smartVaultDao.clearAllSchedules()
    }

    suspend fun clearAllocationHistory() {
        allocationHistoryDao.deleteAll()
    }

    suspend fun insertAchievements(achievements: List<Achievement>) {
        achievementDao.upsertAll(achievements.map { it.toEntity() })
    }

    suspend fun insertTransfers(transfers: List<SavingsTransfer>) {
        transfers.forEach { transferDao.upsert(it.toEntity()) }
    }

    suspend fun insertVaultContributions(contributions: List<VaultContribution>) {
        contributions.forEach { smartVaultDao.upsertContribution(it.toEntity()) }
    }

    suspend fun insertVaultAdjustments(adjustments: List<VaultBalanceAdjustment>) {
        adjustments.forEach { smartVaultDao.insertAdjustment(it.toEntity()) }
    }

    suspend fun insertAllocationHistory(history: List<com.example.sparely.data.local.AllocationHistoryEntity>) {
        history.forEach { allocationHistoryDao.insert(it) }
    }

    // Store functions
    fun observeStores(): Flow<List<Store>> =
        storeDao.observeStores().map { entities -> entities.map { it.toDomain() } }

    suspend fun searchStores(query: String): List<Store> =
        storeDao.searchStores(query).map { it.toDomain() }

    suspend fun getStoreById(id: Long): Store? =
        storeDao.getStoreById(id)?.toDomain()

    suspend fun insertStore(store: Store): Long =
        storeDao.insertStore(store.toEntity())

    suspend fun updateStore(store: Store) {
        storeDao.updateStore(store.toEntity())
    }

    suspend fun deleteStore(store: Store) {
        storeDao.deleteStore(store.toEntity())
    }

    suspend fun clearStores() {
        storeDao.clearAll()
    }

    // Payment Method functions
    fun observePaymentMethods(): Flow<List<PaymentMethod>> =
        paymentMethodDao.getAllPaymentMethods().map { entities -> entities.map { it.toDomain() } }

    suspend fun getPaymentMethodById(id: Long): PaymentMethod? =
        paymentMethodDao.getPaymentMethodById(id)?.toDomain()

    suspend fun insertPaymentMethod(method: PaymentMethod): Long {
        if (method.isDefault) {
            paymentMethodDao.clearDefaultPaymentMethod()
        }
        return paymentMethodDao.insertPaymentMethod(method.toEntity())
    }

    suspend fun updatePaymentMethod(method: PaymentMethod) {
        if (method.isDefault) {
            paymentMethodDao.clearDefaultPaymentMethod()
        }
        paymentMethodDao.updatePaymentMethod(method.toEntity())
    }

    suspend fun deletePaymentMethod(method: PaymentMethod) {
        paymentMethodDao.deletePaymentMethod(method.toEntity())
    }

    suspend fun clearPaymentMethods() {
        paymentMethodDao.deleteAll()
         // Re-seed defaults if needed, but for now we just clear
    }

    // Credit Card specific functions
    fun observeCreditCards(): Flow<List<PaymentMethod>> =
        paymentMethodDao.getCreditCards().map { entities -> entities.map { it.toDomain() } }

    suspend fun addToCreditCardBalance(paymentMethodId: Long, amount: Double) {
        paymentMethodDao.addToBalance(paymentMethodId, amount)
    }

    suspend fun recordCreditCardPayment(
        paymentMethodId: Long,
        amount: Double,
        note: String? = null,
        date: LocalDate = LocalDate.now(),
        deductFromMainAccount: Boolean = false
    ) {
        if (!amount.isValidAmount()) return
        withMainAccountLock { database.withTransaction {
            // Insert payment record
            val payment = com.example.sparely.data.local.CreditCardPaymentEntity(
                paymentMethodId = paymentMethodId,
                amount = amount,
                date = date,
                note = note
            )
            creditCardPaymentDao.insertPayment(payment)
            // Update balance on payment method
            paymentMethodDao.recordPayment(paymentMethodId, amount, date)

            // If deducting from main account, update balance and log transaction
            if (deductFromMainAccount) {
                val paymentMethod = paymentMethodDao.getPaymentMethodById(paymentMethodId)
                val cardName = paymentMethod?.name ?: "Credit Card"

                // Same source of truth as every other main-account operation (latest
                // transaction), not the settings snapshot which can lag behind it.
                val currentBalance = getLatestMainAccountBalance()
                val newBalance = (currentBalance - amount).coerceAtLeast(0.0)

                // Log transaction with CREDIT_CARD_PAYMENT type. Amounts are stored positive;
                // the type determines the direction (matches the other transaction types).
                val transaction = com.example.sparely.data.local.MainAccountTransactionEntity(
                    type = com.example.sparely.data.local.MainAccountTransactionType.CREDIT_CARD_PAYMENT,
                    amount = amount,
                    balanceAfter = newBalance,
                    timestamp = java.time.LocalDateTime.now(),
                    description = "Payment to $cardName${note?.let { ": $it" } ?: ""}"
                )
                mainAccountDao.insertTransaction(transaction)
                preferencesRepository.updateMainAccountBalance(newBalance)
            }
        } }
    }

    suspend fun getAllCreditCardPayments(): List<com.example.sparely.domain.model.CreditCardPayment> =
        creditCardPaymentDao.getAllPayments().first().map { it.toDomain() }

    fun observeCreditCardPayments(): Flow<List<com.example.sparely.domain.model.CreditCardPayment>> =
        creditCardPaymentDao.getAllPayments().map { entities -> entities.map { it.toDomain() } }

    suspend fun insertCreditCardPayment(payment: com.example.sparely.domain.model.CreditCardPayment) {
        creditCardPaymentDao.insertPayment(payment.toEntity())
    }

    suspend fun clearCreditCardPayments() {
        creditCardPaymentDao.deleteAll()
    }

    /**
     * Process a recurring expense payment with full expense logic.
     * This mirrors the logic in SparelyViewModel.addExpense() but runs in the background worker context.
     * Handles:
     * - Creating expense with all field mappings (storeId, paymentMethodId, isRecurring)
     * - Credit card balance updates
     * - Vault contributions (saving tax)
     * - Main account deductions
     * - Advancing the recurring schedule (callers must not also call updateRecurringExpenseProcessed)
     */
    suspend fun processRecurringExpensePayment(
        recurringEntity: com.example.sparely.data.local.RecurringExpenseEntity,
        processDate: LocalDate,
        settings: com.example.sparely.domain.model.SparelySettings,
        vaults: List<com.example.sparely.domain.model.SmartVault>
    ): Unit = withMainAccountLock { database.withTransaction {
        if (!recurringEntity.amount.isValidAmount()) return@withTransaction
        // Create expense entity with all field mappings
        val percentages = if (recurringEntity.manualPercentEmergency != null) {
            com.example.sparely.domain.model.SavingsPercentages(
                emergency = recurringEntity.manualPercentEmergency,
                invest = recurringEntity.manualPercentInvest ?: 0.0,
                `fun` = recurringEntity.manualPercentFun ?: 0.0,
                safeInvestmentSplit = recurringEntity.manualSafeSplit ?: 0.5
            )
        } else {
            settings.defaultPercentages
        }
        
        val adjusted = percentages.adjustWithinBudget()
        val amount = recurringEntity.amount
        val emergency = amount * adjusted.emergency
        val invest = amount * adjusted.invest
        val funAmount = amount * adjusted.`fun`
        val safe = invest * adjusted.safeInvestmentSplit
        val risky = invest - safe
        
        val expenseEntity = com.example.sparely.data.local.ExpenseEntity(
            id = 0L,
            description = recurringEntity.description,
            amount = amount,
            category = recurringEntity.category,
            date = processDate,
            includesTax = recurringEntity.includesTax,
            emergencyAmount = emergency.roundCurrency(),
            investmentAmount = invest.roundCurrency(),
            funAmount = funAmount.roundCurrency(),
            safeInvestmentAmount = safe.roundCurrency(),
            highRiskInvestmentAmount = risky.roundCurrency(),
            autoRecommended = false,
            appliedPercentEmergency = adjusted.emergency,
            appliedPercentInvest = adjusted.invest,
            appliedPercentFun = adjusted.`fun`,
            appliedSafeSplit = adjusted.safeInvestmentSplit,
            riskLevelUsed = settings.riskLevel,
            deductedFromVaultId = recurringEntity.deductedFromVaultId,
            storeId = recurringEntity.storeId,
            paymentMethodId = recurringEntity.paymentMethodId,
            isRecurring = true,
            type = recurringEntity.type
        )
        val createdExpense = upsertExpense(expenseEntity)

        // Link assets from recurring expense if any
        val recurringDomain = recurringEntity.toDomain()
        recurringDomain.assetAllocations.forEach { (assetId, percentage) ->
            linkExpenseToAsset(createdExpense, assetId, percentage)
        }

        withMainAccountLock {
        // Get current balance
        var currentBalance = getLatestMainAccountBalance()

        // Handle vault deduction if specified
        if (recurringEntity.deductedFromVaultId != null) {
            // Re-read the vault inside the lock: the list passed in may be stale by now.
            val vault = smartVaultDao.getVaultById(recurringEntity.deductedFromVaultId)
            if (vault != null) {
                val vaultBalanceBefore = vault.currentBalance
                val deductFromVault = amount.coerceAtMost(vaultBalanceBefore)
                val overflowToMainAccount = (amount - vaultBalanceBefore).coerceAtLeast(0.0)
                val vaultBalanceAfter = (vaultBalanceBefore - deductFromVault).coerceAtLeast(0.0)
                
                if (deductFromVault > 0.0) {
                    recordVaultBalanceAdjustment(
                        vaultId = vault.id,
                        previousBalance = vaultBalanceBefore,
                        newBalance = vaultBalanceAfter,
                        type = VaultAdjustmentType.MANUAL_DEDUCTION,
                        reason = "Recurring expense: ${recurringEntity.description.take(100)}"
                    )
                }
                
                if (overflowToMainAccount > 0.0 && recurringEntity.deductFromMainAccount) {
                    val newBalance = (currentBalance - overflowToMainAccount).coerceAtLeast(0.0)
                    val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                        type = com.example.sparely.data.local.MainAccountTransactionType.EXPENSE,
                        amount = overflowToMainAccount,
                        balanceAfter = newBalance,
                        timestamp = java.time.LocalDateTime.now(),
                        description = "Overflow from ${vault.name} - recurring: ${recurringEntity.description.take(70)}",
                        relatedExpenseId = createdExpense
                    )
                    insertMainAccountTransaction(transaction)
                    preferencesRepository.updateMainAccountBalance(newBalance)
                    currentBalance = newBalance
                }
            }
        } else if (recurringEntity.deductFromMainAccount) {
            val newBalance = (currentBalance - amount).coerceAtLeast(0.0)
            val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                type = com.example.sparely.data.local.MainAccountTransactionType.EXPENSE,
                amount = amount,
                balanceAfter = newBalance,
                timestamp = java.time.LocalDateTime.now(),
                description = "Auto-logged recurring: ${recurringEntity.description.take(100)}",
                        relatedExpenseId = createdExpense
            )
            insertMainAccountTransaction(transaction)
            preferencesRepository.updateMainAccountBalance(newBalance)
            currentBalance = newBalance
        }
        
        
        // Apply saving tax to vaults
        val savingTaxContext = com.example.sparely.domain.logic.SavingTaxEngine.Context(
            expenseAmount = amount,
            expenseDate = processDate,
            settings = settings,
            vaults = vaults,
            // Without this the engine assumes an unlimited balance and ignores the user's
            // minimum main-account balance protection.
            currentMainAccountBalance = currentBalance
        )
        val savingTaxPlans = com.example.sparely.domain.logic.SavingTaxEngine.calculate(savingTaxContext)
        
        if (savingTaxPlans.isNotEmpty()) {
            val contributions = savingTaxPlans.map { plan ->
                VaultContribution(
                    vaultId = plan.vaultId,
                    amount = plan.amount,
                    date = processDate,
                    source = VaultContributionSource.SAVING_TAX,
                    note = "Saving tax from recurring: ${recurringEntity.description}".take(120)
                )
            }
            val contributionIds = logVaultContributions(contributions)
            
            val totalSavingTax = savingTaxPlans.sumOf { it.amount }
            if (totalSavingTax > 0.0) {
                val newBalance = (currentBalance - totalSavingTax).coerceAtLeast(0.0)
                val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                    type = com.example.sparely.data.local.MainAccountTransactionType.VAULT_CONTRIBUTION,
                    amount = totalSavingTax,
                    balanceAfter = newBalance,
                    timestamp = java.time.LocalDateTime.now(),
                    description = "Saving tax to ${savingTaxPlans.size} vault(s)",
                    relatedVaultContributionIds = contributionIds
                )
                insertMainAccountTransaction(transaction)
                preferencesRepository.updateMainAccountBalance(newBalance)
            }
        }
        }

        // Advance the schedule in the same transaction as the charge, so a failure between the
        // two can't make the next worker run charge this occurrence again.
        updateRecurringExpenseProcessed(recurringEntity.id, processDate)
    } }

    private fun Double.roundCurrency(): Double = kotlin.math.round(this * 100) / 100.0

    // Expense Item methods
    fun observeItemsForExpense(expenseId: Long): Flow<List<com.example.sparely.domain.model.ExpenseItem>> =
        expenseItemDao.observeItemsForExpense(expenseId).map { entities -> entities.map { it.toDomain() } }

    suspend fun getItemsForExpense(expenseId: Long): List<com.example.sparely.domain.model.ExpenseItem> =
        expenseItemDao.getItemsForExpense(expenseId).map { it.toDomain() }

    suspend fun getAllExpenseItems(): List<com.example.sparely.domain.model.ExpenseItem> =
        expenseItemDao.getAllItems().map { it.toDomain() }

    suspend fun insertExpenseItem(item: com.example.sparely.domain.model.ExpenseItem): Long =
        expenseItemDao.insertItem(item.toEntity())

    suspend fun insertExpenseItems(items: List<com.example.sparely.domain.model.ExpenseItem>) {
        expenseItemDao.insertItems(items.map { it.toEntity() })
    }

    suspend fun updateExpenseItem(item: com.example.sparely.domain.model.ExpenseItem) {
        expenseItemDao.updateItem(item.toEntity())
    }

    suspend fun deleteExpenseItem(item: com.example.sparely.domain.model.ExpenseItem) {
        expenseItemDao.deleteItem(item.toEntity())
    }

    suspend fun deleteItemsForExpense(expenseId: Long) {
        expenseItemDao.deleteItemsForExpense(expenseId)
    }

    suspend fun clearExpenseItems() {
        expenseItemDao.clearAll()
    }

    // ============================================================================
    // Assets, Wishlists, and Refunds - Backup Support
    // ============================================================================

    suspend fun clearAssets() {
        assetDao.clearAll()
    }

    suspend fun clearAssetExpenseLinks() {
        assetExpenseLinkDao.clearAll()
    }

    suspend fun clearWishlists() {
        wishlistDao.clearAll()
    }

    suspend fun clearWishlistSavings() {
        wishlistSavingsDao.clearAll()
    }

    suspend fun clearExpenseRefunds() {
        expenseRefundDao.clearAll()
    }

    suspend fun getAllAssetExpenseLinks(): List<com.example.sparely.domain.model.AssetExpenseLink> =
        assetExpenseLinkDao.getAllLinks().map { it.toDomain() }

    suspend fun getAllWishlistSavings(): List<com.example.sparely.data.local.WishlistSavingsEntity> =
        wishlistSavingsDao.getAllSavings()

    suspend fun getAllExpenseRefunds(): List<com.example.sparely.domain.model.ExpenseRefund> =
        expenseRefundDao.getAllRefunds().map { it.toDomain() }

    suspend fun insertAssetExpenseLink(link: com.example.sparely.domain.model.AssetExpenseLink) {
        assetExpenseLinkDao.insertLink(link.toEntity())
    }

    suspend fun insertWishlistSavings(savings: com.example.sparely.data.local.WishlistSavingsEntity) {
        wishlistSavingsDao.insertSavings(savings)
    }

    suspend fun upsertWishlist(wishlist: com.example.sparely.domain.model.Wishlist): Long {
        return if (wishlist.id == 0L) {
            addWishlist(wishlist)
        } else {
            updateWishlist(wishlist)
            wishlist.id
        }
    }

    // ============================================================================
    // HISA (Savings Account) Transfer & Transaction History
    // ============================================================================

    /**
     * Transfer money FROM Main Account TO Savings Account.
     * This decreases Main Account balance and increases HISA balance.
     */
    suspend fun transferToSavingsAccount(
        accountId: Long,
        amount: Double,
        description: String = "Transfer to HISA"
    ) {
        if (!amount.isValidAmount()) return

        withMainAccountLock { database.withTransaction {
            // Get current balances
            val account = savingsAccountDao.getAccountById(accountId) ?: return@withTransaction
            // Latest transaction balance is the canonical main-account balance used everywhere else.
            val currentMainBalance = getLatestMainAccountBalance()

            // Update main account balance
            val newMainBalance = (currentMainBalance - amount).coerceAtLeast(0.0).roundCurrency()
            preferencesRepository.updateMainAccountBalance(newMainBalance)

            // Update savings account balance
            savingsAccountDao.incrementBalance(accountId, amount)
            val newHisaBalance = (account.currentBalance + amount).roundCurrency()

            val now = LocalDateTime.now()

            // Log main account transaction (negative amount = outflow)
            val mainTxId = mainAccountDao.insertTransaction(
                MainAccountTransactionEntity(
                    type = MainAccountTransactionType.HISA_TRANSFER,
                    amount = -amount, // Negative = outflow from main
                    balanceAfter = newMainBalance,
                    timestamp = now,
                    description = description
                )
            )

            // Log HISA transaction
            savingsAccountTransactionDao.insert(
                SavingsAccountTransactionEntity(
                    accountId = accountId,
                    type = SavingsAccountTransactionType.TRANSFER_IN,
                    amount = amount,
                    balanceAfter = newHisaBalance,
                    timestamp = now,
                    description = description,
                    relatedMainAccountTransactionId = mainTxId
                )
            )
        } }
    }

    /**
     * Transfer money FROM Savings Account TO Main Account.
     * This increases Main Account balance and decreases HISA balance.
     */
    suspend fun transferFromSavingsAccount(
        accountId: Long,
        amount: Double,
        description: String = "Transfer from HISA"
    ) {
        if (!amount.isValidAmount()) return

        withMainAccountLock { database.withTransaction {
            // Get current balances
            val account = savingsAccountDao.getAccountById(accountId) ?: return@withTransaction
            if (account.currentBalance < amount) return@withTransaction // Insufficient funds

            // Latest transaction balance is the canonical main-account balance used everywhere else.
            val currentMainBalance = getLatestMainAccountBalance()

            // Update main account balance
            val newMainBalance = (currentMainBalance + amount).roundCurrency()
            preferencesRepository.updateMainAccountBalance(newMainBalance)

            // Update savings account balance
            savingsAccountDao.incrementBalance(accountId, -amount)
            val newHisaBalance = (account.currentBalance - amount).roundCurrency()

            val now = LocalDateTime.now()

            // Log main account transaction (positive amount = inflow)
            val mainTxId = mainAccountDao.insertTransaction(
                MainAccountTransactionEntity(
                    type = MainAccountTransactionType.HISA_TRANSFER,
                    amount = amount, // Positive = inflow to main
                    balanceAfter = newMainBalance,
                    timestamp = now,
                    description = description
                )
            )

            // Log HISA transaction
            savingsAccountTransactionDao.insert(
                SavingsAccountTransactionEntity(
                    accountId = accountId,
                    type = SavingsAccountTransactionType.TRANSFER_OUT,
                    amount = amount,
                    balanceAfter = newHisaBalance,
                    timestamp = now,
                    description = description,
                    relatedMainAccountTransactionId = mainTxId
                )
            )
        } }
    }

    /**
     * Record interest earned on a HISA with transaction logging.
     */
    suspend fun recordInterestWithTransaction(
        accountId: Long,
        amount: Double,
        entryDate: LocalDate = LocalDate.now()
    ) {
        if (!amount.isValidAmount()) return
        
        database.withTransaction {
            // Update balance and interest totals
            savingsAccountDao.recordInterestEarned(accountId, amount, entryDate)
            
            // Get new balance
            val account = savingsAccountDao.getAccountById(accountId) ?: return@withTransaction
            
            // Log the transaction
            savingsAccountTransactionDao.insert(
                SavingsAccountTransactionEntity(
                    accountId = accountId,
                    type = SavingsAccountTransactionType.INTEREST,
                    amount = amount,
                    balanceAfter = account.currentBalance,
                    timestamp = entryDate.atStartOfDay(),
                    description = "Interest credited"
                )
            )
        }
    }

    /**
     * Observe all transactions for a savings account.
     */
    fun observeSavingsAccountTransactions(accountId: Long): Flow<List<SavingsAccountTransaction>> =
        savingsAccountTransactionDao.observeTransactionsForAccount(accountId).map { entities ->
            entities.map { it.toDomain() }
        }

    /**
     * Get recent transactions for a savings account.
     */
    suspend fun getRecentSavingsAccountTransactions(accountId: Long, limit: Int = 20): List<SavingsAccountTransaction> =
        savingsAccountTransactionDao.getRecentTransactions(accountId, limit).map { it.toDomain() }

    /**
     * Get total balance across all non-archived HISA accounts.
     */
    suspend fun getTotalHisaBalance(): Double =
        savingsAccountDao.getTotalBalance()

    /**
     * Process a variable recurring expense with a user-confirmed amount.
     * Updates the amount history and removes from pending list.
     */
    suspend fun processVariableRecurringExpenseWithAmount(
        recurringExpenseId: Long,
        actualAmount: Double,
        processDate: LocalDate,
        settings: com.example.sparely.domain.model.SparelySettings,
        vaults: List<com.example.sparely.domain.model.SmartVault>
    ): Unit = withMainAccountLock { database.withTransaction {
        if (!actualAmount.isValidAmount()) return@withTransaction
        val recurringEntity = recurringExpenseDao.getById(recurringExpenseId) ?: return@withTransaction

        // Update amount history in the entity
        val currentHistory = try {
            recurringEntity.amountHistoryJson?.let {
                com.example.sparely.data.local.Converters().fromAmountHistoryJson(it)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val updatedHistory = (currentHistory + com.example.sparely.domain.model.AmountHistoryEntry(
            date = processDate,
            amount = actualAmount
        )).takeLast(24)  // Keep last 24 entries to limit storage

        val historySerialized = com.example.sparely.data.local.Converters().toAmountHistoryJson(updatedHistory)

        val updatedEntity = recurringEntity.copy(
            amountHistoryJson = historySerialized,
            estimatedAmount = actualAmount
        )
        recurringExpenseDao.update(updatedEntity)

        // Create expense with the actual amount
        val percentages = if (recurringEntity.manualPercentEmergency != null) {
            com.example.sparely.domain.model.SavingsPercentages(
                emergency = recurringEntity.manualPercentEmergency,
                invest = recurringEntity.manualPercentInvest ?: 0.0,
                `fun` = recurringEntity.manualPercentFun ?: 0.0,
                safeInvestmentSplit = recurringEntity.manualSafeSplit ?: 0.5
            )
        } else {
            settings.defaultPercentages
        }

        val adjusted = percentages.adjustWithinBudget()
        val amount = actualAmount  // Use actual amount instead of stored amount
        val emergency = amount * adjusted.emergency
        val invest = amount * adjusted.invest
        val funAmount = amount * adjusted.`fun`
        val safe = invest * adjusted.safeInvestmentSplit
        val risky = invest - safe

        val expenseEntity = com.example.sparely.data.local.ExpenseEntity(
            id = 0L,
            description = recurringEntity.description,
            amount = amount,
            category = recurringEntity.category,
            date = processDate,
            includesTax = recurringEntity.includesTax,
            emergencyAmount = emergency.roundCurrency(),
            investmentAmount = invest.roundCurrency(),
            funAmount = funAmount.roundCurrency(),
            safeInvestmentAmount = safe.roundCurrency(),
            highRiskInvestmentAmount = risky.roundCurrency(),
            autoRecommended = false,
            appliedPercentEmergency = adjusted.emergency,
            appliedPercentInvest = adjusted.invest,
            appliedPercentFun = adjusted.`fun`,
            appliedSafeSplit = adjusted.safeInvestmentSplit,
            riskLevelUsed = settings.riskLevel,
            deductedFromVaultId = recurringEntity.deductedFromVaultId,
            storeId = recurringEntity.storeId,
            paymentMethodId = recurringEntity.paymentMethodId,
            isRecurring = true,
            type = recurringEntity.type
        )
        val createdExpense = upsertExpense(expenseEntity)

        // Link assets from recurring expense if any
        val recurringDomain = updatedEntity.toDomain()
        recurringDomain.assetAllocations.forEach { (assetId, percentage) ->
            linkExpenseToAsset(createdExpense, assetId, percentage)
        }

        withMainAccountLock {
        // Get current balance
        var currentBalance = getLatestMainAccountBalance()

        // Handle vault deduction if specified
        if (recurringEntity.deductedFromVaultId != null) {
            // Re-read the vault inside the lock: the list passed in may be stale by now.
            val vault = smartVaultDao.getVaultById(recurringEntity.deductedFromVaultId)
            if (vault != null) {
                val vaultBalanceBefore = vault.currentBalance
                val deductFromVault = amount.coerceAtMost(vaultBalanceBefore)
                val overflowToMainAccount = (amount - vaultBalanceBefore).coerceAtLeast(0.0)
                val vaultBalanceAfter = (vaultBalanceBefore - deductFromVault).coerceAtLeast(0.0)

                if (deductFromVault > 0.0) {
                    recordVaultBalanceAdjustment(
                        vaultId = vault.id,
                        previousBalance = vaultBalanceBefore,
                        newBalance = vaultBalanceAfter,
                        type = VaultAdjustmentType.MANUAL_DEDUCTION,
                        reason = "Variable recurring expense: ${recurringEntity.description.take(100)}"
                    )
                }

                if (overflowToMainAccount > 0.0 && recurringEntity.deductFromMainAccount) {
                    val newBalance = (currentBalance - overflowToMainAccount).coerceAtLeast(0.0)
                    val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                        type = com.example.sparely.data.local.MainAccountTransactionType.EXPENSE,
                        amount = overflowToMainAccount,
                        balanceAfter = newBalance,
                        timestamp = java.time.LocalDateTime.now(),
                        description = "Overflow from ${vault.name} - variable recurring: ${recurringEntity.description.take(70)}",
                        relatedExpenseId = createdExpense
                    )
                    insertMainAccountTransaction(transaction)
                    preferencesRepository.updateMainAccountBalance(newBalance)
                    currentBalance = newBalance
                }
            }
        } else if (recurringEntity.deductFromMainAccount) {
            val newBalance = (currentBalance - amount).coerceAtLeast(0.0)
            val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                type = com.example.sparely.data.local.MainAccountTransactionType.EXPENSE,
                amount = amount,
                balanceAfter = newBalance,
                timestamp = java.time.LocalDateTime.now(),
                description = "Variable amount: ${recurringEntity.description.take(100)}",
                        relatedExpenseId = createdExpense
            )
            insertMainAccountTransaction(transaction)
            preferencesRepository.updateMainAccountBalance(newBalance)
            currentBalance = newBalance
        }

        // Apply saving tax to vaults
        val savingTaxContext = com.example.sparely.domain.logic.SavingTaxEngine.Context(
            expenseAmount = amount,
            expenseDate = processDate,
            settings = settings,
            vaults = vaults,
            // Without this the engine assumes an unlimited balance and ignores the user's
            // minimum main-account balance protection.
            currentMainAccountBalance = currentBalance
        )
        val savingTaxPlans = com.example.sparely.domain.logic.SavingTaxEngine.calculate(savingTaxContext)

        if (savingTaxPlans.isNotEmpty()) {
            val contributions = savingTaxPlans.map { plan ->
                VaultContribution(
                    vaultId = plan.vaultId,
                    amount = plan.amount,
                    date = processDate,
                    source = VaultContributionSource.SAVING_TAX,
                    note = "Saving tax from variable recurring: ${recurringEntity.description}".take(120)
                )
            }
            val contributionIds = logVaultContributions(contributions)

            val totalSavingTax = savingTaxPlans.sumOf { it.amount }
            if (totalSavingTax > 0.0) {
                val newBalance = (currentBalance - totalSavingTax).coerceAtLeast(0.0)
                val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                    type = com.example.sparely.data.local.MainAccountTransactionType.VAULT_CONTRIBUTION,
                    amount = totalSavingTax,
                    balanceAfter = newBalance,
                    timestamp = java.time.LocalDateTime.now(),
                    description = "Saving tax to ${savingTaxPlans.size} vault(s)",
                    relatedVaultContributionIds = contributionIds
                )
                insertMainAccountTransaction(transaction)
                preferencesRepository.updateMainAccountBalance(newBalance)
            }
        }
        }

        // Mark as processed and remove from pending list
        updateRecurringExpenseProcessed(recurringExpenseId, processDate)
        pendingVariableRecurringExpenseDao.deleteByRecurringExpenseId(recurringExpenseId)
    } }

    /*
    // TODO: These functions need proper implementation with correct DAO methods
    suspend fun getPendingVariableRecurringExpenses(): List<Pair<com.example.sparely.domain.model.RecurringExpense, Double>> {
        return pendingVariableRecurringExpenseDao.getAll().mapNotNull { pending ->
            val recurring = recurringExpenseDao.getById(pending.recurringExpenseId)
            if (recurring != null) {
                Pair(recurring.toDomain(), pending.predictedAmount)
            } else {
                null
            }
        }
    }

    fun observePendingVariableRecurringExpenses(): Flow<List<Pair<com.example.sparely.domain.model.RecurringExpense, Double>>> {
        return pendingVariableRecurringExpenseDao.observeAll().map { pendingList ->
            pendingList.mapNotNull { pending ->
                val recurring = recurringExpenseDao.observeRecurringExpenses().first().find { it.id == pending.recurringExpenseId }
                if (recurring != null) {
                    Pair(recurring.toDomain(), pending.predictedAmount)
                } else {
                    null
                }
            }
        }
    }
    */

    suspend fun getAssetCostProjection(
        assetId: Long,
        months: Int = 12
    ): com.example.sparely.domain.model.AssetCostProjection? {
        val assetEntity = assetDao.getAssetById(assetId) ?: return null

        val linkedExpensesWithPercentage = getExpensesLinkedToAsset(assetId)
        val linkedRecurringWithPercentage = getRecurringExpensesLinkedToAsset(assetId)

        return com.example.sparely.domain.logic.AssetProjectionEngine.calculateAssetProjections(
            asset = assetEntity.toDomain(),
            linkedExpenses = linkedExpensesWithPercentage,
            linkedRecurringExpenses = linkedRecurringWithPercentage,
            months = months
        )
    }

    fun observeAssetCostProjection(
        assetId: Long,
        months: Int = 12
    ): Flow<com.example.sparely.domain.model.AssetCostProjection?> {
        return observeActiveAssets().transformLatest { assets ->
            val asset = assets.find { it.id == assetId }
            if (asset == null) {
                emit(null)
            } else {
                val linkedExpensesWithPercentage = getExpensesLinkedToAsset(assetId)
                val linkedRecurringWithPercentage = getRecurringExpensesLinkedToAsset(assetId)

                val projection = com.example.sparely.domain.logic.AssetProjectionEngine.calculateAssetProjections(
                    asset = asset,
                    linkedExpenses = linkedExpensesWithPercentage,
                    linkedRecurringExpenses = linkedRecurringWithPercentage,
                    months = months
                )
                emit(projection)
            }
        }
    }

    private suspend fun getRecurringExpensesLinkedToAsset(
        assetId: Long
    ): List<Pair<com.example.sparely.domain.model.RecurringExpense, Double>> {
        return recurringExpenseDao.getAll()
            .map { it.toDomain() }
            .mapNotNull { recurring ->
                val percentage = recurring.assetAllocations[assetId] ?: return@mapNotNull null
                recurring to percentage
            }
    }
}

// Extension to convert entity to domain model
private fun SavingsAccountTransactionEntity.toDomain(): SavingsAccountTransaction =
    SavingsAccountTransaction(
        id = id,
        accountId = accountId,
        type = when (type) {
            SavingsAccountTransactionType.DEPOSIT -> SavingsAccountTransactionDisplayType.DEPOSIT
            SavingsAccountTransactionType.WITHDRAWAL -> SavingsAccountTransactionDisplayType.WITHDRAWAL
            SavingsAccountTransactionType.INTEREST -> SavingsAccountTransactionDisplayType.INTEREST
            SavingsAccountTransactionType.TRANSFER_IN -> SavingsAccountTransactionDisplayType.TRANSFER_IN
            SavingsAccountTransactionType.TRANSFER_OUT -> SavingsAccountTransactionDisplayType.TRANSFER_OUT
        },
        amount = amount,
        balanceAfter = balanceAfter,
        timestamp = timestamp,
        description = description
    )
