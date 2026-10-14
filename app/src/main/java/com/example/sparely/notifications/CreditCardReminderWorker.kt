package com.example.sparely.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sparely.AppContainer
import com.example.sparely.SparelyApplication
import kotlinx.coroutines.flow.first
import com.example.sparely.domain.logic.CreditCardStatements
import java.time.LocalDate

/**
 * Worker that checks for credit card bills coming due and shows reminders.
 * Runs daily at the configured reminder hour.
 */
class CreditCardReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = context.applicationContext as? SparelyApplication ?: return Result.failure()
        val container = app.container
        
        val settings = container.preferencesRepository.getSettingsSnapshot()
        if (!settings.creditCardReminderEnabled) {
            return Result.success()
        }
        
        val creditCards = container.savingsRepository.observeCreditCards().first()
        if (creditCards.isEmpty()) {
            return Result.success()
        }
        
        val today = LocalDate.now()
        val daysBefore = settings.creditCardReminderDaysBefore
        // Purchases since a statement came out are on the next bill, so the amount due needs them.
        val expenses = container.savingsRepository.observeExpenses().first()
        
        // The billing day is the statement date; the payment is due some days after it.
        creditCards.forEach { card ->
            if (card.currentBalance <= 0) return@forEach
            val bill = CreditCardStatements.currentBill(card, expenses, today) ?: return@forEach

            // Remind only about what's on the bill, ahead of its real due date.
            if (!bill.isPaid && bill.daysUntilDue in 0..daysBefore) {
                NotificationHelper.showCreditCardDueReminder(
                    context = context,
                    cardId = card.id,
                    cardName = card.name,
                    amountDue = bill.amountDue,
                    dueDate = bill.dueDate,
                    daysUntilDue = bill.daysUntilDue
                )
            }

            // High utilization check
            if (settings.creditCardUtilizationAlertEnabled) {
                val limit = card.creditLimit
                if (limit != null && limit > 0) {
                    val utilizationPercent = ((card.currentBalance / limit) * 100).toInt()
                    if (utilizationPercent >= settings.creditCardUtilizationThreshold) {
                        NotificationHelper.showCreditCardUtilizationAlert(
                            context = context,
                            cardId = card.id,
                            cardName = card.name,
                            utilization = utilizationPercent,
                            threshold = settings.creditCardUtilizationThreshold
                        )
                    }
                }
            }
        }
        
        return Result.success()
    }
    
    companion object {
        const val WORK_NAME = "credit-card-reminder"
    }
}
