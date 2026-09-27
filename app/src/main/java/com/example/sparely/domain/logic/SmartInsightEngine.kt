package com.example.sparely.domain.logic

import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.VaultType
import com.example.sparely.domain.model.balanceAfterMonths
import com.example.sparely.ui.utils.roundToTwoDecimals
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.max

/**
 * Engine for generating smart insights including recurring pattern detection,
 * idle money analysis, seasonal predictions, and unique expense identification.
 */
object SmartInsightEngine {

    // === Data Classes ===

    enum class PatternFrequency(val label: String, val maxDaysBetween: Int) {
        DAILY("Daily", 2),
        EVERY_FEW_DAYS("Every few days", 5),
        WEEKLY("Weekly", 9),
        BIWEEKLY("Biweekly", 17),
        MONTHLY("Monthly", 35)
    }

    data class RecurringPatternInsight(
        val description: String,
        val averageAmount: Double,
        val frequency: PatternFrequency,
        val confidence: Double, // 0.0 to 1.0
        val category: ExpenseCategory,
        val lastSeen: LocalDate,
        val predictedNextOccurrence: LocalDate?,
        val totalMonthlyImpact: Double,
        val occurrenceCount: Int
    )

    enum class UniqueReason {
        UNUSUALLY_LARGE,
        MAJOR_PURCHASE
    }

    data class UniqueExpenseInsight(
        val expense: Expense,
        val reasonType: UniqueReason,
        val zScore: Double = 0.0,
        val isLikelyOneTime: Boolean,
        val comparisonAmount: Double // Average for comparison
    )

    data class IdleMoneyInsight(
        val currentBalance: Double,
        val projectedMonthlyExpenses: Double,
        val recommendedReserve: Double, // 1.5x monthly expenses
        val excessAmount: Double,
        val suggestedTransferAmount: Double,
        val suggestedVault: SmartVault?,
        val estimatedMonthlyInterest: Double,
        val estimatedAnnualInterest: Double,
        val estimatedMonthlySavings: Double = 0.0,
        val growthProjections: Map<Int, Double> = emptyMap() // Months -> Projected Balance
    )

    data class LowBalanceInsight(
        val currentBalance: Double,
        val minRequiredBalance: Double,
        val shortfall: Double,
        val suggestedSourceVaultId: Long?,
        val suggestedSourceVaultName: String?
    )

    data class SeasonalInsight(
        val category: ExpenseCategory,
        val currentMonth: Int,
        val month: Int,
        val expectedChangePercent: Double, // Positive = higher, negative = lower
        val adjustedBudgetSuggestion: Double,
        val historicalMonthlyAverage: Double,
        val isHighSeason: Boolean
    )

    // === Default Constants ===
    
    private const val DEFAULT_APY = 4.5 // 4.5% APY for high-yield savings estimate
    private const val IDLE_MONEY_MULTIPLIER = 1.5 // Keep 1.5x monthly expenses as reserve
    private const val MIN_IDLE_TRANSFER = 50.0 // Minimum suggested transfer amount

    // === Core Analysis Functions ===

    /**
     * Detect recurring spending patterns like daily coffee, weekly groceries, etc.
     * Uses clustering by description and interval analysis.
     */
    fun detectRecurringPatterns(
        expenses: List<Expense>,
        lookbackDays: Int = 90
    ): List<RecurringPatternInsight> {
        if (expenses.isEmpty()) return emptyList()

        val cutoff = LocalDate.now().minusDays(lookbackDays.toLong())
        val recentExpenses = expenses.filter { !it.date.isBefore(cutoff) }

        // Group by normalized description
        val grouped = recentExpenses.groupBy { normalizeDescription(it.description) }

        return grouped.mapNotNull { (description, cluster) ->
            detectPatternInCluster(description, cluster)
        }
            .filter { it.confidence >= 0.5 } // Only confident patterns
            .sortedByDescending { it.totalMonthlyImpact }
            .take(10) // Top 10 patterns
    }

    private fun detectPatternInCluster(
        description: String,
        cluster: List<Expense>
    ): RecurringPatternInsight? {
        if (cluster.size < 3) return null // Need at least 3 occurrences

        val sorted = cluster.sortedBy { it.date }
        val intervals = sorted.zipWithNext { a, b ->
            ChronoUnit.DAYS.between(a.date, b.date).toInt()
        }.filter { it > 0 }

        if (intervals.isEmpty()) return null

        // Calculate average interval and variance
        val avgInterval = intervals.average()
        val variance = intervals.map { (it - avgInterval) * (it - avgInterval) }.average()
        val stdDev = sqrt(variance)

        // Determine frequency based on average interval
        val frequency = when {
            avgInterval <= 2 -> PatternFrequency.DAILY
            avgInterval <= 5 -> PatternFrequency.EVERY_FEW_DAYS
            avgInterval <= 9 -> PatternFrequency.WEEKLY
            avgInterval <= 17 -> PatternFrequency.BIWEEKLY
            avgInterval <= 35 -> PatternFrequency.MONTHLY
            else -> return null // Too infrequent
        }

        // Calculate confidence based on consistency
        val coefficientOfVariation = if (avgInterval > 0) stdDev / avgInterval else 1.0
        val confidence = (1.0 - coefficientOfVariation.coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)

        val averageAmount = cluster.map { it.amount }.average().roundToTwoDecimals()
        val lastSeen = sorted.last().date

        // Predict next occurrence
        val predictedNext = lastSeen.plusDays(avgInterval.toLong().coerceAtLeast(1))
            .takeIf { it.isAfter(LocalDate.now()) || it.isEqual(LocalDate.now()) }

        // Calculate monthly impact
        val occurrencesPerMonth = when (frequency) {
            PatternFrequency.DAILY -> 30.0
            PatternFrequency.EVERY_FEW_DAYS -> 30.0 / avgInterval
            PatternFrequency.WEEKLY -> 4.33
            PatternFrequency.BIWEEKLY -> 2.17
            PatternFrequency.MONTHLY -> 1.0
        }
        val monthlyImpact = (averageAmount * occurrencesPerMonth).roundToTwoDecimals()

        return RecurringPatternInsight(
            description = sorted.first().description, // Use original description
            averageAmount = averageAmount,
            frequency = frequency,
            confidence = confidence,
            category = sorted.first().category,
            lastSeen = lastSeen,
            predictedNextOccurrence = predictedNext,
            totalMonthlyImpact = monthlyImpact,
            occurrenceCount = cluster.size
        )
    }

    private fun normalizeDescription(description: String): String {
        return description.trim().lowercase()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("#\\d+"), "") // Remove order numbers
            .replace(Regex("\\d{4,}"), "") // Remove long numbers
            .trim()
    }

    /**
     * Detect unique/one-time expenses that are unusually large or first-time.
     * These should not affect recurring budget predictions.
     */
    fun detectUniqueExpenses(
        expenses: List<Expense>,
        lookbackDays: Int = 180,
        // Statistics use the full lookback; this only limits which purchases are reported.
        reportWithinDays: Int = lookbackDays
    ): List<UniqueExpenseInsight> {
        if (expenses.size < 10) return emptyList()

        val cutoff = LocalDate.now().minusDays(lookbackDays.toLong())
        val recentExpenses = expenses.filter { !it.date.isBefore(cutoff) }

        // Calculate category-level statistics
        val categoryStats = recentExpenses.groupBy { it.category }
            .mapValues { (_, categoryExpenses) ->
                val amounts = categoryExpenses.map { it.amount }
                val mean = amounts.average()
                val stdDev = calculateStdDev(amounts, mean)
                Pair(mean, stdDev)
            }

        val uniqueExpenses = mutableListOf<UniqueExpenseInsight>()

        for (expense in recentExpenses) {
            val (mean, stdDev) = categoryStats[expense.category] ?: continue

            // Check if significantly larger than average (3+ standard deviations)
            val zScore = if (stdDev > 0) (expense.amount - mean) / stdDev else 0.0

            when {
                // Very large expense (3+ std devs above mean)
                zScore >= 3.0 && expense.amount >= 200 -> {
                    uniqueExpenses.add(
                        UniqueExpenseInsight(
                            expense = expense,
                            reasonType = UniqueReason.UNUSUALLY_LARGE,
                            zScore = zScore,
                            isLikelyOneTime = true,
                            comparisonAmount = mean
                        )
                    )
                }
                // First time in this category at this amount level
                expense.amount >= mean * 5 && expense.amount >= 500 -> {
                    uniqueExpenses.add(
                        UniqueExpenseInsight(
                            expense = expense,
                            reasonType = UniqueReason.MAJOR_PURCHASE,
                            isLikelyOneTime = true,
                            comparisonAmount = mean
                        )
                    )
                }
            }
        }

        val reportCutoff = LocalDate.now().minusDays(reportWithinDays.toLong())
        return uniqueExpenses
            .filter { !it.expense.date.isBefore(reportCutoff) }
            .distinctBy { it.expense.id }
            .sortedByDescending { it.expense.amount }
            .take(5)
    }

    private fun calculateStdDev(values: List<Double>, mean: Double): Double {
        if (values.size < 2) return 0.0
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

    /**
     * Filters out ignored expenses and statistical anomalies (amount > 2.5 std devs above the
     * overall mean), leaving the set that's safe to feed into burn-rate / month-end / trend
     * predictions without a single large one-off expense skewing them.
     */
    fun filterPredictionExpenses(expenses: List<Expense>): List<Expense> {
        val amounts = expenses.map { it.amount }
        val mean = if (amounts.isNotEmpty()) amounts.average() else 0.0
        val stdDev = if (amounts.size > 1) calculateStdDev(amounts, mean) else 0.0

        return expenses.filter { expense ->
            val isAnomaly = stdDev > 0.0 && (expense.amount - mean) / stdDev > 2.5
            !expense.isIgnored && !isAnomaly
        }
    }

    /**
     * Analyze if there's excess money in the account that could be earning interest.
     */
    fun analyzeIdleMoney(
        currentBalance: Double,
        expenses: List<Expense>,
        vaults: List<SmartVault>,
        minMainAccountBalance: Double = 0.0,
        monthlyIncome: Double = 0.0,
        mainOverflowAccountId: Long? = null,
        defaultApy: Double = DEFAULT_APY
    ): IdleMoneyInsight? {
        if (currentBalance <= 0) return null

        // Calculate average monthly expenses from last 3 months
        val threeMonthsAgo = LocalDate.now().minusMonths(3)
        val recentExpenses = expenses.filter { !it.date.isBefore(threeMonthsAgo) }

        if (recentExpenses.isEmpty()) return null

        // Filter out one-time large expenses to get accurate average
        val uniqueExpenses = detectUniqueExpenses(recentExpenses)
        val uniqueExpenseIds = uniqueExpenses.map { it.expense.id }.toSet()
        val filteredExpenses = recentExpenses.filter { it.id !in uniqueExpenseIds }

        val monthlyExpenses = filteredExpenses.groupBy { YearMonth.from(it.date) }
            .mapValues { (_, monthExpenses) -> monthExpenses.sumOf { it.amount }.roundToTwoDecimals() }
            .values.toList()

        val avgMonthlyExpenses = if (monthlyExpenses.isNotEmpty()) {
            monthlyExpenses.average().roundToTwoDecimals()
        } else {
            return null
        }

        // Recommended reserve = 1.5x monthly expenses OR user defined min balance, whichever is higher
        val recommendedReserve = max(avgMonthlyExpenses * IDLE_MONEY_MULTIPLIER, minMainAccountBalance).roundToTwoDecimals()
        val excessAmount = (currentBalance - recommendedReserve).coerceAtLeast(0.0).roundToTwoDecimals()

        // Only suggest transfer if excess is significant
        if (excessAmount < MIN_IDLE_TRANSFER) return null

        // Round suggested transfer to nearest $25
        val suggestedTransfer = ((excessAmount / 25.0).toInt() * 25.0).roundToTwoDecimals()

        // Find best vault for transfer (prefer high-yield, then by priority)
        val suggestedVault = vaults
            .filter { !it.archived }
            .sortedWith(
                compareByDescending<SmartVault> { it.id == mainOverflowAccountId }
                    .thenByDescending { it.type == VaultType.HIGH_YIELD_SAVINGS }
                    .thenByDescending { it.annualPercentageYield ?: 0.0 }
                    .thenByDescending { it.priority.ordinal }
            )
            .firstOrNull()

        // Calculate projected interest
        val effectiveApy = suggestedVault?.effectiveApy ?: defaultApy
        val projectedAnnual = (suggestedTransfer * (effectiveApy / 100.0)).roundToTwoDecimals()
        val projectedMonthly = (projectedAnnual / 12.0).roundToTwoDecimals()

        // Compound interest projections (Balance after 6, 12, 24 months)
        val estimatedMonthlySavings = (monthlyIncome - avgMonthlyExpenses).coerceAtLeast(0.0).roundToTwoDecimals()
        val growthProjections = mutableMapOf<Int, Double>()
        val startBalanceForProjection = (suggestedVault?.currentBalance ?: 0.0) + suggestedTransfer
        
        listOf(6, 12, 24).forEach { months ->
            val projectedVal = if (suggestedVault != null) {
                suggestedVault.copy(currentBalance = startBalanceForProjection)
                    .balanceAfterMonths(months, estimatedMonthlySavings).roundToTwoDecimals()
            } else {
                // Simple monthly compounding fallback if no vault
                var bal = startBalanceForProjection
                val monthlyRate = effectiveApy / 100.0 / 12.0
                repeat(months) {
                    bal += estimatedMonthlySavings
                    bal *= (1 + monthlyRate)
                }
                bal.roundToTwoDecimals()
            }
            growthProjections[months] = projectedVal
        }

        return IdleMoneyInsight(
            currentBalance = currentBalance,
            projectedMonthlyExpenses = avgMonthlyExpenses,
            recommendedReserve = recommendedReserve,
            excessAmount = excessAmount,
            suggestedTransferAmount = suggestedTransfer,
            suggestedVault = suggestedVault,
            estimatedMonthlyInterest = projectedMonthly,
            estimatedAnnualInterest = projectedAnnual,
            estimatedMonthlySavings = estimatedMonthlySavings,
            growthProjections = growthProjections
        )
    }

    /**
     * Get seasonal spending insights for the current month.
     * Identifies categories that historically spike or dip in current month.
     */
    fun getSeasonalInsights(
        expenses: List<Expense>,
        currentMonth: Int = LocalDate.now().monthValue,
        minMonthsOfData: Int = 6
    ): List<SeasonalInsight> {
        // Need at least 6 months of data for meaningful seasonal analysis
        val monthsOfData = expenses.map { YearMonth.from(it.date) }.distinct().size
        if (monthsOfData < minMonthsOfData) return emptyList()

        // Analyze each category
        val categoryInsights = ExpenseCategory.entries.mapNotNull { category ->
            val categoryExpenses = expenses.filter { it.category == category }
            if (categoryExpenses.size < 12) return@mapNotNull null // Need decent sample

            // Group by calendar month and calculate averages
            val byMonth = categoryExpenses.groupBy { it.date.monthValue }
                .mapValues { (_, monthExpenses) ->
                    // Average per occurrence in that month across years
                    val monthlyTotals = monthExpenses.groupBy { YearMonth.from(it.date) }
                        .values.map { it.sumOf { e -> e.amount } }
                    monthlyTotals.average()
                }

            if (byMonth.size < 6) return@mapNotNull null // Need data for multiple months

            val overallAverage = byMonth.values.average()
            val currentMonthAverage = byMonth[currentMonth] ?: return@mapNotNull null

            // Calculate percentage deviation
            val deviation = if (overallAverage > 0) {
                ((currentMonthAverage - overallAverage) / overallAverage) * 100
            } else {
                0.0
            }

            // Only report significant deviations (>15%)
            if (abs(deviation) < 15) return@mapNotNull null

            SeasonalInsight(
                category = category,
                currentMonth = currentMonth,
                month = currentMonth,
                expectedChangePercent = deviation,
                adjustedBudgetSuggestion = currentMonthAverage,
                historicalMonthlyAverage = overallAverage,
                isHighSeason = deviation > 0
            )
        }

        return categoryInsights
            .sortedByDescending { abs(it.expectedChangePercent) }
            .take(5)
    }

    /**
     * Detect if main account balance is below minimum and suggest top-up.
     */
    fun detectLowBalance(
        currentBalance: Double,
        minMainAccountBalance: Double,
        vaults: List<SmartVault>,
        mainOverflowAccountId: Long?
    ): LowBalanceInsight? {
        if (minMainAccountBalance <= 0.0) return null
        if (currentBalance >= minMainAccountBalance) return null

        val shortfall = minMainAccountBalance - currentBalance
        
        // Find best source vault (HISA main first, then any HISA)
        val sourceVault = if (mainOverflowAccountId != null) {
            vaults.find { it.id == mainOverflowAccountId }
        } else {
            vaults.filter { !it.archived && it.currentBalance >= shortfall }
                .maxByOrNull { it.currentBalance }
                ?: vaults.filter { !it.archived && it.type == VaultType.HIGH_YIELD_SAVINGS }
                    .maxByOrNull { it.currentBalance }
        }

        return LowBalanceInsight(
            currentBalance = currentBalance,
            minRequiredBalance = minMainAccountBalance,
            shortfall = shortfall,
            suggestedSourceVaultId = sourceVault?.id,
            suggestedSourceVaultName = sourceVault?.name
        )
    }

    /**
     * Combined analysis that returns all insights at once.
     */
    data class SmartInsightBundle(
        val recurringPatterns: List<RecurringPatternInsight>,
        val uniqueExpenses: List<UniqueExpenseInsight>,
        val idleMoneyInsight: IdleMoneyInsight?,
        val seasonalInsights: List<SeasonalInsight>,
        val lowBalanceInsight: LowBalanceInsight? = null
    )

    fun analyzeAll(
        expenses: List<Expense>,
        currentBalance: Double,
        vaults: List<SmartVault>,
        minMainAccountBalance: Double = 0.0,
        monthlyIncome: Double = 0.0,
        mainOverflowAccountId: Long? = null
    ): SmartInsightBundle {
        return SmartInsightBundle(
            recurringPatterns = detectRecurringPatterns(expenses),
            uniqueExpenses = detectUniqueExpenses(expenses),
            idleMoneyInsight = analyzeIdleMoney(currentBalance, expenses, vaults, minMainAccountBalance, monthlyIncome, mainOverflowAccountId),
            seasonalInsights = getSeasonalInsights(expenses),
            lowBalanceInsight = detectLowBalance(currentBalance, minMainAccountBalance, vaults, mainOverflowAccountId)
        )
    }
}
