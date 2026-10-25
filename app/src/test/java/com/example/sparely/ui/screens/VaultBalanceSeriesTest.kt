package com.example.sparely.ui.screens

import com.example.sparely.domain.model.HistoryContribution
import com.example.sparely.domain.model.VaultContribution
import com.example.sparely.domain.model.VaultContributionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VaultBalanceSeriesTest {

    private fun deposit(id: Long, daysAgo: Long, amount: Double, reconciled: Boolean = true) = HistoryContribution(
        VaultContribution(
            id = id,
            vaultId = 1L,
            amount = amount,
            date = LocalDate.now().minusDays(daysAgo),
            source = VaultContributionSource.MANUAL,
            reconciled = reconciled
        )
    )

    @Test
    fun worksBackFromTodaysBalance() {
        val series = VaultBalanceSeries.of(
            current = 300.0,
            history = listOf(deposit(1, 20, 100.0), deposit(2, 10, 200.0))
        )
        // Before the first deposit, after it, after the second, today
        assertEquals(listOf(0.0, 100.0, 300.0), series.map { it.second }.distinct())
        assertEquals(LocalDate.now(), series.last().first)
        assertEquals(300.0, series.last().second, 1e-9)
    }

    @Test
    fun transfersStillWaitingAreLeftOut() {
        val series = VaultBalanceSeries.of(
            current = 100.0,
            history = listOf(deposit(1, 5, 100.0), deposit(2, 1, 50.0, reconciled = false))
        )
        assertTrue(series.none { it.second > 100.0 })
    }

    @Test
    fun noHistoryNoLine() {
        assertTrue(VaultBalanceSeries.of(500.0, emptyList()).isEmpty())
    }
}
