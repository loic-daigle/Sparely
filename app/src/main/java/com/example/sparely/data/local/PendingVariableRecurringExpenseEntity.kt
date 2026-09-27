package com.example.sparely.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "pending_variable_recurring_expenses",
    foreignKeys = [
        ForeignKey(
            entity = RecurringExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringExpenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("recurringExpenseId"),
        Index("createdAt")
    ]
)
data class PendingVariableRecurringExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recurringExpenseId: Long,
    val predictedAmount: Double,
    val createdAt: LocalDateTime
)
