package com.example.sparely.domain.logic

import com.example.sparely.domain.model.AllocationBreakdown
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsPercentages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TitleSuggestionEngineTest {

    private var nextId = 1L

    private fun expense(
        description: String,
        date: LocalDate = LocalDate.of(2026, 1, 1),
        amount: Double = 10.0,
        category: ExpenseCategory = ExpenseCategory.OTHER,
        storeId: Long? = null,
        paymentMethodId: Long? = null
    ) = Expense(
        id = nextId++,
        description = description,
        amount = amount,
        category = category,
        date = date,
        includesTax = false,
        allocation = AllocationBreakdown(0.0, 0.0, 0.0, 0.0, 0.0),
        appliedPercentages = SavingsPercentages(0.0, 0.0, 0.0),
        autoRecommended = false,
        riskLevelUsed = RiskLevel.BALANCED,
        storeId = storeId,
        paymentMethodId = paymentMethodId
    )

    private fun index(vararg expenses: Expense, stores: Set<Long> = emptySet(), methods: Set<Long> = emptySet()) =
        TitleSuggestionEngine.buildIndex(expenses.toList(), stores, methods)

    @Test
    fun `titles match regardless of case, accents and spacing`() {
        val known = index(expense("Épicerie  Metro", category = ExpenseCategory.GROCERIES))
        val match = TitleSuggestionEngine.exactMatch(" epicerie metro", known)
        assertEquals(ExpenseCategory.GROCERIES, match?.category)
        assertEquals(1, TitleSuggestionEngine.suggest("EPIC", known).size)
    }

    @Test
    fun `most recent use decides the category after a recategorisation`() {
        val known = index(
            expense("Netflix", LocalDate.of(2026, 1, 5), category = ExpenseCategory.OTHER),
            expense("Netflix", LocalDate.of(2026, 3, 5), category = ExpenseCategory.ENTERTAINMENT, amount = 18.99),
            expense("netflix", LocalDate.of(2026, 2, 5), category = ExpenseCategory.OTHER)
        )
        assertEquals(1, known.size)
        assertEquals(ExpenseCategory.ENTERTAINMENT, known.single().category)
        assertEquals(18.99, known.single().lastAmount, 0.0)
        assertEquals(3, known.single().useCount)
    }

    @Test
    fun `prefix matches rank above word and substring matches`() {
        val known = index(
            expense("Bus pass"),
            expense("Metro bus"),
            expense("Minibus"),
            expense("Minibus"),
            expense("Minibus")
        )
        val titles = TitleSuggestionEngine.suggest("bus", known).map { it.title }
        assertEquals(listOf("Bus pass", "Metro bus", "Minibus"), titles)
    }

    @Test
    fun `frequent titles rank first within the same match type`() {
        val known = index(expense("Coffee shop"), expense("Coffee beans"), expense("Coffee beans"))
        assertEquals("Coffee beans", TitleSuggestionEngine.suggest("cof", known).first().title)
    }

    @Test
    fun `deleted stores and payment methods are not suggested`() {
        val known = index(
            expense("Gas", storeId = 7, paymentMethodId = 3),
            expense("Lunch", storeId = 8, paymentMethodId = 4),
            stores = setOf(8),
            methods = setOf(4)
        )
        val gas = known.first { it.title == "Gas" }
        val lunch = known.first { it.title == "Lunch" }
        assertNull(gas.storeId)
        assertNull(gas.paymentMethodId)
        assertEquals(8L, lunch.storeId)
        assertEquals(4L, lunch.paymentMethodId)
    }

    @Test
    fun `queries shorter than the minimum suggest nothing`() {
        val known = index(expense("Rent"))
        assertTrue(TitleSuggestionEngine.suggest("r", known).isEmpty())
        assertNull(TitleSuggestionEngine.exactMatch("r", known))
    }

    @Test
    fun `blank descriptions are not remembered`() {
        assertTrue(index(expense("   ")).isEmpty())
    }

    @Test
    fun `exact match requires the whole title`() {
        val known = index(expense("Netflix"))
        assertNull(TitleSuggestionEngine.exactMatch("Netfl", known))
    }
}
