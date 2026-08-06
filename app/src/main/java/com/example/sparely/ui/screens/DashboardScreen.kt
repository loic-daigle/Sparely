package com.example.sparely.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import com.example.sparely.ui.theme.ExpressiveShapes
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import com.example.sparely.domain.logic.SmartInsightEngine
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyOutlinedButton
import com.sparely.app.R
import com.example.sparely.domain.model.*
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.SavingsTrendCard
import com.example.sparely.ui.components.SingleLineText
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyOutlinedButton
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.TotalUsableMoneyBottomSheet
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.state.SparelyUiState
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.ExpressiveMotionTokens
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.spacing
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.warning
import com.example.sparely.domain.logic.CashflowEngine
import com.example.sparely.domain.logic.SpendingPatternEngine
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: SparelyUiState,
    pendingVaultContributions: List<VaultContribution> = emptyList(),
    onAddExpense: () -> Unit,
    onRepeatLastExpense: (com.example.sparely.domain.model.Expense) -> Unit = {},
    onNavigateToHistory: () -> Unit,
    onNavigateToBudgets: () -> Unit = {},
    onNavigateToChallenges: () -> Unit = {},
    onNavigateToHealth: () -> Unit = {},
    onNavigateToRecurring: () -> Unit = {},
    onManageVaults: () -> Unit = {},
    onNavigateToVaultTransfers: () -> Unit = {},
    onNavigateToMainAccount: () -> Unit = {},
    onManageSavingsAccounts: () -> Unit = {},
    savingsAccounts: List<SavingsAccount> = emptyList(),
    onNavigateToCreditCards: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onManageAssets: () -> Unit = {},
    onConvertRecurringInsight: (DetectedRecurringTransaction) -> Unit = {},
    onTransferIdleToSavings: (amount: Double) -> Unit = {},
    // allow parent to hide dashboard's own FAB when a global FAB/menu is provided
    showFloatingFab: Boolean = true
) {
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    val spacing = MaterialTheme.spacing
    
    var showTotalUsableMoneySheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val listState = rememberLazyListState()
    
    // Hide FAB if QuickActionsRow (index 2) is clearly visible
    val isQuickActionsVisible by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            visibleItems.any { it.index == 2 } // QuickActionsRow is the 3rd item (index 2)
        }
    }

    if (showTotalUsableMoneySheet) {
        TotalUsableMoneyBottomSheet(
            onDismissRequest = { showTotalUsableMoneySheet = false },
            sheetState = sheetState,
            mainBalance = uiState.settings.mainAccountBalance,
            minMainBalance = uiState.settings.minMainAccountBalance,
            hisaAccounts = uiState.savingsAccounts.filter { !it.archived },
            onNavigateToMainAccount = onNavigateToMainAccount,
            onNavigateToAccount = { _ -> onManageSavingsAccounts() } // Fallback to list for now
        )
    }

    // Removed local TopAppBar - using global SparelyTopBar instead
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (showFloatingFab && !isQuickActionsVisible) {
                ExtendedFloatingActionButton(
                    text = { SingleLineText(stringResource(R.string.dashboard_log_purchase)) },
                    icon = {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.ADD,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = onAddExpense,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = spacing.lg,
                end = spacing.lg,
                top = spacing.sm,
                bottom = spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ════════════════════════════════════════════════════════════════
            // SECTION 1: HERO - Total Saved with savings rate
            // ════════════════════════════════════════════════════════════════
            item {
                DashboardHeroSection(
                    totalBalance = uiState.totalVaultBalance,
                    monthlyIncome = uiState.settings.monthlyIncome,
                    actualSavingsRate = uiState.smartSavingSummary?.actualSavingsRate ?: 0.0,
                    onAddExpense = onAddExpense,
                    onNavigateToHistory = onNavigateToHistory,
                    onManageVaults = onManageVaults
                )
            }

            // ════════════════════════════════════════════════════════════════
            // SECTION 2: QUICK METRICS ROW - Condensed key figures
            // ════════════════════════════════════════════════════════════════
            val creditCards = uiState.paymentMethods.filter { it.isCreditCard }
            val netUsable = (uiState.totalUsableMoney - uiState.settings.minMainAccountBalance).coerceAtLeast(0.0)
            val safeToSpend = uiState.cashflowForecast?.safeToSpend ?: 0.0
            val creditUtilization = if (creditCards.isNotEmpty()) {
                val totalBalance = creditCards.sumOf { it.currentBalance }
                val totalLimit = creditCards.sumOf { it.creditLimit ?: 0.0 }
                if (totalLimit > 0) totalBalance / totalLimit else 0.0
            } else null
            
            if (netUsable > 0 || safeToSpend > 0 || creditUtilization != null) {
                item {
                    QuickMetricsRow(
                        totalUsable = netUsable,
                        safeToSpend = safeToSpend,
                        creditUtilization = creditUtilization,
                        onUsableClick = { showTotalUsableMoneySheet = true },
                        onSafeToSpendClick = onNavigateToInsights,
                        onCreditClick = onNavigateToCreditCards
                    )
                }
            }

            // ════════════════════════════════════════════════════════════════
            // SECTION 3: QUICK ACTIONS ROW - Primary user actions
            // ════════════════════════════════════════════════════════════════
            item {
                QuickActionsRow(
                    lastExpense = uiState.expenses.firstOrNull(),
                    onLogExpense = onAddExpense,
                    onRepeatLast = { uiState.expenses.firstOrNull()?.let { onRepeatLastExpense(it) } },
                    onManageVaults = onManageVaults,
                    onViewHistory = onNavigateToHistory
                )
            }

            // ════════════════════════════════════════════════════════════════
            // SECTION 4: ACTIONABLE INSIGHTS - Important alerts & suggestions
            // ════════════════════════════════════════════════════════════════
            val lowBalanceWarning = uiState.cashflowForecast?.lowBalanceWarning
            val idleMoneyInsight = uiState.idleMoneyInsight
            val spendingPatterns = uiState.spendingPatterns
            
            if (lowBalanceWarning != null || idleMoneyInsight != null || 
                (spendingPatterns != null && (spendingPatterns.anomalies.isNotEmpty() || spendingPatterns.trend != SpendingPatternEngine.SpendingTrend.STABLE))) {
                item {
                    PrimaryInsightsCard(
                        safeToSpend = uiState.cashflowForecast?.safeToSpend ?: 0.0,
                        runwayDays = uiState.cashflowForecast?.runwayDays ?: 0,
                        lowBalanceWarning = lowBalanceWarning,
                        idleMoneyInsight = idleMoneyInsight,
                        spendingPatterns = spendingPatterns,
                        onNavigateToInsights = onNavigateToInsights,
                        onTransferIdle = { amount -> onTransferIdleToSavings(amount) }
                    )
                }
            }

            // ════════════════════════════════════════════════════════════════
            // SECTION 5: ACCOUNTS & GOALS - Unified Savings and Vaults
            // ════════════════════════════════════════════════════════════════
            item {
                UnifiedAccountsDashboardSection(
                    savingsAccounts = savingsAccounts,
                    onManageSavingsAccounts = onManageSavingsAccounts,
                    vaults = uiState.smartVaults,
                    totalVaultBalance = uiState.totalVaultBalance,
                    pendingContributions = pendingVaultContributions,
                    onManageVaults = onManageVaults,
                    onManageAssets = onManageAssets,
                    onNavigateToVaultTransfers = onNavigateToVaultTransfers
                )
            }

            // ════════════════════════════════════════════════════════════════
            // SECTION 6: HEALTH & BUDGET GRID - Financial status at a glance
            // ════════════════════════════════════════════════════════════════
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    uiState.financialHealthScore?.let { healthScore ->
                        Box(modifier = Modifier.weight(1f)) {
                            QuickHealthScoreCard(healthScore, onNavigateToHealth)
                        }
                    }

                    val budgetSummary = uiState.budgetSummary
                    Box(modifier = Modifier.weight(1f)) {
                        if (budgetSummary != null) {
                            QuickBudgetCard(budgetSummary, onNavigateToBudgets)
                        } else {
                            BudgetEmptyCard(onNavigateToBudgets)
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════════
            // SECTION 7: SAVINGS STATUS - Emergency Fund & Smart Saving
            // ════════════════════════════════════════════════════════════════
            uiState.emergencyFundGoal?.let { goal ->
                item {
                    EmergencyFundCard(goal = goal, settings = uiState.settings)
                }
            }

            uiState.smartSavingSummary?.let { summary ->
                item {
                    SmartSavingSnapshotCard(
                        summary = summary,
                        monthlyIncome = uiState.settings.monthlyIncome
                    )
                }
            }

            // ════════════════════════════════════════════════════════════════
            // SECTION 8: CREDIT CARDS - Full details (if present)
            // ════════════════════════════════════════════════════════════════
            if (creditCards.isNotEmpty()) {
                item {
                    CreditCardSummaryCard(
                        creditCards = creditCards,
                        onClick = onNavigateToCreditCards
                    )
                }
            }

            // ════════════════════════════════════════════════════════════════
            // SECTION 9: UPCOMING & RECURRING
            // ════════════════════════════════════════════════════════════════
            item {
                UpcomingRecurringCard(
                    items = uiState.upcomingRecurring,
                    hasRecurring = uiState.recurringExpenses.isNotEmpty(),
                    onManageRecurring = onNavigateToRecurring
                )
            }

            val today = LocalDate.now()
            val activeRecurringInsights = uiState.detectedRecurringTransactions.filter { insight ->
                val daysSinceLast = ChronoUnit.DAYS.between(insight.lastOccurrence, today).toInt()
                val maxGrace = insight.cadenceDays + 7
                daysSinceLast <= maxGrace
            }

            if (activeRecurringInsights.isNotEmpty()) {
                item {
                    RecurringInsightsCard(
                        insights = activeRecurringInsights,
                        onConvert = onConvertRecurringInsight
                    )
                }
            }

            // ════════════════════════════════════════════════════════════════
            // SECTION 10: CHALLENGES
            // ════════════════════════════════════════════════════════════════
            item {
                if (uiState.activeChallenges.isEmpty()) {
                    ChallengesEmptyCard(onAddChallenge = onNavigateToChallenges)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                        Text(
                           stringResource(R.string.dashboard_active_challenges_title), 
                           style = MaterialTheme.typography.titleMedium,
                           fontWeight = FontWeight.Bold
                        )
                        for (challenge in uiState.activeChallenges) {
                            QuickChallengeItem(challenge = challenge, onClick = onNavigateToChallenges)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)


@Composable
private fun DashboardHeroSection(
    totalBalance: Double,
    monthlyIncome: Double,
    actualSavingsRate: Double,
    onAddExpense: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onManageVaults: () -> Unit
) {
    val spacing = MaterialTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        ExpressiveCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = spacing.lg)
            ) {
                Text(
                    text = stringResource(R.string.dashboard_total_saved),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = totalBalance.formatCurrency(),
                    style = MaterialTheme.typography.displayLarge.copy(
                         fontWeight = FontWeight.Bold,
                         letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center
                )

                if (monthlyIncome > 0) {
                    val displayRate = (actualSavingsRate * 100)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.TRENDING_UP,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.dashboard_saving_rate, actualSavingsRate.formatPercent()),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            SparelyTonalButton(
                onClick = onManageVaults,
                modifier = Modifier.weight(1f),
                icon = {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.ACCOUNT_BALANCE,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            ) {
                SingleLineText(stringResource(R.string.dashboard_manage))
            }
            SparelyTextButton(
                onClick = onNavigateToHistory,
                modifier = Modifier.weight(1f),
                icon = {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.HISTORY,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            ) {
                SingleLineText(stringResource(R.string.dashboard_history))
            }
        }
    }
}

@Composable
private fun UnifiedAccountsDashboardSection(
    savingsAccounts: List<SavingsAccount>,
    onManageSavingsAccounts: () -> Unit,
    vaults: List<SmartVault>,
    totalVaultBalance: Double,
    pendingContributions: List<VaultContribution>,
    onManageVaults: () -> Unit,
    onManageAssets: () -> Unit,
    onNavigateToVaultTransfers: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val (selectedTab, setSelectedTab) = remember { mutableIntStateOf(0) }
    val tabs = listOf(
        R.string.dashboard_tab_savings,
        R.string.dashboard_tab_vaults,
        R.string.assets_tab
    )
    
    val pendingVaultCount = pendingContributions.mapNotNull { it.vaultId }.distinct().size

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        // Tab Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.weight(1f),
                containerColor = Color.Transparent,
                divider = {},
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[selectedTab])
                                .height(3.dp)
                                .padding(horizontal = 12.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                )
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, titleResId ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { setSelectedTab(index) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(titleResId),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (index == 1 && pendingVaultCount > 0) {
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.error, CircleShape)
                                    )
                                }
                            }
                        }
                    )
                }
            }

            SparelyTextButton(
                onClick = when (selectedTab) {
                    0 -> onManageSavingsAccounts
                    1 -> onManageVaults
                    else -> onManageAssets
                }
            ) {
                Text(stringResource(R.string.dashboard_manage_action))
            }
        }

        // Content based on tab
        when (selectedTab) {
            0 -> {
                // Savings Content (Inspired by DashboardSavingsSection)
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    if (savingsAccounts.isEmpty()) {
                        EmptyAccountsPlaceholder(
                            title = stringResource(R.string.dashboard_start_saving_title),
                            subtitle = stringResource(R.string.dashboard_start_saving_subtitle),
                            onClick = onManageSavingsAccounts
                        )
                    } else {
                        savingsAccounts.forEach { account ->
            AccountItem(
                                name = account.name,
                                balance = account.currentBalance,
                                subtitle = stringResource(R.string.dashboard_apy_suffix, formatApy(account.annualPercentageYield)),
                                onClick = onManageSavingsAccounts
                            )
                        }
                    }
                }
            }
            1 -> {
                // Vaults Content (Inspired by DashboardVaultsSection)
                val accentColor = MaterialTheme.colorScheme.tertiary
                val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
                
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(vaults.take(5)) { vault ->
                            Box(modifier = Modifier.width(320.dp).height(145.dp)) {
                                VaultItem(vault = vault, accentColor = accentColor, dateFormatter = dateFormatter)
                            }
                        }
                        
                        if (vaults.size > 5) {
                            item {
                                MoreVaultsItem(onClick = onManageVaults)
                            }
                        }
                    }
                    
                    if (pendingVaultCount > 0) {
                        SparelyButton(
                            onClick = onNavigateToVaultTransfers,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            val label = if (pendingVaultCount == 1) {
                                stringResource(R.string.dashboard_pending_vault_action, pendingVaultCount)
                            } else {
                                stringResource(R.string.dashboard_pending_vault_actions, pendingVaultCount)
                            }
                            SingleLineText(label)
                        }
                    }
                }
            }
            2 -> {
                // Assets Content (Inspired by Settings/Vaults)
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(
                        text = "Manage your physical and financial assets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    
                    Surface(
                        shape = ExpressiveShapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        onClick = onManageAssets,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                MaterialSymbolIcon(
                                    icon = MaterialSymbols.SHOPPING_BAG,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.assets_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Tap to manage items and valuations",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.ARROW_FORWARD,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAccountsPlaceholder(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = ExpressiveShapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccountItem(
    name: String,
    balance: Double,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = ExpressiveShapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = balance.formatCurrency(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MoreVaultsItem(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(120.dp).height(145.dp),
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MaterialSymbolIcon(
                icon = MaterialSymbols.ARROW_FORWARD,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.dashboard_view_all),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun VaultItem(
    vault: SmartVault,
    accentColor: Color,
    dateFormatter: DateTimeFormatter
) {
    val spacing = MaterialTheme.spacing
    val progress = if (vault.targetAmount <= 0) 0f
                  else (vault.currentBalance / vault.targetAmount).toFloat().coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(
            durationMillis = ExpressiveMotionTokens.EmphasizedDurationMillis,
            easing = ExpressiveMotionTokens.EmphasizedEasing
        ),
        label = "progress"
    )

    val urgencyColor = when (vault.priority) {
        VaultPriority.CRITICAL -> MaterialTheme.colorScheme.error
        VaultPriority.HIGH -> accentColor
        VaultPriority.MEDIUM -> MaterialTheme.colorScheme.primary
        VaultPriority.LOW -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(52.dp),
                        color = urgencyColor,
                        strokeWidth = 5.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Column {
                        Text(
                            vault.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val targetText = buildString {
                            append(stringResource(R.string.dashboard_goal_prefix))
                            append(vault.targetAmount.formatCurrency())
                            vault.targetDate?.let {
                                append(stringResource(R.string.dashboard_separator) + " ${it.format(dateFormatter)}")
                            }
                        }
                        Text(
                            text = targetText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = vault.currentBalance.formatCurrency(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = urgencyColor
                    )
                    Surface(
                        shape = ExpressiveShapes.extraSmall,
                        color = urgencyColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = vault.type.displayName(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = urgencyColor
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))

            vault.nextExpectedContribution?.takeIf { it > 0 }?.let { nextAmount ->
                Spacer(modifier = Modifier.height(spacing.xs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xxs)
                ) {
                    MaterialSymbolIcon(icon = MaterialSymbols.TRENDING_UP,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = urgencyColor
                    )
                    Text(
                        text = stringResource(R.string.dashboard_next_contribution, nextAmount.formatCurrency()),
                        style = MaterialTheme.typography.labelMedium,
                        color = urgencyColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartSavingSnapshotCard(summary: SmartSavingSummary, monthlyIncome: Double) {
    val spacing = MaterialTheme.spacing
    val isOnTrack = summary.actualSavingsRate >= summary.targetSavingsRate
    val statusColor = if (isOnTrack) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.critical
    
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(ExpressiveShapes.extraSmall)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.SAVINGS,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.dashboard_smart_saving),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (summary.allocationMode) {
                                SmartAllocationMode.MANUAL -> stringResource(R.string.dashboard_allocation_manual_mode)
                                SmartAllocationMode.GUIDED -> stringResource(R.string.dashboard_allocation_guided_mode)
                                SmartAllocationMode.AUTOMATIC -> stringResource(R.string.dashboard_allocation_automatic_mode)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Savings Rate Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_savings_rate_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = summary.actualSavingsRate.formatPercent(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.dashboard_target_with_amount_label, summary.targetSavingsRate.formatPercent()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MaterialSymbolIcon(
                            icon = if (isOnTrack) MaterialSymbols.CHECK_CIRCLE else MaterialSymbols.WARNING,
                            contentDescription = null,
                            size = 16.dp,
                            tint = statusColor
                        )
                        Text(
                            text = if (isOnTrack) stringResource(R.string.dashboard_on_track) else stringResource(R.string.dashboard_below_target),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            // Monthly target hint
            if (monthlyIncome > 0.0) {
                val monthlyTarget = monthlyIncome * summary.targetSavingsRate
                Text(
                    text = stringResource(R.string.dashboard_aim_for_target, monthlyTarget.formatCurrency()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AllocationChip(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecurringInsightsCard(
    insights: List<DetectedRecurringTransaction>,
    onConvert: (DetectedRecurringTransaction) -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val formatter = DateTimeFormatter.ofPattern("MMM d")
    val previewInsights = insights.take(3)

    ExpressiveCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
        contentPadding = 16.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.AUTORENEW,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            size = 18.dp
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.dashboard_recurring_patterns_found),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (insight in previewInsights) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpressiveShapes.extraSmall,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        tonalElevation = 0.dp,
                        onClick = { onConvert(insight) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = insight.description,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(
                                        R.string.dashboard_recurring_pattern_detail,
                                        insight.cadenceDays,
                                        insight.lastOccurrence.plusDays(insight.cadenceDays.toLong()).format(formatter)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = insight.averageAmount.formatCurrency(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                MaterialSymbolIcon(
                                    icon = MaterialSymbols.ARROW_FORWARD,
                                    contentDescription = null,
                                    size = 18.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            
            Text(
                text = stringResource(R.string.dashboard_convert_subscriptions_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun UpcomingRecurringCard(
    items: List<UpcomingRecurringExpense>,
    hasRecurring: Boolean,
    onManageRecurring: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("MMM d")
    val spacing = MaterialTheme.spacing
    val totalUpcoming = items.sumOf { it.recurringExpense.amount }
    
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onManageRecurring,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = ExpressiveShapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.CALENDAR_MONTH,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                size = 22.dp
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.dashboard_up_next),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (items.isNotEmpty()) {
                                pluralStringResource(
                                    R.plurals.dashboard_payments_due_count,
                                    items.size,
                                    items.size
                                )
                            } else {
                                stringResource(R.string.dashboard_no_upcoming_bills)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                SparelyTextButton(onClick = onManageRecurring) {
                    Text(stringResource(R.string.dashboard_manage_action))
                }
            }

            if (items.isNotEmpty()) {
                // Focus on Total
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = ExpressiveShapes.extraSmall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_total_commitment),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = totalUpcoming.formatCurrency(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // List
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (upcoming in items.take(3)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = upcoming.recurringExpense.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.dashboard_due_date, upcoming.dueDate.format(formatter)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = upcoming.recurringExpense.amount.formatCurrency(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    if (items.size > 3) {
                         Text(
                            text = pluralStringResource(
                                R.plurals.dashboard_more_items_count,
                                items.size - 3,
                                items.size - 3
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            } else {
                Text(
                    text = if (hasRecurring) {
                        stringResource(R.string.dashboard_all_bills_paid)
                    } else {
                        stringResource(R.string.dashboard_add_subscriptions_prompt)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EmergencyFundCard(goal: EmergencyFundGoal, settings: SparelySettings) {
    val spacing = MaterialTheme.spacing
    val coverage = goal.coverageRatio.coerceIn(0.0, 1.0)
    val animatedCoverage by animateFloatAsState(
        targetValue = coverage.toFloat(),
        animationSpec = tween(
            durationMillis = ExpressiveMotionTokens.SlowDurationMillis,
            easing = ExpressiveMotionTokens.EmphasizedEasing
        ),
        label = "coverage"
    )
    val savedAmount = (goal.targetAmount - goal.shortfallAmount).coerceAtLeast(0.0)
    val shortfall = goal.shortfallAmount.coerceAtLeast(0.0)
    val statusColor = if (coverage >= 1.0) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.primary

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(ExpressiveShapes.extraSmall)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.SECURITY,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.dashboard_emergency_runway),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.dashboard_month_goal_count,
                                goal.targetMonths.toInt(),
                                goal.targetMonths.toInt()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Balance and Progress
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.dashboard_current_cushion),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = savedAmount.formatCurrency(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.dashboard_target_amount_label, goal.targetAmount.formatCurrency()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format("%.0f%%", coverage * 100),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { animatedCoverage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round
                )

                // Shortfall or success message
                if (shortfall > 0.0) {
                    Text(
                        text = stringResource(R.string.dashboard_remaining_to_go_amount, shortfall.formatCurrency()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.CHECK_CIRCLE,
                            contentDescription = null,
                            size = 16.dp,
                            tint = MaterialTheme.colorScheme.success
                        )
                        Text(
                            text = stringResource(R.string.dashboard_goal_reached),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.success,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

@Composable
private fun MetricsRow(uiState: SparelyUiState) {
    val spacing = MaterialTheme.spacing
    val totalsByType = uiState.smartVaults
        .groupBy { it.type }
        .mapValues { (_, vaults) -> vaults.sumOf { it.currentBalance } }

    val shortTermTotal = totalsByType[VaultType.SHORT_TERM] ?: 0.0
    val longTermTotal = totalsByType[VaultType.LONG_TERM] ?: 0.0
    val passiveTotal = totalsByType[VaultType.PASSIVE_INVESTMENT] ?: 0.0

    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Surface(
                shape = ExpressiveShapes.extraSmall,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.SAVINGS,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = stringResource(R.string.dashboard_savings_breakdown_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                ModernMetricCard(
                    title = stringResource(R.string.dashboard_short_term),
                    value = shortTermTotal,
                    icon = MaterialSymbols.SAVINGS,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                ModernMetricCard(
                    title = stringResource(R.string.dashboard_long_term),
                    value = longTermTotal,
                    icon = MaterialSymbols.ACCOUNT_BALANCE,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                ModernMetricCard(
                    title = stringResource(R.string.dashboard_passive_growth),
                    value = passiveTotal,
                    icon = MaterialSymbols.TRENDING_UP,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                ModernMetricCard(
                    title = stringResource(R.string.dashboard_monthly_avg),
                    value = uiState.analytics.averageMonthlyReserve,
                    subtitle = stringResource(R.string.dashboard_projected_in_6_months, formatCurrency(uiState.analytics.projectedReserveSixMonths)),
                    icon = MaterialSymbols.TRENDING_UP,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ModernMetricCard(
    title: String,
    value: Double,
    @androidx.annotation.DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val spacing = MaterialTheme.spacing
    ExpressiveCard(
        modifier = modifier,
        shape = ExpressiveShapes.small,
        tonalElevation = 4.dp,
        contentPadding = spacing.md
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MaterialSymbolIcon(
                    icon = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                    size = 20.dp
                )
            }
            Text(
                text = formatCurrency(value),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(recommendation: RecommendationResult) {
    ExpressiveCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentPadding = 16.dp
    ) {
        Column {
            Text(
                text = stringResource(R.string.dashboard_suggested_allocations),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            AllocationRow(label = stringResource(R.string.settings_category_emergency), value = recommendation.recommendedPercentages.emergency)
            AllocationRow(label = stringResource(R.string.settings_category_invest), value = recommendation.recommendedPercentages.invest)
            AllocationRow(label = stringResource(R.string.settings_category_fun), value = recommendation.recommendedPercentages.`fun`)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dashboard_investments_split, formatPercent(recommendation.safeInvestmentRatio), formatPercent(recommendation.highRiskInvestmentRatio)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = recommendation.rationale,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun AllocationRow(label: String, value: Double) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(formatPercent(value))
    }
}

@Composable
private fun AlertsSection(alerts: List<AlertMessage>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.dashboard_insights_title),
            style = MaterialTheme.typography.titleMedium
        )
        for (alert in alerts) {
            AssistChip(
                onClick = {},
                label = { Text(alert.title) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
            Text(
                text = alert.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        }
    }
}

@Composable
private fun GoalsSnapshot(uiState: SparelyUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.dashboard_vaults_progress_title),
            style = MaterialTheme.typography.titleMedium
        )
        val mainVaults = uiState.smartVaults.filter { !it.archived }.take(3)
        for (vault in mainVaults) {
            ExpressiveCard(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                tonalElevation = 1.dp,
                contentPadding = 16.dp
            ) {
                Column {
                    Text(vault.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dashboard_vault_progress_text, formatCurrency(vault.currentBalance), formatCurrency(vault.targetAmount)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ProgressBar(progress = vault.progressPercent)
                    vault.targetDate?.let { date ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.dashboard_target_date_label, date.format(DateTimeFormatter.ISO_DATE)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Double) {
    val clamped = progress.coerceIn(0.0, 1.0).toFloat()
    LinearProgressIndicator(
    progress = { clamped },
    modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
    color = MaterialTheme.colorScheme.primary,
    trackColor = MaterialTheme.colorScheme.surfaceVariant,
    strokeCap = StrokeCap.Round,
    )
}

@Composable
private fun QuickHealthScoreCard(healthScore: FinancialHealthScore, onClick: () -> Unit) {
    val healthColor = when (healthScore.healthLevel) {
        HealthLevel.EXCELLENT -> MaterialTheme.colorScheme.success
        HealthLevel.GOOD -> MaterialTheme.colorScheme.primary
        HealthLevel.FAIR -> MaterialTheme.colorScheme.warning
        HealthLevel.NEEDS_WORK -> MaterialTheme.colorScheme.critical
        HealthLevel.CRITICAL -> MaterialTheme.colorScheme.critical
    }
    
    ExpressiveCard(
        onClick = onClick,
        containerColor = healthColor.copy(alpha = 0.08f),
        contentPadding = 0.dp,
        modifier = Modifier.fillMaxWidth().height(180.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_financial_health_title),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = healthColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = stringResource(when(healthScore.healthLevel) {
                            HealthLevel.EXCELLENT -> R.string.health_level_excellent
                            HealthLevel.GOOD -> R.string.health_level_good
                            HealthLevel.FAIR -> R.string.health_level_fair
                            HealthLevel.NEEDS_WORK -> R.string.health_level_needs_work
                            HealthLevel.CRITICAL -> R.string.health_level_critical
                        }),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = healthColor
                    )
                }
                
                Surface(
                    shape = CircleShape,
                    color = healthColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.HEALTH_AND_SAFETY,
                            contentDescription = null,
                            tint = healthColor,
                            size = 18.dp
                        )
                    }
                }
            }
            
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { healthScore.overallScore.toFloat() / 100f },
                    modifier = Modifier.size(76.dp),
                    color = healthColor,
                    strokeWidth = 8.dp,
                    trackColor = healthColor.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round
                )
                Text(
                    text = "${healthScore.overallScore}",
                    style = MaterialTheme.typography.headlineSmall,
                     fontWeight = FontWeight.Black,
                    color = healthColor
                )
            }
            
            Text(
                text = stringResource(R.string.dashboard_tap_for_analysis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun QuickBudgetCard(budgetSummary: BudgetSummary, onClick: () -> Unit) {
    val statusColor = when (budgetSummary.overallHealth) {
        BudgetHealthStatus.HEALTHY -> MaterialTheme.colorScheme.success
        BudgetHealthStatus.WARNING -> MaterialTheme.colorScheme.warning
        BudgetHealthStatus.CRITICAL -> MaterialTheme.colorScheme.critical
        BudgetHealthStatus.OVER_BUDGET -> MaterialTheme.colorScheme.critical
    }
    
    ExpressiveCard(
        onClick = onClick,
        containerColor = statusColor.copy(alpha = 0.08f),
        contentPadding = 0.dp,
        modifier = Modifier.fillMaxWidth().height(180.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_budget_usage_title),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = stringResource(R.string.dashboard_amount_left, formatCurrency(budgetSummary.totalRemaining)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor
                    )
                }
                
                Surface(
                    shape = CircleShape,
                    color = statusColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.ACCOUNT_BALANCE,
                            contentDescription = null,
                            tint = statusColor,
                            size = 18.dp
                        )
                    }
                }
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = formatPercent(budgetSummary.percentageUsed),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = statusColor
                    )
                    Text(
                         text = stringResource(R.string.dashboard_of_total_amount, formatCurrency(budgetSummary.totalBudget)),
                         style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                LinearProgressIndicator(
                    progress = { budgetSummary.percentageUsed.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round,
                )
            }
            
            Text(
                text = stringResource(R.string.dashboard_tap_for_details),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun BudgetEmptyCard(onClick: () -> Unit) {
    ExpressiveCard(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().height(180.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                 Box(contentAlignment = Alignment.Center) {
                     MaterialSymbolIcon(
                        icon = MaterialSymbols.PIE_CHART,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 24.dp
                    )
                 }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.dashboard_no_budgets_set),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.dashboard_plan_spending_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickChallengeItem(challenge: SavingsChallenge, onClick: () -> Unit) {
    val streakColor = Color(0xFFFF5722) // More vibrant fire color
    ExpressiveCard(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = 16.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = challenge.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (challenge.targetAmount > 0) {
                            pluralStringResource(
                                R.plurals.dashboard_challenge_progress_complete,
                                (challenge.progressPercent * 100).toInt(),
                                formatPercent(challenge.progressPercent)
                            )
                        } else {
                            stringResource(R.string.dashboard_ongoing_challenge)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (challenge.streakDays > 0) {
                    Surface(
                        color = streakColor.copy(alpha = 0.1f),
                        shape = CircleShape,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                             MaterialSymbolIcon(
                                icon = MaterialSymbols.LOCAL_FIRE_DEPARTMENT,
                                contentDescription = null,
                                tint = streakColor,
                                size = 16.dp
                            )
                            Text(
                                text = challenge.streakDays.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = streakColor
                            )
                        }
                    }
                }
            }
            
            if (challenge.targetAmount > 0) {
                LinearProgressIndicator(
                    progress = { challenge.progressPercent.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun ChallengesEmptyCard(onAddChallenge: () -> Unit) {
    ExpressiveCard(
        onClick = onAddChallenge,
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = 16.dp
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.TROPHY,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 28.dp
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.dashboard_join_challenge),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.dashboard_challenge_competitive_desc),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            SparelyOutlinedButton(
                onClick = onAddChallenge
            ) {
                Text(stringResource(R.string.dashboard_browse_challenges))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TotalUsableMoneyCard(totalUsable: Double, onClick: () -> Unit = {}) {
    ExpressiveCard(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentPadding = 20.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.dashboard_total_usable_money_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.INFO,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                        size = 16.dp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatCurrency(totalUsable),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            MaterialSymbolIcon(
                icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                size = 32.dp
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// QUICK METRICS ROW - Compact horizontal view of key financial figures
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun QuickMetricsRow(
    totalUsable: Double,
    safeToSpend: Double,
    creditUtilization: Double?,
    onUsableClick: () -> Unit,
    onSafeToSpendClick: () -> Unit,
    onCreditClick: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        // Total Usable Chip
        MetricChip(
            label = stringResource(R.string.dashboard_usable_chip),
            value = formatCurrency(totalUsable),
            icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onUsableClick
        )
        
        // Safe to Spend Chip
        val safeColor = if (safeToSpend >= 0) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
        val safeContentColor = if (safeToSpend >= 0) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        }
        MetricChip(
            label = stringResource(R.string.dashboard_safe_chip),
            value = formatCurrency(safeToSpend),
            icon = MaterialSymbols.SECURITY,
            containerColor = safeColor,
            contentColor = safeContentColor,
            onClick = onSafeToSpendClick
        )
        
        // Credit Utilization Chip (if applicable)
        creditUtilization?.let { util ->
            val utilColor = when {
                util < 0.3 -> MaterialTheme.colorScheme.success
                util < 0.5 -> MaterialTheme.colorScheme.warning
                else -> MaterialTheme.colorScheme.critical
            }
            MetricChip(
                label = stringResource(R.string.dashboard_credit_chip),
                value = formatPercent(util),
                icon = MaterialSymbols.CREDIT_CARD,
                containerColor = utilColor.copy(alpha = 0.15f),
                contentColor = utilColor,
                onClick = onCreditClick
            )
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String,
    @androidx.annotation.DrawableRes icon: Int,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp), // Full pill for expressive feel
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MaterialSymbolIcon(
                icon = icon,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.8f),
                size = 20.dp
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
// QUICK ACTIONS ROW - Primary action buttons for common tasks
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun QuickActionsRow(
    lastExpense: com.example.sparely.domain.model.Expense?,
    onLogExpense: () -> Unit,
    onRepeatLast: () -> Unit,
    onManageVaults: () -> Unit,
    onViewHistory: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        // Primary: Log Expense
        SparelyTonalButton(
            onClick = onLogExpense,
            modifier = Modifier.weight(1f),
            icon = {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.ADD,
                    contentDescription = null,
                    size = 18.dp
                )
            }
        ) {
            Text(text = stringResource(R.string.dashboard_log_action), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        
        // Secondary: Repeat Last (only if there's a recent expense)
        if (lastExpense != null) {
            SparelyOutlinedButton(
                onClick = onRepeatLast,
                modifier = Modifier.weight(1f),
                icon = {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.REFRESH,
                        contentDescription = null,
                        size = 18.dp
                    )
                }
            ) {
                Text(text = stringResource(R.string.dashboard_repeat_action), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        
        // Tertiary: Manage Vaults
        SparelyOutlinedButton(
            onClick = onManageVaults,
            modifier = Modifier.weight(1f),
            icon = {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.SAVINGS,
                    contentDescription = null,
                    size = 18.dp
                )
            }
        ) {
            Text(text = stringResource(R.string.dashboard_vaults_action), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class BudgetStatusColors(
    val containerColor: Color,
    val indicatorColor: Color,
    val contentColor: Color
)

private fun formatMonths(months: Double): String =
    if (months % 1.0 == 0.0) months.toInt().toString() else months.formatCurrency("", 1)

@Composable
private fun PrimaryInsightsCard(
    safeToSpend: Double,
    runwayDays: Int,
    lowBalanceWarning: CashflowEngine.LowBalanceWarning?,
    idleMoneyInsight: SmartInsightEngine.IdleMoneyInsight?,
    spendingPatterns: SpendingPatternEngine.SpendingPatternResult?,
    onNavigateToInsights: () -> Unit,
    onTransferIdle: (Double) -> Unit
) {
    val spacing = MaterialTheme.spacing
    
    // Determine which insights to show
    val availableInsights = remember(lowBalanceWarning, idleMoneyInsight, spendingPatterns) {
        buildList {
            if (lowBalanceWarning != null) add(InsightType.Warning)
            if (idleMoneyInsight != null) add(InsightType.IdleMoney)
            if (spendingPatterns != null && (spendingPatterns.anomalies.isNotEmpty() || spendingPatterns.trend != SpendingPatternEngine.SpendingTrend.STABLE)) {
                add(InsightType.Spending)
            }
        }
    }
    
    if (availableInsights.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { availableInsights.size })
    val coroutineScope = rememberCoroutineScope()
    
    ExpressiveCard(
        onClick = { }, // Inside content handles clicks
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = 0.dp,
        tonalElevation = 2.dp
    ) {
        Column {
            // Header / Tabs
            if (availableInsights.size > 1) {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {},
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            Box(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                                    .height(3.dp)
                                    .padding(horizontal = 12.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                    )
                            )
                        }
                    }
                ) {
                    availableInsights.forEachIndexed { index, type ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(
                                    text = stringResource(type.titleResId),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            } else {
                // Simple label if only one insight
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(availableInsights.first().titleResId),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.LIGHTBULB,
                        contentDescription = null,
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
            }
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                val insightType = availableInsights[pageIndex]
                Box(modifier = Modifier.padding(20.dp)) {
                    when (insightType) {
                        InsightType.Warning -> SafeToSpendInsightContent(
                            safeToSpend = safeToSpend,
                            runwayDays = runwayDays,
                            lowBalanceWarning = lowBalanceWarning!!,
                            onClick = onNavigateToInsights
                        )
                        InsightType.IdleMoney -> IdleMoneyInsightContent(
                            insight = idleMoneyInsight!!,
                            onTransfer = { onTransferIdle(idleMoneyInsight.suggestedTransferAmount) }
                        )
                        InsightType.Spending -> SpendingInsightsContent(
                            patterns = spendingPatterns!!,
                            onViewDetails = onNavigateToInsights
                        )
                    }
                }
            }
        }
    }
}

private sealed class InsightType(val titleResId: Int) {
    object Warning : InsightType(R.string.dashboard_low_balance_alert)
    object IdleMoney : InsightType(R.string.dashboard_money_sitting_idle)
    object Spending : InsightType(R.string.dashboard_spending_trend)
}

@Composable
private fun SafeToSpendInsightContent(
    safeToSpend: Double,
    runwayDays: Int,
    lowBalanceWarning: CashflowEngine.LowBalanceWarning,
    onClick: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val contentColor = MaterialTheme.colorScheme.onErrorContainer
    
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ExpressiveShapes.extraSmall)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.WARNING,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        size = 24.dp
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_low_balance_alert),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.dashboard_balance_drop_soon),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrency(safeToSpend),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (runwayDays < Int.MAX_VALUE) {
                    Text(
                        text = stringResource(R.string.dashboard_runway_days, runwayDays),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = spacing.xs)
        )
        
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            shape = ExpressiveShapes.extraSmall,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(
                    R.string.dashboard_balance_drop_detail,
                    formatCurrency(lowBalanceWarning.projectedLowBalance),
                    lowBalanceWarning.daysUntilLowBalance
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun SpendingInsightsContent(
    patterns: SpendingPatternEngine.SpendingPatternResult,
    onViewDetails: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val trend = patterns.trend
    val trendPercentage = patterns.trendPercentage
    val anomalies = patterns.anomalies
    val predictedMonthEnd = patterns.predictedMonthEndSpending
    
    val trendColor = when (trend) {
        SpendingPatternEngine.SpendingTrend.INCREASING -> MaterialTheme.colorScheme.error
        SpendingPatternEngine.SpendingTrend.DECREASING -> MaterialTheme.colorScheme.success
        SpendingPatternEngine.SpendingTrend.STABLE -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }
    
    val trendIcon = when (trend) {
        SpendingPatternEngine.SpendingTrend.INCREASING -> MaterialSymbols.TRENDING_UP
        SpendingPatternEngine.SpendingTrend.DECREASING -> MaterialSymbols.TRENDING_DOWN
        SpendingPatternEngine.SpendingTrend.STABLE -> MaterialSymbols.SWAP_HORIZ
        else -> MaterialSymbols.SWAP_HORIZ
    }
    
    val trendText = when (trend) {
        SpendingPatternEngine.SpendingTrend.INCREASING -> 
            stringResource(R.string.dashboard_trend_increasing, kotlin.math.abs(trendPercentage))
        SpendingPatternEngine.SpendingTrend.DECREASING -> 
            stringResource(R.string.dashboard_trend_decreasing, kotlin.math.abs(trendPercentage))
        SpendingPatternEngine.SpendingTrend.STABLE -> 
            stringResource(R.string.dashboard_trend_stable)
        else -> stringResource(R.string.dashboard_trend_stable)
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dashboard_spending_trend),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Trend Chip
            Surface(
                shape = CircleShape,
                color = trendColor.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MaterialSymbolIcon(
                        icon = trendIcon,
                        contentDescription = null,
                        size = 16.dp,
                        tint = trendColor
                    )
                    Text(
                        text = trendText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = trendColor
                    )
                }
            }
        }
        
        Text(
            text = stringResource(R.string.dashboard_predicted_month_end, formatCurrency(predictedMonthEnd)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (anomalies.isNotEmpty()) {
            Surface(
                shape = ExpressiveShapes.extraSmall,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.WARNING,
                            contentDescription = null,
                            size = 16.dp,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.dashboard_unusual_transactions_count,
                                anomalies.size,
                                anomalies.size
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    for (anomaly in anomalies.take(2)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = anomaly.expense.description,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatCurrency(anomaly.expense.amount),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        
        SparelyTextButton(
            onClick = onViewDetails,
            modifier = Modifier.align(Alignment.End),
            icon = null
        ) {
            Text(stringResource(R.string.dashboard_full_analysis))
            Spacer(Modifier.width(4.dp))
            MaterialSymbolIcon(
                icon = MaterialSymbols.ARROW_FORWARD,
                contentDescription = null,
                size = 16.dp
            )
        }
    }
}



@Composable
private fun RepeatLastExpenseCard(
    lastExpense: com.example.sparely.domain.model.Expense,
    onRepeat: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d") }
    
    ExpressiveCard(
        onClick = onRepeat,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentPadding = 16.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ExpressiveShapes.extraSmall)
                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.REFRESH,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        size = 24.dp
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_repeat_last),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.dashboard_repeat_last_detail, lastExpense.description, formatCurrency(lastExpense.amount)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Surface(
                shape = ExpressiveShapes.medium,
                color = MaterialTheme.colorScheme.secondary
            ) {
                Text(
                    text = lastExpense.date.format(dateFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}


@Composable
private fun CreditCardSummaryCard(
    creditCards: List<PaymentMethod>,
    onClick: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val totalBalance = creditCards.sumOf { it.currentBalance }
    val totalLimit = creditCards.sumOf { it.creditLimit ?: 0.0 }
    val overallUtilization = if (totalLimit > 0) totalBalance / totalLimit else 0.0
    
    val healthLevel = when {
        overallUtilization < 0.3 -> HealthLevel.GOOD
        overallUtilization < 0.5 -> HealthLevel.FAIR
        else -> HealthLevel.CRITICAL
    }
    
    val healthColor = when (healthLevel) {
        HealthLevel.GOOD -> MaterialTheme.colorScheme.success
        HealthLevel.FAIR -> MaterialTheme.colorScheme.warning
        else -> MaterialTheme.colorScheme.critical
    }

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(ExpressiveShapes.extraSmall)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.CREDIT_CARD,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.dashboard_credit_cards_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.dashboard_credit_cards_count,
                                creditCards.size,
                                creditCards.size
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Main Balance
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.dashboard_total_balance),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatCurrency(totalBalance),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Utilization Bar
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_credit_utilization),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatPercent(overallUtilization),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = healthColor
                    )
                }
                LinearProgressIndicator(
                    progress = { overallUtilization.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = healthColor,
                    trackColor = healthColor.copy(alpha = 0.2f),
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap
                )
                if (healthLevel != HealthLevel.GOOD) {
                    Text(
                        text = stringResource(R.string.dashboard_utilization_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = healthColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Card Breakdown
            if (creditCards.isNotEmpty()) {
                HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    creditCards.take(3).forEach { card ->
                        val cardUtil = if ((card.creditLimit ?: 0.0) > 0) card.currentBalance / card.creditLimit!! else 0.0
                        val cardColor = when {
                            cardUtil < 0.3 -> MaterialTheme.colorScheme.onSurface
                            cardUtil < 0.5 -> MaterialTheme.colorScheme.warning
                            else -> MaterialTheme.colorScheme.critical
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                com.example.sparely.ui.components.PaymentMethodIcon(
                                    method = card,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = card.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatCurrency(card.currentBalance),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cardColor
                                )
                            }
                        }
                    }
                    if (creditCards.size > 3) {
                         Text(
                            text = pluralStringResource(
                                R.plurals.dashboard_more_items_count,
                                creditCards.size - 3,
                                creditCards.size - 3
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun IdleMoneyInsightContent(
    insight: SmartInsightEngine.IdleMoneyInsight,
    onTransfer: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = ExpressiveShapes.extraSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.SAVINGS,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dashboard_money_sitting_idle),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.dashboard_put_idle_cash_to_work),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Main Suggestion Overlay
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ExpressiveShapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.dashboard_suggested_transfer),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCurrency(insight.suggestedTransferAmount),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                SparelyButton(
                    onClick = onTransfer
                ) {
                    Text(stringResource(R.string.dashboard_transfer_action))
                }
            }
        }
        
        // Growth Projection
        if (insight.growthProjections.isNotEmpty()) {
            Column {
                Text(
                    text = stringResource(R.string.dashboard_projected_growth),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for ((months, amount) in insight.growthProjections.toList().sortedBy { it.first }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when(months) {
                                    6 -> stringResource(R.string.dashboard_duration_months, 6)
                                    12 -> stringResource(R.string.dashboard_duration_years, 1)
                                    24 -> stringResource(R.string.dashboard_duration_years, 2)
                                    else -> stringResource(R.string.dashboard_duration_months, months)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formatCurrency(amount),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (insight.estimatedAnnualInterest > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f), ExpressiveShapes.extraSmall)
                    .padding(8.dp)
            ) {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.TRENDING_UP,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(
                        R.string.dashboard_est_annual_interest,
                        formatCurrency(insight.estimatedAnnualInterest)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

private fun formatApy(value: Double): String {
    return value.formatPercent(2)
}
