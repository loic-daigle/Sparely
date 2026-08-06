package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsAccountTransactionDao {
    
    @Query("SELECT * FROM savings_account_transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun observeTransactionsForAccount(accountId: Long): Flow<List<SavingsAccountTransactionEntity>>
    
    @Query("SELECT * FROM savings_account_transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
    suspend fun getTransactionsForAccount(accountId: Long): List<SavingsAccountTransactionEntity>
    
    @Query("SELECT * FROM savings_account_transactions WHERE accountId = :accountId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTransactions(accountId: Long, limit: Int): List<SavingsAccountTransactionEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: SavingsAccountTransactionEntity): Long
    
    @Query("DELETE FROM savings_account_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM savings_account_transactions WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: Long)
    
    @Query("DELETE FROM savings_account_transactions")
    suspend fun clearAll()
}
