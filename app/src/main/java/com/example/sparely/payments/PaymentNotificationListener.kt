package com.example.sparely.payments

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
 * Watches purchase notifications from the apps the user picked (Google Wallet, a bank or card app,
 * PayPal, ...) and offers to log each purchase. Android only runs it once the user grants
 * Notification access, and it does nothing unless "Detect payments" is on. Notifications from any
 * app not on the user's list are dropped before their content is read.
 */
class PaymentNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = sbn.packageName ?: return
        if (source == packageName) return
        val appContext = applicationContext
        scope.launch {
            val settings = (appContext as SparelyApplication).container.preferencesRepository.getSettingsSnapshot()
            if (!settings.paymentDetectionEnabled || source !in settings.paymentDetectionApps) return@launch

            val notification = sbn.notification ?: return@launch
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return@launch
            val extras = notification.extras ?: return@launch
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
            val appName = appLabel(appContext, source)
            val payment = PaymentNotificationParser.parse(title, text, appName) ?: return@launch
            if (!SeenPayments.isNew(appContext, payment, sbn.postTime)) return@launch
            PaymentSuggestionNotifier.suggest(appContext, payment, appName, source)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val GOOGLE_WALLET = "com.google.android.apps.walletnfcrel"

        fun appLabel(context: Context, packageName: String): String = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}

/**
 * One purchase can produce several notifications: Wallet updating its own, or Wallet and the card's
 * bank app both announcing the same tap. The same amount within a few minutes is suggested once.
 */
internal object SeenPayments {
    private const val PREFS = "payment_notifications_seen"
    private const val WINDOW_MS = 10 * 60 * 1000L
    private const val KEEP_MS = 24 * 60 * 60 * 1000L

    @Synchronized
    fun isNew(context: Context, payment: DetectedPayment, postTime: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "%.2f".format(java.util.Locale.US, payment.amount)
        val last = prefs.getLong(key, Long.MIN_VALUE)
        if (last != Long.MIN_VALUE && kotlin.math.abs(postTime - last) < WINDOW_MS) return false
        val editor = prefs.edit()
        prefs.all.forEach { (k, v) -> if (v is Long && postTime - v > KEEP_MS) editor.remove(k) }
        editor.putLong(key, postTime).apply()
        return true
    }
}
