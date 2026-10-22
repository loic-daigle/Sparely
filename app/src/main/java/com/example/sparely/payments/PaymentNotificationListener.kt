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
            handle(appContext, payment, appName, source, sbn.postTime, settings.paymentDetectionMethods.keys)
        }
    }

    /**
     * Suggests [payment], or, when it's a second notification for a purchase already suggested,
     * fills in what the first one lacked: the store's name, or the amount the card was charged
     * (from an app with a payment method set, usually the bank's) instead of the store's.
     */
    private fun handle(
        context: Context,
        payment: DetectedPayment,
        appName: String,
        source: String,
        postTime: Long,
        cardApps: Set<String>
    ) {
        synchronized(SeenPayments) { suggestOrMerge(context, payment, appName, source, postTime, cardApps) }
    }

    private fun suggestOrMerge(
        context: Context,
        payment: DetectedPayment,
        appName: String,
        source: String,
        postTime: Long,
        cardApps: Set<String>
    ) {
        val earlier = PaymentDedupe.findSame(payment, source, postTime, SeenPayments.recent(context, postTime))
        if (earlier == null) {
            val id = PaymentSuggestionNotifier.suggest(context, payment, appName, source) ?: return
            SeenPayments.remember(context, SeenPayment(payment.amount, payment.merchant, source, postTime, id))
            return
        }
        val useNewAmount = source in cardApps && earlier.sourcePackage !in cardApps
        val merged = DetectedPayment(
            amount = if (useNewAmount) payment.amount else earlier.amount,
            merchant = earlier.merchant ?: payment.merchant
        )
        val changed = merged.amount != earlier.amount || merged.merchant != earlier.merchant
        // Only a suggestion still waiting in the shade is updated; one already added or ignored stays done
        if (!changed || !PaymentSuggestionNotifier.isShowing(context, earlier.notificationId)) return
        val mergedSource = if (useNewAmount) source else earlier.sourcePackage
        PaymentSuggestionNotifier.suggest(
            context, merged, appLabel(context, mergedSource), mergedSource, existingId = earlier.notificationId
        )
        SeenPayments.remember(
            context,
            SeenPayment(merged.amount, merged.merchant, mergedSource, earlier.postTime, earlier.notificationId),
            replacing = earlier
        )
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
 * Payments suggested in the last day, so a second notification for the same purchase (see
 * [PaymentDedupe]) doesn't make a second suggestion.
 */
internal object SeenPayments {
    private const val PREFS = "payment_notifications_seen"
    private const val KEY = "recent"
    private const val KEEP_MS = 24 * 60 * 60 * 1000L

    @Synchronized
    fun recent(context: Context, now: Long): List<SeenPayment> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                SeenPayment(
                    amount = o.getDouble("amount"),
                    merchant = o.optString("merchant").takeIf { it.isNotEmpty() },
                    sourcePackage = o.getString("source"),
                    postTime = o.getLong("time"),
                    notificationId = o.getInt("id")
                )
            }.filter { now - it.postTime < KEEP_MS }
        }.getOrDefault(emptyList())
    }

    /** Records [payment] as suggested; replaces [replacing] when it updates that suggestion. */
    @Synchronized
    fun remember(context: Context, payment: SeenPayment, replacing: SeenPayment? = null) {
        val kept = recent(context, payment.postTime).filter { it != replacing } + payment
        val array = org.json.JSONArray()
        kept.forEach {
            array.put(
                org.json.JSONObject()
                    .put("amount", it.amount)
                    .put("merchant", it.merchant ?: "")
                    .put("source", it.sourcePackage)
                    .put("time", it.postTime)
                    .put("id", it.notificationId)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
