package com.example.sparely.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sparely.SparelyApplication
import java.io.File
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * WorkManager worker for performing automatic backups in the background.
 */
class AutoBackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val BACKUP_DIR_NAME = "backups"
        private const val MAX_BACKUP_FILES = 5
    }
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting automatic backup")
        
        return try {
            val app = applicationContext as SparelyApplication
            val backupRepository = app.container.backupRepository
            val preferencesRepository = app.container.preferencesRepository
            
            // Check if auto backup is still enabled
            val settings = preferencesRepository.getSettingsSnapshot()
            if (!settings.autoBackupEnabled) {
                Log.d(TAG, "Auto backup disabled, skipping")
                return Result.success()
            }
            
            // An empty database (lost to a reinstall or a downgrade) must not push the last good
            // backups out: only MAX_BACKUP_FILES are kept, so a few empty ones would replace them all
            val backupDir = getBackupDirectory()
            val savingsRepository = app.container.savingsRepository
            val hasData = savingsRepository.observeExpenses().first().isNotEmpty() ||
                savingsRepository.observeAllSmartVaults().first().isNotEmpty() ||
                savingsRepository.observeMainAccountTransactions().first().isNotEmpty() ||
                savingsRepository.observeRecurringExpenses().first().isNotEmpty()
            val hasEarlierBackups = backupDir.listFiles { file ->
                file.isFile && file.name.startsWith("sparely_backup_") && file.name.endsWith(".json")
            }?.isNotEmpty() == true
            if (!hasData && hasEarlierBackups) {
                Log.w(TAG, "No data to back up; keeping the earlier backups as they are")
                return Result.success()
            }

            // Export data to JSON
            val json = backupRepository.exportData()

            // Create backup file
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val backupFile = File(backupDir, "sparely_backup_$timestamp.json")
            
            backupFile.writeText(json)
            
            // Clean up old backups
            cleanupOldBackups(backupDir)
            
            // Update last backup timestamp
            preferencesRepository.updateLastAutoBackupTimestamp(System.currentTimeMillis())
            
            Log.d(TAG, "Backup completed: ${backupFile.absolutePath}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            Result.retry()
        }
    }
    
    private fun getBackupDirectory(): File {
        val backupDir = File(applicationContext.getExternalFilesDir(null), BACKUP_DIR_NAME)
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        return backupDir
    }
    
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
