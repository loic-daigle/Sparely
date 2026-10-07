package com.example.sparely.domain.logic

import com.example.sparely.domain.logic.PurchaseAffordabilityEngine.Verdict
import com.example.sparely.domain.model.Wishlist
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class WishReadinessTest {

    private val now = LocalDateTime.of(2026, 10, 7, 12, 0)

    private fun wish(saved: Double = 0.0, cooldownEnds: LocalDateTime? = null) = Wishlist(
        description = "Laptop",
        targetAmount = 1_000.0,
        currentSavings = saved,
        createdAt = now.minusDays(10),
        cooldownExpiresAt = cooldownEnds
    )

    @Test
    fun `affordable wish with an expired cool-off is ready`() {
        assertEquals(WishReadiness.READY, WishReadiness.assess(wish(cooldownEnds = now.minusDays(1)), Verdict.GO_AHEAD, now))
    }

    @Test
    fun `affordable wish still cooling down waits`() {
        assertEquals(WishReadiness.COOLING_DOWN, WishReadiness.assess(wish(cooldownEnds = now.plusDays(2)), Verdict.GO_AHEAD, now))
    }

    @Test
    fun `wait verdict keeps saving up`() {
        assertEquals(WishReadiness.SAVING_UP, WishReadiness.assess(wish(saved = 400.0), Verdict.WAIT, now))
    }

    @Test
    fun `fully saved wish is ready even when the verdict is wait`() {
        assertEquals(WishReadiness.READY, WishReadiness.assess(wish(saved = 1_000.0), Verdict.WAIT, now))
    }

    @Test
    fun `money held back by the schedule counts as saved`() {
        assertEquals(WishReadiness.READY, WishReadiness.assess(wish(saved = 0.0), Verdict.WAIT, now, reserved = 1_000.0))
    }

    @Test
    fun `avoid verdict is never ready even when fully saved`() {
        assertEquals(WishReadiness.NOT_AFFORDABLE, WishReadiness.assess(wish(saved = 1_000.0), Verdict.AVOID, now))
    }
}
