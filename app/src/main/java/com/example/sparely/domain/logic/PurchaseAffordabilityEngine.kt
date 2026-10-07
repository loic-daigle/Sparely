package com.example.sparely.domain.logic

import com.example.sparely.domain.model.BudgetHealthStatus
import com.example.sparely.domain.model.EmergencyFundGoal
import com.example.sparely.domain.model.HealthLevel
import com.example.sparely.domain.model.IncomeStability
import com.example.sparely.domain.model.PaymentMethod
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.SparelySettings
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Answers "can I afford this big purchase?" by running the cashflow forecast twice - as things
 * stand and with the purchase paid out of liquid money - and comparing the two.
 * Also weighs the saving tax the purchase triggers, savings it would draw on, and planned vault deposits it could squeeze out.
 * Numbers only: no purpose/urgency weighting yet.
 */
object PurchaseAffordabilityEngine {

    enum class Verdict { GO_AHEAD, WAIT, AVOID }

    enum class Impact { OK, CAUTION, PROBLEM }

    enum class FindingKind {
        SHORT_OF_FUNDS,          // primary = amount short
        NEGATIVE_IN_30_DAYS,     // primary = projected balance
        POSITIVE_IN_30_DAYS,     // primary = balance after, secondary = balance without the purchase
        ESSENTIAL_BILL_UNCOVERED, // primary = days until it can't be covered
        LOW_BALANCE_SOON,        // primary = lowest balance, secondary = days until it happens
        OVER_SAFE_TO_SPEND,      // primary = amount over, secondary = safe-to-spend
        WITHIN_SAFE_TO_SPEND,    // primary = safe-to-spend
        BUFFER_UNDER_MONTH,      // primary = months of reserve left
        BUFFER_THIN,             // primary = months of reserve left
        BUFFER_COMFORTABLE,      // primary = months of reserve left
        LARGE_SHARE_OF_INCOME,   // primary = share of monthly income (0.75 = 75%)
        DIPS_INTO_SAVINGS,       // primary = amount taken from savings accounts, secondary = share of them (0.4 = 40%)
        SAVING_TAX_ADDED,        // primary = saving tax that moves into vaults, secondary = price + that tax
        SAVING_TAX_SKIPPED,      // primary = part of the usual saving tax that can't be set aside, secondary = the usual amount
        SAVINGS_DEPOSITS_AT_RISK, // primary = vault saving likely missed because of the purchase, secondary = planned monthly vault saving
        CARD_BALANCE_OWED,       // primary = total credit card balance
        CARDS_NOT_COVERED,       // primary = card balance, secondary = money left after the purchase
        EMERGENCY_FUND_BELOW_TARGET, // primary = share of target funded (0.4 = 40%), secondary = amount still missing
        INCOME_UNSTABLE,         // primary = 1 for moderately variable, 2 for highly variable
        HEALTH_SCORE_LOW,        // primary = financial health score
        BUDGETS_OVER,            // primary unused
        COULD_PAY_TWICE,         // primary unused: liquid money covers the price at least twice
        SUGGESTED_WAIT,          // primary = days to sleep on it before buying
        SAVE_UP_PLAN,            // primary = months to save up, secondary = amount to set aside each month
        NO_MONTHLY_SURPLUS       // primary unused: nothing is left over each month to save toward it
    }

    /** Everything beyond the cashflow forecast that the verdict can lean on. All optional. */
    data class Context(
        val vaults: List<SmartVault> = emptyList(),
        /** Needed for the saving tax the purchase would trigger. */
        val settings: SparelySettings? = null,
        val today: LocalDate = LocalDate.now(),
        val paymentMethods: List<PaymentMethod> = emptyList(),
        val emergencyFund: EmergencyFundGoal? = null,
        val incomeStability: IncomeStability? = null,
        val healthScore: Int? = null,
        val healthLevel: HealthLevel? = null,
        val budgetHealth: BudgetHealthStatus? = null
    )

    /** One reason behind the verdict. The UI turns [kind] plus the numbers into localized text. */
    data class Finding(
        val impact: Impact,
        val kind: FindingKind,
        val primary: Double,
        val secondary: Double = 0.0
    )

    data class Result(
        val verdict: Verdict,
        val price: Double,
        val before: CashflowEngine.CashflowForecast,
        val after: CashflowEngine.CashflowForecast,
        /** How much of the price exceeds today's safe-to-spend (0 when it fits). */
        val overSafeToSpend: Double,
        /** Liquid money after the purchase, in months of essential bills plus normal spending. Null if there is no cost baseline. */
        val monthsOfBufferAfter: Double?,
        /** Price as a share of expected monthly income. Null when income is unknown. */
        val shareOfMonthlyIncome: Double?,
        /** Saving tax the purchase would move from the main account into vaults. */
        val savingTax: Double,
        /** What the user is left with each month after bills and normal spending (can be negative). */
        val monthlySurplus: Double,
        val findings: List<Finding>
    )

    /** Below this many months of living costs left after buying, the purchase is a stretch. */
    private const val MIN_BUFFER_MONTHS = 1.0
    private const val DEFAULT_COMFORTABLE_BUFFER_MONTHS = 3.0
    private const val LARGE_SHARE_OF_INCOME = 0.5

    // Cool-off tiers by price as a share of monthly income: <10% none, <25% 3 days, <50% a week, else 3 weeks.
    private const val WAIT_TIER_1 = 0.10
    private const val WAIT_TIER_2 = 0.25
    private const val WAIT_TIER_3 = 0.50
    private const val MAX_PLAN_MONTHS = 24

    fun evaluate(
        input: CashflowEngine.ForecastInput,
        price: Double,
        context: Context = Context()
    ): Result {
        require(price > 0) { "price must be positive" }

        val before = CashflowEngine.forecast(input)
        val afterPrice = input.afterPurchase(price)
        val fromSavings = input.totalHisaBalance - afterPrice.totalHisaBalance

        // The app's saving tax also takes a cut of this expense, out of the main account into vaults.
        val activeVaults = context.vaults.filter { !it.archived }
        val taxPlans = context.settings?.let {
            SavingTaxEngine.calculate(
                SavingTaxEngine.Context(
                    expenseAmount = price,
                    expenseDate = context.today,
                    settings = it,
                    vaults = context.vaults,
                    currentMainAccountBalance = afterPrice.currentBalance
                )
            )
        }.orEmpty()
        val savingTax = taxPlans.sumOf { it.amount }
        val usualSavingTax = context.settings?.takeIf {
            context.vaults.any { v -> !v.archived && v.allowAutoIncome && v.targetAmount - v.currentBalance > 0.0 }
        }?.let { ceil(price * it.savingTaxRate.coerceIn(0.0, 1.0)) } ?: 0.0
        val after = CashflowEngine.forecast(afterPrice.copy(currentBalance = afterPrice.currentBalance - savingTax))

        val liquidBefore = input.currentBalance + input.totalHisaBalance
        val shortfall = price > max(liquidBefore, 0.0)
        val overSafeToSpend = (price - before.safeToSpend).coerceAtLeast(0.0)

        val monthlyCost = before.essentialObligations30Days + before.dailyBurnRate * 30
        val monthsOfBufferAfter = if (monthlyCost > 0) after.currentBalance / monthlyCost else null
        val shareOfIncome = input.expectedMonthlyIncome.takeIf { it > 0 }?.let { price / it }

        // Uneven income needs a thicker cushion before a big purchase is comfortable.
        val bufferFactor = when (context.incomeStability) {
            IncomeStability.MODERATE -> 1.5
            IncomeStability.VARIABLE -> 2.0
            else -> 1.0
        }
        val minBufferMonths = MIN_BUFFER_MONTHS * bufferFactor
        val comfortableBufferMonths = max(
            context.emergencyFund?.targetMonths ?: DEFAULT_COMFORTABLE_BUFFER_MONTHS,
            minBufferMonths
        )

        val cardBalance = context.paymentMethods.filter { it.isCreditCard }.sumOf { it.currentBalance }.coerceAtLeast(0.0)

        val findings = mutableListOf<Finding>()
        var verdict = Verdict.GO_AHEAD
        fun add(impact: Impact, kind: FindingKind, primary: Double, secondary: Double = 0.0, level: Verdict? = null) {
            findings += Finding(impact, kind, primary, secondary)
            if (level != null && level.ordinal > verdict.ordinal) verdict = level
        }

        if (shortfall) {
            add(Impact.PROBLEM, FindingKind.SHORT_OF_FUNDS, price - max(liquidBefore, 0.0), level = Verdict.AVOID)
        }

        if (after.projectedBalance30Days < 0) {
            add(Impact.PROBLEM, FindingKind.NEGATIVE_IN_30_DAYS, after.projectedBalance30Days, level = Verdict.AVOID)
        } else if (before.projectedBalance30Days >= 0) {
            add(Impact.OK, FindingKind.POSITIVE_IN_30_DAYS, after.projectedBalance30Days, before.projectedBalance30Days)
        }

        if (after.essentialsCoveredDays != null && before.essentialsCoveredDays == null) {
            add(Impact.PROBLEM, FindingKind.ESSENTIAL_BILL_UNCOVERED, after.essentialsCoveredDays.toDouble(), level = Verdict.AVOID)
        }

        val newWarning = after.lowBalanceWarning?.takeIf { before.lowBalanceWarning == null }
        if (newWarning != null) {
            val critical = newWarning.severity == CashflowEngine.WarningSeverity.CRITICAL
            add(
                if (critical) Impact.PROBLEM else Impact.CAUTION,
                FindingKind.LOW_BALANCE_SOON,
                newWarning.projectedLowBalance,
                newWarning.daysUntilLowBalance.toDouble(),
                level = if (critical) Verdict.AVOID else Verdict.WAIT
            )
        }

        if (!shortfall) {
            if (overSafeToSpend > 0) {
                add(Impact.CAUTION, FindingKind.OVER_SAFE_TO_SPEND, overSafeToSpend, before.safeToSpend, level = Verdict.WAIT)
            } else {
                add(Impact.OK, FindingKind.WITHIN_SAFE_TO_SPEND, before.safeToSpend)
            }

            if (monthsOfBufferAfter != null) {
                when {
                    monthsOfBufferAfter < minBufferMonths ->
                        add(Impact.CAUTION, FindingKind.BUFFER_UNDER_MONTH, monthsOfBufferAfter, level = Verdict.WAIT)
                    monthsOfBufferAfter < comfortableBufferMonths ->
                        add(Impact.CAUTION, FindingKind.BUFFER_THIN, monthsOfBufferAfter)
                    else ->
                        add(Impact.OK, FindingKind.BUFFER_COMFORTABLE, monthsOfBufferAfter)
                }
            }
        }

        if (!shortfall && fromSavings > 0) {
            add(Impact.CAUTION, FindingKind.DIPS_INTO_SAVINGS, fromSavings, fromSavings / input.totalHisaBalance, level = Verdict.WAIT)
        }

        if (savingTax > 0) {
            add(Impact.OK, FindingKind.SAVING_TAX_ADDED, savingTax, price + savingTax)
        }
        if (usualSavingTax - savingTax >= 1.0) {
            add(Impact.CAUTION, FindingKind.SAVING_TAX_SKIPPED, usualSavingTax - savingTax, usualSavingTax)
        }

        // Planned vault deposits come out of what is left after the purchase; flag the ones it would squeeze out.
        val plannedDeposits = activeVaults.filter { it.autoSaveEnabled }.sumOf { it.monthlyNeed ?: 0.0 }
        if (plannedDeposits > 0) {
            val missedBefore = (plannedDeposits - max(before.projectedBalance30Days, 0.0)).coerceIn(0.0, plannedDeposits)
            val missedAfter = (plannedDeposits - max(after.projectedBalance30Days, 0.0)).coerceIn(0.0, plannedDeposits)
            if (missedAfter - missedBefore >= 1.0) {
                add(Impact.CAUTION, FindingKind.SAVINGS_DEPOSITS_AT_RISK, missedAfter - missedBefore, plannedDeposits, level = Verdict.WAIT)
            }
        }

        if (cardBalance > 0) {
            if (cardBalance > max(after.currentBalance, 0.0)) {
                add(Impact.CAUTION, FindingKind.CARDS_NOT_COVERED, cardBalance, after.currentBalance, level = Verdict.WAIT)
            } else {
                add(Impact.CAUTION, FindingKind.CARD_BALANCE_OWED, cardBalance)
            }
        }

        context.emergencyFund?.takeIf { it.shortfallAmount > 0 }?.let {
            add(Impact.CAUTION, FindingKind.EMERGENCY_FUND_BELOW_TARGET, it.coverageRatio, it.shortfallAmount)
        }

        if (context.incomeStability == IncomeStability.MODERATE || context.incomeStability == IncomeStability.VARIABLE) {
            add(Impact.CAUTION, FindingKind.INCOME_UNSTABLE, if (context.incomeStability == IncomeStability.VARIABLE) 2.0 else 1.0)
        }

        if (context.healthLevel == HealthLevel.NEEDS_WORK || context.healthLevel == HealthLevel.CRITICAL) {
            add(
                Impact.CAUTION,
                FindingKind.HEALTH_SCORE_LOW,
                (context.healthScore ?: 0).toDouble(),
                level = if (context.healthLevel == HealthLevel.CRITICAL) Verdict.WAIT else null
            )
        }

        if (context.budgetHealth == BudgetHealthStatus.OVER_BUDGET) {
            add(Impact.CAUTION, FindingKind.BUDGETS_OVER, 0.0)
        }

        if (shareOfIncome != null && shareOfIncome >= LARGE_SHARE_OF_INCOME) {
            add(Impact.CAUTION, FindingKind.LARGE_SHARE_OF_INCOME, shareOfIncome)
        }

        // "Could buy it twice": a comfortable cushion beyond the purchase itself.
        if (!shortfall && fromSavings <= 0 && liquidBefore >= 2 * price) {
            add(Impact.OK, FindingKind.COULD_PAY_TWICE, 0.0)
        }

        // The bigger the purchase relative to income, the longer it should sit before buying.
        val sizeShare = shareOfIncome ?: if (liquidBefore > 0) price / liquidBefore else 1.0
        val waitDays = when {
            sizeShare < WAIT_TIER_1 -> 0
            sizeShare < WAIT_TIER_2 -> 3
            sizeShare < WAIT_TIER_3 -> 7
            else -> 21
        }
        if (waitDays > 0) {
            add(Impact.CAUTION, FindingKind.SUGGESTED_WAIT, waitDays.toDouble())
        }

        // Not a clear yes: turn it into a save-up plan from the monthly surplus.
        val monthlySurplus = before.projectedBalance30Days - before.currentBalance
        val cushionGap = if (monthlyCost > 0) minBufferMonths * monthlyCost - after.currentBalance else 0.0
        val gap = max(max(price - before.safeToSpend, cushionGap), 0.0)
        if (verdict != Verdict.GO_AHEAD && gap > 0) {
            if (monthlySurplus >= 1.0) {
                val months = ceil(gap / monthlySurplus).toInt().coerceIn(1, MAX_PLAN_MONTHS)
                add(Impact.OK, FindingKind.SAVE_UP_PLAN, months.toDouble(), ceil(gap / months))
            } else {
                add(Impact.CAUTION, FindingKind.NO_MONTHLY_SURPLUS, 0.0)
            }
        }

        return Result(
            verdict = verdict,
            price = price,
            before = before,
            after = after,
            overSafeToSpend = overSafeToSpend,
            monthsOfBufferAfter = monthsOfBufferAfter,
            shareOfMonthlyIncome = shareOfIncome,
            savingTax = savingTax,
            monthlySurplus = monthlySurplus,
            findings = findings
        )
    }

    /** Pays [price] from the main account first, then the savings account; any excess leaves main overdrawn. */
    private fun CashflowEngine.ForecastInput.afterPurchase(price: Double): CashflowEngine.ForecastInput {
        val fromHisa = min(totalHisaBalance, max(0.0, price - max(currentBalance, 0.0)))
        return copy(
            currentBalance = currentBalance - (price - fromHisa),
            totalHisaBalance = totalHisaBalance - fromHisa
        )
    }
}
