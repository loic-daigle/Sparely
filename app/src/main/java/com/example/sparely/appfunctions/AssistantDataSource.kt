package com.example.sparely.appfunctions

import com.example.sparely.data.preferences.UserPreferencesRepository
import com.example.sparely.data.repository.SavingsRepository
import com.example.sparely.domain.model.CategoryBudget
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.model.Wishlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.YearMonth

/** The data AI assistants may read. Kept narrow so it can be faked in tests. */
interface AssistantDataSource {
    suspend fun settings(): SparelySettings
    suspend fun expenses(): List<Expense>
    suspend fun budgetsForMonth(month: YearMonth): List<CategoryBudget>
    suspend fun activeVaults(): List<SmartVault>
    suspend fun mainAccountBalance(): Double
    suspend fun recurringExpenses(): List<RecurringExpense>
    suspend fun activeWishlists(): List<Wishlist>
}

class RepositoryAssistantDataSource(
    private val savingsRepository: SavingsRepository,
    private val preferencesRepository: UserPreferencesRepository
) : AssistantDataSource {

    override suspend fun settings(): SparelySettings = preferencesRepository.getSettingsSnapshot()

    override suspend fun expenses(): List<Expense> =
        withContext(Dispatchers.IO) { savingsRepository.observeExpenses().first() }

    override suspend fun budgetsForMonth(month: YearMonth): List<CategoryBudget> =
        savingsRepository.getBudgetsForMonth(month.year, month.monthValue)

    override suspend fun activeVaults(): List<SmartVault> =
        withContext(Dispatchers.IO) { savingsRepository.observeSmartVaults().first() }

    override suspend fun mainAccountBalance(): Double = savingsRepository.getLatestMainAccountBalance()

    override suspend fun recurringExpenses(): List<RecurringExpense> =
        withContext(Dispatchers.IO) { savingsRepository.observeRecurringExpenses().first() }

    override suspend fun activeWishlists(): List<Wishlist> = savingsRepository.getActiveWishlists()
}
