package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExpenseDao {
    @androidx.room.Transaction
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    fun observeExpenses(): Flow<List<ExpenseWithItemsRelation>>

    @Query("SELECT * FROM expenses WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun observeExpensesBetween(from: LocalDate, to: LocalDate): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    suspend fun getExpensesBetween(from: LocalDate, to: LocalDate): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExpense(entity: ExpenseEntity): Long

    @Delete
    suspend fun deleteExpense(entity: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun findExpenseById(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC LIMIT 1")
    suspend fun getMostRecentExpense(): ExpenseEntity?

    @Query("SELECT IFNULL(SUM(amount), 0.0) FROM expenses WHERE date BETWEEN :from AND :to")
    suspend fun getTotalSpentBetween(from: LocalDate, to: LocalDate): Double

    @Query("DELETE FROM expenses")
    suspend fun clearAll()

    @Query("DELETE FROM expenses WHERE date < :date")
    suspend fun deleteExpensesBefore(date: LocalDate): Int

    @androidx.room.Transaction
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getExpensesPaged(limit: Int, offset: Int): List<ExpenseWithItemsRelation>

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun countTotalExpenses(): Int

    @androidx.room.Transaction
    // Assuming storeId is the link, or maybe storeName if no FK. Let's check ExpenseEntity first.
    // If I see ExpenseEntity has storeId, I use storeId.
    @Query("SELECT * FROM expenses WHERE storeId = :storeId ORDER BY date DESC, id DESC")
    suspend fun getExpensesForStore(storeId: Long): List<ExpenseWithItemsRelation>

    @Query("SELECT COUNT(*) FROM expenses WHERE date < :date")
    suspend fun countExpensesBefore(date: LocalDate): Int
}
