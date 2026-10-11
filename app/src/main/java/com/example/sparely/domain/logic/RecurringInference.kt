package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RecurringExpense
import java.time.LocalDate
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

    /** A bill found only from the expense history, with the next date it is expected. */
    data class InferredBill(
        val description: String,
        val amount: Double,
        val category: ExpenseCategory,
        val dueDate: LocalDate
    )

    /**
     * Bills that repeat in the history but have no active recurring entry behind them, projected
     * forward to their next date within [horizonDays]. Bills already covered by an active recurring
     * entry are left out, since the forecast schedules those itself. A bill that is overdue by more
     * than half its cadence is treated as stopped; one overdue by less is expected today.
     */
    fun upcomingInferredBills(
        expenses: List<Expense>,
        recurring: List<RecurringExpense>,
        today: LocalDate,
        horizonDays: Int = 30
    ): List<InferredBill> {
        val active = recurring.filter { it.isActive }
        val candidates = expenses.filter { !it.isIgnored && !it.isRecurring && !it.date.isAfter(today) }
        val coveredIds = matchKnownBills(candidates, active)
        val activeNames = active.flatMap { listOf(normalize(it.description), normalize(it.merchantName)) }
            .filter { it.isNotEmpty() }.toSet()

        return patternClusters(candidates.filter { it.id !in coveredIds })
            .filter { normalize(it.expenses.first().description) !in activeNames }
            .mapNotNull { cluster ->
                val latest = cluster.expenses.maxBy { it.date }
                val interval = cluster.medianInterval.toLong()
                // Bills fall on a set day of the month, so step by calendar months rather than a day count
                val expected = when (cluster.medianInterval) {
                    in 26.0..35.0 -> latest.date.plusMonths(1)
                    in 85.0..95.0 -> latest.date.plusMonths(3)
                    in 355.0..375.0 -> latest.date.plusYears(1)
                    else -> latest.date.plusDays(interval)
                }
                val due = when {
                    !expected.isBefore(today) -> expected
                    ChronoUnit.DAYS.between(expected, today) <= interval / 2 -> today
                    else -> return@mapNotNull null
                }
                if (ChronoUnit.DAYS.between(today, due) > horizonDays) return@mapNotNull null
                InferredBill(latest.description, cluster.medianAmount, latest.category, due)
            }
            .sortedBy { it.dueDate }
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
    private fun detectPatterns(candidates: List<Expense>): Set<Long> =
        patternClusters(candidates).flatMap { cluster -> cluster.expenses.map { it.id } }.toSet()

    private class PatternCluster(val expenses: List<Expense>, val medianInterval: Double, val medianAmount: Double)

    private fun patternClusters(candidates: List<Expense>): List<PatternCluster> =
        candidates
            .filter { normalize(it.description).isNotEmpty() }
            .groupBy { normalize(it.description) }
            .values
            .filter { it.size >= MIN_OCCURRENCES }
            .mapNotNull { cluster ->
                val sorted = cluster.sortedBy { it.date }
                val intervals = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date).toInt() }
                    .filter { it > 0 }
                if (intervals.size < MIN_OCCURRENCES - 1) return@mapNotNull null

                val medianInterval = median(intervals.map { it.toDouble() })
                if (medianInterval < MIN_CADENCE_DAYS) return@mapNotNull null
                // Bills fall due on a set day, so tolerate a few days of drift but not a wandering date
                val slack = (medianInterval * 0.25).coerceIn(2.0, 5.0)
                val regularShare = intervals.count { abs(it - medianInterval) <= slack }.toDouble() / intervals.size
                if (regularShare < REGULAR_SHARE) return@mapNotNull null

                val medianAmount = median(cluster.map { it.amount })
                if (medianAmount <= 0) return@mapNotNull null
                val isBillCadence = medianInterval in 26.0..35.0 ||
                    medianInterval in 85.0..95.0 ||
                    medianInterval in 355.0..375.0
                val tolerance = if (isBillCadence) VARIABLE_BILL_AMOUNT_TOLERANCE else FIXED_AMOUNT_TOLERANCE
                val similarShare = cluster.count { abs(it.amount - medianAmount) <= medianAmount * tolerance }
                    .toDouble() / cluster.size
                if (similarShare < REGULAR_SHARE) return@mapNotNull null

                PatternCluster(sorted, medianInterval, medianAmount)
            }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }
}
