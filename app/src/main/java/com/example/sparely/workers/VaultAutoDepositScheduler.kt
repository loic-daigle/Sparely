package com.example.sparely.workers

import android.content.Context
import androidx.work.*
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Manages the scheduling of the VaultAutoDepositWorker.
 */
class VaultAutoDepositScheduler(private val context: Context) {
    
    private val workManager = WorkManager.getInstance(context.applicationContext)
    
    /**
     * Schedules daily auto-deposit checks.
     * @param enabled Whether auto-deposits are enabled globally
     * @param checkTimeHour Hour of day (0-23) to run the check
     */
    fun schedule(enabled: Boolean, checkTimeHour: Int = 9) {
        if (!enabled) {
            cancel()
            return
        }
        
        val initialDelay = computeInitialDelay(checkTimeHour)
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        
        val request = PeriodicWorkRequestBuilder<VaultAutoDepositWorker>(1, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
    
    /**
     * Cancels the scheduled auto-deposit worker.
     */
    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }
    
    /**
     * Triggers an immediate one-time check for due auto-deposits.
     */
    fun runImmediateCheck() {
        val request = OneTimeWorkRequestBuilder<VaultAutoDepositWorker>()
            .addTag(WORK_TAG)
            .build()
        
        workManager.enqueue(request)
    }
    
    private fun computeInitialDelay(targetHour: Int): Long {
        val now = ZonedDateTime.now()
        val targetToday = now.withHour(targetHour.coerceIn(0, 23))
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        
        var firstRun = if (targetToday.isBefore(now)) {
            targetToday.plusDays(1)
        } else {
            targetToday
        }
        
        var delay = Duration.between(now, firstRun)
        
        // Ensure minimum delay
        val minimumDelay = Duration.ofMinutes(5)
        if (delay < minimumDelay) {
            firstRun = firstRun.plusDays(1)
            delay = Duration.between(now, firstRun)
        }
        
        return delay.toMillis()
    }
    
    companion object {
        private const val WORK_NAME = "sparely-vault-auto-deposits"
        private const val WORK_TAG = "vault-auto-deposit"
    }
}
