package com.example.sparely.workers

import com.sparely.app.R

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.sparely.SparelyApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Scheduler for automatic backup functionality.
 * Uses WorkManager to schedule periodic backups based on user settings.
 */
class AutoBackupScheduler(private val context: Context) {
    
    companion object {
        private const val TAG = "AutoBackupScheduler"
        private const val WORK_NAME = "auto_backup_work"
        private const val SAFETY_WORK_NAME = "safety_backup_daily"
    }

    /**
     * Schedule or cancel auto backup based on settings.
     */
    fun schedule(enabled: Boolean, frequencyDays: Int) {
        val workManager = WorkManager.getInstance(context)
        
        if (!enabled) {
            Log.d(TAG, "Auto backup disabled, cancelling scheduled work")
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        
        Log.d(TAG, "Scheduling auto backup every $frequencyDays days")
        
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        
        val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            frequencyDays.toLong(), TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS) // Start first backup in 1 hour
            .addTag("auto_backup")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * Trigger an immediate backup (for manual "Backup Now" button).
     */
    suspend fun runImmediateBackup(
        context: Context,
        onResult: (Boolean, String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val app = context.applicationContext as SparelyApplication
                val backupRepository = app.container.backupRepository
                val preferencesRepository = app.container.preferencesRepository
                
                val backupFile = com.example.sparely.data.backup.SafetyBackups.write(context, backupRepository.exportData())

                // Update last backup timestamp
                preferencesRepository.updateLastAutoBackupTimestamp(System.currentTimeMillis())
                
                Log.d(TAG, "Backup saved: ${backupFile.absolutePath}")
                
                withContext(Dispatchers.Main) {
                    onResult(true, context.getString(R.string.backup_saved))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backup failed", e)
                withContext(Dispatchers.Main) {
                    onResult(false, context.getString(R.string.backup_failed, e.message ?: ""))
                }
            }
        }
    }
    
    /** The backup folder (see [com.example.sparely.data.backup.SafetyBackups]). */
    fun getBackupDirectory(context: Context): File = com.example.sparely.data.backup.SafetyBackups.directory(context)

    /** Existing backup files, newest first. */
    fun getBackupFiles(context: Context): List<File> = com.example.sparely.data.backup.SafetyBackups.files(context)

    /**
     * The daily safety backup: runs every day whatever the auto backup setting says, so there is
     * always a recent copy to fall back on. It skips the day when the database is empty.
     */
    fun scheduleSafetyBackup() {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setInputData(workDataOf(AutoBackupWorker.KEY_SAFETY to true))
            .setInitialDelay(15, TimeUnit.MINUTES)
            .addTag("safety_backup")
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SAFETY_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
