package com.example.sparely.domain.usecase

import com.example.sparely.data.local.ExpenseEntity
import com.example.sparely.data.local.MainAccountTransactionType
import com.example.sparely.data.preferences.UserPreferencesRepository
import com.example.sparely.data.repository.SavingsRepository
import com.example.sparely.domain.logic.SavingTaxEngine
import com.example.sparely.domain.logic.SavingsCalculator
import com.example.sparely.domain.model.ExpenseInput
import com.example.sparely.domain.model.MainAccountTransaction
import com.example.sparely.domain.model.PaymentMethod
import com.example.sparely.domain.model.SavingsPercentages
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.model.VaultAdjustmentType
import com.example.sparely.domain.model.VaultArchivePrompt
import com.example.sparely.domain.model.VaultContribution
import com.example.sparely.domain.model.VaultContributionSource
import java.time.LocalDateTime

/**
 * Records an expense together with every money movement it implies: savings allocation, vault
 * deduction (with overflow to the main account), main-account debit and saving-tax contributions.
 *
 * This is the single entry point for creating an expense, so every caller (the UI, and later the
 * AppFunctions exposed to AI assistants) leaves balances consistent. Everything runs as one atomic
 * unit under the main-account lock: a failure halfway must not leave an expense without its money
 * moves.
 */
class AddExpenseUseCase(
    private val savingsRepository: SavingsRepository,
    private val preferencesRepository: UserPreferencesRepository
) {

    /** App state the expense is recorded against. */
    data class Context(
        val settings: SparelySettings,
        /** The current smart recommendation, used when auto recommendations are enabled. */
        val recommendedPercentages: SavingsPercentages?,
        val paymentMethods: List<PaymentMethod>,
        val smartVaults: List<SmartVault>
    )

    data class Result(
        val expenseId: Long,
        /** Set when the expense used up 90% or more of the vault it was deducted from. */
        val vaultArchivePrompt: VaultArchivePrompt?
    )

    /** @throws IllegalArgumentException if [ExpenseInput.amount] is not a finite positive number. */
    suspend operator fun invoke(input: ExpenseInput, context: Context): Result {
        require(input.amount.isFinite() && input.amount > 0.0) { "Please enter a valid amount" }

        val settings = context.settings
        val recommendedPercentages = when {
            input.manualPercentages != null -> input.manualPercentages
            settings.autoRecommendationsEnabled && context.recommendedPercentages != null ->
                context.recommendedPercentages
            else -> settings.defaultPercentages
        }
        val allocation = SavingsCalculator.calculateAllocation(input, recommendedPercentages, settings.riskLevel)
        val applied = recommendedPercentages.adjustWithinBudget()
        val entity = ExpenseEntity(
            id = input.id ?: 0L,
            description = input.description.trim().ifEmpty { "General purchase" },
            amount = input.amount,
            category = input.category,
            date = input.date,
            includesTax = input.includesTax,
            emergencyAmount = allocation.emergencyAmount,
            investmentAmount = allocation.investmentAmount,
            funAmount = allocation.funAmount,
            safeInvestmentAmount = allocation.safeInvestmentAmount,
            highRiskInvestmentAmount = allocation.highRiskInvestmentAmount,
            autoRecommended = input.manualPercentages == null && settings.autoRecommendationsEnabled,
            appliedPercentEmergency = applied.emergency,
            appliedPercentInvest = applied.invest,
            appliedPercentFun = applied.`fun`,
            appliedSafeSplit = applied.safeInvestmentSplit,
            riskLevelUsed = settings.riskLevel,
            deductedFromVaultId = input.deductFromVaultId,
            storeId = input.storeId,
            paymentMethodId = input.paymentMethodId,
            isRecurring = input.isRecurring,
            notes = input.notes,
            orderNumber = input.orderNumber,
            type = input.type.name
        )

        var insertedExpenseId = 0L
        var archivePrompt: VaultArchivePrompt? = null
        savingsRepository.withMainAccountLock { savingsRepository.runInTransaction {
            insertedExpenseId = savingsRepository.upsertExpense(entity)

            // Save line items if present (filter out items with no name or price)
            val validItems = input.items.filter { it.name.isNotBlank() && it.totalPrice > 0 }
            if (validItems.isNotEmpty()) {
                val itemsWithId = validItems.map { it.copy(expenseId = insertedExpenseId, id = 0L) }
                savingsRepository.insertExpenseItems(itemsWithId)
            }

            // Link assets if specified
            input.assetAllocations.forEach { (assetId, percentageAllocated) ->
                savingsRepository.linkExpenseToAsset(insertedExpenseId, assetId, percentageAllocated)
            }

            // Get current balance once at the start
            var currentBalance = savingsRepository.getLatestMainAccountBalance()

            if (input.deductFromVaultId != null) {
                // Read the vault fresh: a caller's copy can be stale (e.g. right after another deduction).
                val vault = savingsRepository.getSmartVaultById(input.deductFromVaultId)
                if (vault != null) {
                    val vaultBalanceBefore = vault.currentBalance
                    val expenseAmount = input.amount

                    // Calculate how much to deduct from vault and overflow
                    val deductFromVault = expenseAmount.coerceAtMost(vaultBalanceBefore)
                    val overflowToMainAccount = (expenseAmount - vaultBalanceBefore).coerceAtLeast(0.0)
                    val vaultBalanceAfter = (vaultBalanceBefore - deductFromVault).coerceAtLeast(0.0)

                    if (deductFromVault > 0.0) {
                        savingsRepository.recordVaultBalanceAdjustment(
                            vaultId = vault.id,
                            previousBalance = vaultBalanceBefore,
                            newBalance = vaultBalanceAfter,
                            type = VaultAdjustmentType.MANUAL_DEDUCTION,
                            reason = "Expense: ${input.description.take(100)}",
                            relatedExpenseId = insertedExpenseId
                        )
                    }

                    // Deduct overflow from main account if specified
                    if (overflowToMainAccount > 0.0 && input.deductFromMainAccount) {
                        val newBalance = currentBalance - overflowToMainAccount
                        savingsRepository.insertMainAccountTransaction(
                            MainAccountTransaction(
                                type = MainAccountTransactionType.EXPENSE,
                                amount = overflowToMainAccount,
                                balanceAfter = newBalance,
                                timestamp = LocalDateTime.now(),
                                description = "Overflow from ${vault.name} expense: ${input.description.take(70)}",
                                relatedExpenseId = insertedExpenseId
                            )
                        )
                        preferencesRepository.updateMainAccountBalance(newBalance)
                        currentBalance = newBalance
                    }

                    // Prompt to archive when 90% or more of the vault was used
                    val percentageUsed = if (vaultBalanceBefore > 0.0) deductFromVault / vaultBalanceBefore else 0.0
                    if (percentageUsed >= 0.90) {
                        archivePrompt = VaultArchivePrompt(
                            vaultId = vault.id,
                            vaultName = vault.name,
                            expenseAmount = expenseAmount,
                            vaultBalanceBefore = vaultBalanceBefore,
                            vaultBalanceAfter = vaultBalanceAfter,
                            overflowToMainAccount = overflowToMainAccount
                        )
                    }
                }
            } else if (input.deductFromMainAccount) {
                val paymentMethod = input.paymentMethodId?.let { id -> context.paymentMethods.find { it.id == id } }
                // Credit card payments: SavingsRepository updates the card balance; the main
                // account is NOT debited here.
                if (paymentMethod?.isCreditCard != true) {
                    val newBalance = currentBalance - input.amount
                    savingsRepository.insertMainAccountTransaction(
                        MainAccountTransaction(
                            type = MainAccountTransactionType.EXPENSE,
                            amount = input.amount,
                            balanceAfter = newBalance,
                            timestamp = LocalDateTime.now(),
                            description = input.description.take(100),
                            relatedExpenseId = insertedExpenseId
                        )
                    )
                    preferencesRepository.updateMainAccountBalance(newBalance)
                    currentBalance = newBalance
                }
            }

            val savingTaxPlans = SavingTaxEngine.calculate(
                SavingTaxEngine.Context(
                    expenseAmount = input.amount,
                    expenseDate = input.date,
                    settings = settings,
                    vaults = context.smartVaults,
                    currentMainAccountBalance = currentBalance
                )
            )
            if (savingTaxPlans.isNotEmpty()) {
                val contributions = savingTaxPlans.map { plan ->
                    VaultContribution(
                        vaultId = plan.vaultId,
                        amount = plan.amount,
                        date = input.date,
                        source = VaultContributionSource.SAVING_TAX,
                        note = "Saving tax from ${input.description}".take(120),
                        relatedExpenseId = insertedExpenseId
                    )
                }
                val contributionIds = savingsRepository.logVaultContributions(contributions)

                // Deduct total saving tax from main account (using updated balance from expense deduction if applicable)
                val totalSavingTax = savingTaxPlans.sumOf { it.amount }
                if (totalSavingTax > 0.0) {
                    val newBalance = currentBalance - totalSavingTax
                    savingsRepository.insertMainAccountTransaction(
                        MainAccountTransaction(
                            type = MainAccountTransactionType.VAULT_CONTRIBUTION,
                            amount = totalSavingTax,
                            balanceAfter = newBalance,
                            timestamp = LocalDateTime.now(),
                            description = "Saving tax to ${savingTaxPlans.size} vault(s)",
                            relatedVaultContributionIds = contributionIds
                        )
                    )
                    preferencesRepository.updateMainAccountBalance(newBalance)
                }
            }
        } }

        return Result(expenseId = insertedExpenseId, vaultArchivePrompt = archivePrompt)
    }
}
