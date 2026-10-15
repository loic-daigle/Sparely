package com.example.sparely.wallet

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.sparely.SparelyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Watches for Google Wallet tap-to-pay notifications and offers to log the purchase. Android only
 * runs it once the user grants Notification access, and it does nothing unless "Detect Google
 * Wallet payments" is on. Every other app's notification is ignored on the first line.
 */
class WalletPaymentListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WALLET_PACKAGES) return
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val payment = WalletNotificationParser.parse(title, text) ?: return

        val appContext = applicationContext
        scope.launch {
            val container = (appContext as SparelyApplication).container
            if (!container.preferencesRepository.getSettingsSnapshot().walletDetectionEnabled) return@launch
            if (!WalletSeenPayments.isNew(appContext, payment, sbn.postTime)) return@launch
            WalletPaymentNotifier.suggest(appContext, payment)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Google Wallet. */
        val WALLET_PACKAGES = setOf("com.google.android.apps.walletnfcrel")
    }
}

/**
 * Wallet can post or update the same payment more than once; the same amount at the same store
 * within a few minutes is suggested only once.
 */
internal object WalletSeenPayments {
    private const val PREFS = "wallet_payments_seen"
    private const val WINDOW_MS = 10 * 60 * 1000L
    private const val KEEP_MS = 24 * 60 * 60 * 1000L

    @Synchronized
    fun isNew(context: Context, payment: WalletPayment, postTime: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "${"%.2f".format(java.util.Locale.US, payment.amount)}|${payment.merchant?.lowercase().orEmpty()}"
        val last = prefs.getLong(key, Long.MIN_VALUE)
        if (last != Long.MIN_VALUE && kotlin.math.abs(postTime - last) < WINDOW_MS) return false
        val editor = prefs.edit()
        prefs.all.forEach { (k, v) -> if (v is Long && postTime - v > KEEP_MS) editor.remove(k) }
        editor.putLong(key, postTime).apply()
        return true
    }
}
