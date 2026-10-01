package com.example.sparely.domain.usecase

import com.example.sparely.data.local.MainAccountTransactionType
import com.example.sparely.data.preferences.UserPreferencesRepository
import com.example.sparely.data.repository.AssistantActionRepository
import com.example.sparely.data.repository.SavingsRepository
import com.example.sparely.domain.model.AssistantAction
import com.example.sparely.domain.model.AssistantActionType
import com.example.sparely.domain.model.MainAccountTransaction
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Reverses a change an AI assistant made: deletes the expense it recorded (giving the money back
 * like a normal delete), or withdraws the income it added. Runs at most once per action.
 */
class UndoAssistantActionUseCase(
    private val savingsRepository: SavingsRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val assistantActionRepository: AssistantActionRepository,
    private val deleteExpense: DeleteExpenseUseCase = DeleteExpenseUseCase(savingsRepository, preferencesRepository)
) {

    enum class Outcome { UNDONE, ALREADY_UNDONE, NOT_FOUND }

    suspend operator fun invoke(actionId: Long): Outcome = savingsRepository.withMainAccountLock {
        val action = assistantActionRepository.findById(actionId) ?: return@withMainAccountLock Outcome.NOT_FOUND
        if (action.isUndone) return@withMainAccountLock Outcome.ALREADY_UNDONE

        var outcome = Outcome.ALREADY_UNDONE
        savingsRepository.runInTransaction {
            // Claim the undo first so a double tap can't reverse the money twice.
            if (!assistantActionRepository.markUndone(action.id)) return@runInTransaction
            // Only reverse the record if it is still the one the assistant created: after a reset
            // or backup restore the same id can belong to something else. If it is gone or
            // different, there is nothing of the assistant's left to reverse.
            when (action.type) {
                AssistantActionType.EXPENSE_RECORDED -> {
                    val expense = savingsRepository.findExpenseById(action.recordId)
                    if (expense != null && matches(expense.description, expense.amount, action)) {
                        deleteExpense(action.recordId)
                    }
                }
                AssistantActionType.INCOME_RECORDED -> {
                    val deposit = savingsRepository.getMainAccountTransactionById(action.recordId)
                    if (deposit != null && deposit.type == MainAccountTransactionType.DEPOSIT &&
                        matches(deposit.description, deposit.amount, action)
                    ) {
                        val newBalance = savingsRepository.getLatestMainAccountBalance() - action.amount
                        savingsRepository.insertMainAccountTransaction(
                            MainAccountTransaction(
                                type = MainAccountTransactionType.WITHDRAWAL,
                                amount = action.amount,
                                balanceAfter = newBalance,
                                timestamp = LocalDateTime.now(),
                                description = "Undo of assistant income: ${action.description}".take(100)
                            )
                        )
                        preferencesRepository.updateMainAccountBalance(newBalance)
                    }
                }
            }
            outcome = Outcome.UNDONE
        }
        outcome
    }

    private fun matches(description: String, amount: Double, action: AssistantAction): Boolean =
        description == action.description && abs(amount - action.amount) < 0.005
}
