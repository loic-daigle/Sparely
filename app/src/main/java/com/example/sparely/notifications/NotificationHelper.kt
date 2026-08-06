package com.example.sparely.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
    private const val REMINDER_NOTIFICATION_ID = 1001
    private const val AUTO_DEPOSIT_NOTIFICATION_ID = 3001
    private const val VAULT_TRANSFER_NOTIFICATION_ID = 4001
    private const val PAYDAY_NOTIFICATION_ID = 4002
    private const val SCHEDULE_SUMMARY_NOTIFICATION_ID = 5001
    private const val CREDIT_CARD_BASE_NOTIFICATION_ID = 6001
    private const val VARIABLE_RECURRING_NOTIFICATION_ID = 7001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                context.getString(R.string.app_name) + " reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Savings and goal nudges from Sparely"
            }
            val autoDepositChannel = NotificationChannel(
                AUTO_DEPOSIT_CHANNEL_ID,
                "Auto Deposits",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for scheduled vault auto-deposits"
            }
            val paydayChannel = NotificationChannel(
                PAYDAY_CHANNEL_ID,
                "Payday Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders to log your paycheck"
                setShowBadge(false)
            }
            val vaultTransferChannel = NotificationChannel(
                VAULT_TRANSFER_CHANNEL_ID,
                "Vault Transfers",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Step-by-step vault transfer workflow"
                setShowBadge(false)
            }
            val creditCardChannel = NotificationChannel(
                CREDIT_CARD_CHANNEL_ID,
                "Credit Card Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for credit card bill due dates"
                setShowBadge(true)
            }
            manager.createNotificationChannel(reminderChannel)
            manager.createNotificationChannel(autoDepositChannel)
            manager.createNotificationChannel(paydayChannel)
            manager.createNotificationChannel(vaultTransferChannel)
            manager.createNotificationChannel(creditCardChannel)
        }
    }

    fun showReminder(context: Context, message: String) {
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle("Sparely reminder")
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
            "Vault Auto-Deposit Due"
        } else {
            "$depositCount Vault Auto-Deposits Due"
        }
        val message = "Transfer $formattedAmount to your savings vaults and mark them as complete in the app."
        
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
            .setContentTitle("Scheduled transfer starting soon")
            .setContentText("${formatAmount(amount)} ${directionPhrase(schedule)} $vaultName")
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
            .setContentTitle("Scheduled transfer complete")
            .setContentText("${formatAmount(amount)} ${directionPhrase(schedule)} $vaultName")
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
        val message = "Transfer of ${formatAmount(amount)} ${directionPhrase(schedule)} $vaultName did not complete."
        val notificationId = scheduleNotificationId(schedule.id, 3)
        val notification = NotificationCompat.Builder(context, AUTO_DEPOSIT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setContentTitle("Scheduled transfer needs attention")
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
        val title = if (count == 1) "1 scheduled transfer executed" else "$count scheduled transfers executed"
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
            .setContentTitle(title)
            .setContentText("$formattedAmount processed today")
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
            " (${currentIndex + 1} of $totalVaultCount)"
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
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Transferred",
                transferredIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Dismiss",
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
        val formattedDate = expectedDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
        val baseMessage = "Enter the amount you received this payday."
        val body = if (suggestedAmount != null && suggestedAmount > 0.0) {
            val formattedAmount = formatAmount(suggestedAmount)
            "$baseMessage\nSuggested amount based on recent paychecks: $formattedAmount"
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
            .setContentTitle("Payday — $formattedDate")
            .setContentText(baseMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Record Income",
                recordIncomeIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Remind Later",
                remindLaterIntent
            )
            .build()

        NotificationManagerCompat.from(context).notify(PAYDAY_NOTIFICATION_ID, notification)
    }

    fun dismissPaydayReminder(context: Context) {
        NotificationManagerCompat.from(context).cancel(PAYDAY_NOTIFICATION_ID)
    }

    fun showCreditCardDueReminder(
        context: Context,
        cardId: Long,
        cardName: String,
        balance: Double,
        daysUntilDue: Int
    ) {
        ensureChannels(context)
        val formattedBalance = formatAmount(balance)
        val title = when (daysUntilDue) {
            0 -> "$cardName bill due today!"
            1 -> "$cardName bill due tomorrow"
            else -> "$cardName bill due in $daysUntilDue days"
        }
        val message = "Current balance: $formattedBalance. Tap to pay now."

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
        val title = "High utilization on $cardName"
        val message = "Utilization is at $utilization%, which exceeds your $threshold% limit. Tap to pay now."

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
        val title = "Confirm bill amount: $recurringExpenseName"
        val body = "Predicted amount: $formattedAmount. Please confirm the actual amount charged."

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
            .setContentTitle(title)
            .setContentText("Predicted: $formattedAmount")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Confirm",
                confirmIntent
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Edit Amount",
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

    private fun directionPhrase(schedule: VaultSchedule): String {
        return when (schedule.direction) {
            VaultTransferDirection.MAIN_TO_VAULT -> "into"
            VaultTransferDirection.VAULT_TO_MAIN -> "from"
        }
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
