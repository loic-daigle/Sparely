package com.example.sparely.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sparely.AppContainer
import com.example.sparely.SparelyApplication
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Worker that checks for upcoming credit card billing due dates and shows reminders.
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
        
        // Check each credit card with a billing cycle day
        creditCards.forEach { card ->
            val billingDay = card.billingCycleDay ?: return@forEach
            if (card.currentBalance <= 0) return@forEach
            
            val dueDate = calculateNextBillingDate(today, billingDay)
            val daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate).toInt()
            
            // Show reminder if within the reminder window
            if (daysUntilDue in 0..daysBefore) {
                NotificationHelper.showCreditCardDueReminder(
                    context = context,
                    cardId = card.id,
                    cardName = card.name,
                    balance = card.currentBalance,
                    daysUntilDue = daysUntilDue
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
    
    private fun calculateNextBillingDate(today: LocalDate, billingDay: Int): LocalDate {
        val safeBillingDay = billingDay.coerceIn(1, 28)
        var dueDate = today.withDayOfMonth(safeBillingDay)
        if (dueDate.isBefore(today)) {
            dueDate = dueDate.plusMonths(1)
        }
        return dueDate
    }
    
    companion object {
        const val WORK_NAME = "credit-card-reminder"
    }
}
