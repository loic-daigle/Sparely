package com.example.sparely.ai

import com.example.sparely.domain.logic.PurchaseAffordabilityEngine
import com.example.sparely.domain.logic.PurchaseAffordabilityEngine.FindingKind
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.ui.state.SparelyUiState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns numbers the app already calculated into [Fact]s for the model. Labels are English because
 * the model reads English most reliably; it still replies in the user's language.
 *
 * Only figures that exist are included, and the list stays short so the prompt fits the model.
 */
object FinanceFacts {

    /** How the month is going: spending, budgets, cash, upcoming bills and savings. */
    fun overview(state: SparelyUiState, money: (Double) -> String): List<Fact> = buildList {
        state.budgetSummary?.takeIf { it.totalBudget > 0 }?.let { budget ->
            add(Fact("Spent this month", "${money(budget.totalSpent)} of a ${money(budget.totalBudget)} total budget"))
        }
        state.analytics.predictedMonthEndSpending.takeIf { it > 0 }?.let {
            add(Fact("Expected spending by month end", money(it)))
        }
        state.analytics.monthOverMonthChange.takeIf { abs(it) >= 1 }?.let {
            add(Fact("Spending compared with last month", signedPercent(it)))
        }
        addAll(budgetFacts(state, money, limit = 3))
        state.cashflowForecast?.let { forecast ->
            add(Fact("Main account balance", money(forecast.currentBalance)))
            add(Fact("Safe to spend now", money(forecast.safeToSpend)))
            add(Fact("Expected balance in 30 days", money(forecast.projectedBalance30Days)))
            val bills = forecast.essentialObligations30Days + forecast.discretionaryObligations30Days
            if (bills > 0) add(Fact("Bills due in the next 30 days", money(bills)))
        }
        if (state.totalVaultBalance > 0) add(Fact("Saved in vaults", money(state.totalVaultBalance)))
        state.emergencyFundGoal?.takeIf { it.targetAmount > 0 }?.let {
            add(Fact("Emergency fund", "${percent(it.coverageRatio)} of its ${money(it.targetAmount)} target"))
        }
        state.financialHealthScore?.let {
            add(Fact("Financial health score", "${it.overallScore} out of 100 (${it.healthLevel.label})"))
        }
    }

    /** Budgets this month, the ones closest to or over their limit first. */
    fun budgets(state: SparelyUiState, money: (Double) -> String): List<Fact> = buildList {
        val summary = state.budgetSummary ?: return@buildList
        if (summary.totalBudget > 0) {
            add(Fact("All budgets this month", "${money(summary.totalSpent)} spent of ${money(summary.totalBudget)}"))
        }
        if (summary.categoriesOverBudget > 0) add(Fact("Budgets over their limit", summary.categoriesOverBudget.toString()))
        addAll(budgetFacts(state, money, limit = 6))
        val daysLeft = summary.categoryStatuses.firstOrNull()?.daysRemainingInMonth
        if (daysLeft != null) add(Fact("Days left in the month", daysLeft.toString()))
    }

    private fun budgetFacts(state: SparelyUiState, money: (Double) -> String, limit: Int): List<Fact> =
        state.budgetSummary?.categoryStatuses.orEmpty()
            .filter { it.limit > 0 }
            .sortedByDescending { it.spent / it.limit }
            .take(limit)
            .map { Fact("${categoryName(it.category)} budget", "${money(it.spent)} of ${money(it.limit)} (${percent(it.spent / it.limit)} used)") }

    /** What the "Can I afford it?" check found. The verdict comes from the app, not the model. */
    fun purchase(result: PurchaseAffordabilityEngine.Result, money: (Double) -> String): List<Fact> = buildList {
        add(Fact("Price including tax and extras", money(result.price)))
        // A cool-off can come with "go ahead": the money is fine, but a big purchase deserves a pause.
        // Said in one fact, so a small model doesn't read "go ahead" and "wait" as a contradiction.
        val waitDays = result.findings.firstOrNull { it.kind == FindingKind.SUGGESTED_WAIT }?.primary?.roundToInt()
        add(
            Fact(
                "Verdict",
                when (result.verdict) {
                    PurchaseAffordabilityEngine.Verdict.GO_AHEAD ->
                        if (waitDays != null && waitDays > 0) {
                            "affordable; the money is fine, but because it's a big purchase, sleep on it for $waitDays days " +
                                "to be sure you still want it"
                        } else {
                            "affordable, it fits the budget"
                        }
                    PurchaseAffordabilityEngine.Verdict.WAIT -> "not yet: better to wait before buying"
                    PurchaseAffordabilityEngine.Verdict.AVOID -> "not affordable right now: avoid it for now"
                }
            )
        )
        add(Fact("Safe to spend before buying", money(result.before.safeToSpend)))
        add(Fact("Expected balance in 30 days without it", money(result.before.projectedBalance30Days)))
        add(Fact("Expected balance in 30 days with it", money(result.after.projectedBalance30Days)))
        // A raw number next to the finding about it reads as two different claims ("only 3.9
        // months... which is comfortable"), so the raw one is left out when a finding covers it.
        val kinds = result.findings.map { it.kind }.toSet()
        if (FindingKind.LARGE_SHARE_OF_INCOME !in kinds) {
            result.shareOfMonthlyIncome?.let { add(Fact("Price as a share of monthly income", percent(it))) }
        }
        if (kinds.none { it in BUFFER_FINDINGS }) {
            result.monthsOfBufferAfter?.let { add(Fact("Months of living costs left after buying", oneDecimal(it))) }
        }
        add(Fact("Left over each month after bills and normal spending", money(result.monthlySurplus)))
        result.findings.mapNotNullTo(this) { finding(it, money) }
    }

    private val BUFFER_FINDINGS = setOf(FindingKind.BUFFER_UNDER_MONTH, FindingKind.BUFFER_THIN, FindingKind.BUFFER_COMFORTABLE)

    /**
     * One wishlist item: what it is, how far along saving for it is and, when the check could run,
     * what buying it would do.
     */
    fun wish(
        name: String,
        price: Double,
        setAside: Double,
        targetDate: java.time.LocalDate?,
        reallyNeeded: Boolean,
        cooldownDaysLeft: Int,
        result: PurchaseAffordabilityEngine.Result?,
        money: (Double) -> String
    ): List<Fact> = buildList {
        add(Fact("Wishlist item", name))
        add(Fact("Set aside for it so far", "${money(setAside)} of ${money(price)}"))
        targetDate?.let { add(Fact("Planned purchase date", it.toString())) }
        add(Fact("The user says it is", if (reallyNeeded) "a real need" else "a want, not a need"))
        if (cooldownDaysLeft > 0) add(Fact("Days left to sleep on it", cooldownDaysLeft.toString()))
        // The check repeats the price, which is already above.
        result?.let { r -> addAll(purchase(r, money).drop(1)) }
    }

    /** The financial health score, its parts, and what would raise it. */
    fun health(score: com.example.sparely.domain.model.FinancialHealthScore): List<Fact> = buildList {
        add(Fact("Financial health score", "${score.overallScore} out of 100 (${score.healthLevel.label})"))
        score.scoreBreakdown.forEach { (part, value) -> add(Fact("$part score", "$value out of 100")) }
        score.topStrengths.take(3).forEach { add(Fact("Strength", it)) }
        score.improvementAreas
            .sortedWith(compareBy<com.example.sparely.domain.model.ImprovementTip> { it.priority.ordinal }.thenByDescending { it.potentialScoreGain })
            .take(3)
            .forEach { add(Fact("Way to improve", "${it.title}: ${it.actionable} (up to +${it.potentialScoreGain} points)")) }
    }

    private fun finding(f: PurchaseAffordabilityEngine.Finding, money: (Double) -> String): Fact? {
        val text = when (f.kind) {
            FindingKind.SHORT_OF_FUNDS -> "Short by ${money(f.primary)} to pay for it"
            FindingKind.NEGATIVE_IN_30_DAYS -> "Balance would go negative within 30 days, down to ${money(f.primary)}"
            FindingKind.POSITIVE_IN_30_DAYS -> "Balance stays positive over the next 30 days"
            FindingKind.ESSENTIAL_BILL_UNCOVERED -> "An essential bill couldn't be covered in ${f.primary.roundToInt()} days"
            FindingKind.LOW_BALANCE_SOON -> "Balance would drop to ${money(f.primary)} in ${f.secondary.roundToInt()} days"
            FindingKind.OVER_SAFE_TO_SPEND -> "${money(f.primary)} over the safe-to-spend amount of ${money(f.secondary)}"
            FindingKind.WITHIN_SAFE_TO_SPEND -> "Fits within the safe-to-spend amount of ${money(f.primary)}"
            FindingKind.BUFFER_UNDER_MONTH -> "Less than a month of living costs would be left"
            FindingKind.BUFFER_THIN -> "Only ${oneDecimal(f.primary)} months of living costs would be left"
            FindingKind.BUFFER_COMFORTABLE -> "A comfortable ${oneDecimal(f.primary)} months of living costs would be left"
            FindingKind.LARGE_SHARE_OF_INCOME -> "The price is ${percent(f.primary)} of a month's income"
            FindingKind.DIPS_INTO_SAVINGS -> "It would take ${money(f.primary)} from savings accounts"
            FindingKind.SAVING_TAX_ADDED -> "Sparely's saving tax adds ${money(f.primary)} set aside into vaults"
            FindingKind.SAVING_TAX_SKIPPED -> "${money(f.primary)} of the usual saving couldn't be set aside"
            FindingKind.SAVINGS_DEPOSITS_AT_RISK -> "About ${money(f.primary)} of planned vault saving might be missed"
            FindingKind.CARD_BALANCE_OWED -> "${money(f.primary)} is owed on credit cards"
            FindingKind.CARDS_NOT_COVERED -> "The ${money(f.primary)} card balance wouldn't be covered afterwards"
            FindingKind.EMERGENCY_FUND_BELOW_TARGET -> "Emergency fund is at ${percent(f.primary)} of its target"
            FindingKind.INCOME_UNSTABLE -> "Income varies from month to month"
            FindingKind.HEALTH_SCORE_LOW -> "Financial health score is low (${f.primary.roundToInt()} out of 100)"
            FindingKind.BUDGETS_OVER -> "Some budgets are already over their limit"
            FindingKind.COULD_PAY_TWICE -> "Available money covers the price at least twice"
            FindingKind.SUGGESTED_WAIT -> return null
            FindingKind.SAVE_UP_PLAN -> "Could be saved up in ${f.primary.roundToInt()} months by setting aside ${money(f.secondary)} a month"
            FindingKind.NO_MONTHLY_SURPLUS -> "Nothing is left over each month to save toward it"
        }
        // The engine already rates each finding; passing that on keeps the model from guessing
        // whether a number is good or bad news.
        val label = when (f.impact) {
            PurchaseAffordabilityEngine.Impact.OK -> "Good sign"
            PurchaseAffordabilityEngine.Impact.CAUTION -> "Watch out"
            PurchaseAffordabilityEngine.Impact.PROBLEM -> "Problem"
        }
        return Fact(label, text)
    }

    private fun categoryName(category: ExpenseCategory): String =
        category.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun percent(ratio: Double): String = "${(ratio * 100).roundToInt()}%"

    private fun signedPercent(value: Double): String {
        val rounded = value.roundToInt()
        return if (rounded > 0) "up $rounded%" else "down ${-rounded}%"
    }

    private fun oneDecimal(value: Double): String = "%.1f".format(java.util.Locale.ROOT, value)
}
