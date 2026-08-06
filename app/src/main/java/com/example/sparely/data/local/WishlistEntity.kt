package com.example.sparely.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "wishlists")
data class WishlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val description: String,
    val targetAmount: Double,
    val currentSavings: Double = 0.0,
    val priority: Int = 1, // 1-5 scale
    val createdAt: LocalDateTime,
    val cooldownExpiresAt: LocalDateTime? = null, // 3 days from creation
    val isReallyNeeded: Boolean = true,
    val category: String? = null, // HOBBY, HOUSE, CAR, GADGET, OTHER
    val notes: String? = null,
    val imageData: ByteArray? = null, // Optional product image
    val imageUrl: String? = null, // URL to product
    val archived: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WishlistEntity

        if (id != other.id) return false
        if (description != other.description) return false
        if (targetAmount != other.targetAmount) return false
        if (currentSavings != other.currentSavings) return false
        if (priority != other.priority) return false
        if (createdAt != other.createdAt) return false
        if (cooldownExpiresAt != other.cooldownExpiresAt) return false
        if (isReallyNeeded != other.isReallyNeeded) return false
        if (category != other.category) return false
        if (notes != other.notes) return false
        if (imageUrl != other.imageUrl) return false
        if (archived != other.archived) return false
        if (imageData != null) {
            if (other.imageData == null) return false
            if (!imageData.contentEquals(other.imageData)) return false
        } else if (other.imageData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + targetAmount.hashCode()
        result = 31 * result + currentSavings.hashCode()
        result = 31 * result + priority
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (cooldownExpiresAt?.hashCode() ?: 0)
        result = 31 * result + isReallyNeeded.hashCode()
        result = 31 * result + (category?.hashCode() ?: 0)
        result = 31 * result + (notes?.hashCode() ?: 0)
        result = 31 * result + (imageUrl?.hashCode() ?: 0)
        result = 31 * result + archived.hashCode()
        result = 31 * result + (imageData?.contentHashCode() ?: 0)
        return result
    }
}

@Entity(
    tableName = "wishlist_savings",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = WishlistEntity::class,
            parentColumns = ["id"],
            childColumns = ["wishlistId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("wishlistId")]
)
data class WishlistSavingsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val wishlistId: Long,
    val amount: Double,
    val date: LocalDateTime,
    val source: String? = null // MANUAL_ALLOCATION, AUTO, INTEREST
)
