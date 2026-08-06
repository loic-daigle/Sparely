package com.example.sparely.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PaymentMethodDao {
    @Query("SELECT * FROM payment_methods ORDER BY isDefault DESC, name ASC")
    fun getAllPaymentMethods(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM payment_methods WHERE id = :id")
    suspend fun getPaymentMethodById(id: Long): PaymentMethodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentMethod(paymentMethod: PaymentMethodEntity): Long

    @Update
    suspend fun updatePaymentMethod(paymentMethod: PaymentMethodEntity)

    @Delete
    suspend fun deletePaymentMethod(paymentMethod: PaymentMethodEntity)

    @Query("UPDATE payment_methods SET isDefault = 0")
    suspend fun clearDefaultPaymentMethod()

    @Query("SELECT COUNT(*) FROM payment_methods")
    suspend fun getCount(): Int

    @Query("DELETE FROM payment_methods")
    suspend fun deleteAll()

    // Credit card specific queries
    @Query("SELECT * FROM payment_methods WHERE isCreditCard = 1 ORDER BY name ASC")
    fun getCreditCards(): Flow<List<PaymentMethodEntity>>

    @Query("UPDATE payment_methods SET currentBalance = :balance WHERE id = :id")
    suspend fun updateBalance(id: Long, balance: Double)

    @Query("UPDATE payment_methods SET currentBalance = ROUND(currentBalance + :amount, 2) WHERE id = :id")
    suspend fun addToBalance(id: Long, amount: Double)

    @Query("UPDATE payment_methods SET currentBalance = ROUND(currentBalance - :amount, 2), lastPaymentDate = :paymentDate, lastPaymentAmount = :amount WHERE id = :id")
    suspend fun recordPayment(id: Long, amount: Double, paymentDate: LocalDate)
}

