package com.example.sparely.domain.model

import java.time.LocalDateTime

/**
 * A variable-amount bill that came due and is waiting for the user to confirm what it actually cost
 * before it's recorded (see VaultAutoDepositWorker and the "Bills to confirm" card).
 */
data class PendingVariableBill(
    val recurringExpense: RecurringExpense,
    val predictedAmount: Double,
    val waitingSince: LocalDateTime
)
