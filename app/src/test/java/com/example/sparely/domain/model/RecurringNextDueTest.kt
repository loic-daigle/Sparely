package com.example.sparely.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurringNextDueTest {

    private val today = LocalDate.of(2026, 10, 21)

    private fun bill(
        startDate: LocalDate,
        nextRun: LocalDate?,
        lastProcessed: LocalDate? = null,
        frequency: RecurringFrequency = RecurringFrequency.MONTHLY
    ) = RecurringExpense(
        id = 1L,
        description = "Phone",
        amount = 50.0,
        category = ExpenseCategory.OTHER,
        frequency = frequency,
        startDate = startDate,
        lastProcessedDate = lastProcessed,
        nextRunAt = nextRun?.atTime(9, 0)
    )

    @Test
    fun nextDueIsTheSchedulersRunWhenUpcoming() {
        // What the home screen showed: the run the scheduler will charge on
        val expense = bill(LocalDate.of(2026, 8, 21), nextRun = LocalDate.of(2026, 10, 23))
        assertEquals(LocalDate.of(2026, 10, 23), expense.nextDueDate(today))
    }

    @Test
    fun runLeftOnTheOldDayAfterAnEditIsOffSchedule() {
        // Start moved from the 23rd to the 21st; the edit kept the run on the 23rd
        val expense = bill(
            LocalDate.of(2026, 8, 21),
            nextRun = LocalDate.of(2026, 10, 23),
            lastProcessed = LocalDate.of(2026, 9, 23)
        )
        assertFalse(expense.isNextRunOnSchedule())
        assertEquals(LocalDate.of(2026, 10, 21), expense.rescheduledRunDate(today))
    }

    @Test
    fun clampedEndOfMonthRunIsOnSchedule() {
        val expense = bill(LocalDate.of(2026, 1, 31), nextRun = LocalDate.of(2026, 2, 28))
        assertTrue(expense.isNextRunOnSchedule())
    }

    @Test
    fun reschedulingDoesNotChargeAPeriodAlreadyPaid() {
        // Paid on the 19th on the old schedule; moving the day to the 21st waits for next month
        val expense = bill(
            LocalDate.of(2026, 8, 21),
            nextRun = LocalDate.of(2026, 11, 19),
            lastProcessed = LocalDate.of(2026, 10, 19)
        )
        assertEquals(LocalDate.of(2026, 11, 21), expense.rescheduledRunDate(today))
    }

    @Test
    fun reschedulingToAFutureStartWaitsForIt() {
        val expense = bill(LocalDate.of(2026, 11, 5), nextRun = LocalDate.of(2026, 10, 23))
        assertEquals(LocalDate.of(2026, 11, 5), expense.rescheduledRunDate(today))
    }

    @Test
    fun neverChargedBillStartingTodayBeginsAtItsNextDate() {
        val expense = bill(today, nextRun = null, frequency = RecurringFrequency.WEEKLY)
        assertEquals(today.plusWeeks(1), expense.rescheduledRunDate(today))
    }
}
