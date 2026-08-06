package com.example.sparely.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "expense_refunds",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("expenseId")]
)
data class ExpenseRefundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val expenseId: Long,
    val refundedAmount: Double,
    val refundDate: LocalDate,
    val refundMethod: String? = null, // ORIGINAL_PAYMENT, STORE_CREDIT, CASH, etc.
    val reason: String? = null,
    val refundedItemIds: String? = null // JSON array of expense_item IDs: "[1,2,3]"
)
