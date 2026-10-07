package com.example.sparely.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.sparely.SparelyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the "Yes" / "No" buttons on a "do you still want it?" notification. */
class WishlistDecisionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val wishlistId = intent.getLongExtra(EXTRA_WISHLIST_ID, -1L)
        if (wishlistId == -1L) return
        if (intent.action != ACTION_STILL_WANT && intent.action != ACTION_NOT_ANYMORE) return

        // The user has answered, so the notification has done its job either way.
        NotificationHelper.dismissWishlistDecision(context, wishlistId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as SparelyApplication).container.savingsRepository
                when (intent.action) {
                    // Answering "yes" ends the reflection time, so the wish can show as ready to buy.
                    ACTION_STILL_WANT -> repository.clearWishlistCooldown(wishlistId)
                    ACTION_NOT_ANYMORE -> repository.archiveWishlist(wishlistId)
                }
                NotificationScheduler(context).cancelWishlistCooldown(wishlistId)
            } catch (e: Exception) {
                // An uncaught exception here would crash the whole app process in the background.
                android.util.Log.e("WishlistDecisionReceiver", "Failed to record answer for wishlist $wishlistId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_STILL_WANT = "com.example.sparely.WISHLIST_STILL_WANT"
        const val ACTION_NOT_ANYMORE = "com.example.sparely.WISHLIST_NOT_ANYMORE"
        const val EXTRA_WISHLIST_ID = "wishlist_id"

        fun createIntent(context: Context, action: String, wishlistId: Long): Intent =
            Intent(context, WishlistDecisionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_WISHLIST_ID, wishlistId)
            }
    }
}
