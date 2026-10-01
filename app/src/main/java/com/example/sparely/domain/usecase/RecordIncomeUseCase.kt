package com.example.sparely.domain.usecase

import com.example.sparely.data.local.MainAccountTransactionType
import com.example.sparely.data.preferences.UserPreferencesRepository
import com.example.sparely.data.repository.SavingsRepository
import com.example.sparely.domain.model.IncomeCategory
import com.example.sparely.domain.model.MainAccountTransaction
import java.time.LocalDateTime

/** Deposits income into the main account. Shared by the UI and by AI assistants. */
class RecordIncomeUseCase(
    private val savingsRepository: SavingsRepository,
    private val preferencesRepository: UserPreferencesRepository
) {

    /**
     * Returns the id of the new main-account transaction.
     * @throws IllegalArgumentException if [amount] is not a finite positive number.
     */
    suspend operator fun invoke(amount: Double, description: String, incomeCategory: IncomeCategory?): Long {
        require(amount.isFinite() && amount > 0.0) { "Income amount must be a positive number" }
        return savingsRepository.withMainAccountLock {
            val newBalance = savingsRepository.getLatestMainAccountBalance() + amount
            val transactionId = savingsRepository.insertMainAccountTransaction(
                MainAccountTransaction(
                    type = MainAccountTransactionType.DEPOSIT,
                    amount = amount,
                    balanceAfter = newBalance,
                    timestamp = LocalDateTime.now(),
                    description = description.take(100),
                    incomeCategory = incomeCategory
                )
            )
            preferencesRepository.updateMainAccountBalance(newBalance)
            transactionId
        }
    }
}
