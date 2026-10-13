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
        assertTrue(prompt.contains("Question de l'utilisateur : Am I spending too much?"))
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
}
