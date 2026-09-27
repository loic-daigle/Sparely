package com.example.sparely.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Entity for tracking transactions on Savings Accounts (HISA, TFSA, etc.)
 * This includes deposits, withdrawals, interest credits, and transfers.
 */
@Entity(
    tableName = "savings_account_transactions",
    foreignKeys = [
        ForeignKey(
            entity = SavingsAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId")]
)
data class SavingsAccountTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val accountId: Long,
    val type: SavingsAccountTransactionType,
    val amount: Double,
    val balanceAfter: Double,
    val timestamp: LocalDateTime,
    val description: String,
    val relatedMainAccountTransactionId: Long? = null
)

enum class SavingsAccountTransactionType {
    DEPOSIT,        // Manual deposit or transfer in
    WITHDRAWAL,     // Manual withdrawal or transfer out
    INTEREST,       // Interest credit
    TRANSFER_IN,    // Transfer from Main Account
    TRANSFER_OUT    // Transfer to Main Account
}
