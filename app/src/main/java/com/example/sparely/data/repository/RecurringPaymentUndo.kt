package com.example.sparely.data.repository

import com.example.sparely.data.local.FrozenFundEntity
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What marking a recurring payment as paid changed, so it can be taken back: the schedule as it
 * was before, the amount that was frozen for the bill, and the expense / paid record it created.
 */
data class RecurringPaymentUndo(
    val recurringExpenseId: Long,
    val previousNextRunAt: LocalDateTime?,
    val previousLastProcessedDate: LocalDate?,
    // The next run the payment scheduled. If the schedule no longer matches it, something else
    // (the worker, an edit) has moved it since and the undo must not rewind it.
    val scheduledNextRunAt: LocalDateTime?,
    val releasedFrozenFunds: List<FrozenFundEntity>,
    val expenseId: Long?,
    val paidRecordId: Long?,
    // A variable bill's "confirm the amount" entry the payment settled
    val clearedPendingVariable: com.example.sparely.data.local.PendingVariableRecurringExpenseEntity? = null
)
