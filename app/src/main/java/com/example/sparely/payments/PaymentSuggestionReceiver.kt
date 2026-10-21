package com.example.sparely.payments

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.sparely.AppContainer
import com.example.sparely.SparelyApplication
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.ExpenseInput
import com.example.sparely.domain.model.PaymentMethod
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.usecase.AddExpenseUseCase
import com.example.sparely.domain.usecase.DeleteExpenseUseCase
import com.example.sparely.notifications.NotificationHelper
import com.sparely.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The Add and Ignore buttons on a payment suggestion, and Undo on the "Logged" notification that
 * follows Add. Edit opens the app instead.
 */
class PaymentSuggestionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(PaymentSuggestionNotifier.EXTRA_NOTIFICATION_ID, 0)
        PaymentSuggestionNotifier.cancel(context, id)
        when (intent.action) {
            ACTION_ADD -> add(context, intent, id)
            ACTION_UNDO -> undo(context, intent, id)
        }
    }

    /** Removes the purchase Add just logged, reversing its balances and allocations like a delete in the app. */
    private fun undo(context: Context, intent: Intent, id: Int) {
        val expenseId = intent.getLongExtra(PaymentSuggestionNotifier.EXTRA_EXPENSE_ID, -1L)
        if (expenseId <= 0L) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as SparelyApplication).container
                DeleteExpenseUseCase(container.savingsRepository, container.preferencesRepository)(expenseId)
                PaymentSuggestionNotifier.showUndone(context, id)
            } catch (e: Exception) {
                android.util.Log.e("PaymentSuggestionReceiver", "Failed to undo the logged payment", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun add(context: Context, intent: Intent, id: Int) {
        val amount = intent.getDoubleExtra(PaymentSuggestionNotifier.EXTRA_AMOUNT, 0.0)
        if (!amount.isFinite() || amount <= 0.0) return
        val merchant = intent.getStringExtra(PaymentSuggestionNotifier.EXTRA_MERCHANT)
        val sourcePackage = intent.getStringExtra(PaymentSuggestionNotifier.EXTRA_SOURCE_PACKAGE)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as SparelyApplication).container
                val expenseId = recordExpense(context, container, amount, merchant, sourcePackage)
                val formatted = NotificationHelper.formatAmount(amount)
                PaymentSuggestionNotifier.showLogged(
                    context,
                    id,
                    merchant?.let { context.getString(R.string.payment_logged_merchant, formatted, it) }
                        ?: context.getString(R.string.payment_logged, formatted),
                    expenseId
                )
            } catch (e: Exception) {
                android.util.Log.e("PaymentSuggestionReceiver", "Failed to log the detected payment", e)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Same defaults as a new expense in the app; the store and category come from past purchases
     * there, and the payment method is the one set for the source app (else the default one).
     */
    private suspend fun recordExpense(
        context: Context,
        container: AppContainer,
        amount: Double,
        merchant: String?,
        sourcePackage: String?
    ): Long {
        val repository = container.savingsRepository
        val guess = PaymentExpenseDefaults.guess(container, merchant)
        val paymentMethods = repository.observePaymentMethods().first()
        val settings = container.preferencesRepository.getSettingsSnapshot()
        val method = PaymentExpenseDefaults.paymentMethodFor(settings, paymentMethods, sourcePackage)
        val input = ExpenseInput(
            description = merchant ?: context.getString(R.string.payment_default_description),
            amount = amount,
            category = guess.category,
            date = LocalDate.now(),
            includesTax = false,
            deductFromMainAccount = method?.defaultDeductFromMainAccount ?: false,
            paymentMethodId = method?.id,
            storeId = guess.storeId,
            // Logged straight from the notification, it's linked to the project being worked on like the form does
            assetAllocations = settings.workingOnProjectId
                ?.takeIf { id -> repository.getActiveAssets().any { it.id == id && it.isProject && !it.isDone } }
                ?.let { mapOf(it to 1.0) }
                ?: emptyMap()
        )
        return AddExpenseUseCase(repository, container.preferencesRepository)(
            input,
            AddExpenseUseCase.Context(
                settings = settings,
                recommendedPercentages = null,
                paymentMethods = paymentMethods,
                smartVaults = repository.observeSmartVaults().first()
            )
        ).expenseId
    }

    companion object {
        const val ACTION_ADD = "com.sparely.app.payment.ADD"
        const val ACTION_IGNORE = "com.sparely.app.payment.IGNORE"
        const val ACTION_UNDO = "com.sparely.app.payment.UNDO"

        fun undoIntent(context: Context, notificationId: Int, expenseId: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId xor ACTION_UNDO.hashCode(),
                Intent(context, PaymentSuggestionReceiver::class.java).apply {
                    action = ACTION_UNDO
                    putExtra(PaymentSuggestionNotifier.EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(PaymentSuggestionNotifier.EXTRA_EXPENSE_ID, expenseId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        fun intent(
            context: Context,
            action: String,
            payment: DetectedPayment,
            notificationId: Int,
            sourcePackage: String
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId xor action.hashCode(),
                Intent(context, PaymentSuggestionReceiver::class.java).apply {
                    this.action = action
                    putExtra(PaymentSuggestionNotifier.EXTRA_AMOUNT, payment.amount)
                    putExtra(PaymentSuggestionNotifier.EXTRA_MERCHANT, payment.merchant)
                    putExtra(PaymentSuggestionNotifier.EXTRA_NOTIFICATION_ID, notificationId)
                    putExtra(PaymentSuggestionNotifier.EXTRA_SOURCE_PACKAGE, sourcePackage)
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

    /** The method set for [sourcePackage] in Settings if it still exists, else the default method. */
    fun paymentMethodFor(
        settings: SparelySettings,
        paymentMethods: List<PaymentMethod>,
        sourcePackage: String?
    ): PaymentMethod? {
        val chosenId = sourcePackage?.let { settings.paymentDetectionMethods[it] }
        return paymentMethods.find { it.id == chosenId } ?: paymentMethods.find { it.isDefault }
    }
}
