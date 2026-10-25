@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.sparely.ui.screens

import com.example.sparely.ui.components.containerTransform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.Asset
import com.example.sparely.domain.model.DateRangeFilter
import com.example.sparely.domain.model.Expense
import com.example.sparely.domain.model.PaymentMethod
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.Store
import com.example.sparely.domain.model.displayName
import com.example.sparely.domain.model.netAmount
import com.example.sparely.ui.components.ButtonGroupItem
import com.example.sparely.ui.components.ExpressiveListHeader
import com.example.sparely.ui.components.SegmentedListItem
import com.example.sparely.ui.components.SingleLineText
import com.example.sparely.ui.components.SparelyButtonGroup
import com.example.sparely.ui.components.segmentSpacing
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.theme.ExpressiveShapes
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.getCategoryColor
import com.example.sparely.ui.theme.getCategoryIcon
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.warning
import com.example.sparely.ui.utils.LocalDates
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.sparely.app.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** The periods the expenses page filters on, as dates, kept apart from the UI so they can be tested. */
internal object ExpensePeriods {
    /** The days a filter covers, ending today at the latest; null for all time. */
    fun range(filter: DateRangeFilter, today: LocalDate, customStart: LocalDate? = null, customEnd: LocalDate? = null): ClosedRange<LocalDate>? =
        when (filter) {
            DateRangeFilter.LAST_7_DAYS -> today.minusDays(6)..today
            DateRangeFilter.LAST_30_DAYS -> today.minusDays(29)..today
            DateRangeFilter.LAST_90_DAYS -> today.minusDays(89)..today
            DateRangeFilter.THIS_MONTH -> today.withDayOfMonth(1)..today
            DateRangeFilter.YEAR_TO_DATE -> today.withDayOfYear(1)..today
            DateRangeFilter.CUSTOM -> if (customStart != null && customEnd != null) customStart..minOf(customEnd, today) else null
            DateRangeFilter.ALL_TIME -> null
        }

    /**
     * The period to compare with: the same days of last month for this month, of last year for the
     * year so far, otherwise the same number of days just before.
     */
    fun previous(filter: DateRangeFilter, range: ClosedRange<LocalDate>): ClosedRange<LocalDate> = when (filter) {
        DateRangeFilter.THIS_MONTH -> range.start.minusMonths(1)..range.endInclusive.minusMonths(1)
        DateRangeFilter.YEAR_TO_DATE -> range.start.minusYears(1)..range.endInclusive.minusYears(1)
        else -> {
            val days = ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1
            range.start.minusDays(days)..range.start.minusDays(1)
        }
    }

    fun total(expenses: List<Expense>, range: ClosedRange<LocalDate>): Double =
        expenses.filter { it.date in range }.sumOf { it.netAmount }

    /** One total per day of [range], oldest first. */
    fun daily(expenses: List<Expense>, range: ClosedRange<LocalDate>): List<Double> {
        val byDay = expenses.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.netAmount } }
        val days = ChronoUnit.DAYS.between(range.start, range.endInclusive).toInt() + 1
        return (0 until days).map { byDay[range.start.plusDays(it.toLong())] ?: 0.0 }
    }

    /** One total per month for the last [months] months up to [current], oldest first. */
    fun monthly(expenses: List<Expense>, current: YearMonth, months: Int = 6): List<Pair<YearMonth, Double>> {
        val byMonth = expenses.groupBy { YearMonth.from(it.date) }.mapValues { (_, list) -> list.sumOf { it.netAmount } }
        return (months - 1 downTo 0).map { current.minusMonths(it.toLong()) }.map { it to (byMonth[it] ?: 0.0) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The expenses page headline
// ─────────────────────────────────────────────────────────────────────────────

/**
 * What was spent in the period, against the period before, and day by day (month by month for long
 * periods) with the daily average as a dashed line (docs/DESIGN.md §3).
 */
@Composable
internal fun ExpensesHero(
    label: String,
    expenses: List<Expense>,
    allExpenses: List<Expense>,
    filter: DateRangeFilter,
    customStart: LocalDate?,
    customEnd: LocalDate?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val onContainer = colors.onPrimaryContainer
    val today = remember { LocalDate.now() }
    val total = expenses.sumOf { it.netAmount }
    val saved = expenses.sumOf { it.allocation.totalSetAside }
    val range = ExpensePeriods.range(filter, today, customStart, customEnd)
    val days = range?.let { ChronoUnit.DAYS.between(it.start, it.endInclusive).toInt() + 1 }
    // Compared only when the whole list is the period (no search or category narrowing it)
    val previous = range?.takeIf { expenses.size == allExpenses.count { e -> e.date in it } }
        ?.let { ExpensePeriods.total(allExpenses, ExpensePeriods.previous(filter, it)) }
        ?.takeIf { it > 0 }
    val change = previous?.let { (total - it) / it }

    Surface(modifier = modifier.fillMaxWidth(), shape = ExpressiveShapes.large, color = colors.primaryContainer, contentColor = onContainer) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = onContainer.copy(alpha = 0.8f), maxLines = 1)
            SingleLineText(
                text = total.formatCurrency(decimals = 0),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = onContainer
            )
            Text(
                listOfNotNull(
                    pluralStringResource(R.plurals.history_transactions_count, expenses.size, expenses.size),
                    days?.takeIf { it > 1 }?.let { stringResource(R.string.exp_per_day, (total / it).formatCurrency(decimals = 0)) }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer.copy(alpha = 0.8f)
            )

            if (range != null && days != null && days <= 92) {
                val values = ExpensePeriods.daily(expenses, range)
                SpendingBars(
                    values = values,
                    average = total / days,
                    highlightLast = range.endInclusive == today,
                    barColor = colors.primary,
                    lineColor = onContainer,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(range.start.format(LocalDates.dayMonth), style = MaterialTheme.typography.labelSmall, color = onContainer.copy(alpha = 0.7f))
                    Spacer(Modifier.weight(1f))
                    Text(range.endInclusive.format(LocalDates.dayMonth), style = MaterialTheme.typography.labelSmall, color = onContainer.copy(alpha = 0.7f))
                }
            } else {
                val months = ExpensePeriods.monthly(expenses, YearMonth.from(today))
                SpendingBars(
                    values = months.map { it.second },
                    average = null,
                    highlightLast = true,
                    barColor = colors.primary,
                    lineColor = onContainer,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    months.forEach { (month, _) ->
                        Text(
                            month.format(java.time.format.DateTimeFormatter.ofPattern("LLL")),
                            style = MaterialTheme.typography.labelSmall,
                            color = onContainer.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (change != null || saved > 0) {
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    change?.let {
                        val versus = stringResource(
                            when (filter) {
                                DateRangeFilter.THIS_MONTH -> R.string.exp_vs_last_month
                                DateRangeFilter.YEAR_TO_DATE -> R.string.exp_vs_last_year
                                else -> R.string.exp_vs_before
                            }
                        )
                        HeroPill(
                            if (it <= 0) MaterialSymbols.TRENDING_DOWN else MaterialSymbols.TRENDING_UP,
                            (if (it <= 0) "−" else "+") + kotlin.math.abs(it).formatPercent(0) + " " + versus
                        )
                    }
                    if (saved > 0) {
                        HeroPill(MaterialSymbols.SAVINGS, stringResource(R.string.exp_set_aside, saved.formatCurrency(decimals = 0)))
                    }
                }
            }
        }
    }
}

/** Bars of [values], the last one stronger when it's today, and [average] as a dashed line. */
@Composable
private fun SpendingBars(
    values: List<Double>,
    average: Double?,
    highlightLast: Boolean,
    barColor: Color,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxWidth().height(64.dp)) {
        if (values.isEmpty()) return@Canvas
        val max = values.max().takeIf { it > 0 } ?: 1.0
        val slot = size.width / values.size
        val gap = (slot * 0.25f).coerceAtMost(4.dp.toPx())
        values.forEachIndexed { i, v ->
            val h = (v / max).toFloat() * size.height
            val shown = maxOf(h, 2.dp.toPx())
            val strong = !highlightLast || i == values.lastIndex
            drawRoundRect(
                color = if (strong) barColor else barColor.copy(alpha = 0.55f),
                topLeft = Offset(i * slot + gap / 2, size.height - shown),
                size = Size(slot - gap, shown),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
        average?.takeIf { it > 0 }?.let {
            val y = size.height - (it / max).toFloat() * size.height
            drawLine(lineColor.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
    }
}

/** Where the money went, by store, as a group of rows with each one's share. */
@Composable
internal fun TopStoresGroup(
    storeStats: List<Triple<Store, Double, Int>>,
    totalSpent: Double,
    brandfetchClientId: String?,
    onStoreClick: (Store) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        ExpressiveListHeader(title = stringResource(R.string.history_top_stores), icon = MaterialSymbols.SHOPPING_BAG)
        storeStats.forEachIndexed { index, (store, amount, count) ->
            val share = if (totalSpent > 0) amount / totalSpent else 0.0
            SegmentedListItem(
                title = store.name,
                subtitle = pluralStringResource(R.plurals.history_store_purchases, count, count) + " · " + share.formatPercent(0),
                shape = segmentedShape(index, storeStats.size),
                modifier = Modifier.segmentSpacing(index),
                onClick = { onStoreClick(store) },
                leading = { com.example.sparely.ui.components.StoreIcon(store = store, brandfetchClientId = brandfetchClientId, size = 44) },
                below = { MarkedBar(progress = share.toFloat(), mark = null, color = colors.primary) },
                trailing = { Text(amount.formatCurrency(decimals = 0), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// One expense
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One expense's page: what was paid and with what, what was set aside, Edit / Duplicate / Refund,
 * its details (category, store, date, vault, projects), its items and note, and Delete.
 */
@Composable
fun ExpenseDetailScreen(
    expense: Expense?,
    store: Store?,
    paymentMethod: PaymentMethod?,
    vault: SmartVault?,
    assets: List<Asset>,
    brandfetchClientId: String?,
    onLoadAssetAllocations: suspend (Long) -> Map<Long, Double>,
    onEdit: (Expense) -> Unit,
    onDuplicate: (Expense) -> Unit,
    onRefund: (Long, Double, List<Long>) -> Unit,
    onDelete: (Expense) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    if (expense == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.exp_d_missing), color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        return
    }
    var refunding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var allocations by remember(expense.id) { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    LaunchedEffect(expense.id) { allocations = onLoadAssetAllocations(expense.id) }
    val categoryColor = getCategoryColor(expense.category)
    val saved = expense.allocation.totalSetAside

    LazyColumn(
        modifier = Modifier.containerTransform("expense-${expense?.id}").fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hero") {
            Surface(shape = ExpressiveShapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.exp_d_paid), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
                    SingleLineText(
                        text = expense.amount.formatCurrency(),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.onPrimaryContainer
                    )
                    Text(
                        listOfNotNull(expense.date.format(LocalDates.medium), paymentMethod?.name).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (saved > 0) {
                            HeroPill(
                                MaterialSymbols.SAVINGS,
                                stringResource(R.string.exp_d_saved, saved.formatCurrency(), (saved / expense.amount).formatPercent(0))
                            )
                        }
                        if (expense.refundedAmount > 0) {
                            HeroPill(MaterialSymbols.UNDO, stringResource(R.string.exp_d_refunded, expense.refundedAmount.formatCurrency()))
                        }
                        if (expense.isRecurring) HeroPill(MaterialSymbols.AUTORENEW, stringResource(R.string.history_card_recurring))
                    }
                }
            }
        }
        item(key = "actions") {
            SparelyButtonGroup(
                buildList {
                    add(ButtonGroupItem(stringResource(R.string.edit), MaterialSymbols.EDIT, emphasized = true) { onEdit(expense) })
                    add(ButtonGroupItem(stringResource(R.string.history_action_duplicate), MaterialSymbols.CONTENT_COPY) { onDuplicate(expense) })
                    if (!expense.isRefunded) add(ButtonGroupItem(stringResource(R.string.history_action_refund), MaterialSymbols.UNDO) { refunding = true })
                }
            )
        }
        item(key = "details") {
            val rows = buildList<Triple<Int, String, String>> {
                add(Triple(getCategoryIcon(expense.category), stringResource(R.string.exp_d_category), expense.category.displayName()))
                store?.let { add(Triple(MaterialSymbols.SHOPPING_BAG, stringResource(R.string.exp_d_store), it.name)) }
                paymentMethod?.let { add(Triple(MaterialSymbols.CREDIT_CARD, stringResource(R.string.exp_d_paid_with), it.name)) }
                add(Triple(MaterialSymbols.CALENDAR_MONTH, stringResource(R.string.exp_d_date), expense.date.format(LocalDates.medium)))
                add(Triple(MaterialSymbols.RECEIPT, stringResource(R.string.exp_d_tax), stringResource(if (expense.includesTax) R.string.exp_d_tax_included else R.string.exp_d_tax_not_included)))
                vault?.let { add(Triple(MaterialSymbols.SAVINGS, stringResource(R.string.exp_d_from_vault), it.name)) }
                allocations.forEach { (assetId, share) ->
                    assets.find { it.id == assetId }?.let { asset ->
                        add(Triple(MaterialSymbols.FLAG, asset.name, if (share < 0.999) share.formatPercent(0) else stringResource(R.string.exp_d_whole)))
                    }
                }
            }
            Column {
                ExpressiveListHeader(title = stringResource(R.string.sav_details_header), icon = MaterialSymbols.INFO)
                rows.forEachIndexed { index, (icon, label, value) ->
                    SegmentedListItem(
                        title = label,
                        shape = segmentedShape(index, rows.size),
                        modifier = Modifier.segmentSpacing(index),
                        icon = icon,
                        iconTint = if (index == 0) categoryColor else colors.primary,
                        trailing = { Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    )
                }
            }
        }
        if (expense.items.isNotEmpty()) {
            item(key = "items") {
                Column {
                    ExpressiveListHeader(
                        title = stringResource(R.string.exp_d_items),
                        icon = MaterialSymbols.LIST,
                        trailing = expense.items.size.toString()
                    )
                    expense.items.forEachIndexed { index, line ->
                        SegmentedListItem(
                            title = line.name,
                            subtitle = if (line.quantity > 1) "${line.quantity} × ${line.unitPrice.formatCurrency()}" else null,
                            shape = segmentedShape(index, expense.items.size),
                            modifier = Modifier.segmentSpacing(index),
                            trailing = { Text(line.totalPrice.formatCurrency(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        }
        expense.notes?.takeIf { it.isNotBlank() }?.let { note ->
            item(key = "note") {
                Surface(shape = ExpressiveShapes.large, color = colors.surfaceContainer) {
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(16.dp))
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
                Text(stringResource(R.string.delete))
            }
        }
    }

    if (refunding) {
        RefundExpenseDialog(
            expense = expense,
            onConfirm = { amount, itemIds ->
                onRefund(expense.id, amount, itemIds)
                refunding = false
            },
            onDismiss = { refunding = false }
        )
    }
    if (deleting) {
        DeleteExpenseConfirmationDialog(
            expense = expense,
            onConfirm = {
                deleting = false
                onDelete(expense)
            },
            onDismiss = { deleting = false }
        )
    }
}
