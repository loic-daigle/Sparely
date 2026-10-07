package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Wishlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WishPlanTest {

    private val start = LocalDate.of(2026, 1, 1)

    private fun wish(
        target: Double = 1_200.0,
        saved: Double = 0.0,
        date: LocalDate? = null,
        archived: Boolean = false
    ) = Wishlist(
        description = "Bike",
        targetAmount = target,
        currentSavings = saved,
        createdAt = start.atStartOfDay(),
        targetDate = date,
        archived = archived
    )

    @Test
    fun `without a date only the manual amount is reserved`() {
        assertEquals(0.0, WishPlan.reserved(wish(), start.plusMonths(3)), 0.001)
        assertEquals(300.0, WishPlan.reserved(wish(saved = 300.0), start.plusMonths(3)), 0.001)
    }

    @Test
    fun `reserve builds up evenly toward the target date`() {
        val w = wish(target = 1_000.0, date = start.plusDays(100))

        assertEquals(0.0, WishPlan.reserved(w, start), 0.001)
        assertEquals(500.0, WishPlan.reserved(w, start.plusDays(50)), 0.001)
        assertEquals(1_000.0, WishPlan.reserved(w, start.plusDays(100)), 0.001)
    }

    @Test
    fun `reserve stops at the target after the date has passed`() {
        val w = wish(target = 1_000.0, date = start.plusDays(100))

        assertEquals(1_000.0, WishPlan.reserved(w, start.plusDays(400)), 0.001)
    }

    @Test
    fun `manual savings ahead of the schedule are kept`() {
        val w = wish(target = 1_000.0, saved = 800.0, date = start.plusDays(100))

        assertEquals(800.0, WishPlan.reserved(w, start.plusDays(10)), 0.001)
    }

    @Test
    fun `archived wishes hold nothing back`() {
        val w = wish(saved = 500.0, date = start.plusDays(10), archived = true)

        assertEquals(0.0, WishPlan.reserved(w, start.plusDays(5)), 0.001)
    }

    @Test
    fun `total reserved adds up every wish`() {
        val wishes = listOf(wish(saved = 100.0), wish(saved = 250.0))

        assertEquals(350.0, WishPlan.totalReserved(wishes, start), 0.001)
    }

    @Test
    fun `monthly amount spreads the target over the months until the date`() {
        // 1,200 over six months.
        assertEquals(200.0, WishPlan.monthlyAmount(wish(date = start.plusMonths(6)))!!, 0.5)
        assertNull(WishPlan.monthlyAmount(wish()))
    }

    @Test
    fun `months between rounds up and is at least one`() {
        assertEquals(1, WishPlan.monthsBetween(start, start))
        assertEquals(1, WishPlan.monthsBetween(start, start.plusDays(20)))
        assertEquals(2, WishPlan.monthsBetween(start, start.plusDays(40)))
        assertEquals(12, WishPlan.monthsBetween(start, start.plusYears(1)))
    }

    @Test
    fun `days left never goes negative`() {
        val w = wish(date = start.plusDays(10))

        assertEquals(4, WishPlan.daysLeft(w, start.plusDays(6)))
        assertEquals(0, WishPlan.daysLeft(w, start.plusDays(30)))
        assertNull(WishPlan.daysLeft(wish(), start))
    }
}
