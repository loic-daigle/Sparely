package com.example.sparely.ai

import com.example.sparely.domain.model.BudgetHealthStatus
import com.example.sparely.domain.model.BudgetStatus
import com.example.sparely.domain.model.BudgetSummary
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.ui.state.SparelyUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class FinanceFactsTest {

    private val money: (Double) -> String = { "$" + it.toInt() }

    private fun status(category: ExpenseCategory, spent: Double, limit: Double) = BudgetStatus(
        category = category,
        limit = limit,
        spent = spent,
        remaining = limit - spent,
        percentageUsed = spent / limit,
        status = BudgetHealthStatus.HEALTHY,
        yearMonth = YearMonth.now()
    )

    private val state = SparelyUiState(
        budgetSummary = BudgetSummary(
            totalBudget = 1500.0,
            totalSpent = 1240.0,
            totalRemaining = 260.0,
            categoryStatuses = listOf(
                status(ExpenseCategory.GROCERIES, 200.0, 400.0),
                status(ExpenseCategory.DINING, 310.0, 250.0)
            ),
            overallHealth = BudgetHealthStatus.WARNING
        )
    )

    @Test
    fun overviewListsTheMonthAndTheTightestBudgetFirst() {
        val facts = FinanceFacts.overview(state, money)
        assertEquals(Fact("Spent this month", "$1240 of a $1500 total budget"), facts.first())
        val budgets = facts.filter { it.label.endsWith("budget") }
        assertEquals(Fact("Dining budget", "$310 of $250 (124% used)"), budgets.first())
        assertEquals(Fact("Groceries budget", "$200 of $400 (50% used)"), budgets[1])
    }

    @Test
    fun emptyStateGivesNoFacts() {
        assertTrue(FinanceFacts.overview(SparelyUiState(), money).isEmpty())
        assertTrue(FinanceFacts.budgets(SparelyUiState(), money).isEmpty())
    }

    @Test
    fun budgetsCountsTheOnesOverTheirLimit() {
        val facts = FinanceFacts.budgets(state, money)
        assertTrue(Fact("Budgets over their limit", "1") in facts)
        assertTrue(Fact("All budgets this month", "$1240 spent of $1500") in facts)
    }

    @Test
    fun healthListsScoreBreakdownAndTheBiggestTipsFirst() {
        val score = com.example.sparely.domain.model.FinancialHealthScore(
            overallScore = 62,
            savingsRateScore = 70,
            emergencyFundScore = 40,
            budgetAdherenceScore = 80,
            goalProgressScore = 55,
            debtRatioScore = 65,
            healthLevel = com.example.sparely.domain.model.HealthLevel.FAIR,
            topStrengths = listOf("Budgets are on track"),
            improvementAreas = listOf(
                com.example.sparely.domain.model.ImprovementTip("Small", "", com.example.sparely.domain.model.Priority.values().last(), 2, "Do a little"),
                com.example.sparely.domain.model.ImprovementTip("Big", "", com.example.sparely.domain.model.Priority.values().first(), 10, "Build the emergency fund")
            )
        )
        val facts = FinanceFacts.health(score)
        assertEquals(Fact("Financial health score", "62 out of 100 (Fair)"), facts.first())
        assertTrue(Fact("Emergency Fund score", "40 out of 100") in facts)
        assertTrue(Fact("Strength", "Budgets are on track") in facts)
        val tips = facts.filter { it.label == "Way to improve" }
        assertEquals("Big: Build the emergency fund (up to +10 points)", tips.first().value)
    }

    @Test
    fun wishIncludesProgressAndSkipsTheCheckWhenItCouldNotRun() {
        val facts = FinanceFacts.wish(
            name = "New bike",
            price = 800.0,
            setAside = 200.0,
            targetDate = null,
            reallyNeeded = false,
            cooldownDaysLeft = 3,
            result = null,
            money = money
        )
        assertEquals(
            listOf(
                Fact("Wishlist item", "New bike"),
                Fact("Already saved for it", "$200 (of the $800 price)"),
                Fact("The user says it is", "a want, not a need"),
                Fact("Days left to sleep on it", "3")
            ),
            facts
        )
    }
}
