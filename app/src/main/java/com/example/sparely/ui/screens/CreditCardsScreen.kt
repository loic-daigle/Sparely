package com.example.sparely.ui.screens

import com.example.sparely.ui.components.containerTransform

import androidx.compose.foundation.layout.fillMaxHeight

import com.example.sparely.domain.model.netAmount

import com.example.sparely.domain.model.displayName

import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.animation.AnimatedVisibility
import com.example.sparely.ui.utils.toSafeDouble
import com.example.sparely.ui.utils.filterAmountExpressionInput
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import com.example.sparely.ui.components.SparelySheetHeader
import com.example.sparely.ui.components.SheetActions
import com.example.sparely.ui.components.SheetAmountField
import com.example.sparely.ui.components.SheetToggleRow
import com.example.sparely.ui.components.SparelyChip
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.sparely.ui.components.ExpressiveEmptyState
import com.example.sparely.ui.components.segmentSpacing
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.theme.ExpressiveShapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.sparely.app.R
import com.example.sparely.domain.model.CreditCardPayment
import com.example.sparely.domain.model.PaymentMethod
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.SparelyAlertDialog
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyTextField
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.spacing
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.warning
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.formatPercent
import java.time.format.DateTimeFormatter
import java.util.Locale

private typealias Bill = com.example.sparely.domain.logic.CreditCardStatements.Bill

/** Credit use above this share of the limit starts to weigh on a credit score. */
private const val GOOD_UTILIZATION = 0.30

/** What a card's bill says in a few words, and its colour: paid, due in N days, or late. */
@Composable
private fun billStatus(card: PaymentMethod, bill: Bill?): Pair<String, Color> {
    val colors = MaterialTheme.colorScheme
    return when {
        card.currentBalance < 0 -> stringResource(R.string.dash_credit_of, (-card.currentBalance).formatCurrency(decimals = 0)) to colors.success
        bill == null -> (card.creditLimit?.let { stringResource(R.string.credit_cards_limit_label, it.formatCurrency(decimals = 0)) }
            ?: stringResource(R.string.dash_credit_card)) to colors.onSurfaceVariant
        bill.isPaid -> stringResource(R.string.credit_cards_bill_paid) to colors.success
        bill.isOverdue -> pluralStringResource(R.plurals.cc_bill_late, -bill.daysUntilDue, bill.amountDue.formatCurrency(decimals = 0), -bill.daysUntilDue) to colors.error
        else -> pluralStringResource(R.plurals.cc_bill_due_in, bill.daysUntilDue, bill.amountDue.formatCurrency(decimals = 0), bill.daysUntilDue) to
            (if (bill.daysUntilDue <= 7) colors.warning else colors.onSurfaceVariant)
    }
}

/**
 * The credit cards page (docs/DESIGN.md): what's owed against the limits with the 30 % mark, then
 * each card with its bill, its use of its limit and a Pay button when a bill is waiting. Tapping a
 * card opens its own page.
 */
@Composable
fun CreditCardsScreen(
    creditCards: List<PaymentMethod>,
    mainAccountBalance: Double = 0.0,
    recentPayments: Map<Long, List<CreditCardPayment>> = emptyMap(),
    onPayBill: (paymentMethodId: Long, amount: Double, note: String?, deductFromMainAccount: Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    initialExpandedCardId: Long? = null,
    onAddCard: (() -> Unit)? = null,
    /** Used to tell this bill's purchases from the ones made since it came out. */
    expenses: List<com.example.sparely.domain.model.Expense> = emptyList(),
    onOpenCard: (Long) -> Unit = {}
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val bills = remember(creditCards, expenses) {
        creditCards.associate { it.id to com.example.sparely.domain.logic.CreditCardStatements.currentBill(it, expenses) }
    }
    var payingCard by remember { mutableStateOf<PaymentMethod?>(null) }
    // A link to one card (a bill reminder) opens its page once
    var openedInitial by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(initialExpandedCardId) {
        if (initialExpandedCardId != null && !openedInitial) {
            openedInitial = true
            onOpenCard(initialExpandedCardId)
        }
    }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), containerColor = colors.background) { innerPadding ->
        if (creditCards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                ExpressiveEmptyState(
                    icon = MaterialSymbols.CREDIT_CARD,
                    title = stringResource(R.string.credit_cards_no_cards),
                    description = stringResource(R.string.credit_cards_add_hint),
                    action = onAddCard?.let {
                        {
                            SparelyButton(
                                onClick = it,
                                icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 20.dp) }
                            ) { Text(stringResource(R.string.credit_cards_add_card_action)) }
                        }
                    }
                )
            }
            return@Scaffold
        }
        // What is owed: a card in credit (after a refund) doesn't pay off another card's balance
        val owed = creditCards.sumOf { it.currentBalance.coerceAtLeast(0.0) }
        val limit = creditCards.mapNotNull { it.creditLimit }.sum()
        val utilization = if (limit > 0) owed / limit else 0.0
        val sorted = creditCards.sortedWith(compareBy<PaymentMethod> { bills[it.id]?.takeIf { b -> !b.isPaid }?.daysUntilDue ?: Int.MAX_VALUE }.thenByDescending { it.currentBalance })
        val nextBill = sorted.firstNotNullOfOrNull { card -> bills[card.id]?.takeIf { !it.isPaid }?.let { card to it } }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = spacing.md, end = spacing.md, top = spacing.xs, bottom = spacing.xl)
        ) {
            item(key = "hero") {
                val accent = if (utilization <= GOOD_UTILIZATION) colors.success else colors.warning
                Surface(modifier = Modifier.fillMaxWidth(), shape = ExpressiveShapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.dash_you_owe), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            com.example.sparely.ui.components.SingleLineText(
                                text = owed.formatCurrency(decimals = 0),
                                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.onPrimaryContainer,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (limit > 0) {
                                Text(
                                    stringResource(R.string.dash_of_limit, limit.formatCurrency(decimals = 0)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                        if (limit > 0) {
                            MarkedBar(
                                progress = utilization.toFloat(),
                                mark = GOOD_UTILIZATION.toFloat(),
                                color = accent,
                                height = 10.dp,
                                trackColor = colors.surface.copy(alpha = 0.6f),
                                markColor = colors.onPrimaryContainer,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Text(
                                stringResource(R.string.dash_used_pct, utilization.formatPercent(0)) + " · " +
                                    stringResource(if (utilization <= GOOD_UTILIZATION) R.string.dash_util_good else R.string.dash_util_high),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        nextBill?.let { (card, bill) ->
                            Box(modifier = Modifier.padding(top = 8.dp)) {
                                HeroPill(
                                    MaterialSymbols.CALENDAR_MONTH,
                                    stringResource(R.string.cc_next_bill, card.name, bill.amountDue.formatCurrency(decimals = 0), bill.dueDate.format(com.example.sparely.ui.utils.LocalDates.dayMonth)),
                                    onClick = { onOpenCard(card.id) }
                                )
                            }
                        }
                    }
                }
            }
            item(key = "header") {
                com.example.sparely.ui.components.ExpressiveListHeader(
                    title = stringResource(R.string.cc_cards_header),
                    icon = MaterialSymbols.CREDIT_CARD,
                    trailing = creditCards.size.toString()
                )
            }
            itemsIndexed(sorted, key = { _, card -> card.id }) { index, card ->
                val bill = bills[card.id]
                CreditCardRow(
                    card = card,
                    bill = bill,
                    shape = segmentedShape(index, sorted.size),
                    modifier = Modifier.segmentSpacing(index),
                    onClick = { onOpenCard(card.id) },
                    onPay = if (bill != null && !bill.isPaid) ({ payingCard = card }) else null
                )
            }
        }
    }

    payingCard?.let { card ->
        PayBillBottomSheet(
            card = card,
            statementAmount = bills[card.id]?.amountDue?.takeIf { it >= 0.005 },
            mainAccountBalance = mainAccountBalance,
            onDismiss = { payingCard = null },
            onPay = { amount, note, deductFromMain ->
                onPayBill(card.id, amount, note, deductFromMain)
                payingCard = null
            }
        )
    }
}

@Composable
private fun CreditCardRow(
    card: PaymentMethod,
    bill: Bill?,
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPay: (() -> Unit)?
) {
    val colors = MaterialTheme.colorScheme
    val (status, statusColor) = billStatus(card, bill)
    val cardLimit = card.creditLimit?.takeIf { it > 0 }
    val used = cardLimit?.let { card.currentBalance.coerceAtLeast(0.0) / it }
    val barColor = when {
        used == null || used <= GOOD_UTILIZATION -> colors.primary
        used <= 0.5 -> colors.warning
        else -> colors.error
    }
    Surface(onClick = onClick, modifier = modifier.containerTransform("card-${card.id}", shape).fillMaxWidth(), shape = shape, color = colors.surfaceContainer) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.tertiary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(icon = MaterialSymbols.CREDIT_CARD, contentDescription = null, size = 22.dp, tint = colors.tertiary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (statusColor == colors.onSurfaceVariant) FontWeight.Normal else FontWeight.Medium,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    card.currentBalance.coerceAtLeast(0.0).formatCurrency(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (used != null && used > 0.5) colors.error else colors.onSurface
                )
            }
            if (used != null && card.currentBalance >= 0) {
                MarkedBar(progress = used.toFloat(), mark = GOOD_UTILIZATION.toFloat(), color = barColor, modifier = Modifier.padding(start = 58.dp, top = 8.dp))
            }
            if (used != null || onPay != null) {
                Row(modifier = Modifier.padding(start = 58.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(
                            used?.let { stringResource(R.string.dash_of_limit_pct, it.formatPercent(0)) },
                            cardLimit?.let { stringResource(R.string.dash_available, (it - card.currentBalance).coerceAtLeast(0.0).formatCurrency(decimals = 0)) }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    onPay?.let { SparelyTonalButton(onClick = it) { Text(stringResource(R.string.credit_cards_pay_bill_action)) } }
                }
            }
        }
    }
}

/**
 * One card's page: the current bill and when it's due on a statement-to-due timeline, what's
 * available and used, Pay the bill, what was spent since the statement by category, and payments.
 */
@Composable
fun CreditCardDetailScreen(
    card: PaymentMethod?,
    mainAccountBalance: Double,
    recentPayments: List<CreditCardPayment>,
    expenses: List<com.example.sparely.domain.model.Expense>,
    onPayBill: (paymentMethodId: Long, amount: Double, note: String?, deductFromMainAccount: Boolean) -> Unit,
    onEditCard: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    if (card == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.credit_cards_no_cards), color = colors.onSurfaceVariant)
        }
        return
    }
    val today = remember { java.time.LocalDate.now() }
    val bill = remember(card, expenses) { com.example.sparely.domain.logic.CreditCardStatements.currentBill(card, expenses) }
    var paying by remember { mutableStateOf(false) }
    val cardLimit = card.creditLimit?.takeIf { it > 0 }
    val used = cardLimit?.let { card.currentBalance.coerceAtLeast(0.0) / it }
    val since = bill?.statementDate
    val sinceStatement = remember(expenses, card.id, since) {
        expenses.filter { it.paymentMethodId == card.id && (since == null || it.date.isAfter(since)) && (since != null || java.time.YearMonth.from(it.date) == java.time.YearMonth.from(today)) }
    }
    val byCategory = sinceStatement.groupBy { it.category }
        .map { (category, list) -> Triple(category, list.sumOf { it.netAmount }, list.size) }
        .sortedByDescending { it.second }
    val sinceTotal = byCategory.sumOf { it.second }
    val dateFormat = com.example.sparely.ui.utils.LocalDates.medium

    LazyColumn(
        modifier = Modifier.containerTransform("card-${card?.id}").fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hero") {
            Surface(shape = ExpressiveShapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (bill != null) {
                        Text(stringResource(R.string.credit_cards_bill_of, bill.statementDate.format(dateFormat)), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
                        com.example.sparely.ui.components.SingleLineText(
                            text = bill.amountDue.formatCurrency(),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onPrimaryContainer
                        )
                        val (status, statusColor) = billStatus(card, bill)
                        Text(
                            if (bill.isPaid) status else stringResource(R.string.credit_cards_bill_due, bill.amountDue.formatCurrency(), bill.dueDate.format(dateFormat)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (bill.isOverdue || bill.isPaid) statusColor else colors.onPrimaryContainer
                        )
                        BillTimeline(statement = bill.statementDate, due = bill.dueDate, today = today, late = bill.isOverdue)
                    } else {
                        Text(stringResource(R.string.credit_cards_total_balance), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
                        com.example.sparely.ui.components.SingleLineText(
                            text = card.currentBalance.formatCurrency(),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onPrimaryContainer
                        )
                        Text(stringResource(R.string.cc_no_statement_day), style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }
        }
        item(key = "tiles") {
            Row(modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.credit_cards_available_label), cardLimit?.let { (it - card.currentBalance).coerceAtLeast(0.0).formatCurrency(decimals = 0) } ?: "—", Modifier.weight(1f).fillMaxHeight())
                StatTile(stringResource(R.string.credit_cards_utilization_label), used?.formatPercent(0) ?: "—", Modifier.weight(1f).fillMaxHeight())
                StatTile(stringResource(R.string.cc_since_statement), sinceTotal.formatCurrency(decimals = 0), Modifier.weight(1f).fillMaxHeight())
            }
        }
        item(key = "actions") {
            com.example.sparely.ui.components.SparelyButtonGroup(
                listOf(
                    com.example.sparely.ui.components.ButtonGroupItem(stringResource(R.string.cc_pay), MaterialSymbols.PAYMENTS, emphasized = true) { paying = true },
                    com.example.sparely.ui.components.ButtonGroupItem(stringResource(R.string.action_edit_short), MaterialSymbols.EDIT, onClick = onEditCard)
                )
            )
        }
        if (byCategory.isNotEmpty()) {
            item(key = "since") {
                Column {
                    com.example.sparely.ui.components.ExpressiveListHeader(
                        title = stringResource(if (since != null) R.string.cc_since_statement else R.string.dashboard_this_month),
                        icon = MaterialSymbols.PIE_CHART,
                        trailing = sinceTotal.formatCurrency(decimals = 0)
                    )
                    byCategory.forEachIndexed { index, (category, amount, count) ->
                        val tint = com.example.sparely.ui.theme.getCategoryColor(category)
                        com.example.sparely.ui.components.SegmentedListItem(
                            title = category.displayName(),
                            subtitle = pluralStringResource(R.plurals.history_store_purchases, count, count),
                            shape = segmentedShape(index, byCategory.size),
                            modifier = Modifier.segmentSpacing(index),
                            icon = com.example.sparely.ui.theme.getCategoryIcon(category),
                            iconTint = tint,
                            below = { MarkedBar(progress = (amount / sinceTotal).toFloat(), mark = null, color = tint) },
                            trailing = { Text(amount.formatCurrency(decimals = 0), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        }
        if (recentPayments.isNotEmpty()) {
            item(key = "payments") {
                Column {
                    com.example.sparely.ui.components.ExpressiveListHeader(title = stringResource(R.string.credit_cards_recent_payments_label), icon = MaterialSymbols.HISTORY)
                    val shown = recentPayments.sortedByDescending { it.date }.take(6)
                    shown.forEachIndexed { index, payment ->
                        com.example.sparely.ui.components.SegmentedListItem(
                            title = payment.note?.takeIf { it.isNotBlank() } ?: stringResource(R.string.cc_payment),
                            subtitle = payment.date.format(dateFormat),
                            shape = segmentedShape(index, shown.size),
                            modifier = Modifier.segmentSpacing(index),
                            icon = MaterialSymbols.CHECK_CIRCLE,
                            iconTint = colors.success,
                            trailing = { Text(payment.amount.formatCurrency(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        }
    }

    if (paying) {
        PayBillBottomSheet(
            card = card,
            statementAmount = bill?.amountDue?.takeIf { it >= 0.005 },
            mainAccountBalance = mainAccountBalance,
            onDismiss = { paying = false },
            onPay = { amount, note, deductFromMain ->
                onPayBill(card.id, amount, note, deductFromMain)
                paying = false
            }
        )
    }
}

/** Statement date, today and the due date on one line, today's dot where it falls between them. */
@Composable
private fun BillTimeline(statement: java.time.LocalDate, due: java.time.LocalDate, today: java.time.LocalDate, late: Boolean) {
    val colors = MaterialTheme.colorScheme
    val total = java.time.temporal.ChronoUnit.DAYS.between(statement, due).toFloat().coerceAtLeast(1f)
    val progress = (java.time.temporal.ChronoUnit.DAYS.between(statement, today) / total).coerceIn(0f, 1f)
    val ink = colors.onPrimaryContainer
    val dueColor = if (late) colors.error else colors.warning
    Column(modifier = Modifier.padding(top = 14.dp)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
            val y = size.height / 2
            val r = 6.dp.toPx()
            drawLine(ink.copy(alpha = 0.2f), androidx.compose.ui.geometry.Offset(r, y), androidx.compose.ui.geometry.Offset(size.width - r, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            val x = r + (size.width - 2 * r) * progress
            drawLine(ink, androidx.compose.ui.geometry.Offset(r, y), androidx.compose.ui.geometry.Offset(x, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(ink, r, androidx.compose.ui.geometry.Offset(r, y))
            drawCircle(dueColor, r, androidx.compose.ui.geometry.Offset(size.width - r, y))
            drawCircle(colors.primaryContainer, r + 1.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
            drawCircle(ink, r + 1.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(statement.format(com.example.sparely.ui.utils.LocalDates.dayMonth), style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.75f))
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.dash_when_today), style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.75f))
            Spacer(Modifier.weight(1f))
            Text(due.format(com.example.sparely.ui.utils.LocalDates.dayMonth), style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.75f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayBillBottomSheet(
    card: PaymentMethod,
    mainAccountBalance: Double,
    onDismiss: () -> Unit,
    onPay: (amount: Double, note: String?, deductFromMainAccount: Boolean) -> Unit,
    /** What's owed on the current bill, when the card has a billing day and something's still due. */
    statementAmount: Double? = null
) {
    // Paying the bill in full avoids interest, so start there; without a bill, the whole balance.
    var amountText by remember {
        mutableStateOf((statementAmount ?: card.currentBalance.coerceAtLeast(0.0)).formatCurrency("", 2))
    }
    var note by remember { mutableStateOf("") }
    var deductFromMainAccount by remember { mutableStateOf(true) }
    val currentBalance = card.currentBalance
    val paymentAmount = amountText.toSafeDouble() ?: 0.0
    val hasInsufficientFunds = deductFromMainAccount && paymentAmount > mainAccountBalance
    val currencySymbol = stringResource(R.string.currency_symbol)
    val pay = {
        val amount = amountText.toSafeDouble()
        if (amount != null && amount > 0 && !hasInsufficientFunds) {
            onPay(amount, note.takeIf { it.isNotBlank() }, deductFromMainAccount)
        }
    }

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SparelySheetHeader(
                    title = stringResource(R.string.credit_cards_pay_dialog_title, card.name),
                    subtitle = stringResource(R.string.credit_cards_current_balance_label, currentBalance.formatCurrency()),
                    icon = MaterialSymbols.CREDIT_CARD
                )

                // Prefilled with the full balance, so the keypad doesn't open by itself
                SheetAmountField(
                    value = amountText,
                    onValueChange = { amountText = it.filterAmountExpressionInput() },
                    label = stringResource(R.string.credit_cards_payment_amount_label),
                    currencySymbol = currencySymbol,
                    autoFocus = false,
                    onImeAction = pay
                )

                // One-tap amounts
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    statementAmount?.let { due ->
                        SparelyChip(
                            selected = amountText == due.formatCurrency("", 2),
                            onClick = { amountText = due.formatCurrency("", 2) },
                            label = { Text(stringResource(R.string.credit_cards_preset_statement)) }
                        )
                    }
                    SparelyChip(
                        selected = amountText == currentBalance.formatCurrency("", 2),
                        onClick = { amountText = currentBalance.formatCurrency("", 2) },
                        label = { Text(stringResource(R.string.credit_cards_preset_full)) }
                    )
                    SparelyChip(
                        selected = amountText == minOf(25.0, currentBalance).formatCurrency("", 2),
                        onClick = { amountText = minOf(25.0, currentBalance).formatCurrency("", 2) },
                        label = { Text(stringResource(R.string.credit_cards_preset_min)) }
                    )
                }

                SheetToggleRow(
                    title = stringResource(R.string.credit_cards_deduct_main_label),
                    description = if (hasInsufficientFunds) {
                        stringResource(R.string.credit_cards_insufficient_funds_error)
                    } else {
                        stringResource(R.string.credit_cards_main_available_label, mainAccountBalance.formatCurrency())
                    },
                    checked = deductFromMainAccount,
                    onCheckedChange = { deductFromMainAccount = it }
                )

                SparelyTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.credit_cards_note_label)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { pay() }),
                    isError = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SheetActions(
                confirmLabel = stringResource(R.string.credit_cards_pay_action_confirm),
                onConfirm = pay,
                confirmEnabled = paymentAmount > 0 && !hasInsufficientFunds,
                dismissLabel = stringResource(R.string.credit_cards_cancel_action),
                onDismiss = onDismiss
            )
        }
    }
}
