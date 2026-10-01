package com.example.sparely.appfunctions

import com.example.sparely.domain.logic.BudgetEngine
import com.example.sparely.domain.logic.UpcomingRecurringCalculator
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.predictNextAmount
import com.example.sparely.ui.utils.roundToTwoDecimals
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * Read-only queries behind Sparely's AppFunctions. Kept free of Android framework types so the
 * logic can be unit tested on the JVM; [SparelyAppFunctionServiceBase] only forwards to it.
 *
 * Every query first checks the user has opted in to assistant access, and rejects bad input with
 * [AssistantInvalidArgumentException] using messages an assistant can act on. The service maps
 * these to the matching AppFunction exceptions.
 */
class SparelyAssistantQueries(
    private val dataSource: AssistantDataSource,
    private val today: () -> LocalDate = LocalDate::now
) {

    suspend fun getSpendingSummary(startDate: String?, endDate: String?): SpendingSummary {
        val currencyCode = requireAccess()
        val now = today()
        val start = parseDate("startDate", startDate) ?: now.withDayOfMonth(1)
        val end = parseDate("endDate", endDate) ?: now
        requireOrdered(start, end)

        val expenses = dataSource.expenses().filter { it.countsTowardSpending() && it.date in start..end }
        val categories = expenses
            .groupBy { it.category }
            .map { (category, list) ->
                CategorySpending(
                    category = category.name,
                    totalSpent = list.sumOf { it.netAmount() }.roundToTwoDecimals(),
                    expenseCount = list.size
                )
            }
            .filter { it.totalSpent > 0.0 }
            .sortedByDescending { it.totalSpent }

        return SpendingSummary(
            startDate = start.toString(),
            endDate = end.toString(),
            currencyCode = currencyCode,
            totalSpent = expenses.sumOf { it.netAmount() }.roundToTwoDecimals(),
            expenseCount = expenses.size,
            categories = categories
        )
    }

    suspend fun searchExpenses(
        query: String?,
        category: String?,
        startDate: String?,
        endDate: String?,
        limit: Int?
    ): ExpenseSearchResult {
        val currencyCode = requireAccess()
        val start = parseDate("startDate", startDate)
        val end = parseDate("endDate", endDate)
        if (start != null && end != null) requireOrdered(start, end)
        val expenseCategory = parseCategory(category)
        val maxResults = (limit ?: DEFAULT_SEARCH_LIMIT).also {
            if (it !in 1..MAX_SEARCH_LIMIT) {
                throw AssistantInvalidArgumentException("limit must be between 1 and $MAX_SEARCH_LIMIT")
            }
        }
        val text = query?.trim()?.takeIf { it.isNotEmpty() }

        val matches = dataSource.expenses()
            .asSequence()
            .filter { !it.isIgnored }
            .filter { expenseCategory == null || it.category == expenseCategory }
            .filter { start == null || !it.date.isBefore(start) }
            .filter { end == null || !it.date.isAfter(end) }
            .filter { text == null || it.matches(text) }
            .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })
            .toList()

        return ExpenseSearchResult(
            currencyCode = currencyCode,
            expenses = matches.take(maxResults).map { it.toRecord() },
            hasMore = matches.size > maxResults
        )
    }

    suspend fun getBudgetStatus(month: String?): BudgetOverview {
        val currencyCode = requireAccess()
        val yearMonth = month?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try {
                YearMonth.parse(it)
            } catch (e: DateTimeParseException) {
                throw AssistantInvalidArgumentException("month must be in YYYY-MM format, got \"$it\"")
            }
        } ?: YearMonth.from(today())

        // Same inputs as the in-app Budget screen, so the assistant reports the numbers the user sees.
        val summary = BudgetEngine.generateBudgetSummary(dataSource.budgetsForMonth(yearMonth), dataSource.expenses(), yearMonth)

        return BudgetOverview(
            month = yearMonth.toString(),
            currencyCode = currencyCode,
            totalBudget = summary.totalBudget,
            totalSpent = summary.totalSpent,
            overallStatus = summary.overallHealth.name,
            categories = summary.categoryStatuses.map { status ->
                BudgetCategoryStatus(
                    category = status.category.name,
                    limit = status.limit,
                    spent = status.spent,
                    remaining = status.remaining,
                    fractionUsed = status.percentageUsed.roundToTwoDecimals(),
                    status = status.status.name
                )
            }
        )
    }

    suspend fun listVaults(): VaultList {
        val currencyCode = requireAccess()
        val vaults = dataSource.activeVaults()
            .filterNot { it.archived }
            .map { vault ->
                VaultSummary(
                    id = vault.id,
                    name = vault.name,
                    currentBalance = vault.currentBalance.roundToTwoDecimals(),
                    targetAmount = vault.targetAmount.roundToTwoDecimals(),
                    progress = vault.progressPercent.roundToTwoDecimals(),
                    targetDate = vault.targetDate?.toString(),
                    monthlyNeed = vault.monthlyNeed?.roundToTwoDecimals()
                )
            }
        return VaultList(currencyCode = currencyCode, vaults = vaults)
    }

    suspend fun getAccountBalances(): AccountBalances {
        val currencyCode = requireAccess()
        val vaults = dataSource.activeVaults().filterNot { it.archived }
        return AccountBalances(
            currencyCode = currencyCode,
            mainAccountBalance = dataSource.mainAccountBalance().roundToTwoDecimals(),
            totalInVaults = vaults.sumOf { it.currentBalance }.roundToTwoDecimals(),
            vaultCount = vaults.size
        )
    }

    suspend fun listUpcomingBills(days: Int?): UpcomingBillList {
        val currencyCode = requireAccess()
        val windowDays = (days ?: DEFAULT_BILL_WINDOW_DAYS).also {
            if (it !in 0..MAX_BILL_WINDOW_DAYS) {
                throw AssistantInvalidArgumentException("days must be between 0 and $MAX_BILL_WINDOW_DAYS")
            }
        }
        val bills = UpcomingRecurringCalculator
            .compute(dataSource.recurringExpenses(), today(), windowDays)
            .map { upcoming ->
                val recurring = upcoming.recurringExpense
                val amount = if (recurring.isVariableAmount) recurring.predictNextAmount() else recurring.amount
                UpcomingBill(
                    id = recurring.id,
                    description = recurring.description,
                    amount = amount.roundToTwoDecimals(),
                    isVariableAmount = recurring.isVariableAmount,
                    category = recurring.category.name,
                    frequency = recurring.frequency.name,
                    dueDate = upcoming.dueDate.toString(),
                    daysUntilDue = upcoming.daysUntilDue
                )
            }
        return UpcomingBillList(
            currencyCode = currencyCode,
            totalDue = bills.sumOf { it.amount }.roundToTwoDecimals(),
            bills = bills
        )
    }

    suspend fun listWishlist(): WishlistOverview {
        val currencyCode = requireAccess()
        val items = dataSource.activeWishlists()
            .filterNot { it.archived }
            .sortedByDescending { it.priority }
            .map { item ->
                WishlistItem(
                    id = item.id,
                    description = item.description,
                    targetAmount = item.targetAmount.roundToTwoDecimals(),
                    currentSavings = item.currentSavings.roundToTwoDecimals(),
                    isOnCooldown = item.isOnCooldown,
                    cooldownEndsDate = item.cooldownExpiresAt?.toLocalDate()?.toString(),
                    category = item.category?.name
                )
            }
        return WishlistOverview(currencyCode = currencyCode, items = items)
    }

    /** Throws unless the user turned on assistant access; returns their currency code. */
    private suspend fun requireAccess(): String {
        val settings = dataSource.settings()
        if (!settings.aiAssistantAccessEnabled) {
            throw AssistantAccessDisabledException(ACCESS_DISABLED_MESSAGE)
        }
        return settings.regionalSettings.currencyCode
    }

    private fun parseDate(name: String, value: String?): LocalDate? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            LocalDate.parse(trimmed)
        } catch (e: DateTimeParseException) {
            throw AssistantInvalidArgumentException("$name must be in YYYY-MM-DD format, got \"$trimmed\"")
        }
    }

    private fun requireOrdered(start: LocalDate, end: LocalDate) {
        if (start.isAfter(end)) {
            throw AssistantInvalidArgumentException("startDate ($start) must not be after endDate ($end)")
        }
    }

    private fun parseCategory(value: String?): ExpenseCategory? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return ExpenseCategory.entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            ?: throw AssistantInvalidArgumentException(
                "Unknown category \"$trimmed\". Use one of: ${ExpenseCategory.entries.joinToString { it.name }}"
            )
    }

    private fun Expense.countsTowardSpending(): Boolean = !isIgnored

    private fun Expense.netAmount(): Double = (amount - refundedAmount).coerceAtLeast(0.0)

    private fun Expense.matches(text: String): Boolean =
        description.contains(text, ignoreCase = true) ||
            notes?.contains(text, ignoreCase = true) == true ||
            orderNumber?.contains(text, ignoreCase = true) == true ||
            items.any { it.name.contains(text, ignoreCase = true) }

    private fun Expense.toRecord() = ExpenseRecord(
        id = id,
        description = description,
        amount = amount.roundToTwoDecimals(),
        refundedAmount = refundedAmount.roundToTwoDecimals(),
        category = category.name,
        date = date.toString(),
        isRecurring = countsAsRecurring,
        notes = notes
    )

    companion object {
        const val DEFAULT_SEARCH_LIMIT = 20
        const val MAX_SEARCH_LIMIT = 50
        const val DEFAULT_BILL_WINDOW_DAYS = 30
        const val MAX_BILL_WINDOW_DAYS = 365
        const val ACCESS_DISABLED_MESSAGE =
            "The user has not allowed AI assistants to access Sparely. Ask them to turn on " +
                "\"Allow AI assistants\" in Sparely under Settings > Security."
    }
}

/** The user has not turned on assistant access. */
class AssistantAccessDisabledException(message: String) : Exception(message)

/** An argument from the assistant is invalid; the message says which one and why. */
class AssistantInvalidArgumentException(message: String) : Exception(message)
