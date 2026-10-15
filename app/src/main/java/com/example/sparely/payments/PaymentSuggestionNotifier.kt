package com.example.sparely.payments

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.sparely.MainActivity
import com.example.sparely.notifications.NotificationHelper
import com.sparely.app.R

/** "Log 12,34 $ at Tim Hortons?" with Add / Edit / Ignore, after a detected payment. */
object PaymentSuggestionNotifier {
    private const val CHANNEL_ID = "payment_suggestions"

    const val EXTRA_AMOUNT = "payment_amount"
    const val EXTRA_MERCHANT = "payment_merchant"
    const val EXTRA_NOTIFICATION_ID = "payment_notification_id"
    /** navigate_to value MainActivity turns into a prefilled expense form. */
    const val NAVIGATE_EDIT = "detectedPayment"

    fun suggest(context: Context, payment: DetectedPayment, sourceApp: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val id = notificationId(payment)
        val amount = NotificationHelper.formatAmount(payment.amount)
        val text = payment.merchant?.let { context.getString(R.string.payment_suggest_text_merchant, amount, it, sourceApp) }
            ?: context.getString(R.string.payment_suggest_text, amount, sourceApp)

        val edit = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", NAVIGATE_EDIT)
                putExtra(EXTRA_AMOUNT, payment.amount)
                putExtra(EXTRA_MERCHANT, payment.merchant)
                putExtra(EXTRA_NOTIFICATION_ID, id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(ContextCompat.getColor(context, R.color.sparely_spruce))
            .setContentTitle(context.getString(R.string.payment_suggest_title))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(edit)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_sparely_mark_mono, context.getString(R.string.payment_action_add),
                PaymentSuggestionReceiver.intent(context, PaymentSuggestionReceiver.ACTION_ADD, payment, id))
            .addAction(R.drawable.ic_sparely_mark_mono, context.getString(R.string.payment_action_edit), edit)
            .addAction(R.drawable.ic_sparely_mark_mono, context.getString(R.string.payment_action_ignore),
                PaymentSuggestionReceiver.intent(context, PaymentSuggestionReceiver.ACTION_IGNORE, payment, id))
            .build()
        notify(context, id, notification)
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    private fun notificationId(payment: DetectedPayment): Int =
        ("payment|${payment.amount}|${payment.merchant}|${System.currentTimeMillis() / 60_000}").hashCode()

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission") // Checked in canNotify()
    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.payment_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.payment_channel_desc) }
        )
    }
}
