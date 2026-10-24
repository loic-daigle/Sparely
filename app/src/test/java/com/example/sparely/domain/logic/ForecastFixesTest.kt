package com.example.sparely.domain.logic

import com.example.sparely.domain.model.AllocationBreakdown
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.RecurringFrequency
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsPercentages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Random

class ForecastFixesTest {

    private val today = LocalDate.of(2026, 9, 20)
    private var nextId = 1L

    private fun expense(
        date: LocalDate,
        amount: Double,
        description: String = "e",
        category: ExpenseCategory = ExpenseCategory.SHOPPING,
        recurring: Boolean = false
    ) = Expense(
        id = nextId++,
        description = description,
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

    private fun forecast(expenses: List<Expense>, recurring: List<RecurringExpense> = emptyList()) =
        CashflowEngine.forecast(
            CashflowEngine.ForecastInput(
                currentBalance = 3000.0,
                recentExpenses = expenses,
                recurringExpenses = recurring,
                expectedMonthlyIncome = 0.0,
                today = today
            )
        )

    /** Rent logged by hand on the 1st, plus steady day-to-day spending, for [months] months up to today. */
    private fun handLoggedRentHistory(months: Int, seed: Long = 7): List<Expense> {
        val random = Random(seed)
        val list = mutableListOf<Expense>()
        var day = today.minusMonths(months.toLong()).withDayOfMonth(1)
        while (!day.isAfter(today)) {
            if (day.dayOfMonth == 1) list += expense(day, 1200.0, "Rent", ExpenseCategory.UTILITIES)
            if (random.nextDouble() < 0.6) list += expense(day, 10 + random.nextDouble() * 40, "Groceries", ExpenseCategory.GROCERIES)
            day = day.plusDays(1)
        }
        return RecurringInference.annotate(list, emptyList())
    }

    // --- Burn rate for new users ---

    @Test
    fun `one purchase on the first day is not read as a daily habit`() {
        val burn = forecast(listOf(expense(today, 50.0))).dailyBurnRate
        assertEquals(50.0 / ProjectionMath.MIN_RATE_DAYS, burn, 0.01)
    }

    @Test
    fun `recent rate is spread over the days actually tracked, today included`() {
        val expenses = listOf(expense(today.minusDays(9), 100.0), expense(today, 100.0))
        assertEquals(20.0, ProjectionMath.recentDailyRate(expenses, today), 0.001)
    }

    @Test
    fun `recent rate uses the full window once tracking began before it`() {
        val expenses = listOf(expense(today.minusDays(100), 999.0), expense(today.minusDays(3), 300.0))
        assertEquals(10.0, ProjectionMath.recentDailyRate(expenses, today, windowDays = 30, trackingStart = today.minusDays(100)), 0.001)
    }

    // --- Hand-logged bills ---

    @Test
    fun `hand-logged rent survives the outlier filter`() {
        val filtered = SmartInsightEngine.filterPredictionExpenses(handLoggedRentHistory(4))
        assertTrue(filtered.any { it.description == "Rent" })
    }

    @Test
    fun `hand-logged rent is scheduled as an upcoming essential obligation`() {
        val result = forecast(handLoggedRentHistory(4))
        val rent = result.upcomingObligations.single { it.description == "Rent" }
        assertEquals(LocalDate.of(2026, 10, 1), rent.dueDate)
        assertEquals(1200.0, rent.amount, 0.001)
        assertTrue(result.essentialObligations30Days >= 1200.0)
    }

    @Test
    fun `inferred bill is not added again when an active recurring entry already covers it`() {
        val entry = RecurringExpense(
            description = "Rent", amount = 1200.0, category = ExpenseCategory.UTILITIES,
            frequency = RecurringFrequency.MONTHLY, startDate = LocalDate.of(2026, 1, 1)
        )
        val result = forecast(handLoggedRentHistory(4), listOf(entry))
        assertEquals(1, result.upcomingObligations.count { it.description == "Rent" })
    }

    @Test
    fun `a bill that stopped months ago is not projected`() {
        val stopped = (1..4).map { expense(LocalDate.of(2026, it, 5), 15.0, "Streaming", ExpenseCategory.ENTERTAINMENT) }
        assertTrue(RecurringInference.upcomingInferredBills(stopped, emptyList(), today).isEmpty())
    }

    @Test
    fun `a bill a few days late is still expected today`() {
        val late = (5..8).map { expense(LocalDate.of(2026, it, 15), 15.0, "Streaming", ExpenseCategory.ENTERTAINMENT) }
        val bill = RecurringInference.upcomingInferredBills(late, emptyList(), today).single()
        assertEquals(today, bill.dueDate)
    }

    // --- Spending patterns ---

    @Test
    fun `weekly average counts quiet weeks instead of skipping them`() {
        // Tracking for 8 weeks, but in the last 4 weeks only one week had spending.
        val expenses = listOf(expense(today.minusDays(55), 10.0), expense(today.minusDays(2), 280.0))
        val result = SpendingPatternEngine.analyze(expenses, today = today)
        assertEquals(70.0, result.weeklyAverageExpense, 0.01)
    }

    @Test
    fun `monthly average leaves out the partial month and counts quiet months`() {
        val expenses = listOf(
            expense(LocalDate.of(2026, 6, 10), 300.0),
            // July: nothing
            expense(LocalDate.of(2026, 8, 10), 300.0),
            expense(LocalDate.of(2026, 9, 2), 50.0)
        )
        assertEquals(200.0, SpendingPatternEngine.analyze(expenses, today = today).monthlyAverageExpense, 0.01)
    }

    @Test
    fun `rent does not hide an unusually large grocery run`() {
        val expenses = handLoggedRentHistory(6) + expense(today.minusDays(3), 400.0, "Big shop", ExpenseCategory.GROCERIES)
        val anomalies = SpendingPatternEngine.analyze(expenses, today = today).anomalies
        val bigShop = anomalies.single { it.expense.description == "Big shop" }
        assertTrue(bigShop.timesTypical > 5)
        assertTrue(anomalies.none { it.expense.description == "Rent" })
    }

    @Test
    fun `ordinary purchases in a category of small ones are not anomalies`() {
        // Daily dining: mostly $4-12 coffees and snacks, plus a handful of normal $23-$88 meals out.
        val random = Random(3)
        val small = (1..90).map { expense(today.minusDays(it.toLong()), 4 + random.nextDouble() * 8, "Coffee", ExpenseCategory.DINING) }
        val meals = listOf(88.0, 38.0, 28.0, 27.0, 23.0).mapIndexed { i, amount ->
            expense(today.minusDays(3L + i * 9), amount, "Meal", ExpenseCategory.DINING)
        }
        val anomalies = SpendingPatternEngine.analyze(small + meals, today = today).anomalies
        assertTrue("flagged: ${anomalies.map { it.expense.amount }}", anomalies.isEmpty())
    }

    @Test
    fun `a purchase far above anything normal for the user is still flagged`() {
        val random = Random(3)
        val small = (1..90).map { expense(today.minusDays(it.toLong()), 4 + random.nextDouble() * 8, "Coffee", ExpenseCategory.DINING) }
        val groceries = (1..30).map { expense(today.minusDays(it * 3L), 40 + random.nextDouble() * 40, "Groceries", ExpenseCategory.GROCERIES) }
        val party = expense(today.minusDays(5), 600.0, "Party", ExpenseCategory.DINING)
        val anomalies = SpendingPatternEngine.analyze(small + groceries + party, today = today).anomalies
        assertEquals(listOf("Party"), anomalies.map { it.expense.description })
    }

    @Test
    fun `cheap purchases are never reported as anomalies`() {
        val expenses = handLoggedRentHistory(6) + expense(today.minusDays(3), 0.5, "Gum", ExpenseCategory.GROCERIES)
        assertTrue(SpendingPatternEngine.analyze(expenses, today = today).anomalies.none { it.expense.description == "Gum" })
    }

    @Test
    fun `a bill landing this month is not a spending trend`() {
        val history = handLoggedRentHistory(6)
        val withAnnualBill = history + expense(LocalDate.of(2026, 9, 5), 900.0, "Insurance", ExpenseCategory.OTHER, recurring = true)
        val base = SpendingPatternEngine.analyze(history, today = today)
        val withBill = SpendingPatternEngine.analyze(withAnnualBill, today = today)
        assertEquals(base.trendPercentage, withBill.trendPercentage, 0.001)
        assertEquals(SpendingPatternEngine.SpendingTrend.STABLE, withBill.trend)
    }

    // --- Forecast accuracy ---

    @Test
    fun `accuracy is not measured with under two months of history`() {
        assertNull(forecast(handLoggedRentHistory(1)).accuracy)
    }

    @Test
    fun `steady spending gives a small measured error and the buffer comes from it`() {
        val result = forecast(handLoggedRentHistory(8))
        assertNotNull(result.accuracy)
        val accuracy = result.accuracy!!
        assertEquals(12, accuracy.samples)
        assertTrue("error ${accuracy.typicalErrorShare}", accuracy.typicalErrorShare < 0.15)
        assertEquals(maxOf(accuracy.overspendP80, 50.0), result.safetyBuffer, 0.001)
    }

    @Test
    fun `a forecast that keeps running low is measured as biased low with a bigger buffer`() {
        // Spending doubles every month, so every past forecast undershoots.
        val list = mutableListOf<Expense>()
        var day = today.minusMonths(6).withDayOfMonth(1)
        while (!day.isAfter(today)) {
            val monthsIn = java.time.temporal.ChronoUnit.MONTHS.between(today.minusMonths(6).withDayOfMonth(1), day)
            list += expense(day, 5.0 * (1 shl monthsIn.toInt()))
            day = day.plusDays(1)
        }
        val accuracy = forecast(list).accuracy!!
        assertTrue("bias ${accuracy.bias}", accuracy.bias < -0.2)
        assertTrue(accuracy.overspendP80 > 0)
    }

    @Test
    fun `measure only uses what was known at each past date`() {
        val asOfDates = mutableListOf<LocalDate>()
        ForecastAccuracy.measure(handLoggedRentHistory(8), today) { known, asOf ->
            assertTrue(known.all { !it.date.isAfter(asOf) })
            asOfDates += asOf
            1.0
        }
        assertEquals(today.minusDays(30), asOfDates.first())
    }

    // --- Day-by-day projection (home screen money line) ---

    @Test
    fun `a week of daily projections adds up to that week's projection`() {
        val result = CashflowEngine.forecast(
            CashflowEngine.ForecastInput(
                currentBalance = 3000.0,
                recentExpenses = handLoggedRentHistory(3),
                recurringExpenses = emptyList(),
                expectedMonthlyIncome = 4000.0,
                today = today
            )
        )
        val days = result.dailyProjections
        assertEquals(30, days.size)
        assertEquals(today, days.first().date)
        var balance = result.currentBalance
        result.weeklyProjections.forEachIndexed { week, projection ->
            val slice = days.subList(week * 7, week * 7 + 7)
            assertEquals(projection.projectedIncome, slice.sumOf { it.income }, 0.01)
            assertEquals(projection.projectedSpending, slice.sumOf { it.spending + it.obligations }, 0.01)
            balance += slice.sumOf { it.change }
            assertEquals(projection.projectedEndBalance, balance, 0.01)
        }
    }
}
