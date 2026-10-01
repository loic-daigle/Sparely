package com.example.sparely.appfunctions

import com.example.sparely.domain.model.RegionalSettings
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.SparelySettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AssistantTransferPlannerTest {

    private var now = 1_000_000_000L
    private var nextId = 0

    private inner class FakeGateway : AssistantTransferGateway {
        var settings = SparelySettings(
            aiAssistantAccessEnabled = true,
            aiAssistantWriteEnabled = true,
            regionalSettings = RegionalSettings(currencyCode = "CAD")
        )
        val vaults = mutableMapOf(
            1L to SmartVault(id = 1, name = "Trip", targetAmount = 2000.0, currentBalance = 300.0),
            2L to SmartVault(
                id = 2, name = "Car", targetAmount = 500.0, currentBalance = 500.0,
                defaultManualDepositDeductFromMain = false, defaultManualWithdrawalCreditMain = false
            ),
            3L to SmartVault(id = 3, name = "Old", targetAmount = 100.0, currentBalance = 100.0, archived = true)
        )
        val expenses = mutableMapOf(
            10L to RefundableExpense(10, "Shoes", 120.0, 20.0, paidByCreditCard = false),
            11L to RefundableExpense(11, "Laptop", 900.0, 0.0, paidByCreditCard = true),
            12L to RefundableExpense(12, "Gift", 50.0, 50.0, paidByCreditCard = false)
        )

        override suspend fun settings() = settings
        override suspend fun vault(id: Long) = vaults[id]
        override suspend fun refundableExpense(id: Long) = expenses[id]
    }

    private val gateway = FakeGateway()
    private val planner = AssistantTransferPlanner(gateway, nowEpochMillis = { now }, newRequestId = { "req-${++nextId}" })

    private inline fun <reified T : Throwable> assertThrows(noinline block: suspend () -> Unit): T {
        try {
            runBlocking { block() }
        } catch (e: Throwable) {
            if (e is T) return e
            throw e
        }
        fail("Expected ${T::class.simpleName}")
        throw IllegalStateException()
    }

    @Test
    fun needsBothAssistantSettings() {
        gateway.settings = gateway.settings.copy(aiAssistantWriteEnabled = false)
        assertThrows<AssistantAccessDisabledException> { planner.planVaultDeposit(1, 50.0, null) }
        gateway.settings = gateway.settings.copy(aiAssistantAccessEnabled = false, aiAssistantWriteEnabled = true)
        assertThrows<AssistantAccessDisabledException> { planner.planRefund(10, null, null) }
    }

    @Test
    fun vaultDeposit_followsVaultDefaultForMainAccount() = runBlocking {
        val fromMain = planner.planVaultDeposit(1, 50.0, "  Bonus  ")
        assertEquals(AssistantMoneyRequest.Kind.VAULT_DEPOSIT, fromMain.request.kind)
        assertEquals("req-1", fromMain.request.requestId)
        assertEquals(now, fromMain.request.createdAtEpochMillis)
        assertEquals("Bonus", fromMain.request.reason)
        assertTrue(fromMain.affectsMainAccount)
        assertTrue(fromMain.summary.contains("50.00 CAD from the main account into the vault \"Trip\""))

        val notFromMain = planner.planVaultDeposit(2, 50.0, " ")
        assertFalse(notFromMain.affectsMainAccount)
        assertNull(notFromMain.request.reason)
    }

    @Test
    fun vaultWithdrawal_cannotExceedBalance() {
        val error = assertThrows<AssistantInvalidArgumentException> { planner.planVaultWithdrawal(1, 300.01, null) }
        assertTrue(error.message!!.contains("300.00"))

        val plan = runBlocking { planner.planVaultWithdrawal(1, 300.0, null) }
        assertTrue(plan.affectsMainAccount)
        assertFalse(runBlocking { planner.planVaultWithdrawal(2, 10.0, null) }.affectsMainAccount)
    }

    @Test
    fun vaults_mustExistAndBeActive() {
        assertThrows<AssistantInvalidArgumentException> { planner.planVaultDeposit(99, 10.0, null) }
        assertThrows<AssistantInvalidArgumentException> { planner.planVaultDeposit(3, 10.0, null) }
    }

    @Test
    fun amounts_mustBePositiveAndCapped() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 100_000.01).forEach { amount ->
            assertThrows<AssistantInvalidArgumentException> { planner.planVaultDeposit(1, amount, null) }
        }
    }

    @Test
    fun refund_defaultsToRemainingAndRespectsLimits() = runBlocking {
        val full = planner.planRefund(10, null, null)
        assertEquals(100.0, full.request.amount, 0.0)
        assertTrue(full.affectsMainAccount)

        val creditCard = planner.planRefund(11, 100.0, null)
        assertFalse(creditCard.affectsMainAccount)

        assertThrows<AssistantInvalidArgumentException> { planner.planRefund(10, 100.01, null) }
        val alreadyRefunded = assertThrows<AssistantInvalidArgumentException> { planner.planRefund(12, null, null) }
        assertTrue(alreadyRefunded.message!!.contains("already fully refunded"))
        assertThrows<AssistantInvalidArgumentException> { planner.planRefund(99, null, null) }
        Unit
    }

    @Test
    fun describe_rechecksAgainstCurrentDataAndExpires() = runBlocking {
        val plan = planner.planVaultWithdrawal(1, 200.0, null)

        // Still fine a bit later.
        now += 60_000
        assertEquals(plan.summary, planner.describe(plan.request).summary)

        // The vault was emptied in the meantime: the old request no longer fits.
        gateway.vaults[1L] = gateway.vaults.getValue(1L).copy(currentBalance = 100.0)
        assertThrows<AssistantInvalidArgumentException> { planner.describe(plan.request) }

        // Too old, whatever the data says.
        gateway.vaults[1L] = gateway.vaults.getValue(1L).copy(currentBalance = 300.0)
        now = plan.request.createdAtEpochMillis + AssistantTransferPlanner.REQUEST_LIFETIME_MILLIS + 1
        val expired = assertThrows<AssistantInvalidArgumentException> { planner.describe(plan.request) }
        assertTrue(expired.message!!.contains("expired"))
    }
}
