package com.example.sparely.ui.screens

import com.example.sparely.ui.components.containerTransform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.logic.WishReadiness
import com.example.sparely.domain.model.Wishlist
import com.example.sparely.domain.model.needsDecision
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyOutlinedButton
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.state.SparelyUiState
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.spacing
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.sparely.app.R
import java.time.LocalDate

/**
 * Everything about one saved wish: how much is already set aside, what buying it would do to the
 * user's money right now (counting what is already spoken for), and why.
 */
@Composable
fun WishDetailScreen(
    uiState: SparelyUiState,
    wishId: Long,
    onNavigateBack: () -> Unit,
    onAddSavings: (wishlistId: Long, amount: Double) -> Unit,
    onSetTargetDate: (wishlistId: Long, date: LocalDate?) -> Unit,
    onStillWant: (wishlistId: Long) -> Unit,
    onNotAnymore: (wishlistId: Long) -> Unit,
    onBought: (wishlistId: Long) -> Unit,
    onDelete: (wishlistId: Long) -> Unit
) {
    val wish = uiState.wishlists.find { it.id == wishId }

    // Bought, removed or archived from here (or elsewhere): there is nothing left to show.
    if (wish == null) {
        LaunchedEffect(Unit) { onNavigateBack() }
        return
    }

    val spacing = MaterialTheme.spacing
    val input = uiState.forecastInput
    val context = remember(uiState) { uiState.toPurchaseContext() }
    val status = remember(wish, input, context) { buildWishStatus(wish, input, context) }
    val result = status.result
    val color = readinessColor(status.readiness)
    val cooldownDays = cooldownDaysLeft(wish)

    var showSavings by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showBought by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .containerTransform("wish-$wishId")
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = spacing.md, end = spacing.md, top = spacing.xs, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        item(key = "header") {
            ExpressiveCard(containerColor = color.copy(alpha = 0.10f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    IconBadge(icon = MaterialSymbols.SHOPPING_BAG, color = color, size = 56.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = wish.description,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        readinessLabel(status.readiness, cooldownDays)?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                    }
                    Text(
                        text = wish.targetAmount.formatCurrency(decimals = 0),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item(key = "set_aside_title") { SectionTitle(stringResource(R.string.wishlist_detail_set_aside)) }
        item(key = "set_aside") {
            SetAsideCard(
                status = status,
                color = color,
                onAddSavings = { showSavings = true },
                onChangeDate = { showDate = true }
            )
        }

        if (wish.needsDecision()) {
            item(key = "decision") {
                DecisionBlock(
                    daysLeft = cooldownDays,
                    onStillWant = { onStillWant(wish.id) },
                    onNotAnymore = { onNotAnymore(wish.id) }
                )
            }
        }

        if (result != null && input != null) {
            item(key = "verdict") { VerdictCard(result) }

            item(key = "ai_explain") {
                val symbol = uiState.settings.regionalSettings.getCurrencySymbol()
                val aiLanguage = com.example.sparely.ui.components.LocalOnDeviceAi.current.language
                val aiFacts = remember(wish, status, symbol, aiLanguage) {
                    com.example.sparely.ai.FinanceFacts.wish(
                        name = wish.description,
                        price = wish.targetAmount,
                        setAside = status.reserved,
                        targetDate = wish.targetDate,
                        reallyNeeded = wish.isReallyNeeded,
                        cooldownDaysLeft = cooldownDays,
                        result = result,
                        lang = aiLanguage
                    ) { aiLanguage.money(it, symbol) }
                }
                com.example.sparely.ui.components.OnDeviceAiSection(
                    title = stringResource(R.string.ai_wish_title),
                    facts = aiFacts,
                    task = com.example.sparely.ai.AiTask.WISH.text(aiLanguage),
                    askSlot = "wish-${wish.id}",
                    askPlaceholder = stringResource(R.string.ai_wish_ask_placeholder)
                )
            }

            item(key = "effect_title") { SectionTitle(stringResource(R.string.wishlist_detail_effect)) }
            item(key = "effect_note") {
                Text(
                    text = stringResource(R.string.wishlist_detail_effect_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item(key = "comparison") { ComparisonCard(result) }

            if (input.committedMoney - status.reserved >= 0.5) {
                item(key = "commitments") {
                    CommitmentsCard(input = input, ownReserve = status.reserved, wishesLabelRes = R.string.commit_wishes_other)
                }
            }

            item(key = "why_title") { SectionTitle(stringResource(R.string.purchase_check_why)) }
            item(key = "findings") { FindingsCard(result.findings) }
        } else {
            item(key = "loading") { HintCard(waitingForData = true) }
        }

        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                if (status.readiness == WishReadiness.READY) {
                    SparelyButton(onClick = { showBought = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.wishlist_bought))
                    }
                } else {
                    SparelyOutlinedButton(onClick = { showBought = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.wishlist_bought))
                    }
                }
                SparelyTextButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = MaterialTheme.colorScheme.critical
                ) {
                    Text(stringResource(R.string.wishlist_delete))
                }
            }
        }
    }

    if (showSavings) {
        AddSavingsDialog(
            remaining = (wish.targetAmount - status.reserved).coerceAtLeast(0.0),
            onDismiss = { showSavings = false },
            onConfirm = {
                onAddSavings(wish.id, it)
                showSavings = false
            }
        )
    }
    if (showDate) {
        ChangeDateDialog(
            wish = wish,
            onDismiss = { showDate = false },
            onConfirm = {
                onSetTargetDate(wish.id, it)
                showDate = false
            }
        )
    }
    if (showBought) {
        ConfirmBoughtDialog(wish, onDismiss = { showBought = false }, onConfirm = {
            showBought = false
            onBought(wish.id)
        })
    }
    if (showDelete) {
        ConfirmDeleteDialog(wish, onDismiss = { showDelete = false }, onConfirm = {
            showDelete = false
            onDelete(wish.id)
        })
    }
}

/** How much is already set aside, what is left to find, and the plan to get there. */
@Composable
private fun SetAsideCard(
    status: WishStatus,
    color: androidx.compose.ui.graphics.Color,
    onAddSavings: () -> Unit,
    onChangeDate: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val wish: Wishlist = status.wish
    val toFind = (wish.targetAmount - status.reserved).coerceAtLeast(0.0)

    ExpressiveCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.wishlist_detail_set_aside),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = status.reserved.formatCurrency(decimals = 0),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.wishlist_detail_still_to_find),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = toFind.formatCurrency(decimals = 0),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LinearProgressIndicator(
                progress = { status.progress },
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
            )
            Text(
                text = status.progress.toDouble().formatPercent(0),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color
            )

            if (status.reserved >= 0.5) {
                InsightRow(
                    icon = MaterialSymbols.INFO,
                    tint = MaterialTheme.colorScheme.primary,
                    text = stringResource(
                        R.string.wishlist_detail_held_explain,
                        status.reserved.formatCurrency(decimals = 0),
                        toFind.formatCurrency(decimals = 0)
                    )
                )
            }

            PlanRow(status = status, onChangeDate = onChangeDate)

            SparelyTonalButton(onClick = onAddSavings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wishlist_add_savings))
            }
        }
    }
}
