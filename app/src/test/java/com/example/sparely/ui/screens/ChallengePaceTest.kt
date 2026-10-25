package com.example.sparely.ui.screens

import com.example.sparely.domain.model.ChallengeType
import com.example.sparely.domain.model.SavingsChallenge
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ChallengePaceTest {

    private fun challenge(start: LocalDate, end: LocalDate) = SavingsChallenge(
        type = ChallengeType.DAILY_SAVINGS,
        title = "t",
        description = "d",
        targetAmount = 150.0,
        startDate = start,
        endDate = end
    )

    @Test
    fun paceIsTheShareOfDaysGone() {
        val c = challenge(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertEquals(0f, challengePace(c, LocalDate.of(2026, 10, 1)), 1e-6f)
        assertEquals(0.5f, challengePace(c, LocalDate.of(2026, 10, 16)), 1e-6f)
        // Before it starts and after it ends, held to the ends
        assertEquals(0f, challengePace(c, LocalDate.of(2026, 9, 20)), 1e-6f)
        assertEquals(1f, challengePace(c, LocalDate.of(2026, 11, 5)), 1e-6f)
    }

    @Test
    fun aSameDayChallengeDoesNotDivideByZero() {
        val day = LocalDate.of(2026, 10, 25)
        assertEquals(0f, challengePace(challenge(day, day), day), 1e-6f)
    }

    @Test
    fun statusLeavesALittleRoomAroundThePace() {
        assertEquals(ChallengePace.ON, challengePaceStatus(0.51f, 0.5f))
        assertEquals(ChallengePace.AHEAD, challengePaceStatus(0.6f, 0.5f))
        assertEquals(ChallengePace.BEHIND, challengePaceStatus(0.4f, 0.5f))
    }
}
