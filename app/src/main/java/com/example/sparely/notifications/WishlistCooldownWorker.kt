package com.example.sparely.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sparely.SparelyApplication
import java.time.LocalDateTime

/**
 * Fires when a wish's reflection time ends and asks "do you still want it?". One of these is
 * scheduled per wish (see [NotificationScheduler.scheduleWishlistCooldown]).
 */
class WishlistCooldownWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val wishlistId = inputData.getLong(KEY_WISHLIST_ID, -1L)
        if (wishlistId == -1L) return Result.success()

        val container = (applicationContext as SparelyApplication).container
        val wish = container.savingsRepository.getWishlistById(wishlistId) ?: return Result.success()

        // Archived, or already answered (answering clears the reflection time): nothing to ask.
        val expiresAt = wish.cooldownExpiresAt
        if (wish.archived || expiresAt == null) return Result.success()

        // The reflection time was extended since this was scheduled: wait for the new end.
        if (expiresAt.isAfter(LocalDateTime.now().plusMinutes(1))) {
            NotificationScheduler(applicationContext).scheduleWishlistCooldown(wishlistId, expiresAt)
            return Result.success()
        }

        NotificationHelper.showWishlistDecision(applicationContext, wishlistId, wish.description)
        return Result.success()
    }

    companion object {
        const val KEY_WISHLIST_ID = "wishlist_id"
    }
}
