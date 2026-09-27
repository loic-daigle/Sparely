package com.example.sparely.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
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
import com.example.sparely.ui.components.TotalUsableMoneyBottomSheet
import com.example.sparely.ui.state.SparelyUiState
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
private const val MAX_DASHBOARD_VAULTS = 5
private const val MAX_DASHBOARD_BILLS = 3
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
            hisaAccounts = uiState.savingsAccounts.filter { !it.archived },
            onNavigateToMainAccount = onNavigateToMainAccount,
            onNavigateToAccount = { _ -> onManageSavingsAccounts() } // Fallback to list for now
        )
    }

    val lastExpense = uiState.expenses.firstOrNull()
    val activeVaults = uiState.smartVaults.filter { !it.archived }
    val creditCards = uiState.paymentMethods.filter { it.isCreditCard }
    val netUsable = (uiState.totalUsableMoney - uiState.settings.minMainAccountBalance).coerceAtLeast(0.0)
    val showUsable = netUsable > 0 || uiState.settings.mainAccountBalance != 0.0 || savingsAccounts.isNotEmpty()
    val pendingVaultCount = pendingVaultContributions.mapNotNull { it.vaultId }.distinct().size
    val attentionItems = buildAttentionItems(
        uiState = uiState,
        pendingVaultCount = pendingVaultCount,
        onNavigateToVaultTransfers = onNavigateToVaultTransfers,
        onNavigateToBudgets = onNavigateToBudgets,
        onNavigateToRecurring = onNavigateToRecurring
    )
    val lowBalanceWarning = uiState.cashflowForecast?.lowBalanceWarning
    val idleMoneyInsight = uiState.idleMoneyInsight
    val spendingPatterns = uiState.spendingPatterns
    val hasInsights = lowBalanceWarning != null || idleMoneyInsight != null ||
        (spendingPatterns != null && (spendingPatterns.anomalies.isNotEmpty() || spendingPatterns.trend != SpendingPatternEngine.SpendingTrend.STABLE))
    val today = LocalDate.now()
    val activeRecurringInsights = uiState.detectedRecurringTransactions.filter { insight ->
        val daysSinceLast = ChronoUnit.DAYS.between(insight.lastOccurrence, today).toInt()
        daysSinceLast <= insight.cadenceDays + 7
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

            // ── Needs attention ─────────────────────────────────────────
            if (attentionItems.isNotEmpty()) {
                dashboardSection(key = "attention", titleRes = R.string.dashboard_needs_attention) {
                    AttentionCard(items = attentionItems)
                }
            }

            // ── Insights (low balance, idle money, spending trend) ──────
            if (hasInsights) {
                dashboardSection(
                    key = "insights",
                    titleRes = R.string.dashboard_insights_title,
                    onAction = onNavigateToInsights,
                    actionRes = R.string.dashboard_view_all
                ) {
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

            // ── Accounts & goals ────────────────────────────────────────
            item(key = "accounts") {
                UnifiedAccountsDashboardSection(
                    savingsAccounts = savingsAccounts,
                    onManageSavingsAccounts = onManageSavingsAccounts,
                    vaults = activeVaults,
                    pendingVaultCount = pendingVaultCount,
                    onManageVaults = onManageVaults,
                    onManageAssets = onManageAssets
                )
            }

            // ── This month ──────────────────────────────────────────────
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

            item(key = "upcoming_bills") {
                UpcomingRecurringCard(
                    items = uiState.upcomingRecurring,
                    hasRecurring = uiState.recurringExpenses.isNotEmpty(),
                    onManageRecurring = onNavigateToRecurring
                )
            }

            if (creditCards.isNotEmpty()) {
                item(key = "credit_cards") {
                    CreditCardSummaryCard(
                        creditCards = creditCards,
                        onClick = onNavigateToCreditCards
                    )
                }
            }

            // ── Savings progress ────────────────────────────────────────
            val summary = uiState.smartSavingSummary
            val emergencyGoal = uiState.emergencyFundGoal
            if (summary != null || emergencyGoal != null) {
                dashboardSection(key = "savings_progress", titleRes = R.string.dashboard_savings_progress) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        summary?.let {
                            SmartSavingSnapshotCard(summary = it, monthlyIncome = uiState.settings.monthlyIncome)
                        }
                        emergencyGoal?.let { EmergencyFundCard(goal = it) }
                    }
                }
            }

            if (activeRecurringInsights.isNotEmpty()) {
                item(key = "recurring_patterns") {
                    RecurringInsightsCard(
                        insights = activeRecurringInsights,
                        onConvert = onConvertRecurringInsight
                    )
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
    content: @Composable () -> Unit
) {
    item(key = key) {
        Column(
            modifier = Modifier.padding(top = MaterialTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
        ) {
            SectionHeader(title = stringResource(titleRes), actionLabel = stringResource(actionRes), onAction = onAction)
            content()
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: (() -> Unit)?) {
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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )
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

@Composable
private fun RoundedProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val shown by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = ExpressiveMotionTokens.EmphasizedDurationMillis,
            easing = ExpressiveMotionTokens.EmphasizedEasing
        ),
        label = "progress"
    )
    LinearProgressIndicator(
        progress = { shown },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape),
        color = color,
        trackColor = color.copy(alpha = 0.18f),
        strokeCap = StrokeCap.Round
    )
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
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary,
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
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
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
    val containerColor = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 72.dp),
        shape = ExpressiveShapes.medium,
        color = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
            MaterialSymbolIcon(icon = icon, contentDescription = null, size = 22.dp)
            SingleLineText(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Needs attention
// ─────────────────────────────────────────────────────────────────────────────

private enum class AttentionTone { INFO, WARNING, CRITICAL }

private data class AttentionItem(
    @DrawableRes val icon: Int,
    val title: String,
    val tone: AttentionTone,
    val onClick: () -> Unit
)

@Composable
private fun buildAttentionItems(
    uiState: SparelyUiState,
    pendingVaultCount: Int,
    onNavigateToVaultTransfers: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToRecurring: () -> Unit
): List<AttentionItem> {
    val items = mutableListOf<AttentionItem>()

    if (pendingVaultCount > 0) {
        items += AttentionItem(
            icon = MaterialSymbols.SWAP_HORIZ,
            title = pluralStringResource(R.plurals.dashboard_attention_pending_transfers, pendingVaultCount, pendingVaultCount),
            tone = AttentionTone.INFO,
            onClick = onNavigateToVaultTransfers
        )
    }

    uiState.budgetSummary?.let { budget ->
        if (budget.categoriesOverBudget > 0) {
            items += AttentionItem(
                icon = MaterialSymbols.PIE_CHART,
                title = pluralStringResource(R.plurals.dashboard_attention_over_budget, budget.categoriesOverBudget, budget.categoriesOverBudget),
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
            items += AttentionItem(
                icon = MaterialSymbols.CALENDAR_MONTH,
                title = stringResource(
                    R.string.dashboard_attention_bill_due,
                    bill.recurringExpense.description,
                    (bill.predictedAmount ?: bill.recurringExpense.amount).formatCurrency(),
                    dueLabel(bill.daysUntilDue)
                ),
                tone = if (bill.daysUntilDue == 0) AttentionTone.WARNING else AttentionTone.INFO,
                onClick = onNavigateToRecurring
            )
        }

    return items
}

@Composable
private fun dueLabel(daysUntilDue: Int): String = when (daysUntilDue) {
    0 -> stringResource(R.string.dashboard_due_today)
    1 -> stringResource(R.string.dashboard_due_tomorrow)
    else -> stringResource(R.string.dashboard_due_in_days, daysUntilDue)
}

@Composable
private fun AttentionCard(items: List<AttentionItem>) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = 12.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
            items.forEach { item -> AttentionRow(item) }
        }
    }
}

@Composable
private fun AttentionRow(item: AttentionItem) {
    val (container, tint) = when (item.tone) {
        AttentionTone.INFO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        AttentionTone.WARNING -> MaterialTheme.colorScheme.warningContainer to MaterialTheme.colorScheme.warning
        AttentionTone.CRITICAL -> MaterialTheme.colorScheme.criticalContainer to MaterialTheme.colorScheme.critical
    }
    Surface(
        onClick = item.onClick,
        shape = ExpressiveShapes.small,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            IconBadge(icon = item.icon, containerColor = container, tint = tint, size = 36.dp)
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            MaterialSymbolIcon(
                icon = MaterialSymbols.ARROW_FORWARD,
                contentDescription = null,
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Accounts & goals
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UnifiedAccountsDashboardSection(
    savingsAccounts: List<SavingsAccount>,
    onManageSavingsAccounts: () -> Unit,
    vaults: List<SmartVault>,
    pendingVaultCount: Int,
    onManageVaults: () -> Unit,
    onManageAssets: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        R.string.dashboard_tab_savings,
        R.string.dashboard_tab_vaults,
        R.string.assets_tab
    )

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        onClick = { selectedTab = index },
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
                                            .background(MaterialTheme.colorScheme.critical, CircleShape)
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

        when (selectedTab) {
            0 -> {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
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
            1 -> DashboardVaultsCarousel(vaults = vaults, onManageVaults = onManageVaults)
            2 -> {
                ExpressiveCard(
                    onClick = onManageAssets,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    CardHeader(
                        icon = MaterialSymbols.SHOPPING_BAG,
                        title = stringResource(R.string.assets_title),
                        subtitle = stringResource(R.string.dashboard_assets_description),
                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                        trailing = {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.ARROW_FORWARD,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
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
    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        CardHeader(
            icon = MaterialSymbols.ACCOUNT_BALANCE,
            title = title,
            subtitle = subtitle,
            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.primary,
            trailing = {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.ADD_CIRCLE,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )
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
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

private val VAULT_CARD_HEIGHT = 152.dp

@Composable
private fun DashboardVaultsCarousel(
    vaults: List<SmartVault>,
    onManageVaults: () -> Unit
) {
    if (vaults.isEmpty()) {
        ExpressiveCard(
            onClick = onManageVaults,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                CardHeader(
                    icon = MaterialSymbols.SAVINGS,
                    title = stringResource(R.string.dashboard_vaults_empty_title),
                    subtitle = stringResource(R.string.dashboard_vaults_empty_description),
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.primary
                )
                SparelyTonalButton(
                    onClick = onManageVaults,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 20.dp) }
                ) {
                    SingleLineText(stringResource(R.string.dashboard_vaults_empty_action))
                }
            }
        }
        return
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    val singleVault = vaults.size == 1
    LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        items(vaults.take(MAX_DASHBOARD_VAULTS), key = { it.id }) { vault ->
            VaultItem(
                vault = vault,
                dateFormatter = dateFormatter,
                onClick = onManageVaults,
                modifier = Modifier.fillParentMaxWidth(if (singleVault) 1f else 0.82f)
            )
        }
        if (vaults.size > MAX_DASHBOARD_VAULTS) {
            item(key = "view_all") {
                val remaining = vaults.size - MAX_DASHBOARD_VAULTS
                Surface(
                    onClick = onManageVaults,
                    modifier = Modifier
                        .width(112.dp)
                        .height(VAULT_CARD_HEIGHT),
                    shape = ExpressiveShapes.large,
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
                            text = pluralStringResource(R.plurals.dashboard_more_vaults_count, remaining, remaining),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultItem(
    vault: SmartVault,
    dateFormatter: DateTimeFormatter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val progress = if (vault.targetAmount <= 0) 0f
                  else (vault.currentBalance / vault.targetAmount).toFloat().coerceIn(0f, 1f)

    val urgencyColor = when (vault.priority) {
        VaultPriority.CRITICAL -> MaterialTheme.colorScheme.critical
        VaultPriority.HIGH -> MaterialTheme.colorScheme.tertiary
        VaultPriority.MEDIUM -> MaterialTheme.colorScheme.primary
        VaultPriority.LOW -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(VAULT_CARD_HEIGHT),
        shape = ExpressiveShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.md),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vault.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = ExpressiveShapes.extraSmall,
                    color = urgencyColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = vault.type.displayName(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = urgencyColor,
                        maxLines = 1
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    SingleLineText(
                        text = vault.currentBalance.formatCurrency(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = urgencyColor
                    )
                }
                RoundedProgressBar(progress = progress, color = urgencyColor)
                vault.nextExpectedContribution?.takeIf { it > 0 }?.let { nextAmount ->
                    Text(
                        text = stringResource(R.string.dashboard_next_contribution, nextAmount.formatCurrency()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// This month
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UpcomingRecurringCard(
    items: List<UpcomingRecurringExpense>,
    hasRecurring: Boolean,
    onManageRecurring: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d") }
    val sorted = remember(items) { items.sortedBy { it.dueDate } }
    val totalUpcoming = sorted.sumOf { it.predictedAmount ?: it.recurringExpense.amount }

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onManageRecurring,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            CardHeader(
                icon = MaterialSymbols.CALENDAR_MONTH,
                title = stringResource(R.string.dashboard_up_next),
                subtitle = when {
                    sorted.isNotEmpty() -> pluralStringResource(R.plurals.dashboard_payments_due_count, sorted.size, sorted.size)
                    hasRecurring -> stringResource(R.string.dashboard_all_bills_paid)
                    else -> stringResource(R.string.dashboard_add_subscriptions_prompt)
                },
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                trailing = {
                    if (sorted.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.dashboard_total_commitment),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = totalUpcoming.formatCurrency(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    } else {
                        MaterialSymbolIcon(
                            icon = if (hasRecurring) MaterialSymbols.CHECK_CIRCLE else MaterialSymbols.ADD_CIRCLE,
                            contentDescription = null,
                            tint = if (hasRecurring) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )

            if (sorted.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    sorted.take(MAX_DASHBOARD_BILLS).forEach { upcoming ->
                        BillRow(upcoming = upcoming, formatter = formatter)
                    }
                    if (sorted.size > MAX_DASHBOARD_BILLS) {
                        val remaining = sorted.size - MAX_DASHBOARD_BILLS
                        Text(
                            text = pluralStringResource(R.plurals.dashboard_more_items_count, remaining, remaining),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BillRow(upcoming: UpcomingRecurringExpense, formatter: DateTimeFormatter) {
    val dueSoon = upcoming.daysUntilDue in 0..BILL_ATTENTION_WINDOW_DAYS
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
    ) {
        // Date badge: day number over short month for fast scanning
        Surface(
            shape = ExpressiveShapes.extraSmall,
            color = if (dueSoon) MaterialTheme.colorScheme.warningContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (dueSoon) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = upcoming.dueDate.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = upcoming.dueDate.format(monthFormatter),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = upcoming.recurringExpense.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (upcoming.daysUntilDue in 0..7) {
                    stringResource(R.string.dashboard_due_date, dueLabel(upcoming.daysUntilDue))
                } else {
                    stringResource(R.string.dashboard_due_date, upcoming.dueDate.format(formatter))
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (dueSoon) MaterialTheme.colorScheme.warning else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = (upcoming.predictedAmount ?: upcoming.recurringExpense.amount).formatCurrency(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = 0.dp
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

        if (patterns.predictedMonthEndConfidence != SuggestionConfidence.HIGH) {
            Text(
                text = stringResource(
                    R.string.dashboard_predicted_month_end_confidence,
                    patterns.predictedMonthEndConfidence.displayName(),
                    java.time.LocalDate.now().dayOfMonth
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

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

// ─────────────────────────────────────────────────────────────────────────────
// Savings progress
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SmartSavingSnapshotCard(summary: SmartSavingSummary, monthlyIncome: Double) {
    val spacing = MaterialTheme.spacing
    val isOnTrack = summary.actualSavingsRate >= summary.targetSavingsRate
    val statusColor = if (isOnTrack) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.critical
    
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
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
private fun EmergencyFundCard(goal: EmergencyFundGoal) {
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
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

// ─────────────────────────────────────────────────────────────────────────────
// Patterns & challenges
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecurringInsightsCard(
    insights: List<DetectedRecurringTransaction>,
    onConvert: (DetectedRecurringTransaction) -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val formatter = DateTimeFormatter.ofPattern("MMM d")
    val previewInsights = insights.take(3)

    ExpressiveCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
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
