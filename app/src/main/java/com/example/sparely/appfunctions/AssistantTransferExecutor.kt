package com.example.sparely.appfunctions

import android.content.Context
import android.content.Intent
import com.example.sparely.AppContainer
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.SparelySettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RepositoryAssistantTransferGateway(private val container: AppContainer) : AssistantTransferGateway {

    override suspend fun settings(): SparelySettings = container.preferencesRepository.getSettingsSnapshot()

    override suspend fun vault(id: Long): SmartVault? = container.savingsRepository.getSmartVaultById(id)

    override suspend fun refundableExpense(id: Long): RefundableExpense? {
        val repository = container.savingsRepository
        val expense = repository.findExpenseById(id) ?: return null
        val paymentMethod = expense.paymentMethodId?.let { repository.getPaymentMethodById(it) }
        return RefundableExpense(
            id = expense.id,
            description = expense.description,
            amount = expense.amount,
            refundedAmount = expense.refundedAmount,
            paidByCreditCard = paymentMethod?.isCreditCard == true
        )
    }
}

/**
 * Carries out a money movement the user confirmed, through the same repository calls as the
 * app's own vault and refund screens. Re-validates the request first and refuses to run the same
 * request twice (a confirmation link could be opened again).
 */
class AssistantTransferExecutor(
    context: Context,
    private val container: AppContainer,
    private val planner: AssistantTransferPlanner = AssistantTransferPlanner(RepositoryAssistantTransferGateway(container))
) {
    private val executedRequests = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * @throws AssistantAccessDisabledException if assistant writes were turned off since.
     * @throws AssistantInvalidArgumentException if the request expired, already ran or no longer fits.
     */
    suspend fun execute(request: AssistantMoneyRequest): Unit = executionLock.withLock {
        val done = executedRequests.getStringSet(KEY_EXECUTED, emptySet()).orEmpty()
        if (request.requestId in done) {
            throw AssistantInvalidArgumentException("This request was already carried out.")
        }
        val plan = planner.describe(request)
        val repository = container.savingsRepository
        val reason = request.reason ?: "Requested via AI assistant"
        when (request.kind) {
            AssistantMoneyRequest.Kind.VAULT_DEPOSIT ->
                repository.depositToVault(request.targetId, request.amount, reason, adjustMainAccount = plan.affectsMainAccount)
            AssistantMoneyRequest.Kind.VAULT_WITHDRAWAL ->
                repository.deductFromVault(request.targetId, request.amount, reason, creditMainAccount = plan.affectsMainAccount)
            AssistantMoneyRequest.Kind.REFUND ->
                repository.processExpenseRefund(
                    expenseId = request.targetId,
                    requestedAmount = request.amount,
                    reason = reason
                ) ?: throw AssistantInvalidArgumentException("The refund could not be recorded.")
        }
        // Keep the most recent ids only; requests expire long before 200 more are made.
        val updated = (done.toList() + request.requestId).takeLast(MAX_REMEMBERED_REQUESTS).toSet()
        executedRequests.edit().putStringSet(KEY_EXECUTED, updated).commit()
        Unit
    }

    companion object {
        private const val PREFS_NAME = "assistant_transfers"
        private const val KEY_EXECUTED = "executed_request_ids"
        private const val MAX_REMEMBERED_REQUESTS = 200
        private val executionLock = Mutex()
    }
}

/** Encodes a request into the confirmation screen's intent and back. */
object AssistantMoneyRequestIntents {
    private const val EXTRA_KIND = "assistant_request_kind"
    private const val EXTRA_TARGET_ID = "assistant_request_target_id"
    private const val EXTRA_AMOUNT = "assistant_request_amount"
    private const val EXTRA_REASON = "assistant_request_reason"
    private const val EXTRA_REQUEST_ID = "assistant_request_id"
    private const val EXTRA_CREATED_AT = "assistant_request_created_at"

    fun putInto(intent: Intent, request: AssistantMoneyRequest): Intent = intent.apply {
        putExtra(EXTRA_KIND, request.kind.name)
        putExtra(EXTRA_TARGET_ID, request.targetId)
        putExtra(EXTRA_AMOUNT, request.amount)
        putExtra(EXTRA_REASON, request.reason)
        putExtra(EXTRA_REQUEST_ID, request.requestId)
        putExtra(EXTRA_CREATED_AT, request.createdAtEpochMillis)
    }

    fun readFrom(intent: Intent): AssistantMoneyRequest? {
        val kind = intent.getStringExtra(EXTRA_KIND)
            ?.let { name -> AssistantMoneyRequest.Kind.entries.firstOrNull { it.name == name } } ?: return null
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return null
        return AssistantMoneyRequest(
            kind = kind,
            targetId = intent.getLongExtra(EXTRA_TARGET_ID, -1L).takeIf { it >= 0 } ?: return null,
            amount = intent.getDoubleExtra(EXTRA_AMOUNT, Double.NaN),
            reason = intent.getStringExtra(EXTRA_REASON),
            requestId = requestId,
            createdAtEpochMillis = intent.getLongExtra(EXTRA_CREATED_AT, 0L)
        )
    }
}
