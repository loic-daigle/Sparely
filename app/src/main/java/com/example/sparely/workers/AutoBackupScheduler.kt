package com.example.sparely.workers

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
        private const val BACKUP_DIR_NAME = "backups"
        private const val MAX_BACKUP_FILES = 5 // Keep last 5 backups
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
                
                // Export data to JSON
                val json = backupRepository.exportData()
                
                // Create backup file
                val backupDir = getBackupDirectory(context)
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                val backupFile = File(backupDir, "sparely_backup_$timestamp.json")
                
                backupFile.writeText(json)
                
                // Clean up old backups
                cleanupOldBackups(backupDir)
                
                // Update last backup timestamp
                preferencesRepository.updateLastAutoBackupTimestamp(System.currentTimeMillis())
                
                Log.d(TAG, "Backup saved: ${backupFile.absolutePath}")
                
                withContext(Dispatchers.Main) {
                    onResult(true, "Backup saved successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backup failed", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Backup failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Get the backup directory, creating it if necessary.
     */
    fun getBackupDirectory(context: Context): File {
        val backupDir = File(context.getExternalFilesDir(null), BACKUP_DIR_NAME)
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        return backupDir
    }
    
    /**
     * Get list of existing backup files, sorted by date (newest first).
     */
    fun getBackupFiles(context: Context): List<File> {
        val backupDir = getBackupDirectory(context)
        return backupDir.listFiles { file -> 
            file.isFile && file.name.startsWith("sparely_backup_") && file.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    /**
     * Clean up old backups, keeping only the most recent MAX_BACKUP_FILES.
     */
    private fun cleanupOldBackups(backupDir: File) {
        val backupFiles = backupDir.listFiles { file ->
            file.isFile && file.name.startsWith("sparely_backup_") && file.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() } ?: return
        
        if (backupFiles.size > MAX_BACKUP_FILES) {
            backupFiles.drop(MAX_BACKUP_FILES).forEach { file ->
                Log.d(TAG, "Deleting old backup: ${file.name}")
                file.delete()
            }
        }
    }
}
