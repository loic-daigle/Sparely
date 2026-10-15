package com.example.sparely.payments

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.sparely.AppContainer
import com.example.sparely.SparelyApplication
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.ExpenseInput
import com.example.sparely.domain.usecase.AddExpenseUseCase
import com.example.sparely.notifications.NotificationHelper
import com.sparely.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The Add and Ignore buttons on a payment suggestion. Edit opens the app instead. */
class PaymentSuggestionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(PaymentSuggestionNotifier.EXTRA_NOTIFICATION_ID, 0)
        PaymentSuggestionNotifier.cancel(context, id)
        if (intent.action != ACTION_ADD) return

        val amount = intent.getDoubleExtra(PaymentSuggestionNotifier.EXTRA_AMOUNT, 0.0)
        if (!amount.isFinite() || amount <= 0.0) return
        val merchant = intent.getStringExtra(PaymentSuggestionNotifier.EXTRA_MERCHANT)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as SparelyApplication).container
                recordExpense(context, container, amount, merchant)
                val formatted = NotificationHelper.formatAmount(amount)
                NotificationHelper.showReminder(
                    context,
                    merchant?.let { context.getString(R.string.payment_logged_merchant, formatted, it) }
                        ?: context.getString(R.string.payment_logged, formatted)
                )
            } catch (e: Exception) {
                android.util.Log.e("PaymentSuggestionReceiver", "Failed to log the detected payment", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** Same defaults as a new expense in the app; the store and category come from past purchases there. */
    private suspend fun recordExpense(context: Context, container: AppContainer, amount: Double, merchant: String?) {
        val repository = container.savingsRepository
        val guess = PaymentExpenseDefaults.guess(container, merchant)
        val paymentMethods = repository.observePaymentMethods().first()
        val defaultMethod = paymentMethods.find { it.isDefault }
        val input = ExpenseInput(
            description = merchant ?: context.getString(R.string.payment_default_description),
            amount = amount,
            category = guess.category,
            date = LocalDate.now(),
            includesTax = false,
            deductFromMainAccount = defaultMethod?.defaultDeductFromMainAccount ?: false,
            paymentMethodId = defaultMethod?.id,
            storeId = guess.storeId
        )
        AddExpenseUseCase(repository, container.preferencesRepository)(
            input,
            AddExpenseUseCase.Context(
                settings = container.preferencesRepository.getSettingsSnapshot(),
                recommendedPercentages = null,
                paymentMethods = paymentMethods,
                smartVaults = repository.observeSmartVaults().first()
            )
        )
    }

    companion object {
        const val ACTION_ADD = "com.sparely.app.payment.ADD"
        const val ACTION_IGNORE = "com.sparely.app.payment.IGNORE"

        fun intent(context: Context, action: String, payment: DetectedPayment, notificationId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId xor action.hashCode(),
                Intent(context, PaymentSuggestionReceiver::class.java).apply {
                    this.action = action
                    putExtra(PaymentSuggestionNotifier.EXTRA_AMOUNT, payment.amount)
                    putExtra(PaymentSuggestionNotifier.EXTRA_MERCHANT, payment.merchant)
                    putExtra(PaymentSuggestionNotifier.EXTRA_NOTIFICATION_ID, notificationId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}

/** Matches a detected store name to a saved store and reuses the category last used there. */
object PaymentExpenseDefaults {
    data class Guess(val storeId: Long?, val category: ExpenseCategory)

    suspend fun guess(container: AppContainer, merchant: String?): Guess {
        if (merchant.isNullOrBlank()) return Guess(null, ExpenseCategory.OTHER)
        val repository = container.savingsRepository
        val store = repository.observeStores().first().firstOrNull { it.name.trim().equals(merchant.trim(), ignoreCase = true) }
        val previous = repository.observeExpenses().first().firstOrNull { expense ->
            (store != null && expense.storeId == store.id) || expense.description.trim().equals(merchant.trim(), ignoreCase = true)
        }
        return Guess(store?.id, previous?.category ?: ExpenseCategory.OTHER)
    }
}
