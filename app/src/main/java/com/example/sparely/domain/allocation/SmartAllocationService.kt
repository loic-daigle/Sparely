package com.example.sparely.domain.allocation

import com.example.sparely.data.local.AllocationHistoryEntity
import com.example.sparely.data.local.AllocationHistoryDao
import com.example.sparely.data.local.SmartVaultDao
import com.example.sparely.data.local.toDomain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate

/**
 * Service wrapper to run the allocation engine and persist allocation suggestions.
 */
class SmartAllocationService(
    private val smartVaultDao: SmartVaultDao,
    private val allocationHistoryDao: AllocationHistoryDao
) {

    /**
     * Run allocation for the given month and persist allocation suggestions.
     * Returns the raw engine result.
     */
    suspend fun runMonthlyAllocation(
        monthlyIncome: Double,
        mainAccountBalance: Double,
        safeBufferPercent: Double = 0.45,
        today: LocalDate = LocalDate.now(),
        mainOverflowAccountId: Long? = null
    ): SmartAllocationEngine.AllocationResult = withContext(Dispatchers.IO) {
        // Get current active vaults snapshot and pending transfers that haven't been reconciled yet
        val vaults = smartVaultDao.observeActiveVaults().first().map { it.toDomain() }
        val pendingByVault = smartVaultDao.getPendingContributions()
            .filter { it.vaultId != null }
            .groupBy { it.vaultId!! }
            .mapValues { (_, contributions) -> contributions.sumOf { it.amount } }

        val input = SmartAllocationEngine.AllocationInput(
            vaults = vaults,
            monthlyIncome = monthlyIncome,
            mainAccountBalance = mainAccountBalance,
            safeBufferPercent = safeBufferPercent,
            today = today,
            pendingContributions = pendingByVault,
            mainOverflowAccountId = mainOverflowAccountId
        )

        val result = SmartAllocationEngine.allocate(input)

        // Persist allocation suggestions
        val date = today
        for ((vaultId, amount) in result.allocations) {
            val entity = AllocationHistoryEntity(
                vaultId = vaultId,
                amount = amount,
                date = date,
                source = "SMART_ALLOCATION",
                note = "Monthly suggested allocation"
            )
            allocationHistoryDao.insert(entity)
        }

        result
    }
}
