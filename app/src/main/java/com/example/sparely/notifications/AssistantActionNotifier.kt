package com.example.sparely.notifications

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
import com.example.sparely.domain.model.AssistantAction
import com.example.sparely.domain.model.AssistantActionType
import com.sparely.app.R
import java.text.NumberFormat
import java.util.Currency

/** Tells the user when an AI assistant changed their data, with a one-tap Undo. */
object AssistantActionNotifier {
    private const val CHANNEL_ID = "sparely_assistant_activity"
    private const val BASE_NOTIFICATION_ID = 8000

    fun showRecorded(context: Context, action: AssistantAction, currencyCode: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val title = context.getString(
            when (action.type) {
                AssistantActionType.EXPENSE_RECORDED -> R.string.assistant_notification_expense_title
                AssistantActionType.INCOME_RECORDED -> R.string.assistant_notification_income_title
            }
        )
        val undoIntent = PendingIntent.getBroadcast(
            context,
            notificationId(action.id),
            Intent(context, AssistantActionReceiver::class.java).apply {
                this.action = AssistantActionReceiver.ACTION_UNDO
                putExtra(AssistantActionReceiver.EXTRA_ACTION_ID, action.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle(title)
            .setContentText("${formatAmount(action.amount, currencyCode)} · ${action.description}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(context))
            .addAction(0, context.getString(R.string.assistant_notification_undo), undoIntent)
            .setAutoCancel(true)
            .build()
        notify(context, notificationId(action.id), notification)
    }

    fun showUndone(context: Context, action: AssistantAction) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle(context.getString(R.string.assistant_notification_undone_title))
            .setContentText(context.getString(R.string.assistant_notification_undone_text, action.description))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setTimeoutAfter(10_000)
            .setAutoCancel(true)
            .build()
        notify(context, notificationId(action.id), notification)
    }

    fun cancel(context: Context, actionId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(actionId))
    }

    private fun notificationId(actionId: Long): Int = BASE_NOTIFICATION_ID + (actionId % 1000).toInt()

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
                context.getString(R.string.assistant_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        BASE_NOTIFICATION_ID,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun formatAmount(amount: Double, currencyCode: String): String {
        val format = NumberFormat.getCurrencyInstance()
        runCatching { format.currency = Currency.getInstance(currencyCode) }
        return format.format(amount)
    }
}
