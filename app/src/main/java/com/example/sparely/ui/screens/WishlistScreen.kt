package com.example.sparely.ui.screens

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
import androidx.compose.foundation.lazy.items
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
import com.example.sparely.ui.components.SparelyAlertDialog
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
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.utils.toSafeDouble
import com.sparely.app.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** A saved wish together with how it stands against the user's finances today. */
private data class WishStatus(
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
    onNotAnymore: (wishlistId: Long) -> Unit
) {
    val spacing = MaterialTheme.spacing
    val input = uiState.forecastInput
    val context = remember(uiState) { uiState.toPurchaseContext() }

    // Closest to buying first, so the most actionable wishes lead the list.
    val statuses = remember(uiState.wishlists, input, context) {
        uiState.wishlists
            .map { wish ->
                val reserved = WishPlan.reserved(wish)
                // The money held back for this wish must not count against buying it.
                val result = input?.let {
                    val others = (it.earmarkedMoney - reserved).coerceAtLeast(0.0)
                    PurchaseAffordabilityEngine.evaluate(it.copy(earmarkedMoney = others), wish.targetAmount, context)
                }
                WishStatus(wish, reserved, result, result?.let { r -> WishReadiness.assess(wish, r.verdict, reserved = reserved) })
            }
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
            modifier = Modifier.fillMaxSize(),
            // Content padding (not Modifier.padding) so cards don't clip while scrolling; the bottom
            // gap keeps the last card clear of the floating action button.
            contentPadding = PaddingValues(start = spacing.md, end = spacing.md, top = spacing.xs, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            item(key = "summary") { SummaryCard(statuses, heldBack = uiState.wishlistReserved) }

            items(statuses, key = { it.wish.id }) { status ->
                WishCard(
                    status = status,
                    onAddSavings = { savingsDialogFor = status.wish },
                    onChangeDate = { dateDialogFor = status.wish },
                    onStillWant = { onStillWant(status.wish.id) },
                    onNotAnymore = { onNotAnymore(status.wish.id) },
                    onBought = { confirmBought = status.wish },
                    onDelete = { confirmDelete = status.wish }
                )
            }

            item(key = "quick_check") {
                SparelyOutlinedButton(
                    onClick = onQuickCheck,
                    modifier = Modifier.fillMaxWidth(),
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
        SparelyAlertDialog(
            onDismissRequest = { confirmBought = null },
            title = { Text(stringResource(R.string.wishlist_bought_title, wish.description)) },
            text = { Text(stringResource(R.string.wishlist_bought_desc)) },
            confirmButton = {
                SparelyTextButton(onClick = {
                    onBought(wish.id)
                    confirmBought = null
                }) { Text(stringResource(R.string.wishlist_bought)) }
            },
            dismissButton = {
                SparelyTextButton(onClick = { confirmBought = null }) { Text(stringResource(R.string.purchase_check_save_cancel)) }
            }
        )
    }

    confirmDelete?.let { wish ->
        SparelyAlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.wishlist_delete_title, wish.description)) },
            confirmButton = {
                SparelyTextButton(
                    onClick = {
                        onDelete(wish.id)
                        confirmDelete = null
                    },
                    contentColor = MaterialTheme.colorScheme.critical
                ) { Text(stringResource(R.string.wishlist_delete)) }
            },
            dismissButton = {
                SparelyTextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.purchase_check_save_cancel)) }
            }
        )
    }
}

// ── Pieces ──────────────────────────────────────────────────────────────────

@Composable
private fun readinessColor(readiness: WishReadiness?): Color = when (readiness) {
    WishReadiness.READY -> MaterialTheme.colorScheme.success
    WishReadiness.COOLING_DOWN -> MaterialTheme.colorScheme.warning
    WishReadiness.SAVING_UP, null -> MaterialTheme.colorScheme.primary
    WishReadiness.NOT_AFFORDABLE -> MaterialTheme.colorScheme.critical
}

@Composable
private fun EmptyWishlist(onQuickCheck: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.FAVORITE,
                        contentDescription = null,
                        size = 48.dp,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Text(
                text = stringResource(R.string.wishlist_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.wishlist_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            SparelyButton(
                onClick = onQuickCheck,
                modifier = Modifier.padding(top = spacing.xs),
                icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 20.dp) }
            ) {
                Text(stringResource(R.string.wishlist_start_check))
            }
        }
    }
}

@Composable
private fun SummaryCard(statuses: List<WishStatus>, heldBack: Double) {
    val spacing = MaterialTheme.spacing
    val ready = statuses.count { it.readiness == WishReadiness.READY }
    val cooling = statuses.count { it.readiness == WishReadiness.COOLING_DOWN }
    val saving = statuses.count { it.readiness == WishReadiness.SAVING_UP }
    val blocked = statuses.count { it.readiness == WishReadiness.NOT_AFFORDABLE }
    val total = statuses.sumOf { it.wish.targetAmount }
    val saved = statuses.sumOf { it.reserved }

    ExpressiveCard(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.wishlist_total_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = total.formatCurrency(decimals = 0),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = stringResource(R.string.wishlist_saved_short, saved.formatCurrency(decimals = 0)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val parts = buildList {
                if (ready > 0) add(stringResource(R.string.wishlist_count_ready, ready))
                if (cooling > 0) add(stringResource(R.string.wishlist_count_cooling, cooling))
                if (saving > 0) add(stringResource(R.string.wishlist_count_saving, saving))
                if (blocked > 0) add(stringResource(R.string.wishlist_count_blocked, blocked))
            }
            if (parts.isNotEmpty()) {
                Text(
                    text = parts.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            if (heldBack > 0.005) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.SECURITY,
                        contentDescription = null,
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    Text(
                        text = stringResource(R.string.wishlist_held_back, heldBack.formatCurrency(decimals = 0)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun WishCard(
    status: WishStatus,
    onAddSavings: () -> Unit,
    onChangeDate: () -> Unit,
    onStillWant: () -> Unit,
    onNotAnymore: () -> Unit,
    onBought: () -> Unit,
    onDelete: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val wish = status.wish
    val readiness = status.readiness
    val color = readinessColor(readiness)

    val now = LocalDateTime.now()
    val cooldownDays = wish.cooldownExpiresAt
        ?.takeIf { it.isAfter(now) }
        ?.let { ((Duration.between(now, it).toHours() + 23) / 24).toInt() } // round up to whole days
        ?: 0

    val statusLabel = when (readiness) {
        WishReadiness.READY -> stringResource(R.string.wishlist_ready)
        WishReadiness.COOLING_DOWN -> stringResource(R.string.wishlist_cooling, cooldownDays.coerceAtLeast(1))
        WishReadiness.SAVING_UP -> stringResource(R.string.wishlist_saving_up)
        WishReadiness.NOT_AFFORDABLE -> stringResource(R.string.wishlist_not_affordable)
        null -> null
    }

    // The most useful reason for where it stands today: the save-up plan, or the first problem.
    val headline = status.result?.findings?.let { findings ->
        findings.firstOrNull { it.kind == FindingKind.SAVE_UP_PLAN }
            ?: findings.firstOrNull { it.impact == PurchaseAffordabilityEngine.Impact.PROBLEM }
            ?: findings.firstOrNull { it.kind == FindingKind.NO_MONTHLY_SURPLUS }
    }
    // With a target date the plan is already chosen, so the generic "save for N months" tip is redundant.
    val showHeadline = headline != null && readiness != WishReadiness.READY &&
        !(wish.targetDate != null && headline.kind == FindingKind.SAVE_UP_PLAN)

    ExpressiveCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                IconBadge(icon = MaterialSymbols.SHOPPING_BAG, color = color, size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = wish.description,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (statusLabel != null) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
                Text(
                    text = wish.targetAmount.formatCurrency(decimals = 0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { status.progress },
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = stringResource(
                            R.string.wishlist_saved_of,
                            status.reserved.formatCurrency(decimals = 0),
                            wish.targetAmount.formatCurrency(decimals = 0)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = status.progress.toDouble().formatPercent(0),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }

            PlanRow(status = status, onChangeDate = onChangeDate)

            if (showHeadline && headline != null) {
                InsightRow(icon = MaterialSymbols.LIGHTBULB, tint = color, text = findingText(headline))
            }

            if (wish.needsDecision()) {
                DecisionBlock(
                    daysLeft = cooldownDays,
                    onStillWant = onStillWant,
                    onNotAnymore = onNotAnymore
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SparelyTonalButton(onClick = onAddSavings, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.wishlist_add_savings), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (readiness == WishReadiness.READY) {
                    SparelyButton(onClick = onBought, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.wishlist_bought), maxLines = 1)
                    }
                } else {
                    SparelyOutlinedButton(onClick = onBought, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.wishlist_bought), maxLines = 1)
                    }
                }
                IconButton(onClick = onDelete) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.DELETE,
                        contentDescription = stringResource(R.string.wishlist_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
private fun DecisionBlock(daysLeft: Int, onStillWant: () -> Unit, onNotAnymore: () -> Unit) {
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
private fun InsightRow(icon: Int, tint: Color, text: String) {
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
private fun PlanRow(status: WishStatus, onChangeDate: () -> Unit) {
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

@Composable
private fun ChangeDateDialog(wish: Wishlist, onDismiss: () -> Unit, onConfirm: (LocalDate?) -> Unit) {
    var date by remember { mutableStateOf(wish.targetDate) }
    SparelyAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(wish.description) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                TargetDatePicker(selectedDate = date, onSelect = { date = it })
                Text(
                    text = stringResource(R.string.wishlist_date_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            SparelyTextButton(onClick = { onConfirm(date) }, enabled = date != wish.targetDate) {
                Text(stringResource(R.string.purchase_check_save_confirm))
            }
        },
        dismissButton = {
            SparelyTextButton(onClick = onDismiss) { Text(stringResource(R.string.purchase_check_save_cancel)) }
        }
    )
}

@Composable
private fun AddSavingsDialog(remaining: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val amount = text.toSafeDouble()?.takeIf { it > 0 }
    SparelyAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wishlist_add_savings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                SparelyTextField(
                    value = text,
                    onValueChange = { text = it.filterCurrencyInput() },
                    label = { Text(stringResource(R.string.wishlist_amount_label)) },
                    prefix = { Text("$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text(
                    text = stringResource(R.string.wishlist_remaining, remaining.formatCurrency(decimals = 0)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            SparelyTextButton(onClick = { amount?.let(onConfirm) }, enabled = amount != null) {
                Text(stringResource(R.string.purchase_check_save_confirm))
            }
        },
        dismissButton = {
            SparelyTextButton(onClick = onDismiss) { Text(stringResource(R.string.purchase_check_save_cancel)) }
        }
    )
}
