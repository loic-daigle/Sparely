package com.example.sparely.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurringScheduleTest {

    private val jan31 = LocalDate.of(2025, 1, 31)

    @Test
    fun monthlyScheduleKeepsDayOfMonthInsteadOfDrifting() {
        var date = jan31
        val seen = mutableListOf<LocalDate>()
        repeat(4) {
            date = RecurringFrequency.MONTHLY.nextOccurrenceAfter(jan31, date)
            seen += date
        }
        assertEquals(
            listOf(
                LocalDate.of(2025, 2, 28),
                LocalDate.of(2025, 3, 31),
                LocalDate.of(2025, 4, 30),
                LocalDate.of(2025, 5, 31)
            ),
            seen
        )
    }

    @Test
    fun returnsAnchorWhenDateIsBeforeStart() {
        assertEquals(jan31, RecurringFrequency.WEEKLY.nextOccurrenceAfter(jan31, jan31.minusDays(10)))
        assertEquals(jan31, RecurringFrequency.MONTHLY.nextOccurrenceAfter(jan31, jan31.minusDays(1)))
    }

    @Test
    fun resultIsAlwaysStrictlyAfterDate() {
        val anchor = LocalDate.of(2020, 2, 29)
        RecurringFrequency.entries.forEach { freq ->
            var d = anchor.minusDays(3)
            repeat(400) {
                val next = freq.nextOccurrenceAfter(anchor, d)
                assert(next.isAfter(d)) { "$freq: $next not after $d" }
                d = d.plusDays(3)
            }
        }
    }

    @Test
    fun snapsOffScheduleDatesBackOntoSchedule() {
        // A date that drifted to the 28th under the old logic snaps back to the 31st schedule.
        assertEquals(
            LocalDate.of(2025, 3, 31),
            RecurringFrequency.MONTHLY.nextOccurrenceAfter(jan31, LocalDate.of(2025, 3, 28))
        )
        assertEquals(
            LocalDate.of(2025, 2, 14),
            RecurringFrequency.BIWEEKLY.nextOccurrenceAfter(jan31, LocalDate.of(2025, 2, 13))
        )
        assertEquals(
            LocalDate.of(2028, 2, 29),
            RecurringFrequency.YEARLY.nextOccurrenceAfter(LocalDate.of(2024, 2, 29), LocalDate.of(2027, 3, 1))
        )
    }

    @Test
    fun farPastDailyScheduleIsComputedWithoutIterationCap() {
        val start = LocalDate.of(2000, 1, 1)
        val today = LocalDate.of(2025, 6, 15)
        assertEquals(today.plusDays(1), RecurringFrequency.DAILY.nextOccurrenceAfter(start, today))
    }
}
