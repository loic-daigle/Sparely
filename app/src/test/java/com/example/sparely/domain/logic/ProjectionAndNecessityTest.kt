package com.example.sparely.domain.logic

import com.example.sparely.domain.model.AllocationBreakdown
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.Necessity
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.RecurringFrequency
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsPercentages
import com.example.sparely.domain.model.SuggestionConfidence
import com.example.sparely.domain.model.defaultNecessity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProjectionAndNecessityTest {

    private val today = LocalDate.of(2026, 9, 20)
    private var nextId = 1L

    private fun expense(
        date: LocalDate,
        amount: Double,
        recurring: Boolean = false,
        category: ExpenseCategory = ExpenseCategory.SHOPPING
    ) = Expense(
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
        isRecurring = recurring
    )

    /** Jun-Aug: 1000 recurring on the 1st plus 300 discretionary; Sep: 1000 recurring plus 100 discretionary so far. */
    private fun threeMonthsOfHistory(): List<Expense> {
        val list = mutableListOf<Expense>()
        for (month in 6..8) {
            list += expense(LocalDate.of(2026, month, 1), 1000.0, recurring = true)
            list += expense(LocalDate.of(2026, month, 10), 300.0)
        }
        list += expense(LocalDate.of(2026, 9, 1), 1000.0, recurring = true)
        list += expense(LocalDate.of(2026, 9, 10), 100.0)
        return list
    }

    @Test
    fun `month end projection uses history for the remaining days and skips already paid recurring`() {
        val projection = ProjectionMath.monthEndProjection(threeMonthsOfHistory(), today)

        assertTrue(projection.usedHistory)
        // 1100 spent + 0 recurring left + ~9.7/day discretionary * 10 remaining days
        assertEquals(1197.0, projection.expected, 8.0)
        assertTrue(projection.low <= projection.expected && projection.expected <= projection.high)
        assertEquals(SuggestionConfidence.MEDIUM, projection.confidence)
    }

    @Test
    fun `month end projection adds recurring bills not yet paid this month`() {
        val withoutRent = threeMonthsOfHistory().filterNot { it.date == LocalDate.of(2026, 9, 1) }
        val projection = ProjectionMath.monthEndProjection(withoutRent, today)

        // 100 spent + 1000 recurring still expected + ~97 discretionary
        assertEquals(1197.0, projection.expected, 8.0)
    }

    @Test
    fun `month end projection falls back to linear with under two months of history`() {
        val expenses = listOf(
            expense(LocalDate.of(2026, 8, 5), 200.0),
            expense(LocalDate.of(2026, 9, 10), 100.0)
        )
        val projection = ProjectionMath.monthEndProjection(expenses, today)

        assertFalse(projection.usedHistory)
        assertEquals(ProjectionMath.linearMonthEndProjection(100.0, 20, 30), projection.expected, 0.001)
    }

    @Test
    fun `burn rate ignores recurring expenses so bills are not double counted`() {
        val forecast = CashflowEngine.forecast(
            CashflowEngine.ForecastInput(
                currentBalance = 2000.0,
                recentExpenses = threeMonthsOfHistory(),
                recurringExpenses = emptyList(),
                expectedMonthlyIncome = 0.0,
                today = today
            )
        )

        assertEquals(9.7, forecast.dailyBurnRate, 0.3)
    }

    private fun bill(category: ExpenseCategory, override: Necessity? = null) = RecurringExpense(
        description = "bill",
        amount = 800.0,
        category = category,
        frequency = RecurringFrequency.WEEKLY,
        startDate = today.minusMonths(3),
        lastProcessedDate = today.minusDays(2), // next due in 5 days
        necessityOverride = override
    )

    @Test
    fun `necessity defaults from category and honors override`() {
        assertEquals(Necessity.ESSENTIAL, ExpenseCategory.UTILITIES.defaultNecessity())
        assertEquals(Necessity.DISCRETIONARY, ExpenseCategory.ENTERTAINMENT.defaultNecessity())
        assertEquals(Necessity.ESSENTIAL, bill(ExpenseCategory.ENTERTAINMENT, Necessity.ESSENTIAL).necessity)
    }

    @Test
    fun `essentials uncovered day is reported when an essential bill exceeds available money`() {
        val forecast = CashflowEngine.forecast(
            CashflowEngine.ForecastInput(
                currentBalance = 500.0,
                recentExpenses = emptyList(),
                recurringExpenses = listOf(bill(ExpenseCategory.UTILITIES)),
                expectedMonthlyIncome = 0.0,
                today = today
            )
        )

        assertEquals(5, forecast.essentialsCoveredDays)
        assertEquals(800.0 * forecast.upcomingObligations.count { it.necessity == Necessity.ESSENTIAL },
            forecast.essentialObligations30Days, 0.001)
    }

    @Test
    fun `discretionary bills never count against essentials coverage`() {
        val forecast = CashflowEngine.forecast(
            CashflowEngine.ForecastInput(
                currentBalance = 500.0,
                recentExpenses = emptyList(),
                recurringExpenses = listOf(bill(ExpenseCategory.ENTERTAINMENT)),
                expectedMonthlyIncome = 0.0,
                today = today
            )
        )

        assertNull(forecast.essentialsCoveredDays)
        assertEquals(0.0, forecast.essentialObligations30Days, 0.001)
    }
}
