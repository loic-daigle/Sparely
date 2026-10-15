package com.example.sparely.notifications

import com.sparely.app.R

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sparely.data.local.SparelyDatabase
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        NotificationHelper.ensureChannels(applicationContext)
        val database = SparelyDatabase.getInstance(applicationContext)
        val expenses = database.expenseDao().observeExpenses().first()
        val message = if (expenses.isEmpty()) {
            applicationContext.getString(R.string.reminder_no_expenses)
        } else {
            val sevenDaysAgo = LocalDate.now().minusDays(7)
            val recent = expenses.filter { !it.expense.date.isBefore(sevenDaysAgo) }
            val reserved = recent.sumOf { it.expense.emergencyAmount + it.expense.investmentAmount + it.expense.funAmount }
            if (recent.isEmpty()) {
                applicationContext.getString(R.string.reminder_none_this_week)
            } else {
                applicationContext.getString(R.string.reminder_set_aside, NotificationHelper.formatAmount(reserved))
            }
        }
        NotificationHelper.showReminder(applicationContext, message)
        return Result.success()
    }
}
