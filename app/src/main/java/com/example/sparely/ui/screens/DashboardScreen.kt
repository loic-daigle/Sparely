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
                DashboardHeroCard(
                    totalBalance = uiState.totalVaultBalance,
                    monthlyIncome = uiState.settings.monthlyIncome,
                    actualSavingsRate = uiState.smartSavingSummary?.actualSavingsRate ?: 0.0,
                    usableBalance = netUsable.takeIf { showUsable },
                    forecast = uiState.cashflowForecast,
                    onUsableClick = { showTotalUsableMoneySheet = true }
                )
            }

            item(key = QUICK_ACTIONS_KEY) {
                QuickActionsRow(
                    lastExpense = lastExpense,
                    onLogExpense = onAddExpense,
                    onRepeatLast = { lastExpense?.let(onRepeatLastExpense) },
                    onManageVaults = onManageVaults,
                    onViewInsights = onNavigateToInsights
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
                dashboardSection(key = "attention", titleRes = R.string.dashboard_needs_attention, badge = attentionItems.size) {
                    AttentionGroup(items = attentionItems)
                }
            }

            // ── Insights: on-device AI's take on today, then each signal as a row ──
            if (hasInsights || aiReady) {
                dashboardSection(
                    key = "insights",
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
            dashboardSection(key = "accounts", titleRes = R.string.dashboard_accounts_title) {
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
            dashboardSection(key = "this_month", titleRes = R.string.dashboard_this_month) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
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

            dashboardSection(key = "upcoming_bills", titleRes = R.string.dashboard_up_next, onAction = onNavigateToRecurring) {
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
                    titleRes = R.string.dashboard_credit_cards_title,
                    onAction = onNavigateToCreditCards,
                    actionRes = R.string.dashboard_manage_action
                ) {
                    CreditCardsGroup(cards = creditCards, onClick = onNavigateToCreditCards)
                }
            }

            item(key = "purchase_check") {
                PurchaseCheckCard(
                    wishlistCount = uiState.wishlists.size,
                    decisionCount = uiState.wishlists.count { it.needsDecision() },
                    onClick = onNavigateToPurchaseCheck
                )
            }

            // ── Savings progress ────────────────────────────────────────
            val summary = uiState.smartSavingSummary
            val emergencyGoal = uiState.emergencyFundGoal
            if (summary != null || emergencyGoal != null) {
                dashboardSection(key = "savings_progress", titleRes = R.string.dashboard_savings_progress) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        emergencyGoal?.let { EmergencyFundProgress(goal = it) }
                        summary?.let {
                            SmartSavingProgress(summary = it, monthlyIncome = uiState.settings.monthlyIncome)
                        }
                    }
                }
            }

            if (activeRecurringInsights.isNotEmpty()) {
                dashboardSection(key = "recurring_patterns", titleRes = R.string.dashboard_recurring_patterns_found) {
                    RepeatingPayments(insights = activeRecurringInsights, onAdd = onConvertRecurringInsight)
                }
            }

            // ── Challenges ──────────────────────────────────────────────
            if (uiState.activeChallenges.isEmpty()) {
                item(key = "challenges_empty") {
                    ChallengesEmptyCard(onAddChallenge = onNavigateToChallenges)
                }
            } else {
                dashboardSection(
                    key = "challenges",
                    titleRes = R.string.dashboard_active_challenges_title,
                    onAction = onNavigateToChallenges,
                    actionRes = R.string.dashboard_view_all
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        for (challenge in uiState.activeChallenges) {
                            QuickChallengeItem(challenge = challenge, onClick = onNavigateToChallenges)
                        }
                    }
                }
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
    badge: Int? = null,
    content: @Composable () -> Unit
) {
    item(key = key) {
        Column(
            modifier = Modifier.padding(top = MaterialTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
        ) {
            SectionHeader(title = stringResource(titleRes), actionLabel = stringResource(actionRes), onAction = onAction, badge = badge)
            content()
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: (() -> Unit)?, badge: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(start = MaterialTheme.spacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
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

/** Card header: tinted icon badge, title, optional subtitle and trailing slot. */
@Composable
private fun CardHeader(
    @DrawableRes icon: Int,
    title: String,
    iconContainerColor: Color,
    iconTint: Color,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
    ) {
        IconBadge(icon = icon, containerColor = iconContainerColor, tint = iconTint)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun IconBadge(
    @DrawableRes icon: Int,
    containerColor: Color,
    tint: Color,
    size: Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(ExpressiveShapes.extraSmall)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        MaterialSymbolIcon(icon = icon, contentDescription = null, tint = tint, size = size * 0.55f)
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
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")) }
    Column(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.xxs)) {
        Text(
            text = today,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(greetingRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DashboardHeroCard(
    totalBalance: Double,
    monthlyIncome: Double,
    actualSavingsRate: Double,
    usableBalance: Double?,
    forecast: CashflowEngine.CashflowForecast?,
    onUsableClick: () -> Unit
) {
    // The dashboard's signature card: always Sparely spruce and mint, whatever the app theme.
    SparelyBrandSurface {
        val spacing = MaterialTheme.spacing
        var forecastExpanded by rememberSaveable { mutableStateOf(false) }
        val onContainer = MaterialTheme.colorScheme.onPrimaryContainer

        ExpressiveCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = onContainer,
            contentPadding = spacing.lg
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_total_saved),
                            style = MaterialTheme.typography.titleMedium,
                            color = onContainer.copy(alpha = 0.75f),
                            fontWeight = FontWeight.Medium
                        )
                        if (monthlyIncome > 0) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    MaterialSymbolIcon(
                                        icon = MaterialSymbols.TRENDING_UP,
                                        contentDescription = null,
                                        size = 16.dp
                                    )
                                    Text(
                                        text = stringResource(R.string.dashboard_saving_rate, actualSavingsRate.formatPercent()),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    SingleLineText(
                        text = totalBalance.formatCurrency(),
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                        // onPrimaryContainer keeps full contrast on the tinted card in every theme
                        color = onContainer,
                        modifier = Modifier.fillMaxWidth(),
                        minFontSize = 24f
                    )
                }

                if (usableBalance != null || forecast != null) {
                    // Every cell always renders a caption line so the cells stay the same height without
                    // intrinsic measurement (SingleLineText is subcomposed and doesn't support it).
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        if (usableBalance != null) {
                            HeroStat(
                                icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET,
                                label = stringResource(R.string.dashboard_usable_chip),
                                value = usableBalance.formatCurrency(),
                                caption = stringResource(R.string.dashboard_see_breakdown),
                                trailingIcon = MaterialSymbols.INFO,
                                onClickLabel = stringResource(R.string.dashboard_total_usable_money_title),
                                onClick = onUsableClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (forecast != null) {
                            val hasWarning = forecast.lowBalanceWarning != null || forecast.safeToSpend < 0
                            HeroStat(
                                icon = if (hasWarning) MaterialSymbols.WARNING else MaterialSymbols.SECURITY,
                                label = stringResource(R.string.dashboard_safe_to_spend),
                                value = forecast.safeToSpend.formatCurrency(),
                                caption = if (forecast.runwayDays < Int.MAX_VALUE) {
                                    stringResource(R.string.dashboard_runway_days, forecast.runwayDays)
                                } else {
                                    stringResource(R.string.dashboard_runway_healthy)
                                },
                                valueColor = if (hasWarning) MaterialTheme.colorScheme.critical else null,
                                trailingIcon = if (forecastExpanded) MaterialSymbols.ARROW_DROP_UP else MaterialSymbols.ARROW_DROP_DOWN,
                                onClickLabel = stringResource(
                                    if (forecastExpanded) R.string.dashboard_collapse else R.string.dashboard_expand
                                ),
                                onClick = { forecastExpanded = !forecastExpanded },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (forecast != null) {
                    AnimatedVisibility(
                        visible = forecastExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        CashflowForecastList(forecast.weeklyProjections)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(
    @DrawableRes icon: Int,
    label: String,
    value: String,
    caption: String,
    @DrawableRes trailingIcon: Int,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color? = null
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MaterialSymbolIcon(
                    icon = icon,
                    contentDescription = null,
                    size = 16.dp,
                    tint = valueColor ?: MaterialTheme.colorScheme.primary
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                MaterialSymbolIcon(
                    icon = trailingIcon,
                    contentDescription = onClickLabel,
                    size = 16.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SingleLineText(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = valueColor ?: contentColor,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CashflowForecastList(weeklyProjections: List<CashflowEngine.WeeklyProjection>) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d") }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.dashboard_forecast_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            if (weeklyProjections.isEmpty()) {
                Text(
                    text = stringResource(R.string.dashboard_forecast_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                weeklyProjections.take(4).forEach { projection ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_week_of, projection.weekStartDate.format(formatter)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = projection.projectedEndBalance.formatCurrency(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (projection.projectedEndBalance < 0) {
                                MaterialTheme.colorScheme.critical
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    lastExpense: Expense?,
    onLogExpense: () -> Unit,
    onRepeatLast: () -> Unit,
    onManageVaults: () -> Unit,
    onViewInsights: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickAction(
            icon = MaterialSymbols.ADD,
            label = stringResource(R.string.dashboard_log_action),
            onClick = onLogExpense,
            emphasized = true,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            icon = MaterialSymbols.REFRESH,
            label = stringResource(R.string.dashboard_repeat_action),
            onClick = onRepeatLast,
            enabled = lastExpense != null,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            icon = MaterialSymbols.SAVINGS,
            label = stringResource(R.string.dashboard_vaults_action),
            onClick = onManageVaults,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            icon = MaterialSymbols.LIGHTBULB,
            label = stringResource(R.string.dashboard_insights_title),
            onClick = onViewInsights,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = when {
        !enabled -> colors.surfaceContainerHigh
        emphasized -> colors.primary
        else -> colors.secondaryContainer
    }
    val contentColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        emphasized -> colors.onPrimary
        else -> colors.onSecondaryContainer
    }
    // Round icon button with its label underneath; the whole column is the touch target
    Column(
        modifier = modifier
            .clip(ExpressiveShapes.medium)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(if (emphasized) ExpressiveShapes.large else CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            MaterialSymbolIcon(icon = icon, contentDescription = null, size = 24.dp, tint = contentColor)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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


@Composable
private fun PurchaseCheckCard(wishlistCount: Int, decisionCount: Int, onClick: () -> Unit) {
    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        CardHeader(
            icon = MaterialSymbols.SHOPPING_BAG,
            title = stringResource(R.string.purchase_check_card_title),
            subtitle = if (decisionCount > 0) {
                stringResource(R.string.purchase_check_card_subtitle_decisions, decisionCount)
            } else if (wishlistCount > 0) {
                stringResource(R.string.purchase_check_card_subtitle_wishlist, wishlistCount)
            } else {
                stringResource(R.string.purchase_check_card_subtitle)
            },
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            trailing = {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.ARROW_FORWARD,
                    contentDescription = null,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
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
        modifier = Modifier.fillMaxWidth().height(156.dp)
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
        modifier = Modifier.fillMaxWidth().height(156.dp)
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

        }
    }
}

@Composable
private fun BudgetEmptyCard(onClick: () -> Unit) {
    ExpressiveCard(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().height(156.dp)
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
    val streakColor = MaterialTheme.colorScheme.warning
    ExpressiveCard(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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


@Composable
private fun ChallengesEmptyCard(onAddChallenge: () -> Unit) {
    ExpressiveCard(
        onClick = onAddChallenge,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        CardHeader(
            icon = MaterialSymbols.TROPHY,
            title = stringResource(R.string.dashboard_join_challenge),
            subtitle = stringResource(R.string.dashboard_challenge_competitive_desc),
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
            trailing = {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.ARROW_FORWARD,
                    contentDescription = stringResource(R.string.dashboard_browse_challenges),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 20.dp
                )
            }
        )
    }
}

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
