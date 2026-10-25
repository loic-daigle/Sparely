@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.sparely.ui.screens

import com.example.sparely.ui.components.containerTransform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import com.example.sparely.ui.components.SparelyAmountTextField
import com.example.sparely.ui.components.ExpressiveEmptyState
import com.example.sparely.ui.components.segmentSpacing
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.theme.ExpressiveShapes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.logic.PurchaseAffordabilityEngine
import com.example.sparely.domain.logic.PurchaseAffordabilityEngine.FindingKind
import com.example.sparely.domain.logic.WishPlan
import com.example.sparely.domain.logic.WishReadiness
import com.example.sparely.domain.model.Wishlist
import com.example.sparely.domain.model.needsDecision
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.SparelySheetHeader
import com.example.sparely.ui.components.SheetActions
import com.example.sparely.ui.components.SheetAmountField
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyOutlinedButton
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyTextField
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.state.SparelyUiState
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.spacing
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.warning
import com.example.sparely.ui.utils.filterAmountExpressionInput
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.utils.toSafeDouble
import com.sparely.app.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** A saved wish together with how it stands against the user's finances today. */
internal data class WishStatus(
    val wish: Wishlist,
    /** Money held back for it so far: the scheduled build-up or what was added by hand. */
    val reserved: Double,
    val result: PurchaseAffordabilityEngine.Result?,
    val readiness: WishReadiness?
) {
    val progress: Float
        get() = if (wish.targetAmount > 0) (reserved / wish.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
}

/**
 * Checks one wish against the user's finances. Money already held back for the wish itself must not
 * count against buying it, so only the reserves for the *other* wishes stay taken off.
 */
internal fun buildWishStatus(
    wish: Wishlist,
    input: com.example.sparely.domain.logic.CashflowEngine.ForecastInput?,
    context: PurchaseAffordabilityEngine.Context
): WishStatus {
    val reserved = WishPlan.reserved(wish)
    val result = input?.let {
        val others = (it.earmarkedMoney - reserved).coerceAtLeast(0.0)
        PurchaseAffordabilityEngine.evaluate(it.copy(earmarkedMoney = others), wish.targetAmount, context)
    }
    return WishStatus(wish, reserved, result, result?.let { r -> WishReadiness.assess(wish, r.verdict, reserved = reserved) })
}

/** Whole days left in the reflection time, rounded up; 0 when it is over or there is none. */
internal fun cooldownDaysLeft(wish: Wishlist): Int {
    val now = LocalDateTime.now()
    return wish.cooldownExpiresAt
        ?.takeIf { it.isAfter(now) }
        ?.let { ((Duration.between(now, it).toHours() + 23) / 24).toInt() }
        ?: 0
}

@Composable
internal fun readinessLabel(readiness: WishReadiness?, cooldownDays: Int): String? = when (readiness) {
    WishReadiness.READY -> stringResource(R.string.wishlist_ready)
    WishReadiness.COOLING_DOWN -> stringResource(R.string.wishlist_cooling, cooldownDays.coerceAtLeast(1))
    WishReadiness.SAVING_UP -> stringResource(R.string.wishlist_saving_up)
    WishReadiness.NOT_AFFORDABLE -> stringResource(R.string.wishlist_not_affordable)
    null -> null
}

/**
 * Saved purchases. Each one is re-checked against the user's current finances every time the
 * screen is shown, so it answers "can I afford it now?" without the user re-entering anything.
 * Wishes with a target date also hold money back: it stays in the bank but is taken off what the
 * app treats as available.
 */
@Composable
fun WishlistScreen(
    uiState: SparelyUiState,
    onQuickCheck: () -> Unit,
    onAddSavings: (wishlistId: Long, amount: Double) -> Unit,
    onBought: (wishlistId: Long) -> Unit,
    onDelete: (wishlistId: Long) -> Unit,
    onSetTargetDate: (wishlistId: Long, date: LocalDate?) -> Unit,
    onStillWant: (wishlistId: Long) -> Unit,
    onNotAnymore: (wishlistId: Long) -> Unit,
    onOpenWish: (wishlistId: Long) -> Unit
) {
    val spacing = MaterialTheme.spacing
    val input = uiState.forecastInput
    val context = remember(uiState) { uiState.toPurchaseContext() }

    // Closest to buying first, so the most actionable wishes lead the list.
    val statuses = remember(uiState.wishlists, input, context) {
        uiState.wishlists
            .map { wish -> buildWishStatus(wish, input, context) }
            .sortedWith(compareBy<WishStatus> { it.readiness?.ordinal ?: Int.MAX_VALUE }.thenByDescending { it.wish.priority })
    }

    var savingsDialogFor by remember { mutableStateOf<Wishlist?>(null) }
    var dateDialogFor by remember { mutableStateOf<Wishlist?>(null) }
    var confirmBought by remember { mutableStateOf<Wishlist?>(null) }
    var confirmDelete by remember { mutableStateOf<Wishlist?>(null) }

    if (statuses.isEmpty()) {
        EmptyWishlist(onQuickCheck)
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            // Content padding (not Modifier.padding) so cards don't clip while scrolling; the bottom
            // gap keeps the last card clear of the floating action button.
            contentPadding = PaddingValues(start = spacing.md, end = spacing.md, top = spacing.xs, bottom = 96.dp)
        ) {
            item(key = "summary") { SummaryCard(statuses, heldBack = uiState.wishlistReserved) }

            // Wishes as one segmented group of rows (docs/DESIGN.md §5); buying, removing and the
            // date are on each wish's page
            itemsIndexed(statuses, key = { _, status -> status.wish.id }) { index, status ->
                WishRow(
                    status = status,
                    shape = segmentedShape(index, statuses.size),
                    modifier = Modifier.segmentSpacing(index),
                    onAddSavings = { savingsDialogFor = status.wish },
                    onOpen = { onOpenWish(status.wish.id) },
                    onStillWant = { onStillWant(status.wish.id) },
                    onNotAnymore = { onNotAnymore(status.wish.id) }
                )
            }

            item(key = "quick_check") {
                SparelyOutlinedButton(
                    onClick = onQuickCheck,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.md),
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 20.dp) }
                ) {
                    Text(stringResource(R.string.wishlist_quick_check))
                }
            }
        }
    }

    savingsDialogFor?.let { wish ->
        AddSavingsDialog(
            remaining = (wish.targetAmount - WishPlan.reserved(wish)).coerceAtLeast(0.0),
            onDismiss = { savingsDialogFor = null },
            onConfirm = { amount ->
                onAddSavings(wish.id, amount)
                savingsDialogFor = null
            }
        )
    }

    dateDialogFor?.let { wish ->
        ChangeDateDialog(
            wish = wish,
            onDismiss = { dateDialogFor = null },
            onConfirm = { date ->
                onSetTargetDate(wish.id, date)
                dateDialogFor = null
            }
        )
    }

    confirmBought?.let { wish ->
        ConfirmBoughtDialog(wish, onDismiss = { confirmBought = null }, onConfirm = {
            onBought(wish.id)
            confirmBought = null
        })
    }

    confirmDelete?.let { wish ->
        ConfirmDeleteDialog(wish, onDismiss = { confirmDelete = null }, onConfirm = {
            onDelete(wish.id)
            confirmDelete = null
        })
    }
}

/** "Bought it?" asked once, in a sheet (docs/DESIGN.md §6). */
@Composable
internal fun ConfirmBoughtDialog(wish: Wishlist, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    SparelyBottomSheet(isOpen = true, onDismiss = onDismiss) {
        WishSheetColumn {
            SparelySheetHeader(
                title = stringResource(R.string.wishlist_bought_title, wish.description),
                subtitle = stringResource(R.string.wishlist_bought_desc),
                icon = MaterialSymbols.CHECK_CIRCLE,
                iconTint = MaterialTheme.colorScheme.success
            )
            SheetActions(
                confirmLabel = stringResource(R.string.wishlist_bought),
                onConfirm = onConfirm,
                dismissLabel = stringResource(R.string.purchase_check_save_cancel),
                onDismiss = onDismiss
            )
        }
    }
}

/** Removing a wish, asked once in a sheet. */
@Composable
internal fun ConfirmDeleteDialog(wish: Wishlist, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    SparelyBottomSheet(isOpen = true, onDismiss = onDismiss) {
        WishSheetColumn {
            SparelySheetHeader(
                title = stringResource(R.string.wishlist_delete_title, wish.description),
                subtitle = stringResource(R.string.budget_delete_undone),
                icon = MaterialSymbols.DELETE,
                iconTint = MaterialTheme.colorScheme.error
            )
            SheetActions(
                confirmLabel = stringResource(R.string.wishlist_delete),
                onConfirm = onConfirm,
                destructive = true,
                dismissLabel = stringResource(R.string.purchase_check_save_cancel),
                onDismiss = onDismiss
            )
        }
    }
}

/** A wish sheet's padding and spacing, like the app's other sheets. */
@Composable
private fun WishSheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

// ── Pieces ──────────────────────────────────────────────────────────────────

@Composable
internal fun readinessColor(readiness: WishReadiness?): Color = when (readiness) {
    WishReadiness.READY -> MaterialTheme.colorScheme.success
    WishReadiness.COOLING_DOWN -> MaterialTheme.colorScheme.warning
    WishReadiness.SAVING_UP, null -> MaterialTheme.colorScheme.primary
    WishReadiness.NOT_AFFORDABLE -> MaterialTheme.colorScheme.critical
}

@Composable
private fun EmptyWishlist(onQuickCheck: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ExpressiveEmptyState(
            icon = MaterialSymbols.FAVORITE,
            title = stringResource(R.string.wishlist_empty_title),
            description = stringResource(R.string.wishlist_empty),
            action = {
                SparelyButton(
                    onClick = onQuickCheck,
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 20.dp) }
                ) {
                    Text(stringResource(R.string.wishlist_start_check))
                }
            }
        )
    }
}

/**
 * The page's headline (docs/DESIGN.md §3): what the wishes add up to, what's set aside for them
 * with its bar, and pills for where they stand and what's held back from spending.
 */
@Composable
private fun SummaryCard(statuses: List<WishStatus>, heldBack: Double) {
    val ready = statuses.count { it.readiness == WishReadiness.READY }
    val cooling = statuses.count { it.readiness == WishReadiness.COOLING_DOWN }
    val saving = statuses.count { it.readiness == WishReadiness.SAVING_UP }
    val blocked = statuses.count { it.readiness == WishReadiness.NOT_AFFORDABLE }
    val total = statuses.sumOf { it.wish.targetAmount }
    val saved = statuses.sumOf { it.reserved }

    val colors = MaterialTheme.colorScheme
    val ink = colors.onPrimaryContainer
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = ExpressiveShapes.large,
        color = colors.primaryContainer,
        contentColor = ink
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(
                    text = stringResource(R.string.wishlist_total_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = ink.copy(alpha = 0.8f)
                )
                Text(
                    text = total.formatCurrency(decimals = 0),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.wishlist_saved_short, saved.formatCurrency(decimals = 0)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink.copy(alpha = 0.8f)
                )
            }
            if (total > 0) {
                MarkedBar(progress = (saved / total).toFloat(), mark = null, color = ink, height = 10.dp)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ready > 0) HeroPill(MaterialSymbols.CHECK_CIRCLE, stringResource(R.string.wishlist_count_ready, ready))
                if (cooling > 0) HeroPill(MaterialSymbols.SCHEDULE, stringResource(R.string.wishlist_count_cooling, cooling))
                if (saving > 0) HeroPill(MaterialSymbols.SAVINGS, stringResource(R.string.wishlist_count_saving, saving))
                if (blocked > 0) HeroPill(MaterialSymbols.BLOCK, stringResource(R.string.wishlist_count_blocked, blocked))
                if (heldBack > 0.005) HeroPill(MaterialSymbols.SECURITY, stringResource(R.string.wish_pill_held, heldBack.formatCurrency(decimals = 0)))
            }
        }
    }
}

/**
 * One wish: its status in words and colour, the price with what's set aside under it, a bar, the
 * plan when it has a date, and ＋ to set money aside. The "still want it?" question shows here
 * when it's due, since it's waiting on an answer.
 */
@Composable
private fun WishRow(
    status: WishStatus,
    onOpen: () -> Unit,
    onAddSavings: () -> Unit,
    onStillWant: () -> Unit,
    onNotAnymore: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val wish = status.wish
    val readiness = status.readiness
    val color = readinessColor(readiness)
    val cooldownDays = cooldownDaysLeft(wish)
    val statusLabel = readinessLabel(readiness, cooldownDays)
    val monthly = WishPlan.monthlyAmount(wish)
    val daysLeft = WishPlan.daysLeft(wish)
    val plan = wish.targetDate?.takeIf { monthly != null && daysLeft != null && daysLeft > 0 }?.let { date ->
        stringResource(R.string.wish_row_plan, monthly!!.formatCurrency(decimals = 0), formatTargetDate(date))
    }

    Surface(onClick = onOpen, modifier = modifier.containerTransform("wish-${wish.id}", shape).fillMaxWidth(), shape = shape, color = colors.surfaceContainer) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(icon = MaterialSymbols.SHOPPING_BAG, contentDescription = null, size = 22.dp, tint = color)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = wish.description,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val line = listOfNotNull(statusLabel, plan).joinToString(" · ")
                    if (line.isNotEmpty()) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (statusLabel != null) color else colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = wish.targetAmount.formatCurrency(decimals = 0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.wishlist_saved_short, status.reserved.formatCurrency(decimals = 0)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                if (status.progress < 1f) {
                    IconButton(onClick = onAddSavings) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.ADD,
                            contentDescription = stringResource(R.string.wishlist_add_savings),
                            tint = colors.primary
                        )
                    }
                }
            }
            MarkedBar(
                progress = status.progress,
                mark = null,
                color = color,
                modifier = Modifier.padding(start = 58.dp, end = 8.dp)
            )
            if (wish.needsDecision()) {
                Box(modifier = Modifier.padding(end = 8.dp)) {
                    DecisionBlock(daysLeft = cooldownDays, onStillWant = onStillWant, onNotAnymore = onNotAnymore)
                }
            }
        }
    }
}

/**
 * "Do you still want it?" for the last days of the reflection time and after it. Answering early
 * is deliberate: someone watching for a sale can decide the day it happens.
 */
@Composable
internal fun DecisionBlock(daysLeft: Int, onStillWant: () -> Unit, onNotAnymore: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = stringResource(R.string.wishlist_decision_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (daysLeft > 0) {
                    stringResource(R.string.wishlist_decision_early, daysLeft)
                } else {
                    stringResource(R.string.wishlist_decision_due)
                },
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SparelyButton(onClick = onStillWant, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.wishlist_decision_yes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SparelyOutlinedButton(onClick = onNotAnymore, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.wishlist_decision_no), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun InsightRow(icon: Int, tint: Color, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
    ) {
        MaterialSymbolIcon(
            icon = icon,
            contentDescription = null,
            size = 18.dp,
            tint = tint,
            modifier = Modifier.padding(top = 1.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

/** The "hold money back until this date" plan, or a prompt to set one. */
@Composable
internal fun PlanRow(status: WishStatus, onChangeDate: () -> Unit) {
    val wish = status.wish
    val target = wish.targetDate
    val monthly = WishPlan.monthlyAmount(wish)
    val daysLeft = WishPlan.daysLeft(wish)

    if (target == null || monthly == null || daysLeft == null) {
        SparelyTextButton(
            onClick = onChangeDate,
            icon = { MaterialSymbolIcon(icon = MaterialSymbols.SCHEDULE, contentDescription = null, size = 18.dp) }
        ) {
            Text(stringResource(R.string.wishlist_set_date))
        }
        return
    }

    val surplus = status.result?.monthlySurplus
    val tooMuch = daysLeft > 0 && surplus != null && monthly > surplus + 0.5
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs)) {
        InsightRow(
            icon = MaterialSymbols.SCHEDULE,
            tint = MaterialTheme.colorScheme.primary,
            text = if (daysLeft > 0) {
                stringResource(R.string.wishlist_plan_monthly, monthly.formatCurrency(decimals = 0), formatTargetDate(target), daysLeft)
            } else {
                stringResource(R.string.wishlist_plan_due, formatTargetDate(target))
            }
        )
        if (tooMuch) {
            InsightRow(
                icon = MaterialSymbols.WARNING,
                tint = MaterialTheme.colorScheme.warning,
                text = stringResource(R.string.wishlist_plan_too_much, surplus!!.coerceAtLeast(0.0).formatCurrency(decimals = 0))
            )
        }
        SparelyTextButton(onClick = onChangeDate) { Text(stringResource(R.string.wishlist_change_date)) }
    }
}

/** Choosing (or clearing) the date a wish should be ready by, in a sheet. */
@Composable
internal fun ChangeDateDialog(wish: Wishlist, onDismiss: () -> Unit, onConfirm: (LocalDate?) -> Unit) {
    var date by remember { mutableStateOf(wish.targetDate) }
    SparelyBottomSheet(isOpen = true, onDismiss = onDismiss) {
        WishSheetColumn {
            SparelySheetHeader(
                title = wish.description,
                subtitle = stringResource(R.string.wishlist_date_hint),
                icon = MaterialSymbols.SCHEDULE
            )
            TargetDatePicker(selectedDate = date, onSelect = { date = it })
            SheetActions(
                confirmLabel = stringResource(R.string.purchase_check_save_confirm),
                onConfirm = { onConfirm(date) },
                confirmEnabled = date != wish.targetDate,
                dismissLabel = stringResource(R.string.purchase_check_save_cancel),
                onDismiss = onDismiss
            )
        }
    }
}

/** Setting money aside for a wish: the amount large, with what's still to go under it. */
@Composable
internal fun AddSavingsDialog(remaining: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val amount = text.toSafeDouble()?.takeIf { it > 0 }
    val save = { amount?.let(onConfirm) ?: Unit }
    SparelyBottomSheet(isOpen = true, onDismiss = onDismiss) {
        WishSheetColumn {
            SparelySheetHeader(title = stringResource(R.string.wishlist_add_savings), icon = MaterialSymbols.SAVINGS)
            SheetAmountField(
                value = text,
                onValueChange = { text = it.filterAmountExpressionInput() },
                label = stringResource(R.string.wishlist_amount_label),
                supportingText = stringResource(R.string.wishlist_remaining, remaining.formatCurrency(decimals = 0)),
                onImeAction = save
            )
            SheetActions(
                confirmLabel = stringResource(R.string.purchase_check_save_confirm),
                onConfirm = save,
                confirmEnabled = amount != null,
                dismissLabel = stringResource(R.string.purchase_check_save_cancel),
                onDismiss = onDismiss
            )
        }
    }
}
