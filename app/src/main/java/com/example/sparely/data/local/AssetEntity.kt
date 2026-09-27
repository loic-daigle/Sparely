package com.example.sparely.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String? = null,
    val category: String, // CAR, HOUSE, ROOM, VACATION, HOBBY, OTHER, CUSTOM
    val icon: String? = null,
    val assetPrice: Double = 0.0, // Cost of the asset
    val createdAt: LocalDateTime,
    val archived: Boolean = false,
    val metadata: String? = null, // JSON for asset-specific data
    val creatorExpenseId: Long? = null // ID of the expense that created this asset
)
