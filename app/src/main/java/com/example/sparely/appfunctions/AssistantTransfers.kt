package com.example.sparely.appfunctions

import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.SparelySettings
import java.util.Locale
import java.util.UUID

/**
 * A money movement an assistant proposed. It does nothing until the user confirms it on
 * Sparely's confirmation screen, and can be carried out at most once, within
 * [AssistantTransferPlanner.REQUEST_LIFETIME_MILLIS].
 */
data class AssistantMoneyRequest(
    val kind: Kind,
    /** The vault id, or the expense id for a refund. */
    val targetId: Long,
    val amount: Double,
    val reason: String?,
    val requestId: String,
    val createdAtEpochMillis: Long
) {
    enum class Kind { VAULT_DEPOSIT, VAULT_WITHDRAWAL, REFUND }
}

/** A validated request, described against the user's current data. */
data class PlannedTransfer(
    val request: AssistantMoneyRequest,
    val currencyCode: String,
    /** The vault name, or the refunded expense's description. */
    val targetName: String,
    /**
     * Whether the main account moves too: a deposit taken from it, a withdrawal or a refund
     * credited to it. Follows the vault's own defaults, and for refunds whether the expense was
     * paid by credit card.
     */
    val affectsMainAccount: Boolean,
    /** English description for the assistant to relay before the user confirms. */
    val summary: String
)

/** An expense as far as refunding it is concerned. */
data class RefundableExpense(
    val id: Long,
    val description: String,
    val amount: Double,
    val refundedAmount: Double,
    val paidByCreditCard: Boolean
)

/** What planning a transfer needs from the app. Kept narrow so it can be faked in tests. */
interface AssistantTransferGateway {
    suspend fun settings(): SparelySettings
    suspend fun vault(id: Long): SmartVault?
    suspend fun refundableExpense(id: Long): RefundableExpense?
}

/**
 * Validates money movements an assistant proposes and describes them. Used when the assistant
 * asks (to build the confirmation request) and again on the confirmation screen and right
 * before executing, so a request is always checked against the current data.
 */
class AssistantTransferPlanner(
    private val gateway: AssistantTransferGateway,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val newRequestId: () -> String = { UUID.randomUUID().toString() }
) {

    suspend fun planVaultDeposit(vaultId: Long, amount: Double, reason: String?): PlannedTransfer =
        describe(newRequest(AssistantMoneyRequest.Kind.VAULT_DEPOSIT, vaultId, amount, reason))

    suspend fun planVaultWithdrawal(vaultId: Long, amount: Double, reason: String?): PlannedTransfer =
        describe(newRequest(AssistantMoneyRequest.Kind.VAULT_WITHDRAWAL, vaultId, amount, reason))

    /** [amount] null refunds whatever has not been refunded yet. */
    suspend fun planRefund(expenseId: Long, amount: Double?, reason: String?): PlannedTransfer {
        val requested = amount ?: run {
            requireAccess()
            val expense = gateway.refundableExpense(expenseId) ?: throw expenseNotFound(expenseId)
            (expense.amount - expense.refundedAmount).also {
                if (it <= 0.005) {
                    throw AssistantInvalidArgumentException("The expense \"${expense.description}\" is already fully refunded.")
                }
            }
        }
        return describe(newRequest(AssistantMoneyRequest.Kind.REFUND, expenseId, requested, reason))
    }

    /**
     * Checks [request] is still allowed and possible right now and describes it.
     * @throws AssistantAccessDisabledException if the user has since turned assistant writes off.
     * @throws AssistantInvalidArgumentException if it expired or no longer fits the user's data.
     */
    suspend fun describe(request: AssistantMoneyRequest): PlannedTransfer {
        val settings = requireAccess()
        val currency = settings.regionalSettings.currencyCode
        if (nowEpochMillis() - request.createdAtEpochMillis > REQUEST_LIFETIME_MILLIS) {
            throw AssistantInvalidArgumentException("This request expired. Ask the assistant again.")
        }
        val amount = request.amount
        if (!amount.isFinite() || amount <= 0.0) {
            throw AssistantInvalidArgumentException("amount must be a positive number")
        }
        if (amount > SparelyAssistantActions.MAX_AMOUNT) {
            throw AssistantInvalidArgumentException(
                "amount must be at most ${SparelyAssistantActions.MAX_AMOUNT}. Larger amounts must be moved in the Sparely app."
            )
        }
        val money = "${format(amount)} $currency"

        return when (request.kind) {
            AssistantMoneyRequest.Kind.VAULT_DEPOSIT -> {
                val vault = activeVault(request.targetId)
                val fromMain = vault.defaultManualDepositDeductFromMain
                PlannedTransfer(
                    request, currency, vault.name, fromMain,
                    summary = if (fromMain) {
                        "Move $money from the main account into the vault \"${vault.name}\"."
                    } else {
                        "Add $money to the vault \"${vault.name}\" (the main account is not changed)."
                    }
                )
            }
            AssistantMoneyRequest.Kind.VAULT_WITHDRAWAL -> {
                val vault = activeVault(request.targetId)
                if (amount > vault.currentBalance + 0.005) {
                    throw AssistantInvalidArgumentException(
                        "The vault \"${vault.name}\" only holds ${format(vault.currentBalance)} $currency."
                    )
                }
                val toMain = vault.defaultManualWithdrawalCreditMain
                PlannedTransfer(
                    request, currency, vault.name, toMain,
                    summary = if (toMain) {
                        "Move $money from the vault \"${vault.name}\" back to the main account."
                    } else {
                        "Take $money out of the vault \"${vault.name}\" (the main account is not changed)."
                    }
                )
            }
            AssistantMoneyRequest.Kind.REFUND -> {
                val expense = gateway.refundableExpense(request.targetId) ?: throw expenseNotFound(request.targetId)
                val refundable = expense.amount - expense.refundedAmount
                if (refundable <= 0.005) {
                    throw AssistantInvalidArgumentException("The expense \"${expense.description}\" is already fully refunded.")
                }
                if (amount > refundable + 0.005) {
                    throw AssistantInvalidArgumentException(
                        "At most ${format(refundable)} $currency of \"${expense.description}\" can still be refunded."
                    )
                }
                val toMain = !expense.paidByCreditCard
                PlannedTransfer(
                    request, currency, expense.description, toMain,
                    summary = "Record a refund of $money for \"${expense.description}\"" +
                        if (toMain) ", credited to the main account." else " (paid by credit card)."
                )
            }
        }
    }

    private suspend fun requireAccess(): SparelySettings {
        val settings = gateway.settings()
        if (!settings.aiAssistantAccessEnabled) {
            throw AssistantAccessDisabledException(SparelyAssistantQueries.ACCESS_DISABLED_MESSAGE)
        }
        if (!settings.aiAssistantWriteEnabled) {
            throw AssistantAccessDisabledException(SparelyAssistantActions.WRITE_DISABLED_MESSAGE)
        }
        return settings
    }

    private suspend fun activeVault(id: Long): SmartVault {
        val vault = gateway.vault(id)
        if (vault == null || vault.archived) {
            throw AssistantInvalidArgumentException("No active vault has id $id. Call listVaults to find the right id.")
        }
        return vault
    }

    private fun expenseNotFound(id: Long) =
        AssistantInvalidArgumentException("No expense has id $id. Call searchExpenses to find the right id.")

    private fun newRequest(kind: AssistantMoneyRequest.Kind, targetId: Long, amount: Double, reason: String?) =
        AssistantMoneyRequest(
            kind = kind,
            targetId = targetId,
            amount = amount,
            reason = reason?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_REASON_LENGTH),
            requestId = newRequestId(),
            createdAtEpochMillis = nowEpochMillis()
        )

    private fun format(amount: Double) = String.format(Locale.ROOT, "%.2f", amount)

    companion object {
        const val REQUEST_LIFETIME_MILLIS = 30 * 60 * 1000L
        const val MAX_REASON_LENGTH = 100
    }
}
