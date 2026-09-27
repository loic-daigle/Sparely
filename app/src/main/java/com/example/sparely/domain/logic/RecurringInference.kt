package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.RecurringExpense
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Finds expenses that behave like recurring bills (rent, subscriptions, insurance) even though they
 * weren't logged through a recurring entry: older data, or bills entered by hand. The result feeds
 * projections only and is never stored.
 */
object RecurringInference {

    private const val MIN_OCCURRENCES = 3
    private const val MIN_CADENCE_DAYS = 6
    private const val FIXED_AMOUNT_TOLERANCE = 0.20
    private const val VARIABLE_BILL_AMOUNT_TOLERANCE = 0.60
    private const val KNOWN_BILL_AMOUNT_TOLERANCE = 0.15
    private const val KNOWN_VARIABLE_BILL_TOLERANCE = 0.60
    private const val REGULAR_SHARE = 0.75

    fun annotate(expenses: List<Expense>, recurring: List<RecurringExpense>): List<Expense> {
        val ids = inferRecurringIds(expenses, recurring)
        if (ids.isEmpty()) return expenses
        return expenses.map { if (it.id in ids) it.copy(looksRecurring = true) else it }
    }

    fun inferRecurringIds(expenses: List<Expense>, recurring: List<RecurringExpense>): Set<Long> {
        val candidates = expenses.filter { !it.isIgnored && !it.isRecurring }
        if (candidates.isEmpty()) return emptySet()

        val result = mutableSetOf<Long>()
        result += matchKnownBills(candidates, recurring)
        result += detectPatterns(candidates)
        return result
    }

    private fun normalize(text: String?): String = text?.trim()?.lowercase().orEmpty()

    private fun matchKnownBills(candidates: List<Expense>, recurring: List<RecurringExpense>): Set<Long> {
        if (recurring.isEmpty()) return emptySet()
        val byName = recurring.flatMap { bill ->
            listOf(normalize(bill.description), normalize(bill.merchantName))
                .filter { it.isNotEmpty() }
                .map { it to bill }
        }.groupBy({ it.first }, { it.second })

        return candidates.filter { expense ->
            val sameName = byName[normalize(expense.description)].orEmpty()
            val sameStore = recurring.filter {
                it.storeId != null && it.storeId == expense.storeId && it.category == expense.category
            }
            (sameName + sameStore).any { bill -> amountMatchesBill(expense.amount, bill) }
        }.map { it.id }.toSet()
    }

    private fun amountMatchesBill(amount: Double, bill: RecurringExpense): Boolean {
        val reference = if (bill.isVariableAmount && bill.amountHistory.isNotEmpty()) {
            bill.amountHistory.map { it.amount }.average()
        } else {
            bill.amount
        }
        if (reference <= 0) return false
        val tolerance = if (bill.isVariableAmount) KNOWN_VARIABLE_BILL_TOLERANCE else KNOWN_BILL_AMOUNT_TOLERANCE
        return abs(amount - reference) <= reference * tolerance
    }

    /**
     * Same description, at least 3 times, spaced at a steady interval of about a week or more, with
     * similar amounts. Daily habits (coffee) and irregular repeat purchases (groceries with varying
     * totals) don't qualify. Monthly-ish/quarterly/yearly cadences may vary more in amount, since
     * utility bills do.
     */
    private fun detectPatterns(candidates: List<Expense>): Set<Long> {
        val flagged = mutableSetOf<Long>()
        candidates
            .filter { normalize(it.description).isNotEmpty() }
            .groupBy { normalize(it.description) }
            .values
            .filter { it.size >= MIN_OCCURRENCES }
            .forEach { cluster ->
                val sorted = cluster.sortedBy { it.date }
                val intervals = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date).toInt() }
                    .filter { it > 0 }
                if (intervals.size < MIN_OCCURRENCES - 1) return@forEach

                val medianInterval = median(intervals.map { it.toDouble() })
                if (medianInterval < MIN_CADENCE_DAYS) return@forEach
                // Bills fall due on a set day, so tolerate a few days of drift but not a wandering date
                val slack = (medianInterval * 0.25).coerceIn(2.0, 5.0)
                val regularShare = intervals.count { abs(it - medianInterval) <= slack }.toDouble() / intervals.size
                if (regularShare < REGULAR_SHARE) return@forEach

                val medianAmount = median(cluster.map { it.amount })
                if (medianAmount <= 0) return@forEach
                val isBillCadence = medianInterval in 26.0..35.0 ||
                    medianInterval in 85.0..95.0 ||
                    medianInterval in 355.0..375.0
                val tolerance = if (isBillCadence) VARIABLE_BILL_AMOUNT_TOLERANCE else FIXED_AMOUNT_TOLERANCE
                val similarShare = cluster.count { abs(it.amount - medianAmount) <= medianAmount * tolerance }
                    .toDouble() / cluster.size
                if (similarShare < REGULAR_SHARE) return@forEach

                cluster.forEach { flagged += it.id }
            }
        return flagged
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }
}
