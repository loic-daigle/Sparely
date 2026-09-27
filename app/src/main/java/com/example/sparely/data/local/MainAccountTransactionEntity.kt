package com.example.sparely.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "main_account_transactions",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["relatedExpenseId"],
            onDelete = androidx.room.ForeignKey.SET_NULL
        )
    ],
    indices = [androidx.room.Index("relatedExpenseId")]
)
data class MainAccountTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: MainAccountTransactionType,
    val amount: Double,
    val balanceAfter: Double,
    val timestamp: LocalDateTime,
    val description: String,
    val relatedExpenseId: Long? = null,
    val incomeCategory: com.example.sparely.domain.model.IncomeCategory? = null,

)

enum class MainAccountTransactionType {
    DEPOSIT,           // Manual deposit/income
    WITHDRAWAL,        // Manual withdrawal
    EXPENSE,           // Expense deduction
    VAULT_CONTRIBUTION, // Saving tax to vaults
    ADJUSTMENT,        // Manual correction
    CREDIT_CARD_PAYMENT, // Payment toward credit card bill
    HISA_TRANSFER      // Transfer to/from High-Interest Savings Account
}
