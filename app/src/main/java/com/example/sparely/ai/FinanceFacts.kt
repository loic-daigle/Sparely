package com.example.sparely.ai

import com.example.sparely.domain.logic.PurchaseAffordabilityEngine
import com.example.sparely.domain.logic.PurchaseAffordabilityEngine.FindingKind
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.FinancialHealthScore
import com.example.sparely.domain.model.HealthLevel
import com.example.sparely.domain.model.ImprovementTip
import com.example.sparely.domain.model.needsDecision
import com.example.sparely.ui.state.SparelyUiState
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns numbers the app already calculated into [Fact]s for the model, written in [PromptLanguage]
 * so the reply stays in one language.
 *
 * Only figures that exist are included, and the list stays short so the prompt fits the model.
 */
object FinanceFacts {

    /** How the month is going: spending, budgets, cash, upcoming bills and savings. */
    fun overview(
        state: SparelyUiState,
        lang: PromptLanguage = PromptLanguage.ENGLISH,
        money: (Double) -> String
    ): List<Fact> = buildList {
        state.budgetSummary?.takeIf { it.totalBudget > 0 }?.let { budget ->
            add(
                Fact(
                    lang.pick("Spent this month", "Dépensé ce mois-ci"),
                    lang.pick(
                        "${money(budget.totalSpent)} of a ${money(budget.totalBudget)} total budget",
                        "${money(budget.totalSpent)} sur un budget total de ${money(budget.totalBudget)}"
                    )
                )
            )
        }
        state.analytics.predictedMonthEndSpending.takeIf { it > 0 }?.let {
            add(Fact(lang.pick("Expected spending by month end", "Dépenses prévues à la fin du mois"), money(it)))
        }
        state.analytics.monthOverMonthChange.takeIf { abs(it) >= 1 }?.let {
            add(Fact(lang.pick("Spending compared with last month", "Dépenses par rapport au mois dernier"), signedPercent(it, lang)))
        }
        addAll(budgetFacts(state, lang, money, limit = 3))
        state.cashflowForecast?.let { forecast ->
            add(Fact(lang.pick("Main account balance", "Solde du compte principal"), money(forecast.currentBalance)))
            add(Fact(lang.pick("Safe to spend now", "Montant dépensable sans risque"), money(forecast.safeToSpend)))
            add(Fact(lang.pick("Expected balance in 30 days", "Solde prévu dans 30 jours"), money(forecast.projectedBalance30Days)))
            val bills = forecast.essentialObligations30Days + forecast.discretionaryObligations30Days
            if (bills > 0) add(Fact(lang.pick("Bills due in the next 30 days", "Factures à payer dans les 30 prochains jours"), money(bills)))
        }
        addAll(creditCardFacts(state.paymentMethods, lang, money))
        if (state.totalVaultBalance > 0) add(Fact(lang.pick("Saved in vaults", "Épargné dans les coffres"), money(state.totalVaultBalance)))
        state.emergencyFundGoal?.takeIf { it.targetAmount > 0 }?.let {
            add(
                Fact(
                    lang.pick("Emergency fund", "Fonds d'urgence"),
                    lang.pick(
                        "${lang.percent(it.coverageRatio)} of its ${money(it.targetAmount)} target",
                        "${lang.percent(it.coverageRatio)} de son objectif de ${money(it.targetAmount)}"
                    )
                )
            )
        }
        state.financialHealthScore?.let { add(healthScoreFact(it, lang)) }
    }

    /**
     * What deserves attention today, for the brief on the dashboard. The dashboard already shows
     * the balance and safe-to-spend, so repeating them helps no one: these are the things worked
     * out from them (on pace to overspend, a bill bigger than what's free, a card running high),
     * ranked problems first, then warnings, then good news, keeping the top [MAX_BRIEF_POINTS].
     */
    fun brief(
        state: SparelyUiState,
        lang: PromptLanguage = PromptLanguage.ENGLISH,
        money: (Double) -> String
    ): List<Fact> {
        val points = mutableListOf<Pair<Int, Fact>>()
        val problem = lang.pick("Problem", "Problème")
        val watch = lang.pick("Watch out", "Attention")
        val good = lang.pick("Good news", "Bonne nouvelle")
        fun add(rank: Int, label: String, text: String) { points += rank to Fact(label, text) }

        val forecast = state.cashflowForecast
        forecast?.lowBalanceWarning?.let { w ->
            add(0, problem, lang.pick(
                "Your balance could drop to ${money(w.projectedLowBalance)} in ${w.daysUntilLowBalance} days",
                "Votre solde pourrait baisser à ${money(w.projectedLowBalance)} dans ${w.daysUntilLowBalance} jours"
            ))
        }
        if (forecast != null) forecast.upcomingObligations.filter { it.daysUntilDue in 0..7 }.forEach { bill ->
            val name = bill.description.replace(Regex("\\s+"), " ").trim()
            val due = when (bill.daysUntilDue) {
                0 -> lang.pick("today", "aujourd'hui")
                1 -> lang.pick("tomorrow", "demain")
                else -> lang.pick("in ${bill.daysUntilDue} days", "dans ${bill.daysUntilDue} jours")
            }
            if (bill.amount > forecast.safeToSpend) {
                add(0, problem, lang.pick(
                    "$name (${money(bill.amount)}) is due $due, more than the ${money(forecast.safeToSpend)} you can safely spend",
                    "$name (${money(bill.amount)}) est dû $due, plus que les ${money(forecast.safeToSpend)} que vous pouvez dépenser sans risque"
                ))
            } else if (bill.daysUntilDue <= 3) {
                add(1, watch, lang.pick("$name (${money(bill.amount)}) is due $due", "$name (${money(bill.amount)}) est dû $due"))
            }
        }

        val summary = state.budgetSummary
        val statuses = summary?.categoryStatuses.orEmpty().filter { it.limit > 0 }
        val daysLeft = statuses.firstOrNull()?.daysRemainingInMonth
        statuses.filter { it.spent > it.limit }.sortedByDescending { it.spent - it.limit }.take(2).forEach {
            add(0, problem, lang.pick(
                "${categoryName(it.category, lang)} budget is over by ${money(it.spent - it.limit)} (${money(it.spent)} of ${money(it.limit)})",
                "Le budget ${categoryName(it.category, lang)} est dépassé de ${money(it.spent - it.limit)} (${money(it.spent)} sur ${money(it.limit)})"
            ))
        }
        statuses.filter { it.spent <= it.limit && it.spent / it.limit >= 0.9 }.take(1).forEach {
            add(1, watch, lang.pick(
                "${categoryName(it.category, lang)} budget is at ${lang.percent(it.spent / it.limit)}" + (daysLeft?.let { d -> " with $d days left" } ?: ""),
                "Le budget ${categoryName(it.category, lang)} est à ${lang.percent(it.spent / it.limit)}" + (daysLeft?.let { d -> " avec $d jours restants" } ?: "")
            ))
        }
        val predicted = state.analytics.predictedMonthEndSpending
        if (summary != null && summary.totalBudget > 0 && predicted > 0) {
            val gap = predicted - summary.totalBudget
            if (gap >= 1) {
                add(1, watch, lang.pick(
                    "At this pace you'll spend ${money(predicted)} this month, ${money(gap)} over your ${money(summary.totalBudget)} budget",
                    "À ce rythme, vous dépenserez ${money(predicted)} ce mois-ci, ${money(gap)} de plus que votre budget de ${money(summary.totalBudget)}"
                ))
            } else if (-gap >= 1) {
                add(2, good, lang.pick(
                    "At this pace you'll finish the month ${money(-gap)} under your ${money(summary.totalBudget)} budget",
                    "À ce rythme, vous finirez le mois ${money(-gap)} sous votre budget de ${money(summary.totalBudget)}"
                ))
            }
        }

        val change = state.analytics.monthOverMonthChange
        val growing = state.analytics.topGrowingCategory?.let { categoryName(it, lang) }
        if (change >= 15) {
            add(1, watch, lang.pick(
                "Spending is up ${lang.percent(change / 100)} from last month" + (growing?.let { g -> ", mostly in $g" } ?: ""),
                "Les dépenses sont en hausse de ${lang.percent(change / 100)} par rapport au mois dernier" + (growing?.let { g -> ", surtout en $g" } ?: "")
            ))
        } else if (change <= -10) {
            add(2, good, lang.pick(
                "Spending is down ${lang.percent(-change / 100)} from last month",
                "Les dépenses sont en baisse de ${lang.percent(-change / 100)} par rapport au mois dernier"
            ))
        }

        state.paymentMethods.filter { it.isCreditCard && it.utilizationPercent > 0.30 }
            .maxByOrNull { it.utilizationPercent }
            ?.let { card ->
                add(if (card.utilizationPercent > 0.50) 0 else 1, if (card.utilizationPercent > 0.50) problem else watch, lang.pick(
                    "${card.name} is at ${lang.percent(card.utilizationPercent)} of its limit (${money(card.currentBalance)} owed); under 30% is healthier",
                    "${card.name} est à ${lang.percent(card.utilizationPercent)} de sa limite (${money(card.currentBalance)} dû); sous 30 % c'est plus sain"
                ))
            }

        val decisions = state.wishlists.count { it.needsDecision() }
        if (decisions > 0) {
            add(1, watch, lang.pick(
                "$decisions wishlist item(s) waiting for your decision",
                "$decisions article(s) de la liste d'envies attendent votre décision"
            ))
        }

        state.idleMoneyInsight?.takeIf { it.suggestedTransferAmount >= 1 }?.let { idle ->
            val vault = idle.suggestedVault?.name
            add(2, good, lang.pick(
                "You could move ${money(idle.suggestedTransferAmount)} of idle money" + (vault?.let { v -> " to $v" } ?: " to savings"),
                "Vous pourriez déplacer ${money(idle.suggestedTransferAmount)} d'argent inactif" + (vault?.let { v -> " vers $v" } ?: " vers l'épargne")
            ))
        }

        // Nothing stands out: say the month is on track rather than nothing at all.
        if (points.isEmpty() && summary != null && summary.totalBudget > 0) {
            add(2, good, lang.pick(
                "Spending is on track: ${money(summary.totalSpent)} of ${money(summary.totalBudget)} used" + (daysLeft?.let { d -> " with $d days left" } ?: ""),
                "Les dépenses sont sur la bonne voie : ${money(summary.totalSpent)} sur ${money(summary.totalBudget)} utilisés" + (daysLeft?.let { d -> " avec $d jours restants" } ?: "")
            ))
        }
        return points.sortedBy { it.first }.take(MAX_BRIEF_POINTS).map { it.second }
    }

    private const val MAX_BRIEF_POINTS = 3

    /**
     * What's owed on credit cards, so a card purchase shows up in the summary. One card is named
     * with its share of the limit; with several, the total comes first, then the 3 largest.
     */
    fun creditCardFacts(
        paymentMethods: List<com.example.sparely.domain.model.PaymentMethod>,
        lang: PromptLanguage,
        money: (Double) -> String
    ): List<Fact> {
        val cards = paymentMethods.filter { it.isCreditCard && it.currentBalance >= 0.005 }
        if (cards.isEmpty()) return emptyList()
        fun owed(card: com.example.sparely.domain.model.PaymentMethod): String {
            val limit = card.creditLimit?.takeIf { it > 0 } ?: return money(card.currentBalance)
            return lang.pick(
                "${money(card.currentBalance)} (${lang.percent(card.utilizationPercent)} of the ${money(limit)} limit)",
                "${money(card.currentBalance)} (${lang.percent(card.utilizationPercent)} de la limite de ${money(limit)})"
            )
        }
        if (cards.size == 1) {
            val card = cards.single()
            return listOf(Fact(lang.pick("Owed on credit card ${card.name}", "Dû sur la carte de crédit ${card.name}"), owed(card)))
        }
        return buildList {
            add(Fact(lang.pick("Owed on all credit cards", "Dû sur toutes les cartes de crédit"), money(cards.sumOf { it.currentBalance })))
            cards.sortedByDescending { it.currentBalance }.take(3).forEach { card ->
                add(Fact(lang.pick("Owed on ${card.name}", "Dû sur ${card.name}"), owed(card)))
            }
        }
    }

    /** Budgets this month, the ones closest to or over their limit first. */
    fun budgets(
        state: SparelyUiState,
        lang: PromptLanguage = PromptLanguage.ENGLISH,
        money: (Double) -> String
    ): List<Fact> = buildList {
        val summary = state.budgetSummary ?: return@buildList
        if (summary.totalBudget > 0) {
            add(
                Fact(
                    lang.pick("All budgets this month", "Tous les budgets ce mois-ci"),
                    lang.pick(
                        "${money(summary.totalSpent)} spent of ${money(summary.totalBudget)}",
                        "${money(summary.totalSpent)} dépensés sur ${money(summary.totalBudget)}"
                    )
                )
            )
        }
        if (summary.categoriesOverBudget > 0) {
            add(Fact(lang.pick("Budgets over their limit", "Budgets dépassés"), summary.categoriesOverBudget.toString()))
        }
        addAll(budgetFacts(state, lang, money, limit = 6))
        val daysLeft = summary.categoryStatuses.firstOrNull()?.daysRemainingInMonth
        if (daysLeft != null) add(Fact(lang.pick("Days left in the month", "Jours restants dans le mois"), daysLeft.toString()))
    }

    private fun budgetFacts(state: SparelyUiState, lang: PromptLanguage, money: (Double) -> String, limit: Int): List<Fact> =
        state.budgetSummary?.categoryStatuses.orEmpty()
            .filter { it.limit > 0 }
            .sortedByDescending { it.spent / it.limit }
            .take(limit)
            .map {
                val used = lang.percent(it.spent / it.limit)
                Fact(
                    lang.pick("${categoryName(it.category, lang)} budget", "Budget ${categoryName(it.category, lang)}"),
                    lang.pick(
                        "${money(it.spent)} of ${money(it.limit)} ($used used)",
                        "${money(it.spent)} sur ${money(it.limit)} ($used utilisé)"
                    )
                )
            }

    /** What the "Can I afford it?" check found. The verdict comes from the app, not the model. */
    fun purchase(
        result: PurchaseAffordabilityEngine.Result,
        lang: PromptLanguage = PromptLanguage.ENGLISH,
        money: (Double) -> String
    ): List<Fact> = buildList {
        add(Fact(lang.pick("Price including tax and extras", "Prix avec taxes et extras"), money(result.price)))
        // A cool-off can come with "go ahead": the money is fine, but a big purchase deserves a pause.
        // Said in one fact, so a small model doesn't read "go ahead" and "wait" as a contradiction.
        val waitDays = result.findings.firstOrNull { it.kind == FindingKind.SUGGESTED_WAIT }?.primary?.roundToInt()
        add(
            Fact(
                lang.pick("Verdict", "Verdict"),
                when (result.verdict) {
                    PurchaseAffordabilityEngine.Verdict.GO_AHEAD ->
                        if (waitDays != null && waitDays > 0) {
                            lang.pick(
                                "affordable; the money is fine, but because it's a big purchase, sleep on it for $waitDays days to be sure you still want it",
                                "abordable; l'argent est suffisant, mais comme c'est un gros achat, mieux vaut prendre $waitDays jours de réflexion pour être sûr de le vouloir"
                            )
                        } else {
                            lang.pick("affordable, it fits the budget", "abordable, ça entre dans le budget")
                        }
                    PurchaseAffordabilityEngine.Verdict.WAIT ->
                        lang.pick("not yet: better to wait before buying", "pas encore : mieux vaut attendre avant d'acheter")
                    PurchaseAffordabilityEngine.Verdict.AVOID ->
                        lang.pick("not affordable right now: avoid it for now", "pas abordable pour l'instant : mieux vaut l'éviter")
                }
            )
        )
        add(Fact(lang.pick("Expected balance in 30 days if bought", "Solde prévu dans 30 jours après l'achat"), money(result.after.projectedBalance30Days)))
        // A small model given many numbers stitches them into claims of its own, so only the
        // findings that matter most go in, worst first. The wait is already part of the verdict.
        val top = result.findings
            .filter { it.kind != FindingKind.SUGGESTED_WAIT }
            .sortedByDescending { it.impact.ordinal }
            .take(MAX_PURCHASE_FINDINGS)
        val kinds = top.map { it.kind }.toSet()
        // A raw number next to the finding about it reads as two different claims ("only 3.9
        // months... which is comfortable"), so the raw one is only used when no finding covers it.
        if (FindingKind.LARGE_SHARE_OF_INCOME !in kinds) {
            result.shareOfMonthlyIncome?.let {
                add(Fact(lang.pick("Price as a share of monthly income", "Prix en part du revenu mensuel"), lang.percent(it)))
            }
        }
        if (kinds.none { it in BUFFER_FINDINGS }) {
            result.monthsOfBufferAfter?.let {
                add(Fact(lang.pick("Months of living costs left after buying", "Mois de dépenses restants après l'achat"), lang.oneDecimal(it)))
            }
        }
        top.mapNotNullTo(this) { finding(it, lang, money) }
        // What's owed on cards matters to any purchase, so it goes in even when it isn't in the top findings.
        if (FindingKind.CARD_BALANCE_OWED !in kinds && FindingKind.CARDS_NOT_COVERED !in kinds) {
            result.findings.firstOrNull { it.kind == FindingKind.CARD_BALANCE_OWED }?.let {
                add(Fact(lang.pick("Owed on credit cards", "Dû sur les cartes de crédit"), money(it.primary)))
            }
        }
    }

    private const val MAX_PURCHASE_FINDINGS = 3

    private val BUFFER_FINDINGS = setOf(FindingKind.BUFFER_UNDER_MONTH, FindingKind.BUFFER_THIN, FindingKind.BUFFER_COMFORTABLE)

    /**
     * One wishlist item: what it is, how far along saving for it is and, when the check could run,
     * what buying it would do.
     */
    fun wish(
        name: String,
        price: Double,
        setAside: Double,
        targetDate: LocalDate?,
        reallyNeeded: Boolean,
        cooldownDaysLeft: Int,
        result: PurchaseAffordabilityEngine.Result?,
        lang: PromptLanguage = PromptLanguage.ENGLISH,
        money: (Double) -> String
    ): List<Fact> = buildList {
        add(Fact(lang.pick("Wishlist item", "Article de la liste d'envies"), name))
        add(
            Fact(
                lang.pick("Already saved for it", "Déjà économisé pour cet article"),
                lang.pick("${money(setAside)} (of the ${money(price)} price)", "${money(setAside)} (sur un prix de ${money(price)})")
            )
        )
        targetDate?.let { add(Fact(lang.pick("Planned purchase date", "Date d'achat prévue"), lang.longDate(it))) }
        add(
            Fact(
                lang.pick("The user says it is", "Selon l'utilisateur, c'est"),
                if (reallyNeeded) lang.pick("a real need", "un vrai besoin") else lang.pick("a want, not a need", "une envie, pas un besoin")
            )
        )
        if (cooldownDaysLeft > 0) {
            add(Fact(lang.pick("Days left to sleep on it", "Jours de réflexion restants"), cooldownDaysLeft.toString()))
        }
        // The check starts with the price, which is already above.
        result?.let { r -> addAll(purchase(r, lang, money).drop(1)) }
    }

    /** The financial health score, its parts, and what would raise it. */
    fun health(score: FinancialHealthScore, lang: PromptLanguage = PromptLanguage.ENGLISH): List<Fact> = buildList {
        add(healthScoreFact(score, lang))
        val parts = listOf(
            lang.pick("Savings rate", "Taux d'épargne") to score.savingsRateScore,
            lang.pick("Emergency fund", "Fonds d'urgence") to score.emergencyFundScore,
            lang.pick("Budget adherence", "Respect des budgets") to score.budgetAdherenceScore,
            lang.pick("Goal progress", "Progrès vers les objectifs") to score.goalProgressScore,
            lang.pick("Debt management", "Gestion des dettes") to score.debtRatioScore
        )
        parts.forEach { (part, value) ->
            add(Fact(lang.pick("$part score", "Score $part"), lang.pick("$value out of 100", "$value sur 100")))
        }
        // The engine writes these in English. In French, only the ones with a known translation go
        // in; an English sentence in a French prompt comes back as franglais.
        score.topStrengths.take(3).mapNotNull { translate(it, lang) }.forEach {
            add(Fact(lang.pick("Strength", "Point fort"), it))
        }
        score.improvementAreas
            .sortedWith(compareBy<ImprovementTip> { it.priority.ordinal }.thenByDescending { it.potentialScoreGain })
            .take(3)
            .forEach { tip ->
                val title = translate(tip.title, lang) ?: return@forEach
                add(
                    Fact(
                        lang.pick("Way to improve", "Façon de s'améliorer"),
                        lang.pick("$title (up to +${tip.potentialScoreGain} points)", "$title (jusqu'à +${tip.potentialScoreGain} points)")
                    )
                )
            }
    }

    private fun healthScoreFact(score: FinancialHealthScore, lang: PromptLanguage) = Fact(
        lang.pick("Financial health score", "Score de santé financière"),
        lang.pick(
            "${score.overallScore} out of 100 (${healthLevel(score.healthLevel, lang)})",
            "${score.overallScore} sur 100 (${healthLevel(score.healthLevel, lang)})"
        )
    )

    private fun healthLevel(level: HealthLevel, lang: PromptLanguage): String = when (level) {
        HealthLevel.EXCELLENT -> lang.pick("Excellent", "Excellent")
        HealthLevel.GOOD -> lang.pick("Good", "Bon")
        HealthLevel.FAIR -> lang.pick("Fair", "Moyen")
        HealthLevel.NEEDS_WORK -> lang.pick("Needs work", "À améliorer")
        HealthLevel.CRITICAL -> lang.pick("Critical", "Critique")
    }

    /** French for the health engine's English strengths and tip titles; null when there is none. */
    private fun translate(english: String, lang: PromptLanguage): String? =
        if (lang == PromptLanguage.ENGLISH) english else HEALTH_FRENCH[english]

    private val HEALTH_FRENCH = mapOf(
        "Excellent savings rate" to "Excellent taux d'épargne",
        "Strong emergency fund" to "Solide fonds d'urgence",
        "Great budget discipline" to "Grande discipline budgétaire",
        "Solid goal progress" to "Bons progrès vers les objectifs",
        "Good debt management" to "Bonne gestion des dettes",
        "Great start on your financial journey" to "Bon départ dans votre parcours financier",
        "Grow Your Savings Habit" to "Développer l'habitude d'épargner",
        "Increase Your Savings Rate" to "Augmenter le taux d'épargne",
        "Start Building Your Emergency Fund" to "Commencer à bâtir le fonds d'urgence",
        "Build Your Emergency Fund" to "Bâtir le fonds d'urgence",
        "Set Up Your First Budgets" to "Créer les premiers budgets",
        "Improve Budget Discipline" to "Mieux respecter les budgets",
        "Create Your First Savings Goal" to "Créer un premier objectif d'épargne",
        "Accelerate Goal Progress" to "Accélérer les progrès vers les objectifs"
    )

    private fun finding(f: PurchaseAffordabilityEngine.Finding, lang: PromptLanguage, money: (Double) -> String): Fact? {
        val days = f.primary.roundToInt()
        val text = when (f.kind) {
            FindingKind.SHORT_OF_FUNDS -> lang.pick(
                "Short by ${money(f.primary)} to pay for it",
                "Il manque ${money(f.primary)} pour le payer"
            )
            FindingKind.NEGATIVE_IN_30_DAYS -> lang.pick(
                "Balance would go negative within 30 days, down to ${money(f.primary)}",
                "Le solde deviendrait négatif d'ici 30 jours, jusqu'à ${money(f.primary)}"
            )
            FindingKind.POSITIVE_IN_30_DAYS -> lang.pick(
                "Balance stays positive over the next 30 days",
                "Le solde reste positif pendant les 30 prochains jours"
            )
            FindingKind.ESSENTIAL_BILL_UNCOVERED -> lang.pick(
                "An essential bill couldn't be covered in $days days",
                "Une facture essentielle ne pourrait pas être payée dans $days jours"
            )
            FindingKind.LOW_BALANCE_SOON -> lang.pick(
                "Balance would drop to ${money(f.primary)} in ${f.secondary.roundToInt()} days",
                "Le solde baisserait à ${money(f.primary)} dans ${f.secondary.roundToInt()} jours"
            )
            FindingKind.OVER_SAFE_TO_SPEND -> lang.pick(
                "${money(f.primary)} over the safe-to-spend amount of ${money(f.secondary)}",
                "${money(f.primary)} de plus que le montant dépensable sans risque de ${money(f.secondary)}"
            )
            FindingKind.WITHIN_SAFE_TO_SPEND -> lang.pick(
                "Fits within the safe-to-spend amount of ${money(f.primary)}",
                "Entre dans le montant dépensable sans risque de ${money(f.primary)}"
            )
            FindingKind.BUFFER_UNDER_MONTH -> lang.pick(
                "Less than a month of living costs would be left",
                "Il resterait moins d'un mois de dépenses"
            )
            FindingKind.BUFFER_THIN -> lang.pick(
                "Only ${lang.oneDecimal(f.primary)} months of living costs would be left",
                "Il ne resterait que ${lang.oneDecimal(f.primary)} mois de dépenses"
            )
            FindingKind.BUFFER_COMFORTABLE -> lang.pick(
                "A comfortable ${lang.oneDecimal(f.primary)} months of living costs would be left",
                "Il resterait ${lang.oneDecimal(f.primary)} mois de dépenses, ce qui est confortable"
            )
            FindingKind.LARGE_SHARE_OF_INCOME -> lang.pick(
                "The price is ${lang.percent(f.primary)} of a month's income",
                "Le prix représente ${lang.percent(f.primary)} d'un mois de revenu"
            )
            FindingKind.DIPS_INTO_SAVINGS -> lang.pick(
                "It would take ${money(f.primary)} from savings accounts",
                "Il faudrait prendre ${money(f.primary)} dans les comptes d'épargne"
            )
            FindingKind.SAVING_TAX_ADDED -> lang.pick(
                "Sparely's saving tax adds ${money(f.primary)} set aside into vaults",
                "La taxe d'épargne de Sparely met ${money(f.primary)} de côté dans les coffres"
            )
            FindingKind.SAVING_TAX_SKIPPED -> lang.pick(
                "${money(f.primary)} of the usual saving couldn't be set aside",
                "${money(f.primary)} de l'épargne habituelle ne pourrait pas être mis de côté"
            )
            FindingKind.SAVINGS_DEPOSITS_AT_RISK -> lang.pick(
                "About ${money(f.primary)} of planned vault saving might be missed",
                "Environ ${money(f.primary)} d'épargne prévue dans les coffres pourrait être manquée"
            )
            FindingKind.CARD_BALANCE_OWED -> lang.pick(
                "${money(f.primary)} is owed on credit cards",
                "${money(f.primary)} sont dus sur les cartes de crédit"
            )
            FindingKind.CARDS_NOT_COVERED -> lang.pick(
                "The ${money(f.primary)} card balance wouldn't be covered afterwards",
                "Le solde de carte de ${money(f.primary)} ne serait plus couvert ensuite"
            )
            FindingKind.EMERGENCY_FUND_BELOW_TARGET -> lang.pick(
                "Emergency fund is at ${lang.percent(f.primary)} of its target",
                "Le fonds d'urgence est à ${lang.percent(f.primary)} de son objectif"
            )
            FindingKind.INCOME_UNSTABLE -> lang.pick(
                "Income varies from month to month",
                "Le revenu varie d'un mois à l'autre"
            )
            FindingKind.HEALTH_SCORE_LOW -> lang.pick(
                "Financial health score is low ($days out of 100)",
                "Le score de santé financière est bas ($days sur 100)"
            )
            FindingKind.BUDGETS_OVER -> lang.pick(
                "Some budgets are already over their limit",
                "Certains budgets sont déjà dépassés"
            )
            FindingKind.COULD_PAY_TWICE -> lang.pick(
                "Available money covers the price at least twice",
                "L'argent disponible couvre le prix au moins deux fois"
            )
            FindingKind.SUGGESTED_WAIT -> return null
            FindingKind.SAVE_UP_PLAN -> lang.pick(
                "Could be saved up in $days months by setting aside ${money(f.secondary)} a month",
                "Pourrait être économisé en $days mois en mettant ${money(f.secondary)} de côté par mois"
            )
            FindingKind.NO_MONTHLY_SURPLUS -> lang.pick(
                "Nothing is left over each month to save toward it",
                "Il ne reste rien chaque mois pour économiser en vue de cet achat"
            )
        }
        // The engine already rates each finding; passing that on keeps the model from guessing
        // whether a number is good or bad news.
        val label = when (f.impact) {
            PurchaseAffordabilityEngine.Impact.OK -> lang.pick("Good sign", "Bon signe")
            PurchaseAffordabilityEngine.Impact.CAUTION -> lang.pick("Watch out", "Attention")
            PurchaseAffordabilityEngine.Impact.PROBLEM -> lang.pick("Problem", "Problème")
        }
        return Fact(label, text)
    }

    private fun categoryName(category: ExpenseCategory, lang: PromptLanguage): String = when (category) {
        ExpenseCategory.GROCERIES -> lang.pick("Groceries", "épicerie")
        ExpenseCategory.DINING -> lang.pick("Dining", "restaurants")
        ExpenseCategory.TRANSPORTATION -> lang.pick("Transportation", "transport")
        ExpenseCategory.ENTERTAINMENT -> lang.pick("Entertainment", "divertissement")
        ExpenseCategory.UTILITIES -> lang.pick("Utilities", "services publics")
        ExpenseCategory.HEALTH -> lang.pick("Health", "santé")
        ExpenseCategory.EDUCATION -> lang.pick("Education", "éducation")
        ExpenseCategory.SHOPPING -> lang.pick("Shopping", "magasinage")
        ExpenseCategory.TRAVEL -> lang.pick("Travel", "voyages")
        ExpenseCategory.OTHER -> lang.pick("Other", "autres")
    }

    private fun signedPercent(value: Double, lang: PromptLanguage): String {
        val rounded = value.roundToInt()
        val amount = lang.percent(abs(rounded) / 100.0)
        return if (rounded > 0) lang.pick("up $amount", "en hausse de $amount") else lang.pick("down $amount", "en baisse de $amount")
    }
}
