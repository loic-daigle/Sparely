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
     * @param task what to produce, e.g. "Summarize how this month is going in 2-3 sentences."
     * @param facts figures the answer may use; nothing else is available to the model
     * @param question the user's own question, if any
     * @param replyLanguage language name in English, e.g. "French"
     */
    fun build(
        task: String,
        facts: List<Fact>,
        question: String? = null,
        replyLanguage: String = "English"
    ): String = buildString {
        appendLine("You are the assistant inside Sparely, a personal budgeting app.")
        appendLine("Rules:")
        appendLine("- Use only the facts below. Never invent, estimate or recalculate numbers.")
        appendLine("- If the facts don't answer the question, say you don't have that information.")
        appendLine("- Don't give investment, tax or legal advice.")
        appendLine("- Plain text only, no Markdown. Be brief and friendly.")
        appendLine("- Reply in $replyLanguage.")
        appendLine()
        appendLine("Facts:")
        if (facts.isEmpty()) {
            appendLine("(none)")
        } else {
            facts.forEach { appendLine("- ${oneLine(it.label)}: ${oneLine(it.value)}") }
        }
        appendLine()
        appendLine("Task: ${oneLine(task)}")
        if (!question.isNullOrBlank()) {
            appendLine("User question: ${oneLine(question)}")
        }
    }.trimEnd()

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
