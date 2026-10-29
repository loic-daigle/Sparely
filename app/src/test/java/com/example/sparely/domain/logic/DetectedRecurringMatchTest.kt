package com.example.sparely.domain.logic

import com.example.sparely.domain.model.DetectedRecurringTransaction
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.RecurringFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DetectedRecurringMatchTest {

    private fun detected(name: String, amount: Double = 28.0, cadence: Int = 30, storeId: Long? = null, category: ExpenseCategory = ExpenseCategory.ENTERTAINMENT) =
        DetectedRecurringTransaction(name, amount, cadence, LocalDate.of(2026, 10, 20), category, storeId)

    private fun saved(name: String, amount: Double = 28.0, frequency: RecurringFrequency = RecurringFrequency.MONTHLY, storeId: Long? = null, category: ExpenseCategory = ExpenseCategory.ENTERTAINMENT) =
        RecurringExpense(description = name, amount = amount, category = category, frequency = frequency, startDate = LocalDate.of(2026, 1, 1), storeId = storeId)

    @Test
    fun aRenamedSubscriptionStillCoversItsOldExpenses() {
        assertTrue(DetectedRecurringMatch.isCovered(detected("Claude pro subscription"), saved("Claude Pro")))
        assertTrue(DetectedRecurringMatch.isCovered(detected("claude pro"), saved("Claude Pro subscription")))
    }

    @Test
    fun namesMatchOnWholeWordsOnly() {
        assertTrue(DetectedRecurringMatch.namesMatch("Netflix", "netflix!"))
        assertFalse(DetectedRecurringMatch.namesMatch("Prov", "Provigo groceries x"))
        // Too short to mean anything alone
        assertFalse(DetectedRecurringMatch.namesMatch("tv", "tv plus"))
    }

    @Test
    fun theSameStoreNeedsAboutTheSameAmount() {
        assertTrue(DetectedRecurringMatch.isCovered(detected("iCloud", amount = 3.99, storeId = 7), saved("Apple storage", amount = 3.99, storeId = 7, category = ExpenseCategory.UTILITIES)))
        assertFalse(DetectedRecurringMatch.isCovered(detected("Apple TV", amount = 12.99, storeId = 7), saved("iCloud", amount = 3.99, storeId = 7, category = ExpenseCategory.UTILITIES)))
    }

    @Test
    fun anotherNameWithTheSameAmountCategoryAndRhythmIsCovered() {
        assertTrue(DetectedRecurringMatch.isCovered(detected("ANTHROPIC*CLAUDE", amount = 28.5), saved("AI assistant", amount = 28.0)))
        // Same amount, but weekly against monthly
        assertFalse(DetectedRecurringMatch.isCovered(detected("Gym", cadence = 7), saved("AI assistant")))
    }

    @Test
    fun unsavedKeepsOnlyWhatNothingCovers() {
        val patterns = listOf(detected("Claude pro subscription"), detected("Spotify", amount = 11.99))
        assertEquals(listOf("Spotify"), DetectedRecurringMatch.unsaved(patterns, listOf(saved("Claude Pro"))).map { it.description })
    }
}
