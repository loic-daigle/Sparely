package com.example.sparely.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.sparely.MainActivity
import com.example.sparely.SparelyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class VariableRecurringExpenseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CONFIRM_AMOUNT -> {
                val recurringExpenseId = intent.getLongExtra(EXTRA_RECURRING_EXPENSE_ID, -1L)
                val confirmedAmount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)

                if (recurringExpenseId > 0 && confirmedAmount.isFinite() && confirmedAmount > 0) {
                    // Dismiss the notification
                    NotificationHelper.dismissVariableRecurringNotification(context)

                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val container = (context.applicationContext as SparelyApplication).container
                            val repository = container.savingsRepository
                            repository.processVariableRecurringExpenseWithAmount(
                                recurringExpenseId = recurringExpenseId,
                                actualAmount = confirmedAmount,
                                processDate = java.time.LocalDate.now(),
                                settings = container.preferencesRepository.getSettingsSnapshot(),
                                vaults = repository.observeSmartVaults().first()
                            )
                            // Show a brief confirmation notification
                            NotificationHelper.showReminder(
                                context,
                                "Amount confirmed: ${NotificationHelper.formatAmount(confirmedAmount)}"
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("VariableRecurringReceiver", "Failed to record confirmed amount", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            ACTION_EDIT_AMOUNT -> {
                val recurringExpenseId = intent.getLongExtra(EXTRA_RECURRING_EXPENSE_ID, -1L)

                // Dismiss the notification and open the app to let user edit the amount
                NotificationHelper.dismissVariableRecurringNotification(context)

                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "variableRecurring")
                    putExtra("recurringExpenseId", recurringExpenseId)
                }
                context.startActivity(mainIntent)
            }
        }
    }

    companion object {
        const val ACTION_CONFIRM_AMOUNT = "com.example.sparely.ACTION_CONFIRM_VARIABLE_RECURRING"
        const val ACTION_EDIT_AMOUNT = "com.example.sparely.ACTION_EDIT_VARIABLE_RECURRING"
        const val EXTRA_RECURRING_EXPENSE_ID = "recurring_expense_id"
        const val EXTRA_AMOUNT = "amount"

        fun createConfirmIntent(
            context: Context,
            recurringExpenseId: Long,
            amount: Double
        ): Intent {
            return Intent(context, VariableRecurringExpenseReceiver::class.java).apply {
                action = ACTION_CONFIRM_AMOUNT
                putExtra(EXTRA_RECURRING_EXPENSE_ID, recurringExpenseId)
                putExtra(EXTRA_AMOUNT, amount)
            }
        }

        fun createEditAmountIntent(
            context: Context,
            recurringExpenseId: Long
        ): Intent {
            return Intent(context, VariableRecurringExpenseReceiver::class.java).apply {
                action = ACTION_EDIT_AMOUNT
                putExtra(EXTRA_RECURRING_EXPENSE_ID, recurringExpenseId)
            }
        }
    }
}
