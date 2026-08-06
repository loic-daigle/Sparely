package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringExpenseDao {
    @Query("SELECT * FROM recurring_expenses ORDER BY startDate DESC")
    fun observeRecurringExpenses(): Flow<List<RecurringExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecurringExpenseEntity)

    @Query("DELETE FROM recurring_expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE recurring_expenses SET lastProcessedDate = :processedDate WHERE id = :id")
    suspend fun updateLastProcessedDate(id: Long, processedDate: java.time.LocalDate?)

    @Query("SELECT * FROM recurring_expenses")
    suspend fun getAll(): List<RecurringExpenseEntity>

    @Query("SELECT * FROM recurring_expenses WHERE id = :id")
    suspend fun getById(id: Long): RecurringExpenseEntity?

    @androidx.room.Update
    suspend fun update(entity: RecurringExpenseEntity)

    @Query("DELETE FROM recurring_expenses")
    suspend fun clear()
}
