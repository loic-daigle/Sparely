package com.example.sparely.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * Domain model for a transaction on a Savings Account (HISA).
 */
data class SavingsAccountTransaction(
    val id: Long = 0L,
    val accountId: Long,
    val type: SavingsAccountTransactionDisplayType,
    val amount: Double,
    val balanceAfter: Double,
    val timestamp: LocalDateTime,
    val description: String
)

enum class SavingsAccountTransactionDisplayType {
    DEPOSIT,        // Manual deposit or transfer in from external
    WITHDRAWAL,     // Manual withdrawal or transfer out to external
    INTEREST,       // Interest credit
    TRANSFER_IN,    // Transfer from Main Account
    TRANSFER_OUT    // Transfer to Main Account
}

/**
 * Captures how each expense should be split into savings buckets.
 * Values are expressed as fractions (0.0 - 1.0) of the original expense amount.
 */
data class SavingsPercentages(
    val emergency: Double,
    val invest: Double,
    val `fun`: Double,
    val safeInvestmentSplit: Double = 0.65
) {
    val total: Double = emergency + invest + `fun`

    fun normalized(): SavingsPercentages {
        if (total <= 0.0) return copy(emergency = 0.15, invest = 0.05, `fun` = 0.05)
        val scale = 1.0 / total
        return SavingsPercentages(
            emergency = emergency * scale,
            invest = invest * scale,
            `fun` = `fun` * scale,
            safeInvestmentSplit = safeInvestmentSplit
        )
    }

    fun clamp(): SavingsPercentages {
        return SavingsPercentages(
            emergency = emergency.coerceIn(0.0, 1.0),
            invest = invest.coerceIn(0.0, 1.0),
            `fun` = `fun`.coerceIn(0.0, 1.0),
            safeInvestmentSplit = safeInvestmentSplit.coerceIn(0.0, 1.0)
        )
    }

    fun adjustWithinBudget(maxTotal: Double = 0.5): SavingsPercentages {
        val clamped = clamp()
        if (clamped.total <= maxTotal) return clamped
        val scale = maxTotal / clamped.total
        return SavingsPercentages(
            emergency = clamped.emergency * scale,
            invest = clamped.invest * scale,
            `fun` = clamped.`fun` * scale,
            safeInvestmentSplit = clamped.safeInvestmentSplit
        )
    }
}

/**
 * Detailed allocation in currency units for a specific expense.
 */
data class AllocationBreakdown(
    val emergencyAmount: Double,
    val investmentAmount: Double,
    val funAmount: Double,
    val safeInvestmentAmount: Double,
    val highRiskInvestmentAmount: Double
) {
    val totalSetAside: Double = emergencyAmount + investmentAmount + funAmount
}

data class PayScheduleSettings(
    val trackingMode: IncomeTrackingMode = IncomeTrackingMode.MANUAL_PER_PAYCHECK,
    val interval: PayInterval = PayInterval.WEEKLY,
    val defaultNetPay: Double = 0.0,
    val defaultSaveRate: Double = 0.15,
    val weeklyDayOfWeek: DayOfWeek = DayOfWeek.FRIDAY,
    val semiMonthlyDay1: Int = 1,
    val semiMonthlyDay2: Int = 15,
    val monthlyDay: Int = 1,
    val customDaysBetween: Int? = null,
    val nextPayDate: LocalDate? = null,
    val lastPayDate: LocalDate? = null,
    val lastPayAmount: Double = 0.0,
    val autoDistributeToVaults: Boolean = true,
    val autoCreatePendingTransfers: Boolean = true,
    val dynamicSaveRateEnabled: Boolean = true,
    val lastComputedSaveRate: Double? = null,
    // New income tracking fields
    val paycheckHistory: List<PaycheckEntry> = emptyList(),
    val incomeVariancePercent: Double = 0.0, // Standard deviation as percentage of mean
    val predictedNextAmount: Double? = null, // ML-based prediction
    val incomeStability: IncomeStability = IncomeStability.STABLE
) {
    /**
     * Calculate average paycheck amount from history
     */
    fun averagePayAmount(): Double {
        if (paycheckHistory.isEmpty()) return lastPayAmount.takeIf { it > 0 } ?: defaultNetPay
        return paycheckHistory.map { it.amount }.average()
    }

    /**
     * Calculate income variance (standard deviation / mean)
     */
    fun calculateVariance(): Double {
        if (paycheckHistory.size < 2) return 0.0
        val amounts = paycheckHistory.map { it.amount }
        val mean = amounts.average()
        if (mean <= 0) return 0.0
        val variance = amounts.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        return (stdDev / mean) * 100 // As percentage
    }

    /**
     * Determine income stability based on variance
     */
    fun determineStability(): IncomeStability {
        val variance = calculateVariance()
        return when {
            variance <= 5.0 -> IncomeStability.VERY_STABLE
            variance <= 15.0 -> IncomeStability.STABLE
            variance <= 30.0 -> IncomeStability.MODERATE
            else -> IncomeStability.VARIABLE
        }
    }

    /**
     * Predict next paycheck amount using weighted moving average
     */
    fun predictNextPaycheck(): Double {
        if (paycheckHistory.isEmpty()) return lastPayAmount.takeIf { it > 0 } ?: defaultNetPay
        if (paycheckHistory.size == 1) return paycheckHistory.first().amount

        // Weighted moving average - more recent paychecks have higher weight
        val weights = paycheckHistory.indices.map { i -> (i + 1).toDouble() }
        val weightedSum = paycheckHistory.zip(weights) { entry, weight -> entry.amount * weight }.sum()
        return weightedSum / weights.sum()
    }
}

data class PaycheckEntry(
    val date: LocalDate,
    val amount: Double,
    val source: String = "Primary", // Support for multiple income sources
    val notes: String? = null
)

enum class IncomeStability {
    VERY_STABLE, // <5% variance
    STABLE,      // 5-15% variance
    MODERATE,    // 15-30% variance
    VARIABLE     // >30% variance
}

/**
 * Represents a tailored emergency fund target for the user.
 */
data class EmergencyFundGoal(
    val targetMonths: Double,
    val targetAmount: Double,
    val shortfallAmount: Double,
    val recommendedMonthlyContribution: Double
) {
    val coverageRatio: Double
        get() = if (targetAmount <= 0.0) 1.0 else (targetAmount - shortfallAmount).coerceAtLeast(0.0) / targetAmount
}

/**
 * User-configurable settings stored in DataStore.
 */
data class SparelySettings(
    val defaultPercentages: SavingsPercentages = SavingsPercentages(
        emergency = 0.18,
        invest = 0.07,
        `fun` = 0.05
    ),
    val age: Int = 30,
    val educationStatus: EducationStatus = EducationStatus.OTHER,
    val employmentStatus: EmploymentStatus = EmploymentStatus.EMPLOYED,
    val livingSituation: LivingSituation = LivingSituation.OTHER,
    val occupation: String? = null,
    val mainAccountBalance: Double = 0.0,
    val savingsAccountBalance: Double = 0.0,
    val vaultsBalance: Double = 0.0,
    val subscriptionTotal: Double = 0.0,
    val riskLevel: RiskLevel = RiskLevel.BALANCED,
    val autoRecommendationsEnabled: Boolean = true,
    val includeTaxByDefault: Boolean = false,
    val monthlyIncome: Double = 4500.0,
    val paySchedule: PayScheduleSettings = PayScheduleSettings(),
    val remindersEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderFrequencyDays: Int = 1,
    val paydayReminderEnabled: Boolean = true,
    val paydayReminderHour: Int = 9,
    val paydayReminderMinute: Int = 0,
    val paydaySuggestAverageIncome: Boolean = true,
    val payHistoryCount: Int = 0,
    val payHistoryAverage: Double = 0.0,
    val joinedDate: LocalDate? = null,
    val hasDebts: Boolean = false,
    val currentEmergencyFund: Double = 0.0,
    val primaryGoal: String? = null,
    val displayName: String? = null,
    val birthday: LocalDate? = null,
    val smartAllocationMode: SmartAllocationMode = SmartAllocationMode.GUIDED,
    val targetSavingsRate: Double = 0.15,
    val savingTaxRate: Double = 0.04,
    val vaultAllocationMode: VaultAllocationMode = VaultAllocationMode.DYNAMIC_AUTO,
    val dynamicSavingTaxEnabled: Boolean = true,
    val lastComputedSavingTaxRate: Double? = null,
    val regionalSettings: RegionalSettings = RegionalSettings(),
    val brandfetchClientId: String? = null,
    val expenseHistoryRetention: ExpenseHistoryRetention = ExpenseHistoryRetention.INDEFINITELY,
    // Credit card reminder settings
    val creditCardReminderEnabled: Boolean = true,
    val creditCardReminderDaysBefore: Int = 3,
    val creditCardReminderHour: Int = 9,
    val promptPayOnCreditCardExpense: Boolean = false,
    // Credit card utilization-based notification
    val creditCardUtilizationAlertEnabled: Boolean = true,
    val creditCardUtilizationThreshold: Int = 30,  // Default 30%, common recommended threshold
    val biometricEnabled: Boolean = false,
    // Lets AI assistants read Sparely data through AppFunctions. Off until the user opts in.
    val aiAssistantAccessEnabled: Boolean = false,
    // Smart transfer notification threshold
    val smartTransferMinimumAmount: Double = 0.0,  // 0 = show all transfers
    // Auto backup settings
    val autoBackupEnabled: Boolean = false,
    val autoBackupFrequencyDays: Int = 7,  // Weekly by default
    val lastAutoBackupTimestamp: Long? = null,
    // Overflow account settings (HISA/High-Yield Savings)
    val mainOverflowAccountId: Long? = null,
    val minMainAccountBalance: Double = 0.0,
    // Asset creation settings
    val autoCreateAssetThreshold: Double = 0.0  // 0 = disabled
) {
    val isNewUser: Boolean
        get() = joinedDate?.let { java.time.temporal.ChronoUnit.DAYS.between(it, java.time.LocalDate.now()) < 7 } ?: true
    fun totalReservedFraction(): Double = defaultPercentages.total

    val effectiveAge: Int
        get() = birthday?.let {
            ChronoUnit.YEARS.between(it, LocalDate.now()).coerceAtLeast(0).toInt()
        } ?: age

    val isBirthdayToday: Boolean
        get() = birthday?.let {
            val today = LocalDate.now()
            it.month == today.month && it.dayOfMonth == today.dayOfMonth
        } ?: false

    fun safeInvestmentSplit(): Double = defaultPercentages.safeInvestmentSplit

    fun withPercentages(percentages: SavingsPercentages): SparelySettings = copy(
        defaultPercentages = percentages.adjustWithinBudget()
    )

    fun withSmartAllocationMode(mode: SmartAllocationMode): SparelySettings = copy(smartAllocationMode = mode)

    fun withTargetSavingsRate(rate: Double): SparelySettings = copy(targetSavingsRate = rate.coerceIn(0.0, 1.0))

    fun withSavingTaxRate(rate: Double): SparelySettings = copy(savingTaxRate = rate.coerceIn(0.0, 1.0))

    fun withVaultAllocationMode(mode: VaultAllocationMode): SparelySettings = copy(vaultAllocationMode = mode)

    fun withRisk(riskLevel: RiskLevel): SparelySettings = copy(riskLevel = riskLevel)

    fun withAge(age: Int): SparelySettings = copy(age = age.coerceIn(13, 100))

    fun withIncome(monthlyIncome: Double): SparelySettings = copy(
        monthlyIncome = max(0.0, monthlyIncome)
    )

    fun withPaySchedule(paySchedule: PayScheduleSettings): SparelySettings = copy(paySchedule = paySchedule)

    fun withEducationStatus(status: EducationStatus): SparelySettings = copy(educationStatus = status)

    fun withEmploymentStatus(status: EmploymentStatus): SparelySettings = copy(employmentStatus = status)

    fun withLivingSituation(situation: LivingSituation): SparelySettings = copy(livingSituation = situation)

    fun withOccupation(label: String?): SparelySettings = copy(occupation = label?.trim().takeUnless { it.isNullOrEmpty() })

    fun withMainAccountBalance(balance: Double): SparelySettings = copy(mainAccountBalance = max(0.0, balance))

    fun withSavingsAccountBalance(balance: Double): SparelySettings = copy(savingsAccountBalance = max(0.0, balance))

    fun withVaultsBalance(balance: Double): SparelySettings = copy(vaultsBalance = max(0.0, balance))

    fun withSubscriptionTotal(amount: Double): SparelySettings = copy(subscriptionTotal = max(0.0, amount))

    fun withHasDebts(value: Boolean): SparelySettings = copy(hasDebts = value)

    fun withEmergencyFundAmount(amount: Double): SparelySettings = copy(currentEmergencyFund = max(0.0, amount))

    fun withPrimaryGoal(goal: String?): SparelySettings = copy(primaryGoal = goal?.trim().takeUnless { it.isNullOrEmpty() })

    fun withDisplayName(name: String?): SparelySettings = copy(displayName = name?.trim().takeUnless { it.isNullOrEmpty() })

    fun withBirthday(date: LocalDate?): SparelySettings = copy(birthday = date)

    fun withPaydayReminder(
        enabled: Boolean = paydayReminderEnabled,
        hour: Int = paydayReminderHour,
        minute: Int = paydayReminderMinute,
        suggestAverage: Boolean = paydaySuggestAverageIncome
    ): SparelySettings = copy(
        paydayReminderEnabled = enabled,
        paydayReminderHour = hour,
        paydayReminderMinute = minute,
        paydaySuggestAverageIncome = suggestAverage
    )

    fun withExpenseHistoryRetention(retention: ExpenseHistoryRetention): SparelySettings = copy(expenseHistoryRetention = retention)
}


data class TransferReminderPreference(
    val enabled: Boolean,
    val frequencyDays: Int,
    val hourOfDay: Int
)

data class SmartSavingSummary(
    val targetSavingsRate: Double,
    val actualSavingsRate: Double,
    val allocationMode: SmartAllocationMode,
    val recommendedSplit: SavingsPercentages,
    val manualSplit: SavingsPercentages
)

data class DetectedRecurringTransaction(
    val description: String,
    val averageAmount: Double,
    val cadenceDays: Int,
    val lastOccurrence: LocalDate,
    val suggestedCategory: ExpenseCategory
)
