package com.example.sparely.domain.logic

import com.example.sparely.domain.model.AllocationBreakdown
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsPercentages
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Random
import kotlin.math.abs

/**
 * Held-out backtest on simulated spending: project the final month as of a mid-month day using
 * only earlier data, and compare against the month's true total.
 */
class ProjectionBacktestTest {

    private enum class Scenario { STABLE, SPENDING_JUMP, ERRATIC_BIG_PURCHASES }

    private var nextId = 1L

    private fun expense(date: LocalDate, amount: Double, recurring: Boolean) = Expense(
        id = nextId++,
        description = "e",
        amount = amount,
        category = ExpenseCategory.SHOPPING,
        date = date,
        includesTax = false,
        allocation = AllocationBreakdown(0.0, 0.0, 0.0, 0.0, 0.0),
        appliedPercentages = SavingsPercentages(0.0, 0.0, 0.0),
        autoRecommended = false,
        riskLevelUsed = RiskLevel.BALANCED,
        isRecurring = recurring
    )

    private fun simulate(scenario: Scenario, seed: Long, months: Int, lastMonth: YearMonth): List<Expense> {
        val random = Random(seed)
        val list = mutableListOf<Expense>()
        for (i in months - 1 downTo 0) {
            val month = lastMonth.minusMonths(i.toLong())
            val monthIndex = months - 1 - i
            list += expense(month.atDay(1), 1200.0, recurring = true)
            list += expense(month.atDay(15), 90.0, recurring = true)
            val level = if (scenario == Scenario.SPENDING_JUMP && monthIndex >= months - 2) 1.7 else 1.0
            for (day in 1..month.lengthOfMonth()) {
                if (random.nextDouble() < 0.6) {
                    list += expense(month.atDay(day), (10 + random.nextDouble() * 50) * level, recurring = false)
                }
                val bigChance = if (scenario == Scenario.ERRATIC_BIG_PURCHASES) 0.05 else 0.0
                if (random.nextDouble() < bigChance) {
                    list += expense(month.atDay(day), 150 + random.nextDouble() * 350, recurring = false)
                }
            }
        }
        return list
    }

    private data class Result(val newError: Double, val linearError: Double, val runs: Int)

    private fun backtest(scenario: Scenario, asOfDay: Int): Result {
        val target = YearMonth.of(2026, 9)
        var newError = 0.0
        var linearError = 0.0
        var runs = 0
        for (seed in 1L..60L) {
            val all = simulate(scenario, seed, months = 9, lastMonth = target)
            val asOf = target.atDay(asOfDay)
            val actual = all.filter { YearMonth.from(it.date) == target }.sumOf { it.amount }
            val known = all.filter { !it.date.isAfter(asOf) }

            newError += abs(ProjectionMath.monthEndProjection(known, asOf).expected - actual)
            linearError += abs(
                ProjectionMath.linearMonthEndProjection(
                    known.filter { YearMonth.from(it.date) == target }.sumOf { it.amount },
                    asOfDay,
                    target.lengthOfMonth()
                ) - actual
            )
            runs++
        }
        return Result(newError / runs, linearError / runs, runs)
    }

    private fun report(scenario: Scenario, asOfDay: Int): Result {
        val r = backtest(scenario, asOfDay)
        println("BACKTEST %-22s day %2d  new avg error %8.2f  linear avg error %8.2f  (%.0f%% of linear)".format(
            scenario, asOfDay, r.newError, r.linearError, 100 * r.newError / r.linearError
        ))
        return r
    }

    @Test
    fun `stable spending - history is much more accurate than linear`() {
        for (day in listOf(5, 10, 20)) {
            val r = report(Scenario.STABLE, day)
            assertTrue("day $day: new ${r.newError} vs linear ${r.linearError}", r.newError < r.linearError * 0.6)
        }
    }

    @Test
    fun `spending jump - never meaningfully worse than linear`() {
        for (day in listOf(5, 10, 20)) {
            val r = report(Scenario.SPENDING_JUMP, day)
            assertTrue("day $day: new ${r.newError} vs linear ${r.linearError}", r.newError <= r.linearError * 1.05)
        }
    }

    @Test
    fun `erratic big purchases - never meaningfully worse than linear`() {
        for (day in listOf(5, 10, 20)) {
            val r = report(Scenario.ERRATIC_BIG_PURCHASES, day)
            assertTrue("day $day: new ${r.newError} vs linear ${r.linearError}", r.newError <= r.linearError * 1.05)
        }
    }
}
