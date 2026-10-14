package com.example.sparely.ai

/** One figure the app already calculated, such as "Dining this month" to "$412 of $300 budget". */
data class Fact(val label: String, val value: String)

/**
 * Builds prompts where the app supplies the numbers and the model only puts them into words.
 * Small models get arithmetic wrong and invent figures, so every number must come from [Fact]s
 * calculated in Kotlin.
 */
object FinancePrompt {

    /**
     * @param task what to produce, e.g. [AiTask.SUMMARY] in [language]
     * @param facts figures the answer may use; nothing else is available to the model
     * @param question the user's own question, if any
     * @param language the language of the whole prompt, and so of the reply
     */
    fun build(
        task: String,
        facts: List<Fact>,
        question: String? = null,
        language: PromptLanguage = PromptLanguage.ENGLISH
    ): String = if (question.isNullOrBlank()) summary(task, facts, language) else answer(task, facts, question, language)

    private fun summary(task: String, facts: List<Fact>, l: PromptLanguage): String = buildString {
        appendLine(l.pick("You are the assistant inside Sparely, a personal budgeting app.", "Tu es l'assistant de Sparely, une application de budget personnel."))
        appendLine(l.pick("Rules:", "Règles :"))
        appendLine(l.pick("- Use only the facts below. Never invent, estimate or recalculate numbers.", "- Utilise seulement les faits ci-dessous. N'invente, n'estime et ne recalcule jamais de chiffres."))
        appendLine(l.pick("- Write every amount exactly as in the facts, with its currency symbol (for example $1,640.09).", "- Écris chaque montant exactement comme dans les faits, avec le symbole de la devise (par exemple 1 640,09 $)."))
        appendLine(l.pick("- Don't give investment, tax or legal advice.", "- Ne donne pas de conseils de placement, d'impôt ou juridiques."))
        appendLine(l.pick("- Talk to the user directly (\"you\"). Never write \"the app says\" or \"according to the facts\".", "- Parle directement à l'utilisateur en le vouvoyant. N'écris jamais « l'application dit » ou « selon les faits »."))
        appendLine(l.pick("- Don't list the facts back. Explain what the 1-2 most important numbers mean for the user.", "- Ne répète pas la liste des faits. Explique ce que les 1 ou 2 chiffres les plus importants veulent dire pour l'utilisateur."))
        appendLine(l.pick("- Plain text only, no Markdown. Be brief and friendly.", "- Texte simple seulement, sans Markdown. Sois bref et amical."))
        appendLine(l.pick("- Reply in English only.", "- Réponds uniquement en français, sans aucun mot anglais."))
        appendLine()
        appendFacts(facts, l)
        appendLine()
        appendLine(l.pick("Task: ", "Tâche : ") + oneLine(task))
    }.trimEnd()

    /**
     * A question gets its own layout. Tacked onto the end of a summary prompt, a small model
     * mostly follows the summary rules and the long facts list and never answers the question, so
     * here the question comes first, the rules are about answering it, and it's repeated last,
     * right where the model starts writing.
     */
    private fun answer(task: String, facts: List<Fact>, question: String, l: PromptLanguage): String = buildString {
        val q = oneLine(question)
        appendLine(l.pick("You are the assistant inside Sparely, a personal budgeting app.", "Tu es l'assistant de Sparely, une application de budget personnel."))
        appendLine(l.pick("The user asks: ", "L'utilisateur demande : ") + q)
        appendLine()
        appendLine(l.pick("Rules:", "Règles :"))
        appendLine(l.pick("- Answer only this question. Don't summarize the user's finances.", "- Réponds seulement à cette question. Ne fais pas de résumé des finances de l'utilisateur."))
        appendLine(l.pick("- Give the answer in your first sentence, then at most one or two short sentences of explanation.", "- Donne la réponse dans ta première phrase, puis au plus une ou deux phrases courtes d'explication."))
        appendLine(l.pick("- Use only the facts below. Never invent, estimate or recalculate numbers.", "- Utilise seulement les faits ci-dessous. N'invente, n'estime et ne recalcule jamais de chiffres."))
        appendLine(l.pick("- If the facts don't answer the question, say so in one sentence.", "- Si les faits ne répondent pas à la question, dis-le en une phrase."))
        appendLine(l.pick("- Write every amount exactly as in the facts, with its currency symbol (for example $1,640.09).", "- Écris chaque montant exactement comme dans les faits, avec le symbole de la devise (par exemple 1 640,09 $)."))
        appendLine(l.pick("- Don't give investment, tax or legal advice.", "- Ne donne pas de conseils de placement, d'impôt ou juridiques."))
        appendLine(l.pick("- Talk to the user directly (\"you\"). Plain text only, no Markdown.", "- Parle directement à l'utilisateur en le vouvoyant. Texte simple seulement, sans Markdown."))
        appendLine(l.pick("- Reply in English only.", "- Réponds uniquement en français, sans aucun mot anglais."))
        appendLine()
        appendFacts(facts, l)
        appendLine()
        appendLine(l.pick("Task: ", "Tâche : ") + oneLine(task))
        appendLine(l.pick("Question to answer: ", "Question à laquelle répondre : ") + q)
    }.trimEnd()

    private fun StringBuilder.appendFacts(facts: List<Fact>, l: PromptLanguage) {
        appendLine(l.pick("Facts:", "Faits :"))
        if (facts.isEmpty()) {
            appendLine(l.pick("(none)", "(aucun)"))
        } else {
            // French puts a space before the colon.
            val separator = l.pick(": ", " : ")
            facts.forEach { appendLine("- ${oneLine(it.label)}$separator${oneLine(it.value)}") }
        }
    }

    /**
     * [facts] as plain sentences, for the summarizer, which takes text to condense rather than
     * instructions. "Dining this month: $412 of $300 budget. Savings rate: 18%."
     */
    fun factsAsText(facts: List<Fact>): String =
        facts.joinToString(" ") { fact ->
            val sentence = "${oneLine(fact.label)}: ${oneLine(fact.value)}"
            if (sentence.endsWith('.') || sentence.endsWith('!') || sentence.endsWith('?')) sentence else "$sentence."
        }

    // Keeps user-written text (expense titles, the question) from breaking the prompt's layout.
    private fun oneLine(text: String): String = text.replace(Regex("\\s+"), " ").trim()
}
