package com.example.sparely.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Tracks when a recurring expense is paid before its scheduled due date.
 * This allows users to record early payments and mark them as completed.
 */
@Entity(
    tableName = "recurring_expenses_paid",
    foreignKeys = [
        ForeignKey(
            entity = RecurringExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringExpenseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("recurringExpenseId"),
        Index("expenseId"),
        Index("dueDate")
    ]
)
data class RecurringExpensePaidEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recurringExpenseId: Long,
    // The actual expense ID created from this early payment (nullable until expense is created)
    val expenseId: Long? = null,
    // The scheduled due date this payment is for
    val dueDate: LocalDate,
    // When the payment was actually made/recorded
    val paidDate: LocalDate,
    // Amount paid (might differ from recurring amount for variable expenses)
    val amountPaid: Double,
    // Additional notes about the payment
    val notes: String? = null,
    // Timestamp when this record was created
    val createdAt: LocalDateTime = LocalDateTime.now()
)
