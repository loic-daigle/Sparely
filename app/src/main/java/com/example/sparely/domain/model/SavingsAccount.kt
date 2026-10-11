package com.example.sparely.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.min
import kotlin.math.pow

/**
 * Domain model for Savings Accounts (HISA, term deposits, notice accounts, TFSA, etc.)
 * These are interest-bearing accounts that receive overflow funds,
 * distinct from goal-based SmartVaults.
 *
 * Money in a TERM or NOTICE account is not freely spendable: [accessibleBalance] is what can be
 * moved back to the main account right now, and [lockedBalance] is the rest.
 */
data class SavingsAccount(
    val id: Long = 0L,
    val name: String,
    val currentBalance: Double = 0.0,
    val annualPercentageYield: Double = 0.0, // APY in % (e.g., 4.5 for 4.5%), i.e. the effective yearly rate
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
    val earlyWithdrawalPenaltyDays: Int? = null,
    /** NOTICE accounts: the day the user asked the bank to release funds. */
    val noticeRequestedDate: LocalDate? = null
) {
    // ---------------------------------------------------------------------------------------
    // Interest
    // ---------------------------------------------------------------------------------------

    /** Yearly growth factor minus one, e.g. 0.045 for 4.5% APY. */
    private val yearlyRate: Double get() = (annualPercentageYield / 100.0).coerceAtLeast(0.0)

    /**
     * Interest earned in one month on [currentBalance]. APY is already an effective yearly
     * rate, so the monthly rate is its 12th root, not APY / 12 (which would overstate it).
     */
    val projectedMonthlyInterest: Double
        get() = currentBalance * ((1.0 + yearlyRate).pow(1.0 / 12.0) - 1.0)

    /** Interest earned in one year on [currentBalance] (APY is the effective yearly rate). */
    val projectedAnnualInterest: Double
        get() = currentBalance * yearlyRate

    /** Interest [principal] would earn over [days] days at this account's APY. */
    fun interestOver(days: Long, principal: Double = currentBalance): Double {
        if (days <= 0 || principal <= 0.0) return 0.0
        return principal * ((1.0 + yearlyRate).pow(days / 365.0) - 1.0)
    }

    /**
     * Interest that has probably built up since interest was last recorded, to pre-fill the
     * "add interest" form. It is an estimate: banks differ in day-count and payout schedule.
     * Term deposits stop earning at maturity.
     */
    fun estimatedAccruedInterest(today: LocalDate = LocalDate.now()): Double {
        val from = lastInterestEntryDate ?: termStartDate ?: createdAt
        val until = maturityDate?.let { if (today.isAfter(it)) it else today } ?: today
        return interestOver(ChronoUnit.DAYS.between(from, until))
    }

    /** What the balance grows to by maturity if nothing is withdrawn. Null unless this is a configured term. */
    fun projectedMaturityValue(today: LocalDate = LocalDate.now()): Double? {
        val maturity = maturityDate ?: return null
        val from = if (today.isBefore(termStartDate ?: today)) termStartDate!! else today
        return currentBalance + interestOver(ChronoUnit.DAYS.between(from, maturity))
    }

    // ---------------------------------------------------------------------------------------
    // Lock rules
    // ---------------------------------------------------------------------------------------

    /** TERM accounts: the day the funds are released without penalty. */
    val maturityDate: LocalDate?
        get() {
            val start = termStartDate ?: return null
            val months = termMonths?.takeIf { it > 0 } ?: return null
            return if (productType == SavingsProductType.TERM) start.plusMonths(months.toLong()) else null
        }

    /** NOTICE accounts: the day a requested withdrawal becomes available. */
    val noticeReadyDate: LocalDate?
        get() {
            val requested = noticeRequestedDate ?: return null
            val days = noticeDays?.takeIf { it > 0 } ?: return null
            return if (productType == SavingsProductType.NOTICE) requested.plusDays(days.toLong()) else null
        }

    /** Whether a TERM account is inside a penalty-free window (the cooling-off period or an anniversary). */
    private fun inPenaltyFreeWindow(today: LocalDate): Boolean {
        val start = termStartDate ?: return false
        val maturity = maturityDate ?: return false
        val grace = gracePeriodDays?.takeIf { it > 0 }
        if (grace != null && !today.isBefore(start) && today.isBefore(start.plusDays(grace.toLong()))) return true
        val window = anniversaryWindowDays?.takeIf { it > 0 } ?: return false
        var anniversary = start.plusYears(1)
        while (anniversary.isBefore(maturity)) {
            if (!today.isBefore(anniversary) && today.isBefore(anniversary.plusDays(window.toLong()))) return true
            anniversary = anniversary.plusYears(1)
        }
        return false
    }

    /** Can the money be withdrawn right now, and if not, why. */
    fun accessStatus(today: LocalDate = LocalDate.now()): SavingsAccess = when (productType) {
        SavingsProductType.FLEXIBLE -> SavingsAccess.AVAILABLE
        SavingsProductType.TERM -> {
            val start = termStartDate
            val maturity = maturityDate
            when {
                start == null || maturity == null -> SavingsAccess.AVAILABLE // not fully configured: don't lock blindly
                today.isBefore(start) || !today.isBefore(maturity) -> SavingsAccess.AVAILABLE
                inPenaltyFreeWindow(today) -> SavingsAccess.AVAILABLE
                else -> SavingsAccess.LOCKED_TERM
            }
        }
        SavingsProductType.NOTICE -> {
            val ready = noticeReadyDate
            when {
                noticeDays == null || noticeDays <= 0 -> SavingsAccess.AVAILABLE
                noticeRequestedDate == null -> SavingsAccess.NOTICE_NOT_REQUESTED
                ready != null && today.isBefore(ready) -> SavingsAccess.NOTICE_PENDING
                else -> SavingsAccess.AVAILABLE
            }
        }
    }

    /** The day [accessStatus] turns AVAILABLE, when that is known. */
    fun accessibleFrom(today: LocalDate = LocalDate.now()): LocalDate? = when (accessStatus(today)) {
        SavingsAccess.AVAILABLE -> null
        SavingsAccess.LOCKED_TERM -> maturityDate
        SavingsAccess.NOTICE_PENDING -> noticeReadyDate
        SavingsAccess.NOTICE_NOT_REQUESTED -> noticeDays?.let { today.plusDays(it.toLong()) }
    }

    /** What can be moved to the main account right now without breaking any rule. */
    fun accessibleBalance(today: LocalDate = LocalDate.now()): Double {
        if (accessStatus(today) != SavingsAccess.AVAILABLE) return 0.0
        return (currentBalance - (minRemainingBalance ?: 0.0)).coerceAtLeast(0.0)
    }

    /** The part of the balance that is not spendable right now (locked, in notice, or the required minimum). */
    fun lockedBalance(today: LocalDate = LocalDate.now()): Double =
        (currentBalance - accessibleBalance(today)).coerceAtLeast(0.0)

    /** Term deposits only take money before they start, during a penalty-free window, or once they are empty. */
    fun acceptsDeposits(today: LocalDate = LocalDate.now()): Boolean =
        !(productType == SavingsProductType.TERM &&
            currentBalance > 0.0 &&
            accessStatus(today) == SavingsAccess.LOCKED_TERM)

    /** Interest forfeited by breaking a locked term early: [earlyWithdrawalPenaltyDays] days of interest on [amount]. */
    fun earlyWithdrawalPenalty(amount: Double, today: LocalDate = LocalDate.now()): Double {
        if (accessStatus(today) != SavingsAccess.LOCKED_TERM) return 0.0
        val days = earlyWithdrawalPenaltyDays?.takeIf { it > 0 } ?: return 0.0
        return min(amount, interestOver(days.toLong(), amount))
    }

    /** Checks a withdrawal of [amount] against every rule of this account and prices any penalty. */
    fun quoteWithdrawal(amount: Double, today: LocalDate = LocalDate.now()): WithdrawalQuote {
        if (!amount.isFinite() || amount <= 0.0) return WithdrawalQuote.Blocked("Enter an amount greater than zero.")
        if (amount > currentBalance + CENT) return WithdrawalQuote.Blocked("That is more than the account holds.")

        val status = accessStatus(today)
        val isEarlyBreak = status == SavingsAccess.LOCKED_TERM
        when (status) {
            SavingsAccess.NOTICE_NOT_REQUESTED ->
                return WithdrawalQuote.Blocked("This account needs $noticeDays days' notice. Request a withdrawal first.")
            SavingsAccess.NOTICE_PENDING ->
                return WithdrawalQuote.Blocked("Your notice period ends on $noticeReadyDate.")
            SavingsAccess.LOCKED_TERM ->
                if (earlyWithdrawalPenaltyDays == null || earlyWithdrawalPenaltyDays <= 0) {
                    return WithdrawalQuote.Blocked("This term is locked until $maturityDate and has no early-withdrawal option.")
                }
            SavingsAccess.AVAILABLE -> Unit
        }

        val isFullWithdrawal = amount >= currentBalance - CENT
        val floor = minRemainingBalance ?: 0.0
        if (!isFullWithdrawal && currentBalance - amount < floor - CENT) {
            return WithdrawalQuote.Blocked("The account must keep a balance of at least ${money(floor)}.")
        }
        val minimum = minWithdrawalAmount ?: 0.0
        if (!isFullWithdrawal && amount < minimum - CENT) {
            return WithdrawalQuote.Blocked("The minimum withdrawal is ${money(minimum)}.")
        }
        if (isFullWithdrawal && floor > 0.0) {
            return WithdrawalQuote.Blocked("The account must keep a balance of at least ${money(floor)}.")
        }

        val penalty = if (isEarlyBreak) earlyWithdrawalPenalty(amount, today) else 0.0
        return WithdrawalQuote.Allowed(gross = amount, penalty = penalty, isEarlyBreak = isEarlyBreak)
    }

    private companion object {
        const val CENT = 0.005
        fun money(value: Double) = String.format(java.util.Locale.getDefault(), "%.2f", value)
    }
}

enum class SavingsProductType {
    FLEXIBLE,
    TERM,
    NOTICE
}

/** Whether an account's money can be withdrawn today. */
enum class SavingsAccess {
    AVAILABLE,
    /** TERM account before maturity, outside a penalty-free window. */
    LOCKED_TERM,
    /** NOTICE account where no withdrawal has been requested yet. */
    NOTICE_NOT_REQUESTED,
    /** NOTICE account waiting for the notice period to end. */
    NOTICE_PENDING
}

sealed interface WithdrawalQuote {
    /**
     * [gross] leaves the account; [net] (gross minus [penalty]) reaches the main account.
     * [isEarlyBreak] means a locked term is being broken and the user must accept the penalty.
     */
    data class Allowed(val gross: Double, val penalty: Double, val isEarlyBreak: Boolean) : WithdrawalQuote {
        val net: Double get() = (gross - penalty).coerceAtLeast(0.0)
    }

    data class Blocked(val reason: String) : WithdrawalQuote
}

/** Outcome of moving money between the main account and a savings account. */
sealed interface SavingsMoveResult {
    data object Success : SavingsMoveResult
    data class Rejected(val reason: String) : SavingsMoveResult
}
