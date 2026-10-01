package com.example.sparely.appfunctions

import com.example.sparely.domain.model.AssistantAction
import com.example.sparely.domain.model.AssistantActionType
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.IncomeCategory
import com.example.sparely.domain.model.SparelySettings
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.abs

/** What assistant writes need from the app. Kept narrow so it can be faked in tests. */
interface AssistantWriteGateway {
    suspend fun settings(): SparelySettings

    /** Records the expense with all its money movements; returns the new expense id. */
    suspend fun recordExpense(
        description: String,
        amount: Double,
        category: ExpenseCategory,
        date: LocalDate,
        notes: String?
    ): Long

    /** Deposits income into the main account; returns the new transaction id. */
    suspend fun recordIncome(amount: Double, description: String, category: IncomeCategory?): Long

    suspend fun actionsSince(epochMillis: Long): List<AssistantAction>

    /** Adds the change to the audit log and tells the user, with a way to undo it. */
    suspend fun logAndNotify(type: AssistantActionType, recordId: Long, description: String, amount: Double): AssistantAction
}

/**
 * Additive writes AI assistants may make: recording an expense or income. Nothing here edits or
 * deletes existing data. Every write needs both "Allow AI assistants" and "Let assistants add
 * entries", is validated, is refused when it repeats a write made moments ago (assistants retry),
 * and is logged so the user can undo it.
 */
class SparelyAssistantActions(
    private val gateway: AssistantWriteGateway,
    private val today: () -> LocalDate = LocalDate::now,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {

    suspend fun recordExpense(
        description: String,
        amount: Double,
        category: String?,
        date: String?,
        notes: String?
    ): RecordedEntry {
        val settings = requireWriteAccess()
        val cleanDescription = requireDescription(description)
        requireAmount(amount)
        val expenseCategory = parseExpenseCategory(category)
        val expenseDate = parseExpenseDate(date)
        val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanNotes != null && cleanNotes.length > MAX_NOTES_LENGTH) {
            throw AssistantInvalidArgumentException("notes must be at most $MAX_NOTES_LENGTH characters")
        }
        rejectDuplicate(AssistantActionType.EXPENSE_RECORDED, cleanDescription, amount)

        val expenseId = gateway.recordExpense(cleanDescription, amount, expenseCategory, expenseDate, cleanNotes)
        gateway.logAndNotify(AssistantActionType.EXPENSE_RECORDED, expenseId, cleanDescription, amount)
        return RecordedEntry(
            id = expenseId,
            currencyCode = settings.regionalSettings.currencyCode,
            message = "Recorded expense \"$cleanDescription\" of $amount ${settings.regionalSettings.currencyCode} " +
                "in ${expenseCategory.name} on $expenseDate. $UNDO_HINT"
        )
    }

    suspend fun recordIncome(amount: Double, description: String, category: String?): RecordedEntry {
        val settings = requireWriteAccess()
        val cleanDescription = requireDescription(description)
        requireAmount(amount)
        val incomeCategory = category?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            IncomeCategory.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw AssistantInvalidArgumentException(
                    "Unknown income category \"$value\". Use one of: ${IncomeCategory.entries.joinToString { it.name }}"
                )
        }
        rejectDuplicate(AssistantActionType.INCOME_RECORDED, cleanDescription, amount)

        val transactionId = gateway.recordIncome(amount, cleanDescription, incomeCategory)
        gateway.logAndNotify(AssistantActionType.INCOME_RECORDED, transactionId, cleanDescription, amount)
        return RecordedEntry(
            id = transactionId,
            currencyCode = settings.regionalSettings.currencyCode,
            message = "Added income \"$cleanDescription\" of $amount ${settings.regionalSettings.currencyCode} " +
                "to the main account. $UNDO_HINT"
        )
    }

    private suspend fun requireWriteAccess(): SparelySettings {
        val settings = gateway.settings()
        if (!settings.aiAssistantAccessEnabled) {
            throw AssistantAccessDisabledException(SparelyAssistantQueries.ACCESS_DISABLED_MESSAGE)
        }
        if (!settings.aiAssistantWriteEnabled) {
            throw AssistantAccessDisabledException(WRITE_DISABLED_MESSAGE)
        }
        return settings
    }

    private fun requireDescription(description: String): String {
        val trimmed = description.trim()
        if (trimmed.isEmpty()) throw AssistantInvalidArgumentException("description must not be empty")
        if (trimmed.length > MAX_DESCRIPTION_LENGTH) {
            throw AssistantInvalidArgumentException("description must be at most $MAX_DESCRIPTION_LENGTH characters")
        }
        return trimmed
    }

    private fun requireAmount(amount: Double) {
        if (!amount.isFinite() || amount <= 0.0) {
            throw AssistantInvalidArgumentException("amount must be a positive number")
        }
        if (amount > MAX_AMOUNT) {
            throw AssistantInvalidArgumentException(
                "amount must be at most $MAX_AMOUNT. Larger amounts must be entered in the Sparely app."
            )
        }
    }

    private fun parseExpenseCategory(value: String?): ExpenseCategory {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return ExpenseCategory.OTHER
        return ExpenseCategory.entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            ?: throw AssistantInvalidArgumentException(
                "Unknown category \"$trimmed\". Use one of: ${ExpenseCategory.entries.joinToString { it.name }}"
            )
    }

    private fun parseExpenseDate(value: String?): LocalDate {
        val now = today()
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return now
        val date = try {
            LocalDate.parse(trimmed)
        } catch (e: DateTimeParseException) {
            throw AssistantInvalidArgumentException("date must be in YYYY-MM-DD format, got \"$trimmed\"")
        }
        if (date.isAfter(now)) throw AssistantInvalidArgumentException("date must not be in the future")
        if (date.isBefore(now.minusDays(MAX_BACKDATE_DAYS))) {
            throw AssistantInvalidArgumentException("date must be within the last $MAX_BACKDATE_DAYS days")
        }
        return date
    }

    /** Assistants retry; an identical entry moments ago is almost certainly the same one. */
    private suspend fun rejectDuplicate(type: AssistantActionType, description: String, amount: Double) {
        val since = nowEpochMillis() - DUPLICATE_WINDOW_MILLIS
        val duplicate = gateway.actionsSince(since).any {
            it.type == type && !it.isUndone &&
                abs(it.amount - amount) < 0.005 &&
                it.description.equals(description, ignoreCase = true)
        }
        if (duplicate) {
            throw AssistantDuplicateException(
                "An identical entry (\"$description\", $amount) was recorded moments ago. " +
                    "Do not record it again unless the user confirms it is a separate one."
            )
        }
    }

    companion object {
        const val MAX_AMOUNT = 100_000.0
        const val MAX_DESCRIPTION_LENGTH = 100
        const val MAX_NOTES_LENGTH = 500
        const val MAX_BACKDATE_DAYS = 365L
        const val DUPLICATE_WINDOW_MILLIS = 5 * 60 * 1000L
        const val UNDO_HINT = "The user can undo this from the Sparely notification or in Settings > Security."
        const val WRITE_DISABLED_MESSAGE =
            "The user has not allowed AI assistants to add entries to Sparely. Ask them to turn on " +
                "\"Let assistants add entries\" in Sparely under Settings > Security, or to enter it in the app."
    }
}

/** The same entry was just recorded; the message tells the assistant what to do. */
class AssistantDuplicateException(message: String) : Exception(message)
