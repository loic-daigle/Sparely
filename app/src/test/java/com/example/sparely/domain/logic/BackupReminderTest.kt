package com.example.sparely.domain.logic

import com.example.sparely.domain.model.SparelySettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderTest {
    private val now = 1_000L * BackupReminder.DAY_MS

    @Test
    fun neverBackedUpWaitsForEnoughExpenses() {
        assertFalse(BackupReminder.isDue(SparelySettings(), expenseCount = 5, nowMs = now))
        assertTrue(BackupReminder.isDue(SparelySettings(), expenseCount = 20, nowMs = now))
    }

    @Test
    fun recentBackupOfEitherKindIsEnough() {
        val recent = now - 5 * BackupReminder.DAY_MS
        assertFalse(BackupReminder.isDue(SparelySettings(lastManualBackupTimestamp = recent), 100, now))
        assertFalse(BackupReminder.isDue(SparelySettings(lastAutoBackupTimestamp = recent), 100, now))
    }

    @Test
    fun staleBackupIsDue() {
        val old = now - 31 * BackupReminder.DAY_MS
        assertTrue(BackupReminder.isDue(SparelySettings(lastManualBackupTimestamp = old), 1, now))
    }

    @Test
    fun automaticBackupsAndSnoozeSilenceIt() {
        assertFalse(BackupReminder.isDue(SparelySettings(autoBackupEnabled = true), 100, now))
        assertFalse(BackupReminder.isDue(SparelySettings(backupReminderSnoozedUntil = now + 1), 100, now))
        assertTrue(BackupReminder.isDue(SparelySettings(backupReminderSnoozedUntil = now - 1), 100, now))
    }
}
