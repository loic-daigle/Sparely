package com.example.sparely.domain.logic

import com.example.sparely.domain.model.AllocationBreakdown
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsPercentages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LongTermHabitsTest {

    private var nextId = 1L
    private val today = LocalDate.of(2026, 11, 5)

    private fun expense(date: LocalDate, amount: Double, category: ExpenseCategory = ExpenseCategory.SHOPPING, name: String = "Achat") = Expense(
        id = nextId++,
        description = name,
        amount = amount,
        category = category,
        date = date,
        includesTax = false,
        allocation = AllocationBreakdown(0.0, 0.0, 0.0, 0.0, 0.0),
        appliedPercentages = SavingsPercentages(0.0, 0.0, 0.0),
        autoRecommended = false,
        riskLevelUsed = RiskLevel.BALANCED
    )

    /** A year of ordinary spending, so big purchases stand out. */
    private fun everyday() = (0 until 52).map { expense(today.minusWeeks(it.toLong()), 40.0, ExpenseCategory.GROCERIES, "Épicerie") }

    @Test
    fun aBigPurchaseEveryTwoYearsInNovemberIsExpectedThisNovember() {
        val history = everyday() + listOf(
            expense(LocalDate.of(2022, 11, 18), 2400.0, name = "Ordinateur"),
            expense(LocalDate.of(2024, 11, 22), 2600.0, name = "Ordinateur")
        )
        val habit = LongTermHabits.detect(history, today).single()
        assertEquals(24, habit.intervalMonths)
        assertEquals(11, habit.month)
        assertEquals(YearMonth.of(2026, 11), habit.nextExpected)
        assertEquals(2500.0, habit.averageAmount, 0.01)
        assertEquals("Ordinateur", habit.label)
        assertEquals(0L, habit.monthsUntil(today))
        assertTrue(habit.isDueSoon(today))
    }

    @Test
    fun thisCyclesPurchaseAlreadyMadeEvenEarlyAndDearer() {
        val history = everyday() + listOf(
            expense(LocalDate.of(2022, 11, 18), 2400.0, name = "Ordinateur"),
            expense(LocalDate.of(2024, 11, 22), 2600.0, name = "Ordinateur"),
            // This time in September, and a bit more
            expense(LocalDate.of(2026, 9, 10), 3100.0, name = "Laptop")
        )
        val habit = LongTermHabits.detect(history, today).single()
        assertTrue(habit.doneThisCycle)
        assertTrue(!habit.isDueSoon(today))
        assertEquals(LocalDate.of(2026, 9, 10), habit.lastDate)
        assertTrue(habit.nextExpected.isAfter(YearMonth.of(2028, 1)))
    }

    @Test
    fun findsSeveralHabitsAtOnce() {
        val history = everyday() + listOf(
            expense(LocalDate.of(2022, 11, 18), 2400.0, name = "Ordinateur"),
            expense(LocalDate.of(2024, 11, 22), 2600.0, name = "Ordinateur"),
            expense(LocalDate.of(2024, 10, 15), 850.0, ExpenseCategory.TRANSPORTATION, "Pneus d'hiver"),
            expense(LocalDate.of(2025, 10, 20), 900.0, ExpenseCategory.TRANSPORTATION, "Pneus d'hiver")
        )
        val habits = LongTermHabits.detect(history, today)
        assertEquals(setOf("Ordinateur", "Pneus d'hiver"), habits.map { it.label }.toSet())
        assertEquals(12, habits.first { it.label == "Pneus d'hiver" }.intervalMonths)
    }

    @Test
    fun theSameStoreCountsEvenAtAnotherAmountOrCategory() {
        val a = expense(LocalDate.of(2024, 11, 1), 2500.0, ExpenseCategory.SHOPPING, "Best Buy")
        val b = expense(LocalDate.of(2026, 9, 1), 1200.0, ExpenseCategory.ENTERTAINMENT, "Best Buy")
        assertTrue(LongTermHabits.sameThing(a, b))
    }

    @Test
    fun differentKindsOrAmountsAreNotAHabit() {
        val history = everyday() + listOf(
            expense(LocalDate.of(2024, 11, 22), 2500.0, ExpenseCategory.SHOPPING, "Ordinateur"),
            expense(LocalDate.of(2025, 11, 22), 2500.0, ExpenseCategory.TRAVEL, "Voyage"),
            expense(LocalDate.of(2023, 3, 1), 900.0, ExpenseCategory.SHOPPING, "Divan")
        )
        assertTrue(LongTermHabits.detect(history, today).isEmpty())
    }

    @Test
    fun closePurchasesAreLeftToTheRegularDetection() {
        val history = everyday() + listOf(
            expense(LocalDate.of(2026, 3, 1), 2500.0),
            expense(LocalDate.of(2026, 7, 1), 2500.0)
        )
        assertTrue(LongTermHabits.detect(history, today).isEmpty())
    }

    @Test
    fun aHabitLongOverdueHasStopped() {
        val history = everyday() + listOf(
            expense(LocalDate.of(2021, 2, 1), 2500.0),
            expense(LocalDate.of(2023, 2, 1), 2500.0)
        )
        // Expected February 2025, twenty months ago
        assertTrue(LongTermHabits.detect(history, today).isEmpty())
    }
}
