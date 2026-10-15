package com.example.sparely.data.repository

import com.example.sparely.data.local.AssistantActionEntity
import com.example.sparely.data.local.PendingVariableRecurringExpenseEntity
import com.example.sparely.data.local.RecurringExpensePaidEntity
import com.example.sparely.data.local.SavingsAccountTransactionEntity
import com.example.sparely.data.local.SavingsAccountTransactionType
import com.example.sparely.data.utils.InstantAdapter
import com.example.sparely.data.utils.LocalDateAdapter
import com.example.sparely.data.utils.LocalDateTimeAdapter
import com.example.sparely.data.utils.YearMonthAdapter
import com.example.sparely.domain.model.BackupData
import com.example.sparely.domain.model.BackupPreference
import com.example.sparely.domain.model.SparelySettings
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** The data added for full backups (account history, recurring state, every setting) survives the JSON trip. */
class BackupFullFidelityTest {
    // Same settings the app's BackupRepository uses.
    private val gson = GsonBuilder()
        .registerTypeHierarchyAdapter(LocalDate::class.java, LocalDateAdapter())
        .registerTypeHierarchyAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        .registerTypeHierarchyAdapter(Instant::class.java, InstantAdapter())
        .registerTypeHierarchyAdapter(YearMonth::class.java, YearMonthAdapter())
        .serializeNulls()
        .create()

    private val at = LocalDateTime.of(2026, 10, 15, 9, 30)

    private fun roundTrip(backup: BackupData): BackupData =
        gson.fromJson(gson.toJson(backup), BackupData::class.java)

    @Test
    fun accountHistoryAndRecurringStateRoundTrip() {
        val accountTx = SavingsAccountTransactionEntity(
            id = 12, accountId = 3, type = SavingsAccountTransactionType.INTEREST,
            amount = 4.25, balanceAfter = 1504.25, timestamp = at, description = "Interest",
            relatedMainAccountTransactionId = 40
        )
        val paid = RecurringExpensePaidEntity(
            id = 5, recurringExpenseId = 2, expenseId = null, dueDate = LocalDate.of(2026, 11, 1),
            paidDate = LocalDate.of(2026, 10, 15), amountPaid = 89.99, notes = "Paid early", createdAt = at
        )
        val pending = PendingVariableRecurringExpenseEntity(id = 8, recurringExpenseId = 2, predictedAmount = 61.5, createdAt = at)
        val action = AssistantActionEntity(
            id = 3, actionType = "EXPENSE_RECORDED", recordId = 99, description = "Coffee",
            amount = 4.5, createdAtEpochMillis = 1_760_000_000_000L
        )

        val restored = roundTrip(
            BackupData(
                settings = SparelySettings(),
                savingsAccountTransactions = listOf(accountTx),
                recurringExpensePayments = listOf(paid),
                pendingVariableRecurringExpenses = listOf(pending),
                assistantActions = listOf(action)
            )
        )

        assertEquals(accountTx, restored.savingsAccountTransactions!!.single())
        assertEquals(paid, restored.recurringExpensePayments!!.single())
        assertEquals(pending, restored.pendingVariableRecurringExpenses!!.single())
        assertEquals(action, restored.assistantActions!!.single())
    }

    @Test
    fun settingsKeepTheirTypesAndValues() {
        val prefs = listOf(
            BackupPreference("biometric_enabled", BackupPreference.BOOLEAN, "true"),
            BackupPreference("reminder_hour", BackupPreference.INT, "20"),
            // Larger than a Double can hold exactly: must not come back rounded.
            BackupPreference("main_overflow_account_id", BackupPreference.LONG, "9007199254740993"),
            BackupPreference("target_savings_rate", BackupPreference.DOUBLE, "0.175"),
            BackupPreference("regional_currency", BackupPreference.STRING, "CAD"),
            BackupPreference("tags", BackupPreference.STRING_SET, values = listOf("a", "b"))
        )

        val restored = roundTrip(BackupData(settings = SparelySettings(), preferences = prefs)).preferences!!

        assertEquals(prefs, restored)
    }

    @Test
    fun olderBackupsHaveNoFullSettings() {
        val json = gson.toJson(BackupData(settings = SparelySettings())).replace(",\"preferences\":null", "")

        assertNull(gson.fromJson(json, BackupData::class.java).preferences)
    }
}
