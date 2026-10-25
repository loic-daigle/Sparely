package com.example.sparely.ui.screens

import com.example.sparely.data.local.MainAccountTransactionType
import com.example.sparely.domain.model.MainAccountTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MainAccountSeriesTest {

    private val today = LocalDate.of(2026, 10, 25)

    private fun tx(date: LocalDate, hour: Int, balanceAfter: Double) = MainAccountTransaction(
        type = MainAccountTransactionType.DEPOSIT,
        amount = 0.0,
        balanceAfter = balanceAfter,
        timestamp = LocalDateTime.of(date, LocalTime.of(hour, 0)),
        description = ""
    )

    @Test
    fun nothingRecordedGivesNoLine() {
        assertTrue(MainAccountSeries.balances(emptyList(), 500.0, today).isEmpty())
    }

    @Test
    fun opensWithTheBalanceBeforeTheWindowAndKeepsTheLastOfEachDay() {
        val series = MainAccountSeries.balances(
            listOf(
                tx(today.minusDays(200), 9, 100.0),
                tx(today.minusDays(10), 9, 300.0),
                tx(today.minusDays(10), 18, 250.0),
                tx(today.minusDays(3), 12, 400.0)
            ),
            currentBalance = 380.0,
            today = today
        )
        assertEquals(
            listOf(
                today.minusDays(89) to 100.0,
                today.minusDays(10) to 250.0,
                today.minusDays(3) to 400.0,
                today to 380.0
            ),
            series
        )
    }
}
