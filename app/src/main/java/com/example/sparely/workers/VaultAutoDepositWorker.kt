package com.example.sparely.workers

import com.example.sparely.ui.utils.roundToTwoDecimals
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sparely.data.local.SparelyDatabase
import com.example.sparely.data.local.toDomain
import com.example.sparely.data.repository.SavingsRepository
import com.example.sparely.domain.model.RecurringFrequency
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.VaultSchedule
import com.example.sparely.domain.model.VaultScheduleType
import com.example.sparely.domain.model.VaultTransferDirection
import com.example.sparely.domain.model.predictNextAmount
import com.example.sparely.notifications.NotificationHelper
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * Background worker that evaluates advanced vault schedules and recurring expenses.
 */
class VaultAutoDepositWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val database = SparelyDatabase.getInstance(applicationContext)
            val preferencesRepository = com.example.sparely.data.preferences.UserPreferencesRepository(applicationContext)
            val savingsRepository = SavingsRepository(
                expenseDao = database.expenseDao(),
                transferDao = database.transferDao(),
                budgetDao = database.budgetDao(),
                recurringExpenseDao = database.recurringExpenseDao(),
                challengeDao = database.challengeDao(),
                achievementDao = database.achievementDao(),
                savingsAccountDao = database.savingsAccountDao(),
                savingsAccountTransactionDao = database.savingsAccountTransactionDao(),
                smartVaultDao = database.smartVaultDao(),
                mainAccountDao = database.mainAccountDao(),
                frozenFundDao = database.frozenFundDao(),
                allocationHistoryDao = database.allocationHistoryDao(),
                storeDao = database.storeDao(),
                paymentMethodDao = database.paymentMethodDao(),
                creditCardPaymentDao = database.creditCardPaymentDao(),
                expenseItemDao = database.expenseItemDao(),
                expenseRefundDao = database.expenseRefundDao(),
                assetDao = database.assetDao(),
                assetExpenseLinkDao = database.assetExpenseLinkDao(),
                wishlistDao = database.wishlistDao(),
                wishlistSavingsDao = database.wishlistSavingsDao(),
                pendingVariableRecurringExpenseDao = database.pendingVariableRecurringExpenseDao(),
                recurringExpensePaidDao = database.recurringExpensePaidDao(),
                preferencesRepository = preferencesRepository,
                database = database
            )

            val now = LocalDateTime.now()
            val today = now.toLocalDate()

            val scheduleEntities = database.smartVaultDao().getEnabledSchedules()
            val schedules = scheduleEntities.map { it.toDomain() }

            var executedCount = 0
            var executedTotal = 0.0

            schedules.forEach { schedule ->
                if (!isScheduleDue(schedule, today, now)) return@forEach

                val vault = savingsRepository.getSmartVaultById(schedule.vaultId)
                val amount = computeTransferAmount(schedule, savingsRepository, vault)
                if (amount <= 0.0) return@forEach

                val vaultName = vault?.name ?: "Vault ${schedule.vaultId}"
                val note = buildTransferNote(schedule, amount, vaultName)

                if (schedule.notifyBefore) {
                    NotificationHelper.showVaultScheduleNotificationBefore(
                        context = applicationContext,
                        vaultName = vaultName,
                        amount = amount,
                        schedule = schedule
                    )
                }

                val executed = savingsRepository.executeVaultTransfer(schedule, amount, now, note)
                if (executed) {
                    executedCount += 1
                    executedTotal += amount

                    val nextRun = computeNextRun(schedule, today)
                    savingsRepository.recordScheduleExecution(schedule.id, schedule.vaultId, amount, now, nextRun)

                    if (schedule.notifyAfter) {
                        NotificationHelper.showVaultScheduleNotificationAfter(
                            context = applicationContext,
                            vaultName = vaultName,
                            amount = amount,
                            schedule = schedule
                        )
                    }
                } else {
                    if (schedule.notifyOnFailure) {
                        NotificationHelper.showVaultScheduleNotificationFailure(
                            context = applicationContext,
                            vaultName = vaultName,
                            amount = amount,
                            schedule = schedule
                        )
                    }
                }
            }

            if (executedCount > 0) {
                NotificationHelper.showVaultScheduleSummary(
                    applicationContext,
                    executedCount,
                    executedTotal
                )
            }

            processRecurringExpenses(database, savingsRepository, preferencesRepository, today)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun processRecurringExpenses(
        database: SparelyDatabase,
        repository: SavingsRepository,
        preferencesRepository: com.example.sparely.data.preferences.UserPreferencesRepository,
        today: LocalDate
    ) {
        val now = LocalDateTime.now()
        val recurringEntities = database.recurringExpenseDao().getAll()
        val dueRecurring = recurringEntities.mapNotNull { entityRow ->
            if (!entityRow.isActive) return@mapNotNull null

            val domain = entityRow.toDomain()
            val nextRun = domain.nextRunAt ?: domain.startDate.atTime(9, 0)

            // It's due if current time is on or after nextRun
            if (!now.isBefore(nextRun)) {
                entityRow
            } else {
                null
            }
        }

        if (dueRecurring.isEmpty()) return

        // Fetch settings and vaults once for all due recurring expenses
        val settings = preferencesRepository.getSettingsSnapshot()
        val vaults = repository.observeSmartVaults().first()

        dueRecurring.forEach { re ->
            val domain = re.toDomain()
            val executeAuto = re.executeAutomatically

            // Special handling for variable amount + auto-execute
            if (re.isVariableAmount && executeAuto) {
                // Send notification and create pending entry instead of directly processing
                val predictedAmount = domain.predictNextAmount()
                NotificationHelper.showVariableRecurringExpenseNotification(
                    context = applicationContext,
                    recurringExpenseId = re.id,
                    recurringExpenseName = re.description,
                    predictedAmount = predictedAmount
                )

                // Insert into pending table for user to confirm
                database.pendingVariableRecurringExpenseDao().insert(
                    com.example.sparely.data.local.PendingVariableRecurringExpenseEntity(
                        recurringExpenseId = re.id,
                        predictedAmount = predictedAmount,
                        createdAt = now
                    )
                )
            } else if (executeAuto) {
                // Regular non-variable auto-execute
                repository.processRecurringExpensePayment(
                    recurringEntity = re,
                    processDate = today,
                    settings = settings,
                    vaults = vaults
                )
                repository.updateRecurringExpenseProcessed(re.id, today)
            } else {
                // Not auto-execute - create frozen fund
                repository.insertFrozenFund(
                    pendingType = "RECURRING_PAYMENT",
                    pendingId = re.id,
                    amount = re.amount,
                    description = "Pending recurring payment: ${re.description}"
                )
                repository.updateRecurringExpenseProcessed(re.id, today)
            }
        }
    }

    private fun isScheduleDue(schedule: VaultSchedule, today: LocalDate, now: LocalDateTime): Boolean {
        if (!schedule.enabled) return false
        val lastRunDate = schedule.lastRunAt?.toLocalDate()
        if (lastRunDate != null && lastRunDate == today) return false

        schedule.nextRunAt?.let { nextRun ->
            return !nextRun.isAfter(now)
        }

        return when (schedule.type) {
            VaultScheduleType.SPECIFIC_DATE -> {
                val target = schedule.dateValue ?: return false
                if (!schedule.repeatAnnually) {
                    target == today && lastRunDate == null
                } else {
                    val candidate = adjustDateForYear(target, today.year)
                    val matchesToday = candidate == today
                    matchesToday && (lastRunDate == null || lastRunDate.year < today.year)
                }
            }
            VaultScheduleType.DAY_OF_MONTH -> {
                val desiredDay = schedule.dayOfMonth ?: today.dayOfMonth
                val monthLength = YearMonth.from(today).lengthOfMonth()
                val actualDay = min(desiredDay, monthLength)
                if (today.dayOfMonth != actualDay) return false
                if (lastRunDate == null) return true
                lastRunDate.year != today.year || lastRunDate.monthValue != today.monthValue
            }
            VaultScheduleType.DAY_OF_WEEK -> {
                val desired = schedule.dayOfWeek ?: today.dayOfWeek.value
                val desiredDow = DayOfWeek.of(((desired - 1) % 7) + 1)
                if (today.dayOfWeek != desiredDow) return false
                val interval = schedule.weekInterval?.takeIf { it > 0 } ?: 1
                if (lastRunDate == null) return true
                val weeksSince = ChronoUnit.WEEKS.between(lastRunDate, today)
                weeksSince >= interval
            }
            VaultScheduleType.DAILY -> {
                if (lastRunDate == null) return true
                lastRunDate < today
            }
            VaultScheduleType.QUARTERLY -> {
                val desiredDay = schedule.dayOfMonth ?: today.dayOfMonth
                val monthLength = YearMonth.from(today).lengthOfMonth()
                val actualDay = min(desiredDay, monthLength)
                if (today.dayOfMonth != actualDay) return false
                if (lastRunDate == null) return true
                ChronoUnit.MONTHS.between(lastRunDate, today) >= 3
            }
        }
    }

    private fun computeNextRun(schedule: VaultSchedule, runDate: LocalDate): LocalDateTime? {
        val time = schedule.nextRunAt?.toLocalTime() ?: LocalTime.of(9, 0)
        return when (schedule.type) {
            VaultScheduleType.SPECIFIC_DATE -> {
                if (!schedule.repeatAnnually) return null
                val base = schedule.dateValue ?: return null
                val nextYearCandidate = adjustDateForYear(base, runDate.year)
                val nextDate = if (nextYearCandidate.isAfter(runDate)) {
                    nextYearCandidate
                } else {
                    adjustDateForYear(base, runDate.year + 1)
                }
                nextDate.atTime(time)
            }
            VaultScheduleType.DAY_OF_MONTH -> {
                val desiredDay = schedule.dayOfMonth ?: runDate.dayOfMonth
                val nextMonth = YearMonth.from(runDate).plusMonths(1)
                val day = min(desiredDay, nextMonth.lengthOfMonth())
                LocalDate.of(nextMonth.year, nextMonth.month, day).atTime(time)
            }
            VaultScheduleType.DAY_OF_WEEK -> {
                val interval = schedule.weekInterval?.takeIf { it > 0 } ?: 1
                val desired = schedule.dayOfWeek ?: runDate.dayOfWeek.value
                val desiredDow = DayOfWeek.of(((desired - 1) % 7) + 1)
                val nextBase = runDate.plusWeeks(interval.toLong())
                val nextDate = nextBase.with(TemporalAdjusters.nextOrSame(desiredDow))
                nextDate.atTime(time)
            }
            VaultScheduleType.DAILY -> {
                runDate.plusDays(1).atTime(time)
            }
            VaultScheduleType.QUARTERLY -> {
                runDate.plusMonths(3).atTime(time)
            }
        }
    }

    private fun adjustDateForYear(base: LocalDate, year: Int): LocalDate {
        val yearMonth = YearMonth.of(year, base.monthValue)
        val day = min(base.dayOfMonth, yearMonth.lengthOfMonth())
        return LocalDate.of(year, base.monthValue, day)
    }

    private suspend fun computeTransferAmount(
        schedule: VaultSchedule,
        repository: SavingsRepository,
        vault: SmartVault?
    ): Double {
        schedule.amount?.takeIf { it > 0.0 }?.let { return it.roundCurrency() }

        val percent = schedule.percentage ?: return 0.0
        val base = when (schedule.direction) {
            VaultTransferDirection.MAIN_TO_VAULT -> repository.getLatestMainAccountBalance()
            VaultTransferDirection.VAULT_TO_MAIN -> vault?.currentBalance ?: 0.0
        }
        if (base <= 0.0) return 0.0
        return (base * percent).roundCurrency()
    }

    // Delegates to the shared BigDecimal rounding: NaN/Infinity become 0 instead of throwing,
    // and large amounts no longer overflow Int cents.
    private fun Double.roundCurrency(): Double = roundToTwoDecimals()

    private fun buildTransferNote(schedule: VaultSchedule, amount: Double, vaultName: String): String {
        val formattedAmount = NotificationHelper.formatAmount(amount)
        val descriptor = when (schedule.type) {
            VaultScheduleType.SPECIFIC_DATE -> "specific date"
            VaultScheduleType.DAY_OF_MONTH -> "day-of-month"
            VaultScheduleType.DAY_OF_WEEK -> "weekly"
            VaultScheduleType.DAILY -> "daily"
            VaultScheduleType.QUARTERLY -> "quarterly"
        }
        val direction = when (schedule.direction) {
            VaultTransferDirection.MAIN_TO_VAULT -> "to"
            VaultTransferDirection.VAULT_TO_MAIN -> "from"
        }
        return "Scheduled $descriptor transfer $direction $vaultName ($formattedAmount)"
    }
}
