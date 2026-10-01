package com.example.sparely.appfunctions

import com.example.sparely.domain.model.AllocationBreakdown
import com.example.sparely.domain.model.CategoryBudget
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.RecurringFrequency
import com.example.sparely.domain.model.RegionalSettings
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsPercentages
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.model.Wishlist
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SparelyAssistantQueriesTest {

    private val today = LocalDate.of(2026, 3, 15)

    private class FakeDataSource(
        var settings: SparelySettings = SparelySettings(
            aiAssistantAccessEnabled = true,
            regionalSettings = RegionalSettings(currencyCode = "CAD")
        ),
        var expenses: List<Expense> = emptyList(),
        var budgets: List<CategoryBudget> = emptyList(),
        var vaults: List<SmartVault> = emptyList(),
        var mainBalance: Double = 0.0,
        var recurring: List<RecurringExpense> = emptyList(),
        var wishlists: List<Wishlist> = emptyList()
    ) : AssistantDataSource {
        override suspend fun settings() = settings
        override suspend fun expenses() = expenses
        override suspend fun budgetsForMonth(month: YearMonth) = budgets.filter { it.yearMonth == month }
        override suspend fun activeVaults() = vaults
        override suspend fun mainAccountBalance() = mainBalance
        override suspend fun recurringExpenses() = recurring
        override suspend fun activeWishlists() = wishlists
    }

    private val data = FakeDataSource()
    private val queries = SparelyAssistantQueries(data) { today }

    private var nextId = 1L

    private fun expense(
        date: LocalDate,
        amount: Double,
        category: ExpenseCategory = ExpenseCategory.GROCERIES,
        description: String = "Purchase",
        refunded: Double = 0.0,
        ignored: Boolean = false,
        notes: String? = null
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
        refundedAmount = refunded,
        isIgnored = ignored,
        notes = notes
    )

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw e
        }
        fail("Expected ${T::class.simpleName}")
        throw IllegalStateException()
    }

    // --- Access gate ---

    @Test
    fun everyQuery_refusedUntilUserOptsIn() = runBlocking {
        data.settings = data.settings.copy(aiAssistantAccessEnabled = false)
        val calls: List<suspend () -> Any> = listOf(
            { queries.getSpendingSummary(null, null) },
            { queries.searchExpenses(null, null, null, null, null) },
            { queries.getBudgetStatus(null) },
            { queries.listVaults() },
            { queries.getAccountBalances() },
            { queries.listUpcomingBills(null) },
            { queries.listWishlist() }
        )
        for (call in calls) {
            val error = assertThrows<AssistantAccessDisabledException> { runBlocking { call() } }
            assertTrue(error.message!!.contains("Allow AI assistants"))
        }
    }

    // --- Spending summary ---

    @Test
    fun spendingSummary_defaultsToMonthToDate_netOfRefunds_excludingIgnored() = runBlocking {
        data.expenses = listOf(
            expense(LocalDate.of(2026, 2, 28), 999.0), // last month
            expense(LocalDate.of(2026, 3, 1), 100.0, ExpenseCategory.GROCERIES),
            expense(LocalDate.of(2026, 3, 10), 50.0, ExpenseCategory.DINING, refunded = 20.0),
            expense(LocalDate.of(2026, 3, 12), 500.0, ExpenseCategory.TRAVEL, ignored = true),
            expense(LocalDate.of(2026, 3, 16), 70.0) // after today
        )

        val summary = queries.getSpendingSummary(null, null)

        assertEquals("2026-03-01", summary.startDate)
        assertEquals("2026-03-15", summary.endDate)
        assertEquals("CAD", summary.currencyCode)
        assertEquals(130.0, summary.totalSpent, 0.001)
        assertEquals(2, summary.expenseCount)
        assertEquals(listOf("GROCERIES", "DINING"), summary.categories.map { it.category })
        assertEquals(30.0, summary.categories[1].totalSpent, 0.001)
    }

    @Test
    fun spendingSummary_rejectsBadDates() = runBlocking {
        val badFormat = assertThrows<AssistantInvalidArgumentException> {
            runBlocking { queries.getSpendingSummary("03/01/2026", null) }
        }
        assertTrue(badFormat.message!!.contains("startDate"))
        assertThrows<AssistantInvalidArgumentException> {
            runBlocking { queries.getSpendingSummary("2026-03-10", "2026-03-01") }
        }
        Unit
    }

    // --- Expense search ---

    @Test
    fun searchExpenses_filtersByTextCategoryAndDate_newestFirst() = runBlocking {
        data.expenses = listOf(
            expense(LocalDate.of(2026, 1, 5), 15.99, ExpenseCategory.ENTERTAINMENT, "Netflix"),
            expense(LocalDate.of(2026, 2, 5), 15.99, ExpenseCategory.ENTERTAINMENT, "Netflix"),
            expense(LocalDate.of(2026, 2, 6), 40.0, ExpenseCategory.GROCERIES, "Costco", notes = "netflix gift card"),
            expense(LocalDate.of(2026, 3, 5), 15.99, ExpenseCategory.ENTERTAINMENT, "Netflix", ignored = true)
        )

        val all = queries.searchExpenses("netflix", null, null, null, null)
        assertEquals(listOf("2026-02-06", "2026-02-05", "2026-01-05"), all.expenses.map { it.date })

        val filtered = queries.searchExpenses("netflix", "entertainment", "2026-02-01", null, null)
        assertEquals(1, filtered.expenses.size)
        assertEquals("2026-02-05", filtered.expenses.single().date)
    }

    @Test
    fun searchExpenses_appliesLimitAndReportsMore() = runBlocking {
        data.expenses = (1..5).map { expense(LocalDate.of(2026, 3, it), 10.0) }

        val result = queries.searchExpenses(null, null, null, null, 2)

        assertEquals(2, result.expenses.size)
        assertTrue(result.hasMore)
        assertFalse(queries.searchExpenses(null, null, null, null, 5).hasMore)
    }

    @Test
    fun searchExpenses_rejectsUnknownCategoryAndBadLimit() = runBlocking {
        val category = assertThrows<AssistantInvalidArgumentException> {
            runBlocking { queries.searchExpenses(null, "FOOD", null, null, null) }
        }
        assertTrue(category.message!!.contains("GROCERIES"))
        assertThrows<AssistantInvalidArgumentException> {
            runBlocking { queries.searchExpenses(null, null, null, null, 0) }
        }
        assertThrows<AssistantInvalidArgumentException> {
            runBlocking { queries.searchExpenses(null, null, null, null, 51) }
        }
        Unit
    }

    // --- Budgets ---

    @Test
    fun budgetStatus_reportsSpentAndStatusForRequestedMonth() = runBlocking {
        val march = YearMonth.of(2026, 3)
        data.budgets = listOf(
            CategoryBudget(id = 1, category = ExpenseCategory.DINING, monthlyLimit = 100.0, yearMonth = march),
            CategoryBudget(id = 2, category = ExpenseCategory.GROCERIES, monthlyLimit = 400.0, yearMonth = march)
        )
        data.expenses = listOf(
            expense(LocalDate.of(2026, 3, 2), 120.0, ExpenseCategory.DINING),
            expense(LocalDate.of(2026, 3, 3), 100.0, ExpenseCategory.GROCERIES),
            expense(LocalDate.of(2026, 2, 3), 300.0, ExpenseCategory.GROCERIES)
        )

        val overview = queries.getBudgetStatus(null)

        assertEquals("2026-03", overview.month)
        assertEquals(500.0, overview.totalBudget, 0.001)
        assertEquals(220.0, overview.totalSpent, 0.001)
        assertEquals("OVER_BUDGET", overview.overallStatus)
        val dining = overview.categories.single { it.category == "DINING" }
        assertEquals("OVER_BUDGET", dining.status)
        assertEquals(0.0, dining.remaining, 0.001)
        val groceries = overview.categories.single { it.category == "GROCERIES" }
        assertEquals(300.0, groceries.remaining, 0.001)
        assertEquals(0.25, groceries.fractionUsed, 0.001)

        assertTrue(queries.getBudgetStatus("2026-04").categories.isEmpty())
    }

    @Test
    fun budgetStatus_rejectsBadMonth() {
        val error = assertThrows<AssistantInvalidArgumentException> {
            runBlocking { queries.getBudgetStatus("March") }
        }
        assertTrue(error.message!!.contains("YYYY-MM"))
    }

    // --- Vaults and balances ---

    @Test
    fun vaultsAndBalances_skipArchivedVaults() = runBlocking {
        data.vaults = listOf(
            SmartVault(id = 1, name = "Trip", targetAmount = 2000.0, currentBalance = 500.0, targetDate = LocalDate.of(2026, 7, 1)),
            SmartVault(id = 2, name = "Old", targetAmount = 100.0, currentBalance = 100.0, archived = true)
        )
        data.mainBalance = -42.5

        val vaults = queries.listVaults()
        assertEquals(listOf("Trip"), vaults.vaults.map { it.name })
        assertEquals(0.25, vaults.vaults.single().progress, 0.001)
        assertEquals("2026-07-01", vaults.vaults.single().targetDate)

        val balances = queries.getAccountBalances()
        assertEquals(-42.5, balances.mainAccountBalance, 0.001)
        assertEquals(500.0, balances.totalInVaults, 0.001)
        assertEquals(1, balances.vaultCount)
    }

    // --- Upcoming bills ---

    @Test
    fun upcomingBills_listsActiveBillsInWindow() = runBlocking {
        data.recurring = listOf(
            RecurringExpense(
                id = 1, description = "Rent", amount = 1200.0, category = ExpenseCategory.UTILITIES,
                frequency = RecurringFrequency.MONTHLY, startDate = LocalDate.of(2026, 1, 20)
            ),
            RecurringExpense(
                id = 2, description = "Insurance", amount = 600.0, category = ExpenseCategory.OTHER,
                frequency = RecurringFrequency.YEARLY, startDate = LocalDate.of(2025, 9, 1)
            ),
            RecurringExpense(
                id = 3, description = "Cancelled gym", amount = 50.0, category = ExpenseCategory.HEALTH,
                frequency = RecurringFrequency.MONTHLY, startDate = LocalDate.of(2026, 1, 16), isActive = false
            )
        )

        val bills = queries.listUpcomingBills(null)

        assertEquals(listOf("Rent"), bills.bills.map { it.description })
        assertEquals("2026-03-20", bills.bills.single().dueDate)
        assertEquals(5, bills.bills.single().daysUntilDue)
        assertEquals(1200.0, bills.totalDue, 0.001)

        assertEquals(2, queries.listUpcomingBills(365).bills.size)
        assertThrows<AssistantInvalidArgumentException> { runBlocking { queries.listUpcomingBills(-1) } }
        Unit
    }

    // --- Wishlist ---

    @Test
    fun wishlist_sortedByPriority_withCooldown() = runBlocking {
        val now = java.time.LocalDateTime.now()
        data.wishlists = listOf(
            Wishlist(id = 1, description = "Headphones", targetAmount = 300.0, priority = 2, createdAt = now, cooldownExpiresAt = now.plusDays(2)),
            Wishlist(id = 2, description = "Bike", targetAmount = 800.0, currentSavings = 200.0, priority = 5, createdAt = now)
        )

        val items = queries.listWishlist().items

        assertEquals(listOf("Bike", "Headphones"), items.map { it.description })
        assertFalse(items[0].isOnCooldown)
        assertTrue(items[1].isOnCooldown)
        assertEquals(now.plusDays(2).toLocalDate().toString(), items[1].cooldownEndsDate)
    }
}
