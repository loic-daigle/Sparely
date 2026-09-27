package com.example.sparely.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.logic.CashflowEngine
import com.example.sparely.domain.logic.SpendingPatternEngine
import com.example.sparely.domain.model.*
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.PaymentMethodIcon
import com.example.sparely.ui.components.SingleLineText
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.state.SparelyUiState
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.spacing
import com.sparely.app.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Positive / "on track" accent shared by every dashboard card. */
private val PositiveColor = Color(0xFF4CAF50)
private val CautionColor = Color(0xFFFF9800)

/** Days ahead within which an upcoming bill is surfaced in "Needs attention". */
private const val BILL_ATTENTION_WINDOW_DAYS = 3

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
    onNavigateToCreditCards: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
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

    val lastExpense = uiState.expenses.firstOrNull()
    val activeVaults = uiState.smartVaults.filter { !it.archived }
    val creditCards = uiState.paymentMethods.filter { it.isCreditCard }
    val showMainAccount = uiState.settings.mainAccountBalance != 0.0 || uiState.mainAccountTransactions.isNotEmpty()
    val attentionItems = buildAttentionItems(
        uiState = uiState,
        pendingTransferCount = pendingVaultContributions.size,
        onNavigateToVaultTransfers = onNavigateToVaultTransfers,
        onNavigateToBudgets = onNavigateToBudgets,
        onNavigateToRecurring = onNavigateToRecurring,
        onNavigateToInsights = onNavigateToInsights
    )

    // Removed local TopAppBar - using global SparelyTopBar instead
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (showFloatingFab) {
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
                    mainAccountBalance = if (showMainAccount) uiState.settings.mainAccountBalance else null,
                    forecast = uiState.cashflowForecast,
                    onMainAccountClick = onNavigateToMainAccount
                )
            }

            item(key = "quick_actions") {
                QuickActionsRow(
                    lastExpense = lastExpense,
                    onAddExpense = onAddExpense,
                    onRepeatLastExpense = { lastExpense?.let(onRepeatLastExpense) },
                    onNavigateToBudgets = onNavigateToBudgets,
                    onNavigateToInsights = onNavigateToInsights
                )
            }

            // ── Needs attention ─────────────────────────────────────────
            if (attentionItems.isNotEmpty()) {
                dashboardSection(key = "attention", titleRes = R.string.dashboard_needs_attention) {
                    AttentionCard(items = attentionItems)
                }
            }

            // ── Vaults ──────────────────────────────────────────────────
            dashboardSection(
                key = "vaults",
                titleRes = R.string.dashboard_smart_vaults,
                onSeeAll = onManageVaults.takeIf { activeVaults.isNotEmpty() }
            ) {
                DashboardVaultsCarousel(
                    vaults = activeVaults,
                    onManageVaults = onManageVaults
                )
            }

            // ── This month ──────────────────────────────────────────────
            dashboardSection(key = "this_month", titleRes = R.string.dashboard_this_month) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    uiState.financialHealthScore?.let { healthScore ->
                        QuickHealthScoreCard(
                            healthScore = healthScore,
                            onClick = onNavigateToHealth,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    val budgetSummary = uiState.budgetSummary
                    if (budgetSummary != null) {
                        QuickBudgetCard(
                            budgetSummary = budgetSummary,
                            onClick = onNavigateToBudgets,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    } else {
                        BudgetEmptyCard(
                            onClick = onNavigateToBudgets,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
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

            uiState.spendingPatterns?.let { patterns ->
                if (patterns.anomalies.isNotEmpty() || patterns.trend != SpendingPatternEngine.SpendingTrend.STABLE) {
                    item(key = "spending_insights") {
                        SpendingInsightsCard(
                            trend = patterns.trend,
                            trendPercentage = patterns.trendPercentage,
                            anomalies = patterns.anomalies,
                            predictedMonthEnd = patterns.predictedMonthEndSpending,
                            topGrowingCategory = patterns.topGrowingCategory,
                            topGrowingCategoryChange = patterns.topGrowingCategoryChange,
                            onViewDetails = onNavigateToInsights
                        )
                    }
                }
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

            // ── Challenges ──────────────────────────────────────────────
            if (uiState.activeChallenges.isEmpty()) {
                item(key = "challenges_empty") {
                    ChallengesEmptyCard(onClick = onNavigateToChallenges)
                }
            } else {
                dashboardSection(
                    key = "challenges",
                    titleRes = R.string.dashboard_active_challenges,
                    onSeeAll = onNavigateToChallenges
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        for (challenge in uiState.activeChallenges) {
                            QuickChallengeItem(challenge = challenge, onClick = onNavigateToChallenges)
                        }
                    }
                }
            }

            if (uiState.detectedRecurringTransactions.isNotEmpty()) {
                item(key = "recurring_patterns") {
                    RecurringInsightsCard(insights = uiState.detectedRecurringTransactions)
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
    onSeeAll: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    item(key = key) {
        Column(
            modifier = Modifier.padding(top = MaterialTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
        ) {
            SectionHeader(title = stringResource(titleRes), onSeeAll = onSeeAll)
            content()
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
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
        if (onSeeAll != null) {
            SparelyTextButton(onClick = onSeeAll) {
                Text(stringResource(R.string.action_view_all))
            }
        }
    }
}

/** Standard container for dashboard content cards so every card shares shape, color and padding. */
@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    ExpressiveCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        contentPadding = 20.dp,
        content = content
    )
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
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        MaterialSymbolIcon(icon = icon, contentDescription = null, tint = tint, size = size * 0.55f)
    }
}

@Composable
private fun LabeledValue(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(horizontalAlignment = horizontalAlignment) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun RoundedProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true
) {
    val target = progress.coerceIn(0f, 1f)
    val shown by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (animate) 900 else 0, easing = FastOutSlowInEasing),
        label = "progress"
    )
    LinearProgressIndicator(
        progress = { shown },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = color,
        trackColor = color.copy(alpha = 0.18f),
        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap
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
    mainAccountBalance: Double?,
    forecast: CashflowEngine.CashflowForecast?,
    onMainAccountClick: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    var forecastExpanded by rememberSaveable { mutableStateOf(false) }
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = onContainer,
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
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
                            shape = RoundedCornerShape(100),
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
                                    text = stringResource(R.string.dashboard_saving_percent, actualSavingsRate * 100),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                SingleLineText(
                    text = formatCurrency(totalBalance),
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    minFontSize = 24f
                )
            }

            if (mainAccountBalance != null || forecast != null) {
                // Both cells always render a caption line so they stay the same height without
                // intrinsic measurement (SingleLineText is subcomposed and doesn't support it).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    if (mainAccountBalance != null) {
                        HeroStat(
                            icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET,
                            label = stringResource(R.string.dashboard_main_account_title),
                            value = formatCurrency(mainAccountBalance),
                            caption = stringResource(R.string.dashboard_view_activity),
                            onClick = onMainAccountClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (forecast != null) {
                        val hasWarning = forecast.lowBalanceWarning != null
                        HeroStat(
                            icon = if (hasWarning) MaterialSymbols.WARNING else MaterialSymbols.PAYMENTS,
                            label = stringResource(R.string.dashboard_safe_to_spend),
                            value = formatCurrency(forecast.safeToSpend),
                            caption = if (forecast.runwayDays < Int.MAX_VALUE) {
                                stringResource(R.string.dashboard_runway_days, forecast.runwayDays)
                            } else {
                                stringResource(R.string.dashboard_runway_healthy)
                            },
                            valueColor = if (hasWarning) MaterialTheme.colorScheme.error else null,
                            trailingIcon = if (forecastExpanded) MaterialSymbols.ARROW_DROP_UP else MaterialSymbols.ARROW_DROP_DOWN,
                            onClickLabel = stringResource(R.string.dashboard_forecast_title),
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    @DrawableRes trailingIcon: Int? = MaterialSymbols.ARROW_FORWARD,
    onClickLabel: String? = null
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
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
                trailingIcon?.let {
                    MaterialSymbolIcon(
                        icon = it,
                        contentDescription = onClickLabel,
                        size = 16.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
        shape = RoundedCornerShape(20.dp),
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
                            text = formatCurrency(projection.projectedEndBalance),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (projection.projectedEndBalance < 0) {
                                MaterialTheme.colorScheme.error
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
    onAddExpense: () -> Unit,
    onRepeatLastExpense: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToInsights: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
    ) {
        QuickAction(
            icon = MaterialSymbols.ADD,
            label = stringResource(R.string.dashboard_quick_log),
            onClick = onAddExpense,
            emphasized = true,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            icon = MaterialSymbols.REFRESH,
            label = stringResource(R.string.dashboard_quick_repeat),
            onClick = onRepeatLastExpense,
            enabled = lastExpense != null,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            icon = MaterialSymbols.PIE_CHART,
            label = stringResource(R.string.dashboard_budget_label),
            onClick = onNavigateToBudgets,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            icon = MaterialSymbols.LIGHTBULB,
            label = stringResource(R.string.dashboard_insights_title),
            onClick = onNavigateToInsights,
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
        shape = RoundedCornerShape(20.dp),
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

private enum class AttentionTone { PRIMARY, WARNING, ERROR }

private data class AttentionItem(
    @DrawableRes val icon: Int,
    val title: String,
    val tone: AttentionTone,
    val onClick: () -> Unit
)

@Composable
private fun buildAttentionItems(
    uiState: SparelyUiState,
    pendingTransferCount: Int,
    onNavigateToVaultTransfers: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToRecurring: () -> Unit,
    onNavigateToInsights: () -> Unit
): List<AttentionItem> {
    val items = mutableListOf<AttentionItem>()

    if (pendingTransferCount > 0) {
        items += AttentionItem(
            icon = MaterialSymbols.SWAP_HORIZ,
            title = pluralStringResource(R.plurals.dashboard_attention_pending_transfers, pendingTransferCount, pendingTransferCount),
            tone = AttentionTone.PRIMARY,
            onClick = onNavigateToVaultTransfers
        )
    }

    uiState.cashflowForecast?.lowBalanceWarning?.let { warning ->
        items += AttentionItem(
            icon = MaterialSymbols.WARNING,
            title = stringResource(
                R.string.dashboard_attention_low_balance,
                formatCurrency(warning.projectedLowBalance),
                warning.daysUntilLowBalance
            ),
            tone = if (warning.severity == CashflowEngine.WarningSeverity.CRITICAL) AttentionTone.ERROR else AttentionTone.WARNING,
            onClick = onNavigateToInsights
        )
    }

    uiState.budgetSummary?.let { budget ->
        if (budget.categoriesOverBudget > 0) {
            items += AttentionItem(
                icon = MaterialSymbols.PIE_CHART,
                title = pluralStringResource(R.plurals.dashboard_attention_over_budget, budget.categoriesOverBudget, budget.categoriesOverBudget),
                tone = AttentionTone.ERROR,
                onClick = onNavigateToBudgets
            )
        }
    }

    uiState.upcomingRecurring
        .filter { it.daysUntilDue in 0..BILL_ATTENTION_WINDOW_DAYS }
        .sortedBy { it.daysUntilDue }
        .take(2)
        .forEach { bill ->
            val amount = formatCurrency(bill.predictedAmount ?: bill.recurringExpense.amount)
            items += AttentionItem(
                icon = MaterialSymbols.CALENDAR_MONTH,
                title = stringResource(
                    R.string.dashboard_attention_bill_due,
                    bill.recurringExpense.description,
                    amount,
                    dueLabel(bill.daysUntilDue)
                ),
                tone = if (bill.daysUntilDue == 0) AttentionTone.WARNING else AttentionTone.PRIMARY,
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
    DashboardCard(modifier = Modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            items.forEach { item -> AttentionRow(item) }
        }
    }
}

@Composable
private fun AttentionRow(item: AttentionItem) {
    val (container, tint) = when (item.tone) {
        AttentionTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        AttentionTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        AttentionTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }
    Surface(
        onClick = item.onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
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
// Vaults
// ─────────────────────────────────────────────────────────────────────────────

private const val MAX_DASHBOARD_VAULTS = 5

@Composable
private fun DashboardVaultsCarousel(
    vaults: List<SmartVault>,
    onManageVaults: () -> Unit
) {
    if (vaults.isEmpty()) {
        DashboardCard(onClick = onManageVaults) {
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
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
    ) {
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
                Surface(
                    onClick = onManageVaults,
                    modifier = Modifier
                        .width(112.dp)
                        .height(VAULT_CARD_HEIGHT),
                    shape = RoundedCornerShape(24.dp),
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
                            text = pluralStringResource(
                                R.plurals.dashboard_more_vaults_count,
                                vaults.size - MAX_DASHBOARD_VAULTS,
                                vaults.size - MAX_DASHBOARD_VAULTS
                            ),
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

private val VAULT_CARD_HEIGHT = 152.dp

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
        VaultPriority.CRITICAL -> MaterialTheme.colorScheme.error
        VaultPriority.HIGH -> MaterialTheme.colorScheme.tertiary
        VaultPriority.MEDIUM -> MaterialTheme.colorScheme.primary
        VaultPriority.LOW -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(VAULT_CARD_HEIGHT),
        shape = RoundedCornerShape(24.dp),
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
                        append(formatCurrency(vault.targetAmount))
                        vault.targetDate?.let { append(" • ${it.format(dateFormatter)}") }
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
                    shape = RoundedCornerShape(8.dp),
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
                        text = formatCurrency(vault.currentBalance),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format("%.0f%%", progress * 100),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = urgencyColor
                    )
                }
                RoundedProgressBar(progress = progress, color = urgencyColor)
                vault.nextExpectedContribution?.takeIf { it > 0 }?.let { nextAmount ->
                    Text(
                        text = stringResource(R.string.dashboard_next_contribution, formatCurrency(nextAmount)),
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
private fun QuickHealthScoreCard(
    healthScore: FinancialHealthScore,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthColor = when (healthScore.healthLevel) {
        HealthLevel.EXCELLENT, HealthLevel.GOOD -> PositiveColor
        HealthLevel.FAIR -> MaterialTheme.colorScheme.tertiary
        HealthLevel.NEEDS_WORK -> CautionColor
        HealthLevel.CRITICAL -> MaterialTheme.colorScheme.error
    }

    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.heightIn(min = 168.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TileHeader(
                icon = MaterialSymbols.HEALTH_AND_SAFETY,
                label = stringResource(R.string.dashboard_health_label),
                tint = healthColor
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { healthScore.overallScore.toFloat() / 100f },
                    modifier = Modifier.size(72.dp),
                    color = healthColor,
                    strokeWidth = 8.dp,
                    trackColor = healthColor.copy(alpha = 0.18f),
                    strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap
                )
                Text(
                    text = "${healthScore.overallScore}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = healthScore.healthLevel.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = healthColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun TileHeader(@DrawableRes icon: Int, label: String, tint: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            MaterialSymbolIcon(icon = icon, contentDescription = null, tint = tint, size = 18.dp)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuickBudgetCard(
    budgetSummary: BudgetSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val indicatorColor = when (budgetSummary.overallHealth) {
        BudgetHealthStatus.HEALTHY -> PositiveColor
        BudgetHealthStatus.WARNING -> MaterialTheme.colorScheme.tertiary
        BudgetHealthStatus.CRITICAL -> CautionColor
        BudgetHealthStatus.OVER_BUDGET -> MaterialTheme.colorScheme.error
    }

    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.heightIn(min = 168.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TileHeader(
                icon = MaterialSymbols.PIE_CHART,
                label = stringResource(R.string.dashboard_budget_label),
                tint = indicatorColor
            )
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = formatPercent(budgetSummary.percentageUsed, cap = 2.0),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = indicatorColor
                )
                Text(
                    text = stringResource(R.string.dashboard_used_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundedProgressBar(progress = budgetSummary.percentageUsed.toFloat(), color = indicatorColor)
                Text(
                    text = stringResource(
                        R.string.dashboard_remaining_of_budget,
                        formatCurrency(budgetSummary.totalRemaining),
                        formatCurrency(budgetSummary.totalBudget)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BudgetEmptyCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.heightIn(min = 168.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TileHeader(
                icon = MaterialSymbols.PIE_CHART,
                label = stringResource(R.string.dashboard_budget_label),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.dashboard_setup_budgets_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.dashboard_create_first_budget),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                MaterialSymbolIcon(
                    icon = MaterialSymbols.ARROW_FORWARD,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 16.dp
                )
            }
        }
    }
}

private const val MAX_DASHBOARD_BILLS = 3

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

    DashboardCard(onClick = onManageRecurring) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            CardHeader(
                icon = MaterialSymbols.CALENDAR_MONTH,
                title = stringResource(R.string.dashboard_upcoming_bills),
                subtitle = when {
                    sorted.isNotEmpty() -> pluralStringResource(R.plurals.dashboard_upcoming_bills_count, sorted.size, sorted.size)
                    hasRecurring -> stringResource(R.string.dashboard_all_caught_up)
                    else -> stringResource(R.string.dashboard_log_subscriptions_reminders)
                },
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.tertiary,
                trailing = {
                    if (sorted.isNotEmpty()) {
                        LabeledValue(
                            label = stringResource(R.string.dashboard_total_due),
                            value = formatCurrency(totalUpcoming),
                            valueColor = MaterialTheme.colorScheme.tertiary,
                            horizontalAlignment = Alignment.End
                        )
                    } else {
                        MaterialSymbolIcon(
                            icon = if (hasRecurring) MaterialSymbols.CHECK_CIRCLE else MaterialSymbols.ADD_CIRCLE,
                            contentDescription = null,
                            tint = if (hasRecurring) PositiveColor else MaterialTheme.colorScheme.primary
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
                        Text(
                            text = stringResource(R.string.dashboard_more_bills, sorted.size - MAX_DASHBOARD_BILLS),
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
    ) {
        // Date chip: day number over short month for fast scanning
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (dueSoon) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (dueSoon) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    text = upcoming.dueDate.format(DateTimeFormatter.ofPattern("MMM")),
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
                    dueLabel(upcoming.daysUntilDue)
                } else {
                    stringResource(R.string.dashboard_due_on_date, upcoming.dueDate.format(formatter))
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (dueSoon) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatCurrency(upcoming.predictedAmount ?: upcoming.recurringExpense.amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SpendingInsightsCard(
    trend: SpendingPatternEngine.SpendingTrend,
    trendPercentage: Double,
    anomalies: List<SpendingPatternEngine.SpendingAnomaly>,
    predictedMonthEnd: Double,
    topGrowingCategory: ExpenseCategory?,
    topGrowingCategoryChange: Double,
    onViewDetails: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val anomalyCount = anomalies.size

    val trendColor = when (trend) {
        SpendingPatternEngine.SpendingTrend.INCREASING -> MaterialTheme.colorScheme.error
        SpendingPatternEngine.SpendingTrend.DECREASING -> PositiveColor
        SpendingPatternEngine.SpendingTrend.STABLE -> MaterialTheme.colorScheme.primary
    }
    val trendIcon = when (trend) {
        SpendingPatternEngine.SpendingTrend.INCREASING -> MaterialSymbols.TRENDING_UP
        SpendingPatternEngine.SpendingTrend.DECREASING -> MaterialSymbols.TRENDING_DOWN
        SpendingPatternEngine.SpendingTrend.STABLE -> MaterialSymbols.SWAP_HORIZ
    }
    val trendText = when (trend) {
        SpendingPatternEngine.SpendingTrend.INCREASING ->
            stringResource(R.string.dashboard_trend_increasing, kotlin.math.abs(trendPercentage))
        SpendingPatternEngine.SpendingTrend.DECREASING ->
            stringResource(R.string.dashboard_trend_decreasing, kotlin.math.abs(trendPercentage))
        SpendingPatternEngine.SpendingTrend.STABLE ->
            stringResource(R.string.dashboard_trend_stable)
    }

    DashboardCard(onClick = { isExpanded = !isExpanded }) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            CardHeader(
                icon = trendIcon,
                title = stringResource(R.string.dashboard_spending_insights),
                subtitle = trendText,
                iconContainerColor = trendColor.copy(alpha = 0.16f),
                iconTint = trendColor,
                trailing = {
                    MaterialSymbolIcon(
                        icon = if (isExpanded) MaterialSymbols.ARROW_DROP_UP else MaterialSymbols.ARROW_DROP_DOWN,
                        contentDescription = stringResource(if (isExpanded) R.string.dashboard_collapse else R.string.dashboard_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dashboard_predicted_month_end, formatCurrency(predictedMonthEnd)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (anomalyCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.WARNING,
                                contentDescription = null,
                                size = 14.dp,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(R.string.dashboard_anomaly_detected, anomalyCount),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    topGrowingCategory?.let { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.dashboard_top_growing_category),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "${category.name.lowercase().replaceFirstChar { it.uppercase() }} +${String.format("%.0f", topGrowingCategoryChange)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (anomalies.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.dashboard_unusual_transactions),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        anomalies.take(5).forEach { anomaly ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = anomaly.expense.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = anomaly.expense.category.name.lowercase().replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatCurrency(anomaly.expense.amount),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = stringResource(R.string.dashboard_times_typical, anomaly.zScore),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    SparelyTextButton(
                        onClick = onViewDetails,
                        modifier = Modifier.align(Alignment.End),
                        icon = { MaterialSymbolIcon(icon = MaterialSymbols.ARROW_FORWARD, contentDescription = null, size = 16.dp) }
                    ) {
                        Text(stringResource(R.string.dashboard_view_full_insights))
                    }
                }
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
    val utilizationColor = utilizationColor(overallUtilization, PositiveColor)

    DashboardCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            CardHeader(
                icon = MaterialSymbols.CREDIT_CARD,
                title = stringResource(R.string.dashboard_credit_cards_title),
                subtitle = pluralStringResource(R.plurals.credit_cards_count, creditCards.size, creditCards.size),
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.secondary,
                trailing = {
                    LabeledValue(
                        label = stringResource(R.string.dashboard_total_balance),
                        value = formatCurrency(totalBalance),
                        horizontalAlignment = Alignment.End
                    )
                }
            )

            if (totalLimit > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_total_utilization),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatPercent(overallUtilization),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = utilizationColor
                        )
                    }
                    RoundedProgressBar(progress = overallUtilization.toFloat(), color = utilizationColor)
                    if (overallUtilization >= 0.3) {
                        Text(
                            text = stringResource(R.string.dashboard_utilization_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = utilizationColor
                        )
                    }
                }
            }

            if (creditCards.size > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    creditCards.take(3).forEach { card ->
                        val limit = card.creditLimit ?: 0.0
                        val cardUtil = if (limit > 0) card.currentBalance / limit else 0.0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PaymentMethodIcon(method = card, modifier = Modifier.size(24.dp))
                            Text(
                                text = card.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatCurrency(card.currentBalance),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = utilizationColor(cardUtil, MaterialTheme.colorScheme.onSurface)
                            )
                        }
                    }
                    if (creditCards.size > 3) {
                        Text(
                            text = stringResource(R.string.dashboard_more_cards, creditCards.size - 3),
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
private fun utilizationColor(utilization: Double, healthyColor: Color): Color = when {
    utilization < 0.3 -> healthyColor
    utilization < 0.5 -> CautionColor
    else -> MaterialTheme.colorScheme.error
}

// ─────────────────────────────────────────────────────────────────────────────
// Savings progress
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SmartSavingSnapshotCard(summary: SmartSavingSummary, monthlyIncome: Double) {
    val spacing = MaterialTheme.spacing
    val isOnTrack = summary.actualSavingsRate >= summary.targetSavingsRate
    val statusColor = if (isOnTrack) PositiveColor else MaterialTheme.colorScheme.error
    val rateProgress = if (summary.targetSavingsRate > 0) {
        (summary.actualSavingsRate / summary.targetSavingsRate).toFloat()
    } else {
        1f
    }

    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            CardHeader(
                icon = MaterialSymbols.SAVINGS,
                title = stringResource(R.string.dashboard_smart_saving),
                subtitle = when (summary.allocationMode) {
                    SmartAllocationMode.MANUAL -> stringResource(R.string.dashboard_allocation_manual_mode)
                    SmartAllocationMode.GUIDED -> stringResource(R.string.dashboard_allocation_guided_mode)
                    SmartAllocationMode.AUTOMATIC -> stringResource(R.string.dashboard_allocation_automatic_mode)
                },
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary
            )

            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    LabeledValue(
                        label = stringResource(R.string.dashboard_savings_rate_label),
                        value = formatPercent(summary.actualSavingsRate),
                        valueColor = statusColor
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.dashboard_target_with_amount_label, formatPercent(summary.targetSavingsRate)),
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
                                text = stringResource(if (isOnTrack) R.string.dashboard_on_track else R.string.dashboard_below_target),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                }
                RoundedProgressBar(progress = rateProgress, color = statusColor)
            }

            if (monthlyIncome > 0.0) {
                Text(
                    text = stringResource(R.string.dashboard_aim_for_target, formatCurrency(monthlyIncome * summary.targetSavingsRate)),
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
    val savedAmount = (goal.targetAmount - goal.shortfallAmount).coerceAtLeast(0.0)
    val shortfall = goal.shortfallAmount.coerceAtLeast(0.0)
    val statusColor = if (coverage >= 1.0) PositiveColor else MaterialTheme.colorScheme.primary

    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            CardHeader(
                icon = MaterialSymbols.SECURITY,
                title = stringResource(R.string.dashboard_emergency_runway),
                subtitle = stringResource(R.string.dashboard_month_goal_text, formatMonths(goal.targetMonths)),
                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                iconTint = MaterialTheme.colorScheme.error
            )

            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    LabeledValue(
                        label = stringResource(R.string.dashboard_current_cushion_label),
                        value = formatCurrency(savedAmount)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.dashboard_target_currency, formatCurrency(goal.targetAmount)),
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
                RoundedProgressBar(progress = coverage.toFloat(), color = statusColor)
            }

            if (shortfall > 0.0) {
                Text(
                    text = stringResource(R.string.dashboard_remaining_to_go_amount, formatCurrency(shortfall)),
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
                        tint = PositiveColor
                    )
                    Text(
                        text = stringResource(R.string.dashboard_goal_reached),
                        style = MaterialTheme.typography.bodySmall,
                        color = PositiveColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Challenges & patterns
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuickChallengeItem(challenge: SavingsChallenge, onClick: () -> Unit) {
    val streakColor = MaterialTheme.colorScheme.tertiary
    DashboardCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = challenge.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (challenge.streakDays > 0) {
                    Surface(
                        color = streakColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(100)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.LOCAL_FIRE_DEPARTMENT,
                                contentDescription = null,
                                tint = streakColor,
                                size = 14.dp
                            )
                            Text(
                                text = "${challenge.streakDays}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = streakColor
                            )
                        }
                    }
                }
            }
            if (challenge.targetAmount > 0) {
                RoundedProgressBar(
                    progress = challenge.progressPercent.toFloat(),
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = stringResource(R.string.dashboard_challenge_progress, formatPercent(challenge.progressPercent)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChallengesEmptyCard(onClick: () -> Unit) {
    DashboardCard(onClick = onClick) {
        CardHeader(
            icon = MaterialSymbols.TROPHY,
            title = stringResource(R.string.dashboard_savings_challenges),
            subtitle = stringResource(R.string.dashboard_challenges_description),
            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconTint = MaterialTheme.colorScheme.secondary,
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

@Composable
private fun RecurringInsightsCard(insights: List<DetectedRecurringTransaction>) {
    val spacing = MaterialTheme.spacing
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d") }
    val previewInsights = insights.take(4)

    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            CardHeader(
                icon = MaterialSymbols.SYNC,
                title = stringResource(R.string.dashboard_recurring_patterns),
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconTint = MaterialTheme.colorScheme.tertiary
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                previewInsights.forEach { insight ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = insight.description,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${stringResource(R.string.dashboard_every_days, insight.cadenceDays)} • " +
                                    stringResource(R.string.dashboard_last_on, insight.lastOccurrence.format(formatter)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatCurrency(insight.averageAmount),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Formatting
// ─────────────────────────────────────────────────────────────────────────────

private fun formatMonths(months: Double): String =
    if (months % 1.0 == 0.0) months.toInt().toString() else String.format("%.1f", months)

private fun formatCurrency(value: Double): String = "$" + String.format("%,.2f", value)

private fun formatPercent(value: Double, cap: Double = 1.0): String =
    String.format("%.1f%%", value.coerceIn(0.0, cap) * 100)
