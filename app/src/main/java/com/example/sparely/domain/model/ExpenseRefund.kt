package com.example.sparely.domain.model

import java.time.LocalDate

data class ExpenseRefund(
    val id: Long = 0L,
    val expenseId: Long,
    val refundedAmount: Double,
    val refundDate: LocalDate,
    val refundMethod: String? = null,
    val reason: String? = null,
    val refundedItemIds: List<Long> = emptyList()
)

enum class RefundMethod {
    ORIGINAL_PAYMENT,
    STORE_CREDIT,
    CASH,
    OTHER
}
