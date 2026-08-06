package com.example.sparely.domain.logic

import com.example.sparely.domain.model.SuggestionConfidence

/**
 * Shared math for "how much will this total by month-end" projections, used by every engine that
 * does day-of-month linear extrapolation (SpendingPatternEngine, BudgetEngine).
 */
object ProjectionMath {

    /**
     * Linearly extrapolates an amount accumulated so far this month to a full-month total,
     * assuming spending continues at today's average daily pace.
     */
    fun linearMonthEndProjection(amountSoFar: Double, dayOfMonth: Int, daysInMonth: Int): Double {
        val safeDayOfMonth = dayOfMonth.coerceAtLeast(1)
        val dailyRate = amountSoFar / safeDayOfMonth
        return dailyRate * daysInMonth
    }

    /**
     * How much to trust a day-of-month linear projection: it's noisy early in the month
     * (a single transaction can dominate) and settles down as more of the month has elapsed.
     */
    fun confidenceForDayOfMonth(dayOfMonth: Int): SuggestionConfidence = when {
        dayOfMonth >= 20 -> SuggestionConfidence.HIGH
        dayOfMonth >= 10 -> SuggestionConfidence.MEDIUM
        else -> SuggestionConfidence.LOW
    }
}
