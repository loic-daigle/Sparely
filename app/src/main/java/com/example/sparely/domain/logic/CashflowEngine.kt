package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Expense
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
        val isRecurring: Boolean
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
            (totalLiquidMoney / effectiveDailyBurn).toInt()
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
            upcomingObligations = upcomingObligations,
            today = input.today
        )

        return CashflowForecast(
            currentBalance = totalLiquidMoney, // Includes main + HISA for consistent graph display
            safeToSpend = safeToSpend,
            projectedBalance30Days = projectedBalance30Days,
            dailyBurnRate = dailyBurn,
            runwayDays = runwayDays,
            lowBalanceWarning = lowBalanceWarning,
            upcomingObligations = upcomingObligations,
            weeklyProjections = weeklyProjections
        )
    }

    // === Helper Functions ===

    private fun calculateDailyBurnRate(expenses: List<Expense>, today: LocalDate): Double {
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
                        isRecurring = true
                    )
                )
            }
        }

        return obligations.sortedBy { it.daysUntilDue }
    }

    private fun calculateNextDueDate(expense: RecurringExpense, today: LocalDate): LocalDate? {
        val baseDate = expense.lastProcessedDate ?: expense.startDate.minusDays(1)
        var nextDue = addFrequencyInterval(baseDate, expense.frequency)

        // Advance until we find a future date
        repeat(100) { // Safety limit
            if (!nextDue.isBefore(today)) return nextDue
            nextDue = addFrequencyInterval(nextDue, expense.frequency)
        }

        return null
    }

    private fun addFrequencyInterval(date: LocalDate, frequency: RecurringFrequency): LocalDate {
        return when (frequency) {
            RecurringFrequency.DAILY -> date.plusDays(1)
            RecurringFrequency.WEEKLY -> date.plusWeeks(1)
            RecurringFrequency.BIWEEKLY -> date.plusWeeks(2)
            RecurringFrequency.MONTHLY -> date.plusMonths(1)
            RecurringFrequency.QUARTERLY -> date.plusMonths(3)
            RecurringFrequency.YEARLY -> date.plusYears(1)
        }
    }

    private fun calculateExpectedIncome(
        monthlyIncome: Double,
        nextPayDate: LocalDate?,
        nextPayAmount: Double?,
        today: LocalDate
    ): Double {
        // If we have a specific next pay date and amount, use that
        if (nextPayDate != null && nextPayAmount != null) {
            val daysUntilPay = ChronoUnit.DAYS.between(today, nextPayDate).toInt()
            if (daysUntilPay in 0..30) {
                // Estimate additional paychecks based on frequency
                // Assume biweekly for simplicity if we only have one date
                val additionalPaychecks = (30 - daysUntilPay) / 14
                return nextPayAmount * (1 + additionalPaychecks)
            }
        }

        // Fall back to estimated monthly income
        return monthlyIncome
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
        upcomingObligations: List<UpcomingObligation>,
        today: LocalDate
    ): List<WeeklyProjection> {
        val projections = mutableListOf<WeeklyProjection>()
        var runningBalance = currentBalance
        val weeklyBurn = dailyBurn * 7
        val weeklyIncome = expectedMonthlyIncome / 4.33 // Approximate weekly income

        for (weekNum in 0..3) { // 4 weeks ahead
            val weekStart = today.plusDays((weekNum * 7).toLong())
            val weekEnd = weekStart.plusDays(7)

            // Calculate obligations for this week
            val weekObligations = upcomingObligations
                .filter { it.dueDate in weekStart..weekEnd }
                .sumOf { it.amount }

            // Check if pay date falls in this week
            val payThisWeek = if (nextPayDate != null && nextPayAmount != null) {
                if (nextPayDate in weekStart..weekEnd) nextPayAmount else 0.0
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
