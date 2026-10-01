package com.example.sparely.appfunctions

import android.content.Context
import com.example.sparely.AppContainer
import com.example.sparely.domain.model.AssistantAction
import com.example.sparely.domain.model.AssistantActionType
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.ExpenseInput
import com.example.sparely.domain.model.IncomeCategory
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.usecase.AddExpenseUseCase
import com.example.sparely.domain.usecase.RecordIncomeUseCase
import com.example.sparely.notifications.AssistantActionNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Performs assistant writes through the same use cases the app's own screens use. */
class RepositoryAssistantWriteGateway(
    private val context: Context,
    private val container: AppContainer
) : AssistantWriteGateway {

    private val addExpense = AddExpenseUseCase(container.savingsRepository, container.preferencesRepository)
    private val recordIncomeUseCase = RecordIncomeUseCase(container.savingsRepository, container.preferencesRepository)

    override suspend fun settings(): SparelySettings = container.preferencesRepository.getSettingsSnapshot()

    override suspend fun recordExpense(
        description: String,
        amount: Double,
        category: ExpenseCategory,
        date: LocalDate,
        notes: String?
    ): Long = withContext(Dispatchers.IO) {
        val repository = container.savingsRepository
        val paymentMethods = repository.observePaymentMethods().first()
        // Same defaults as a new expense in the app: the default payment method decides whether
        // the main account is debited.
        val defaultMethod = paymentMethods.find { it.isDefault }
        val input = ExpenseInput(
            description = description,
            amount = amount,
            category = category,
            date = date,
            includesTax = false,
            deductFromMainAccount = defaultMethod?.defaultDeductFromMainAccount ?: false,
            paymentMethodId = defaultMethod?.id,
            notes = notes
        )
        addExpense(
            input,
            AddExpenseUseCase.Context(
                settings = settings(),
                // Not computed outside the UI; the use case falls back to the default split.
                recommendedPercentages = null,
                paymentMethods = paymentMethods,
                smartVaults = repository.observeSmartVaults().first()
            )
        ).expenseId
    }

    override suspend fun recordIncome(amount: Double, description: String, category: IncomeCategory?): Long =
        recordIncomeUseCase(amount, description, category)

    override suspend fun actionsSince(epochMillis: Long): List<AssistantAction> =
        container.assistantActionRepository.actionsSince(epochMillis)

    override suspend fun logAndNotify(
        type: AssistantActionType,
        recordId: Long,
        description: String,
        amount: Double
    ): AssistantAction {
        val action = container.assistantActionRepository.log(type, recordId, description, amount)
        AssistantActionNotifier.showRecorded(context, action, settings().regionalSettings.currencyCode)
        return action
    }
}
