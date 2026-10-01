package com.example.sparely.domain.usecase

import com.example.sparely.data.local.ExpenseEntity
import com.example.sparely.data.local.MainAccountTransactionType
import com.example.sparely.data.preferences.UserPreferencesRepository
import com.example.sparely.data.repository.SavingsRepository
import com.example.sparely.domain.model.MainAccountTransaction
import com.example.sparely.domain.model.VaultAdjustmentType
import java.time.LocalDateTime

/**
 * Deletes an expense and gives back exactly what it took, to where it came from: the vault part
 * to the vault, the main-account part (net of refunds already credited) to the main account.
 * Credit card impact is handled by the repository; expenses that were never deducted give
 * nothing back. Shared by the UI and by undoing an assistant's expense.
 */
class DeleteExpenseUseCase(
    private val savingsRepository: SavingsRepository,
    private val preferencesRepository: UserPreferencesRepository
) {

    data class Result(
        val deletedExpense: ExpenseEntity,
        /** Amount credited back to the main account. */
        val mainAccountCredit: Double,
        /** Vault id and amount returned to it, when part of the expense came from a vault. */
        val vaultCredit: Pair<Long, Double>?
    )

    /** Returns null when no expense has this id. */
    suspend operator fun invoke(expenseId: Long): Result? {
        var result: Result? = null
        savingsRepository.withMainAccountLock { savingsRepository.runInTransaction {
            val expenseEntity = savingsRepository.findExpenseById(expenseId) ?: return@runInTransaction

            val reversal = savingsRepository.computeExpenseReversal(expenseEntity)
            var creditBack = reversal.mainAccountCredit
            var restoredToVault = 0.0
            val sourceVault = reversal.vaultId?.let { savingsRepository.getSmartVaultById(it) }
            if (reversal.vaultCredit > 0.0) {
                if (sourceVault != null) {
                    savingsRepository.recordVaultBalanceAdjustment(
                        vaultId = sourceVault.id,
                        previousBalance = sourceVault.currentBalance,
                        newBalance = sourceVault.currentBalance + reversal.vaultCredit,
                        type = VaultAdjustmentType.MANUAL_DEPOSIT,
                        reason = "Reversal of deleted expense: ${expenseEntity.description.take(80)}"
                    )
                    restoredToVault = reversal.vaultCredit
                } else {
                    // The vault no longer exists: return its share to the main account instead.
                    creditBack += reversal.vaultCredit
                }
            }
            if (creditBack > 0.0) {
                val currentBalance = savingsRepository.getLatestMainAccountBalance()
                val newBalance = currentBalance + creditBack
                savingsRepository.insertMainAccountTransaction(
                    MainAccountTransaction(
                        type = MainAccountTransactionType.DEPOSIT,
                        amount = creditBack,
                        balanceAfter = newBalance,
                        timestamp = LocalDateTime.now(),
                        description = "Reversal of deleted expense: ${expenseEntity.description}",
                        relatedExpenseId = null // The expense is being deleted
                    )
                )
                preferencesRepository.updateMainAccountBalance(newBalance)
            }

            // Cancel pending tax contributions, then delete the expense record
            savingsRepository.deletePendingContributionsForExpense(expenseId)
            savingsRepository.deleteExpense(expenseEntity)

            result = Result(
                deletedExpense = expenseEntity,
                mainAccountCredit = creditBack,
                vaultCredit = if (restoredToVault > 0.0) sourceVault?.id?.let { it to restoredToVault } else null
            )
        } }
        return result
    }
}
