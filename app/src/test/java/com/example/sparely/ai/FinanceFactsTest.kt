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
        val facts = FinanceFacts.overview(state, money = money)
        assertEquals(Fact("Spent this month", "$1240 of a $1500 total budget"), facts.first())
        val budgets = facts.filter { it.label.endsWith("budget") }
        assertEquals(Fact("Dining budget", "$310 of $250 (124% used)"), budgets.first())
        assertEquals(Fact("Groceries budget", "$200 of $400 (50% used)"), budgets[1])
    }

    @Test
    fun emptyStateGivesNoFacts() {
        assertTrue(FinanceFacts.overview(SparelyUiState(), money = money).isEmpty())
        assertTrue(FinanceFacts.budgets(SparelyUiState(), money = money).isEmpty())
    }

    @Test
    fun budgetsCountsTheOnesOverTheirLimit() {
        val facts = FinanceFacts.budgets(state, money = money)
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
        assertTrue(Fact("Emergency fund score", "40 out of 100") in facts)
        assertTrue(Fact("Strength", "Budgets are on track") in facts)
        val tips = facts.filter { it.label == "Way to improve" }
        assertEquals("Big (up to +10 points)", tips.first().value)
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
                Fact(
                    "Reflection pause",
                    "3 days left; because it's marked as a want, Sparely suggests waiting a bit before buying to be sure"
                )
            ),
            facts
        )
    }

    @Test
    fun frenchFactsHaveNoEnglish() {
        val euro: (Double) -> String = { PromptLanguage.FRENCH.money(it, "$") }
        val facts = FinanceFacts.overview(state, PromptLanguage.FRENCH, euro)
        assertEquals(Fact("Dépensé ce mois-ci", "1 240,00 $ sur un budget total de 1 500,00 $"), facts.first().normalizeSpaces())
        assertTrue(Fact("Budget restaurants", "310,00 $ sur 250,00 $ (124 % utilisé)") in facts.map { it.normalizeSpaces() })
    }

    @Test
    fun frenchHealthSkipsStrengthsWithoutATranslation() {
        val score = com.example.sparely.domain.model.FinancialHealthScore(
            overallScore = 62, savingsRateScore = 70, emergencyFundScore = 40, budgetAdherenceScore = 80,
            goalProgressScore = 55, debtRatioScore = 65,
            healthLevel = com.example.sparely.domain.model.HealthLevel.FAIR,
            topStrengths = listOf("Strong emergency fund", "Some new English strength"),
            improvementAreas = emptyList()
        )
        val facts = FinanceFacts.health(score, PromptLanguage.FRENCH)
        assertEquals(Fact("Score de santé financière", "62 sur 100 (Moyen)"), facts.first())
        assertEquals(listOf("Solide fonds d'urgence"), facts.filter { it.label == "Point fort" }.map { it.value })
    }

    @Test
    fun frenchMoneyAndDatesAreWrittenTheFrenchWay() {
        assertEquals("1 640,09 $", PromptLanguage.FRENCH.money(1640.09, "$").replace('\u202F', ' ').replace('\u00A0', ' '))
        assertEquals("$1,640.09", PromptLanguage.ENGLISH.money(1640.09, "$"))
        assertEquals("8 novembre 2026", PromptLanguage.FRENCH.longDate(java.time.LocalDate.of(2026, 11, 8)))
        assertEquals("November 8, 2026", PromptLanguage.ENGLISH.longDate(java.time.LocalDate.of(2026, 11, 8)))
        assertEquals(PromptLanguage.FRENCH, PromptLanguage.fromCode("fr-CA"))
        assertEquals(PromptLanguage.ENGLISH, PromptLanguage.fromCode("en"))
    }

    // French number formatting uses non-breaking spaces between thousands.
    private fun Fact.normalizeSpaces() = Fact(label, value.replace('\u202F', ' ').replace('\u00A0', ' '))

    private fun card(name: String, balance: Double, limit: Double?) = com.example.sparely.domain.model.PaymentMethod(
        name = name,
        type = com.example.sparely.domain.model.PaymentMethodType.CARD,
        defaultDeductFromMainAccount = false,
        isCreditCard = true,
        creditLimit = limit,
        currentBalance = balance
    )

    @Test
    fun overviewIncludesWhatIsOwedOnCreditCards() {
        val withCard = state.copy(paymentMethods = listOf(card("Visa", 540.0, 2000.0)))
        val facts = FinanceFacts.overview(withCard, money = money)
        assertTrue(Fact("Owed on credit card Visa", "$540 (27% of the $2000 limit)") in facts)
    }

    @Test
    fun severalCardsGiveATotalThenTheLargest() {
        val cards = listOf(card("Visa", 540.0, 2000.0), card("Amex", 100.0, null), card("Paid off", 0.0, 1000.0))
        val facts = FinanceFacts.creditCardFacts(cards, PromptLanguage.ENGLISH, money)
        assertEquals(
            listOf(
                Fact("Owed on all credit cards", "$640"),
                Fact("Owed on Visa", "$540 (27% of the $2000 limit)"),
                Fact("Owed on Amex", "$100")
            ),
            facts
        )
    }

    @Test
    fun briefPointsOutWhatNeedsAttentionInsteadOfRepeatingTheDashboard() {
        val withCards = state.copy(paymentMethods = listOf(card("Visa", 1100.0, 2000.0)))
        val facts = FinanceFacts.brief(withCards, money = money)
        assertEquals(
            listOf(
                // Problems first: the dining budget is over, and the card is above half its limit.
                Fact("Problem", "Dining budget is over by $60 ($310 of $250)"),
                Fact("Problem", "Visa is at 55% of its limit ($1100 owed); under 30% is healthier")
            ),
            facts
        )
        // Nothing here repeats safe-to-spend or the balance, which the dashboard already shows.
        assertTrue(facts.none { it.value.contains("safe", ignoreCase = true) })
    }

    @Test
    fun briefSaysOnTrackWhenNothingStandsOut() {
        val calm = state.copy(
            budgetSummary = state.budgetSummary!!.copy(
                categoryStatuses = listOf(status(ExpenseCategory.GROCERIES, 200.0, 400.0))
            )
        )
        val facts = FinanceFacts.brief(calm, money = money)
        assertEquals(1, facts.size)
        assertEquals("Good news", facts.single().label)
        assertTrue(facts.single().value.startsWith("Spending is on track: $1240 of $1500 used"))
    }

    @Test
    fun vaultsPutTheGoalAtRiskFirst() {
        val today = java.time.LocalDate.of(2026, 10, 14)
        val trip = com.example.sparely.domain.model.SmartVault(
            id = 1, name = "Trip", targetAmount = 2000.0, currentBalance = 800.0,
            targetDate = java.time.LocalDate.of(2027, 4, 14)
        )
        val car = com.example.sparely.domain.model.SmartVault(id = 2, name = "Car", targetAmount = 5000.0, currentBalance = 1000.0)
        val atRisk = com.example.sparely.domain.model.VaultProjection(
            vaultId = 2, projectedCompletionDate = null, monthlyContributionNeeded = 0.0,
            riskFlag = com.example.sparely.domain.model.VaultRiskFlag(com.example.sparely.domain.model.VaultRiskType.TARGET_AT_RISK, "")
        )
        val facts = FinanceFacts.vaults(listOf(trip, car), listOf(atRisk), monthlyIncome = 4000.0, money = money, today = today)
        assertEquals(Fact("Saved across all goals", "$1800 of $7000 (26%) in 2 goals"), facts.first())
        val goals = facts.filter { it.label.startsWith("Goal ") }
        assertEquals("Goal Car", goals.first().label)
        assertTrue(goals.first().value.endsWith("target at risk"))
        assertTrue(goals[1].value.contains("a month"))
    }

    @Test
    fun recurringWorksOutTheMonthlyCost() {
        fun recurring(name: String, amount: Double, frequency: com.example.sparely.domain.model.RecurringFrequency, category: ExpenseCategory) =
            com.example.sparely.domain.model.RecurringExpense(
                description = name, amount = amount, category = category, frequency = frequency,
                startDate = java.time.LocalDate.of(2026, 1, 1)
            )
        val rent = recurring("Rent", 1200.0, com.example.sparely.domain.model.RecurringFrequency.MONTHLY, ExpenseCategory.UTILITIES)
        val netflix = recurring("Netflix", 20.0, com.example.sparely.domain.model.RecurringFrequency.MONTHLY, ExpenseCategory.ENTERTAINMENT)
        val insurance = recurring("Insurance", 600.0, com.example.sparely.domain.model.RecurringFrequency.YEARLY, ExpenseCategory.OTHER)
        val facts = FinanceFacts.recurring(listOf(rent, netflix, insurance), emptyList(), monthlyIncome = 0.0, money = money)
        assertEquals(Fact("Recurring payments", "3 active, about $1270 a month ($15240 a year)"), facts.first())
        assertEquals("Rent", facts[1].label)
        assertTrue(Fact("Entertainment subscriptions", "about $20 a month") in facts)
    }
}
