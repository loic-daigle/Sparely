package com.example.sparely.ui.screens

import com.example.sparely.domain.model.AllocationBreakdown
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsPercentages
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetMathTest {

    private var nextId = 1L

    private fun expense(date: LocalDate, amount: Double, category: ExpenseCategory = ExpenseCategory.DINING, refunded: Double = 0.0) = Expense(
        id = nextId++,
        description = "e",
        amount = amount,
        category = category,
        date = date,
        includesTax = false,
        allocation = AllocationBreakdown(0.0, 0.0, 0.0, 0.0, 0.0),
        appliedPercentages = SavingsPercentages(0.0, 0.0, 0.0),
        autoRecommended = false,
        riskLevelUsed = RiskLevel.BALANCED,
        refundedAmount = refunded
    )

    @Test
    fun paceIsTheShareOfTheMonthGone() {
        assertEquals(0.5, BudgetMath.pace(LocalDate.of(2026, 11, 15)), 1e-9)
        assertEquals(1.0, BudgetMath.pace(LocalDate.of(2026, 10, 31)), 1e-9)
    }

    @Test
    fun daysLeftCountsToday() {
        assertEquals(1, BudgetMath.daysLeft(LocalDate.of(2026, 10, 31)))
        assertEquals(8, BudgetMath.daysLeft(LocalDate.of(2026, 10, 24)))
    }

    @Test
    fun projectionKeepsThePaceSoFar() {
        // 150 $ in 15 days of a 30-day month → 300 $
        assertEquals(300.0, BudgetMath.projection(150.0, LocalDate.of(2026, 11, 15)), 1e-9)
    }

    @Test
    fun groups() {
        assertEquals(BudgetMath.Group.OVER, BudgetMath.group(spent = 412.0, limit = 350.0, projected = 520.0))
        // Close to the limit
        assertEquals(BudgetMath.Group.WATCH, BudgetMath.group(spent = 520.0, limit = 600.0, projected = 590.0))
        // Far from it now, but on its way past it
        assertEquals(BudgetMath.Group.WATCH, BudgetMath.group(spent = 100.0, limit = 250.0, projected = 300.0))
        assertEquals(BudgetMath.Group.OK, BudgetMath.group(spent = 96.0, limit = 200.0, projected = 125.0))
    }

    @Test
    fun historyIsOldestFirstPerCategoryAndNetOfRefunds() {
        val month = YearMonth.of(2026, 10)
        val expenses = listOf(
            expense(LocalDate.of(2026, 10, 3), 40.0),
            expense(LocalDate.of(2026, 10, 9), 60.0, refunded = 10.0),
            expense(LocalDate.of(2026, 9, 12), 80.0),
            expense(LocalDate.of(2026, 5, 1), 25.0),
            // Other category and too old: ignored
            expense(LocalDate.of(2026, 10, 4), 500.0, category = ExpenseCategory.GROCERIES),
            expense(LocalDate.of(2026, 4, 30), 999.0)
        )
        assertEquals(listOf(25.0, 0.0, 0.0, 0.0, 80.0, 90.0), BudgetMath.history(expenses, ExpenseCategory.DINING, month))
    }
}
