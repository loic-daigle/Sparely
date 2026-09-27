package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseRefundDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRefund(refund: ExpenseRefundEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRefunds(refunds: List<ExpenseRefundEntity>)

    @Delete
    suspend fun deleteRefund(refund: ExpenseRefundEntity)

    @Query("DELETE FROM expense_refunds WHERE id = :refundId")
    suspend fun deleteRefundById(refundId: Long)

    @Query("SELECT * FROM expense_refunds WHERE expenseId = :expenseId ORDER BY refundDate DESC")
    fun observeRefundsForExpense(expenseId: Long): Flow<List<ExpenseRefundEntity>>

    @Query("SELECT * FROM expense_refunds WHERE expenseId = :expenseId ORDER BY refundDate DESC")
    suspend fun getRefundsForExpense(expenseId: Long): List<ExpenseRefundEntity>

    @Query("SELECT * FROM expense_refunds WHERE id = :refundId LIMIT 1")
    suspend fun getRefundById(refundId: Long): ExpenseRefundEntity?

    @Query("SELECT IFNULL(SUM(refundedAmount), 0.0) FROM expense_refunds WHERE expenseId = :expenseId")
    suspend fun getTotalRefundedForExpense(expenseId: Long): Double

    @Query("DELETE FROM expense_refunds WHERE expenseId = :expenseId")
    suspend fun deleteRefundsForExpense(expenseId: Long)

    @Query("SELECT * FROM expense_refunds ORDER BY refundDate DESC")
    suspend fun getAllRefunds(): List<ExpenseRefundEntity>

    @Query("DELETE FROM expense_refunds")
    suspend fun clearAll()
}
