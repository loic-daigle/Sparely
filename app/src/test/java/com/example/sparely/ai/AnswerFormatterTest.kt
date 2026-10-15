package com.example.sparely.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerFormatterTest {

    @Test
    fun stripsMarkdownAndLabels() {
        val raw = "Summary: ## This month\n* **Dining** is over budget\n- `Groceries` are fine\n\n\n\nKeep going!  "
        assertEquals(
            "This month\n• Dining is over budget\n• Groceries are fine\n\nKeep going!",
            AnswerFormatter.clean(raw)
        )
    }

    @Test
    fun removesQuotesWrappingTheWholeReply() {
        assertEquals("You're on track.", AnswerFormatter.clean("\"You're on track.\""))
        assertEquals("Tout va bien.", AnswerFormatter.clean("« Tout va bien. »"))
        assertEquals("\"a\" and \"b\"", AnswerFormatter.clean("\"a\" and \"b\""))
    }

    @Test
    fun handlesFrenchLabels() {
        assertEquals("Vous dépensez moins.", AnswerFormatter.clean("Réponse : Vous dépensez moins."))
    }

    @Test
    fun cutsLongRepliesAtASentence() {
        val raw = "First sentence is here. Second sentence is here. Third one runs on and on"
        assertEquals("First sentence is here. Second sentence is here.", AnswerFormatter.clean(raw, maxChars = 60))
    }

    @Test
    fun cutsAtAWordWhenNoSentenceFits() {
        val cleaned = AnswerFormatter.clean("one two three four five six", maxChars = 12)
        assertEquals("one two…", cleaned)
        assertTrue(cleaned.length <= 12)
    }

    @Test
    fun blankStaysBlank() {
        assertEquals("", AnswerFormatter.clean("  \n "))
    }

    @Test
    fun dropsASentenceRepeatedWordForWord() {
        assertEquals(
            "Vous avez indiqué que c'est une envie. C'est une envie, pas un besoin.",
            AnswerFormatter.clean("Vous avez indiqué que c'est une envie. C'est une envie, pas un besoin. C'est une envie, pas un besoin.")
        )
    }

    @Test
    fun keepsSimilarButDifferentSentences() {
        val raw = "You can afford it. You can afford it next month too."
        assertEquals(raw, AnswerFormatter.clean(raw))
    }
}
