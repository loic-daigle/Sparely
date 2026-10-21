package com.example.sparely.domain.model

import java.time.LocalDateTime

data class Asset(
    val id: Long = 0L,
    val name: String,
    val description: String? = null,
    val category: AssetCategory,
    val icon: String? = null,
    val assetPrice: Double = 0.0, // Cost of the asset (e.g. car price, house price)
    val createdAt: LocalDateTime,
    val archived: Boolean = false,
    val totalSpending: Double = 0.0, // Calculated field - expenses linked to this asset
    val creatorExpenseId: Long? = null, // ID of the expense that created this asset
    val kind: AssetKind = AssetKind.ASSET,
    val completedAt: LocalDateTime? = null // Set when a project is marked done
) {
    val isProject: Boolean get() = kind == AssetKind.PROJECT
    /** A finished project: its total is final and new expenses aren't offered it. */
    val isDone: Boolean get() = isProject && completedAt != null
}

/** An asset costs money for as long as it's owned; a project has a start and an end. */
enum class AssetKind { ASSET, PROJECT }

data class AssetExpenseLink(
    val id: Long = 0L,
    val assetId: Long,
    val expenseId: Long,
    val percentageAllocated: Double = 100.0,
    val linkedAt: LocalDateTime
)

enum class AssetCategory {
    CAR,
    HOUSE,
    ROOM,
    VACATION,
    HOBBY,
    EDUCATION,
    HEALTH,
    OTHER,
    CUSTOM
}

/** What a new asset of this category usually is. */
fun AssetCategory.defaultKind(): AssetKind = when (this) {
    AssetCategory.HOBBY, AssetCategory.VACATION, AssetCategory.EDUCATION -> AssetKind.PROJECT
    else -> AssetKind.ASSET
}

fun AssetCategory.displayName(): String = when (this) {
    AssetCategory.CAR -> "Car"
    AssetCategory.HOUSE -> "House"
    AssetCategory.ROOM -> "Room"
    AssetCategory.VACATION -> "Vacation"
    AssetCategory.HOBBY -> "Hobby"
    AssetCategory.EDUCATION -> "Education"
    AssetCategory.HEALTH -> "Health"
    AssetCategory.OTHER -> "Other"
    AssetCategory.CUSTOM -> "Custom"
}
