package com.example.sparely.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sparely.DefaultAppContainer
import com.example.sparely.data.preferences.UserPreferencesRepository
import java.time.LocalDate

/**
 * Background worker that runs the smart allocation engine once per month and
 * persists the suggested allocations to the allocation_history table.
 */
class MonthlyAllocationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val container = DefaultAppContainer(applicationContext)
            val prefs = UserPreferencesRepository(applicationContext)

            // Read settings snapshot for monthly income and main account balance
            val settings = prefs.getSettingsSnapshot()
            val monthlyIncome = settings.monthlyIncome.coerceAtLeast(0.0)
            // Pass the real (possibly overdrawn) balance: a negative balance means a bigger buffer shortfall.
            val mainAccountBalance = settings.mainAccountBalance

            // Run allocation (service persists allocation suggestions)
            container.smartAllocationService.runMonthlyAllocation(
                monthlyIncome = monthlyIncome,
                mainAccountBalance = mainAccountBalance,
                // safeBufferPercent left as default in service for now
            )

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
