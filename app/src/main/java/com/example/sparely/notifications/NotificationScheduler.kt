package com.example.sparely.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.logic.PayScheduleCalculator
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class NotificationScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun schedule(settings: SparelySettings) {
        if (!settings.remindersEnabled) {
            cancel()
            return
        }
        NotificationHelper.ensureChannels(appContext)
        val initialDelay = computeInitialDelay(settings.reminderHour)
        val frequencyDays = settings.reminderFrequencyDays.toLong().coerceAtLeast(1L)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(frequencyDays, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    fun schedulePaydayReminder(settings: SparelySettings) {
        if (!settings.paydayReminderEnabled) {
            cancelPaydayReminder()
            return
        }

        val upcoming = PayScheduleCalculator.resolveUpcomingPayDate(settings.paySchedule) ?: return
        val zone = ZoneId.systemDefault()
        val hour = settings.paydayReminderHour.coerceIn(0, 23)
        val minute = settings.paydayReminderMinute.coerceIn(0, 59)
        var scheduledDate = upcoming
        var trigger = scheduledDate.atTime(hour, minute).atZone(zone)
        val now = ZonedDateTime.now(zone)
        if (!trigger.isAfter(now.plusMinutes(1))) {
            PayScheduleCalculator.computeNextPayDate(settings.paySchedule, scheduledDate)?.let { next ->
                scheduledDate = next
                trigger = scheduledDate.atTime(hour, minute).atZone(zone)
            } ?: return
        }

        val delay = Duration.between(now, trigger).coerceAtLeast(Duration.ZERO)
        val data = workDataOf(
            PaydayReminderWorker.KEY_EXPECTED_PAYDAY_EPOCH_DAY to scheduledDate.toEpochDay()
        )

        val request = OneTimeWorkRequestBuilder<PaydayReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        workManager.enqueueUniqueWork(
            PAYDAY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelPaydayReminder() {
        workManager.cancelUniqueWork(PAYDAY_WORK_NAME)
        NotificationHelper.dismissPaydayReminder(appContext)
    }

    fun dismissPaydayReminderNotification() {
        NotificationHelper.dismissPaydayReminder(appContext)
    }

    suspend fun showVaultTransferWorkflow(
        savingsRepository: com.example.sparely.data.repository.SavingsRepository,
        preferencesRepository: com.example.sparely.data.preferences.UserPreferencesRepository? = null
    ) {
        val pendingContributions = savingsRepository.getPendingVaultContributions()
        if (pendingContributions.isEmpty()) {
            NotificationHelper.dismissVaultTransferNotification(appContext)
            return
        }
        
        // Get minimum amount setting
        val minimumAmount = preferencesRepository?.getSettingsSnapshot()?.smartTransferMinimumAmount ?: 0.0

        // Group first, then filter whole vaults by their pending total, so a vault that
        // clears the threshold still reports its FULL total (matching the Transfer page)
        // instead of silently dropping individual contributions from the sum.
        val groupedByVault = pendingContributions.groupBy { it.vaultId }
            .filterValues { contributions -> minimumAmount <= 0 || contributions.sumOf { it.amount } >= minimumAmount }

        if (groupedByVault.isEmpty()) {
            NotificationHelper.dismissVaultTransferNotification(appContext)
            return
        }

        // Reset progress counter
        val prefs = appContext.getSharedPreferences("vault_transfer_workflow", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("completed_count", 0).apply()
        
        // Show notification for first vault
        val firstVaultId = groupedByVault.keys.first()
        val firstContributions = groupedByVault[firstVaultId]!!
        
        // Get vault name from flow
        val allVaults = savingsRepository.observeSmartVaults().first()
        val vault = allVaults.find { it.id == firstVaultId }
        
        NotificationHelper.showVaultTransferNotification(
            context = appContext,
            vaultId = firstVaultId,
            vaultName = vault?.name ?: "Vault",
            contributions = firstContributions,
            currentIndex = 0,
            totalVaultCount = groupedByVault.size
        )
    }

    fun dismissVaultTransferWorkflow() {
        NotificationHelper.dismissVaultTransferNotification(appContext)
    }

    fun scheduleCreditCardReminders(settings: SparelySettings) {
        if (!settings.creditCardReminderEnabled) {
            cancelCreditCardReminders()
            return
        }
        NotificationHelper.ensureChannels(appContext)
        
        val initialDelay = computeInitialDelay(settings.creditCardReminderHour)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
            
        val request = PeriodicWorkRequestBuilder<CreditCardReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
            
        workManager.enqueueUniquePeriodicWork(
            CREDIT_CARD_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelCreditCardReminders() {
        workManager.cancelUniqueWork(CREDIT_CARD_WORK_NAME)
    }

    private fun computeInitialDelay(targetHour: Int): Long {
        val now = ZonedDateTime.now()
        val targetToday = now.withHour(targetHour.coerceIn(0, 23))
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        var firstRun = if (targetToday.isBefore(now)) targetToday.plusDays(1) else targetToday
        var delay = Duration.between(now, firstRun)
        val minimumDelay = Duration.ofMinutes(15)
        if (delay < minimumDelay) {
            firstRun = firstRun.plusDays(1)
            delay = Duration.between(now, firstRun)
        }
        return delay.toMillis()
    }

    companion object {
        private const val WORK_NAME = "sparely-daily-reminder"
        private const val PAYDAY_WORK_NAME = "sparely-payday-reminder"
        private const val CREDIT_CARD_WORK_NAME = "sparely-credit-card-reminder"
    }
}
