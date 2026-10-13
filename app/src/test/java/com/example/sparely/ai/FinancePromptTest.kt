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
            replyLanguage = "French"
        )
        assertTrue(prompt.contains("- Dining this month: $412 of $300 budget"))
        assertTrue(prompt.contains("Task: Summarize the month."))
        assertTrue(prompt.contains("User question: Am I spending too much?"))
        assertTrue(prompt.contains("Reply in French."))
    }

    @Test
    fun saysWhenThereAreNoFacts() {
        val prompt = FinancePrompt.build(task = "Summarize.", facts = emptyList())
        assertTrue(prompt.contains("Facts:\n(none)"))
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
