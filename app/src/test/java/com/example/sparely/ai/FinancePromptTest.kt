package com.example.sparely.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class FinancePromptTest {

    @Test
    fun includesFactsTaskQuestionAndLanguage() {
        val prompt = FinancePrompt.build(
            task = "Summarize the month.",
            facts = listOf(Fact("Dining this month", "$412 of $300 budget")),
            question = "Am I\nspending too much?",
            language = PromptLanguage.FRENCH
        )
        assertTrue(prompt.contains("- Dining this month : $412 of $300 budget"))
        assertTrue(prompt.contains("Tâche : Summarize the month."))
        assertTrue(prompt.contains("L'utilisateur demande : Am I spending too much?"))
        assertTrue(prompt.endsWith("Question à laquelle répondre : Am I spending too much?"))
        assertTrue(prompt.contains("Réponds uniquement en français"))
        assertTrue(prompt.contains("avec le symbole de la devise"))
        // A French reply needs a fully French prompt: no English rules for the model to copy.
        assertFalse(prompt.contains("Rules:"))
        assertFalse(prompt.contains("Use only the facts"))
    }

    @Test
    fun saysWhenThereAreNoFacts() {
        val prompt = FinancePrompt.build(task = "Summarize.", facts = emptyList())
        assertTrue(prompt.contains("Facts:\n(none)"))
        assertTrue(prompt.contains("Reply in English only."))
        assertFalse(prompt.contains("User question"))
    }

    @Test
    fun factsAsTextMakesSentences() {
        val text = FinancePrompt.factsAsText(
            listOf(Fact("Dining this month", "$412 of $300 budget"), Fact("Note", "Spending is down."))
        )
        assertEquals("Dining this month: $412 of $300 budget. Note: Spending is down.", text)
    }

    @Test
    fun questionsGetTheirOwnLayout() {
        val prompt = FinancePrompt.build(
            task = "Answer it.",
            facts = listOf(Fact("Dining budget", "$310 of $250")),
            question = "How much is left for dining?"
        )
        val lines = prompt.lines()
        // The question comes before the facts, and again last, right where the model starts writing.
        assertEquals("The user asks: How much is left for dining?", lines[1])
        assertEquals("Question to answer: How much is left for dining?", lines.last())
        assertTrue(prompt.contains("Answer only this question. Don't summarize the user's finances."))
        // The summary-style rule that made answers read like summaries is left out.
        assertFalse(prompt.contains("most important numbers"))
    }
}
