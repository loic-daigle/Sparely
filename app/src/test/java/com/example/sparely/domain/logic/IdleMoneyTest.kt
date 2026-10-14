package com.example.sparely.domain.logic

import com.example.sparely.domain.model.AllocationBreakdown
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsPercentages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class IdleMoneyTest {

    private val today = LocalDate.of(2026, 10, 14)
    private var nextId = 1L

    private fun expense(date: LocalDate, amount: Double) = Expense(
        id = nextId++,
        description = "Groceries",
        amount = amount,
        category = ExpenseCategory.GROCERIES,
        date = date,
        includesTax = false,
        allocation = AllocationBreakdown(0.0, 0.0, 0.0, 0.0, 0.0),
        appliedPercentages = SavingsPercentages(0.0, 0.0, 0.0),
        autoRecommended = false,
        riskLevelUsed = RiskLevel.BALANCED
    )

    /** $1,000 spent in each of July, August and September, in four even purchases. */
    private val history = listOf(7, 8, 9).flatMap { month ->
        listOf(3, 10, 17, 24).map { day -> expense(LocalDate.of(2026, month, day), 250.0) }
    }

    @Test
    fun reserveUsesCompleteMonthsOnly() {
        // A half month of October spending must not drag the average down.
        val withPartialMonth = history + expense(LocalDate.of(2026, 10, 2), 100.0)
        val insight = SmartInsightEngine.analyzeIdleMoney(
            currentBalance = 3000.0, expenses = withPartialMonth, vaults = emptyList(), today = today
        )!!
        assertEquals(1000.0, insight.projectedMonthlyExpenses, 0.01)
        assertEquals(1500.0, insight.recommendedReserve, 0.01)
        assertEquals(1500.0, insight.excessAmount, 0.01)
    }

    @Test
    fun committedMoneyIsNotIdle() {
        val insight = SmartInsightEngine.analyzeIdleMoney(
            currentBalance = 3000.0, expenses = history, vaults = emptyList(), today = today,
            committedMoney = 1100.0 // e.g. a credit card balance
        )!!
        assertEquals(400.0, insight.excessAmount, 0.01)
        assertEquals(400.0, insight.suggestedTransferAmount, 0.01)
    }

    @Test
    fun upcomingBillsRaiseTheReserve() {
        val insight = SmartInsightEngine.analyzeIdleMoney(
            currentBalance = 3000.0, expenses = history, vaults = emptyList(), today = today,
            upcomingBills30Days = 2200.0
        )!!
        assertEquals(2200.0, insight.recommendedReserve, 0.01)
        assertEquals(800.0, insight.excessAmount, 0.01)
    }

    @Test
    fun nothingIsIdleWhenCommitmentsEatTheBalance() {
        assertNull(
            SmartInsightEngine.analyzeIdleMoney(
                currentBalance = 3000.0, expenses = history, vaults = emptyList(), today = today,
                committedMoney = 1600.0
            )
        )
    }

    @Test
    fun needsAtLeastOneCompleteMonthOfHistory() {
        val onlyThisMonth = listOf(expense(LocalDate.of(2026, 10, 2), 100.0))
        assertNull(
            SmartInsightEngine.analyzeIdleMoney(
                currentBalance = 3000.0, expenses = onlyThisMonth, vaults = emptyList(), today = today
            )
        )
    }
}
