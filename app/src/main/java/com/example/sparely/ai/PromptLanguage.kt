package com.example.sparely.ai

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The language of everything the model reads. A small model copies phrases from its prompt into
 * the reply, so a French reply needs a fully French prompt: English facts come back as franglais.
 */
enum class PromptLanguage(private val locale: Locale) {
    ENGLISH(Locale.ENGLISH),
    FRENCH(Locale.CANADA_FRENCH);

    /** [en] or [fr], whichever this language is. */
    fun pick(en: String, fr: String): String = if (this == FRENCH) fr else en

    /** "$1,640.09" or "1 640,09 $". */
    fun money(value: Double, symbol: String): String {
        val safe = if (value.isNaN() || value.isInfinite() || (value > -0.005 && value < 0.0)) 0.0 else value
        val number = String.format(locale, "%,.2f", kotlin.math.abs(safe))
        val sign = if (safe < 0) "-" else ""
        return if (this == FRENCH) "$sign$number $symbol" else "$sign$symbol$number"
    }

    /** "50%" or "50 %". */
    fun percent(ratio: Double): String {
        val value = (ratio * 100).roundToInt()
        return if (this == FRENCH) "$value %" else "$value%"
    }

    /** "3.9" or "3,9". */
    fun oneDecimal(value: Double): String = String.format(locale, "%.1f", value)

    /** "November 8, 2026" or "8 novembre 2026": an ISO date gets copied into the reply as is. */
    fun longDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern(pick("MMMM d, yyyy", "d MMMM yyyy"), locale))

    companion object {
        /** From the app's language setting, e.g. "fr" or "fr-CA". Anything else gets English. */
        fun fromCode(languageCode: String): PromptLanguage =
            if (languageCode.lowercase().startsWith("fr")) FRENCH else ENGLISH
    }
}

/** What a card asks the model to write, in each language. */
enum class AiTask(private val en: String, private val fr: String) {
    SUMMARY(
        "Summarize how the user's finances are going in 2-3 short sentences.",
        "Résume en 2 ou 3 phrases courtes comment vont les finances de l'utilisateur."
    ),
    QUESTION(
        "Answer the user's question directly, starting with the answer, in 1-3 short sentences.",
        "Réponds directement à la question de l'utilisateur, en commençant par la réponse, en 1 à 3 phrases courtes."
    ),
    INSIGHTS(
        "Summarize how the user's month is going in 2-3 short sentences, then give one practical tip based only on the facts.",
        "Résume en 2 ou 3 phrases courtes comment se passe le mois de l'utilisateur, puis donne un conseil pratique basé seulement sur les faits."
    ),
    BUDGET(
        "Summarize how the user's budgets are doing this month in 2-3 short sentences, naming the ones that need attention.",
        "Résume en 2 ou 3 phrases courtes comment vont les budgets de l'utilisateur ce mois-ci, en nommant ceux qui demandent de l'attention."
    ),
    PURCHASE(
        "The user checked if they can afford a purchase. The verdict is already shown to them, so don't repeat it. In 2 short " +
            "sentences, explain why, using one or two key numbers (like the balance after buying or months of living costs). " +
            "If there's a waiting period, say it's a pause to be sure they still want it, not a money problem. " +
            "Never contradict the verdict: if it says affordable, never call buying a bad idea or a bad time.",
        "L'utilisateur a vérifié s'il peut se permettre un achat. Le verdict lui est déjà affiché : ne le répète pas. En 2 phrases " +
            "courtes, explique pourquoi avec un ou deux chiffres clés (comme le solde après l'achat ou les mois de dépenses). " +
            "S'il y a une période de réflexion, dis que c'est une pause pour être sûr de le vouloir, pas un problème d'argent. " +
            "Ne contredis jamais le verdict : s'il dit abordable, ne dis jamais que l'achat est une mauvaise idée ou un mauvais moment."
    ),
    WISH(
        "The user is thinking about buying this wishlist item. The verdict is already shown to them, so don't repeat it. In 2 " +
            "short sentences, explain why, using one or two key numbers (like the balance after buying or how much is already " +
            "saved). If there's a reflection pause, say how many days are left and that it's a pause to be sure, not a money " +
            "problem. Never contradict the verdict: if it says affordable, never call buying a bad idea or a bad time.",
        "L'utilisateur pense acheter cet article de sa liste d'envies. Le verdict lui est déjà affiché : ne le répète pas. En 2 " +
            "phrases courtes, explique pourquoi avec un ou deux chiffres clés (comme le solde après l'achat ou le montant déjà " +
            "économisé). S'il y a une pause de réflexion, dis combien de jours il reste et que c'est une pause pour être sûr, pas " +
            "un problème d'argent. Ne contredis jamais le verdict : s'il dit abordable, ne dis jamais que l'achat est une mauvaise " +
            "idée ou un mauvais moment."
    ),
    BRIEF(
        "In 1 or 2 short sentences (at most 35 words), tell the user what deserves their attention today, most important " +
            "first, and suggest one concrete action if a fact calls for one. Don't mention their balance or safe-to-spend " +
            "unless a fact is about it.",
        "En 1 ou 2 phrases courtes (au plus 35 mots), dis à l'utilisateur ce qui mérite son attention aujourd'hui, le plus " +
            "important d'abord, et propose une action concrète si un fait le demande. Ne parle pas de son solde ni du montant " +
            "dépensable sans risque, sauf si un fait en parle."
    ),
    VAULTS(
        "In 2-3 short sentences, tell the user how their savings goals are going: the overall progress, the goal that " +
            "needs the most attention and why, and what monthly amount would keep it on track.",
        "En 2 ou 3 phrases courtes, dis à l'utilisateur comment vont ses objectifs d'épargne : le progrès global, " +
            "l'objectif qui demande le plus d'attention et pourquoi, et le montant mensuel qui le garderait dans les temps."
    ),
    RECURRING(
        "In 2-3 short sentences, tell the user what their recurring payments cost each month and per year, which ones " +
            "weigh the most, and one they could review to save money.",
        "En 2 ou 3 phrases courtes, dis à l'utilisateur combien ses paiements récurrents coûtent par mois et par année, " +
            "lesquels pèsent le plus, et un qu'il pourrait revoir pour économiser."
    ),
    HEALTH(
        "Explain the user's financial health score in 2-3 short sentences: what's going well, and the one change that would raise it most.",
        "Explique en 2 ou 3 phrases courtes le score de santé financière de l'utilisateur : ce qui va bien, et le changement qui l'améliorerait le plus."
    );

    fun text(language: PromptLanguage): String = language.pick(en, fr)
}
