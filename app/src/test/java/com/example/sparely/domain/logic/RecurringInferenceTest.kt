package com.example.sparely.domain.logic

import com.example.sparely.domain.model.AllocationBreakdown
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.RecurringFrequency
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsPercentages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringInferenceTest {

    private var nextId = 1L

    private fun expense(
        description: String,
        date: LocalDate,
        amount: Double,
        recurring: Boolean = false,
        storeId: Long? = null,
        category: ExpenseCategory = ExpenseCategory.OTHER
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
        isRecurring = recurring,
        storeId = storeId
    )

    private fun monthly(description: String, amount: Double, months: IntRange = 1..5) =
        months.map { expense(description, LocalDate.of(2026, it, 1), amount) }

    private fun flagged(result: List<Expense>) = result.filter { it.looksRecurring }

    @Test
    fun `steady monthly bill logged by hand is detected`() {
        val result = RecurringInference.annotate(monthly("Rent", 1200.0), emptyList())
        assertEquals(5, flagged(result).size)
        assertTrue(result.all { it.countsAsRecurring })
    }

    @Test
    fun `variable monthly utility bill is detected`() {
        val amounts = listOf(80.0, 110.0, 65.0, 95.0, 120.0)
        val expenses = amounts.mapIndexed { i, a -> expense("Hydro", LocalDate.of(2026, i + 1, 3), a) }
        assertEquals(5, flagged(RecurringInference.annotate(expenses, emptyList())).size)
    }

    @Test
    fun `daily habit is not treated as recurring`() {
        val expenses = (1..20).map { expense("Coffee", LocalDate.of(2026, 9, it), 4.5) }
        assertTrue(flagged(RecurringInference.annotate(expenses, emptyList())).isEmpty())
    }

    @Test
    fun `irregular repeat purchases with varying amounts are not recurring`() {
        val dates = listOf(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 20), LocalDate.of(2026, 2, 25), LocalDate.of(2026, 3, 2))
        val amounts = listOf(20.0, 140.0, 35.0, 210.0)
        val expenses = dates.zip(amounts) { d, a -> expense("Costco", d, a) }
        assertTrue(flagged(RecurringInference.annotate(expenses, emptyList())).isEmpty())
    }

    @Test
    fun `two occurrences are not enough`() {
        assertTrue(flagged(RecurringInference.annotate(monthly("Gym", 40.0, 1..2), emptyList())).isEmpty())
    }

    @Test
    fun `manual expense matching a defined recurring bill is detected even once`() {
        val bill = RecurringExpense(
            description = "Insurance", amount = 150.0, category = ExpenseCategory.OTHER,
            frequency = RecurringFrequency.MONTHLY, startDate = LocalDate.of(2026, 1, 1)
        )
        val match = expense("insurance", LocalDate.of(2026, 9, 5), 150.0)
        val differentAmount = expense("Insurance", LocalDate.of(2026, 9, 6), 900.0)
        val result = RecurringInference.annotate(listOf(match, differentAmount), listOf(bill))
        assertTrue(result.first { it.id == match.id }.looksRecurring)
        assertFalse(result.first { it.id == differentAmount.id }.looksRecurring)
    }

    @Test
    fun `already flagged and ignored expenses are left alone`() {
        val flaggedAlready = monthly("Rent", 1200.0).map { it.copy(isRecurring = true) }
        val result = RecurringInference.annotate(flaggedAlready, emptyList())
        assertTrue(result.none { it.looksRecurring })
        assertTrue(result.all { it.countsAsRecurring })
    }

    @Test
    fun `inferring rent improves projections when it was logged by hand`() {
        // 6 months of hand-logged rent plus small daily-ish spending, today mid-September.
        val history = mutableListOf<Expense>()
        val funDays = listOf(17, 6, 24, 11, 27, 8, 20)
        val groceryDays = listOf(4, 13, 7, 19, 3, 16, 10)
        val funAmounts = listOf(35.0, 62.0, 18.0, 44.0, 75.0, 22.0, 50.0)
        val groceryAmounts = listOf(88.0, 31.0, 64.0, 120.0, 45.0, 97.0, 26.0)
        for ((i, month) in (3..9).withIndex()) {
            history += expense("Rent", LocalDate.of(2026, month, 1), 1200.0)
            // Irregular days and amounts, like real day-to-day spending
            history += expense("Groceries", LocalDate.of(2026, month, groceryDays[i]), groceryAmounts[i])
            history += expense("Fun", LocalDate.of(2026, month, funDays[i]), funAmounts[i])
        }
        val today = LocalDate.of(2026, 9, 20)
        val known = history.filter { !it.date.isAfter(today) }

        val annotated = RecurringInference.annotate(known, emptyList())
        val plain = ProjectionMath.historyBaseline(known, today)
        val inferred = ProjectionMath.historyBaseline(annotated, today)

        // Rent moves out of the daily discretionary rate and into the recurring monthly total.
        assertTrue(inferred.discretionaryDailyRate < plain.discretionaryDailyRate / 5)
        assertEquals(1200.0, inferred.recurringMonthlyTotal, 1.0)
    }
}
