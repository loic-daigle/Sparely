package com.example.sparely.domain.logic

import com.example.sparely.domain.logic.PurchaseAffordabilityEngine.Verdict
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.RecurringFrequency
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.SparelySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PurchaseAffordabilityEngineTest {

    private val today = LocalDate.of(2026, 9, 20)

    private fun input(
        main: Double,
        hisa: Double = 0.0,
        income: Double = 3000.0,
        recurring: List<RecurringExpense> = emptyList()
    ) = CashflowEngine.ForecastInput(
        currentBalance = main,
        totalHisaBalance = hisa,
        recentExpenses = emptyList(),
        recurringExpenses = recurring,
        expectedMonthlyIncome = income,
        today = today
    )

    /** An essential 800 bill landing 5 days from now. */
    private fun rent() = RecurringExpense(
        description = "rent",
        amount = 800.0,
        category = ExpenseCategory.UTILITIES,
        frequency = RecurringFrequency.WEEKLY,
        startDate = today.minusDays(2).minusWeeks(13),
        lastProcessedDate = today.minusDays(2)
    )

    @Test
    fun `small purchase well inside safe to spend is a go ahead`() {
        val result = PurchaseAffordabilityEngine.evaluate(input(main = 10_000.0), price = 200.0)

        assertEquals(Verdict.GO_AHEAD, result.verdict)
        assertEquals(0.0, result.overSafeToSpend, 0.001)
    }

    @Test
    fun `purchase larger than all liquid money is avoided`() {
        val result = PurchaseAffordabilityEngine.evaluate(input(main = 500.0, hisa = 300.0), price = 1_000.0)

        assertEquals(Verdict.AVOID, result.verdict)
    }

    @Test
    fun `purchase that exceeds safe to spend but stays positive is a wait`() {
        // Buffer is 10% of liquid, capped at 500, so safe to spend is 9,500 on 10,000.
        val result = PurchaseAffordabilityEngine.evaluate(input(main = 10_000.0), price = 9_700.0)

        assertTrue(result.overSafeToSpend > 0)
        assertTrue(result.verdict != Verdict.GO_AHEAD)
    }

    @Test
    fun `purchase that leaves an essential bill uncovered is avoided`() {
        val result = PurchaseAffordabilityEngine.evaluate(
            input(main = 1_000.0, income = 0.0, recurring = listOf(rent())),
            price = 500.0
        )

        assertNotNull(result.after.essentialsCoveredDays)
        assertEquals(Verdict.AVOID, result.verdict)
    }

    @Test
    fun `savings account covers the rest when the main account is too small`() {
        val result = PurchaseAffordabilityEngine.evaluate(input(main = 200.0, hisa = 5_000.0), price = 1_000.0)

        assertEquals(4_200.0, result.after.currentBalance, 0.001)
    }

    private val vault = SmartVault(name = "Holiday", targetAmount = 5_000.0, monthlyNeed = 400.0)

    @Test
    fun `saving tax is added to what leaves the account`() {
        val context = PurchaseAffordabilityEngine.Context(
            vaults = listOf(vault),
            settings = SparelySettings(savingTaxRate = 0.10),
            today = today
        )
        val result = PurchaseAffordabilityEngine.evaluate(input(main = 10_000.0), price = 1_000.0, context = context)

        assertEquals(100.0, result.savingTax, 1.0)
        assertEquals(8_900.0, result.after.currentBalance, 1.0)
        assertTrue(result.findings.any { it.kind == PurchaseAffordabilityEngine.FindingKind.SAVING_TAX_ADDED })
    }

    @Test
    fun `saving tax that the account cannot cover is reported as skipped`() {
        val context = PurchaseAffordabilityEngine.Context(
            vaults = listOf(vault),
            settings = SparelySettings(savingTaxRate = 0.10),
            today = today
        )
        // Nothing is left in the main account after the purchase, so none of the 100 tax can be set aside.
        val result = PurchaseAffordabilityEngine.evaluate(input(main = 1_000.0), price = 1_000.0, context = context)

        assertEquals(0.0, result.savingTax, 0.001)
        assertTrue(result.findings.any { it.kind == PurchaseAffordabilityEngine.FindingKind.SAVING_TAX_SKIPPED })
    }

    @Test
    fun `drawing on savings accounts is a wait`() {
        val result = PurchaseAffordabilityEngine.evaluate(input(main = 200.0, hisa = 20_000.0), price = 1_000.0)

        assertTrue(result.findings.any { it.kind == PurchaseAffordabilityEngine.FindingKind.DIPS_INTO_SAVINGS })
        assertTrue(result.verdict != Verdict.GO_AHEAD)
    }

    @Test
    fun `purchase that squeezes out planned vault deposits is flagged`() {
        val context = PurchaseAffordabilityEngine.Context(vaults = listOf(vault), today = today)
        // 30-day balance is ~1,200 without the purchase (covers the 400 deposit) and ~200 after.
        val result = PurchaseAffordabilityEngine.evaluate(input(main = 1_200.0, income = 0.0), price = 1_100.0, context = context)

        assertTrue(result.findings.any { it.kind == PurchaseAffordabilityEngine.FindingKind.SAVINGS_DEPOSITS_AT_RISK })
    }

    @Test
    fun `being able to pay twice is noted`() {
        val result = PurchaseAffordabilityEngine.evaluate(input(main = 10_000.0), price = 200.0)

        assertTrue(result.findings.any { it.kind == PurchaseAffordabilityEngine.FindingKind.COULD_PAY_TWICE })
    }

    @Test
    fun `wait period grows with the size of the purchase`() {
        fun waitDays(price: Double) = PurchaseAffordabilityEngine
            .evaluate(input(main = 50_000.0, income = 4_000.0), price)
            .findings.firstOrNull { it.kind == PurchaseAffordabilityEngine.FindingKind.SUGGESTED_WAIT }?.primary

        assertEquals(null, waitDays(100.0))     // 2.5% of income
        assertEquals(3.0, waitDays(600.0)!!, 0.0)   // 15%
        assertEquals(7.0, waitDays(1_500.0)!!, 0.0) // 37.5%
        assertEquals(21.0, waitDays(3_000.0)!!, 0.0) // 75%
    }

    @Test
    fun `a purchase that has to wait comes with a save up plan`() {
        // Safe to spend is 9,500 on 10,000; income of 3,000 a month with no bills or spending leaves a surplus.
        val result = PurchaseAffordabilityEngine.evaluate(input(main = 10_000.0, income = 3_000.0), price = 9_800.0)
        val plan = result.findings.firstOrNull { it.kind == PurchaseAffordabilityEngine.FindingKind.SAVE_UP_PLAN }

        assertNotNull(plan)
        assertTrue(plan!!.primary >= 1.0)
        assertTrue(plan.secondary > 0.0)
    }

    @Test
    fun `no plan is offered when nothing is left over each month`() {
        val result = PurchaseAffordabilityEngine.evaluate(input(main = 1_000.0, income = 0.0), price = 950.0)

        assertTrue(result.findings.any { it.kind == PurchaseAffordabilityEngine.FindingKind.NO_MONTHLY_SURPLUS })
    }

    @Test
    fun `money held back for wishes is not safe to spend`() {
        val free = PurchaseAffordabilityEngine.evaluate(input(main = 10_000.0), price = 100.0)
        val held = PurchaseAffordabilityEngine.evaluate(input(main = 10_000.0).copy(earmarkedMoney = 3_000.0), price = 100.0)

        assertEquals(free.before.safeToSpend - 3_000.0, held.before.safeToSpend, 0.001)
    }

    @Test
    fun `credit card balances and pending transfers are not safe to spend`() {
        val free = PurchaseAffordabilityEngine.evaluate(input(main = 10_000.0), price = 100.0)
        val committed = PurchaseAffordabilityEngine.evaluate(
            input(main = 10_000.0).copy(creditCardBalance = 1_500.0, pendingTransfers = 500.0),
            price = 100.0
        )

        assertEquals(free.before.safeToSpend - 2_000.0, committed.before.safeToSpend, 0.001)
    }

    @Test
    fun `a purchase that only fits by spending the card bill is not a go ahead`() {
        // 5,000 in the account but 4,000 of it is owed on a card: a 2,000 purchase leaves too little.
        val result = PurchaseAffordabilityEngine.evaluate(
            input(main = 5_000.0).copy(creditCardBalance = 4_000.0),
            price = 2_000.0
        )

        assertTrue(result.verdict != Verdict.GO_AHEAD)
    }

    @Test
    fun `card balances are no cushion when judging what is left`() {
        val plain = PurchaseAffordabilityEngine.evaluate(input(main = 6_000.0, income = 0.0, recurring = listOf(rent())), 500.0)
        val owing = PurchaseAffordabilityEngine.evaluate(
            input(main = 6_000.0, income = 0.0, recurring = listOf(rent())).copy(creditCardBalance = 3_000.0),
            500.0
        )

        assertTrue(owing.monthsOfBufferAfter!! < plain.monthsOfBufferAfter!!)
    }

    @Test
    fun `reserves for other wishes count against a new purchase`() {
        val without = PurchaseAffordabilityEngine.evaluate(input(main = 10_000.0), price = 9_000.0)
        val withWishes = PurchaseAffordabilityEngine.evaluate(input(main = 10_000.0).copy(earmarkedMoney = 2_000.0), price = 9_000.0)

        assertTrue(withWishes.overSafeToSpend > without.overSafeToSpend)
    }

    @Test
    fun `monthly surplus is reported`() {
        val result = PurchaseAffordabilityEngine.evaluate(input(main = 10_000.0, income = 3_000.0), price = 100.0)

        assertEquals(3_000.0, result.monthlySurplus, 1.0)
    }

    @Test
    fun `large share of income is called out`() {
        val result = PurchaseAffordabilityEngine.evaluate(input(main = 20_000.0, income = 2_000.0), price = 1_500.0)

        assertEquals(0.75, result.shareOfMonthlyIncome!!, 0.001)
        assertTrue(result.findings.any { it.kind == PurchaseAffordabilityEngine.FindingKind.LARGE_SHARE_OF_INCOME })
    }
}
