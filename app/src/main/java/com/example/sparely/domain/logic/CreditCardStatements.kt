package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.PaymentMethod
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * How a credit card bill works: on the statement date (the card's billing day) the bank bills
 * everything charged before that day, and the payment is due [GRACE_DAYS] later. Purchases made on
 * or after the statement date go on the next bill, so they aren't due yet.
 */
object CreditCardStatements {

    /** Days from the statement date to the payment due date (the legal minimum in Canada). */
    const val GRACE_DAYS = 21L

    data class Bill(
        /** When the bill came out. */
        val statementDate: LocalDate,
        val dueDate: LocalDate,
        /** What's still owed on this bill: the balance minus purchases made since it came out. */
        val amountDue: Double,
        /** Charged since the statement date; goes on the next bill. */
        val newCharges: Double,
        val daysUntilDue: Int
    ) {
        val isPaid: Boolean get() = amountDue < 0.005
        val isOverdue: Boolean get() = daysUntilDue < 0 && !isPaid
    }

    /** The statement date on or before [today]. Short months use their last day (billing day 31 in February is the 28th or 29th). */
    fun latestStatementDate(billingDay: Int, today: LocalDate): LocalDate {
        val thisMonth = statementDateIn(YearMonth.from(today), billingDay)
        return if (!thisMonth.isAfter(today)) thisMonth else statementDateIn(YearMonth.from(today).minusMonths(1), billingDay)
    }

    private fun statementDateIn(month: YearMonth, billingDay: Int): LocalDate =
        month.atDay(billingDay.coerceIn(1, month.lengthOfMonth()))

    /**
     * The most recent bill for [card], or null without a billing day. [charges] can be any
     * expenses; only the ones paid with this card on or after the statement date are used.
     */
    fun currentBill(card: PaymentMethod, charges: List<Expense>, today: LocalDate = LocalDate.now()): Bill? {
        val billingDay = card.billingCycleDay ?: return null
        val statementDate = latestStatementDate(billingDay, today)
        val newCharges = charges
            .filter { it.paymentMethodId == card.id && !it.date.isBefore(statementDate) }
            .sumOf { (it.amount - it.refundedAmount).coerceAtLeast(0.0) }
        val dueDate = statementDate.plusDays(GRACE_DAYS)
        return Bill(
            statementDate = statementDate,
            dueDate = dueDate,
            // Payments since the statement already lowered the balance, so they count against this bill.
            amountDue = (card.currentBalance - newCharges).coerceAtLeast(0.0),
            newCharges = newCharges,
            daysUntilDue = ChronoUnit.DAYS.between(today, dueDate).toInt()
        )
    }
}
