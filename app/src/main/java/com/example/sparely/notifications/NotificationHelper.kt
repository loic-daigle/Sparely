package com.example.sparely.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.sparely.MainActivity
import com.sparely.app.R
import com.example.sparely.domain.model.VaultSchedule
import com.example.sparely.domain.model.VaultTransferDirection
import java.text.NumberFormat

object NotificationHelper {
    const val REMINDER_CHANNEL_ID = "sparely_reminders"
    const val AUTO_DEPOSIT_CHANNEL_ID = "sparely_auto_deposits"
    const val VAULT_TRANSFER_CHANNEL_ID = "sparely_vault_transfers"
    const val PAYDAY_CHANNEL_ID = "sparely_payday_reminders"
    const val CREDIT_CARD_CHANNEL_ID = "sparely_credit_card_reminders"
    const val WISHLIST_CHANNEL_ID = "sparely_wishlist"
    private const val REMINDER_NOTIFICATION_ID = 1001
    private const val AUTO_DEPOSIT_NOTIFICATION_ID = 3001
    private const val VAULT_TRANSFER_NOTIFICATION_ID = 4001
    private const val PAYDAY_NOTIFICATION_ID = 4002
    private const val SCHEDULE_SUMMARY_NOTIFICATION_ID = 5001
    private const val CREDIT_CARD_BASE_NOTIFICATION_ID = 6001
    private const val VARIABLE_RECURRING_NOTIFICATION_ID = 7001
    private const val WISHLIST_NOTIFICATION_BASE_ID = 100_000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                context.getString(R.string.notif_channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_reminders_desc)
            }
            val autoDepositChannel = NotificationChannel(
                AUTO_DEPOSIT_CHANNEL_ID,
                context.getString(R.string.notif_channel_auto_deposits),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_auto_deposits_desc)
            }
            val paydayChannel = NotificationChannel(
                PAYDAY_CHANNEL_ID,
                context.getString(R.string.notif_channel_payday),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_payday_desc)
                setShowBadge(false)
            }
            val vaultTransferChannel = NotificationChannel(
                VAULT_TRANSFER_CHANNEL_ID,
                context.getString(R.string.notif_channel_vault_transfers),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_vault_transfers_desc)
                setShowBadge(false)
            }
            val creditCardChannel = NotificationChannel(
                CREDIT_CARD_CHANNEL_ID,
                context.getString(R.string.notif_channel_credit_cards),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_credit_cards_desc)
                setShowBadge(true)
            }
            val wishlistChannel = NotificationChannel(
                WISHLIST_CHANNEL_ID,
                context.getString(R.string.wishlist_notif_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.wishlist_notif_channel_desc)
            }
            manager.createNotificationChannel(wishlistChannel)
            manager.createNotificationChannel(reminderChannel)
            manager.createNotificationChannel(autoDepositChannel)
            manager.createNotificationChannel(paydayChannel)
            manager.createNotificationChannel(vaultTransferChannel)
            manager.createNotificationChannel(creditCardChannel)
        }
    }

    /** One notification per wish, so answering or dismissing one never affects another. */
    private fun wishlistNotificationId(wishlistId: Long): Int =
        WISHLIST_NOTIFICATION_BASE_ID + (wishlistId % 1_000_000L).toInt()

    /** "Do you still want it?" once the reflection time on a wish is over, answerable from the notification. */
    fun showWishlistDecision(context: Context, wishlistId: Long, name: String) {
        ensureChannels(context)
        val notificationId = wishlistNotificationId(wishlistId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val open = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "wishlist")
            },
            flags
        )
        val stillWant = PendingIntent.getBroadcast(
            context,
            notificationId,
            WishlistDecisionReceiver.createIntent(context, WishlistDecisionReceiver.ACTION_STILL_WANT, wishlistId),
            flags
        )
        val notAnymore = PendingIntent.getBroadcast(
            context,
            notificationId + WISHLIST_NOTIFICATION_BASE_ID,
            WishlistDecisionReceiver.createIntent(context, WishlistDecisionReceiver.ACTION_NOT_ANYMORE, wishlistId),
            flags
        )

        val text = context.getString(R.string.wishlist_notif_text)
        val notification = NotificationCompat.Builder(context, WISHLIST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(ContextCompat.getColor(context, R.color.sparely_spruce))
            .setContentTitle(context.getString(R.string.wishlist_notif_title, name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.wishlist_notif_yes), stillWant)
            .addAction(0, context.getString(R.string.wishlist_notif_no), notAnymore)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun dismissWishlistDecision(context: Context, wishlistId: Long) {
        NotificationManagerCompat.from(context).cancel(wishlistNotificationId(wishlistId))
    }

    fun showReminder(context: Context, message: String) {
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(ContextCompat.getColor(context, R.color.sparely_spruce))
            .setContentTitle(context.getString(R.string.notif_reminder_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_ID, notification)
    }
    
    fun showAutoDepositReminder(context: Context, depositCount: Int, totalAmount: Double) {
        val formattedAmount = formatAmount(totalAmount)
        val title = if (depositCount == 1) {
            context.getString(R.string.notif_auto_deposit_due_one)
        } else {
            context.getString(R.string.notif_auto_deposit_due_many, depositCount)
        }
        val message = context.getString(R.string.notif_auto_deposit_message, formattedAmount)
        
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "vaultTransfers")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, AUTO_DEPOSIT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(ContextCompat.getColor(context, R.color.sparely_spruce))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(AUTO_DEPOSIT_NOTIFICATION_ID, notification)
    }

    fun showVaultScheduleNotificationBefore(
        context: Context,
        vaultName: String,
        amount: Double,
        schedule: VaultSchedule
    ) {
        ensureChannels(context)
        val notificationId = scheduleNotificationId(schedule.id, 1)
        val notification = NotificationCompat.Builder(context, AUTO_DEPOSIT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(ContextCompat.getColor(context, R.color.sparely_spruce))
            .setContentTitle(context.getString(R.string.notif_schedule_soon_title))
            .setContentText(transferLine(context, amount, schedule, vaultName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(vaultScheduleIntent(context, schedule, notificationId))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun showVaultScheduleNotificationAfter(
        context: Context,
        vaultName: String,
        amount: Double,
        schedule: VaultSchedule
    ) {
        ensureChannels(context)
        val notificationId = scheduleNotificationId(schedule.id, 2)
        val notification = NotificationCompat.Builder(context, AUTO_DEPOSIT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(ContextCompat.getColor(context, R.color.sparely_spruce))
            .setContentTitle(context.getString(R.string.notif_schedule_done_title))
            .setContentText(transferLine(context, amount, schedule, vaultName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(vaultScheduleIntent(context, schedule, notificationId))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun showVaultScheduleNotificationFailure(
        context: Context,
        vaultName: String,
        amount: Double,
        schedule: VaultSchedule
    ) {
        ensureChannels(context)
        val message = context.getString(R.string.notif_schedule_failed_message, transferLine(context, amount, schedule, vaultName))
        val notificationId = scheduleNotificationId(schedule.id, 3)
        val notification = NotificationCompat.Builder(context, AUTO_DEPOSIT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(ContextCompat.getColor(context, R.color.sparely_spruce))
            .setContentTitle(context.getString(R.string.notif_schedule_failed_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(vaultScheduleIntent(context, schedule, notificationId))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun showVaultScheduleSummary(context: Context, count: Int, totalAmount: Double) {
        ensureChannels(context)
        val formattedAmount = formatAmount(totalAmount)
        val title = if (count == 1) context.getString(R.string.notif_schedule_summary_one) else context.getString(R.string.notif_schedule_summary_many, count)
        val contentIntent = PendingIntent.getActivity(
            context,
            SCHEDULE_SUMMARY_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "vaultTransfers")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, AUTO_DEPOSIT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(ContextCompat.getColor(context, R.color.sparely_spruce))
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_schedule_summary_text, formattedAmount))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(SCHEDULE_SUMMARY_NOTIFICATION_ID, notification)
    }

    fun showVaultTransferNotification(
        context: Context,
        vaultId: Long?,
        vaultName: String,
        contributions: List<com.example.sparely.domain.model.VaultContribution>,
        currentIndex: Int,
        totalVaultCount: Int
    ) {
        ensureChannels(context)
        
        val totalAmount = contributions.sumOf { it.amount }
        val formattedTotal = formatAmount(totalAmount)
        
        val title = if (totalVaultCount > 1) {
            context.getString(R.string.notification_vault_transfer_multi_title, totalVaultCount)
        } else {
            context.getString(R.string.notification_vault_transfer_single_title)
        }
        
        val progressText = if (totalVaultCount > 1) {
            " " + context.getString(R.string.notif_transfer_progress, currentIndex + 1, totalVaultCount)
        } else {
            ""
        }
        
        val contentText = context.getString(
            R.string.notification_vault_transfer_message,
            formattedTotal,
            vaultName
        ) + progressText
        
        val bigText = contentText
        
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "vaultTransfers")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val uniqueId = vaultId ?: (contributions.firstOrNull()?.savingsAccountId ?: 0L)
        val requestCode = (uniqueId % Int.MAX_VALUE).toInt()

        val transferredIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            VaultTransferNotificationReceiver.createTransferredIntent(context, vaultId, if (vaultId == null) uniqueId else null),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val dismissIntent = PendingIntent.getBroadcast(
            context,
            requestCode + 10000,
            VaultTransferNotificationReceiver.createDismissIntent(context, vaultId, if (vaultId == null) uniqueId else null),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, VAULT_TRANSFER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(ContextCompat.getColor(context, R.color.sparely_spruce))
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_sparely_mark_mono,
                context.getString(R.string.notif_action_transferred),
                transferredIntent
            )
            .addAction(
                R.drawable.ic_sparely_mark_mono,
                context.getString(R.string.notif_action_dismiss),
                dismissIntent
            )
            .build()
        
        NotificationManagerCompat.from(context).notify(VAULT_TRANSFER_NOTIFICATION_ID, notification)
    }

    fun dismissVaultTransferNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(VAULT_TRANSFER_NOTIFICATION_ID)
    }

    fun showPaydayReminder(
        context: Context,
        expectedDate: java.time.LocalDate,
        suggestedAmount: Double?
    ) {
        ensureChannels(context)
        val formattedDate = expectedDate.format(com.example.sparely.ui.utils.LocalDates.dayMonth)
        val baseMessage = context.getString(R.string.notif_payday_message)
        val body = if (suggestedAmount != null && suggestedAmount > 0.0) {
            val formattedAmount = formatAmount(suggestedAmount)
            baseMessage + "\n" + context.getString(R.string.notif_payday_suggested, formattedAmount)
        } else baseMessage

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "paycheck")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val recordIncomeIntent = PendingIntent.getBroadcast(
            context,
            1,
            PaydayNotificationReceiver.createRecordIncomeIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remindLaterIntent = PendingIntent.getBroadcast(
            context,
            2,
            PaydayNotificationReceiver.createRemindLaterIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PAYDAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(ContextCompat.getColor(context, R.color.sparely_spruce))
            .setContentTitle(context.getString(R.string.notif_payday_title, formattedDate))
            .setContentText(baseMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_sparely_mark_mono,
                context.getString(R.string.fab_record_income),
                recordIncomeIntent
            )
            .addAction(
                R.drawable.ic_sparely_mark_mono,
                context.getString(R.string.notif_action_remind_later),
                remindLaterIntent
            )
            .build()

        NotificationManagerCompat.from(context).notify(PAYDAY_NOTIFICATION_ID, notification)
    }

    fun dismissPaydayReminder(context: Context) {
        NotificationManagerCompat.from(context).cancel(PAYDAY_NOTIFICATION_ID)
    }

    /**
     * Reminds that a card's bill is coming due. [amountDue] is what's on that bill, not the whole
     * balance: purchases made since the statement came out are due on the next one.
     */
    fun showCreditCardDueReminder(
        context: Context,
        cardId: Long,
        cardName: String,
        amountDue: Double,
        dueDate: java.time.LocalDate,
        daysUntilDue: Int,
        /** The bill came out today: a nudge to pay it early, so it isn't forgotten. */
        isNewBill: Boolean = false
    ) {
        ensureChannels(context)
        val formattedAmount = formatAmount(amountDue)
        val title = when {
            isNewBill -> context.getString(R.string.credit_card_new_bill, cardName)
            daysUntilDue == 0 -> context.getString(R.string.credit_card_due_today, cardName)
            daysUntilDue == 1 -> context.getString(R.string.credit_card_due_tomorrow, cardName)
            else -> context.getString(R.string.credit_card_due_in_days, cardName, daysUntilDue)
        }
        val formattedDate = dueDate.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
        val message = context.getString(R.string.credit_card_due_message, formattedAmount, formattedDate)

        val contentIntent = PendingIntent.getActivity(
            context,
            cardId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "creditCards")
                putExtra("cardId", cardId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CREDIT_CARD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(ContextCompat.getColor(context, R.color.sparely_spruce))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        val notificationId = CREDIT_CARD_BASE_NOTIFICATION_ID + (cardId % 1000).toInt()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun showCreditCardUtilizationAlert(
        context: Context,
        cardId: Long,
        cardName: String,
        utilization: Int,
        threshold: Int
    ) {
        ensureChannels(context)
        val title = context.getString(R.string.notif_utilization_title, cardName)
        val message = context.getString(R.string.notif_utilization_message, utilization, threshold)

        val contentIntent = PendingIntent.getActivity(
            context,
            cardId.toInt() + 2000,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "creditCards")
                putExtra("cardId", cardId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CREDIT_CARD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(ContextCompat.getColor(context, R.color.sparely_spruce))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        val notificationId = CREDIT_CARD_BASE_NOTIFICATION_ID + (cardId % 1000).toInt() + 1000
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun dismissCreditCardReminder(context: Context, cardId: Long) {
        val notificationId = CREDIT_CARD_BASE_NOTIFICATION_ID + (cardId % 1000).toInt()
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun showVariableRecurringExpenseNotification(
        context: Context,
        recurringExpenseId: Long,
        recurringExpenseName: String,
        predictedAmount: Double
    ) {
        ensureChannels(context)
        val formattedAmount = formatAmount(predictedAmount)
        val title = context.getString(R.string.notif_variable_bill_title, recurringExpenseName)
        val body = context.getString(R.string.notif_variable_bill_body, formattedAmount)

        val contentIntent = PendingIntent.getActivity(
            context,
            recurringExpenseId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "variableRecurring")
                putExtra("recurringExpenseId", recurringExpenseId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val editAmountIntent = PendingIntent.getBroadcast(
            context,
            (recurringExpenseId % Int.MAX_VALUE).toInt(),
            com.example.sparely.notifications.VariableRecurringExpenseReceiver.createEditAmountIntent(
                context,
                recurringExpenseId
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val confirmIntent = PendingIntent.getBroadcast(
            context,
            (recurringExpenseId % Int.MAX_VALUE).toInt() + 1,
            com.example.sparely.notifications.VariableRecurringExpenseReceiver.createConfirmIntent(
                context,
                recurringExpenseId,
                predictedAmount
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PAYDAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setColor(ContextCompat.getColor(context, R.color.sparely_spruce))
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_variable_bill_short, formattedAmount))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_sparely_mark_mono,
                context.getString(R.string.pending_bills_confirm),
                confirmIntent
            )
            .addAction(
                R.drawable.ic_sparely_mark_mono,
                context.getString(R.string.notif_action_edit_amount),
                editAmountIntent
            )
            .build()

        NotificationManagerCompat.from(context).notify(VARIABLE_RECURRING_NOTIFICATION_ID, notification)
    }

    fun dismissVariableRecurringNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(VARIABLE_RECURRING_NOTIFICATION_ID)
    }

    fun formatAmount(amount: Double): String {
        // Avoid negative zero and very small negative values rounding to -0.00
        val sanitizedAmount = if (amount > -0.005 && amount <= 0.0) 0.0 else amount
        return NumberFormat.getCurrencyInstance().apply {
            isGroupingUsed = false
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(sanitizedAmount)
    }

    private fun transferLine(context: Context, amount: Double, schedule: VaultSchedule, vaultName: String): String =
        when (schedule.direction) {
            VaultTransferDirection.MAIN_TO_VAULT -> context.getString(R.string.notif_transfer_into, formatAmount(amount), vaultName)
            VaultTransferDirection.VAULT_TO_MAIN -> context.getString(R.string.notif_transfer_from, formatAmount(amount), vaultName)
        }

    private fun scheduleNotificationId(scheduleId: Long, offset: Int): Int {
        val base = (scheduleId % Int.MAX_VALUE).toInt()
        return base * 10 + offset
    }

    private fun vaultScheduleIntent(
        context: Context,
        schedule: VaultSchedule,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "vaultDetails")
            putExtra("vault_id", schedule.vaultId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
