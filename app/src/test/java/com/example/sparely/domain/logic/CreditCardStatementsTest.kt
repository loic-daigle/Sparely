package com.example.sparely.domain.logic

import com.example.sparely.domain.model.AllocationBreakdown
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.PaymentMethod
import com.example.sparely.domain.model.PaymentMethodType
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsPercentages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CreditCardStatementsTest {

    private var nextId = 1L

    private fun card(balance: Double, billingDay: Int? = 14) = PaymentMethod(
        id = 7,
        name = "Visa",
        type = PaymentMethodType.CARD,
        defaultDeductFromMainAccount = false,
        isCreditCard = true,
        creditLimit = 2000.0,
        currentBalance = balance,
        billingCycleDay = billingDay
    )

    private fun charge(date: LocalDate, amount: Double, cardId: Long = 7) = Expense(
        id = nextId++,
        description = "Purchase",
        amount = amount,
        category = ExpenseCategory.SHOPPING,
        date = date,
        includesTax = false,
        allocation = AllocationBreakdown(0.0, 0.0, 0.0, 0.0, 0.0),
        appliedPercentages = SavingsPercentages(0.0, 0.0, 0.0),
        autoRecommended = false,
        riskLevelUsed = RiskLevel.BALANCED,
        paymentMethodId = cardId
    )

    @Test
    fun onStatementDayTheBillIsDueIn21DaysNotToday() {
        val today = LocalDate.of(2026, 10, 14)
        val bill = CreditCardStatements.currentBill(card(500.0), emptyList(), today)!!
        assertEquals(today, bill.statementDate)
        assertEquals(LocalDate.of(2026, 11, 4), bill.dueDate)
        assertEquals(21, bill.daysUntilDue)
        assertEquals(500.0, bill.amountDue, 0.001)
    }

    @Test
    fun purchasesSinceTheStatementGoOnTheNextBill() {
        val today = LocalDate.of(2026, 10, 30)
        val charges = listOf(
            charge(LocalDate.of(2026, 10, 13), 100.0), // before the statement: on this bill
            charge(LocalDate.of(2026, 10, 14), 40.0),  // on the statement date: next bill
            charge(LocalDate.of(2026, 10, 29), 60.0),  // yesterday: next bill
            charge(LocalDate.of(2026, 10, 29), 999.0, cardId = 8) // another card
        )
        val bill = CreditCardStatements.currentBill(card(600.0), charges, today)!!
        assertEquals(LocalDate.of(2026, 10, 14), bill.statementDate)
        assertEquals(100.0, bill.newCharges, 0.001)
        assertEquals(500.0, bill.amountDue, 0.001)
        assertEquals(5, bill.daysUntilDue)
    }

    @Test
    fun paymentsLowerWhatsDueAndCanPayItOff() {
        // Balance 80 after paying, with 100 charged since the statement: nothing left on this bill.
        val today = LocalDate.of(2026, 10, 30)
        val bill = CreditCardStatements.currentBill(card(80.0), listOf(charge(LocalDate.of(2026, 10, 20), 100.0)), today)!!
        assertTrue(bill.isPaid)
    }

    @Test
    fun beforeThisMonthsStatementTheLastBillIsLastMonths() {
        val today = LocalDate.of(2026, 10, 5)
        val bill = CreditCardStatements.currentBill(card(300.0), emptyList(), today)!!
        assertEquals(LocalDate.of(2026, 9, 14), bill.statementDate)
        assertEquals(LocalDate.of(2026, 10, 5), bill.dueDate)
        assertEquals(0, bill.daysUntilDue)
    }

    @Test
    fun billingDay31UsesTheLastDayOfShortMonths() {
        assertEquals(
            LocalDate.of(2026, 2, 28),
            CreditCardStatements.latestStatementDate(31, LocalDate.of(2026, 3, 10))
        )
    }

    @Test
    fun noBillingDayNoBill() {
        assertNull(CreditCardStatements.currentBill(card(500.0, billingDay = null), emptyList(), LocalDate.of(2026, 10, 14)))
    }
}
