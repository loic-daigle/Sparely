package com.example.sparely.domain.model

import java.time.LocalDateTime

data class Wishlist(
    val id: Long = 0L,
    val description: String,
    val targetAmount: Double,
    val currentSavings: Double = 0.0,
    val priority: Int = 1, // 1-5
    val createdAt: LocalDateTime,
    val cooldownExpiresAt: LocalDateTime? = null,
    val isReallyNeeded: Boolean = true,
    val category: WishlistCategory? = null,
    val notes: String? = null,
    val imageUrl: String? = null,
    val archived: Boolean = false
) {
    val progress: Double
        get() = if (targetAmount > 0) (currentSavings / targetAmount).coerceIn(0.0, 1.0) else 0.0

    val isOnCooldown: Boolean
        get() = cooldownExpiresAt?.let { it.isAfter(LocalDateTime.now()) } ?: false

    val daysUntilCooldownExpires: Long
        get() = if (isOnCooldown) {
            java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), cooldownExpiresAt)
        } else 0L

    val amountRemaining: Double
        get() = (targetAmount - currentSavings).coerceAtLeast(0.0)

    val daysToCompletion: Double
        get() {
            if (amountRemaining <= 0.0) return 0.0
            // Estimate: if no savings yet, estimate 30 days; otherwise extrapolate
            val ageInDays = java.time.temporal.ChronoUnit.DAYS.between(createdAt, LocalDateTime.now()).toDouble()
            if (ageInDays <= 0 || currentSavings <= 0.0) return 30.0
            val savingsPerDay = currentSavings / ageInDays
            return if (savingsPerDay > 0) amountRemaining / savingsPerDay else 30.0
        }
}

data class WishlistSavings(
    val id: Long = 0L,
    val wishlistId: Long,
    val amount: Double,
    val date: LocalDateTime,
    val source: SavingsSource? = null
)

enum class WishlistCategory {
    HOBBY,
    HOUSE,
    CAR,
    GADGET,
    VACATION,
    EDUCATION,
    HEALTH,
    OTHER
}

enum class SavingsSource {
    MANUAL_ALLOCATION,
    AUTO_DEPOSIT,
    INTEREST
}

fun WishlistCategory.displayName(): String = when (this) {
    WishlistCategory.HOBBY -> "Hobby"
    WishlistCategory.HOUSE -> "House"
    WishlistCategory.CAR -> "Car"
    WishlistCategory.GADGET -> "Gadget"
    WishlistCategory.VACATION -> "Vacation"
    WishlistCategory.EDUCATION -> "Education"
    WishlistCategory.HEALTH -> "Health"
    WishlistCategory.OTHER -> "Other"
}
