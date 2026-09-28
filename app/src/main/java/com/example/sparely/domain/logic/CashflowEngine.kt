package com.example.sparely.domain.logic

import com.example.sparely.domain.model.nextOccurrenceAfter
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.Necessity
import com.example.sparely.domain.model.PayScheduleSettings
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.RecurringFrequency
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Engine for cashflow forecasting and "safe to spend" calculations.
 * Provides projections based on expected income, recurring expenses, and spending patterns.
 */
object CashflowEngine {

    // === Data Classes ===

    data class CashflowForecast(
        val currentBalance: Double,
        val safeToSpend: Double, // Available after reserving for upcoming obligations
        val projectedBalance30Days: Double,
        val dailyBurnRate: Double,
        val runwayDays: Int, // Days until balance hits zero
        val essentialObligations30Days: Double = 0.0,
        val discretionaryObligations30Days: Double = 0.0,
        val essentialsCoveredDays: Int? = null, // Days until money can't cover essential bills; null = covered for the horizon
        val lowBalanceWarning: LowBalanceWarning?,
        val upcomingObligations: List<UpcomingObligation>,
        val weeklyProjections: List<WeeklyProjection>
    )

    data class LowBalanceWarning(
        val daysUntilLowBalance: Int,
        val projectedLowBalance: Double,
        val threshold: Double,
        val severity: WarningSeverity
    )

    enum class WarningSeverity { INFO, WARNING, CRITICAL }

    data class UpcomingObligation(
        val description: String,
        val amount: Double,
        val dueDate: LocalDate,
        val daysUntilDue: Int,
        val isRecurring: Boolean,
        val necessity: Necessity = Necessity.IMPORTANT
    )

    data class WeeklyProjection(
        val weekStartDate: LocalDate,
        val projectedSpending: Double,
        val projectedIncome: Double,
        val projectedEndBalance: Double
    )

    data class ForecastInput(
        val currentBalance: Double,
        val totalHisaBalance: Double = 0.0,
        val minMainAccountBalance: Double = 0.0,
        val recentExpenses: List<Expense>,
        val recurringExpenses: List<RecurringExpense>,
        val expectedMonthlyIncome: Double,
        val nextPayDate: LocalDate? = null,
        val nextPayAmount: Double? = null,
        val paySchedule: PayScheduleSettings? = null,
        val lowBalanceThreshold: Double = 100.0,
        val today: LocalDate = LocalDate.now()
    )

    // === Main Forecast Function ===

    fun forecast(input: ForecastInput): CashflowForecast {
        val dailyBurn = calculateDailyBurnRate(input.recentExpenses, input.today)
        val upcomingObligations = calculateUpcomingObligations(input.recurringExpenses, input.today)
        val totalObligations30Days = upcomingObligations
            .filter { it.daysUntilDue <= 30 }
            .sumOf { it.amount }

        // Total Liquid Money = Main Account + HISA
        val totalLiquidMoney = input.currentBalance + input.totalHisaBalance
        
        // Reserved Money = Minimum Balance setting
        val reservedMoney = input.minMainAccountBalance

        // Safe to spend = Total Liquid - Upcoming Obligations - Buffer - Reserved
        // We use a buffer on the MAIN account specifically to avoid overdrafts there, 
        // but for "Safe to Spend" global view, we consider HISA as backup.
        // However, let's keep the buffer logic simpler: 10% of TOTAL liquid.
        val buffer = (totalLiquidMoney * 0.10).coerceIn(50.0, 500.0) 
        
        val safeToSpend = (totalLiquidMoney - totalObligations30Days - buffer - reservedMoney).coerceAtLeast(0.0)

        // Calculate expected income in next 30 days
        val incomeIn30Days = calculateExpectedIncome(
            monthlyIncome = input.expectedMonthlyIncome,
            nextPayDate = input.nextPayDate,
            nextPayAmount = input.nextPayAmount,
            paySchedule = input.paySchedule,
            today = input.today
        )

        // Project balance in 30 days (Total Liquid)
        val projectedSpending30Days = dailyBurn * 30
        val projectedBalance30Days = totalLiquidMoney + incomeIn30Days - projectedSpending30Days - totalObligations30Days

        // Calculate runway using TOTAL liquid money
        val effectiveDailyBurn = if (incomeIn30Days > 0) {
            val dailyIncome = incomeIn30Days / 30.0
            (dailyBurn - dailyIncome).coerceAtLeast(0.0)
        } else {
            dailyBurn
        }
        val runwayDays = if (effectiveDailyBurn > 0) {
            // An overdrawn account has no runway, not a negative one.
            (totalLiquidMoney / effectiveDailyBurn).toInt().coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }

        // Check for low balance warning
        // We use Main Account balance specifically for "Low Main Account Balance" warning if we wanted strictness,
        // but the user wants "Total Usable Money". 
        // However, bills behave differently. Usually bills come out of Main.
        // If Main < 0, it's a problem even if HISA > 0 (unless auto-transfer).
        // For now, let's use Total Liquid for the "General Health" low balance warning, 
        // assuming the user moves money if needed.
        val lowBalanceWarning = detectLowBalanceWarning(
            currentBalance = totalLiquidMoney,
            dailyBurn = dailyBurn,
            incomeIn30Days = incomeIn30Days,
            upcomingObligations = upcomingObligations,
            threshold = input.lowBalanceThreshold + reservedMoney, // Warn if we dip into reserved
            today = input.today
        )

        // Build weekly projections
        val weeklyProjections = buildWeeklyProjections(
            currentBalance = totalLiquidMoney,
            dailyBurn = dailyBurn,
            expectedMonthlyIncome = input.expectedMonthlyIncome,
            nextPayDate = input.nextPayDate,
            nextPayAmount = input.nextPayAmount,
            paySchedule = input.paySchedule,
            upcomingObligations = upcomingObligations,
            today = input.today
        )

        return CashflowForecast(
            currentBalance = totalLiquidMoney, // Includes main + HISA for consistent graph display
            safeToSpend = safeToSpend,
            projectedBalance30Days = projectedBalance30Days,
            dailyBurnRate = dailyBurn,
            runwayDays = runwayDays,
            essentialObligations30Days = upcomingObligations
                .filter { it.necessity == Necessity.ESSENTIAL }.sumOf { it.amount },
            discretionaryObligations30Days = upcomingObligations
                .filter { it.necessity == Necessity.DISCRETIONARY }.sumOf { it.amount },
            essentialsCoveredDays = firstDayEssentialsUncovered(
                liquid = totalLiquidMoney,
                obligations = upcomingObligations,
                payDates = projectPayDates(input.paySchedule, input.nextPayDate, input.nextPayAmount, input.today, input.today.plusDays(30)),
                today = input.today
            ),
            lowBalanceWarning = lowBalanceWarning,
            upcomingObligations = upcomingObligations,
            weeklyProjections = weeklyProjections
        )
    }

    // === Helper Functions ===

    /**
     * Discretionary burn only: recurring-logged expenses are excluded because they're projected
     * separately as upcoming obligations (counting both would double count them). With 2+ completed
     * months of history we use the long-history baseline; otherwise the last 30 days.
     */
    private fun calculateDailyBurnRate(allExpenses: List<Expense>, today: LocalDate): Double {
        val expenses = allExpenses.filter { !it.countsAsRecurring }
        val baseline = ProjectionMath.historyBaseline(expenses, today)
        if (baseline.hasEnoughHistory && ProjectionMath.historyBeatsLinear(allExpenses, today)) {
            return baseline.discretionaryDailyRate
        }

        val cutoff = today.minusDays(30)
        val recentExpenses = expenses.filter { !it.date.isBefore(cutoff) }

        if (recentExpenses.isEmpty()) return 0.0

        // Filter out one-time large expenses to get accurate burn rate
        val uniqueExpenses = SmartInsightEngine.detectUniqueExpenses(recentExpenses)
        val uniqueExpenseIds = uniqueExpenses.map { it.expense.id }.toSet()
        val filteredExpenses = recentExpenses.filter { it.id !in uniqueExpenseIds }

        val totalSpent = filteredExpenses.sumOf { it.amount }
        val daysOfData = ChronoUnit.DAYS.between(
            filteredExpenses.minOfOrNull { it.date } ?: today,
            today
        ).toInt().coerceAtLeast(1)

        return if (filteredExpenses.isNotEmpty()) totalSpent / daysOfData else 0.0
    }

    private fun calculateUpcomingObligations(
        recurring: List<RecurringExpense>,
        today: LocalDate
    ): List<UpcomingObligation> {
        val obligations = mutableListOf<UpcomingObligation>()
        val window = 30 // Look ahead 30 days

        for (expense in recurring.filter { it.isActive }) {
            val nextDue = calculateNextDueDate(expense, today) ?: continue
            val daysUntil = ChronoUnit.DAYS.between(today, nextDue).toInt()

            if (daysUntil in 0..window) {
                obligations.add(
                    UpcomingObligation(
                        description = expense.description,
                        amount = expense.amount,
                        dueDate = nextDue,
                        daysUntilDue = daysUntil,
                        isRecurring = true,
                        necessity = expense.necessity
                    )
                )
            }
        }

        return obligations.sortedBy { it.daysUntilDue }
    }

    private fun calculateNextDueDate(expense: RecurringExpense, today: LocalDate): LocalDate? {
        // The scheduler's own next run date is the source of truth when it is still upcoming.
        expense.nextRunAt?.toLocalDate()?.takeIf { !it.isBefore(today) }?.let { scheduled ->
            return expense.endDate?.let { end -> scheduled.takeIf { !it.isAfter(end) } } ?: scheduled
        }
        val baseDate = expense.lastProcessedDate ?: expense.startDate.minusDays(1)
        var nextDue = expense.nextOccurrenceAfter(baseDate)
        // Jump straight to the first occurrence on or after today (no iteration cap that could
        // drop long-running daily/weekly schedules).
        if (nextDue.isBefore(today)) {
            nextDue = expense.nextOccurrenceAfter(today.minusDays(1))
        }
        expense.endDate?.let { end -> if (nextDue.isAfter(end)) return null }
        return nextDue
    }


    private fun calculateExpectedIncome(
        monthlyIncome: Double,
        nextPayDate: LocalDate?,
        nextPayAmount: Double?,
        paySchedule: PayScheduleSettings?,
        today: LocalDate
    ): Double {
        val payDates = projectPayDates(paySchedule, nextPayDate, nextPayAmount, today, today.plusDays(30))
        if (payDates.isNotEmpty()) {
            return payDates.sumOf { it.second }
        }

        // Fall back to estimated monthly income
        return monthlyIncome
    }

    /**
     * Projects every paycheck landing in [rangeStart, rangeEndExclusive), using the real pay
     * cadence from [paySchedule] (via [PayScheduleCalculator]) when available. Without a schedule,
     * only the single known [nextPayDate]/[nextPayAmount] is considered - we don't guess at a
     * cadence (e.g. assuming biweekly) when we don't actually know it.
     */
    private fun projectPayDates(
        paySchedule: PayScheduleSettings?,
        nextPayDate: LocalDate?,
        nextPayAmount: Double?,
        rangeStart: LocalDate,
        rangeEndExclusive: LocalDate
    ): List<Pair<LocalDate, Double>> {
        val firstPayDate = nextPayDate ?: return emptyList()
        val firstPayAmount = nextPayAmount ?: return emptyList()

        var date = firstPayDate
        var amount = firstPayAmount
        var iterations = 0

        // Advance past a stale pay date using the real schedule, if we have one.
        while (date.isBefore(rangeStart)) {
            if (paySchedule == null || iterations >= 24) return emptyList()
            date = PayScheduleCalculator.computeNextPayDate(paySchedule, date) ?: return emptyList()
            amount = paySchedule.defaultNetPay.takeIf { it > 0 } ?: amount
            iterations++
        }

        val dates = mutableListOf<Pair<LocalDate, Double>>()
        while (date.isBefore(rangeEndExclusive) && iterations < 48) {
            dates.add(date to amount)
            if (paySchedule == null) break // no cadence to project a second paycheck from
            date = PayScheduleCalculator.computeNextPayDate(paySchedule, date) ?: break
            amount = paySchedule.defaultNetPay.takeIf { it > 0 } ?: amount
            iterations++
        }
        return dates
    }

    /**
     * Assumes all discretionary spending stops, so only essential bills draw down [liquid] while
     * paychecks add to it. Returns the first day money can't cover an essential bill, or null.
     */
    private fun firstDayEssentialsUncovered(
        liquid: Double,
        obligations: List<UpcomingObligation>,
        payDates: List<Pair<LocalDate, Double>>,
        today: LocalDate
    ): Int? {
        var balance = liquid
        for (day in 0..30) {
            val date = today.plusDays(day.toLong())
            balance += payDates.filter { it.first == date }.sumOf { it.second }
            balance -= obligations
                .filter { it.necessity == Necessity.ESSENTIAL && it.dueDate == date }
                .sumOf { it.amount }
            if (balance < 0) return day
        }
        return null
    }

    private fun detectLowBalanceWarning(
        currentBalance: Double,
        dailyBurn: Double,
        incomeIn30Days: Double,
        upcomingObligations: List<UpcomingObligation>,
        threshold: Double,
        today: LocalDate
    ): LowBalanceWarning? {
        if (dailyBurn <= 0) return null

        // Simulate day-by-day balance
        var balance = currentBalance
        val dailyIncome = incomeIn30Days / 30.0
        var firstLowDay: Int? = null
        var lowestBalance = currentBalance

        for (day in 1..30) {
            balance -= dailyBurn
            balance += dailyIncome

            // Deduct obligations on their due days
            val dayDate = today.plusDays(day.toLong())
            val obligationsToday = upcomingObligations
                .filter { it.dueDate == dayDate }
                .sumOf { it.amount }
            balance -= obligationsToday

            if (balance < lowestBalance) {
                lowestBalance = balance
            }

            if (balance < threshold && firstLowDay == null) {
                firstLowDay = day
            }
        }

        if (firstLowDay != null) {
            val severity = when {
                firstLowDay <= 7 -> WarningSeverity.CRITICAL
                firstLowDay <= 14 -> WarningSeverity.WARNING
                else -> WarningSeverity.INFO
            }

            return LowBalanceWarning(
                daysUntilLowBalance = firstLowDay,
                projectedLowBalance = lowestBalance,
                threshold = threshold,
                severity = severity
            )
        }

        return null
    }

    private fun buildWeeklyProjections(
        currentBalance: Double,
        dailyBurn: Double,
        expectedMonthlyIncome: Double,
        nextPayDate: LocalDate?,
        nextPayAmount: Double?,
        paySchedule: PayScheduleSettings?,
        upcomingObligations: List<UpcomingObligation>,
        today: LocalDate
    ): List<WeeklyProjection> {
        val projections = mutableListOf<WeeklyProjection>()
        var runningBalance = currentBalance
        val weeklyBurn = dailyBurn * 7
        val weeklyIncome = expectedMonthlyIncome / 4.33 // Fallback when no real schedule is known

        // Same pay-date projection used by calculateExpectedIncome, so the 4-week chart and the
        // 30-day "safe to spend" figure agree on how many paychecks land in the horizon.
        val payDates = projectPayDates(paySchedule, nextPayDate, nextPayAmount, today, today.plusDays(28))

        for (weekNum in 0..3) { // 4 weeks ahead
            val weekStart = today.plusDays((weekNum * 7).toLong())
            val weekEnd = weekStart.plusDays(7)

            // Calculate obligations for this week
            val weekObligations = upcomingObligations
                .filter { it.dueDate in weekStart..weekEnd }
                .sumOf { it.amount }

            val payThisWeek = if (payDates.isNotEmpty()) {
                payDates.filter { it.first >= weekStart && it.first < weekEnd }.sumOf { it.second }
            } else {
                weeklyIncome
            }

            val projectedSpending = weeklyBurn + weekObligations
            runningBalance = runningBalance + payThisWeek - projectedSpending

            projections.add(
                WeeklyProjection(
                    weekStartDate = weekStart,
                    projectedSpending = projectedSpending,
                    projectedIncome = payThisWeek,
                    projectedEndBalance = runningBalance
                )
            )
        }

        return projections
    }

    // === Utility Functions ===

    /**
     * Calculate the "safe to spend" amount considering upcoming obligations
     */
    fun calculateSafeToSpend(
        currentBalance: Double,
        recurringExpenses: List<RecurringExpense>,
        bufferPercentage: Double = 0.15,
        daysAhead: Int = 14,
        today: LocalDate = LocalDate.now()
    ): Double {
        val obligations = calculateUpcomingObligations(recurringExpenses, today)
            .filter { it.daysUntilDue <= daysAhead }
            .sumOf { it.amount }

        val buffer = currentBalance * bufferPercentage
        return (currentBalance - obligations - buffer).coerceAtLeast(0.0)
    }
}
