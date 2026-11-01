package com.example.sparely.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class SafetyBackupsTest {

    private val today = LocalDate.of(2026, 11, 1)

    @Test
    fun keepsTheNewestTenAndOnePerDayForAMonth() {
        // Twelve backups today (an empty database backed up again and again), then one a day before
        val todays = (1..12).map { "today_$it" to today }
        val older = (1..40).map { "day_$it" to today.minusDays(it.toLong()) }
        val kept = SafetyBackups.selectKept(todays + older, today)
        assertEquals((1..10).map { "today_$it" }, kept.take(10))
        // Every one of the last 30 days keeps its backup, so the good copies survive
        assertTrue((1..30).all { "day_$it" in kept })
        assertTrue("day_31" !in kept)
    }

    @Test
    fun readsWhatABackupHolds() {
        val file = File.createTempFile("sparely_backup_", ".json")
        file.writeText("""{"timestamp": 1761955200000, "expenses": [{}, {}, {}], "vaults": [{}], "recurringExpenses": [], "transactions": []}""")
        val summary = SafetyBackups.summarize(file)!!
        assertEquals(3, summary.expenses)
        assertEquals(1, summary.vaults)
        assertTrue(summary.hasData)
        file.writeText("""{"timestamp": 1761955200000, "expenses": [], "vaults": []}""")
        assertTrue(!SafetyBackups.summarize(file)!!.hasData)
        file.delete()
    }
}
