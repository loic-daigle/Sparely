package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingVariableRecurringExpenseDao {

    @Insert
    suspend fun insert(entity: PendingVariableRecurringExpenseEntity): Long

    @Query("DELETE FROM pending_variable_recurring_expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_variable_recurring_expenses WHERE recurringExpenseId = :recurringExpenseId")
    suspend fun deleteByRecurringExpenseId(recurringExpenseId: Long)

    @Query("SELECT * FROM pending_variable_recurring_expenses WHERE id = :id")
    suspend fun getById(id: Long): PendingVariableRecurringExpenseEntity?

    @Query("SELECT * FROM pending_variable_recurring_expenses WHERE recurringExpenseId = :recurringExpenseId")
    suspend fun getByRecurringExpenseId(recurringExpenseId: Long): PendingVariableRecurringExpenseEntity?

    @Query("SELECT * FROM pending_variable_recurring_expenses ORDER BY createdAt DESC")
    suspend fun getAll(): List<PendingVariableRecurringExpenseEntity>

    @Query("SELECT * FROM pending_variable_recurring_expenses ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PendingVariableRecurringExpenseEntity>>
}
