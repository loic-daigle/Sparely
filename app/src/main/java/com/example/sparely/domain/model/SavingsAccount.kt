package com.example.sparely.domain.model

import java.time.LocalDate

/**
 * Domain model for Savings Accounts (HISA, TFSA, etc.)
 * These are interest-bearing accounts that receive overflow funds,
 * distinct from goal-based SmartVaults.
 */
data class SavingsAccount(
    val id: Long = 0L,
    val name: String,
    val currentBalance: Double = 0.0,
    val annualPercentageYield: Double = 0.0, // APY in % (e.g., 4.5 for 4.5%)
    val totalInterestEarned: Double = 0.0,   // Lifetime interest earned
    val lastInterestEntryDate: LocalDate? = null,
    val accountNumber: String? = null,
    val institution: String? = null,
    val accountNotes: String? = null,
    val isMainOverflowAccount: Boolean = false,
    val iconName: String? = null,
    val createdAt: LocalDate = LocalDate.now(),
    val archived: Boolean = false,
    val productType: SavingsProductType = SavingsProductType.FLEXIBLE,
    val termMonths: Int? = null,
    val termStartDate: LocalDate? = null,
    val noticeDays: Int? = null,
    val gracePeriodDays: Int? = null,
    val anniversaryWindowDays: Int? = null,
    val minWithdrawalAmount: Double? = null,
    val minRemainingBalance: Double? = null,
    val earlyWithdrawalPenaltyDays: Int? = null
) {
    /**
     * Projected monthly interest based on current balance and APY.
     */
    val projectedMonthlyInterest: Double
        get() = currentBalance * (annualPercentageYield / 100.0 / 12.0)
    
    /**
     * Projected annual interest based on current balance and APY.
     */
    val projectedAnnualInterest: Double
        get() = currentBalance * (annualPercentageYield / 100.0)
}

enum class SavingsProductType {
    FLEXIBLE,
    TERM,
    NOTICE
}
