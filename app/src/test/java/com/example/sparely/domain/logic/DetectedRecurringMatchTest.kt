package com.example.sparely.domain.logic

import com.example.sparely.domain.model.DetectedRecurringTransaction
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.RecurringFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DetectedRecurringMatchTest {

    private fun detected(name: String) =
        DetectedRecurringTransaction(name, 28.0, 30, LocalDate.of(2026, 10, 20), ExpenseCategory.ENTERTAINMENT, listOf(1L, 2L, 3L))

    private fun saved(id: Long, name: String) = RecurringExpense(
        id = id,
        description = name,
        amount = 28.0,
        category = ExpenseCategory.ENTERTAINMENT,
        frequency = RecurringFrequency.MONTHLY,
        startDate = LocalDate.of(2026, 1, 1)
    )

    @Test
    fun aPatternBelongsToTheSavedPaymentWithItsName() {
        assertEquals(4L, DetectedRecurringMatch.owner(detected("Claude pro subscription"), listOf(saved(4, "Claude Pro")))?.id)
        assertEquals(4L, DetectedRecurringMatch.owner(detected("claude pro"), listOf(saved(4, "Claude Pro subscription")))?.id)
        assertNull(DetectedRecurringMatch.owner(detected("Spotify"), listOf(saved(4, "Claude Pro"))))
    }

    @Test
    fun namesMatchOnWholeWordsOnly() {
        assertTrue(DetectedRecurringMatch.namesMatch("Netflix", "netflix!"))
        assertFalse(DetectedRecurringMatch.namesMatch("Prov", "Provigo groceries x"))
        // Too short to mean anything alone
        assertFalse(DetectedRecurringMatch.namesMatch("tv", "tv plus"))
    }

    @Test
    fun unsavedKeepsOnlyWhatNoSavedPaymentOwns() {
        val patterns = listOf(detected("Claude pro subscription"), detected("Spotify"))
        assertEquals(listOf("Spotify"), DetectedRecurringMatch.unsaved(patterns, listOf(saved(4, "Claude Pro"))).map { it.description })
    }
}
