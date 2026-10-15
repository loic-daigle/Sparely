package com.example.sparely.ai

/**
 * Turns the model's raw reply into plain text for the UI. Small models like to add Markdown,
 * labels and quotes even when asked not to, and Compose `Text` would show those literally.
 */
object AnswerFormatter {

    const val DEFAULT_MAX_CHARS = 1_200

    private val LEADING_LABEL = Regex("^(answer|summary|response|réponse|résumé)\\s*:\\s*", RegexOption.IGNORE_CASE)
    private val HEADING = Regex("^#{1,6}\\s+", RegexOption.MULTILINE)
    private val BULLET = Regex("^\\s*[*\\-+]\\s+", RegexOption.MULTILINE)
    private val EMPHASIS = Regex("(\\*\\*|__|`)")
    private val TRAILING_SPACES = Regex("[ \\t]+$", RegexOption.MULTILINE)
    private val EXTRA_BLANK_LINES = Regex("\\n{3,}")
    private val SENTENCE_END = Regex("[.!?](\\s|$)")
    private val SENTENCE_SPLIT = Regex("(?<=[.!?])\\s+")
    private val NOT_A_LETTER_OR_DIGIT = Regex("[^\\p{L}\\p{N}]+")

    fun clean(raw: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        var text = raw.replace("\r\n", "\n").trim()
        text = text.replace(LEADING_LABEL, "")
        text = text.replace(HEADING, "")
        text = text.replace(BULLET, "• ")
        text = text.replace(EMPHASIS, "")
        text = text.replace(TRAILING_SPACES, "")
        text = text.replace(EXTRA_BLANK_LINES, "\n\n")
        text = unquote(text.trim())
        text = dropRepeatedSentences(text)
        return truncate(text, maxChars)
    }

    /**
     * Small models sometimes say the same sentence twice ("It's a want, not a need. It's a want, not a
     * need."). Drops a sentence only when it repeats an earlier one word for word (ignoring case and
     * punctuation); everything else is left as written.
     */
    private fun dropRepeatedSentences(text: String): String = text.lines().joinToString("\n") { line ->
        val seen = mutableSetOf<String>()
        line.split(SENTENCE_SPLIT).filter { sentence ->
            val key = sentence.lowercase().replace(NOT_A_LETTER_OR_DIGIT, " ").trim()
            key.isEmpty() || seen.add(key)
        }.joinToString(" ")
    }

    private fun unquote(text: String): String {
        if (text.length < 2) return text
        val pairs = listOf('"' to '"', '“' to '”', '«' to '»')
        val pair = pairs.firstOrNull { (open, close) -> text.first() == open && text.last() == close }
            ?: return text
        val inner = text.substring(1, text.length - 1)
        // Leave "a" and "b" alone: only strip when the quotes wrap the whole reply.
        return if (pair.first in inner || pair.second in inner) text else inner.trim()
    }

    /** Cuts long replies at the last full sentence that fits, or at a word if there is none. */
    private fun truncate(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val head = text.substring(0, maxChars)
        val lastSentenceEnd = SENTENCE_END.findAll(head).lastOrNull()?.range?.first
        if (lastSentenceEnd != null && lastSentenceEnd >= maxChars / 2) {
            return head.substring(0, lastSentenceEnd + 1)
        }
        val lastSpace = head.lastIndexOf(' ').takeIf { it > 0 } ?: maxChars
        return head.substring(0, lastSpace).trimEnd() + "…"
    }
}
