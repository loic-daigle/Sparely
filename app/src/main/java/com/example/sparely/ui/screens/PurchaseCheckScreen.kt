package com.example.sparely.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.logic.PurchaseAffordabilityEngine
import com.example.sparely.domain.logic.PurchaseAffordabilityEngine.FindingKind
import com.example.sparely.domain.logic.PurchaseAffordabilityEngine.Impact
import com.example.sparely.domain.logic.PurchaseAffordabilityEngine.Verdict
import com.example.sparely.domain.logic.WishPlan
import com.example.sparely.ui.components.SparelyAmountTextField
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.SegmentGap
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.components.SparelyAlertDialog
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyChip
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyTextField
import com.example.sparely.ui.state.SparelyUiState
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.spacing
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.warning
import com.example.sparely.ui.utils.filterAmountExpressionInput
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import com.example.sparely.ui.utils.toInputString
import com.example.sparely.ui.utils.toSafeDouble
import com.sparely.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Every saved wish gets at least this long to sit before buying. */
private const val MIN_COOLDOWN_DAYS = 3

@Composable
fun PurchaseCheckScreen(
    uiState: SparelyUiState,
    onOpenWishlist: () -> Unit = {},
    /** name, real cost, days to sleep on it, whether it is a real need, and the date to buy it by (if any) */
    onSaveToWishlist: (String, Double, Int, Boolean, LocalDate?) -> Unit = { _, _, _, _, _ -> }
) {
    val spacing = MaterialTheme.spacing
    val input = uiState.forecastInput

    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var priceText by rememberSaveable { mutableStateOf("") }
    val sticker = priceText.toSafeDouble()?.takeIf { it > 0 }

    // Sticker prices usually leave out sales tax, delivery and extras; the real cost includes them.
    val defaultTaxPercent = remember(uiState.settings.regionalSettings) {
        val rate = uiState.settings.regionalSettings.getCountryConfig()?.taxConfig?.salesTaxRate ?: 0.0
        (rate * 100).toInputString(3)
    }
    var addTax by rememberSaveable { mutableStateOf(true) }
    var taxPercentText by rememberSaveable(defaultTaxPercent) { mutableStateOf(defaultTaxPercent) }
    var extrasText by rememberSaveable { mutableStateOf("") }
    val taxRate = if (addTax) (taxPercentText.toSafeDouble() ?: 0.0).coerceIn(0.0, 100.0) / 100.0 else 0.0
    val extras = extrasText.toSafeDouble()?.coerceAtLeast(0.0) ?: 0.0
    val realCost = sticker?.let { it * (1 + taxRate) + extras }

    // Re-run only when the real cost or the underlying numbers change.
    val context = remember(uiState) { uiState.toPurchaseContext() }
    val result = remember(input, realCost, context) {
        if (input != null && realCost != null) PurchaseAffordabilityEngine.evaluate(input, realCost, context) else null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        // Content padding (not Modifier.padding) so cards don't clip while scrolling; the bottom
        // gap keeps the last card clear of the floating action button.
        contentPadding = PaddingValues(start = spacing.md, end = spacing.md, top = spacing.xs, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        item(key = "wishlist_link") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WishlistPill(count = uiState.wishlists.size, onClick = onOpenWishlist)
            }
        }

        item(key = "input") {
            InputCard(
                priceText = priceText,
                onPriceChange = { priceText = it.filterAmountExpressionInput() },
                enabled = input != null,
                addTax = addTax,
                onAddTaxChange = { addTax = it },
                taxPercentText = taxPercentText,
                onTaxPercentChange = { taxPercentText = it.filterCurrencyInput() },
                extrasText = extrasText,
                onExtrasChange = { extrasText = it.filterAmountExpressionInput() },
                realCost = realCost.takeIf { sticker != null && it != null && it - sticker >= 0.005 }
            )
        }

        if (result == null) {
            item(key = "hint") { HintCard(waitingForData = input == null) }
        } else {
            item(key = "verdict") { VerdictCard(result) }

            item(key = "ai_explain") {
                val symbol = uiState.settings.regionalSettings.getCurrencySymbol()
                val aiLanguage = com.example.sparely.ui.components.LocalOnDeviceAi.current.language
                val aiFacts = remember(result, symbol, aiLanguage) {
                    com.example.sparely.ai.FinanceFacts.purchase(result, aiLanguage) { aiLanguage.money(it, symbol) }
                }
                com.example.sparely.ui.components.OnDeviceAiSection(
                    title = stringResource(R.string.ai_purchase_title),
                    facts = aiFacts,
                    task = com.example.sparely.ai.AiTask.PURCHASE.text(aiLanguage),
                    askSlot = "purchase",
                    askPlaceholder = stringResource(R.string.ai_purchase_ask_placeholder)
                )
            }

            item(key = "impact_title") { SectionTitle(stringResource(R.string.purchase_check_section_impact)) }
            item(key = "comparison") { ComparisonCard(result) }

            if (input != null && input.committedMoney >= 0.5) {
                item(key = "commitments") {
                    CommitmentsCard(input = input, ownReserve = 0.0, wishesLabelRes = R.string.commit_wishes_all)
                }
            }

            item(key = "why_title") { SectionTitle(stringResource(R.string.purchase_check_why)) }
            item(key = "findings") { FindingsCard(result.findings) }

            item(key = "save") {
                SparelyButton(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        MaterialSymbolIcon(icon = MaterialSymbols.FAVORITE, contentDescription = null, size = 20.dp)
                    }
                ) {
                    Text(stringResource(R.string.purchase_check_save_to_wishlist))
                }
            }
        }
    }

    if (showSaveDialog && result != null && realCost != null) {
        val suggestedWait = result.findings
            .firstOrNull { it.kind == FindingKind.SUGGESTED_WAIT }?.primary?.toInt() ?: 0
        val cooldownDays = maxOf(MIN_COOLDOWN_DAYS, suggestedWait)
        // If the check came up with a save-up plan, start from the closest date that fits it.
        val planMonths = result.findings
            .firstOrNull { it.kind == FindingKind.SAVE_UP_PLAN }?.primary?.toInt()
        SaveToWishlistDialog(
            realCost = realCost,
            cooldownDays = cooldownDays,
            suggestedMonths = planMonths?.let { m -> TARGET_MONTH_OPTIONS.firstOrNull { it >= m } ?: TARGET_MONTH_OPTIONS.last() },
            onDismiss = { showSaveDialog = false },
            onSave = { name, isNeed, targetDate ->
                onSaveToWishlist(name, realCost, cooldownDays, isNeed, targetDate)
                showSaveDialog = false
                onOpenWishlist()
            }
        )
    }
}

internal fun SparelyUiState.toPurchaseContext() = PurchaseAffordabilityEngine.Context(
    vaults = smartVaults,
    settings = settings,
    paymentMethods = paymentMethods,
    emergencyFund = emergencyFundGoal,
    incomeStability = settings.paySchedule.determineStability(),
    healthScore = financialHealthScore?.overallScore,
    healthLevel = financialHealthScore?.healthLevel,
    budgetHealth = budgetSummary?.overallHealth
)

// ── Building blocks ─────────────────────────────────────────────────────────

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(start = MaterialTheme.spacing.xxs, top = MaterialTheme.spacing.sm)
            .semantics { heading() }
    )
}

@Composable
private fun WishlistPill(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MaterialSymbolIcon(icon = MaterialSymbols.FAVORITE, contentDescription = null, size = 18.dp)
            Text(
                text = stringResource(R.string.purchase_check_open_wishlist, count),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InputCard(
    priceText: String,
    onPriceChange: (String) -> Unit,
    enabled: Boolean,
    addTax: Boolean,
    onAddTaxChange: (Boolean) -> Unit,
    taxPercentText: String,
    onTaxPercentChange: (String) -> Unit,
    extrasText: String,
    onExtrasChange: (String) -> Unit,
    realCost: Double?
) {
    val spacing = MaterialTheme.spacing
    ExpressiveCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                text = stringResource(R.string.purchase_check_how_much),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            SparelyAmountTextField(
                value = priceText,
                onValueChange = onPriceChange,
                label = { Text(stringResource(R.string.purchase_check_price_label)) },
                prefix = { Text("$") },
                enabled = enabled
            )

            SparelyChip(
                selected = addTax,
                onClick = { onAddTaxChange(!addTax) },
                label = { Text(stringResource(R.string.purchase_check_add_tax)) },
                leadingIcon = if (addTax) {
                    { MaterialSymbolIcon(icon = MaterialSymbols.CHECK, contentDescription = null, size = 18.dp) }
                } else null
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                if (addTax) {
                    SparelyTextField(
                        value = taxPercentText,
                        onValueChange = onTaxPercentChange,
                        label = { Text(stringResource(R.string.purchase_check_tax_rate_label)) },
                        trailingIcon = { Text("%", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                SparelyAmountTextField(
                    value = extrasText,
                    onValueChange = onExtrasChange,
                    label = { Text(stringResource(R.string.purchase_check_extras_label), maxLines = 1) },
                    prefix = { Text("$") },
                    showCalculatorKeys = false,
                    modifier = Modifier.weight(1f)
                )
            }

            if (realCost != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.purchase_check_real_cost_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = realCost.formatCurrency(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Money that is still in the account but already spoken for, which is why it is not counted as
 * available. [ownReserve] is the part of the wishlist reserve that belongs to the item being looked at.
 */
@Composable
internal fun CommitmentsCard(
    input: com.example.sparely.domain.logic.CashflowEngine.ForecastInput,
    ownReserve: Double,
    wishesLabelRes: Int
) {
    val spacing = MaterialTheme.spacing
    val otherWishes = (input.earmarkedMoney - ownReserve).coerceAtLeast(0.0)
    val rows = listOf(
        R.string.commit_cards to input.creditCardBalance.coerceAtLeast(0.0),
        R.string.commit_pending to input.pendingTransfers.coerceAtLeast(0.0),
        wishesLabelRes to otherWishes
    ).filter { it.second >= 0.5 }
    val total = rows.sumOf { it.second }

    ExpressiveCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = stringResource(R.string.commit_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            rows.forEach { (label, amount) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                    Text(amount.formatCurrency(decimals = 0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.commit_total), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(total.formatCurrency(decimals = 0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                text = stringResource(R.string.commit_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun HintCard(waitingForData: Boolean) {
    val spacing = MaterialTheme.spacing
    ExpressiveCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            IconBadge(icon = MaterialSymbols.LIGHTBULB, color = MaterialTheme.colorScheme.primary, size = 44.dp)
            Text(
                text = stringResource(
                    if (waitingForData) R.string.purchase_check_no_data else R.string.purchase_check_intro
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** A rounded tile holding an icon, tinted with [color]. Used for statuses across both screens. */
@Composable
internal fun IconBadge(icon: Int, color: Color, size: androidx.compose.ui.unit.Dp = 40.dp) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.14f),
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            MaterialSymbolIcon(icon = icon, contentDescription = null, size = size * 0.55f, tint = color)
        }
    }
}

private data class VerdictStyle(val color: Color, val titleRes: Int, val descRes: Int, val icon: Int)

@Composable
internal fun verdictColor(verdict: Verdict): Color = when (verdict) {
    Verdict.GO_AHEAD -> MaterialTheme.colorScheme.success
    Verdict.WAIT -> MaterialTheme.colorScheme.warning
    Verdict.AVOID -> MaterialTheme.colorScheme.critical
}

@Composable
internal fun VerdictCard(result: PurchaseAffordabilityEngine.Result) {
    val color = verdictColor(result.verdict)
    val style = when (result.verdict) {
        Verdict.GO_AHEAD -> VerdictStyle(color, R.string.purchase_check_verdict_go, R.string.purchase_check_verdict_go_desc, MaterialSymbols.CHECK_CIRCLE)
        Verdict.WAIT -> VerdictStyle(color, R.string.purchase_check_verdict_wait, R.string.purchase_check_verdict_wait_desc, MaterialSymbols.SCHEDULE)
        Verdict.AVOID -> VerdictStyle(color, R.string.purchase_check_verdict_avoid, R.string.purchase_check_verdict_avoid_desc, MaterialSymbols.BLOCK)
    }
    ExpressiveCard(containerColor = color.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
        ) {
            IconBadge(icon = style.icon, color = color, size = 56.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(style.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = stringResource(style.descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
internal fun ComparisonCard(result: PurchaseAffordabilityEngine.Result) {
    ExpressiveCard {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1.3f))
                ColumnHeader(stringResource(R.string.purchase_check_without), Modifier.weight(1f))
                ColumnHeader(stringResource(R.string.purchase_check_with), Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ComparisonRow(
                label = stringResource(R.string.purchase_check_row_safe),
                before = result.before.safeToSpend,
                after = result.after.safeToSpend
            )
            ComparisonRow(
                label = stringResource(R.string.purchase_check_row_30_days),
                before = result.before.projectedBalance30Days,
                after = result.after.projectedBalance30Days
            )
            ComparisonRow(
                label = stringResource(R.string.purchase_check_row_liquid),
                before = result.before.currentBalance,
                after = result.after.currentBalance
            )
        }
    }
}

@Composable
private fun ColumnHeader(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = modifier
    )
}

@Composable
private fun ComparisonRow(label: String, before: Double, after: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.3f))
        Text(
            text = before.formatCurrency(decimals = 0),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = after.formatCurrency(decimals = 0),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (after < 0) MaterialTheme.colorScheme.critical else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/** All reasons in one card, most serious first, so the worst news is never buried. */
@Composable
internal fun FindingsCard(findings: List<PurchaseAffordabilityEngine.Finding>) {
    val ordered = remember(findings) { findings.sortedByDescending { it.impact.ordinal } }
    // One segmented block: rounded ends, thin gaps between reasons
    Column(verticalArrangement = Arrangement.spacedBy(SegmentGap)) {
        ordered.forEachIndexed { index, finding ->
            Surface(
                shape = segmentedShape(index, ordered.size),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    FindingRow(finding)
                }
            }
        }
    }
}

@Composable
private fun FindingRow(finding: PurchaseAffordabilityEngine.Finding) {
    val (color, icon) = when (finding.impact) {
        Impact.OK -> MaterialTheme.colorScheme.success to MaterialSymbols.CHECK_CIRCLE
        Impact.CAUTION -> MaterialTheme.colorScheme.warning to MaterialSymbols.WARNING
        Impact.PROBLEM -> MaterialTheme.colorScheme.critical to MaterialSymbols.BLOCK
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
    ) {
        IconBadge(icon = icon, color = color, size = 32.dp)
        Text(
            text = findingText(finding),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(top = 5.dp)
        )
    }
}

@Composable
internal fun findingText(f: PurchaseAffordabilityEngine.Finding): String {
    val primary = f.primary
    val secondary = f.secondary
    val money = { v: Double -> v.formatCurrency(decimals = 0) }
    val months = { v: Double -> "%.1f".format(v) }
    return when (f.kind) {
        FindingKind.SHORT_OF_FUNDS -> stringResource(R.string.purchase_check_f_short, money(primary))
        FindingKind.NEGATIVE_IN_30_DAYS -> stringResource(R.string.purchase_check_f_negative, money(primary))
        FindingKind.POSITIVE_IN_30_DAYS -> stringResource(R.string.purchase_check_f_positive, money(primary), money(secondary))
        FindingKind.ESSENTIAL_BILL_UNCOVERED -> stringResource(R.string.purchase_check_f_essential, primary.toInt())
        FindingKind.LOW_BALANCE_SOON -> stringResource(R.string.purchase_check_f_low_balance, money(primary), secondary.toInt())
        FindingKind.OVER_SAFE_TO_SPEND -> stringResource(R.string.purchase_check_f_over_safe, money(primary), money(secondary))
        FindingKind.WITHIN_SAFE_TO_SPEND -> stringResource(R.string.purchase_check_f_within_safe, money(primary))
        FindingKind.BUFFER_UNDER_MONTH -> stringResource(R.string.purchase_check_f_buffer_under, months(primary))
        FindingKind.BUFFER_THIN -> stringResource(R.string.purchase_check_f_buffer_thin, months(primary))
        FindingKind.BUFFER_COMFORTABLE -> stringResource(R.string.purchase_check_f_buffer_ok, months(primary))
        FindingKind.LARGE_SHARE_OF_INCOME -> stringResource(R.string.purchase_check_f_income_share, primary.formatPercent(0))
        FindingKind.DIPS_INTO_SAVINGS ->
            stringResource(R.string.purchase_check_f_dips_savings, money(primary), secondary.formatPercent(0))
        FindingKind.SAVING_TAX_ADDED ->
            stringResource(R.string.purchase_check_f_tax_added, money(primary), money(secondary))
        FindingKind.SAVING_TAX_SKIPPED ->
            // primary = the part that would be missed, secondary = the usual full amount
            if (primary >= secondary - 0.5) {
                stringResource(R.string.purchase_check_f_tax_none, money(secondary))
            } else {
                stringResource(R.string.purchase_check_f_tax_skipped, money(primary), money(secondary))
            }
        FindingKind.SAVINGS_DEPOSITS_AT_RISK ->
            stringResource(R.string.purchase_check_f_deposits_risk, money(primary), money(secondary))
        FindingKind.CARDS_NOT_COVERED ->
            stringResource(R.string.purchase_check_f_cards_not_covered, money(primary), money(secondary.coerceAtLeast(0.0)))
        FindingKind.CARD_BALANCE_OWED -> stringResource(R.string.purchase_check_f_cards, money(primary))
        FindingKind.EMERGENCY_FUND_BELOW_TARGET ->
            stringResource(R.string.purchase_check_f_emergency, primary.formatPercent(0), money(secondary))
        FindingKind.INCOME_UNSTABLE ->
            stringResource(if (primary >= 2.0) R.string.purchase_check_f_income_variable else R.string.purchase_check_f_income_moderate)
        FindingKind.HEALTH_SCORE_LOW -> stringResource(R.string.purchase_check_f_health, primary.toInt())
        FindingKind.BUDGETS_OVER -> stringResource(R.string.purchase_check_f_budgets_over)
        FindingKind.COULD_PAY_TWICE -> stringResource(R.string.purchase_check_f_twice)
        FindingKind.SUGGESTED_WAIT -> stringResource(R.string.purchase_check_f_wait, primary.toInt())
        FindingKind.SAVE_UP_PLAN -> stringResource(R.string.purchase_check_f_plan, money(secondary), primary.toInt())
        FindingKind.NO_MONTHLY_SURPLUS -> stringResource(R.string.purchase_check_f_no_surplus)
    }
}

/** Quick choices for "when do you want to buy it?", in months from today. */
internal val TARGET_MONTH_OPTIONS = listOf(1, 3, 6, 12)

internal fun formatTargetDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
private fun monthsLabel(months: Int): String = when (months) {
    1 -> stringResource(R.string.wishlist_date_1m)
    12 -> stringResource(R.string.wishlist_date_1y)
    else -> stringResource(R.string.wishlist_date_months, months)
}

/**
 * "No date", a few presets, and a calendar for anything else. Picking a date makes the app hold
 * money back gradually until then.
 */
@Composable
internal fun TargetDatePicker(selectedDate: LocalDate?, onSelect: (LocalDate?) -> Unit) {
    var showCalendar by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val presets = TARGET_MONTH_OPTIONS.map { it to today.plusMonths(it.toLong()) }
    val isCustom = selectedDate != null && presets.none { it.second == selectedDate }

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
        Text(
            text = stringResource(R.string.wishlist_date_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)
        ) {
            SparelyChip(
                selected = selectedDate == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.wishlist_date_none)) }
            )
            presets.forEach { (months, date) ->
                SparelyChip(
                    selected = selectedDate == date,
                    onClick = { onSelect(date) },
                    label = { Text(monthsLabel(months)) }
                )
            }
            SparelyChip(
                selected = isCustom,
                onClick = { showCalendar = true },
                label = {
                    Text(
                        if (isCustom && selectedDate != null) formatTargetDate(selectedDate)
                        else stringResource(R.string.wishlist_date_pick)
                    )
                },
                leadingIcon = {
                    MaterialSymbolIcon(icon = MaterialSymbols.CALENDAR_MONTH, contentDescription = null, size = 18.dp)
                }
            )
        }
    }

    if (showCalendar) {
        CalendarDialog(
            initial = selectedDate,
            onDismiss = { showCalendar = false },
            onPick = {
                onSelect(it)
                showCalendar = false
            }
        )
    }
}

/** A calendar limited to future dates. Material date pickers speak UTC midnight milliseconds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDialog(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val tomorrow = LocalDate.now().plusDays(1)
    fun LocalDate.toMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.toDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial?.takeIf { it >= tomorrow } ?: tomorrow).toMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.toDate() >= tomorrow
            override fun isSelectableYear(year: Int) = year >= tomorrow.year
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            SparelyTextButton(
                onClick = { state.selectedDateMillis?.let { onPick(it.toDate()) } },
                enabled = state.selectedDateMillis != null
            ) { Text(stringResource(R.string.purchase_check_save_confirm)) }
        },
        dismissButton = {
            SparelyTextButton(onClick = onDismiss) { Text(stringResource(R.string.purchase_check_save_cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun SaveToWishlistDialog(
    realCost: Double,
    cooldownDays: Int,
    suggestedMonths: Int?,
    onDismiss: () -> Unit,
    onSave: (name: String, isNeed: Boolean, targetDate: LocalDate?) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var isNeed by rememberSaveable { mutableStateOf(false) }
    var targetDate by rememberSaveable {
        mutableStateOf<LocalDate?>(suggestedMonths?.let { LocalDate.now().plusMonths(it.toLong()) })
    }
    SparelyAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.purchase_check_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                SparelyTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.purchase_check_save_name_label)) }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.purchase_check_save_need),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(MaterialTheme.spacing.xs))
                    Switch(checked = isNeed, onCheckedChange = { isNeed = it })
                }
                TargetDatePicker(selectedDate = targetDate, onSelect = { targetDate = it })
                Text(
                    text = targetDate?.let { date ->
                        stringResource(
                            R.string.purchase_check_save_summary_dated,
                            (realCost / WishPlan.monthsBetween(LocalDate.now(), date)).formatCurrency(),
                            formatTargetDate(date)
                        )
                    } ?: stringResource(R.string.purchase_check_save_summary, realCost.formatCurrency(), cooldownDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            SparelyTextButton(onClick = { onSave(name.trim(), isNeed, targetDate) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.purchase_check_save_confirm))
            }
        },
        dismissButton = {
            SparelyTextButton(onClick = onDismiss) { Text(stringResource(R.string.purchase_check_save_cancel)) }
        }
    )
}
