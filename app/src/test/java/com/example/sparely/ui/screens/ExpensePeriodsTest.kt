package com.example.sparely.ui.screens

import com.example.sparely.domain.model.AllocationBreakdown
import com.example.sparely.domain.model.DateRangeFilter
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsPercentages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ExpensePeriodsTest {

    private val today = LocalDate.of(2026, 10, 24)
    private var nextId = 1L

    private fun expense(date: LocalDate, amount: Double, refunded: Double = 0.0) = Expense(
        id = nextId++,
        description = "e",
        amount = amount,
        category = ExpenseCategory.GROCERIES,
        date = date,
        includesTax = false,
        allocation = AllocationBreakdown(0.0, 0.0, 0.0, 0.0, 0.0),
        appliedPercentages = SavingsPercentages(0.0, 0.0, 0.0),
        autoRecommended = false,
        riskLevelUsed = RiskLevel.BALANCED,
        refundedAmount = refunded
    )

    @Test
    fun rangesEndToday() {
        assertEquals(LocalDate.of(2026, 10, 1)..today, ExpensePeriods.range(DateRangeFilter.THIS_MONTH, today))
        assertEquals(LocalDate.of(2026, 9, 25)..today, ExpensePeriods.range(DateRangeFilter.LAST_30_DAYS, today))
        assertNull(ExpensePeriods.range(DateRangeFilter.ALL_TIME, today))
    }

    @Test
    fun thisMonthComparesWithTheSameDaysLastMonth() {
        val range = ExpensePeriods.range(DateRangeFilter.THIS_MONTH, today)!!
        assertEquals(LocalDate.of(2026, 9, 1)..LocalDate.of(2026, 9, 24), ExpensePeriods.previous(DateRangeFilter.THIS_MONTH, range))
    }

    @Test
    fun rollingPeriodsCompareWithTheDaysJustBefore() {
        val range = ExpensePeriods.range(DateRangeFilter.LAST_7_DAYS, today)!!
        assertEquals(LocalDate.of(2026, 10, 11)..LocalDate.of(2026, 10, 17), ExpensePeriods.previous(DateRangeFilter.LAST_7_DAYS, range))
    }

    @Test
    fun dailyTotalsCoverEveryDayNetOfRefunds() {
        val range = LocalDate.of(2026, 10, 20)..today
        val expenses = listOf(
            expense(LocalDate.of(2026, 10, 20), 10.0),
            expense(LocalDate.of(2026, 10, 20), 5.0),
            expense(LocalDate.of(2026, 10, 23), 40.0, refunded = 15.0),
            expense(LocalDate.of(2026, 10, 19), 99.0)
        )
        assertEquals(listOf(15.0, 0.0, 0.0, 25.0, 0.0), ExpensePeriods.daily(expenses, range))
        assertEquals(40.0, ExpensePeriods.total(expenses, range), 1e-9)
    }
}
