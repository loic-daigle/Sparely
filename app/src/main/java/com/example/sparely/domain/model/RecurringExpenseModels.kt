package com.example.sparely.domain.model

import java.time.LocalDate

/**
 * Represents a recurring expense (subscription, rent, etc.)
 * Contains the template for creating actual expenses when processed.
 */
data class RecurringExpense(
    val id: Long = 0,
    val description: String,
    val amount: Double,
    val category: ExpenseCategory,
    val frequency: RecurringFrequency,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val lastProcessedDate: LocalDate? = null,
    val isActive: Boolean = true,
    val autoLog: Boolean = true,
    // Controls whether the scheduled payment is executed automatically or created as pending
    val executeAutomatically: Boolean = false,
    val reminderDaysBefore: Int = 2,
    val merchantName: String? = null,
    val notes: String? = null,
    // Store/merchant for this recurring expense
    val storeId: Long? = null,
    // Payment method for this recurring expense
    val paymentMethodId: Long? = null,
    // Expense-related fields (same as ExpenseInput)
    val includesTax: Boolean = false,
    val deductFromMainAccount: Boolean = false,
    val deductedFromVaultId: Long? = null,
    val manualPercentages: SavingsPercentages? = null,
    // Variable amount support for bills like electricity, water, etc.
    val isVariableAmount: Boolean = false,
    val amountHistory: List<AmountHistoryEntry> = emptyList(),
    val estimatedAmount: Double? = null, // Predicted amount for next occurrence
    val nextRunAt: java.time.LocalDateTime? = null,
    val type: ExpenseType = ExpenseType.PRODUCT, // PRODUCT or SERVICE
    // Line items support (new for feature parity with expenses)
    val items: List<ExpenseItem> = emptyList(),
    // Asset allocations support (new for feature parity with expenses)
    val assetAllocations: Map<Long, Double> = emptyMap(), // assetId -> percentageAllocated
    // Explicit user choice; null means "derive from category"
    val necessityOverride: Necessity? = null
) {
    val necessity: Necessity get() = necessityOverride ?: category.defaultNecessity()
}

/**
 * Tracks historical amounts for variable recurring expenses.
 */
data class AmountHistoryEntry(
    val date: LocalDate,
    val amount: Double
)

/**
 * Frequency options for recurring expenses.
 */
enum class RecurringFrequency(val daysInterval: Int) {
    DAILY(1),
    WEEKLY(7),
    BIWEEKLY(14),
    MONTHLY(30),
    QUARTERLY(90),
    YEARLY(365)
}

/**
 * Returns the [index]-th occurrence of a schedule that started on [anchor].
 * Computed from the anchor (not by repeatedly adding to the previous date) so month-based
 * schedules keep their day of month: Jan 31 -> Feb 28 -> Mar 31, instead of drifting to the 28th.
 */
fun RecurringFrequency.occurrenceAt(anchor: LocalDate, index: Long): LocalDate = when (this) {
    RecurringFrequency.DAILY -> anchor.plusDays(index)
    RecurringFrequency.WEEKLY -> anchor.plusWeeks(index)
    RecurringFrequency.BIWEEKLY -> anchor.plusWeeks(2 * index)
    RecurringFrequency.MONTHLY -> anchor.plusMonths(index)
    RecurringFrequency.QUARTERLY -> anchor.plusMonths(3 * index)
    RecurringFrequency.YEARLY -> anchor.plusYears(index)
}

/**
 * First occurrence of the schedule anchored on [anchor] that falls strictly after [date].
 * If [date] is before the anchor, the anchor itself is returned.
 */
fun RecurringFrequency.nextOccurrenceAfter(anchor: LocalDate, date: LocalDate): LocalDate {
    if (date.isBefore(anchor)) return anchor
    val estimate = when (this) {
        RecurringFrequency.DAILY -> java.time.temporal.ChronoUnit.DAYS.between(anchor, date)
        RecurringFrequency.WEEKLY -> java.time.temporal.ChronoUnit.DAYS.between(anchor, date) / 7
        RecurringFrequency.BIWEEKLY -> java.time.temporal.ChronoUnit.DAYS.between(anchor, date) / 14
        RecurringFrequency.MONTHLY -> java.time.temporal.ChronoUnit.MONTHS.between(anchor, date)
        RecurringFrequency.QUARTERLY -> java.time.temporal.ChronoUnit.MONTHS.between(anchor, date) / 3
        RecurringFrequency.YEARLY -> java.time.temporal.ChronoUnit.YEARS.between(anchor, date)
    }
    var index = (estimate - 1).coerceAtLeast(0)
    var candidate = occurrenceAt(anchor, index)
    while (!candidate.isAfter(date)) {
        index++
        candidate = occurrenceAt(anchor, index)
    }
    return candidate
}

/**
 * How many times per month a payment of this frequency occurs on average (a year has 52 weeks,
 * 365 days). Every monthly-equivalent figure uses it, so screens and suggestions agree.
 */
val RecurringFrequency.perMonth: Double get() = when (this) {
    RecurringFrequency.DAILY -> 365.0 / 12.0
    RecurringFrequency.WEEKLY -> 52.0 / 12.0
    RecurringFrequency.BIWEEKLY -> 26.0 / 12.0
    RecurringFrequency.MONTHLY -> 1.0
    RecurringFrequency.QUARTERLY -> 1.0 / 3.0
    RecurringFrequency.YEARLY -> 1.0 / 12.0
}

/** Next scheduled date for this recurring expense strictly after [date], anchored on its start date. */
fun RecurringExpense.nextOccurrenceAfter(date: LocalDate): LocalDate =
    frequency.nextOccurrenceAfter(startDate, date)

/**
 * The date this recurring expense is next due, on or after [today], or null once it's past its end
 * date. Every screen uses this so they agree with each other and with the scheduler, which charges
 * on [RecurringExpense.nextRunAt].
 */
fun RecurringExpense.nextDueDate(today: LocalDate = LocalDate.now()): LocalDate? {
    // The scheduler's own next run date is the source of truth when it is still upcoming.
    nextRunAt?.toLocalDate()?.takeIf { !it.isBefore(today) }?.let { scheduled ->
        return endDate?.let { end -> scheduled.takeIf { !it.isAfter(end) } } ?: scheduled
    }
    val baseDate = lastProcessedDate ?: startDate.minusDays(1)
    var nextDue = nextOccurrenceAfter(baseDate)
    // Jump straight to the first occurrence on or after today (no iteration cap that could
    // drop long-running daily/weekly schedules).
    if (nextDue.isBefore(today)) {
        nextDue = nextOccurrenceAfter(today.minusDays(1))
    }
    endDate?.let { end -> if (nextDue.isAfter(end)) return null }
    return nextDue
}

/** Whether [nextRunAt] is a date the schedule (start date and frequency) actually lands on. */
fun RecurringExpense.isNextRunOnSchedule(): Boolean {
    val scheduled = nextRunAt?.toLocalDate() ?: return true
    return nextOccurrenceAfter(scheduled.minusDays(1)) == scheduled
}

/**
 * The next run date after the schedule changed (new start date or frequency): the first date of
 * the new schedule that is not in the past and not in the period already handled, so changing the
 * day of a bill neither skips this period's charge nor charges a period twice.
 */
fun RecurringExpense.rescheduledRunDate(today: LocalDate = LocalDate.now()): LocalDate {
    val handled = lastProcessedDate
    val earliest = if (handled == null) {
        // Like a new recurring expense: one starting today or earlier begins at its next date,
        // so a bill already entered by hand today isn't charged again
        if (startDate.isAfter(today)) startDate else today.plusDays(1)
    } else {
        maxOf(today, handled.plusDays((frequency.daysInterval / 2).toLong()))
    }
    return nextOccurrenceAfter(earliest.minusDays(1))
}

/**
 * Input for creating recurring expense.
 */
data class RecurringExpenseInput(
    val description: String,
    val amount: Double,
    val category: ExpenseCategory,
    val frequency: RecurringFrequency,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val autoLog: Boolean = true,
    val executeAutomatically: Boolean = false,
    val reminderDaysBefore: Int = 2,
    val notes: String? = null,
    // Store/merchant for this recurring expense
    val storeId: Long? = null,
    // Payment method for this recurring expense
    val paymentMethodId: Long? = null,
    // Expense-related fields (same as ExpenseInput)
    val includesTax: Boolean = false,
    val deductFromMainAccount: Boolean = false,
    val deductedFromVaultId: Long? = null,
    val manualPercentages: SavingsPercentages? = null,
    // Variable amount support
    val isVariableAmount: Boolean = false,
    val nextRunAt: java.time.LocalDateTime? = null,
    val type: ExpenseType = ExpenseType.PRODUCT, // PRODUCT or SERVICE
    // Line items support (new for feature parity with expenses)
    val items: List<ExpenseItem> = emptyList(),
    // Asset allocations support (new for feature parity with expenses)
    val assetAllocations: Map<Long, Double> = emptyMap(), // assetId -> percentageAllocated
    val necessityOverride: Necessity? = null
)

/**
 * Upcoming recurring expense notification.
 */
data class UpcomingRecurringExpense(
    val recurringExpense: RecurringExpense,
    val dueDate: LocalDate,
    val daysUntilDue: Int,
    val predictedAmount: Double? = null // For variable expenses
)

// === Extension Functions for Variable Amount Prediction ===

/**
 * Predict next amount for variable recurring expenses using weighted moving average.
 * More recent entries have higher weight.
 */
fun RecurringExpense.predictNextAmount(): Double {
    if (!isVariableAmount || amountHistory.isEmpty()) return amount
    if (amountHistory.size == 1) return amountHistory.first().amount

    // Sort by date (most recent last) and use weighted average
    val sorted = amountHistory.sortedBy { it.date }
    val weights = sorted.indices.map { (it + 1).toDouble() }
    val weightedSum = sorted.zip(weights) { entry, weight -> entry.amount * weight }.sum()
    return weightedSum / weights.sum()
}

/**
 * Get seasonal adjustment factor for this expense based on historical data.
 * Returns multiplier (1.0 = no change, 1.25 = 25% higher expected).
 */
fun RecurringExpense.getSeasonalFactor(month: Int = LocalDate.now().monthValue): Double {
    if (amountHistory.size < 6) return 1.0 // Need sufficient history

    // Group amounts by calendar month
    val monthlyAverages = amountHistory.groupBy { it.date.monthValue }
        .mapValues { (_, entries) -> entries.map { it.amount }.average() }

    if (monthlyAverages.size < 4) return 1.0 // Need data for multiple months

    val overallAverage = amountHistory.map { it.amount }.average()
    val monthAverage = monthlyAverages[month] ?: return 1.0

    return if (overallAverage > 0) monthAverage / overallAverage else 1.0
}

/**
 * Get seasonally-adjusted predicted amount.
 */
fun RecurringExpense.getSeasonallyAdjustedPrediction(month: Int = LocalDate.now().monthValue): Double {
    val basePrediction = predictNextAmount()
    val seasonalFactor = getSeasonalFactor(month)
    return basePrediction * seasonalFactor
}

/**
 * Calculate the average amount from history.
 */
fun RecurringExpense.averageHistoricalAmount(): Double {
    if (amountHistory.isEmpty()) return amount
    return amountHistory.map { it.amount }.average()
}

/**
 * Calculate the amount variance (standard deviation as percentage of mean).
 */
fun RecurringExpense.amountVariancePercent(): Double {
    if (amountHistory.size < 2) return 0.0
    val amounts = amountHistory.map { it.amount }
    val mean = amounts.average()
    if (mean <= 0) return 0.0
    val variance = amounts.map { (it - mean) * (it - mean) }.average()
    val stdDev = kotlin.math.sqrt(variance)
    return (stdDev / mean) * 100
}

