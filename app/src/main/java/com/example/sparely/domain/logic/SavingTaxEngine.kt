package com.example.sparely.domain.logic

import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.SparelySettings
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Distributes the "saving tax" portion of each expense across smart vaults.
 */
object SavingTaxEngine {

    data class Context(
        val expenseAmount: Double,
        val expenseDate: LocalDate,
        val settings: SparelySettings,
        val vaults: List<SmartVault>,
        val minimumContribution: Double = 0.0,
        val currentMainAccountBalance: Double = Double.MAX_VALUE // Default for backward compatibility/testing
    )

    data class PlannedContribution(
        val vaultId: Long,
        val amount: Double
    )

    fun calculate(context: Context): List<PlannedContribution> {
        val baseRate = context.settings.savingTaxRate.coerceIn(0.0, 1.0)
        if (baseRate <= 0.0) return emptyList()
        if (context.expenseAmount <= 0.0) return emptyList()

        // Exclude archived vaults and vaults explicitly opted out of automatic allocations
        val eligibleVaults = context.vaults.filter { vault ->
            !vault.archived && vault.allowAutoIncome && (vault.targetAmount > 0.0) && (vault.targetAmount - vault.currentBalance) > 0.0
        }
        if (eligibleVaults.isEmpty()) return emptyList()

        // Calculate base amount and round UP to nearest dollar for final transfer amount
        val rawBaseAmount = context.expenseAmount * baseRate
        var baseAmount = ceil(rawBaseAmount)
        
        // Check minimum balance protection
        // We assume 'currentMainAccountBalance' is the balance *after* the expense has been deducted (if applicable).
        // So we only subtract the potential tax amount.
        val projectedBalance = context.currentMainAccountBalance - baseAmount
        val minBalance = context.settings.minMainAccountBalance
        
        if (minBalance > 0 && projectedBalance < minBalance) {
             // Calculate max affordable tax
             val affordableTax = (context.currentMainAccountBalance - minBalance).coerceAtLeast(0.0)
             if (affordableTax <= 0) return emptyList()
             
             // Cap the base amount
             if (baseAmount > affordableTax) {
                 baseAmount = kotlin.math.floor(affordableTax) // Round down to be safe
             }
        }

        if (baseAmount < context.minimumContribution) return emptyList()
        if (baseAmount <= 0.01) return emptyList()

        val weights = DynamicAllocationEngine.calculateWeights(
            vaults = eligibleVaults,
            settings = context.settings,
            today = context.expenseDate
        )
        if (weights.isEmpty()) return emptyList()

        val weightMap = weights.associateBy { it.vaultId }
        val adjustedWeights = eligibleVaults.map { vault ->
            val baseWeight = weightMap[vault.id]?.weight ?: 0.0
            val modifier = overrideMultiplier(baseRate, vault.savingTaxRateOverride)
            vault.id to baseWeight * modifier
        }

        val totalAdjusted = adjustedWeights.sumOf { it.second }
        if (totalAdjusted <= 0.0) return emptyList()

        val normalized = adjustedWeights.map { (vaultId, raw) ->
            vaultId to (raw / totalAdjusted)
        }

        val baseCents = (baseAmount * 100).roundToInt()
        if (baseCents <= 0) return emptyList()

        // Round UP to cents for each vault contribution
        val results = normalized.map { (vaultId, weight) ->
            val rawCents = weight * baseCents
            val ceilCents = ceil(rawCents).toInt()
            val amount = ceilCents / 100.0

            if (amount >= context.minimumContribution) {
                PlannedContribution(vaultId, amount)
            } else null
        }.filterNotNull()

        return results
    }

    private fun overrideMultiplier(baseRate: Double, override: Double?): Double {
        if (override == null) return 1.0
        val safeBase = baseRate.takeIf { it > 0.0 } ?: return 1.0
        val ratio = override / safeBase
        return when {
            ratio.isNaN() || ratio.isInfinite() -> 1.0
            ratio <= 0.0 -> 0.0
            else -> ratio
        }
    }

    private data class ContributionDraft(
        val vaultId: Long,
        var cents: Int,
        val fractional: Double
    )
}
