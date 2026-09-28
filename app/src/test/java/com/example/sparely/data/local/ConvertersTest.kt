package com.example.sparely.data.local

import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RecurringFrequency
import com.example.sparely.domain.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun unknownEnumNamesFallBackInsteadOfCrashing() {
        assertEquals(ExpenseCategory.OTHER, converters.fromExpenseCategory("SOMETHING_NEW"))
        assertEquals(RiskLevel.BALANCED, converters.fromRisk(""))
        assertEquals(RecurringFrequency.MONTHLY, converters.fromRecurringFrequency("fortnightly"))
        assertEquals(ExpenseCategory.GROCERIES, converters.fromExpenseCategory("groceries"))
        assertNull(converters.fromExpenseCategory(null))
    }

    @Test
    fun corruptJsonColumnsDecodeToNullInsteadOfThrowing() {
        assertNull(converters.fromAmountHistoryJson("{not json"))
        assertNull(converters.fromAssetAllocationJson("[1,2"))
        assertNull(converters.fromAmountHistoryJson(""))
    }

    @Test
    fun amountHistoryDropsIncompleteEntries() {
        // Must not throw; entries without a date are dropped. (On a desktop JDK Gson may refuse
        // reflective access to LocalDate entirely, in which case the column decodes to null.)
        val decoded = converters.fromAmountHistoryJson("""[{"amount": 12.5}, null]""")
        assertTrue(decoded == null || decoded.isEmpty())
    }

    @Test
    fun assetAllocationRoundTrips() {
        val json = converters.toAssetAllocationJson(mapOf(1L to 0.5, 2L to 0.5))
        assertEquals(mapOf(1L to 0.5, 2L to 0.5), converters.fromAssetAllocationJson(json))
    }
}
