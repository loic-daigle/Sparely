package com.example.sparely.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense_items",
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
data class ExpenseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val expenseId: Long,
    val name: String,
    val quantity: Int = 1,
    val unitPrice: Double,
    val totalPrice: Double = quantity * unitPrice,
    val type: String = "PRODUCT" // PRODUCT or SERVICE
)

data class ExpenseWithItemsRelation(
    @androidx.room.Embedded val expense: ExpenseEntity,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "expenseId"
    )
    val items: List<ExpenseItemEntity>
)
