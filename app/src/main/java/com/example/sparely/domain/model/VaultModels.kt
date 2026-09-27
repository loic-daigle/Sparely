package com.example.sparely.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Domain models backing the Smart Vault system.
 */
data class SmartVault(
    val id: Long = 0L,
    val name: String,
    val targetAmount: Double,
    val currentBalance: Double = 0.0,
    val targetDate: LocalDate? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val monthlyNeed: Double? = null,
    val priorityWeight: Double = 1.0,
    val autoSaveEnabled: Boolean = true,
    val allowAutoIncome: Boolean = true,
    val priority: VaultPriority = VaultPriority.MEDIUM,
    val type: VaultType = VaultType.SHORT_TERM,
    val interestRate: Double? = null,
    val allocationMode: VaultAllocationMode = VaultAllocationMode.DYNAMIC_AUTO,
    val manualAllocationPercent: Double? = null,
    val nextExpectedContribution: Double? = null,
    val lastContributionDate: LocalDate? = null,
    val savingTaxRateOverride: Double? = null,
    val archived: Boolean = false,
    // Account information for linking vault to real-world account within the main account
    val accountType: AccountType? = null,
    val accountNumber: String? = null,
    val accountNotes: String? = null,
    val createdAt: LocalDate = LocalDate.now(),
    val defaultManualDepositDeductFromMain: Boolean = true,
    val defaultManualWithdrawalCreditMain: Boolean = true,
    val schedules: List<VaultSchedule> = emptyList(),
    val iconName: String? = null,
    // High-Yield Savings Account features
    val isHighYieldAccount: Boolean = false,
    val annualPercentageYield: Double? = null, // APY for high-yield accounts (e.g., 4.5 for 4.5%)
    val lastInterestCalculation: LocalDate? = null,
    val accruedInterest: Double = 0.0
) {
    val progressPercent: Double = if (targetAmount > 0) (currentBalance / targetAmount).coerceIn(0.0, 1.0) else 0.0
    
    /** Check if this is a high-yield savings vault */
    val isHighYieldSavings: Boolean 
        get() = type == VaultType.HIGH_YIELD_SAVINGS || isHighYieldAccount
    
    /** Get effective APY (uses vault APY or default 4.5%) */
    val effectiveApy: Double
        get() = annualPercentageYield ?: if (isHighYieldSavings) 4.5 else 0.0
}

/** Calculate projected annual interest earnings based on current balance and APY */
fun SmartVault.projectedAnnualInterest(): Double {
    if (effectiveApy <= 0) return 0.0
    return currentBalance * (effectiveApy / 100.0)
}

/** Calculate projected monthly interest earnings */
fun SmartVault.projectedMonthlyInterest(): Double {
    return projectedAnnualInterest() / 12.0
}

/** Calculate interest earned over a specific number of months */
fun SmartVault.projectedInterestOverMonths(months: Int): Double {
    return projectedMonthlyInterest() * months
}

/** Calculate what the balance would be after N months with compound interest (monthly compounding) */
fun SmartVault.balanceAfterMonths(months: Int, additionalMonthlyDeposit: Double = 0.0): Double {
    if (effectiveApy <= 0) return currentBalance + (additionalMonthlyDeposit * months)
    
    val monthlyRate = effectiveApy / 100.0 / 12.0
    var balance = currentBalance
    
    repeat(months) {
        balance += additionalMonthlyDeposit
        balance *= (1 + monthlyRate)
    }
    
    return balance
}



enum class VaultScheduleType {
    DAILY,
    SPECIFIC_DATE,
    DAY_OF_MONTH,
    DAY_OF_WEEK,
    QUARTERLY
}

enum class VaultTransferDirection {
    MAIN_TO_VAULT,
    VAULT_TO_MAIN
}

data class VaultSchedule(
    val id: Long = 0L,
    val vaultId: Long,
    val type: VaultScheduleType,
    val amount: Double? = null,
    val percentage: Double? = null,
    val direction: VaultTransferDirection = VaultTransferDirection.MAIN_TO_VAULT,
    val dateValue: LocalDate? = null,
    val repeatAnnually: Boolean = false,
    val dayOfMonth: Int? = null,
    val dayOfWeek: Int? = null,
    val weekInterval: Int? = null,
    val onlyIfBalanceAvailable: Boolean = true,
    val notifyBefore: Boolean = false,
    val notifyAfter: Boolean = false,
    val notifyOnFailure: Boolean = true,
    val nextRunAt: LocalDateTime? = null,
    val lastRunAt: LocalDateTime? = null,
    val enabled: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class VaultContribution(
    val id: Long = 0L,
    val vaultId: Long? = null,  // Nullable: null when this is an HISA transfer
    val amount: Double,
    val date: LocalDate = LocalDate.now(),
    val source: VaultContributionSource,
    val note: String? = null,
    val reconciled: Boolean = false,
    val relatedExpenseId: Long? = null,
    val savingsAccountId: Long? = null  // Non-null when this is an HISA transfer
) {
    /** Check if this contribution targets an HISA account rather than a vault */
    val isHisaTransfer: Boolean get() = savingsAccountId != null && vaultId == null
}

enum class VaultContributionSource {
    INCOME,
    SAVING_TAX,
    AUTO_DEPOSIT,
    MANUAL,
    TRANSFER,
    INTEREST
}

enum class VaultAdjustmentType {
    MANUAL_DEPOSIT,
    MANUAL_DEDUCTION,
    MANUAL_EDIT,
    AUTOMATIC_RECURRING_TRANSFER
}

data class VaultBalanceAdjustment(
    val id: Long = 0L,
    val vaultId: Long,
    val type: VaultAdjustmentType,
    val delta: Double,
    val resultingBalance: Double,
    val createdAt: Instant,
    val reason: String? = null
)

data class VaultProjection(
    val vaultId: Long,
    val projectedCompletionDate: LocalDate?,
    val monthlyContributionNeeded: Double,
    val riskFlag: VaultRiskFlag?
)

data class VaultRiskFlag(
    val type: VaultRiskType,
    val message: String
)

enum class VaultRiskType {
    BEHIND_SCHEDULE,
    TARGET_AT_RISK,
    COMPLETE
}

data class VaultArchivePrompt(
    val vaultId: Long,
    val vaultName: String,
    val expenseAmount: Double,
    val vaultBalanceBefore: Double,
    val vaultBalanceAfter: Double,
    val overflowToMainAccount: Double
)

sealed interface VaultHistoryItem {
    val id: Long
    val date: LocalDateTime
    val amount: Double
    val description: String?
}

data class HistoryContribution(
    val contribution: VaultContribution
) : VaultHistoryItem {
    override val id = contribution.id
    override val date: LocalDateTime = contribution.date.atStartOfDay()
    override val amount = contribution.amount
    override val description = contribution.note
}

data class HistoryAdjustment(
    val adjustment: VaultBalanceAdjustment
) : VaultHistoryItem {
    override val id = adjustment.id
    override val date: LocalDateTime = LocalDateTime.ofInstant(adjustment.createdAt, java.time.ZoneId.systemDefault())
    override val amount = adjustment.delta
    override val description = adjustment.reason
    val balanceAfter = adjustment.resultingBalance
}
