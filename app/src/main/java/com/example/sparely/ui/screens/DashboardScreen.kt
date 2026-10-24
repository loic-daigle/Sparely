package com.example.sparely.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.logic.CashflowEngine
import com.example.sparely.domain.logic.SmartInsightEngine
import com.example.sparely.domain.logic.SpendingPatternEngine
import com.example.sparely.domain.model.*
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.SingleLineText
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.components.SegmentGap
import com.example.sparely.ui.components.SegmentedListItem
import com.example.sparely.ui.components.segmentedShape
import androidx.compose.ui.graphics.Shape
import com.example.sparely.ui.components.TotalUsableMoneyBottomSheet
import com.example.sparely.ui.state.SparelyUiState
import com.example.sparely.ui.theme.SparelyBrandSurface
import com.example.sparely.ui.theme.ExpressiveMotionTokens
import com.example.sparely.ui.theme.ExpressiveShapes
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.criticalContainer
import com.example.sparely.ui.theme.spacing
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.warning
import com.example.sparely.ui.theme.warningContainer
import com.example.sparely.ui.utils.displayName
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.sparely.app.R
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Days ahead within which an upcoming bill is surfaced in "Needs attention". */
private const val BILL_ATTENTION_WINDOW_DAYS = 3
private const val QUICK_ACTIONS_KEY = "quick_actions"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: SparelyUiState,
    pendingVaultContributions: List<VaultContribution> = emptyList(),
    onAddExpense: () -> Unit,
    onRepeatLastExpense: (com.example.sparely.domain.model.Expense) -> Unit = {},
    onRecordIncome: () -> Unit = {},
    onNavigateToHistory: () -> Unit,
    onNavigateToBudgets: () -> Unit = {},
    onNavigateToChallenges: () -> Unit = {},
    onNavigateToHealth: () -> Unit = {},
    onNavigateToPurchaseCheck: () -> Unit = {},
    onNavigateToRecurring: () -> Unit = {},
    onManageVaults: () -> Unit = {},
    onNavigateToVaultTransfers: () -> Unit = {},
    onNavigateToMainAccount: () -> Unit = {},
    onManageSavingsAccounts: () -> Unit = {},
    savingsAccounts: List<SavingsAccount> = emptyList(),
    onNavigateToCreditCards: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onManageAssets: () -> Unit = {},
    onOpenVault: (Long) -> Unit = {},
    onConvertRecurringInsight: (DetectedRecurringTransaction) -> Unit = {},
    onTransferIdleToSavings: (amount: Double) -> Unit = {},
    pendingVariableBills: List<com.example.sparely.domain.model.PendingVariableBill> = emptyList(),
    onConfirmVariableBill: (recurringExpenseId: Long, amount: Double) -> Unit = { _, _ -> },
    showBackupReminder: Boolean = false,
    onBackupNow: () -> Unit = {},
    onSnoozeBackupReminder: () -> Unit = {},
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

    // Hide the FAB while the quick actions row (which already offers "Log") is on screen
    val isQuickActionsVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == QUICK_ACTIONS_KEY }
        }
    }

    if (showTotalUsableMoneySheet) {
        TotalUsableMoneyBottomSheet(
            onDismissRequest = { showTotalUsableMoneySheet = false },
            sheetState = sheetState,
            mainBalance = uiState.settings.mainAccountBalance,
            minMainBalance = uiState.settings.minMainAccountBalance,
            wishlistReserved = uiState.wishlistReserved,
            pendingTransfers = uiState.pendingTransfersTotal,
            billsDue = uiState.billsDueTotal,
            creditCardOwed = uiState.creditCardOwed,
            netUsable = uiState.netUsableMoney,
            hisaAccounts = uiState.savingsAccounts.filter { !it.archived },
            onNavigateToMainAccount = onNavigateToMainAccount,
            onNavigateToAccount = { _ -> onManageSavingsAccounts() } // Fallback to list for now
        )
    }

    val lastExpense = uiState.expenses.firstOrNull()
    val activeVaults = uiState.smartVaults.filter { !it.archived }
    val creditCards = uiState.paymentMethods.filter { it.isCreditCard }
    val netUsable = uiState.netUsableMoney
    val showUsable = netUsable > 0 || uiState.settings.mainAccountBalance != 0.0 || savingsAccounts.isNotEmpty()
    val attentionItems = buildAttentionItems(
        uiState = uiState,
        pendingVaultContributions = pendingVaultContributions,
        onNavigateToVaultTransfers = onNavigateToVaultTransfers,
        onNavigateToBudgets = onNavigateToBudgets,
        onNavigateToRecurring = onNavigateToRecurring
    )
    val lowBalanceWarning = uiState.cashflowForecast?.lowBalanceWarning
    val idleMoneyInsight = uiState.idleMoneyInsight
    val spendingPatterns = uiState.spendingPatterns
    val hasInsights = lowBalanceWarning != null || idleMoneyInsight != null ||
        (spendingPatterns != null && (spendingPatterns.anomalies.isNotEmpty() || spendingPatterns.trend != SpendingPatternEngine.SpendingTrend.STABLE))
    val aiReady = com.example.sparely.ui.components.rememberOnDeviceAiReady()
    val aiLanguage = com.example.sparely.ui.components.LocalOnDeviceAi.current.language
    val aiSymbol = uiState.settings.regionalSettings.getCurrencySymbol()
    val aiBriefFacts = remember(uiState, aiLanguage, aiSymbol) {
        com.example.sparely.ai.FinanceFacts.brief(uiState, aiLanguage) { aiLanguage.money(it, aiSymbol) }
    }
    val today = LocalDate.now()
    val activeRecurringInsights = uiState.detectedRecurringTransactions.filter { insight ->
        val daysSinceLast = ChronoUnit.DAYS.between(insight.lastOccurrence, today).toInt()
        daysSinceLast <= insight.cadenceDays + 7
    }

    // Removed local TopAppBar - using global SparelyTopBar instead
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
                start = spacing.md,
                end = spacing.md,
                top = spacing.xs,
                // Leave room so the last card can scroll clear of the floating action button
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            // ── At a glance ─────────────────────────────────────────────
            item(key = "greeting") {
                DashboardGreeting()
            }

            item(key = "hero") {
                HomeHero(
                    usable = netUsable,
                    days = uiState.cashflowForecast?.dailyProjections.orEmpty(),
                    totalSaved = uiState.totalVaultBalance,
                    savingsRate = uiState.smartSavingSummary?.actualSavingsRate?.takeIf { uiState.settings.monthlyIncome > 0 },
                    lowBalanceThreshold = 0.0,
                    onBreakdown = { showTotalUsableMoneySheet = true },
                    onVaults = onManageVaults
                )
            }

            // The page's main actions as one connected group (docs/DESIGN.md §2)
            item(key = QUICK_ACTIONS_KEY) {
                com.example.sparely.ui.components.SparelyButtonGroup(
                    listOf(
                        com.example.sparely.ui.components.ButtonGroupItem(stringResource(R.string.home_action_expense), MaterialSymbols.REMOVE, emphasized = true, onClick = onAddExpense),
                        com.example.sparely.ui.components.ButtonGroupItem(stringResource(R.string.home_action_income), MaterialSymbols.ADD, onClick = onRecordIncome),
                        com.example.sparely.ui.components.ButtonGroupItem(
                            stringResource(R.string.dashboard_repeat_action),
                            MaterialSymbols.REFRESH,
                            enabled = lastExpense != null,
                            onClick = { lastExpense?.let(onRepeatLastExpense) }
                        )
                    )
                )
            }

            // ── Variable bills waiting for their real amount ──────────────
            if (pendingVariableBills.isNotEmpty()) {
                item(key = "pending_variable_bills") {
                    com.example.sparely.ui.components.PendingVariableBillsCard(
                        bills = pendingVariableBills,
                        currencySymbol = uiState.settings.regionalSettings.getCurrencySymbol(),
                        onConfirm = onConfirmVariableBill
                    )
                }
            }

            // ── No recent backup: everything lives only on this phone ──
            if (showBackupReminder) {
                item(key = "backup_reminder") {
                    BackupReminderCard(onBackupNow = onBackupNow, onLater = onSnoozeBackupReminder)
                }
            }

            // ── Needs attention ─────────────────────────────────────────
            if (attentionItems.isNotEmpty()) {
                dashboardSection(key = "attention", titleRes = R.string.dashboard_needs_attention, icon = MaterialSymbols.WARNING, badge = attentionItems.size) {
                    AttentionGroup(items = attentionItems)
                }
            }

            // ── Insights: on-device AI's take on today, then each signal as a row ──
            if (hasInsights || aiReady) {
                dashboardSection(
                    key = "insights",
                    icon = MaterialSymbols.LIGHTBULB,
                    titleRes = R.string.dashboard_insights_title,
                    onAction = onNavigateToInsights,
                    actionRes = R.string.dashboard_view_all
                ) {
                    DashboardInsightsGroup(
                        aiFacts = aiBriefFacts,
                        aiReady = aiReady,
                        lowBalanceWarning = lowBalanceWarning,
                        idleMoneyInsight = idleMoneyInsight,
                        spendingPatterns = spendingPatterns,
                        onNavigateToInsights = onNavigateToInsights,
                        onTransferIdle = { amount -> onTransferIdleToSavings(amount) }
                    )
                }
            }

            // ── Accounts & goals ────────────────────────────────────────
            dashboardSection(key = "accounts", titleRes = R.string.dashboard_accounts_title, icon = MaterialSymbols.SAVINGS) {
                AccountsAndGoals(
                    savingsAccounts = savingsAccounts.filter { !it.archived },
                    vaults = activeVaults,
                    pendingByVault = pendingVaultContributions
                        .mapNotNull { c -> c.vaultId?.let { it to c.amount } }
                        .groupBy({ it.first }, { it.second })
                        .mapValues { (_, amounts) -> amounts.sum() },
                    assets = uiState.assets,
                    workingOnProjectId = uiState.settings.workingOnProjectId,
                    onManageSavingsAccounts = onManageSavingsAccounts,
                    onManageVaults = onManageVaults,
                    onOpenVault = onOpenVault,
                    onManageAssets = onManageAssets
                )
            }

            // ── This month: scores, then what's coming up, then card debt ──
            dashboardSection(key = "this_month", titleRes = R.string.dashboard_this_month, icon = MaterialSymbols.PIE_CHART) {
                ThisMonthGroup(
                    budgetSummary = uiState.budgetSummary,
                    healthScore = uiState.financialHealthScore,
                    wishlistCount = uiState.wishlists.size,
                    decisionCount = uiState.wishlists.count { it.needsDecision() },
                    onBudgets = onNavigateToBudgets,
                    onHealth = onNavigateToHealth,
                    onPurchaseCheck = onNavigateToPurchaseCheck
                )
            }

            dashboardSection(key = "upcoming_bills", titleRes = R.string.dashboard_up_next, icon = MaterialSymbols.CALENDAR_MONTH, onAction = onNavigateToRecurring) {
                UpcomingPayments(
                    items = uiState.upcomingRecurring,
                    recurring = uiState.recurringExpenses,
                    paymentMethods = uiState.paymentMethods,
                    onManageRecurring = onNavigateToRecurring
                )
            }

            if (creditCards.isNotEmpty()) {
                dashboardSection(
                    key = "credit_cards",
                    icon = MaterialSymbols.CREDIT_CARD,
                    titleRes = R.string.dashboard_credit_cards_title,
                    onAction = onNavigateToCreditCards,
                    actionRes = R.string.dashboard_manage_action
                ) {
                    CreditCardsGroup(cards = creditCards, onClick = onNavigateToCreditCards)
                }
            }

            // ── Savings progress ────────────────────────────────────────
            val summary = uiState.smartSavingSummary
            val emergencyGoal = uiState.emergencyFundGoal
            if (summary != null || emergencyGoal != null) {
                dashboardSection(key = "savings_progress", titleRes = R.string.dashboard_savings_progress, icon = MaterialSymbols.TRENDING_UP) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        emergencyGoal?.let { EmergencyFundProgress(goal = it) }
                        summary?.let {
                            SmartSavingProgress(summary = it, monthlyIncome = uiState.settings.monthlyIncome)
                        }
                    }
                }
            }

            if (activeRecurringInsights.isNotEmpty()) {
                dashboardSection(key = "recurring_patterns", titleRes = R.string.dashboard_recurring_patterns_found, icon = MaterialSymbols.AUTORENEW) {
                    RepeatingPayments(insights = activeRecurringInsights, onAdd = onConvertRecurringInsight)
                }
            }

            // ── Challenges ──────────────────────────────────────────────
            dashboardSection(
                key = "challenges",
                titleRes = R.string.dashboard_active_challenges_title,
                icon = MaterialSymbols.TROPHY,
                onAction = onNavigateToChallenges.takeIf { uiState.activeChallenges.isNotEmpty() },
                actionRes = R.string.dashboard_view_all
            ) {
                ChallengesGroup(challenges = uiState.activeChallenges, onOpen = onNavigateToChallenges)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout scaffolding
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Adds a titled section to the dashboard. The header and body are emitted as a single
 * item so the heading always stays attached to its content.
 */
private fun LazyListScope.dashboardSection(
    key: String,
    titleRes: Int,
    onAction: (() -> Unit)? = null,
    actionRes: Int = R.string.dashboard_view_all,
    @DrawableRes icon: Int? = null,
    badge: Int? = null,
    content: @Composable () -> Unit
) {
    item(key = key) {
        Column(
            modifier = Modifier.padding(top = MaterialTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
        ) {
            SectionHeader(title = stringResource(titleRes), actionLabel = stringResource(actionRes), onAction = onAction, icon = icon, badge = badge)
            content()
        }
    }
}

/** Same look as [com.example.sparely.ui.components.ExpressiveListHeader], plus a count badge and an action. */
@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: (() -> Unit)?, @DrawableRes icon: Int? = null, badge: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = 4.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                MaterialSymbolIcon(icon = it, contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        // How many things are waiting, in the same red as the alerts
        badge?.takeIf { it > 0 }?.let { count ->
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.criticalContainer) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.critical
                )
            }
        }
        if (onAction != null) {
            SparelyTextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}



// ─────────────────────────────────────────────────────────────────────────────
// At a glance
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardGreeting() {
    val greetingRes = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> R.string.dashboard_greeting_morning
            in 12..17 -> R.string.dashboard_greeting_afternoon
            else -> R.string.dashboard_greeting_evening
        }
    }
    val today = remember { LocalDate.now().format(com.example.sparely.ui.utils.LocalDates.weekdayDayMonth) }
    Column(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xxs)) {
        Text(
            text = today,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // The page's large title (docs/DESIGN.md §2): home draws its own instead of the shared bar
        Text(
            text = stringResource(greetingRes),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Needs attention
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun buildAttentionItems(
    uiState: SparelyUiState,
    pendingVaultContributions: List<VaultContribution>,
    onNavigateToVaultTransfers: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToRecurring: () -> Unit
): List<AttentionItem> {
    val items = mutableListOf<AttentionItem>()

    if (pendingVaultContributions.isNotEmpty()) {
        val count = pendingVaultContributions.size
        items += AttentionItem(
            icon = MaterialSymbols.SWAP_HORIZ,
            title = stringResource(R.string.dash_att_transfers_title),
            subtitle = pluralStringResource(R.plurals.dash_att_transfers_sub, count, count),
            amount = pendingVaultContributions.sumOf { it.amount }.formatCurrency(decimals = 0),
            tone = AttentionTone.INFO,
            onClick = onNavigateToVaultTransfers
        )
    }

    uiState.budgetSummary?.let { budget ->
        val over = budget.categoryStatuses.filter { it.isOverBudget }
        if (over.isNotEmpty()) {
            items += AttentionItem(
                icon = MaterialSymbols.PIE_CHART,
                title = stringResource(R.string.dash_att_over_budget_title),
                subtitle = over.joinToString(" · ") { it.category.displayName() },
                amount = "+" + over.sumOf { it.spent - it.limit }.formatCurrency(decimals = 0),
                tone = AttentionTone.CRITICAL,
                onClick = onNavigateToBudgets
            )
        }
    }

    uiState.upcomingRecurring
        .filter { it.daysUntilDue in 0..BILL_ATTENTION_WINDOW_DAYS }
        .sortedBy { it.daysUntilDue }
        .take(2)
        .forEach { bill ->
            val recurring = bill.recurringExpense
            val paidWith = uiState.paymentMethods.find { it.id == recurring.paymentMethodId }?.name
                ?: if (recurring.deductFromMainAccount) stringResource(R.string.dash_main_account) else null
            val whenText = when (bill.daysUntilDue) {
                0 -> stringResource(R.string.dash_when_today)
                1 -> stringResource(R.string.dash_when_tomorrow)
                else -> stringResource(R.string.dash_when_in_days, bill.daysUntilDue)
            }
            val amount = (bill.predictedAmount ?: recurring.amount).formatCurrency()
            items += AttentionItem(
                icon = MaterialSymbols.CALENDAR_MONTH,
                title = recurring.description,
                subtitle = listOfNotNull(whenText, paidWith).joinToString(" · "),
                amount = if (recurring.isVariableAmount) "≈ $amount" else amount,
                tone = if (bill.daysUntilDue == 0) AttentionTone.WARNING else AttentionTone.INFO,
                onClick = onNavigateToRecurring
            )
        }

    return items
}

// ─────────────────────────────────────────────────────────────────────────────
// This month
// ─────────────────────────────────────────────────────────────────────────────


/**
 * The dashboard's insights, as one segmented group like the rest of the app: on-device AI's take
 * on today first (when it's on), then a compact row for each signal the app found. Every row is
 * visible at once; tapping one opens Insights, and idle money can be moved from its row.
 */
@Composable
private fun DashboardInsightsGroup(
    aiFacts: List<com.example.sparely.ai.Fact>,
    aiReady: Boolean,
    lowBalanceWarning: CashflowEngine.LowBalanceWarning?,
    idleMoneyInsight: SmartInsightEngine.IdleMoneyInsight?,
    spendingPatterns: SpendingPatternEngine.SpendingPatternResult?,
    onNavigateToInsights: () -> Unit,
    onTransferIdle: (Double) -> Unit
) {
    val showTrend = spendingPatterns != null &&
        (spendingPatterns.anomalies.isNotEmpty() || spendingPatterns.trend != SpendingPatternEngine.SpendingTrend.STABLE)
    val rows = buildList<@Composable (Shape) -> Unit> {
        if (aiReady && aiFacts.isNotEmpty()) {
            add { shape ->
                val language = com.example.sparely.ui.components.LocalOnDeviceAi.current.language
                com.example.sparely.ui.components.OnDeviceAiBriefSegment(
                    title = stringResource(R.string.dashboard_insight_ai_title),
                    facts = aiFacts,
                    task = com.example.sparely.ai.AiTask.BRIEF.text(language),
                    slot = "dashboard",
                    shape = shape,
                    onClick = onNavigateToInsights
                )
            }
        }
        if (lowBalanceWarning != null) {
            add { shape ->
                SegmentedListItem(
                    title = stringResource(R.string.dashboard_insight_low_balance),
                    subtitle = stringResource(
                        R.string.dashboard_insight_low_balance_detail,
                        formatCurrency(lowBalanceWarning.projectedLowBalance),
                        lowBalanceWarning.daysUntilLowBalance
                    ),
                    shape = shape,
                    icon = MaterialSymbols.WARNING,
                    iconTint = MaterialTheme.colorScheme.critical,
                    onClick = onNavigateToInsights,
                    trailing = { RowChevron() }
                )
            }
        }
        if (idleMoneyInsight != null && idleMoneyInsight.suggestedTransferAmount >= 1) {
            add { shape ->
                val amount = formatCurrency(idleMoneyInsight.suggestedTransferAmount)
                SegmentedListItem(
                    title = stringResource(R.string.dashboard_money_sitting_idle),
                    subtitle = idleMoneyInsight.suggestedVault?.name
                        ?.let { stringResource(R.string.dashboard_insight_idle_to_vault, amount, it) }
                        ?: stringResource(R.string.dashboard_insight_idle_to_savings, amount),
                    shape = shape,
                    icon = MaterialSymbols.SAVINGS,
                    iconTint = MaterialTheme.colorScheme.success,
                    trailing = {
                        SparelyTonalButton(onClick = { onTransferIdle(idleMoneyInsight.suggestedTransferAmount) }) {
                            Text(stringResource(R.string.dashboard_insight_idle_move))
                        }
                    }
                )
            }
        }
        if (showTrend) {
            add { shape ->
                val patterns = spendingPatterns!!
                val up = patterns.trend == SpendingPatternEngine.SpendingTrend.INCREASING
                val down = patterns.trend == SpendingPatternEngine.SpendingTrend.DECREASING
                val parts = buildList {
                    when {
                        up -> add(stringResource(R.string.dashboard_insight_trend_up, kotlin.math.abs(patterns.trendPercentage)))
                        down -> add(stringResource(R.string.dashboard_insight_trend_down, kotlin.math.abs(patterns.trendPercentage)))
                    }
                    if (patterns.predictedMonthEndSpending > 0) {
                        add(stringResource(R.string.dashboard_insight_month_end, formatCurrency(patterns.predictedMonthEndSpending)))
                    }
                    if (patterns.anomalies.isNotEmpty()) {
                        add(pluralStringResource(R.plurals.dashboard_insight_unusual, patterns.anomalies.size, patterns.anomalies.size))
                    }
                }
                SegmentedListItem(
                    title = stringResource(R.string.dashboard_insight_trend),
                    subtitle = parts.joinToString(" · "),
                    shape = shape,
                    icon = if (down) MaterialSymbols.TRENDING_DOWN else MaterialSymbols.TRENDING_UP,
                    iconTint = when {
                        up -> MaterialTheme.colorScheme.critical
                        down -> MaterialTheme.colorScheme.success
                        else -> MaterialTheme.colorScheme.primary
                    },
                    onClick = onNavigateToInsights,
                    trailing = { RowChevron() }
                )
            }
        }
    }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        rows.forEachIndexed { index, row -> row(segmentedShape(index, rows.size)) }
    }
}

@Composable
private fun RowChevron() {
    MaterialSymbolIcon(
        icon = MaterialSymbols.ARROW_FORWARD,
        contentDescription = null,
        size = 18.dp,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}


// ─────────────────────────────────────────────────────────────────────────────
// Patterns & challenges
// ─────────────────────────────────────────────────────────────────────────────


/** Nudge to export a backup (or turn on automatic ones) when none is recent. */
@Composable
private fun BackupReminderCard(onBackupNow: () -> Unit, onLater: () -> Unit) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.HISTORY,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(R.string.backup_reminder_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = stringResource(R.string.backup_reminder_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                SparelyTextButton(onClick = onLater) { Text(stringResource(R.string.backup_reminder_later)) }
                SparelyButton(onClick = onBackupNow) { Text(stringResource(R.string.backup_reminder_action)) }
            }
        }
    }
}
