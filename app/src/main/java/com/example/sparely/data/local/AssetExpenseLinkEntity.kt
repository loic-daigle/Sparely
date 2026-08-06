package com.example.sparely.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Room entity for linking expenses to assets with percentage allocation.
 * Represents a many-to-many relationship between expenses and assets.
 */
@Entity(
    tableName = "asset_expense_links",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("assetId"),
        Index("expenseId"),
        Index("assetId", "expenseId", unique = true)
    ]
)
data class AssetExpenseLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val assetId: Long,
    val expenseId: Long,
    val percentageAllocated: Double = 100.0,
    val linkedAt: LocalDateTime
)
