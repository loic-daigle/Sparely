package com.example.sparely.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.sparely.AppContainer
import com.example.sparely.data.local.ExpenseEntity
import com.example.sparely.data.local.SavingsTransferEntity
import com.example.sparely.data.local.toDomain
import com.example.sparely.data.local.toEntity
import com.example.sparely.data.repository.BackupRepository
import com.example.sparely.data.repository.SavingsRepository
import com.example.sparely.domain.logic.AlertsGenerator
import com.example.sparely.domain.logic.AnalyticsEngine
import com.example.sparely.domain.logic.BudgetEngine
import com.example.sparely.domain.logic.ChallengeEngine
import com.example.sparely.domain.logic.FinancialHealthEngine
import com.example.sparely.domain.logic.RecommendationEngine
import com.example.sparely.domain.logic.SavingsAdvisor
import com.example.sparely.domain.logic.SavingsCalculator
import com.example.sparely.domain.logic.SavingTaxEngine
import com.example.sparely.domain.logic.DynamicAllocationEngine
import com.example.sparely.domain.logic.IncomeAutomationEngine
import com.example.sparely.domain.logic.PayScheduleCalculator
import com.example.sparely.domain.logic.EmergencyFundCalculator
import com.example.sparely.domain.logic.CashflowEngine
import com.example.sparely.domain.logic.SpendingPatternEngine
import com.example.sparely.domain.logic.SmartInsightEngine
import com.example.sparely.domain.model.Achievement
import com.example.sparely.domain.model.Asset
import com.example.sparely.domain.model.ExpenseHistoryRetention
import com.example.sparely.domain.model.AlertMessage
import com.example.sparely.domain.model.AlertType
import com.example.sparely.domain.model.AllocationBreakdown
import com.example.sparely.domain.model.AnalyticsSnapshot
import com.example.sparely.domain.model.BudgetInput
import com.example.sparely.domain.model.BudgetOverrunPrompt
import com.example.sparely.domain.model.BudgetSummary
import com.example.sparely.domain.model.CategoryBudget
import com.example.sparely.domain.model.ChallengeInput
import com.example.sparely.domain.model.ChallengeType
import com.example.sparely.domain.model.EducationStatus
import com.example.sparely.domain.model.EmploymentStatus
import com.example.sparely.domain.model.LivingSituation
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.ExpenseInput
import com.example.sparely.domain.model.RecurringExpense
import com.example.sparely.domain.model.RecurringExpenseInput
import com.example.sparely.domain.model.RecurringFrequency
import com.example.sparely.domain.model.RiskLevel
import com.example.sparely.domain.model.SavingsCategory
import com.example.sparely.domain.model.SavingsChallenge
import com.example.sparely.domain.model.SavingsPercentages
import com.example.sparely.domain.model.SparelySettings
import com.example.sparely.domain.model.SmartAllocationMode
import com.example.sparely.domain.model.SmartSavingSummary
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.UpcomingRecurringExpense
import com.example.sparely.domain.model.UserProfileSetup
import com.example.sparely.domain.model.DetectedRecurringTransaction
import com.example.sparely.domain.model.RecommendationResult
import com.example.sparely.domain.model.VaultAllocationMode
import com.example.sparely.domain.model.VaultContribution
import com.example.sparely.domain.model.VaultContributionSource
import com.example.sparely.domain.model.toSmartVault
import com.example.sparely.domain.model.toSmartVaultSetup
import com.example.sparely.domain.model.IncomeTrackingMode
import com.example.sparely.domain.model.PayInterval
import com.example.sparely.domain.model.PayScheduleSettings
import com.example.sparely.notifications.NotificationScheduler
import com.example.sparely.workers.VaultAutoDepositScheduler
import com.example.sparely.data.preferences.UserPreferencesRepository
import com.example.sparely.domain.model.SmartVaultSetup
import com.example.sparely.domain.model.Store
import com.example.sparely.domain.model.StoreInput
import com.example.sparely.domain.model.VaultAdjustmentType
import com.example.sparely.domain.model.VaultArchivePrompt
import com.example.sparely.ui.state.SparelyUiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt
import com.example.sparely.domain.model.PaymentMethod
import com.example.sparely.domain.model.PaymentMethodType

class SparelyViewModel(
    private val savingsRepository: SavingsRepository,
    private val backupRepository: BackupRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val recommendationEngine: RecommendationEngine,
    private val notificationScheduler: NotificationScheduler,
    private val vaultAutoDepositScheduler: VaultAutoDepositScheduler,
    private val brandfetchRepository: com.example.sparely.data.repository.BrandfetchRepository? = null,
    private val container: AppContainer,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    // Expose container for UI that needs app-level dependencies (e.g., vaultRepository)
    val appContainer: AppContainer get() = container

    private val _uiState = MutableStateFlow(SparelyUiState())
    val uiState: StateFlow<SparelyUiState> = _uiState.asStateFlow()

    // Launches on [dispatcher] like viewModelScope.launch(dispatcher), but catches exceptions
    // thrown by [block] and surfaces them as errorMessage instead of crashing the app. Use this
    // for money-mutating operations (expenses, vault/main-account balance changes) where an
    // uncaught Room/DAO exception would otherwise take the whole app down mid-transaction.
    private fun safeLaunch(block: suspend () -> Unit) = viewModelScope.launch(dispatcher) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("SparelyViewModel", "Unhandled error in safeLaunch", e)
            _uiState.update { it.copy(errorMessage = e.message ?: "Something went wrong") }
        }
    }

    // Brandfetch Search
    private val _brandSearchResults = MutableStateFlow<List<com.example.sparely.data.remote.BrandfetchBrand>>(emptyList())
    val brandSearchResults: StateFlow<List<com.example.sparely.data.remote.BrandfetchBrand>> = _brandSearchResults.asStateFlow()

    fun searchBrands(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _brandSearchResults.value = emptyList()
                return@launch
            }
            val clientId = _uiState.value.settings.brandfetchClientId
            if (clientId.isNullOrBlank()) {
                return@launch
            }
            val results = brandfetchRepository?.searchBrands(query, clientId) ?: emptyList()
            _brandSearchResults.value = results
        }
    }

    fun clearBrandSearchResults() {
        _brandSearchResults.value = emptyList()
    }

    fun loadMoreExpenses() {
        if (_uiState.value.isLoadingMoreExpenses || !_uiState.value.canLoadMoreExpenses) return
        
        _uiState.update { it.copy(isLoadingMoreExpenses = true) }
        viewModelScope.launch(dispatcher) {
            val currentSize = _uiState.value.pagedExpenses.size
            val newExpenses = savingsRepository.getExpensesPaged(limit = 20, offset = currentSize)
            _uiState.update {
                it.copy(
                    pagedExpenses = it.pagedExpenses + newExpenses,
                    isLoadingMoreExpenses = false,
                    canLoadMoreExpenses = newExpenses.size == 20
                )
            }
        }
    }

    fun selectStore(store: Store) {
        _uiState.update { it.copy(selectedStore = store, isStoreHistoryLoading = true) }
        viewModelScope.launch(dispatcher) {
            val history = savingsRepository.getExpensesForStore(store.id)
            _uiState.update {
                it.copy(selectedStoreHistory = history, isStoreHistoryLoading = false)
            }
        }
    }

    fun clearSelectedStore() {
        _uiState.update { it.copy(selectedStore = null, selectedStoreHistory = emptyList()) }
    }

    // Brandfetch Search


    private val processedAchievementTitles = mutableSetOf<String>()
    private val handledBudgetPrompts = mutableSetOf<String>()
    private var lastBirthdayGreetingDate: LocalDate? = null

    private fun promptKey(category: ExpenseCategory, month: YearMonth): String = "${category.name}-${month}"
    private fun promptKey(prompt: BudgetOverrunPrompt): String = promptKey(prompt.category, prompt.month)

    private fun isMeaningfullyDifferent(current: Double?, candidate: Double, tolerance: Double = 0.0025): Boolean {
        if (current == null || current.isNaN()) return true
        return abs(current - candidate) > tolerance
    }

    private fun paychecksPerMonth(schedule: PayScheduleSettings): Double {
        return when (schedule.interval) {
            PayInterval.WEEKLY -> 52.0 / 12.0
            PayInterval.BIWEEKLY -> 26.0 / 12.0
            PayInterval.SEMI_MONTHLY -> 2.0
            PayInterval.MONTHLY -> 1.0
            PayInterval.CUSTOM -> schedule.customDaysBetween?.let { 30.0 / it.coerceAtLeast(1) } ?: 1.0
        }
    }

    private data class AggregatedFeeds(
        val expenses: List<Expense>,
        val transfers: List<SavingsTransferEntity>,
        val settings: SparelySettings,
        val vaults: List<SmartVault> = emptyList(),
        val savingsAccounts: List<com.example.sparely.domain.model.SavingsAccount> = emptyList(),
        val assets: List<Asset> = emptyList(),
        val budgets: List<CategoryBudget> = emptyList(),
        val recurring: List<RecurringExpense> = emptyList(),
        val challenges: List<SavingsChallenge> = emptyList(),
        val achievements: List<Achievement> = emptyList(),
        val stores: List<Store> = emptyList(),
        val paymentMethods: List<PaymentMethod> = emptyList(),
        val creditCardPayments: List<com.example.sparely.domain.model.CreditCardPayment> = emptyList(),
        val recurringPaidRecords: List<com.example.sparely.data.local.RecurringExpensePaidEntity> = emptyList()
    )

    init {
        // Initial load for paged expenses
        loadMoreExpenses()

        viewModelScope.launch(dispatcher) {
            preferencesRepository.refreshAgeFromBirthday()
            // ... (rest of init)
            val autoDepositsEnabled = preferencesRepository.getAutoDepositsEnabled()
            val checkHour = preferencesRepository.getAutoDepositCheckHour()
            vaultAutoDepositScheduler.schedule(autoDepositsEnabled, checkHour)
            // Initialize monthly smart allocation scheduling based on user setting
            val settingsSnapshot = preferencesRepository.getSettingsSnapshot()
            val autoAllocationsEnabled = settingsSnapshot.smartAllocationMode == SmartAllocationMode.AUTOMATIC
            container.monthlyAllocationScheduler.schedule(autoAllocationsEnabled)
        }
        viewModelScope.launch {
            combine(
                savingsRepository.observeExpenses(),
                savingsRepository.observeTransfers(),
                preferencesRepository.settingsFlow
            ) { expenses, transfers, settings ->
                AggregatedFeeds(
                    expenses = expenses,
                    transfers = transfers,
                    settings = settings
                )
            }
                .combine(savingsRepository.observeSmartVaults()) { feed: AggregatedFeeds, vaults: List<SmartVault> ->
                    feed.copy(vaults = vaults)
                }
                .combine(savingsRepository.observeBudgets()) { feed: AggregatedFeeds, budgets: List<CategoryBudget> ->
                    feed.copy(budgets = budgets)
                }
                .combine(savingsRepository.observeSavingsAccounts()) { feed: AggregatedFeeds, accounts: List<com.example.sparely.domain.model.SavingsAccount> ->
                    feed.copy(savingsAccounts = accounts)
                }
                .combine(savingsRepository.observeActiveAssets()) { feed: AggregatedFeeds, assets: List<Asset> ->
                    feed.copy(assets = assets)
                }
                .combine(savingsRepository.observeRecurringExpenses()) { feed: AggregatedFeeds, recurring: List<RecurringExpense> ->
                    feed.copy(recurring = recurring)
                }
                .combine(savingsRepository.observeChallenges()) { feed: AggregatedFeeds, challenges: List<SavingsChallenge> ->
                    feed.copy(challenges = challenges)
                }
                .combine(savingsRepository.observeAchievements()) { feed: AggregatedFeeds, achievements: List<Achievement> ->
                    feed.copy(achievements = achievements)
                }
                .combine(savingsRepository.observeStores()) { feed: AggregatedFeeds, stores: List<Store> ->
                    feed.copy(stores = stores)
                }
                .combine(savingsRepository.observePaymentMethods()) { feed: AggregatedFeeds, methods: List<PaymentMethod> ->
                    feed.copy(paymentMethods = methods)
                }
                .combine(savingsRepository.observeCreditCardPayments()) { feed: AggregatedFeeds, payments: List<com.example.sparely.domain.model.CreditCardPayment> ->
                    feed.copy(creditCardPayments = payments)
                }
                .combine(savingsRepository.observeRecurringExpensePaidRecords()) { feed: AggregatedFeeds, records: List<com.example.sparely.data.local.RecurringExpensePaidEntity> ->
                    feed.copy(recurringPaidRecords = records)
                }
                .combine(preferencesRepository.onboardingCompletedFlow) { feed, onboardingCompleted ->
                    feed to onboardingCompleted
                }
                .combine(preferencesRepository.autoDepositCheckHourFlow) { (feed, onboardingCompleted), autoDepositCheckHour ->
                    Triple(feed, onboardingCompleted, autoDepositCheckHour)
                }
                .combine(savingsRepository.observeMainAccountTransactions()) { (feed, onboardingCompleted, autoDepositCheckHour), mainAccountTransactions ->
                    Quadruple(feed, onboardingCompleted, autoDepositCheckHour, mainAccountTransactions)
                }
                .catch { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.message
                    )
                }
                .map { (feed, onboardingCompleted, autoDepositCheckHour, mainAccountTransactions) ->
                    val domainExpenses = feed.expenses
                    
                    // Automatically detect and ignore statistical anomalies (large one-off expenses)
                    val amounts = domainExpenses.map { it.amount }
                    val mean = if (amounts.isNotEmpty()) amounts.average() else 0.0
                    val stdDev = if (amounts.size > 1) {
                        val variance = amounts.map { (it - mean) * (it - mean) }.average()
                        kotlin.math.sqrt(variance)
                    } else 0.0
                    
                    val predictionExpenses = domainExpenses.filter { expense ->
                        val isAnomaly = stdDev > 0.0 && (expense.amount - mean) / stdDev > 2.5
                        !expense.isIgnored && !isAnomaly
                    }
                    
                    val domainTransfers = feed.transfers.map { it.toDomain() }
                    val analytics = AnalyticsEngine.build(domainExpenses, domainTransfers)

                    val currentMonth = YearMonth.now()
                    val activeBudgets = feed.budgets.filter { it.isActive && it.yearMonth == currentMonth }
                    val budgetSummary: BudgetSummary? = if (activeBudgets.isNotEmpty()) {
                        BudgetEngine.generateBudgetSummary(activeBudgets, domainExpenses, currentMonth)
                    } else {
                        null
                    }
                    val budgetSuggestions = BudgetEngine.suggestBudgetAdjustments(activeBudgets, predictionExpenses, feed.settings, context = container.context)
                    val budgetPrompts = budgetSummary?.let {
                        BudgetEngine.detectBudgetPrompts(it, domainExpenses, budgetSuggestions, feed.settings)
                            .filterNot { handledBudgetPrompts.contains(promptKey(it.category, it.month)) }
                    }.orEmpty()
                    val budgetAlerts = budgetSummary?.let { BudgetEngine.generateBudgetAlerts(it, container.context) }.orEmpty()

                    val monthlyExpenseEstimate = when {
                        analytics.averageMonthlyExpense > 0.0 -> analytics.averageMonthlyExpense
                        else -> {
                            val cutoff = LocalDate.now().minusDays(30)
                            domainExpenses.filter { !it.date.isBefore(cutoff) }.sumOf { it.amount }
                        }
                    }
                    val emergencyGoal = EmergencyFundCalculator.calculate(
                        settings = feed.settings,
                        monthlyExpenseEstimate = monthlyExpenseEstimate,
                        existingEmergency = analytics.totalEmergency
                    )

                    val recommendationBundle = recommendationEngine.generate(
                        expenses = domainExpenses,
                        transfers = domainTransfers,
                        settings = feed.settings,
                        autoTune = feed.settings.autoRecommendationsEnabled,
                        emergencyGoal = emergencyGoal
                    )
                    val recommendation = if (feed.settings.autoRecommendationsEnabled) recommendationBundle else null
                    val plan = recommendationBundle.savingsPlan

                    notificationScheduler.schedule(feed.settings)
                    notificationScheduler.schedulePaydayReminder(feed.settings)
                    notificationScheduler.scheduleCreditCardReminders(feed.settings)

                    val alerts = AlertsGenerator.buildAlerts(analytics, recommendation, feed.settings, emptyList())
                    var combinedAlerts = (alerts + budgetAlerts)
                        .distinctBy { it.title }
                        .sortedByDescending { it.priority }
                    feed.settings.takeIf { it.isBirthdayToday }?.let { settings ->
                        val today = LocalDate.now()
                        if (lastBirthdayGreetingDate != today) {
                            val message = AlertMessage(
                                title = "Happy birthday " + (settings.displayName ?: "!"),
                                description = "We boosted your celebrations by keeping spending tips fresh. Enjoy your day!",
                                type = AlertType.SUCCESS,
                                priority = 12,
                                actionable = false
                            )
                            combinedAlerts = (combinedAlerts + message)
                                .distinctBy { it.title }
                                .sortedByDescending { it.priority }
                            lastBirthdayGreetingDate = today
                        }
                    }

                    val upcomingRecurring = computeUpcomingRecurring(feed.recurring)
                    val enrichedChallenges = feed.challenges.map { challenge ->
                        val streak = ChallengeEngine.calculateStreak(challenge, domainExpenses)
                        if (challenge.streakDays != streak) {
                            challenge.copy(streakDays = streak)
                        } else {
                            challenge
                        }
                    }
                    val achievements = feed.achievements.sortedByDescending { it.earnedDate }

                    processedAchievementTitles.retainAll(achievements.map { it.title }.toSet())

                    val newAchievements = ChallengeEngine
                        .checkForNewAchievements(analytics, emptyList(), enrichedChallenges, achievements)
                        .filter { processedAchievementTitles.add(it.title) }
                    if (newAchievements.isNotEmpty()) {
                        viewModelScope.launch(dispatcher) {
                            savingsRepository.upsertAchievements(newAchievements)
                        }
                    }

                    val financialHealthScore = FinancialHealthEngine.calculateHealthScore(
                        expenses = domainExpenses,
                        transfers = domainTransfers,
                        goals = emptyList(),
                        budgetSummary = budgetSummary,
                        settings = feed.settings,
                        analytics = analytics
                    )


                    // Calculate Total Liquid Money
                    val totalHisaBalance = feed.savingsAccounts.filter { !it.archived }.sumOf { it.currentBalance }
                    val totalUsableMoney = feed.settings.mainAccountBalance + totalHisaBalance

                    // Compute cashflow forecast for Safe to Spend display
                    val cashflowForecast = CashflowEngine.forecast(
                        CashflowEngine.ForecastInput(
                            currentBalance = feed.settings.mainAccountBalance,
                            totalHisaBalance = totalHisaBalance,
                            minMainAccountBalance = feed.settings.minMainAccountBalance,
                            recentExpenses = predictionExpenses,
                            recurringExpenses = feed.recurring,
                            expectedMonthlyIncome = feed.settings.monthlyIncome,
                            nextPayDate = feed.settings.paySchedule.nextPayDate,
                            nextPayAmount = feed.settings.paySchedule.defaultNetPay.takeIf { it > 0 }
                        )
                    )

                    // Compute spending pattern analysis for anomaly detection and trends
                    val categoryBudgetMap = activeBudgets.associate { it.category to it.monthlyLimit }
                    val spendingPatterns = SpendingPatternEngine.analyze(
                        expenses = predictionExpenses,
                        mainAccountBalance = feed.settings.mainAccountBalance,
                        categoryBudgets = categoryBudgetMap
                    )

                    val totalVaultBalance = feed.vaults.sumOf { it.currentBalance }
                    val smartSavingSummary = buildSmartSavingSummary(feed.settings, analytics, recommendation)
                    val detectedRecurring = detectRecurringTransactions(domainExpenses).pruneStaleRecurring(feed.recurring)
                    
                    // Smart Insight Engine computations
                    val recurringPatterns = SmartInsightEngine.detectRecurringPatterns(
                        expenses = predictionExpenses
                    )
                    val seasonalInsights = SmartInsightEngine.getSeasonalInsights(
                        expenses = predictionExpenses
                    )
                    val idleMoneyInsight = SmartInsightEngine.analyzeIdleMoney(
                        currentBalance = feed.settings.mainAccountBalance,
                        expenses = predictionExpenses,
                        vaults = feed.vaults,
                        minMainAccountBalance = feed.settings.minMainAccountBalance,
                        monthlyIncome = feed.settings.monthlyIncome,
                        mainOverflowAccountId = feed.settings.mainOverflowAccountId
                    )
                    val uniqueExpenses = SmartInsightEngine.detectUniqueExpenses(
                        expenses = predictionExpenses
                    )

                    val automationActive = feed.settings.paySchedule.dynamicSaveRateEnabled || feed.settings.dynamicSavingTaxEnabled
                    var automationNotes: List<String> = emptyList()
                    var automatedSaveRate: Double? = feed.settings.paySchedule.lastComputedSaveRate
                    var automatedSavingTaxRate: Double? = feed.settings.lastComputedSavingTaxRate
                    if (automationActive) {
                        val referencePay = listOf(
                            feed.settings.paySchedule.lastPayAmount,
                            feed.settings.paySchedule.defaultNetPay,
                            feed.settings.monthlyIncome / (paychecksPerMonth(feed.settings.paySchedule).takeIf { it > 0.0 } ?: 1.0)
                        ).firstOrNull { it > 0.0 }
                        if (referencePay != null && referencePay > 0.0) {
                            val automationInput = IncomeAutomationEngine.Input(
                                currentPayAmount = referencePay,
                                schedule = feed.settings.paySchedule,
                                settings = feed.settings,
                                analytics = analytics,
                                recurringExpenses = feed.recurring
                            )
                            val automationResult = IncomeAutomationEngine.evaluate(automationInput)
                            automationNotes = automationResult.rationale
                            if (feed.settings.paySchedule.dynamicSaveRateEnabled) {
                                automatedSaveRate = automationResult.saveRate
                                if (isMeaningfullyDifferent(feed.settings.paySchedule.lastComputedSaveRate, automationResult.saveRate)) {
                                    val updatedSchedule = feed.settings.paySchedule.copy(lastComputedSaveRate = automationResult.saveRate)
                                    viewModelScope.launch(dispatcher) {
                                        preferencesRepository.updatePaySchedule(updatedSchedule)
                                    }
                                }
                            }
                            if (feed.settings.dynamicSavingTaxEnabled) {
                                automatedSavingTaxRate = automationResult.savingTaxRate
                                if (isMeaningfullyDifferent(feed.settings.lastComputedSavingTaxRate, automationResult.savingTaxRate)) {
                                    viewModelScope.launch(dispatcher) {
                                        preferencesRepository.updateSavingTaxRate(automationResult.savingTaxRate, fromAutomation = true)
                                    }
                                }
                            }
                        } else {
                            automationNotes = listOf("Awaiting paycheck history to compute automation.")
                        }
                    } else {
                        automationNotes = listOf("Automation toggles are off — adjust savings and tax rates manually.")
                    }

                    val activeSaveRate = when {
                        feed.settings.paySchedule.dynamicSaveRateEnabled -> (automatedSaveRate
                            ?: feed.settings.paySchedule.lastComputedSaveRate
                            ?: feed.settings.paySchedule.defaultSaveRate).coerceIn(0.0, 1.0)
                        else -> feed.settings.paySchedule.defaultSaveRate.coerceIn(0.0, 1.0)
                    }
                    val activeSavingTaxRate = when {
                        feed.settings.dynamicSavingTaxEnabled -> (automatedSavingTaxRate
                            ?: feed.settings.lastComputedSavingTaxRate
                            ?: feed.settings.savingTaxRate).coerceIn(0.0, 1.0)
                        else -> feed.settings.savingTaxRate.coerceIn(0.0, 1.0)
                    }

                    // Sort vaults by urgency using the engine logic
                    val sortedVaults = feed.vaults.sortedWith(
                        compareByDescending<SmartVault> { vault ->
                            if (vault.archived) -1.0 else {
                                val desired = com.example.sparely.domain.allocation.SmartAllocationEngine.computeDesiredMonthly(
                                    vault, LocalDate.now(), 3, 0.0, feed.settings.monthlyIncome
                                )
                                com.example.sparely.domain.allocation.SmartAllocationEngine.computeUrgency(
                                    vault, LocalDate.now(), feed.settings.monthlyIncome, desired
                                )
                            }
                        }.thenByDescending { it.priorityWeight }
                         .thenBy { if (it.targetAmount > 0) it.currentBalance / it.targetAmount else 0.0 }
                    )

                    // Preserve transient state from previous UI state
                    val previousState = _uiState.value
                    
                    // Reconstruct pagedExpenses with updated data
                    val expenseMap = domainExpenses.associateBy { it.id }
                    val preservedPagedExpenses = previousState.pagedExpenses.mapNotNull { expenseMap[it.id] }
                    
                    // Reconstruct store history with updated data
                    val preservedStoreHistory = previousState.selectedStoreHistory.mapNotNull { expenseMap[it.id] }

                    SparelyUiState(
                        settings = feed.settings,
                        expenses = domainExpenses,
                        analytics = analytics,
                        recommendation = recommendation,
                        manualTransfers = domainTransfers,
                        savingsPlan = plan,
                        smartSavingSummary = smartSavingSummary,
                        alerts = combinedAlerts,
                        smartVaults = sortedVaults,
                        savingsAccounts = feed.savingsAccounts,
                        assets = feed.assets,
                        totalVaultBalance = totalVaultBalance,
                        totalHisaBalance = totalHisaBalance,
                        totalUsableMoney = totalUsableMoney,
                        emergencyFundGoal = emergencyGoal,
                        onboardingCompleted = onboardingCompleted,
                        activeSaveRate = activeSaveRate,
                        activeSavingTaxRate = activeSavingTaxRate,
                        automationRationale = automationNotes,
                        budgets = activeBudgets,
                        budgetSummary = budgetSummary,
                        budgetSuggestions = budgetSuggestions,
                        budgetPrompts = budgetPrompts,
                        recurringExpenses = feed.recurring,
                        upcomingRecurring = upcomingRecurring,
                        recurringPaidRecords = feed.recurringPaidRecords,
                        activeChallenges = enrichedChallenges,
                        achievements = achievements,
                        financialHealthScore = financialHealthScore,
                        detectedRecurringTransactions = detectedRecurring,
                        
                        // preserve any transient UI prompts and state
                        vaultArchivePrompt = previousState.vaultArchivePrompt,
                        lastDeletedExpense = previousState.lastDeletedExpense,
                        prefillExpense = previousState.prefillExpense,
                        
                        // Infinite Scroll & Store History Preservation
                        pagedExpenses = preservedPagedExpenses,
                        canLoadMoreExpenses = previousState.canLoadMoreExpenses,
                        isLoadingMoreExpenses = previousState.isLoadingMoreExpenses,
                        selectedStore = previousState.selectedStore,
                        selectedStoreHistory = preservedStoreHistory,
                        isStoreHistoryLoading = previousState.isStoreHistoryLoading,

                        stores = feed.stores,
                        paymentMethods = feed.paymentMethods,
                        creditCardPayments = feed.creditCardPayments.groupBy { it.paymentMethodId }, // Group by card ID
                        mainAccountTransactions = mainAccountTransactions,
                        cashflowForecast = cashflowForecast,
                        spendingPatterns = spendingPatterns,
                        // Smart Insight Engine data
                        recurringPatterns = recurringPatterns,
                        seasonalInsights = seasonalInsights,
                        idleMoneyInsight = idleMoneyInsight,
                        uniqueExpenses = uniqueExpenses,
                        isLoading = false,
                        errorMessage = null
                    )
                }
                .flowOn(Dispatchers.Default)
                .collect { newState ->
                    _uiState.value = newState
                }
        }
    }
        fun addBudget(input: BudgetInput) {
            viewModelScope.launch(dispatcher) {
                val currentMonth = YearMonth.now()
                val existing = _uiState.value.budgets.firstOrNull { it.category == input.category && it.yearMonth == currentMonth }
                val sanitizedLimit = input.monthlyLimit.coerceAtLeast(0.0)
                val budget = existing?.copy(
                    monthlyLimit = sanitizedLimit,
                    isActive = true
                ) ?: CategoryBudget(
                    category = input.category,
                    monthlyLimit = sanitizedLimit,
                    yearMonth = currentMonth,
                    isActive = true
                )
                savingsRepository.upsertBudget(budget)
            }
        }

        fun updateBudget(budget: CategoryBudget) {
            viewModelScope.launch(dispatcher) {
                savingsRepository.upsertBudget(budget.copy(monthlyLimit = budget.monthlyLimit.coerceAtLeast(0.0)))
            }
        }

        fun deleteBudget(id: Long) {
            if (id == 0L) return
            viewModelScope.launch(dispatcher) {
                savingsRepository.deleteBudget(id)
            }
        }

        fun addRecurringExpense(input: RecurringExpenseInput) {
            viewModelScope.launch(dispatcher) {
                val expense = RecurringExpense(
                    description = input.description.trim().ifEmpty { "Recurring payment" },
                    amount = input.amount.coerceAtLeast(0.0),
                    category = input.category,
                    frequency = input.frequency,
                    startDate = input.startDate,
                    endDate = input.endDate,
                    autoLog = input.autoLog,
                    executeAutomatically = input.executeAutomatically,
                    reminderDaysBefore = input.reminderDaysBefore.coerceAtLeast(0),
                    notes = input.notes,
                    storeId = input.storeId,
                    paymentMethodId = input.paymentMethodId,
                    includesTax = input.includesTax,
                    deductFromMainAccount = input.deductFromMainAccount,
                    deductedFromVaultId = input.deductedFromVaultId,
                    manualPercentages = input.manualPercentages,
                    isVariableAmount = input.isVariableAmount,
                    nextRunAt = input.nextRunAt,
                    type = input.type,
                    items = input.items,
                    assetAllocations = input.assetAllocations
                )
                savingsRepository.upsertRecurringExpense(expense)
            }
        }

        fun updateRecurringExpense(expense: RecurringExpense) {
            viewModelScope.launch(dispatcher) {
                val sanitized = expense.copy(amount = expense.amount.coerceAtLeast(0.0))
                savingsRepository.upsertRecurringExpense(sanitized)
            }
        }

        fun deleteRecurringExpense(id: Long) {
            if (id == 0L) return
            viewModelScope.launch(dispatcher) {
                savingsRepository.deleteRecurringExpense(id)
            }
        }

        fun toggleRecurringActive(id: Long, active: Boolean) {
            val current = _uiState.value.recurringExpenses.firstOrNull { it.id == id } ?: return
            updateRecurringExpense(current.copy(isActive = active))
        }

        fun markRecurringProcessed(id: Long) {
            viewModelScope.launch(dispatcher) {
                // Find the expense to mark
                val recurring = _uiState.value.recurringExpenses.find { it.id == id }
                if (recurring != null) {
                    // Create an actual expense entry
                    val input = ExpenseInput(
                        description = recurring.description,
                        amount = recurring.amount,
                        category = recurring.category,
                        date = LocalDate.now(), // Processed today
                        includesTax = recurring.includesTax,
                        manualPercentages = recurring.manualPercentages,
                        deductFromMainAccount = recurring.deductFromMainAccount,
                        deductFromVaultId = recurring.deductedFromVaultId,
                        storeId = recurring.storeId,
                        paymentMethodId = recurring.paymentMethodId,
                        isRecurring = true,
                        type = recurring.type,
                        items = recurring.items,
                        assetAllocations = recurring.assetAllocations
                    )
                    addExpense(input)
                }

                savingsRepository.updateRecurringExpenseProcessed(id, LocalDate.now())
            }
        }

        /**
         * Mark a recurring expense as paid early before the due date.
         * Creates an expense entry and records the early payment.
         */
        fun payRecurringExpenseEarly(
            recurringExpenseId: Long,
            dueDate: java.time.LocalDate,
            amountPaid: Double = 0.0,
            paidDate: java.time.LocalDate = java.time.LocalDate.now(),
            notes: String? = null
        ) {
            viewModelScope.launch(dispatcher) {
                try {
                    val recurring = _uiState.value.recurringExpenses.find { it.id == recurringExpenseId }
                    if (recurring != null) {
                        val finalAmount = if (amountPaid > 0) amountPaid else recurring.amount

                        // 1. Create an actual expense entry for this early payment using addExpense
                        val input = ExpenseInput(
                            description = recurring.description,
                            amount = finalAmount,
                            category = recurring.category,
                            date = paidDate,
                            includesTax = recurring.includesTax,
                            manualPercentages = recurring.manualPercentages,
                            deductFromMainAccount = recurring.deductFromMainAccount,
                            deductFromVaultId = recurring.deductedFromVaultId,
                            storeId = recurring.storeId,
                            paymentMethodId = recurring.paymentMethodId,
                            isRecurring = true,
                            notes = notes?.let { "Early payment (due $dueDate): $it" },
                            type = recurring.type,
                            items = recurring.items,
                            assetAllocations = recurring.assetAllocations
                        )

                        // Use the existing addExpense flow which handles all calculations
                        addExpense(input) {
                            // After expense is created, record the early payment
                            viewModelScope.launch(dispatcher) {
                                try {
                                    savingsRepository.recordRecurringExpensePaidEarly(
                                        recurringExpenseId = recurringExpenseId,
                                        dueDate = dueDate,
                                        amountPaid = finalAmount,
                                        paidDate = paidDate,
                                        notes = notes
                                    )
                                    // Also mark as processed to advance the schedule
                                    savingsRepository.updateRecurringExpenseProcessed(
                                        recurringExpenseId,
                                        dueDate
                                    )
                                } catch (e: Exception) {
                                    // Log error but don't crash
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Log error but don't crash
                }
            }
        }

        /**
         * Check if a recurring expense has been paid for a specific due date.
         */
        suspend fun isRecurringExpensePaid(recurringExpenseId: Long, dueDate: java.time.LocalDate): Boolean {
            return savingsRepository.isRecurringExpensePaidForDate(recurringExpenseId, dueDate)
        }



        fun startChallenge(input: ChallengeInput) {
            viewModelScope.launch(dispatcher) {
                val startDate = LocalDate.now()
                val resolved = when (input.type) {
                    ChallengeType.FIFTY_TWO_WEEK -> ChallengeEngine.createFiftyTwoWeekChallenge(startDate)
                    ChallengeType.DAILY_SAVINGS -> {
                        val rawDuration = ChronoUnit.DAYS.between(startDate, input.endDate)
                        val durationDays = when {
                            rawDuration < 1 -> 30
                            else -> rawDuration.toInt()
                        }.coerceAtLeast(1)
                        val dailyAmount = if (durationDays > 0) input.targetAmount / durationDays else input.targetAmount
                        ChallengeEngine.createDailySavingsChallenge(dailyAmount.coerceAtLeast(1.0), durationDays, startDate)
                    }
                    else -> SavingsChallenge(
                        type = input.type,
                        title = input.title,
                        description = input.description,
                        targetAmount = input.targetAmount,
                        startDate = startDate,
                        endDate = input.endDate
                    )
                }
                val challenge = resolved.copy(
                    title = input.title,
                    description = input.description,
                    targetAmount = input.targetAmount,
                    endDate = input.endDate
                )
                savingsRepository.upsertSavingsChallenge(challenge)
            }
        }

        private fun computeUpcomingRecurring(
            recurring: List<RecurringExpense>,
            today: LocalDate = LocalDate.now()
        ): List<UpcomingRecurringExpense> {
            val windowDays = 30
            return recurring
                .filter { it.isActive }
                .mapNotNull { expense ->
                    var nextDue = expense.nextRunAt?.toLocalDate() 
                        ?: expense.lastProcessedDate?.let { addFrequencyInterval(it, expense.frequency) }
                        ?: expense.startDate
                    
                    // Advance until we reach a future date or today (if not processed today)
                    while (nextDue.isBefore(today) && expense.lastProcessedDate != today) {
                        nextDue = addFrequencyInterval(nextDue, expense.frequency)
                    }
                    
                    expense.endDate?.let { end ->
                        if (nextDue.isAfter(end)) return@mapNotNull null
                    }
                    val daysUntilDue = ChronoUnit.DAYS.between(today, nextDue).toInt()
                    if (daysUntilDue < 0 || daysUntilDue > windowDays) return@mapNotNull null
                    UpcomingRecurringExpense(expense, nextDue, daysUntilDue)
                }
                .sortedBy { it.dueDate }
        }
        
        /**
         * Add one frequency interval to a date.
         * For monthly/quarterly/yearly, this preserves the day of month.
         */
        private fun addFrequencyInterval(date: LocalDate, frequency: RecurringFrequency): LocalDate {
            return when (frequency) {
                RecurringFrequency.DAILY -> date.plusDays(1)
                RecurringFrequency.WEEKLY -> date.plusWeeks(1)
                RecurringFrequency.BIWEEKLY -> date.plusWeeks(2)
                RecurringFrequency.MONTHLY -> date.plusMonths(1)
                RecurringFrequency.QUARTERLY -> date.plusMonths(3)
                RecurringFrequency.YEARLY -> date.plusYears(1)
            }
        }

    private fun buildSmartSavingSummary(
        settings: SparelySettings,
        analytics: AnalyticsSnapshot,
        recommendation: RecommendationResult?
    ): SmartSavingSummary {
        val observedSavings = analytics.averageMonthlyReserve.coerceAtLeast(0.0)
        val observedSpending = analytics.averageMonthlyExpense.coerceAtLeast(0.0)
        val observedTotal = observedSavings + observedSpending
        val monthlyIncome = settings.monthlyIncome.coerceAtLeast(0.0)

        // Prefer an observed savings rate from actual spending patterns; fall back to income if needed.
        val actualRate = when {
            observedTotal > 0.0 -> (observedSavings / observedTotal).coerceIn(0.0, 1.0)
            monthlyIncome > 0.0 -> (observedSavings / monthlyIncome).coerceIn(0.0, 1.0)
            else -> 0.0
        }
        val recommendedSplit = recommendation?.recommendedPercentages ?: settings.defaultPercentages
        return SmartSavingSummary(
            targetSavingsRate = settings.targetSavingsRate,
            actualSavingsRate = actualRate,
            allocationMode = settings.smartAllocationMode,
            recommendedSplit = recommendedSplit,
            manualSplit = settings.defaultPercentages
        )
    }

    private fun detectRecurringTransactions(expenses: List<Expense>): List<DetectedRecurringTransaction> {
        if (expenses.isEmpty()) return emptyList()
        val windowStart = LocalDate.now().minusDays(180)
        val grouped = expenses
            .filter { !it.date.isBefore(windowStart) }
            .groupBy { it.description.trim().lowercase() }
        return grouped.values.mapNotNull { cluster ->
            if (cluster.size < 3) return@mapNotNull null
            val sorted = cluster.sortedBy { it.date }
            val intervals = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date).toInt() }
            val positiveIntervals = intervals.filter { it > 0 }
            if (positiveIntervals.isEmpty()) return@mapNotNull null
            val cadence = positiveIntervals.average().takeIf { !it.isNaN() } ?: return@mapNotNull null
            val roundedCadence = cadence.roundToInt().coerceAtLeast(1)
            val daysSinceLast = ChronoUnit.DAYS.between(sorted.last().date, LocalDate.now()).toInt()
            // Drop patterns that appear stale (one cadence + short grace window)
            if (daysSinceLast > roundedCadence + 7) return@mapNotNull null
            val averageAmount = cluster.map { it.amount }.average()
            DetectedRecurringTransaction(
                description = sorted.first().description,
                averageAmount = averageAmount,
                cadenceDays = roundedCadence,
                lastOccurrence = sorted.last().date,
                suggestedCategory = sorted.first().category
            )
        }
            .sortedBy { it.cadenceDays }
            .take(8)
    }

    private fun List<DetectedRecurringTransaction>.pruneStaleRecurring(
        existingRecurring: List<RecurringExpense> = emptyList(),
        now: LocalDate = LocalDate.now()
    ): List<DetectedRecurringTransaction> {
        val existingDescriptions = existingRecurring.map { it.description.trim().lowercase() }.toSet()
        return filter { insight ->
            val daysSinceLast = ChronoUnit.DAYS.between(insight.lastOccurrence, now).toInt()
            val isStale = daysSinceLast > insight.cadenceDays + 7
            val isAlreadyKnown = insight.description.trim().lowercase() in existingDescriptions
            !isStale && !isAlreadyKnown
        }
    }

    suspend fun findVaultIdForContribution(contributionId: Long): Long? {
        return savingsRepository.getVaultContributionById(contributionId)?.vaultId
    }

    fun startRecurringFromInsight(insight: DetectedRecurringTransaction) {
        _uiState.update { state -> state.copy(pendingDetectedRecurring = insight) }
    }

    fun clearPendingDetectedRecurring() {
        _uiState.update { state -> state.copy(pendingDetectedRecurring = null) }
    }

    fun addDetectedRecurring(input: RecurringExpenseInput, insight: DetectedRecurringTransaction) {
        viewModelScope.launch(dispatcher) {
            val expense = RecurringExpense(
                description = input.description.trim().ifEmpty { "Recurring payment" },
                amount = input.amount.coerceAtLeast(0.0),
                category = input.category,
                frequency = input.frequency,
                startDate = input.startDate,
                endDate = input.endDate,
                autoLog = input.autoLog,
                reminderDaysBefore = input.reminderDaysBefore.coerceAtLeast(0),
                notes = input.notes,
                includesTax = input.includesTax,
                deductFromMainAccount = input.deductFromMainAccount,
                deductedFromVaultId = input.deductedFromVaultId,
                manualPercentages = input.manualPercentages,
                storeId = input.storeId,
                paymentMethodId = input.paymentMethodId,
                nextRunAt = input.nextRunAt
            )
            savingsRepository.upsertRecurringExpense(expense)

            _uiState.update { state ->
                state.copy(
                    detectedRecurringTransactions = state.detectedRecurringTransactions.filterNot {
                        it.description.equals(insight.description, ignoreCase = true)
                    },
                    pendingDetectedRecurring = null
                )
            }
        }
    }

    fun addExpense(input: ExpenseInput, onComplete: () -> Unit = {}) {
        safeLaunch {
            val currentState = _uiState.value
            val settings = currentState.settings
            val recommendedPercentages = when {
                input.manualPercentages != null -> input.manualPercentages
                settings.autoRecommendationsEnabled && currentState.recommendation != null ->
                    currentState.recommendation.recommendedPercentages
                else -> settings.defaultPercentages
            }
            val allocation = SavingsCalculator.calculateAllocation(input, recommendedPercentages, settings.riskLevel)
            val applied = recommendedPercentages.adjustWithinBudget()
            val entity = ExpenseEntity(
                id = input.id ?: 0L,
                description = input.description.trim().ifEmpty { "General purchase" },
                amount = input.amount,
                category = input.category,
                date = input.date,
                includesTax = input.includesTax,
                emergencyAmount = allocation.emergencyAmount,
                investmentAmount = allocation.investmentAmount,
                funAmount = allocation.funAmount,
                safeInvestmentAmount = allocation.safeInvestmentAmount,
                highRiskInvestmentAmount = allocation.highRiskInvestmentAmount,
                autoRecommended = input.manualPercentages == null && settings.autoRecommendationsEnabled,
                appliedPercentEmergency = applied.emergency,
                appliedPercentInvest = applied.invest,
                appliedPercentFun = applied.`fun`,
                appliedSafeSplit = applied.safeInvestmentSplit,
                riskLevelUsed = settings.riskLevel,
                deductedFromVaultId = input.deductFromVaultId,
                storeId = input.storeId,
                paymentMethodId = input.paymentMethodId,
                isRecurring = input.isRecurring,
                notes = input.notes,
                orderNumber = input.orderNumber,
                type = input.type.name
            )
            val insertedExpenseId = savingsRepository.upsertExpense(entity)

            // Save line items if present (filter out items with no name or price)
            val validItems = input.items.filter { it.name.isNotBlank() && it.totalPrice > 0 }
            if (validItems.isNotEmpty()) {
                val itemsWithId = validItems.map { it.copy(expenseId = insertedExpenseId, id = 0L) }
                savingsRepository.insertExpenseItems(itemsWithId)
            }

            // Link assets if specified
            if (input.assetAllocations.isNotEmpty()) {
                input.assetAllocations.forEach { (assetId, percentageAllocated) ->
                    savingsRepository.linkExpenseToAsset(insertedExpenseId, assetId, percentageAllocated)
                }
            }

            savingsRepository.withMainAccountLock {
            // Get current balance once at the start
            var currentBalance = savingsRepository.getLatestMainAccountBalance()

            // Handle vault deduction if specified
            if (input.deductFromVaultId != null) {
                val vault = currentState.smartVaults.find { it.id == input.deductFromVaultId }
                if (vault != null) {
                    val vaultBalanceBefore = vault.currentBalance
                    val expenseAmount = input.amount
                    
                    // Calculate how much to deduct from vault and overflow
                    val deductFromVault = expenseAmount.coerceAtMost(vaultBalanceBefore)
                    val overflowToMainAccount = (expenseAmount - vaultBalanceBefore).coerceAtLeast(0.0)
                    val vaultBalanceAfter = (vaultBalanceBefore - deductFromVault).coerceAtLeast(0.0)
                    
                    // Deduct from vault
                    if (deductFromVault > 0.0) {
                        savingsRepository.recordVaultBalanceAdjustment(
                            vaultId = vault.id,
                            previousBalance = vaultBalanceBefore,
                            newBalance = vaultBalanceAfter,
                            type = VaultAdjustmentType.MANUAL_DEDUCTION,
                            reason = "Expense: ${input.description.take(100)}"
                        )
                    }
                    
                    // Deduct overflow from main account if specified
                    if (overflowToMainAccount > 0.0 && input.deductFromMainAccount) {
                        val newBalance = (currentBalance - overflowToMainAccount).coerceAtLeast(0.0)
                        val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                            type = com.example.sparely.data.local.MainAccountTransactionType.EXPENSE,
                            amount = overflowToMainAccount,
                            balanceAfter = newBalance,
                            timestamp = java.time.LocalDateTime.now(),
                            description = "Overflow from ${vault.name} expense: ${input.description.take(70)}",
                            relatedExpenseId = insertedExpenseId
                        )
                        savingsRepository.insertMainAccountTransaction(transaction)
                        preferencesRepository.updateMainAccountBalance(newBalance)
                        currentBalance = newBalance
                    }
                    
                    // Check if we should prompt for archive (90% or more of vault used)
                    val percentageUsed = if (vaultBalanceBefore > 0.0) {
                        (deductFromVault / vaultBalanceBefore)
                    } else {
                        0.0
                    }
                    
                    if (percentageUsed >= 0.90) {
                        _uiState.update { state ->
                            state.copy(
                                vaultArchivePrompt = VaultArchivePrompt(
                                    vaultId = vault.id,
                                    vaultName = vault.name,
                                    expenseAmount = expenseAmount,
                                    vaultBalanceBefore = vaultBalanceBefore,
                                    vaultBalanceAfter = vaultBalanceAfter,
                                    overflowToMainAccount = overflowToMainAccount
                                )
                            )
                        }
                    }
                }
            } else if (input.deductFromMainAccount) {
                // Check if payment method is a credit card
                val paymentMethod = if (input.paymentMethodId != null) {
                    currentState.paymentMethods.find { it.id == input.paymentMethodId }
                } else null
                val isCreditCardPayment = paymentMethod?.isCreditCard == true
                
                if (isCreditCardPayment) {
                    // Credit card payment: DO NOTHING here as SavingsRepository handles balance update.
                    // We explicitly do NOT deduct from main account.
                } else {
                    // Non-credit card payment: deduct from main account
                    val newBalance = (currentBalance - input.amount).coerceAtLeast(0.0)
                    val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                        type = com.example.sparely.data.local.MainAccountTransactionType.EXPENSE,
                        amount = input.amount,
                        balanceAfter = newBalance,
                        timestamp = java.time.LocalDateTime.now(),
                        description = input.description.take(100),
                        relatedExpenseId = insertedExpenseId
                    )
                    savingsRepository.insertMainAccountTransaction(transaction)
                    preferencesRepository.updateMainAccountBalance(newBalance)
                    currentBalance = newBalance
                }
            }
            
            val savingTaxPlans = SavingTaxEngine.calculate(
                SavingTaxEngine.Context(
                    expenseAmount = input.amount,
                    expenseDate = input.date,
                    settings = settings,
                    vaults = currentState.smartVaults,
                    currentMainAccountBalance = currentBalance
                )
            )
            if (savingTaxPlans.isNotEmpty()) {
                val contributions = savingTaxPlans.map { plan ->
                    VaultContribution(
                        vaultId = plan.vaultId,
                        amount = plan.amount,
                        date = input.date,
                        source = VaultContributionSource.SAVING_TAX,
                        note = "Saving tax from ${input.description}".take(120),
                        relatedExpenseId = insertedExpenseId
                    )
                }
                val contributionIds = savingsRepository.logVaultContributions(contributions)
            
                // Deduct total saving tax from main account (using updated balance from expense deduction if applicable)
                val totalSavingTax = savingTaxPlans.sumOf { it.amount }
                if (totalSavingTax > 0.0) {
                    val newBalance = (currentBalance - totalSavingTax).coerceAtLeast(0.0)
                    val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                        type = com.example.sparely.data.local.MainAccountTransactionType.VAULT_CONTRIBUTION,
                        amount = totalSavingTax,
                        balanceAfter = newBalance,
                        timestamp = java.time.LocalDateTime.now(),
                        description = "Saving tax to ${savingTaxPlans.size} vault(s)",
                        relatedVaultContributionIds = contributionIds
                    )
                    savingsRepository.insertMainAccountTransaction(transaction)
                    preferencesRepository.updateMainAccountBalance(newBalance)
                }
            }
            }

            // Refresh paged list to include new expense
            _uiState.update { it.copy(pagedExpenses = emptyList(), canLoadMoreExpenses = true) }
            loadMoreExpenses()

            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

fun deleteExpense(id: Long) {
    safeLaunch {
        val expenseEntity = savingsRepository.findExpenseById(id) ?: return@safeLaunch
        _uiState.update { it.copy(lastDeletedExpense = expenseEntity.toDomain()) }
        
        // 1. Revert financial impact
        // Calculate how much was actually deducted (considering refunds)
        val originalAmount = expenseEntity.amount
        val refundedSoFar = expenseEntity.refundedAmount
        val netAmountDeleted = (originalAmount - refundedSoFar).coerceAtLeast(0.0)
        
        if (netAmountDeleted > 0.0) {
             val paymentMethodId = expenseEntity.paymentMethodId
             val paymentMethod = if (paymentMethodId != null) {
                 savingsRepository.getPaymentMethodById(paymentMethodId)
             } else null
             
             if (paymentMethod?.isCreditCard == true) {
                 // Determine if we should reduce credit card balance. Usually yes.
                 // We add negative amount to reduce balance
                 // HANDLED BY REPOSITORY
             } else {
                 // Refund to main account
                 savingsRepository.withMainAccountLock {
                     val currentBalance = savingsRepository.getLatestMainAccountBalance()
                     val newBalance = currentBalance + netAmountDeleted
                     val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                        type = com.example.sparely.data.local.MainAccountTransactionType.DEPOSIT,
                        amount = netAmountDeleted,
                        balanceAfter = newBalance,
                        timestamp = java.time.LocalDateTime.now(),
                        description = "Reversal of deleted expense: ${expenseEntity.description}",
                        relatedExpenseId = null // Set to null since the expense is being deleted
                    )
                    savingsRepository.insertMainAccountTransaction(transaction)
                    preferencesRepository.updateMainAccountBalance(newBalance)
                 }
             }
        }
        
        // 2. Cancel pending tax contributions
        savingsRepository.deletePendingContributionsForExpense(id)

        // 3. Delete the expense record
        savingsRepository.deleteExpense(expenseEntity)
    }
}

fun refundExpense(expenseId: Long, refundAmount: Double, refundedItemIds: List<Long> = emptyList()) {
    safeLaunch {
        savingsRepository.processExpenseRefund(
            expenseId = expenseId,
            requestedAmount = refundAmount,
            refundedItemIds = refundedItemIds
        )
    }
}

    fun undoDeleteExpense() {
        val lastDeleted = _uiState.value.lastDeletedExpense ?: return
        viewModelScope.launch(dispatcher) {
            savingsRepository.upsertExpense(lastDeleted.toEntity())
            _uiState.update { it.copy(lastDeletedExpense = null) }
        }
    }

    fun clearDeletedExpense() {
        _uiState.update { it.copy(lastDeletedExpense = null) }
    }
    
    // Repeat Last Expense support
    fun setPrefillExpense(expense: com.example.sparely.domain.model.Expense?) {
        _uiState.update { it.copy(prefillExpense = expense) }
        // Load asset allocations for this expense
        expense?.let { exp ->
            viewModelScope.launch(dispatcher) {
                try {
                    val links = savingsRepository.getLinksForExpense(exp.id)
                    val allocations = links.associate { it.assetId to (it.percentageAllocated / 100.0) }
                    _uiState.update { it.copy(prefillAssetAllocations = allocations) }
                } catch (e: Exception) {
                    // Log error but don't crash - just use empty allocations
                }
            }
        } ?: run {
            _uiState.update { it.copy(prefillAssetAllocations = emptyMap()) }
        }
    }

    fun clearPrefillExpense() {
        _uiState.update { it.copy(prefillExpense = null, prefillAssetAllocations = emptyMap()) }
    }

    suspend fun getAssetAllocationsForExpense(expenseId: Long): Map<Long, Double> {
        return try {
            savingsRepository.getLinksForExpense(expenseId)
                .associate { it.assetId to (it.percentageAllocated / 100.0) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun getExpensesLinkedToAsset(assetId: Long): List<Pair<com.example.sparely.domain.model.Expense, Double>> {
        return savingsRepository.getExpensesLinkedToAsset(assetId)
    }
    
    fun dismissVaultArchivePrompt() {
        _uiState.update { it.copy(vaultArchivePrompt = null) }
    }
    
    fun archiveVaultFromPrompt(vaultId: Long) {
        viewModelScope.launch(dispatcher) {
            savingsRepository.updateVaultArchived(vaultId, true)
            _uiState.update { it.copy(vaultArchivePrompt = null) }
        }
    }

    fun resetHistory(clearVaults: Boolean = false) {
        viewModelScope.launch(dispatcher) {
            savingsRepository.clearExpenses()
            savingsRepository.clearTransfers()
            if (clearVaults) {
                savingsRepository.clearSmartVaults()
            }
        }
    }



    // Store functions
    fun addStore(input: StoreInput): kotlinx.coroutines.Deferred<Long> {
        return viewModelScope.async(dispatcher) {
            val store = Store(
                name = input.name.trim(),
                websiteUrl = input.websiteUrl?.trim(),
                iconName = input.iconName
            )
            savingsRepository.insertStore(store)
        }
    }

    suspend fun searchStores(query: String): List<Store> {
        return savingsRepository.searchStores(query)
    }

    suspend fun getStoreById(id: Long): Store? {
        return savingsRepository.getStoreById(id)
    }

    fun updateStore(store: Store) {
        viewModelScope.launch(dispatcher) {
            savingsRepository.updateStore(store)
        }
    }

    fun deleteStore(store: Store) {
        viewModelScope.launch(dispatcher) {
            savingsRepository.deleteStore(store)
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch(dispatcher) {
            updateExpenseWithAssets(expense, emptyMap())
        }
    }

    fun updateExpenseWithAssets(expense: Expense, assetAllocations: Map<Long, Double>) {
        safeLaunch {
            val oldEntity = savingsRepository.findExpenseById(expense.id) ?: return@safeLaunch

            // 1. Calculate difference in amount
            val oldAmount = oldEntity.amount
            val newAmount = expense.amount
            val diff = newAmount - oldAmount

            if (abs(diff) > 0.001) { savingsRepository.withMainAccountLock {
                // Amount changed, adjust balances
                val paymentMethodId = expense.paymentMethodId // Assuming payment method didn't change for now, or use new one
                val paymentMethod = if (paymentMethodId != null) {
                     savingsRepository.getPaymentMethodById(paymentMethodId)
                } else null
                
                val effectiveMainBalance = if (paymentMethod?.isCreditCard == true) {
                    // If amount increased (diff > 0), add to CC debt. 
                    // If decreased (diff < 0), reduce CC debt.
                    // HANDLED BY REPOSITORY
                    _uiState.value.settings.mainAccountBalance
                } else {
                    // Main Account logic
                    // If amount increased (diff > 0), deduct from Main Account.
                    // If decreased (diff < 0), refund to Main Account.
                    val currentBalance = savingsRepository.getLatestMainAccountBalance()
                    // We subtract the difference. E.g. price up $10 -> balance down $10. Price down $10 (-10) -> balance up $10.
                    val newBalance = (currentBalance - diff).coerceAtLeast(0.0)
                    
                    val transactionType = if (diff > 0) com.example.sparely.data.local.MainAccountTransactionType.EXPENSE 
                                          else com.example.sparely.data.local.MainAccountTransactionType.DEPOSIT
                                          
                    val description = if (diff > 0) "Adjustment: Price increased for ${expense.description}"
                                      else "Adjustment: Price decreased for ${expense.description}"
                                          
                    val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                        type = transactionType,
                        amount = abs(diff),
                        balanceAfter = newBalance,
                        timestamp = java.time.LocalDateTime.now(),
                        description = description,
                        relatedExpenseId = expense.id
                    )
                    savingsRepository.insertMainAccountTransaction(transaction)
                    preferencesRepository.updateMainAccountBalance(newBalance)
                    newBalance
                }
                
                // 2. Handle Saving Tax Updates
                // Cancel old pending tax
                savingsRepository.deletePendingContributionsForExpense(expense.id)
                
                // Recalculate and log new tax
                val currentState = _uiState.value
                val savingTaxPlans = SavingTaxEngine.calculate(
                    SavingTaxEngine.Context(
                        expenseAmount = newAmount,
                        expenseDate = expense.date,
                        settings = currentState.settings,
                        vaults = currentState.smartVaults,
                        currentMainAccountBalance = effectiveMainBalance
                    )
                )
                
                if (savingTaxPlans.isNotEmpty()) {
                    val contributions = savingTaxPlans.map { plan ->
                        VaultContribution(
                            vaultId = plan.vaultId,
                            amount = plan.amount,
                            date = expense.date,
                            source = VaultContributionSource.SAVING_TAX,
                            note = "Saving tax (adjusted) from ${expense.description}".take(120),
                            relatedExpenseId = expense.id
                        )
                    }
                    val contributionIds = savingsRepository.logVaultContributions(contributions)
                    
                    // Note: We are NOT automatically deducting the new tax from main account here to avoid double whammy if user already paid old tax?
                    // Actually, if we deleted pending tax, we assume it wasn't paid.
                    // If it WAS paid, we might have an issue. 
                    // Current design: 'deletePendingContributionsForExpense' only deletes UNRECONCILED ones.
                    // So if we add new ones here, they will be pending. 
                    // The user will see them in "Pending Transfers" and can approve them.
                    // We do NOT auto-deduct the tax difference from Main Account in this update flow to keep it safe.
                    // The user will approve the new tax transfer separately.
                }
            } }

            // 3. Update the expense record itself
            val entity = expense.toEntity()
            savingsRepository.upsertExpense(entity)
            
            // 4. Update line items: delete existing and insert new ones (filter out items with no name or price)
            savingsRepository.deleteItemsForExpense(expense.id)
            val validItems = expense.items.filter { it.name.isNotBlank() && it.totalPrice > 0 }
            if (validItems.isNotEmpty()) {
                val itemsWithId = validItems.map { it.copy(expenseId = expense.id, id = 0L) }
                savingsRepository.insertExpenseItems(itemsWithId)
            }

            // 5. Update asset allocations if provided
            if (assetAllocations.isNotEmpty()) {
                // Delete old allocations
                val oldLinks = savingsRepository.getLinksForExpense(expense.id)
                oldLinks.forEach { link ->
                    savingsRepository.unlinkExpenseFromAsset(expense.id, link.assetId)
                }
                // Add new allocations
                assetAllocations.forEach { (assetId, percentageAllocated) ->
                    savingsRepository.linkExpenseToAsset(expense.id, assetId, percentageAllocated)
                }
            }
        }
    }






    fun depositToMainAccount(amount: Double, description: String, incomeCategory: com.example.sparely.domain.model.IncomeCategory? = null) {
        if (amount <= 0.0) return
        safeLaunch {
            savingsRepository.withMainAccountLock {
                val currentBalance = savingsRepository.getLatestMainAccountBalance()
                val newBalance = currentBalance + amount
                val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                    type = com.example.sparely.data.local.MainAccountTransactionType.DEPOSIT,
                    amount = amount,
                    balanceAfter = newBalance,
                    timestamp = java.time.LocalDateTime.now(),
                    description = description.take(100),
                    incomeCategory = incomeCategory
                )
                savingsRepository.insertMainAccountTransaction(transaction)
                preferencesRepository.updateMainAccountBalance(newBalance)
            }
        }
    }

    fun withdrawFromMainAccount(amount: Double, description: String) {
        if (amount <= 0.0) return
        safeLaunch {
            savingsRepository.withMainAccountLock {
                val currentBalance = savingsRepository.getLatestMainAccountBalance()
                val newBalance = (currentBalance - amount).coerceAtLeast(0.0)
                val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                    type = com.example.sparely.data.local.MainAccountTransactionType.WITHDRAWAL,
                    amount = amount,
                    balanceAfter = newBalance,
                    timestamp = java.time.LocalDateTime.now(),
                    description = description.take(100)
                )
                savingsRepository.insertMainAccountTransaction(transaction)
                preferencesRepository.updateMainAccountBalance(newBalance)
            }
        }
    }

    fun adjustMainAccountBalance(newBalance: Double, reason: String) {
        safeLaunch {
            savingsRepository.withMainAccountLock {
                val currentBalance = savingsRepository.getLatestMainAccountBalance()
                val delta = newBalance - currentBalance
                val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                    type = com.example.sparely.data.local.MainAccountTransactionType.ADJUSTMENT,
                    amount = abs(delta),
                    balanceAfter = newBalance.coerceAtLeast(0.0),
                    timestamp = java.time.LocalDateTime.now(),
                    description = reason.take(100)
                )
                savingsRepository.insertMainAccountTransaction(transaction)
                preferencesRepository.updateMainAccountBalance(newBalance.coerceAtLeast(0.0))
            }
        }
    }



    fun recordPaycheck(
        amount: Double,
        payday: LocalDate,
        autoDistribute: Boolean,
        createPendingTransfers: Boolean,
        incomeCategory: com.example.sparely.domain.model.IncomeCategory? = null,
        description: String = "Paycheck"
    ) {
        if (amount <= 0.0) return
        safeLaunch {
            val settingsSnapshot = preferencesRepository.getSettingsSnapshot()
            val schedule = settingsSnapshot.paySchedule
            val normalizedAmount = amount.toCurrencyPrecision()
            var mutatedSchedule = schedule

            var automationSaveRate = (schedule.lastComputedSaveRate ?: schedule.defaultSaveRate).coerceIn(0.0, 1.0)
            if (schedule.dynamicSaveRateEnabled || settingsSnapshot.dynamicSavingTaxEnabled) {
                val automationInput = IncomeAutomationEngine.Input(
                    currentPayAmount = normalizedAmount,
                    schedule = schedule,
                    settings = settingsSnapshot,
                    analytics = _uiState.value.analytics,
                    recurringExpenses = _uiState.value.recurringExpenses
                )
                val recommendation = IncomeAutomationEngine.evaluate(automationInput)
                if (schedule.dynamicSaveRateEnabled) {
                    val recommendedRate = recommendation.saveRate.coerceIn(0.0, 1.0)
                    automationSaveRate = recommendedRate
                    if (isMeaningfullyDifferent(schedule.lastComputedSaveRate, recommendedRate)) {
                        mutatedSchedule = mutatedSchedule.copy(lastComputedSaveRate = recommendedRate)
                    }
                }
                if (settingsSnapshot.dynamicSavingTaxEnabled && isMeaningfullyDifferent(settingsSnapshot.lastComputedSavingTaxRate, recommendation.savingTaxRate)) {
                    preferencesRepository.updateSavingTaxRate(recommendation.savingTaxRate, fromAutomation = true)
                }
            }

            var vaultContributionAmount = 0.0
            val balanceAfterPaycheck = savingsRepository.withMainAccountLock {
            if (autoDistribute) {
                val saveRate = if (schedule.dynamicSaveRateEnabled) {
                    automationSaveRate
                } else {
                    schedule.defaultSaveRate.coerceIn(0.0, 1.0)
                }
                val amountForVaults = (normalizedAmount * saveRate).toCurrencyPrecision()
                if (amountForVaults > 0.0) {
                    // Use the current main account balance + paycheck deposit as the mainAccountBalance
                    // so the smart allocation engine can consider the new deposit when computing urgency
                    val balanceBefore = savingsRepository.getLatestMainAccountBalance()
                    val expectedMainAccountBalance = (balanceBefore + normalizedAmount)

                    val planned = planIncomeContributions(
                        amount = amountForVaults,
                        payday = payday,
                        createPending = createPendingTransfers,
                        mainAccountBalance = expectedMainAccountBalance
                    )
                    if (planned.isNotEmpty()) {
                        val contributionIds = savingsRepository.logVaultContributions(planned)
                        vaultContributionAmount = amountForVaults
                        if (createPendingTransfers) {
                            // UI refresh handled by flows
                        }
                    }
                }
            }

            val nextDate = if (schedule.trackingMode == IncomeTrackingMode.MANUAL_PER_PAYCHECK) {
                null
            } else {
                PayScheduleCalculator.computeNextPayDate(schedule, payday)
            }

            mutatedSchedule = mutatedSchedule.copy(
                lastPayDate = payday,
                lastPayAmount = normalizedAmount,
                nextPayDate = nextDate
            )
            preferencesRepository.updatePaySchedule(mutatedSchedule)
            preferencesRepository.recordPaycheckHistory(normalizedAmount)
            
            // Add paycheck to main account and deduct vault contributions
            var currentBalance = savingsRepository.getLatestMainAccountBalance()
            
            // 1. Add paycheck deposit
            currentBalance += normalizedAmount
            val depositTransaction = com.example.sparely.domain.model.MainAccountTransaction(
                type = com.example.sparely.data.local.MainAccountTransactionType.DEPOSIT,
                amount = normalizedAmount,
                balanceAfter = currentBalance,
                timestamp = java.time.LocalDateTime.now(),
                description = description.take(100),
                incomeCategory = incomeCategory
            )
            savingsRepository.insertMainAccountTransaction(depositTransaction)
            preferencesRepository.updateMainAccountBalance(currentBalance)
            
            // 2. Deduct vault contributions if auto-distributed
            if (vaultContributionAmount > 0.0) {
                currentBalance -= vaultContributionAmount
                val vaultTransaction = com.example.sparely.domain.model.MainAccountTransaction(
                    type = com.example.sparely.data.local.MainAccountTransactionType.VAULT_CONTRIBUTION,
                    amount = vaultContributionAmount,
                    balanceAfter = currentBalance,
                    timestamp = java.time.LocalDateTime.now(),
                    description = "Auto-distributed to vaults from paycheck"
                )
                savingsRepository.insertMainAccountTransaction(vaultTransaction)
                preferencesRepository.updateMainAccountBalance(currentBalance)
            }
            currentBalance
            }
            // Trigger allocation run now that the paycheck and any auto-distributions have been applied
            try {
                // Use the paycheck date as 'today' for allocation so flows starting next month are prioritized correctly
                container.smartAllocationService.runMonthlyAllocation(
                    monthlyIncome = settingsSnapshot.monthlyIncome,
                    mainAccountBalance = balanceAfterPaycheck,
                    today = payday,
                    mainOverflowAccountId = settingsSnapshot.mainOverflowAccountId
                )
            } catch (e: Exception) {
                // Non-fatal: log and continue
                android.util.Log.e("SparelyViewModel", "Failed to run smart allocation on paycheck: ${e.message}")
            }

            notificationScheduler.dismissPaydayReminderNotification()
            val refreshedSettings = preferencesRepository.getSettingsSnapshot()
            notificationScheduler.schedulePaydayReminder(refreshedSettings)
        }
    }


    
    // Vault Management moved to VaultViewModel

    
    
    // Checkpoint A
    
    // Checkpoint B

    

    

    





    

    


    fun logManualTransfer(
        category: SavingsCategory,
        amount: Double,
        sourceAccountId: Long? = null,
        destinationAccountId: Long? = null,
        note: String? = null
    ) {
        if (amount <= 0.0) return
        viewModelScope.launch(dispatcher) {
            val transfer = SavingsTransferEntity(
                category = category,
                amount = amount.toCurrencyPrecision(),
                date = LocalDate.now(),
                sourceAccountId = sourceAccountId,
                destinationAccountId = destinationAccountId,
                note = note
            )
            savingsRepository.logTransfer(transfer)
        }
    }

    private suspend fun planIncomeContributions(
        amount: Double,
        payday: LocalDate,
        createPending: Boolean,
        mainAccountBalance: Double
    ): List<VaultContribution> {
        if (amount <= 0.0) return emptyList()
        val state = _uiState.value
        val vaults = state.smartVaults.filter { !it.archived }
        if (vaults.isEmpty()) return emptyList()

        // Ask the smart allocation service for suggested monthly allocations
        val result = try {
            container.smartAllocationService.runMonthlyAllocation(
                monthlyIncome = state.settings.monthlyIncome,
                mainAccountBalance = mainAccountBalance,
                today = payday,
                mainOverflowAccountId = state.settings.mainOverflowAccountId
            )
        } catch (e: Exception) {
            // Fall back to previous dynamic allocation if the smart service fails
            android.util.Log.e("SparelyViewModel", "Smart allocation failed during paycheck planning: ${e.message}")
            null
        }

        // If service failed or produced nothing, fall back to existing weight-based distribution
        if (result == null || result.allocations.isEmpty()) {
            val weights = DynamicAllocationEngine.calculateWeights(
                vaults = vaults,
                settings = state.settings,
                today = payday
            )
            if (weights.isEmpty()) return emptyList()
            val sortedWeights = weights.sortedByDescending { it.weight }
            val contributions = mutableListOf<VaultContribution>()
            var allocated = 0.0
            sortedWeights.forEachIndexed { index, weight ->
                val rawShare = amount * weight.weight
                val share = if (index == sortedWeights.lastIndex) {
                    (amount - allocated).coerceAtLeast(0.0)
                } else {
                    rawShare
                }
                val rounded = share.toCurrencyPrecision()
                if (rounded > 0.0) {
                    allocated += rounded
                    contributions.add(
                        VaultContribution(
                            vaultId = weight.vaultId,
                            amount = rounded,
                            date = payday,
                            source = VaultContributionSource.INCOME,
                            note = "Paycheck allocation",
                            reconciled = !createPending
                        )
                    )
                }
            }
            return contributions
        }

        // Scale smart allocation suggestions down to the paycheck's amount proportionally
        val suggested = result.allocations.filterKeys { id -> vaults.any { it.id == id } }
        val totalSuggested = suggested.values.sumOf { it }
        if (totalSuggested <= 0.0) return emptyList()

        val contributions = mutableListOf<VaultContribution>()
        var allocated = 0.0
        val entries = suggested.entries.toList()
        entries.forEachIndexed { index, entry ->
            val vaultId = entry.key
            val suggestedAmount = entry.value
            val proportion = (suggestedAmount / totalSuggested).coerceAtLeast(0.0)
            val raw = amount * proportion
            val share = if (index == entries.lastIndex) {
                (amount - allocated).coerceAtLeast(0.0)
            } else {
                raw
            }
            val rounded = share.toCurrencyPrecision()
            if (rounded > 0.0) {
                allocated += rounded
                contributions.add(
                    VaultContribution(
                        vaultId = vaultId,
                        amount = rounded,
                        date = payday,
                        source = VaultContributionSource.INCOME,
                        note = "Paycheck allocation (smart)",
                        reconciled = !createPending
                    )
                )
            }
        }

        return contributions
    }



    fun completeOnboarding(profile: UserProfileSetup) {
        viewModelScope.launch(dispatcher) {
            preferencesRepository.updateMonthlyIncome(profile.monthlyIncome)
            preferencesRepository.updateAge(profile.age)
            preferencesRepository.updateRiskLevel(profile.riskLevel)
            preferencesRepository.updateEducationStatus(profile.educationStatus)
            preferencesRepository.updateEmploymentStatus(profile.employmentStatus)
            preferencesRepository.updateLivingSituation(profile.livingSituation)
            preferencesRepository.updateOccupation(profile.occupation)
            if (profile.mainAccountBalance > 0.0) {
                val transaction = com.example.sparely.domain.model.MainAccountTransaction(
                    type = com.example.sparely.data.local.MainAccountTransactionType.DEPOSIT,
                    amount = profile.mainAccountBalance,
                    balanceAfter = profile.mainAccountBalance,
                    timestamp = java.time.LocalDateTime.now(),
                    description = "Initial deposit from onboarding"
                )
                savingsRepository.insertMainAccountTransaction(transaction)
                preferencesRepository.updateMainAccountBalance(profile.mainAccountBalance)
            } else {
                preferencesRepository.updateMainAccountBalance(0.0)
            }
            preferencesRepository.updateSavingsAccountBalance(profile.savingsAccountBalance)
            preferencesRepository.updateHasDebts(profile.hasDebts)
            preferencesRepository.updateEmergencyFund(profile.currentEmergencyFund)
            val subscriptionTotal = profile.subscriptions.sumOf { it.amount.coerceAtLeast(0.0) }
            preferencesRepository.updateSubscriptionTotal(subscriptionTotal)
            preferencesRepository.updatePrimaryGoal(profile.primaryGoal)
            preferencesRepository.updateDisplayName(profile.name)
            preferencesRepository.updateBirthday(profile.birthday)
            preferencesRepository.setJoinedDate(profile.joinedDate)
            
            // Generate starter budgets if none provided
            val shouldAutoGenerateBudgets = profile.monthlyIncome > 0.0
            if (shouldAutoGenerateBudgets) {
                val starterBudgets = com.example.sparely.domain.logic.OnboardingHelper.generateStarterBudgets(profile)
                starterBudgets.forEach { budget ->
                    savingsRepository.upsertBudget(budget)
                }
            }
            
            val resolvedVaultSetups = when {
                profile.smartVaults.isNotEmpty() -> profile.smartVaults
                profile.savingsAccounts.isNotEmpty() -> profile.savingsAccounts.map { it.toSmartVaultSetup(profile.monthlyIncome) }
                shouldAutoGenerateBudgets -> {
                    // Use OnboardingHelper to generate vaults
                    val generatedVaults = com.example.sparely.domain.logic.OnboardingHelper.generateStarterVaults(profile)
                    generatedVaults.map { vault ->
                        SmartVaultSetup(
                            name = vault.name,
                            targetAmount = vault.targetAmount,
                            currentBalance = vault.currentBalance,
                            targetDate = vault.targetDate,
                            priority = vault.priority,
                            type = vault.type,
                            interestRate = vault.interestRate,
                            allocationMode = vault.allocationMode,
                            manualAllocationPercent = vault.manualAllocationPercent,
                            savingTaxRateOverride = vault.savingTaxRateOverride
                        )
                    }
                }
                else -> SavingsAdvisor.recommendedVaults(profile)
            }
            val recommendedPercentages = SavingsAdvisor.recommendedPercentages(profile)
            preferencesRepository.updatePercentages(recommendedPercentages)
            profile.transferReminder?.let { reminder ->
                preferencesRepository.updateReminders(reminder.enabled, reminder.hourOfDay, reminder.frequencyDays)
            }
            val resolvedVaults = resolvedVaultSetups
                .map { setup -> setup.toSmartVault() }
                .mapIndexed { index, vault ->
                    vault.copy(name = vault.name.ifBlank { "Vault ${index + 1}" })
                }
            val resolvedVaultBalance = max(
                profile.vaultsBalance.coerceAtLeast(0.0),
                resolvedVaults.sumOf { it.currentBalance }
            )
            preferencesRepository.updateVaultsBalance(resolvedVaultBalance)
            savingsRepository.seedSmartVaults(resolvedVaults)
            preferencesRepository.setOnboardingCompleted(true)
        }
    }


    fun payCreditCardBill(paymentMethodId: Long, amount: Double, note: String?, deductFromMainAccount: Boolean) {
        viewModelScope.launch(dispatcher) {
            savingsRepository.recordCreditCardPayment(
                paymentMethodId = paymentMethodId,
                amount = amount,
                note = note,
                date = LocalDate.now(),
                deductFromMainAccount = deductFromMainAccount
            )
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch(dispatcher) {
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    fun markBudgetPromptAsOneOff(prompt: BudgetOverrunPrompt) {
        handledBudgetPrompts.add(promptKey(prompt))
        _uiState.update { state ->
            state.copy(budgetPrompts = state.budgetPrompts.filterNot { promptKey(it) == promptKey(prompt) })
        }
    }

    fun adjustBudgetFromPrompt(prompt: BudgetOverrunPrompt, newLimit: Double) {
        val sanitized = newLimit.coerceAtLeast(0.0)
        viewModelScope.launch(dispatcher) {
            val existing = _uiState.value.budgets.firstOrNull { it.category == prompt.category && it.yearMonth == prompt.month }
            val budget = existing?.copy(monthlyLimit = sanitized, isActive = true)
                ?: CategoryBudget(
                    category = prompt.category,
                    monthlyLimit = sanitized,
                    yearMonth = prompt.month,
                    isActive = true
                )
            savingsRepository.upsertBudget(budget)
            handledBudgetPrompts.add(promptKey(prompt))
            _uiState.update { state ->
                state.copy(budgetPrompts = state.budgetPrompts.filterNot { promptKey(it) == promptKey(prompt) })
            }
        }
    }

    fun applySuggestedBudgetFromPrompt(prompt: BudgetOverrunPrompt) {
        val suggested = prompt.suggestion?.suggestedLimit ?: return
        adjustBudgetFromPrompt(prompt, suggested)
    }



    fun addSavingsAccount(account: com.example.sparely.domain.model.SavingsAccount) {
        viewModelScope.launch {
            savingsRepository.upsertSavingsAccount(account)
        }
    }

    fun getAccountTransactions(accountId: Long): Flow<List<com.example.sparely.domain.model.SavingsAccountTransaction>> {
        return savingsRepository.observeSavingsAccountTransactions(accountId)
    }

    fun updateSavingsAccount(account: com.example.sparely.domain.model.SavingsAccount) {
        viewModelScope.launch {
            savingsRepository.upsertSavingsAccount(account)
        }
    }

    fun recordSavingsAccountInterest(accountId: Long, amount: Double) {
        viewModelScope.launch(dispatcher) {
            savingsRepository.recordInterestEarned(accountId, amount)
        }
    }

    fun archiveSavingsAccount(accountId: Long) {
        viewModelScope.launch {
            savingsRepository.archiveSavingsAccount(accountId)
        }
    }

    fun addAsset(asset: com.example.sparely.domain.model.Asset) {
        viewModelScope.launch {
            savingsRepository.upsertAsset(asset)
        }
    }

    fun updateAsset(asset: com.example.sparely.domain.model.Asset) {
        viewModelScope.launch {
            savingsRepository.upsertAsset(asset)
        }
    }

    fun deleteAsset(assetId: Long) {
        viewModelScope.launch {
            savingsRepository.deleteAsset(assetId)
        }
    }

    fun createAssetFromExpense(
        name: String,
        category: com.example.sparely.domain.model.AssetCategory,
        description: String?,
        assetPrice: Double,
        creatorExpenseId: Long
    ) {
        viewModelScope.launch {
            savingsRepository.createAssetFromExpense(
                name = name,
                category = category,
                description = description,
                assetPrice = assetPrice,
                creatorExpenseId = creatorExpenseId
            )
        }
    }

    fun linkCreatorExpenseToAsset(assetId: Long, expenseId: Long, updateAssetPrice: Boolean = true) {
        viewModelScope.launch {
            savingsRepository.linkCreatorExpenseToAsset(assetId, expenseId, updateAssetPrice)
        }
    }

    suspend fun updateAutoCreateAssetThreshold(threshold: Double) {
        preferencesRepository.updateAutoCreateAssetThreshold(threshold)
    }

    private fun Double.toCurrencyPrecision(): Double = round(this * 100) / 100.0
}

class SparelyViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SparelyViewModel::class.java)) {
            return SparelyViewModel(
                savingsRepository = container.savingsRepository,
                backupRepository = container.backupRepository,
                preferencesRepository = container.preferencesRepository,
                recommendationEngine = container.recommendationEngine,
                notificationScheduler = container.notificationScheduler,
                vaultAutoDepositScheduler = container.vaultAutoDepositScheduler,
                brandfetchRepository = container.brandfetchRepository,
                container = container
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class $modelClass")
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
