@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.sparely.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.example.sparely.ui.theme.ExpressiveMotionTokens
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.BudgetInput
import com.example.sparely.domain.model.BudgetStatus
import com.example.sparely.domain.model.CategoryBudget
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.displayName
import com.example.sparely.domain.model.netAmount
import com.example.sparely.ui.components.ButtonGroupItem
import com.example.sparely.ui.components.ExpressiveListHeader
import com.example.sparely.ui.components.SegmentedListItem
import com.example.sparely.ui.components.SheetActions
import com.example.sparely.ui.components.SingleLineText
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.SparelyButtonGroup
import com.example.sparely.ui.components.SparelySheetHeader
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.components.segmentSpacing
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.state.SparelyUiState
import com.example.sparely.ui.theme.ExpressiveShapes
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.getCategoryColor
import com.example.sparely.ui.theme.getCategoryIcon
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.warning
import com.example.sparely.ui.utils.LocalDates
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.sparely.app.R
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** The numbers behind the budgets pages, kept apart from the UI so they can be tested. */
internal object BudgetMath {
    /** Share of the month gone by the end of [today]: 0.5 on the 15th of a 30-day month. */
    fun pace(today: LocalDate): Double = today.dayOfMonth.toDouble() / today.lengthOfMonth()

    /** Days left to spend in, today included. */
    fun daysLeft(today: LocalDate): Int = today.lengthOfMonth() - today.dayOfMonth + 1

    fun spentIn(expenses: List<Expense>, category: ExpenseCategory, month: YearMonth): Double =
        expenses.filter { it.category == category && YearMonth.from(it.date) == month }.sumOf { it.netAmount }

    /** Spending in [category] for the [months] months up to [current], oldest first. */
    fun history(expenses: List<Expense>, category: ExpenseCategory, current: YearMonth, months: Int = 6): List<Double> =
        (months - 1 downTo 0).map { spentIn(expenses, category, current.minusMonths(it.toLong())) }

    /** Where the month ends at the pace so far, when there's no forecast from the budget engine. */
    fun projection(spent: Double, today: LocalDate): Double =
        spent / today.dayOfMonth * today.lengthOfMonth()

    enum class Group { OVER, WATCH, OK }

    /** Over its limit; close to it (80 %) or on its way past it; or fine. */
    fun group(spent: Double, limit: Double, projected: Double): Group = when {
        spent > limit -> Group.OVER
        limit > 0 && (spent / limit >= 0.8 || projected > limit) -> Group.WATCH
        else -> Group.OK
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared drawing
// ─────────────────────────────────────────────────────────────────────────────

/** A bar filled to [progress] with a tick at [mark] (today's share of the month, a limit…). */
@Composable
internal fun MarkedBar(
    progress: Float,
    mark: Float?,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    trackColor: Color = color.copy(alpha = 0.18f),
    markColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(ExpressiveMotionTokens.EmphasizedDurationMillis, easing = ExpressiveMotionTokens.EmphasizedEasing), label = "bar")
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height + 8.dp)
    ) {
        val barH = height.toPx()
        val top = (size.height - barH) / 2
        val r = CornerRadius(barH / 2, barH / 2)
        drawRoundRect(trackColor, topLeft = Offset(0f, top), size = Size(size.width, barH), cornerRadius = r)
        if (shown > 0f) drawRoundRect(color, topLeft = Offset(0f, top), size = Size(size.width * shown, barH), cornerRadius = r)
        mark?.let {
            val x = size.width * it.coerceIn(0f, 1f)
            drawLine(markColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** A small trend line of [values], left to right. */
@Composable
internal fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max().takeIf { it > 0 } ?: 1.0
        val min = values.min()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        val pad = 2.dp.toPx()
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - pad - ((v - min) / span).toFloat() * (size.height - 2 * pad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Budgets
// ─────────────────────────────────────────────────────────────────────────────

/** One budget with everything its row and page show. */
private data class BudgetLine(
    val budget: CategoryBudget?,
    val status: BudgetStatus,
    val projected: Double,
    val history: List<Double>,
    val group: BudgetMath.Group
)

@Composable
fun BudgetScreen(
    uiState: SparelyUiState,
    onAddBudget: (BudgetInput) -> Unit,
    onUpdateBudget: (CategoryBudget) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenBudget: (ExpenseCategory) -> Unit = {}
) {
    var adding by remember { mutableStateOf<ExpenseCategory?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val month = YearMonth.from(today)
    val pace = BudgetMath.pace(today)
    val daysLeft = BudgetMath.daysLeft(today)
    val currencySymbol = uiState.settings.regionalSettings.getCurrencySymbol()
    val aiLanguage = com.example.sparely.ui.components.LocalOnDeviceAi.current.language
    val aiFacts = remember(uiState.budgetSummary, currencySymbol, aiLanguage) {
        com.example.sparely.ai.FinanceFacts.budgets(uiState, aiLanguage) { aiLanguage.money(it, currencySymbol) }
    }

    val summary = uiState.budgetSummary
    val lines = remember(summary, uiState.budgets, uiState.expenses, uiState.budgetForecasts) {
        summary?.categoryStatuses.orEmpty().map { status ->
            val projected = uiState.budgetForecasts.find { it.category == status.category }?.predictedMonthEndSpending
                ?: BudgetMath.projection(status.spent, today)
            BudgetLine(
                budget = uiState.budgets.find { it.category == status.category && it.yearMonth == status.yearMonth },
                status = status,
                projected = projected,
                history = BudgetMath.history(uiState.expenses, status.category, month),
                group = BudgetMath.group(status.spent, status.limit, projected)
            )
        }.sortedByDescending { it.status.percentageUsed }
    }
    val budgeted = lines.map { it.status.category }.toSet()
    // Categories with spending this month and no budget
    val unbudgeted = remember(uiState.expenses, budgeted) {
        uiState.expenses.filter { YearMonth.from(it.date) == month && it.category !in budgeted }
            .groupBy { it.category }
            .map { (category, list) -> Triple(category, list.sumOf { it.netAmount }, list.size) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
    }
    val suggestions = uiState.budgetSuggestions.filter { suggestion ->
        val existing = uiState.budgets.find { it.category == suggestion.category && it.yearMonth == month }
        existing == null || abs(suggestion.suggestedLimit - existing.monthlyLimit) >= 1.0
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
        ) {
            if (lines.isEmpty()) {
                item(key = "empty") { EmptyBudgetState(onAddBudget = { showAdd = true }) }
            } else if (summary != null) {
                item(key = "hero") {
                    val incomeExceeded = uiState.settings.monthlyIncome > 0 &&
                        summary.totalBudget > uiState.settings.monthlyIncome
                    BudgetsHero(
                        month = month,
                        totalBudget = summary.totalBudget,
                        totalSpent = summary.totalSpent,
                        pace = pace,
                        daysLeft = daysLeft,
                        overCount = lines.count { it.group == BudgetMath.Group.OVER },
                        suggestionCount = suggestions.size,
                        incomeExceeded = incomeExceeded,
                        onSuggestions = {
                            // The suggestions group is the list's last item
                            scope.launch { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) }
                        }
                    )
                }
            }

            item(key = "ai") {
                com.example.sparely.ui.components.OnDeviceAiSection(
                    title = stringResource(R.string.ai_budget_title),
                    facts = aiFacts,
                    task = com.example.sparely.ai.AiTask.BUDGET.text(aiLanguage),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            for (group in BudgetMath.Group.entries) {
                val inGroup = lines.filter { it.group == group }
                if (inGroup.isEmpty()) continue
                item(key = "header_$group") {
                    ExpressiveListHeader(
                        title = stringResource(
                            when (group) {
                                BudgetMath.Group.OVER -> R.string.bud_group_over
                                BudgetMath.Group.WATCH -> R.string.bud_group_watch
                                BudgetMath.Group.OK -> R.string.bud_group_ok
                            }
                        ),
                        icon = when (group) {
                            BudgetMath.Group.OVER -> MaterialSymbols.WARNING
                            BudgetMath.Group.WATCH -> MaterialSymbols.SCHEDULE
                            BudgetMath.Group.OK -> MaterialSymbols.CHECK_CIRCLE
                        },
                        trailing = inGroup.size.toString()
                    )
                }
                itemsIndexed(inGroup, key = { _, line -> "budget_${line.status.category}" }) { index, line ->
                    BudgetRow(
                        line = line,
                        pace = pace,
                        daysLeft = daysLeft,
                        today = today,
                        shape = segmentedShape(index, inGroup.size),
                        modifier = Modifier.segmentSpacing(index),
                        onClick = { onOpenBudget(line.status.category) }
                    )
                }
            }

            if (unbudgeted.isNotEmpty()) {
                item(key = "header_unbudgeted") {
                    ExpressiveListHeader(
                        title = stringResource(R.string.bud_group_unbudgeted),
                        icon = MaterialSymbols.INFO,
                        trailing = unbudgeted.sumOf { it.second }.formatCurrency(decimals = 0)
                    )
                }
                itemsIndexed(unbudgeted, key = { _, item -> "unbudgeted_${item.first}" }) { index, (category, spent, count) ->
                    SegmentedListItem(
                        title = category.displayName(),
                        subtitle = pluralStringResource(R.plurals.bud_purchases, count, count),
                        shape = segmentedShape(index, unbudgeted.size),
                        modifier = Modifier.segmentSpacing(index),
                        icon = getCategoryIcon(category),
                        iconTint = getCategoryColor(category),
                        trailing = {
                            Text(spent.formatCurrency(decimals = 0), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            SparelyTonalButton(onClick = { adding = category }) { Text(stringResource(R.string.bud_create)) }
                        }
                    )
                }
            }

            if (suggestions.isNotEmpty()) {
                suggestionsGroup(
                    suggestions = suggestions,
                    budgets = uiState.budgets.filter { it.yearMonth == month },
                    onApply = { category, amount ->
                        val existing = uiState.budgets.find { it.category == category && it.yearMonth == month }
                        if (existing != null) onUpdateBudget(existing.copy(monthlyLimit = amount))
                        else onAddBudget(BudgetInput(category, amount))
                    }
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            expanded = fabExpanded,
            icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = if (fabExpanded) null else stringResource(R.string.budget_add_budget), size = 24.dp) },
            text = { Text(stringResource(R.string.budget_add_budget)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )
    }

    if (showAdd || adding != null) {
        AddBudgetDialog(
            initialCategory = adding,
            onDismiss = {
                showAdd = false
                adding = null
            },
            onConfirm = { input ->
                onAddBudget(input)
                showAdd = false
                adding = null
            }
        )
    }
}

/** What's left this month, the spending against today's share of the month, and the alerts. */
@Composable
private fun BudgetsHero(
    month: YearMonth,
    totalBudget: Double,
    totalSpent: Double,
    pace: Double,
    daysLeft: Int,
    overCount: Int,
    suggestionCount: Int,
    incomeExceeded: Boolean,
    onSuggestions: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val onContainer = colors.onPrimaryContainer
    val left = totalBudget - totalSpent
    val used = if (totalBudget > 0) totalSpent / totalBudget else 0.0
    val monthName = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
    val (barColor, verdict) = when {
        left < 0 -> colors.critical to stringResource(R.string.bud_verdict_over)
        used > pace + 0.05 -> colors.warning to stringResource(R.string.bud_verdict_ahead)
        else -> colors.success to stringResource(R.string.bud_verdict_on_pace)
    }
    Surface(modifier = Modifier.fillMaxWidth(), shape = ExpressiveShapes.large, color = colors.primaryContainer, contentColor = onContainer) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(if (left >= 0) R.string.bud_hero_left else R.string.bud_hero_over, monthName),
                style = MaterialTheme.typography.labelLarge,
                color = onContainer.copy(alpha = 0.8f)
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleLineText(
                    text = abs(left).formatCurrency(decimals = 0),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = onContainer,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (left > 0) {
                    Text(
                        pluralStringResource(R.plurals.bud_per_day, daysLeft, (left / daysLeft).formatCurrency(decimals = 0), daysLeft),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            MarkedBar(
                progress = used.toFloat(),
                mark = pace.toFloat(),
                color = barColor,
                height = 10.dp,
                trackColor = colors.surface.copy(alpha = 0.6f),
                markColor = onContainer,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .semantics { contentDescription = verdict }
            )
            Text(
                stringResource(R.string.bud_pace_line, used.formatPercent(0), pace.formatPercent(0)) + " · " + verdict,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer.copy(alpha = 0.8f)
            )
            if (overCount > 0 || suggestionCount > 0 || incomeExceeded) {
                FlowRow(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (overCount > 0) HeroPill(MaterialSymbols.WARNING, pluralStringResource(R.plurals.bud_pill_over, overCount, overCount))
                    if (incomeExceeded) HeroPill(MaterialSymbols.WARNING, stringResource(R.string.bud_pill_over_income))
                    if (suggestionCount > 0) {
                        HeroPill(MaterialSymbols.LIGHTBULB, pluralStringResource(R.plurals.bud_pill_suggestions, suggestionCount, suggestionCount), onClick = onSuggestions)
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(
    line: BudgetLine,
    pace: Double,
    daysLeft: Int,
    today: LocalDate,
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val status = line.status
    val accent = when (line.group) {
        BudgetMath.Group.OVER -> colors.critical
        BudgetMath.Group.WATCH -> colors.warning
        BudgetMath.Group.OK -> colors.primary
    }
    val categoryColor = getCategoryColor(status.category)
    val left = status.limit - status.spent
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = colors.surfaceContainer) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(icon = getCategoryIcon(status.category), contentDescription = null, size = 22.dp, tint = categoryColor)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(status.category.displayName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (left < 0) stringResource(R.string.assets_over_by, (-left).formatCurrency(decimals = 0))
                        else stringResource(R.string.bud_left_per_day, left.formatCurrency(decimals = 0), (left / daysLeft).formatCurrency(decimals = 0)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (line.group == BudgetMath.Group.OK) FontWeight.Normal else FontWeight.Medium,
                        color = if (line.group == BudgetMath.Group.OK) colors.onSurfaceVariant else accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(status.spent.formatCurrency(decimals = 0), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.assets_of_amount, status.limit.formatCurrency(decimals = 0)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            MarkedBar(
                progress = status.percentageUsed.toFloat(),
                mark = pace.toFloat(),
                color = accent,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.bud_projected, line.projected.formatCurrency(decimals = 0), today.withDayOfMonth(today.lengthOfMonth()).format(LocalDates.dayMonth)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (line.projected > status.limit) accent else colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(stringResource(R.string.bud_six_months), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Sparkline(line.history, accent, Modifier.size(width = 56.dp, height = 18.dp))
            }
        }
    }
}

private fun LazyListScope.suggestionsGroup(
    suggestions: List<com.example.sparely.domain.model.BudgetSuggestion>,
    budgets: List<CategoryBudget>,
    onApply: (ExpenseCategory, Double) -> Unit
) {
    item(key = "header_suggestions") {
        ExpressiveListHeader(title = stringResource(R.string.bud_group_suggestions), icon = MaterialSymbols.LIGHTBULB, trailing = suggestions.size.toString())
    }
    itemsIndexed(suggestions, key = { _, s -> "suggestion_${s.category}" }) { index, suggestion ->
        val existing = budgets.find { it.category == suggestion.category }
        SegmentedListItem(
            title = suggestion.category.displayName(),
            subtitle = if (existing != null) stringResource(R.string.bud_suggest_change, suggestion.suggestedLimit.formatCurrency(decimals = 0), existing.monthlyLimit.formatCurrency(decimals = 0))
            else stringResource(R.string.bud_suggest_new, suggestion.suggestedLimit.formatCurrency(decimals = 0)),
            shape = segmentedShape(index, suggestions.size),
            modifier = Modifier.segmentSpacing(index),
            icon = getCategoryIcon(suggestion.category),
            iconTint = getCategoryColor(suggestion.category),
            trailing = {
                SparelyTonalButton(onClick = { onApply(suggestion.category, suggestion.suggestedLimit.coerceAtLeast(0.0)) }) {
                    Text(stringResource(R.string.bud_apply))
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// One budget
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One budget's page: spent against the limit and today's share of the month, the month-end
 * projection, its usual spending and Sparely's suggestion, the last six months, and this month's
 * expenses. Changing the limit is a sheet; deleting asks once.
 */
@Composable
fun BudgetDetailScreen(
    uiState: SparelyUiState,
    category: ExpenseCategory,
    onUpdateBudget: (CategoryBudget) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    onDeleted: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    val month = YearMonth.from(today)
    val budget = uiState.budgets.find { it.category == category && it.yearMonth == month }
    val status = uiState.budgetSummary?.categoryStatuses?.find { it.category == category }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    if (budget == null || status == null) {
        // Deleted (or not set for this month): nothing to show
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.bud_d_missing), color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        return
    }

    val pace = BudgetMath.pace(today)
    val daysLeft = BudgetMath.daysLeft(today)
    val projected = uiState.budgetForecasts.find { it.category == category }?.predictedMonthEndSpending
        ?: BudgetMath.projection(status.spent, today)
    val history = remember(uiState.expenses) { BudgetMath.history(uiState.expenses, category, month) }
    val average = history.dropLast(1).takeLast(3).filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()
    val suggestion = uiState.budgetSuggestions.find { it.category == category }
        ?.suggestedLimit?.takeIf { abs(it - budget.monthlyLimit) >= 1.0 }
    val expenses = remember(uiState.expenses) {
        uiState.expenses.filter { it.category == category && YearMonth.from(it.date) == month }.sortedByDescending { it.date }
    }
    val group = BudgetMath.group(status.spent, status.limit, projected)
    val accent = when (group) {
        BudgetMath.Group.OVER -> colors.critical
        BudgetMath.Group.WATCH -> colors.warning
        BudgetMath.Group.OK -> colors.primary
    }
    val left = status.limit - status.spent

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hero") {
            Surface(shape = ExpressiveShapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.budget_spent_label), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(status.spent.formatCurrency(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "/ " + status.limit.formatCurrency(decimals = 0),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    MarkedBar(
                        progress = status.percentageUsed.toFloat(),
                        mark = pace.toFloat(),
                        color = accent,
                        height = 10.dp,
                        trackColor = colors.surface.copy(alpha = 0.6f),
                        markColor = colors.onPrimaryContainer,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        if (left < 0) stringResource(R.string.assets_over_by, (-left).formatCurrency())
                        else stringResource(R.string.bud_left_per_day, left.formatCurrency(), (left / daysLeft).formatCurrency(decimals = 0)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (left < 0) colors.critical else colors.onPrimaryContainer
                    )
                }
            }
        }
        item(key = "tiles") {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.bud_tile_projected), projected.formatCurrency(decimals = 0), Modifier.weight(1f).fillMaxHeight())
                StatTile(stringResource(R.string.bud_tile_average), average?.formatCurrency(decimals = 0) ?: "—", Modifier.weight(1f).fillMaxHeight())
                StatTile(stringResource(R.string.bud_tile_suggested), suggestion?.formatCurrency(decimals = 0) ?: "—", Modifier.weight(1f).fillMaxHeight())
            }
        }
        item(key = "actions") {
            SparelyButtonGroup(
                buildList {
                    add(ButtonGroupItem(stringResource(R.string.bud_change_limit), MaterialSymbols.EDIT, emphasized = true) { editing = true })
                    suggestion?.let { amount ->
                        add(ButtonGroupItem(stringResource(R.string.bud_set_amount, amount.formatCurrency(decimals = 0)), MaterialSymbols.CHECK) {
                            onUpdateBudget(budget.copy(monthlyLimit = amount))
                        })
                    }
                }
            )
        }
        item(key = "history") {
            Column {
                ExpressiveListHeader(title = stringResource(R.string.bud_d_history), icon = MaterialSymbols.BAR_CHART)
                MonthBars(history = history, limit = status.limit, month = month, accent = accent)
            }
        }
        item(key = "expenses_header") {
            ExpressiveListHeader(
                title = stringResource(R.string.dashboard_this_month),
                icon = MaterialSymbols.RECEIPT,
                trailing = expenses.size.takeIf { it > 0 }?.toString()
            )
        }
        if (expenses.isEmpty()) {
            item(key = "no_expenses") {
                Text(
                    stringResource(R.string.bud_d_no_expenses),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
            }
        } else {
            item(key = "expenses") {
                Column {
                    expenses.forEachIndexed { index, expense ->
                        SegmentedListItem(
                            title = expense.description.ifBlank { category.displayName() },
                            subtitle = expense.date.format(LocalDates.dayMonth),
                            shape = segmentedShape(index, expenses.size),
                            modifier = Modifier.segmentSpacing(index),
                            icon = getCategoryIcon(category),
                            iconTint = getCategoryColor(category),
                            trailing = { Text(expense.netAmount.formatCurrency(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        }
        item(key = "delete") {
            OutlinedButton(
                onClick = { deleting = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error)
            ) {
                MaterialSymbolIcon(icon = MaterialSymbols.DELETE, contentDescription = null, size = 18.dp, tint = colors.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.bud_delete))
            }
        }
    }

    if (editing) {
        EditBudgetDialog(
            budget = budget,
            onDismiss = { editing = false },
            onConfirm = { amount ->
                onUpdateBudget(budget.copy(monthlyLimit = amount))
                editing = false
            },
            onDelete = {
                editing = false
                onDeleteBudget(budget.id)
                onDeleted()
            }
        )
    }
    if (deleting) {
        SparelyBottomSheet(isOpen = true, onDismiss = { deleting = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SparelySheetHeader(
                    title = stringResource(R.string.budget_delete_confirmation_title),
                    subtitle = stringResource(R.string.budget_delete_confirmation_desc, category.displayName()),
                    icon = MaterialSymbols.DELETE,
                    iconTint = colors.error
                )
                SheetActions(
                    confirmLabel = stringResource(R.string.delete),
                    onConfirm = {
                        deleting = false
                        onDeleteBudget(budget.id)
                        onDeleted()
                    },
                    destructive = true,
                    dismissLabel = stringResource(R.string.cancel),
                    onDismiss = { deleting = false }
                )
            }
        }
    }
}

/** Six months of spending as bars, with the limit as a dashed line and this month in [accent]. */
@Composable
private fun MonthBars(history: List<Double>, limit: Double, month: YearMonth, accent: Color) {
    val colors = MaterialTheme.colorScheme
    val max = maxOf(history.maxOrNull() ?: 0.0, limit).takeIf { it > 0 } ?: 1.0
    val monthLabel = DateTimeFormatter.ofPattern("LLL", Locale.getDefault())
    Surface(shape = ExpressiveShapes.large, color = colors.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    history.forEachIndexed { index, value ->
                        val current = index == history.lastIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight((value / max).toFloat().coerceIn(0.02f, 1f))
                                .clip(ExpressiveShapes.small)
                                .background(if (current) accent else colors.primary.copy(alpha = 0.35f))
                        )
                    }
                }
                val limitColor = colors.onSurfaceVariant
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height * (1f - (limit / max).toFloat())
                    drawLine(limitColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                history.indices.forEach { index ->
                    Text(
                        month.minusMonths((history.lastIndex - index).toLong()).format(monthLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                stringResource(R.string.bud_d_limit_line, limit.formatCurrency(decimals = 0)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
