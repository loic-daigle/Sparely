package com.example.sparely.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sparely.SparelyApplication
import com.example.sparely.data.backup.SafetyBackups

/**
 * WorkManager worker for performing automatic backups in the background. Run with
 * [KEY_SAFETY] set, it's the daily safety backup, which runs whatever the auto backup setting is.
 */
class AutoBackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AutoBackupWorker"
        const val KEY_SAFETY = "safety"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting automatic backup")

        return try {
            val app = applicationContext as SparelyApplication
            val backupRepository = app.container.backupRepository
            val preferencesRepository = app.container.preferencesRepository
            val safety = inputData.getBoolean(KEY_SAFETY, false)

            // Check if auto backup is still enabled (the safety backup always runs)
            val settings = preferencesRepository.getSettingsSnapshot()
            if (!safety && !settings.autoBackupEnabled) {
                Log.d(TAG, "Auto backup disabled, skipping")
                return Result.success()
            }

            // An empty database (lost to a reinstall, a downgrade or a damaged file) must not be
            // backed up over the good copies; the app offers those back instead
            val hasData = SafetyBackups.databaseHasData(app.container)
            if (!hasData && SafetyBackups.files(applicationContext).isNotEmpty()) {
                Log.w(TAG, "No data to back up; keeping the earlier backups as they are")
                return Result.success()
            }

            val file = SafetyBackups.write(applicationContext, backupRepository.exportData())
            preferencesRepository.updateLastAutoBackupTimestamp(System.currentTimeMillis())

            Log.d(TAG, "Backup completed: ${file.absolutePath}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            Result.retry()
        }
    }
}
