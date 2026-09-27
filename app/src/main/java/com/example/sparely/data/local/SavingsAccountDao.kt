package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SavingsAccountDao {
    
    @Query("SELECT * FROM savings_accounts WHERE archived = 0 ORDER BY isMainOverflowAccount DESC, name ASC")
    fun observeAccounts(): Flow<List<SavingsAccountEntity>>
    
    @Query("SELECT * FROM savings_accounts ORDER BY isMainOverflowAccount DESC, name ASC")
    fun observeAllAccounts(): Flow<List<SavingsAccountEntity>>
    
    @Query("SELECT * FROM savings_accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): SavingsAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: SavingsAccountEntity): Long

    @Query("DELETE FROM savings_accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM savings_accounts")
    suspend fun clearAll()

    @Query("UPDATE savings_accounts SET currentBalance = :balance WHERE id = :id")
    suspend fun updateBalance(id: Long, balance: Double)

    @Query("UPDATE savings_accounts SET currentBalance = currentBalance + :delta WHERE id = :id")
    suspend fun incrementBalance(id: Long, delta: Double)
    
    /** Record interest earned: add to balance and to total interest */
    @Query("""
        UPDATE savings_accounts 
        SET currentBalance = currentBalance + :amount,
            totalInterestEarned = totalInterestEarned + :amount,
            lastInterestEntryDate = :entryDate
        WHERE id = :id
    """)
    suspend fun recordInterestEarned(id: Long, amount: Double, entryDate: LocalDate)

    @Query("SELECT IFNULL(SUM(currentBalance), 0.0) FROM savings_accounts WHERE archived = 0")
    suspend fun getTotalBalance(): Double
    
    @Query("SELECT * FROM savings_accounts WHERE isMainOverflowAccount = 1 AND archived = 0 LIMIT 1")
    suspend fun getMainOverflowAccount(): SavingsAccountEntity?
    
    @Transaction
    suspend fun setAsMainOverflowAccount(id: Long) {
        // Clear existing overflow designation
        clearMainOverflowFlag()
        // Set new overflow account
        upsert(getAccountById(id)?.copy(isMainOverflowAccount = true) ?: return)
    }
    
    @Query("UPDATE savings_accounts SET isMainOverflowAccount = 0")
    suspend fun clearMainOverflowFlag()
    
    @Query("UPDATE savings_accounts SET archived = 1 WHERE id = :id")
    suspend fun archiveAccount(id: Long)
    
    @Query("UPDATE savings_accounts SET archived = 0 WHERE id = :id")
    suspend fun unarchiveAccount(id: Long)
}
