package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface MainAccountDao {
    @androidx.room.Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: MainAccountTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionVaultCrossRef(crossRef: TransactionVaultContributionCrossRef)

    @androidx.room.Transaction
    @Query("SELECT * FROM main_account_transactions ORDER BY timestamp DESC, id DESC")
    fun observeAllTransactions(): Flow<List<MainAccountTransactionDetails>>

    @androidx.room.Transaction
    @Query("SELECT * FROM main_account_transactions ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun getRecentTransactions(limit: Int = 50): List<MainAccountTransactionDetails>

    @androidx.room.Transaction
    @Query("SELECT * FROM main_account_transactions WHERE timestamp >= :since ORDER BY timestamp DESC, id DESC")
    suspend fun getTransactionsSince(since: LocalDateTime): List<MainAccountTransactionDetails>

    @androidx.room.Transaction
    @Query("SELECT * FROM main_account_transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): MainAccountTransactionDetails?

    @androidx.room.Transaction
    @Query("SELECT * FROM main_account_transactions ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getLatestTransaction(): MainAccountTransactionDetails?

    @Query("SELECT COUNT(*) FROM transaction_vault_contribution_cross_ref WHERE contributionId = :contributionId")
    suspend fun countTransactionsForContribution(contributionId: Long): Int

    @Query("DELETE FROM main_account_transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Query("DELETE FROM main_account_transactions")
    suspend fun deleteAllTransactions()

    @Query("SELECT IFNULL(SUM(CASE WHEN type = 'DEPOSIT' THEN amount WHEN type = 'WITHDRAWAL' THEN -amount WHEN type = 'EXPENSE' THEN -amount WHEN type = 'VAULT_CONTRIBUTION' THEN -amount WHEN type = 'ADJUSTMENT' THEN amount WHEN type = 'CREDIT_CARD_PAYMENT' THEN -ABS(amount) WHEN type = 'HISA_TRANSFER' THEN amount ELSE 0 END), 0.0) FROM main_account_transactions")
    suspend fun calculateBalanceFromTransactions(): Double
}
