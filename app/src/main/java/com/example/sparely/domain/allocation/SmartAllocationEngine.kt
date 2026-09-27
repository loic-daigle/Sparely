package com.example.sparely.domain.allocation

import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.VaultType
import com.example.sparely.domain.model.monthsUntil
import com.example.sparely.ui.utils.roundToTwoDecimals
import com.example.sparely.ui.utils.formatCurrency
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * SmartAllocationEngine: Fully adaptive, self-adjusting vault allocation system.
 * Automatically balances vault contributions with daily liquidity needs.
 */
object SmartAllocationEngine {


    data class AllocationInput(
        val vaults: List<SmartVault>,
        val monthlyIncome: Double,
        val mainAccountBalance: Double,
        val safeBufferPercent: Double = 0.45,
        val today: LocalDate = LocalDate.now(),
        val rampWindowMonths: Int = 3,
        val recentMonthlyExpenses: List<Double> = emptyList(),
        val minBufferPercent: Double = 0.35,
        val maxAllocationPercent: Double = 0.65,
        val pendingContributions: Map<Long, Double> = emptyMap(),
        val mainOverflowAccountId: Long? = null
    )

    data class AllocationResult(
        val allocations: Map<Long, Double>,
        val totalAllocated: Double,
        val archiveVaultIds: List<Long>,
        val adjustedBufferPercent: Double,
        val mainAccountSafetyMargin: Double,
        val allocationDetails: Map<Long, AllocationDetail>
    )

    data class AllocationDetail(
        val vaultId: Long,
        val vaultName: String,
        val amount: Double,
        val urgencyScore: Double,
        val desiredAmount: Double,
        val priorityWeight: Double,
        val reason: String
    )

    /**
     * Compute adaptive monthly allocations that balance vault goals with daily liquidity.
     */
    fun allocate(input: AllocationInput): AllocationResult {
    // Exclude archived vaults and any vaults that opted out of automatic income
    val activeVaults = input.vaults.filter { !it.archived && it.allowAutoIncome }

        // 1. Identify completed vaults for archival
        val archiveIds = activeVaults
            .filter { it.targetAmount > 0.0 && it.currentBalance >= it.targetAmount }
            .map { it.id }

        // 2. Calculate adaptive buffer based on actual spending behavior
        val adaptiveBuffer = calculateAdaptiveBuffer(
            baseBufferPercent = input.safeBufferPercent,
            recentExpenses = input.recentMonthlyExpenses,
            monthlyIncome = input.monthlyIncome,
            mainAccountBalance = input.mainAccountBalance,
            minBuffer = input.minBufferPercent,
            maxAllocation = input.maxAllocationPercent
        )

        // 3. Determine available funds for allocation
        val bufferAmount = input.monthlyIncome * adaptiveBuffer.adjustedBufferPercent
        val currentShortfall = max(0.0, bufferAmount - input.mainAccountBalance)
        var availableForVaults = (input.monthlyIncome - currentShortfall).coerceIn(
            0.0,
            input.monthlyIncome * input.maxAllocationPercent
        )
        
        // Safety check: ensure minimum liquidity
        if (availableForVaults <= 0.0 || activeVaults.isEmpty()) {
            return AllocationResult(
                allocations = emptyMap(),
                totalAllocated = 0.0,
                archiveVaultIds = archiveIds,
                adjustedBufferPercent = adaptiveBuffer.adjustedBufferPercent,
                mainAccountSafetyMargin = input.mainAccountBalance - bufferAmount,
                allocationDetails = emptyMap()
            )
        }

        // 4. Apply spending-based adjustment
        availableForVaults *= adaptiveBuffer.allocationMultiplier
        
        // 5. Compute vault priorities and desired amounts
        val pendingByVault = input.pendingContributions

        val vaultStates = activeVaults.map { vault ->
            val pending = pendingByVault[vault.id] ?: 0.0
            val desiredMonthly = computeDesiredMonthly(
                vault = vault,
                today = input.today,
                rampWindowMonths = input.rampWindowMonths,
                pendingAmount = pending,
                monthlyIncome = input.monthlyIncome
            )
            
            val remainingNeed = if (vault.monthlyNeed != null) {
                if (vault.targetAmount > 0.0) {
                    (vault.targetAmount - (vault.currentBalance + pending)).coerceAtLeast(0.0)
                } else {
                    (vault.monthlyNeed * input.rampWindowMonths - (vault.currentBalance + pending)).coerceAtLeast(0.0)
                }
            } else {
                (vault.targetAmount - (vault.currentBalance + pending)).coerceAtLeast(0.0)
            }

            VaultState(
                vault = vault,
                urgency = computeUrgency(
                    vault = vault,
                    today = input.today,
                    monthlyIncome = input.monthlyIncome,
                    desiredMonthly = desiredMonthly
                ),
                desiredMonthly = desiredMonthly,
                priorityWeight = vault.priorityWeight.takeIf { it > 0.0 } ?: 1.0,
                pendingAmount = pending,
                remainingNeed = remainingNeed
            )
        }.sortedByDescending { it.effectivePriority }

        // 6. CRITICAL CHANGE: Tiered allocation based on urgency thresholds
        val allocations = mutableMapOf<Long, Double>()
        val details = mutableMapOf<Long, AllocationDetail>()
        var remaining = availableForVaults

        // TIER 1: CRITICAL urgency (≥15.0) - Flow goals starting in 0-1 months with high income pressure
        val criticalVaults = vaultStates.filter { it.urgency >= 15.0 && it.remainingNeed > 0.0 }
        remaining = allocateToTier(criticalVaults, remaining, allocations, details, "Critical: imminent flow goal")

        // TIER 2: HIGH urgency (≥8.0) - Flow goals starting in 2-3 months OR fixed goals due soon
        val highUrgencyVaults = vaultStates.filter { 
            it.vault.id !in allocations && it.urgency >= 8.0 && it.remainingNeed > 0.0 
        }
        remaining = allocateToTier(highUrgencyVaults, remaining, allocations, details, "High urgency")

        // TIER 3: MODERATE urgency (≥4.0) - Flow goals 4-6 months out OR standard fixed goals
        val moderateVaults = vaultStates.filter { 
            it.vault.id !in allocations && it.urgency >= 4.0 && it.remainingNeed > 0.0 
        }
        remaining = allocateToTier(moderateVaults, remaining, allocations, details, "Moderate urgency")

        // TIER 4: LOW urgency (<4.0) - Long-term goals
        val lowUrgencyVaults = vaultStates.filter { 
            it.vault.id !in allocations && it.remainingNeed > 0.0 
        }
        remaining = allocateToTier(lowUrgencyVaults, remaining, allocations, details, "Building long-term goal")

        // 7. OVERFLOW: If a HISA Main Account is designated, dump any remaining available funds there
        if (remaining > 0.1 && input.mainOverflowAccountId != null) {
            val hisaId = input.mainOverflowAccountId
            // Check if this vault is active
            if (activeVaults.any { it.id == hisaId }) {
                val currentAlloc = allocations[hisaId] ?: 0.0
                allocations[hisaId] = currentAlloc + remaining
                
                // Add or update detail
                val state = vaultStates.find { it.vault.id == hisaId }
                if (state != null) {
                    details[hisaId] = createAllocationDetail(
                        state = state,
                        amount = allocations[hisaId]!!,
                        reasonSuffix = "Overflow from unused income"
                    )
                }
                remaining = 0.0
            }
        }

        // Round and finalize
        val finalAllocations = allocations.mapValues { (_, amount) ->
            amount.roundToTwoDecimals()
        }

        val totalAllocated = finalAllocations.values.sum()
        val safetyMargin = input.mainAccountBalance + (input.monthlyIncome - totalAllocated) - bufferAmount

        return AllocationResult(
            allocations = finalAllocations,
            totalAllocated = totalAllocated,
            archiveVaultIds = archiveIds,
            adjustedBufferPercent = adaptiveBuffer.adjustedBufferPercent,
            mainAccountSafetyMargin = safetyMargin,
            allocationDetails = details
        )
    }

    /**
     * Allocate funds to a tier of vaults based on their priority
     */
    private fun allocateToTier(
        vaults: List<VaultState>,
        availableFunds: Double,
        allocations: MutableMap<Long, Double>,
        details: MutableMap<Long, AllocationDetail>,
        reasonSuffix: String
    ): Double {
        if (vaults.isEmpty() || availableFunds <= 0.0) return availableFunds
        
        var remainingFunds = availableFunds
        val tempAllocations = mutableMapOf<Long, Double>()
        
        // Iterative distribution to handle surpluses
        // We loop until no funds remain, no vaults need more, or we can't distribute further
        var iterations = 0
        while (remainingFunds > 0.01 && iterations < 10) {
            iterations++
            
            // 1. Identify candidates that still need money
            val candidates = vaults.filter { state ->
                val currentAlloc = tempAllocations[state.vault.id] ?: 0.0
                val cap = min(state.desiredMonthly, state.remainingNeed)
                cap - currentAlloc > 0.01
            }
            
            if (candidates.isEmpty()) break
            
            // 2. Calculate proportional shares of the CURRENT remaining funds
            // Note: We used to calculate based on availableFunds in the first pass, 
            // but for recursion, we're distributing the *surplus* (remainingFunds).
            // However, simply using remainingFunds * weight / totalWeight works for the surplus 
            // essentially adding to the previous allocation.
            
            val totalWeight = candidates.sumOf { it.effectivePriority }
            var distributedInThisPass = 0.0
            
            for (state in candidates) {
                // Share of the pie available in THIS iteration
                val share = if (totalWeight > 0.0) {
                    remainingFunds * (state.effectivePriority / totalWeight)
                } else {
                    remainingFunds / candidates.size
                }
                
                val currentAlloc = tempAllocations[state.vault.id] ?: 0.0
                val totalNeed = min(state.desiredMonthly, state.remainingNeed)
                val needed = totalNeed - currentAlloc
                
                // Take what is needed, up to the share, capped by what's actually available 
                // (though share summing handles availability generally, rounding might drift)
                val toAllocate = min(share, needed)
                
                if (toAllocate > 0.0) {
                    tempAllocations[state.vault.id] = currentAlloc + toAllocate
                    distributedInThisPass += toAllocate
                }
            }
            
            // 3. Update remaining funds
            // We subtract what was actually *added* to allocations in this pass
            remainingFunds -= distributedInThisPass
            
            if (distributedInThisPass < 0.01) break // Prevent infinite loop if stuck
        }
        
        // Commit to main maps and generate details
        tempAllocations.forEach { (id, amount) ->
            if (amount > 0.5) { // minimal filter matching original logic
                allocations[id] = amount
                val state = vaults.first { it.vault.id == id }
                details[id] = createAllocationDetail(state, amount, reasonSuffix)
            } else {
                // If we allocated dust, return it to remaining? 
                // The main function rounds at the end. 
                // Original logic stored it if > 0.5. 
                // Let's stick to adding it to map, but maybe detail generation filters?
                // Actually, if we don't put it in 'allocations', we should add back to 'remaining' return?
                // The original logic: "remaining -= allocation" only if > 0.5.
                // So if we summarized 0.4, we effectively keep it in remaining for next tier?
                // The loop above decremented remainingFunds for ALL precise double amounts.
                // So if we decided NOT to persist it, we need to add it back.
                remainingFunds += amount
            }
        }
        
        return remainingFunds
    }

    private fun createAllocationDetail(
        state: VaultState,
        amount: Double,
        reasonSuffix: String
    ): AllocationDetail {
        val reason = determineAllocationReason(state, reasonSuffix)
        return AllocationDetail(
            vaultId = state.vault.id,
            vaultName = state.vault.name,
            amount = amount,
            urgencyScore = state.urgency,
            desiredAmount = state.desiredMonthly,
            priorityWeight = state.priorityWeight,
            reason = reason
        )
    }

    private data class VaultState(
        val vault: SmartVault,
        val urgency: Double,
        val desiredMonthly: Double,
        val priorityWeight: Double,
        val pendingAmount: Double,
        val remainingNeed: Double
    ) {
        val effectivePriority: Double = priorityWeight * urgency
        val monthsToDeadline: Int = vault.targetDate?.let {
            vault.monthsUntil(it, LocalDate.now()).coerceAtLeast(0)
        } ?: Int.MAX_VALUE
        val isFlowGoal: Boolean get() = vault.monthlyNeed != null
    }

    private data class AdaptiveBufferResult(
        val adjustedBufferPercent: Double,
        val allocationMultiplier: Double,
        val spendingTrend: SpendingTrend
    )

    private enum class SpendingTrend {
        UNDER_BUDGET, ON_TARGET, OVER_BUDGET
    }

    /**
     * Dynamically adjust buffer based on actual spending patterns.
     */
    private fun calculateAdaptiveBuffer(
        baseBufferPercent: Double,
        recentExpenses: List<Double>,
        monthlyIncome: Double,
        mainAccountBalance: Double,
        minBuffer: Double,
        maxAllocation: Double
    ): AdaptiveBufferResult {
        if (recentExpenses.isEmpty() || monthlyIncome <= 0.0) {
            return AdaptiveBufferResult(
                adjustedBufferPercent = baseBufferPercent,
                allocationMultiplier = 1.0,
                spendingTrend = SpendingTrend.ON_TARGET
            )
        }

        val avgExpenses = recentExpenses.average()
        val baseBuffer = monthlyIncome * baseBufferPercent
        val spendingRatio = avgExpenses / baseBuffer

        val (trend, bufferAdjustment, allocationMultiplier) = when {
            spendingRatio < 0.80 -> {
                Triple(SpendingTrend.UNDER_BUDGET, -0.05, 1.10)
            }
            spendingRatio > 1.20 -> {
                Triple(SpendingTrend.OVER_BUDGET, 0.05, 0.90)
            }
            else -> {
                Triple(SpendingTrend.ON_TARGET, 0.0, 1.0)
            }
        }

        val balanceRatio = mainAccountBalance / baseBuffer
        val finalBufferAdjustment = when {
            balanceRatio < 0.5 -> bufferAdjustment + 0.05
            balanceRatio > 2.0 -> bufferAdjustment - 0.03
            else -> bufferAdjustment
        }

        val adjustedPercent = (baseBufferPercent + finalBufferAdjustment)
            .coerceIn(minBuffer, 1.0 - maxAllocation)

        return AdaptiveBufferResult(
            adjustedBufferPercent = adjustedPercent,
            allocationMultiplier = allocationMultiplier,
            spendingTrend = trend
        )
    }

    /**
     * IMPROVED: Calculate urgency score with exponential scaling for imminent flow goals
     * and proper income pressure consideration.
     */
    fun computeUrgency(
        vault: SmartVault,
        today: LocalDate,
        monthlyIncome: Double,
        desiredMonthly: Double
    ): Double {
        // Flow goal
        vault.startDate?.let { startDate ->
            val monthsUntilStart = vault.monthsUntil(startDate, today)
            val monthlyNeed = vault.monthlyNeed ?: 0.0

            // Income pressure: how much of income will this consume?
            val incomePressure = if (monthlyIncome > 0.0) {
                (monthlyNeed / monthlyIncome).coerceIn(0.0, 10.0)
            } else {
                5.0
            }

            // CRITICAL CHANGE: Exponential urgency scaling for imminent flows
            val baseUrgency = when {
                monthsUntilStart <= 0 -> 20.0  // Already active - maximum priority
                monthsUntilStart == 1 -> 18.0  // Next month - critical
                monthsUntilStart == 2 -> 12.0  // 2 months - very high
                monthsUntilStart == 3 -> 8.0   // 3 months - high
                monthsUntilStart <= 6 -> 5.0   // 4-6 months - moderate
                monthsUntilStart <= 12 -> 3.0  // 7-12 months - building
                else -> 1.5                     // >12 months - low
            }

            // Amplify by income pressure (e.g., $600 need / $2000 income = 0.3 → +30% urgency)
            // But for imminent flows, multiply by pressure to create dramatic difference
            val urgencyMultiplier = if (monthsUntilStart <= 2) {
                1.0 + (incomePressure * 1.5)  // More aggressive for imminent flows
            } else {
                1.0 + (incomePressure * 0.5)  // Less aggressive for distant flows
            }

            return (baseUrgency * urgencyMultiplier).coerceAtMost(30.0)
        }

        // Fixed goal
        vault.targetDate?.let { targetDate ->
            val monthsRemaining = vault.monthsUntil(targetDate, today).coerceAtLeast(0)
            val incomePressure = if (monthlyIncome > 0.0 && desiredMonthly > 0.0) {
                (desiredMonthly / monthlyIncome).coerceIn(0.0, 5.0)
            } else {
                1.0
            }

            // Fixed Goal: strict prioritization by deadline
            // Formula: 120 / (months + 1) ensures strict decay:
            // 0mo -> 120
            // 1mo -> 60
            // 5mo -> 20
            // 6mo -> 17.1
            // Income pressure is heavily dampened to act only as a tie-breaker.
            
            val baseUrgency = 120.0 / (monthsRemaining + 1.0)
            
            // Dampened pressure: +2% max boost instead of +5%
            val pressureFactor = 1.0 + (incomePressure * 0.02) 
            
            val finalUrgency = baseUrgency * pressureFactor

            return finalUrgency
        }

        return 0.5
    }

    /**
     * IMPROVED: Calculate desired monthly contribution with better pre-funding logic
     */
    fun computeDesiredMonthly(
        vault: SmartVault,
        today: LocalDate,
        rampWindowMonths: Int,
        pendingAmount: Double,
        monthlyIncome: Double
    ): Double {
        // Flow goal
        vault.monthlyNeed?.let { monthlyNeed ->
            val startDate = vault.startDate
            val monthsUntilStart = if (startDate != null) {
                vault.monthsUntil(startDate, today)
            } else 0

            // CRITICAL CHANGE: Request full monthly need earlier
            val desiredBase = when {
                monthsUntilStart <= 0 -> monthlyNeed  // Active - need full amount
                monthsUntilStart == 1 -> monthlyNeed  // Next month - need full amount NOW
                monthsUntilStart == 2 -> monthlyNeed * 0.9  // 2 months - 90%
                monthsUntilStart == 3 -> monthlyNeed * 0.75 // 3 months - 75%
                monthsUntilStart <= 6 -> monthlyNeed * 0.5  // 4-6 months - 50%
                else -> monthlyNeed * 0.3  // >6 months - 30%
            }

            return (desiredBase - pendingAmount).coerceAtLeast(0.0)
        }

        // Fixed goal: spread remaining amount over remaining months
        val effectiveBalance = vault.currentBalance + pendingAmount
        val remaining = (vault.targetAmount - effectiveBalance).coerceAtLeast(0.0)
        if (vault.targetDate != null) {
            val months = vault.monthsUntil(vault.targetDate, today).coerceAtLeast(1)
            return remaining / months
        }
        return 0.0
    }

    private fun determineAllocationReason(state: VaultState, reasonSuffix: String): String {
        val base = when {
            state.vault.monthlyNeed != null && state.vault.startDate != null -> {
                val months = state.vault.monthsUntil(state.vault.startDate!!, LocalDate.now())
                when {
                    months <= 0 -> "Active flow: ${state.vault.monthlyNeed.formatCurrency()}/month needed"
                    months == 1 -> "URGENT: Flow starts next month (${state.vault.monthlyNeed.formatCurrency()}/month)"
                    months <= 3 -> "Flow starts in $months months (${state.vault.monthlyNeed.formatCurrency()}/month)"
                    else -> "Pre-funding flow goal (starts in $months months)"
                }
            }
            state.vault.monthlyNeed != null -> "Recurring flow goal"
            state.vault.targetDate != null -> {
                val months = state.monthsToDeadline
                "Fixed goal: $months month${if (months != 1) "s" else ""} remaining"
            }
            else -> reasonSuffix
        }

        return if (state.pendingAmount > 0.0) {
            "$base (awaiting ${state.pendingAmount.formatCurrency()} transfer)"
        } else {
            base
        }
    }

    /**
     * Compute vault deduction for expenses with overflow to main account.
     */
    fun computeVaultDeduction(expenseAmount: Double, vaultBalance: Double): Pair<Double, Double> {
        val deduction = min(expenseAmount, vaultBalance).roundToTwoDecimals()
        val overflow = max(0.0, expenseAmount - vaultBalance).roundToTwoDecimals()
        return Pair(deduction, overflow)
    }
}
