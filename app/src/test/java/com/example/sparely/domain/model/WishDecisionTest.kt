package com.example.sparely.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WishDecisionTest {

    private val now = LocalDateTime.of(2026, 10, 7, 12, 0)

    private fun wish(cooldownEnds: LocalDateTime?, archived: Boolean = false) = Wishlist(
        description = "Headphones",
        targetAmount = 300.0,
        createdAt = now.minusDays(10),
        cooldownExpiresAt = cooldownEnds,
        archived = archived
    )

    @Test
    fun `not asked while the reflection time has more than three days left`() {
        assertFalse(wish(now.plusDays(4)).needsDecision(now))
    }

    @Test
    fun `asked from three days before the reflection time ends`() {
        assertTrue(wish(now.plusDays(3)).needsDecision(now))
        assertTrue(wish(now.plusDays(1)).needsDecision(now))
    }

    @Test
    fun `still asked after the reflection time ended until answered`() {
        assertTrue(wish(now.minusDays(2)).needsDecision(now))
    }

    @Test
    fun `answering clears the reflection time so it is not asked again`() {
        assertFalse(wish(null).needsDecision(now))
    }

    @Test
    fun `archived wishes are never asked`() {
        assertFalse(wish(now.minusDays(1), archived = true).needsDecision(now))
    }
}
