package com.example.sparely.domain.logic

import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.model.VaultAllocationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SavingTaxEngineTest {

    private val mockVault = SmartVault(
        id = 1L,
        name = "Test Vault",
        targetAmount = 1000.0,
        currentBalance = 100.0,
        allocationMode = VaultAllocationMode.MANUAL,
        manualAllocationPercent = 0.0,
        createdAt = LocalDate.now()
    )

    private fun createSettings(
        taxRate: Double = 0.10,
        minBalance: Double = 0.0
    ): SparelySettings {
        return SparelySettings(
            savingTaxRate = taxRate,
            minMainAccountBalance = minBalance
        )
    }

    @Test
    fun `calculate returns standard tax when balance is high enough`() {
        val settings = createSettings(taxRate = 0.10, minBalance = 100.0)
        val context = SavingTaxEngine.Context(
            expenseAmount = 50.0,
            expenseDate = LocalDate.now(),
            settings = settings,
            vaults = listOf(mockVault.copy(id = 1, name = "V1")),
            currentMainAccountBalance = 200.0
        )

        val plans = SavingTaxEngine.calculate(context)

        // Expense 50, Tax 10% = 5.0
        // Balance 200 -> 200 - 5 = 195 > 100. OK.

        val totalTax = plans.sumOf { it.amount }
        assertEquals(5.0, totalTax, 0.01)
    }

    @Test
    fun `calculate skips tax when balance would fall below min`() {
        val settings = createSettings(taxRate = 0.10, minBalance = 100.0)
        // Balance 102. Expense 50 (already deducted? context says "currentMainAccountBalance").
        // We assume 'currentMainAccountBalance' is the balance *after* the expense has been deducted (if applicable).

        // So Balance 102.
        // Tax = 5.0.
        // Projected = 102 - 5 = 97 < 100.
        // Affordable = 102 - 100 = 2.0.
        // Tax capped at 2.0? Or skipped depending on logic?
        // Logic: "if (baseAmount > affordableTax) baseAmount = floor(affordableTax)"
        // If affordableTax is 2.0, baseAmount becomes 2.0.

        val context = SavingTaxEngine.Context(
            expenseAmount = 50.0,
            expenseDate = LocalDate.now(),
            settings = settings,
            vaults = listOf(mockVault.copy(id = 1)),
            currentMainAccountBalance = 102.0
        )

        val plans = SavingTaxEngine.calculate(context)
        val totalTax = plans.sumOf { it.amount }

        assertEquals(2.0, totalTax, 0.01)
    }

    @Test
    fun `calculate returns zero when balance is already below min`() {
        val settings = createSettings(taxRate = 0.10, minBalance = 100.0)
        val context = SavingTaxEngine.Context(
            expenseAmount = 50.0,
            expenseDate = LocalDate.now(),
            settings = settings,
            vaults = listOf(mockVault.copy(id = 1)),
            currentMainAccountBalance = 90.0
        )

        val plans = SavingTaxEngine.calculate(context)
        assertTrue(plans.isEmpty())
    }
}
