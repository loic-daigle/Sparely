package com.example.sparely.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavingsAccountRulesTest {
    private val start = LocalDate.of(2026, 1, 1)

    private fun term(
        balance: Double = 1_000.0,
        months: Int = 12,
        apy: Double = 5.0,
        grace: Int? = null,
        anniversary: Int? = null,
        penaltyDays: Int? = 90
    ) = SavingsAccount(
        name = "GIC",
        currentBalance = balance,
        annualPercentageYield = apy,
        productType = SavingsProductType.TERM,
        termMonths = months,
        termStartDate = start,
        gracePeriodDays = grace,
        anniversaryWindowDays = anniversary,
        earlyWithdrawalPenaltyDays = penaltyDays
    )

    private fun notice(requested: LocalDate? = null) = SavingsAccount(
        name = "Notice",
        currentBalance = 500.0,
        productType = SavingsProductType.NOTICE,
        noticeDays = 30,
        noticeRequestedDate = requested
    )

    @Test
    fun `flexible account is always fully accessible`() {
        val account = SavingsAccount(name = "HISA", currentBalance = 250.0)
        assertEquals(250.0, account.accessibleBalance(), 0.001)
        assertEquals(0.0, account.lockedBalance(), 0.001)
    }

    @Test
    fun `term is locked until maturity then released`() {
        val account = term()
        assertEquals(SavingsAccess.LOCKED_TERM, account.accessStatus(LocalDate.of(2026, 6, 1)))
        assertEquals(0.0, account.accessibleBalance(LocalDate.of(2026, 6, 1)), 0.001)
        assertEquals(1_000.0, account.lockedBalance(LocalDate.of(2026, 6, 1)), 0.001)
        assertEquals(LocalDate.of(2027, 1, 1), account.maturityDate)
        assertEquals(SavingsAccess.AVAILABLE, account.accessStatus(LocalDate.of(2027, 1, 1)))
        assertEquals(1_000.0, account.accessibleBalance(LocalDate.of(2027, 1, 1)), 0.001)
    }

    @Test
    fun `term is not locked before it starts or when not fully configured`() {
        assertEquals(SavingsAccess.AVAILABLE, term().accessStatus(LocalDate.of(2025, 12, 31)))
        assertEquals(SavingsAccess.AVAILABLE, term().copy(termStartDate = null).accessStatus(LocalDate.of(2026, 6, 1)))
        assertEquals(SavingsAccess.AVAILABLE, term().copy(termMonths = null).accessStatus(LocalDate.of(2026, 6, 1)))
    }

    @Test
    fun `grace period and anniversary windows are penalty free`() {
        val account = term(months = 24, grace = 10, anniversary = 14)
        assertEquals(SavingsAccess.AVAILABLE, account.accessStatus(LocalDate.of(2026, 1, 5)))
        assertEquals(SavingsAccess.LOCKED_TERM, account.accessStatus(LocalDate.of(2026, 2, 1)))
        assertEquals(SavingsAccess.AVAILABLE, account.accessStatus(LocalDate.of(2027, 1, 10)))
        assertEquals(SavingsAccess.LOCKED_TERM, account.accessStatus(LocalDate.of(2027, 1, 20)))
    }

    @Test
    fun `notice account needs a request and then waits out the notice period`() {
        assertEquals(SavingsAccess.NOTICE_NOT_REQUESTED, notice().accessStatus(LocalDate.of(2026, 3, 1)))
        assertEquals(0.0, notice().accessibleBalance(LocalDate.of(2026, 3, 1)), 0.001)

        val requested = notice(requested = LocalDate.of(2026, 3, 1))
        assertEquals(SavingsAccess.NOTICE_PENDING, requested.accessStatus(LocalDate.of(2026, 3, 20)))
        assertEquals(LocalDate.of(2026, 3, 31), requested.accessibleFrom(LocalDate.of(2026, 3, 20)))
        assertEquals(SavingsAccess.AVAILABLE, requested.accessStatus(LocalDate.of(2026, 3, 31)))
        assertEquals(500.0, requested.accessibleBalance(LocalDate.of(2026, 3, 31)), 0.001)
    }

    @Test
    fun `minimum remaining balance is held back`() {
        val account = SavingsAccount(name = "HISA", currentBalance = 1_000.0, minRemainingBalance = 300.0)
        assertEquals(700.0, account.accessibleBalance(), 0.001)
        assertEquals(300.0, account.lockedBalance(), 0.001)
    }

    @Test
    fun `monthly interest uses the effective monthly rate not apy over twelve`() {
        val account = SavingsAccount(name = "HISA", currentBalance = 10_000.0, annualPercentageYield = 12.0)
        // 12% APY compounds from a monthly rate of ~0.9489%, not 1%
        assertEquals(94.89, account.projectedMonthlyInterest, 0.01)
        assertEquals(1_200.0, account.projectedAnnualInterest, 0.001)
        // twelve compounded months must reproduce the APY exactly
        val after = 10_000.0 * Math.pow(1 + account.projectedMonthlyInterest / 10_000.0, 12.0)
        assertEquals(11_200.0, after, 0.01)
    }

    @Test
    fun `interest over a year equals apy and over zero days is zero`() {
        val account = SavingsAccount(name = "HISA", currentBalance = 1_000.0, annualPercentageYield = 4.5)
        assertEquals(45.0, account.interestOver(365), 0.001)
        assertEquals(0.0, account.interestOver(0), 0.0)
        assertEquals(0.0, SavingsAccount(name = "x", currentBalance = 1_000.0).interestOver(365), 0.0)
    }

    @Test
    fun `projected maturity value adds the remaining interest`() {
        val account = term(balance = 1_000.0, apy = 5.0)
        assertEquals(1_050.0, account.projectedMaturityValue(start)!!, 0.01)
        assertEquals(null, SavingsAccount(name = "HISA", currentBalance = 1.0).projectedMaturityValue())
    }

    @Test
    fun `accrued interest stops at maturity`() {
        val account = term(balance = 1_000.0, apy = 5.0)
        assertEquals(50.0, account.estimatedAccruedInterest(LocalDate.of(2030, 1, 1)), 0.01)
    }

    @Test
    fun `early break is priced as days of interest and capped by the amount`() {
        val account = term(apy = 5.0, penaltyDays = 90)
        val quote = account.quoteWithdrawal(1_000.0, LocalDate.of(2026, 6, 1))
        quote as WithdrawalQuote.Allowed
        assertTrue(quote.isEarlyBreak)
        assertEquals(1_000.0 * (Math.pow(1.05, 90 / 365.0) - 1), quote.penalty, 0.001)
        assertEquals(1_000.0 - quote.penalty, quote.net, 0.001)

        val huge = term(apy = 5.0, penaltyDays = 100_000)
        assertEquals(1_000.0, huge.earlyWithdrawalPenalty(1_000.0, LocalDate.of(2026, 6, 1)), 0.001)
    }

    @Test
    fun `locked term without a penalty option cannot be withdrawn from`() {
        val quote = term(penaltyDays = null).quoteWithdrawal(100.0, LocalDate.of(2026, 6, 1))
        assertTrue(quote is WithdrawalQuote.Blocked)
    }

    @Test
    fun `matured term withdraws without penalty`() {
        val quote = term().quoteWithdrawal(400.0, LocalDate.of(2027, 2, 1)) as WithdrawalQuote.Allowed
        assertFalse(quote.isEarlyBreak)
        assertEquals(0.0, quote.penalty, 0.0)
        assertEquals(400.0, quote.net, 0.0)
    }

    @Test
    fun `withdrawal rules reject overdrafts, small amounts and breaking the minimum balance`() {
        val account = SavingsAccount(
            name = "HISA",
            currentBalance = 1_000.0,
            minWithdrawalAmount = 100.0,
            minRemainingBalance = 200.0
        )
        assertTrue(account.quoteWithdrawal(0.0) is WithdrawalQuote.Blocked)
        assertTrue(account.quoteWithdrawal(1_500.0) is WithdrawalQuote.Blocked)
        assertTrue(account.quoteWithdrawal(50.0) is WithdrawalQuote.Blocked)
        assertTrue(account.quoteWithdrawal(900.0) is WithdrawalQuote.Blocked)
        assertTrue(account.quoteWithdrawal(800.0) is WithdrawalQuote.Allowed)
    }

    @Test
    fun `notice account blocks withdrawals until the notice has run`() {
        assertTrue(notice().quoteWithdrawal(100.0, LocalDate.of(2026, 3, 1)) is WithdrawalQuote.Blocked)
        val requested = notice(requested = LocalDate.of(2026, 3, 1))
        assertTrue(requested.quoteWithdrawal(100.0, LocalDate.of(2026, 3, 15)) is WithdrawalQuote.Blocked)
        assertTrue(requested.quoteWithdrawal(100.0, LocalDate.of(2026, 4, 1)) is WithdrawalQuote.Allowed)
    }

    @Test
    fun `locked term refuses new deposits unless empty`() {
        assertFalse(term().acceptsDeposits(LocalDate.of(2026, 6, 1)))
        assertTrue(term(balance = 0.0).acceptsDeposits(LocalDate.of(2026, 6, 1)))
        assertTrue(term().acceptsDeposits(LocalDate.of(2027, 2, 1)))
    }
}
