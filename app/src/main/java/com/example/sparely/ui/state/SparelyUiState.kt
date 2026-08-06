package com.example.sparely.ui.state

import com.example.sparely.domain.logic.BudgetEngine
import com.example.sparely.domain.logic.CashflowEngine
import com.example.sparely.domain.logic.SmartInsightEngine
import com.example.sparely.domain.logic.SpendingPatternEngine
import com.example.sparely.domain.model.*

data class SparelyUiState(
    val settings: SparelySettings = SparelySettings(),
    val expenses: List<Expense> = emptyList(),
    val analytics: AnalyticsSnapshot = AnalyticsSnapshot(),
    val recommendation: RecommendationResult? = null,
    val manualTransfers: List<SavingsTransfer> = emptyList(),
    val savingsPlan: SavingsPlan? = null,
    val smartSavingSummary: SmartSavingSummary? = null,
    val alerts: List<AlertMessage> = emptyList(),
    val smartVaults: List<SmartVault> = emptyList(),
    val savingsAccounts: List<com.example.sparely.domain.model.SavingsAccount> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val totalVaultBalance: Double = 0.0,

    val emergencyFundGoal: EmergencyFundGoal? = null,
    val onboardingCompleted: Boolean = false,
    val activeSaveRate: Double = 0.0,
    val activeSavingTaxRate: Double = 0.0,
    val automationRationale: List<String> = emptyList(),
    
    // New features
    val budgets: List<CategoryBudget> = emptyList(),
    val budgetSummary: BudgetSummary? = null,
    val budgetSuggestions: List<BudgetSuggestion> = emptyList(),
    val budgetPrompts: List<BudgetOverrunPrompt> = emptyList(),
    val budgetForecasts: List<BudgetEngine.BudgetForecast> = emptyList(),
    val preemptiveWarnings: List<BudgetEngine.PreemptiveBudgetWarning> = emptyList(),
    val recurringExpenses: List<RecurringExpense> = emptyList(),
    val upcomingRecurring: List<UpcomingRecurringExpense> = emptyList(),
    val recurringPaidRecords: List<com.example.sparely.data.local.RecurringExpensePaidEntity> = emptyList(),
    val activeChallenges: List<SavingsChallenge> = emptyList(),
    val achievements: List<Achievement> = emptyList(),
    val financialHealthScore: FinancialHealthScore? = null,
    val detectedRecurringTransactions: List<DetectedRecurringTransaction> = emptyList(),
    val vaultProjections: List<VaultProjection> = emptyList(),
    val upcomingVaultDeposits: List<VaultContribution> = emptyList(),

    val autoDepositCheckHour: Int = 9,
    val mainAccountTransactions: List<MainAccountTransaction> = emptyList(),
    val stores: List<Store> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val creditCardPayments: Map<Long, List<CreditCardPayment>> = emptyMap(),
    val vaultArchivePrompt: VaultArchivePrompt? = null,
    val lastDeletedExpense: Expense? = null,
    
    // Cashflow and spending insights
    val cashflowForecast: CashflowEngine.CashflowForecast? = null,
    val spendingPatterns: SpendingPatternEngine.SpendingPatternResult? = null,
    
    // Smart Insight Engine data
    val recurringPatterns: List<SmartInsightEngine.RecurringPatternInsight> = emptyList(),
    val seasonalInsights: List<SmartInsightEngine.SeasonalInsight> = emptyList(),
    val idleMoneyInsight: SmartInsightEngine.IdleMoneyInsight? = null,
    val uniqueExpenses: List<SmartInsightEngine.UniqueExpenseInsight> = emptyList(),
    val pendingDetectedRecurring: DetectedRecurringTransaction? = null,
    
    // For repeat last expense feature
    val prefillExpense: Expense? = null,
    val prefillAssetAllocations: Map<Long, Double> = emptyMap(),

    // Infinite Scroll & Store History
    val pagedExpenses: List<Expense> = emptyList(),
    val canLoadMoreExpenses: Boolean = true,
    val isLoadingMoreExpenses: Boolean = false,
    val selectedStore: Store? = null,
    val selectedStoreHistory: List<Expense> = emptyList(),
    val isStoreHistoryLoading: Boolean = false,

    val totalHisaBalance: Double = 0.0,
    val totalUsableMoney: Double = 0.0,
    
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

