package com.example.sparely.ui.screens

import androidx.compose.animation.AnimatedVisibility
import com.example.sparely.ui.utils.toSafeDouble
import com.example.sparely.ui.utils.filterCurrencyInput
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

@OptIn(ExperimentalMaterial3Api::class)
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
    expenses: List<com.example.sparely.domain.model.Expense> = emptyList()
) {
    val spacing = MaterialTheme.spacing
    val bills = remember(creditCards, expenses) {
        creditCards.associate { it.id to com.example.sparely.domain.logic.CreditCardStatements.currentBill(it, expenses) }
    }
    var expandedCardId by remember { mutableStateOf<Long?>(initialExpandedCardId) }
    var payingCard by remember { mutableStateOf<PaymentMethod?>(null) }
    
    // Using global SparelyTopBar for navigation - no local topBar needed
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        if (creditCards.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveEmptyState(
                    icon = MaterialSymbols.CREDIT_CARD,
                    title = stringResource(R.string.credit_cards_no_cards),
                    description = stringResource(R.string.credit_cards_add_hint),
                    action = onAddCard?.let {
                        {
                            SparelyButton(
                                onClick = it,
                                icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 20.dp) }
                            ) {
                                Text(stringResource(R.string.credit_cards_add_card_action))
                            }
                        }
                    }
                )
            }
        } else {
            val totalDebt = creditCards.sumOf { it.currentBalance }
            val totalLimit = creditCards.mapNotNull { it.creditLimit }.sum()
            val overallUtilization = if (totalLimit > 0) (totalDebt / totalLimit).coerceIn(0.0, 1.0) else 0.0
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                // Content padding (not Modifier.padding) so cards don't clip at the edges
                contentPadding = PaddingValues(start = spacing.md, end = spacing.md, top = spacing.xs, bottom = spacing.xl)
            ) {
                // Summary Header
                item(key = "summary") {
                    CreditCardsSummaryHeader(
                        totalDebt = totalDebt,
                        totalLimit = totalLimit,
                        overallUtilization = overallUtilization,
                        cardCount = creditCards.size,
                        modifier = Modifier.padding(bottom = spacing.md)
                    )
                }

                // Individual cards as one segmented block: rounded ends, thin gaps
                itemsIndexed(creditCards, key = { _, card -> card.id }) { index, card ->
                    CreditCardDetailItem(
                        card = card,
                        shape = segmentedShape(index, creditCards.size, outer = 28.dp),
                        modifier = Modifier.segmentSpacing(index),
                        isExpanded = expandedCardId == card.id,
                        recentPayments = recentPayments[card.id] ?: emptyList(),
                        bill = bills[card.id],
                        onClick = { 
                            expandedCardId = if (expandedCardId == card.id) null else card.id
                        },
                        onPayBill = { payingCard = card }
                    )
                }
            }
        }
    }
    
    // Pay Bill Bottom Sheet
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
private fun CreditCardsSummaryHeader(
    totalDebt: Double,
    totalLimit: Double,
    overallUtilization: Double,
    cardCount: Int,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val utilizationColor = when {
        overallUtilization <= 0.30 -> MaterialTheme.colorScheme.success
        overallUtilization <= 0.50 -> MaterialTheme.colorScheme.warning
        else -> MaterialTheme.colorScheme.critical
    }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = ExpressiveShapes.large
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        stringResource(R.string.credit_cards_total_balance),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        totalDebt.formatCurrency(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        pluralStringResource(R.plurals.credit_cards_count, cardCount, cardCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    if (totalLimit > 0) {
                        Text(
                            stringResource(R.string.credit_cards_of_limit, totalLimit.formatCurrency()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            if (totalLimit > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.credit_cards_overall_utilization),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            overallUtilization.formatPercent(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = utilizationColor
                        )
                    }
                    LinearProgressIndicator(
                        progress = { overallUtilization.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = utilizationColor,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                        strokeCap = StrokeCap.Round
                    )
                }
                
                if (overallUtilization > 0.30) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.WARNING,
                            contentDescription = null,
                            size = 16.dp,
                            tint = utilizationColor
                        )
                        Text(
                            if (overallUtilization > 0.50) 
                                stringResource(R.string.credit_cards_utilization_high_desc)
                            else 
                                stringResource(R.string.credit_cards_utilization_warning_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = utilizationColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditCardDetailItem(
    card: PaymentMethod,
    isExpanded: Boolean,
    recentPayments: List<CreditCardPayment>,
    onClick: () -> Unit,
    onPayBill: () -> Unit,
    shape: Shape = ExpressiveShapes.large,
    bill: com.example.sparely.domain.logic.CreditCardStatements.Bill? = null,
    modifier: Modifier = Modifier
) {
    val spacing = MaterialTheme.spacing
    val isCreditBalance = card.currentBalance < -0.005
    val utilizationColor = when {
        card.isUtilizationHealthy -> MaterialTheme.colorScheme.success
        card.isUtilizationWarning -> MaterialTheme.colorScheme.warning
        else -> MaterialTheme.colorScheme.critical
    }

    ExpressiveCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(ExpressiveShapes.small)
                            .background(utilizationColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.CREDIT_CARD,
                            contentDescription = null,
                            tint = utilizationColor,
                            size = 24.dp
                        )
                    }
                    Column {
                        Text(
                            card.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        card.creditLimit?.let { limit ->
                            Text(
                                stringResource(R.string.credit_cards_limit_label, limit.formatCurrency()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        card.currentBalance.formatCurrency(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isCreditBalance -> MaterialTheme.colorScheme.success
                            card.currentBalance > 0 -> utilizationColor
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (isCreditBalance) {
                        Text(
                            stringResource(R.string.credit_cards_credit_balance_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.success,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            card.utilizationPercent.formatPercent(),
                            style = MaterialTheme.typography.labelSmall,
                            color = utilizationColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Utilization Bar
            card.creditLimit?.let { limit ->
                if (limit > 0) {
                    LinearProgressIndicator(
                        progress = { card.utilizationPercent.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = utilizationColor,
                        trackColor = utilizationColor.copy(alpha = 0.18f),
                        strokeCap = StrokeCap.Round
                    )
                }
            }
            
            // Expanded Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    // Quick Stats
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ExpressiveShapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(vertical = spacing.sm),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Available credit only makes sense when a limit is set
                        card.creditLimit?.takeIf { it > 0 }?.let { limit ->
                            StatItem(
                                label = stringResource(R.string.credit_cards_available_label),
                                value = (limit - card.currentBalance).coerceAtLeast(0.0).formatCurrency()
                            )
                        }
                        StatItem(
                            label = stringResource(R.string.credit_cards_billing_day_label),
                            value = card.billingCycleDay?.toString() ?: stringResource(R.string.credit_cards_billing_day_not_set)
                        )
                        StatItem(
                            label = stringResource(R.string.credit_cards_utilization_label),
                            value = card.utilizationPercent.formatPercent(),
                            valueColor = utilizationColor
                        )
                    }
                    
                    bill?.let { CurrentBillSummary(it) }

                    // Recent Payments
                    if (recentPayments.isNotEmpty()) {
                        Text(
                            stringResource(R.string.credit_cards_recent_payments_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        recentPayments.take(3).forEach { payment ->
                            PaymentHistoryItem(payment)
                        }
                    }
                    
                    if (isCreditBalance) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.PAYMENTS,
                                contentDescription = null,
                                size = 16.dp,
                                tint = MaterialTheme.colorScheme.success
                            )
                            Text(
                                stringResource(R.string.credit_cards_credit_balance_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.success,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            // Primary action always visible; details expand on tap
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                if (card.currentBalance > 0.0) {
                    SparelyButton(
                        onClick = onPayBill,
                        modifier = Modifier.weight(1f),
                        icon = { MaterialSymbolIcon(icon = MaterialSymbols.PAYMENTS, contentDescription = null, size = 18.dp) }
                    ) {
                        Text(stringResource(R.string.credit_cards_pay_bill_action))
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                MaterialSymbolIcon(
                    icon = if (isExpanded) MaterialSymbols.ARROW_DROP_UP else MaterialSymbols.ARROW_DROP_DOWN,
                    contentDescription = stringResource(if (isExpanded) R.string.dashboard_collapse else R.string.dashboard_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PaymentHistoryItem(payment: CreditCardPayment) {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                payment.date.format(formatter),
                style = MaterialTheme.typography.bodyMedium
            )
            payment.note?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "-${payment.amount.formatCurrency()}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.success
        )
    }
}

/**
 * The bill that came out on the statement date: what's still owed on it and by when, apart from
 * purchases made since, which go on the next bill.
 */
@Composable
private fun CurrentBillSummary(bill: com.example.sparely.domain.logic.CreditCardStatements.Bill) {
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.credit_cards_bill_of, bill.statementDate.format(dateFormat)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when {
                bill.isPaid -> stringResource(R.string.credit_cards_bill_paid)
                bill.isOverdue -> stringResource(R.string.credit_cards_bill_overdue, bill.amountDue.formatCurrency(), bill.dueDate.format(dateFormat))
                else -> stringResource(R.string.credit_cards_bill_due, bill.amountDue.formatCurrency(), bill.dueDate.format(dateFormat))
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = when {
                bill.isPaid -> MaterialTheme.colorScheme.success
                bill.isOverdue -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
        if (bill.newCharges >= 0.005) {
            Text(
                text = stringResource(R.string.credit_cards_bill_new_charges, bill.newCharges.formatCurrency()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    onValueChange = { amountText = it.filterCurrencyInput() },
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
