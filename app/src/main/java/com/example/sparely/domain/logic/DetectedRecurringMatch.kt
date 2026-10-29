package com.example.sparely.domain.logic

import com.example.sparely.domain.model.DetectedRecurringTransaction
import com.example.sparely.domain.model.RecurringExpense
import kotlin.math.abs

/**
 * Whether a repeating payment found in the expenses is already a saved recurring payment. Names
 * alone aren't enough: renaming "Claude Pro subscription" to "Claude Pro" left the old expenses
 * looking like a new, unsaved subscription. So a saved payment also covers a pattern when one name
 * holds the other as whole words, when it's the same store at about the same amount, or when it's
 * the same category, amount and rhythm under another name.
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

    private fun closeAmount(amount: Double, reference: Double, tolerance: Double) =
        reference > 0 && abs(amount - reference) <= reference * tolerance

    fun isCovered(detected: DetectedRecurringTransaction, saved: RecurringExpense): Boolean {
        if (namesMatch(detected.description, saved.description)) return true
        // A store can sell more than one subscription, so the amount has to agree too
        if (detected.storeId != null && detected.storeId == saved.storeId &&
            closeAmount(detected.averageAmount, saved.amount, 0.25)
        ) return true
        val interval = saved.frequency.daysInterval
        val cadenceFits = abs(detected.cadenceDays - interval) <= maxOf(3, interval / 10)
        return detected.suggestedCategory == saved.category && cadenceFits &&
            closeAmount(detected.averageAmount, saved.amount, 0.10)
    }

    /** The patterns that no saved recurring payment (running or paused) covers. */
    fun unsaved(detected: List<DetectedRecurringTransaction>, saved: List<RecurringExpense>) =
        detected.filter { pattern -> saved.none { isCovered(pattern, it) } }
}
