package com.example.sparely.domain.logic

import com.example.sparely.domain.model.SparelySettings

/**
 * When the dashboard nudges the user to back up. Everything lives only on the phone, so a lost or
 * reset phone loses it all unless a backup exists somewhere.
 */
object BackupReminder {
    const val DAY_MS = 24 * 60 * 60 * 1000L
    /** A backup older than this counts as missing. */
    const val STALE_AFTER_MS = 30 * DAY_MS
    /** "Later" hides the reminder this long. */
    const val SNOOZE_MS = 14 * DAY_MS
    /** Never backed up: wait until there's this much worth losing before asking. */
    const val MIN_EXPENSES_WITHOUT_BACKUP = 20

    fun isDue(settings: SparelySettings, expenseCount: Int, nowMs: Long): Boolean {
        if (settings.autoBackupEnabled) return false
        if (settings.backupReminderSnoozedUntil?.let { nowMs < it } == true) return false
        val lastBackup = listOfNotNull(settings.lastManualBackupTimestamp, settings.lastAutoBackupTimestamp).maxOrNull()
        return if (lastBackup == null) {
            expenseCount >= MIN_EXPENSES_WITHOUT_BACKUP
        } else {
            nowMs - lastBackup > STALE_AFTER_MS
        }
    }
}
