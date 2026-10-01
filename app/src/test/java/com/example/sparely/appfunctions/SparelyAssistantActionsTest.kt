package com.example.sparely.appfunctions

import com.example.sparely.domain.model.AssistantAction
import com.example.sparely.domain.model.AssistantActionType
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.IncomeCategory
import com.example.sparely.domain.model.RegionalSettings
import com.example.sparely.domain.model.SparelySettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class SparelyAssistantActionsTest {

    private val today = LocalDate.of(2026, 3, 15)
    private var now = 1_000_000_000L

    private data class RecordedExpense(
        val description: String,
        val amount: Double,
        val category: ExpenseCategory,
        val date: LocalDate,
        val notes: String?
    )

    private inner class FakeGateway : AssistantWriteGateway {
        var settings = SparelySettings(
            aiAssistantAccessEnabled = true,
            aiAssistantWriteEnabled = true,
            regionalSettings = RegionalSettings(currencyCode = "CAD")
        )
        val expenses = mutableListOf<RecordedExpense>()
        val incomes = mutableListOf<Triple<Double, String, IncomeCategory?>>()
        val log = mutableListOf<AssistantAction>()

        override suspend fun settings() = settings

        override suspend fun recordExpense(
            description: String,
            amount: Double,
            category: ExpenseCategory,
            date: LocalDate,
            notes: String?
        ): Long {
            expenses += RecordedExpense(description, amount, category, date, notes)
            return 100L + expenses.size
        }

        override suspend fun recordIncome(amount: Double, description: String, category: IncomeCategory?): Long {
            incomes += Triple(amount, description, category)
            return 500L + incomes.size
        }

        override suspend fun actionsSince(epochMillis: Long) = log.filter { it.createdAtEpochMillis >= epochMillis }

        override suspend fun logAndNotify(
            type: AssistantActionType,
            recordId: Long,
            description: String,
            amount: Double
        ): AssistantAction {
            val action = AssistantAction(log.size + 1L, type, recordId, description, amount, now)
            log += action
            return action
        }
    }

    private val gateway = FakeGateway()
    private val actions = SparelyAssistantActions(gateway, today = { today }, nowEpochMillis = { now })

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

    // --- Permissions ---

    @Test
    fun writesNeedBothSettings() {
        gateway.settings = gateway.settings.copy(aiAssistantWriteEnabled = false)
        val writeOff = assertThrows<AssistantAccessDisabledException> {
            actions.recordExpense("Lunch", 12.0, null, null, null)
        }
        assertTrue(writeOff.message!!.contains("Let assistants add entries"))

        gateway.settings = gateway.settings.copy(aiAssistantAccessEnabled = false, aiAssistantWriteEnabled = true)
        val accessOff = assertThrows<AssistantAccessDisabledException> {
            actions.recordIncome(50.0, "Gift", null)
        }
        assertTrue(accessOff.message!!.contains("Allow AI assistants"))

        assertTrue(gateway.expenses.isEmpty())
        assertTrue(gateway.incomes.isEmpty())
        assertTrue(gateway.log.isEmpty())
    }

    // --- Expenses ---

    @Test
    fun recordExpense_recordsLogsAndConfirms() = runBlocking {
        val entry = actions.recordExpense("  Groceries at Costco ", 42.5, "groceries", "2026-03-14", " weekly shop ")

        assertEquals(RecordedExpense("Groceries at Costco", 42.5, ExpenseCategory.GROCERIES, LocalDate.of(2026, 3, 14), "weekly shop"), gateway.expenses.single())
        assertEquals(101L, entry.id)
        assertEquals("CAD", entry.currencyCode)
        assertTrue(entry.message.contains("Groceries at Costco"))
        assertTrue(entry.message.contains("undo"))
        val logged = gateway.log.single()
        assertEquals(AssistantActionType.EXPENSE_RECORDED, logged.type)
        assertEquals(101L, logged.recordId)
        assertEquals(42.5, logged.amount, 0.0)
    }

    @Test
    fun recordExpense_defaultsToTodayAndOther() = runBlocking {
        actions.recordExpense("Parking", 8.0, null, null, null)

        val recorded = gateway.expenses.single()
        assertEquals(today, recorded.date)
        assertEquals(ExpenseCategory.OTHER, recorded.category)
        assertNull(recorded.notes)
    }

    @Test
    fun recordExpense_rejectsInvalidInputWithoutWriting() {
        val cases: List<suspend () -> Unit> = listOf(
            { actions.recordExpense(" ", 10.0, null, null, null) },
            { actions.recordExpense("x".repeat(101), 10.0, null, null, null) },
            { actions.recordExpense("Lunch", 0.0, null, null, null) },
            { actions.recordExpense("Lunch", -5.0, null, null, null) },
            { actions.recordExpense("Lunch", Double.NaN, null, null, null) },
            { actions.recordExpense("Lunch", Double.POSITIVE_INFINITY, null, null, null) },
            { actions.recordExpense("Lunch", 100_000.01, null, null, null) },
            { actions.recordExpense("Lunch", 10.0, "FOOD", null, null) },
            { actions.recordExpense("Lunch", 10.0, null, "15/03/2026", null) },
            { actions.recordExpense("Lunch", 10.0, null, "2026-03-16", null) },
            { actions.recordExpense("Lunch", 10.0, null, "2025-03-14", null) },
            { actions.recordExpense("Lunch", 10.0, null, null, "n".repeat(501)) }
        )
        cases.forEach { assertThrows<AssistantInvalidArgumentException>(it) }
        assertTrue(gateway.expenses.isEmpty())
        assertTrue(gateway.log.isEmpty())
    }

    @Test
    fun recordExpense_refusesRepeatWithinWindow_allowsAfterOrIfUndone() = runBlocking {
        actions.recordExpense("Coffee", 4.5, null, null, null)

        assertThrows<AssistantDuplicateException> { actions.recordExpense("coffee", 4.5, "DINING", null, null) }
        assertEquals(1, gateway.expenses.size)

        // A different amount is a different purchase.
        actions.recordExpense("Coffee", 5.0, null, null, null)
        assertEquals(2, gateway.expenses.size)

        // Once the first one is undone, recording it again is fine.
        gateway.log[0] = gateway.log[0].copy(undoneAtEpochMillis = now)
        actions.recordExpense("Coffee", 4.5, null, null, null)
        assertEquals(3, gateway.expenses.size)

        // Outside the window it is treated as a new purchase.
        now += SparelyAssistantActions.DUPLICATE_WINDOW_MILLIS + 1
        actions.recordExpense("Coffee", 5.0, null, null, null)
        assertEquals(4, gateway.expenses.size)
    }

    // --- Income ---

    @Test
    fun recordIncome_recordsWithCategory() = runBlocking {
        val entry = actions.recordIncome(1500.0, "Paycheck", "salary")

        assertEquals(Triple(1500.0, "Paycheck", IncomeCategory.SALARY), gateway.incomes.single())
        assertEquals(501L, entry.id)
        assertEquals(AssistantActionType.INCOME_RECORDED, gateway.log.single().type)
    }

    @Test
    fun recordIncome_validatesAndDeduplicates() {
        assertThrows<AssistantInvalidArgumentException> { actions.recordIncome(50.0, "Gift", "LOTTERY") }
        assertThrows<AssistantInvalidArgumentException> { actions.recordIncome(-1.0, "Gift", null) }

        runBlocking { actions.recordIncome(50.0, "Gift", null) }
        assertThrows<AssistantDuplicateException> { actions.recordIncome(50.0, "gift", "GIFT") }
        assertEquals(1, gateway.incomes.size)

        // An expense with the same text and amount is not a duplicate income.
        runBlocking { actions.recordExpense("Gift", 50.0, null, null, null) }
        assertEquals(1, gateway.expenses.size)
    }
}
