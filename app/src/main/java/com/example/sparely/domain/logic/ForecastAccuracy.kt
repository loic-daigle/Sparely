package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Expense
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Measures how well the 30-day spending forecast has done on this user's own history, by replaying
 * it: at past dates, forecast the next 30 days of day-to-day spending using only what was known then,
 * and compare with what was actually spent. Bills are left out, since their amounts and dates are
 * known up front; the uncertainty is in everyday spending.
 *
 * Nothing is stored: the replay runs on the expense history, so it works from the first launch on
 * existing data and always reflects the current engine.
 */
object ForecastAccuracy {

    const val HORIZON_DAYS = 30
    private const val STEP_DAYS = 7
    private const val MAX_SAMPLES = 12
    private const val MIN_SAMPLES = 4
    /** A forecast made with less history than this is a guess, not something worth grading. */
    private const val MIN_HISTORY_DAYS = 14
    private const val BUFFER_PERCENTILE = 0.8

    data class Result(
        /** Past 30-day windows the forecast was checked against. */
        val samples: Int,
        /** Typical size of the miss as a share of what was spent (0.12 = off by 12%). Median, so one odd month can't dominate. */
        val typicalErrorShare: Double,
        /** Typical direction of the miss as a share of what was spent: positive when forecasts ran high, negative when low. */
        val bias: Double,
        /**
         * Spending above the forecast that 80% of past windows stayed within. This is how much
         * cushion the user actually needed, so it sizes the safety buffer. 0 when forecasts ran high.
         */
        val overspendP80: Double
    )

    /**
     * Replays [dailyRate] (spend per day, given the history known as of a date) over past windows.
     * Null when there are fewer than [MIN_SAMPLES] windows to check, i.e. under about two months of history.
     */
    fun measure(
        expenses: List<Expense>,
        today: LocalDate,
        dailyRate: (known: List<Expense>, asOf: LocalDate) -> Double
    ): Result? {
        val usable = expenses.filter { !it.isIgnored }
        val trackingStart = usable.minOfOrNull { it.date } ?: return null

        val misses = mutableListOf<Double>() // actual - forecast
        val shares = mutableListOf<Double>()  // (forecast - actual) / actual
        for (k in 0 until MAX_SAMPLES) {
            val asOf = today.minusDays(HORIZON_DAYS + k * STEP_DAYS.toLong())
            if (asOf.isBefore(trackingStart.plusDays(MIN_HISTORY_DAYS.toLong()))) break

            val known = usable.filter { !it.date.isAfter(asOf) }
            val forecast = dailyRate(known, asOf) * HORIZON_DAYS
            val windowEnd = asOf.plusDays(HORIZON_DAYS.toLong())
            val actual = usable
                .filter { !it.countsAsRecurring && it.date.isAfter(asOf) && !it.date.isAfter(windowEnd) }
                .sumOf { it.amount }
            if (forecast <= 0.0 && actual <= 0.0) continue

            misses += actual - forecast
            if (actual > 0.0) shares += (forecast - actual) / actual
        }
        if (misses.size < MIN_SAMPLES || shares.isEmpty()) return null

        return Result(
            samples = misses.size,
            typicalErrorShare = median(shares.map { abs(it) }),
            bias = median(shares),
            overspendP80 = percentile(misses, BUFFER_PERCENTILE).coerceAtLeast(0.0)
        )
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    /** Nearest-rank percentile. */
    private fun percentile(values: List<Double>, p: Double): Double {
        val sorted = values.sorted()
        val rank = ceil(p * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}
