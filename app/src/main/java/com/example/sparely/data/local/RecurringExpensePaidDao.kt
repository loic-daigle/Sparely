package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RecurringExpensePaidDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaid(entity: RecurringExpensePaidEntity): Long

    @Delete
    suspend fun deletePaid(entity: RecurringExpensePaidEntity)

    @Query("SELECT * FROM recurring_expenses_paid WHERE recurringExpenseId = :recurringExpenseId ORDER BY dueDate DESC")
    fun observePaidHistoryForRecurring(recurringExpenseId: Long): Flow<List<RecurringExpensePaidEntity>>

    @Query("SELECT * FROM recurring_expenses_paid WHERE recurringExpenseId = :recurringExpenseId ORDER BY dueDate DESC")
    suspend fun getPaidHistoryForRecurring(recurringExpenseId: Long): List<RecurringExpensePaidEntity>

    @Query("SELECT * FROM recurring_expenses_paid WHERE recurringExpenseId = :recurringExpenseId AND dueDate = :dueDate LIMIT 1")
    suspend fun getPaidRecordForDate(recurringExpenseId: Long, dueDate: LocalDate): RecurringExpensePaidEntity?

    @Query("SELECT * FROM recurring_expenses_paid WHERE recurringExpenseId = :recurringExpenseId AND dueDate >= :fromDate AND dueDate <= :toDate ORDER BY dueDate")
    suspend fun getPaidRecordsInRange(recurringExpenseId: Long, fromDate: LocalDate, toDate: LocalDate): List<RecurringExpensePaidEntity>

    @Query("SELECT COUNT(*) FROM recurring_expenses_paid WHERE recurringExpenseId = :recurringExpenseId AND dueDate >= :sinceDate")
    suspend fun countPaidSince(recurringExpenseId: Long, sinceDate: LocalDate): Int

    @Query("UPDATE recurring_expenses_paid SET expenseId = :expenseId WHERE id = :paidRecordId")
    suspend fun linkExpenseToPayment(paidRecordId: Long, expenseId: Long)

    @Query("DELETE FROM recurring_expenses_paid WHERE dueDate < :beforeDate AND expenseId IS NOT NULL")
    suspend fun deleteOldPaidRecords(beforeDate: LocalDate)

    @Query("SELECT SUM(amountPaid) FROM recurring_expenses_paid WHERE recurringExpenseId = :recurringExpenseId AND paidDate BETWEEN :fromDate AND :toDate")
    suspend fun getTotalPaidInRange(recurringExpenseId: Long, fromDate: LocalDate, toDate: LocalDate): Double?

    @Query("SELECT * FROM recurring_expenses_paid ORDER BY dueDate DESC")
    fun observeAllPaidRecords(): Flow<List<RecurringExpensePaidEntity>>
}
