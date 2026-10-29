package com.example.sparely.domain.logic

import com.example.sparely.domain.model.DetectedRecurringTransaction
import com.example.sparely.domain.model.RecurringExpense

/**
 * Which saved recurring payment a repeating pattern found in the expenses belongs to, for the
 * expenses that aren't tied to one by id yet (logged by hand before the payment was saved, or
 * from before expenses carried the id). Once tied, the id decides and the name no longer matters:
 * renaming "Claude Pro subscription" to "Claude Pro" keeps its expenses with it.
 */
object DetectedRecurringMatch {

    private fun normalize(text: String) = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** Equal, or one is the other with words added ("claude pro" in "claude pro subscription"). */
    fun namesMatch(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val (short, long) = if (x.length <= y.length) x to y else y to x
        // Too short to mean anything on its own ("tv", "gas")
        if (short.length < 4) return false
        return " $long ".contains(" $short ")
    }

    /** The saved payment (running or paused) whose name is this pattern's, if any. */
    fun owner(detected: DetectedRecurringTransaction, saved: List<RecurringExpense>): RecurringExpense? =
        saved.firstOrNull { namesMatch(detected.description, it.description) }

    /** The patterns no saved payment owns. */
    fun unsaved(detected: List<DetectedRecurringTransaction>, saved: List<RecurringExpense>) =
        detected.filter { owner(it, saved) == null }
}
