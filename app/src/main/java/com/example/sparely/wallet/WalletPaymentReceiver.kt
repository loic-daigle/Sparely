package com.example.sparely.wallet

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

/** The Add and Ignore buttons on a Google Wallet suggestion. Edit opens the app instead. */
class WalletPaymentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(WalletPaymentNotifier.EXTRA_NOTIFICATION_ID, 0)
        WalletPaymentNotifier.cancel(context, id)
        if (intent.action != ACTION_ADD) return

        val amount = intent.getDoubleExtra(WalletPaymentNotifier.EXTRA_AMOUNT, 0.0)
        if (!amount.isFinite() || amount <= 0.0) return
        val merchant = intent.getStringExtra(WalletPaymentNotifier.EXTRA_MERCHANT)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as SparelyApplication).container
                recordExpense(context, container, amount, merchant)
                val formatted = NotificationHelper.formatAmount(amount)
                NotificationHelper.showReminder(
                    context,
                    merchant?.let { context.getString(R.string.wallet_logged_merchant, formatted, it) }
                        ?: context.getString(R.string.wallet_logged, formatted)
                )
            } catch (e: Exception) {
                android.util.Log.e("WalletPaymentReceiver", "Failed to log the Wallet payment", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** Same defaults as a new expense in the app; the store and category come from past purchases there. */
    private suspend fun recordExpense(context: Context, container: AppContainer, amount: Double, merchant: String?) {
        val repository = container.savingsRepository
        val guess = WalletExpenseDefaults.guess(container, merchant)
        val paymentMethods = repository.observePaymentMethods().first()
        val defaultMethod = paymentMethods.find { it.isDefault }
        val input = ExpenseInput(
            description = merchant ?: context.getString(R.string.wallet_default_description),
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
        const val ACTION_ADD = "com.sparely.app.wallet.ADD"
        const val ACTION_IGNORE = "com.sparely.app.wallet.IGNORE"

        fun intent(context: Context, action: String, payment: WalletPayment, notificationId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId xor action.hashCode(),
                Intent(context, WalletPaymentReceiver::class.java).apply {
                    this.action = action
                    putExtra(WalletPaymentNotifier.EXTRA_AMOUNT, payment.amount)
                    putExtra(WalletPaymentNotifier.EXTRA_MERCHANT, payment.merchant)
                    putExtra(WalletPaymentNotifier.EXTRA_NOTIFICATION_ID, notificationId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}

/** Matches a Wallet store name to a saved store and reuses the category last used there. */
object WalletExpenseDefaults {
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
