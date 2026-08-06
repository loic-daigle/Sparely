package com.example.sparely.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
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
import com.example.sparely.ui.theme.ExpressiveShapes
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
    initialExpandedCardId: Long? = null
) {
    val spacing = MaterialTheme.spacing
    var expandedCardId by remember { mutableStateOf<Long?>(initialExpandedCardId) }
    var payingCard by remember { mutableStateOf<PaymentMethod?>(null) }
    
    // Using global SparelyTopBar for navigation - no local topBar needed
    Scaffold { innerPadding ->
        if (creditCards.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.CREDIT_CARD,
                        contentDescription = null,
                        size = 64.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        stringResource(R.string.credit_cards_no_cards),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.credit_cards_add_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val totalDebt = creditCards.sumOf { it.currentBalance }
            val totalLimit = creditCards.mapNotNull { it.creditLimit }.sum()
            val overallUtilization = if (totalLimit > 0) (totalDebt / totalLimit).coerceIn(0.0, 1.0) else 0.0
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                // Summary Header
                item {
                    CreditCardsSummaryHeader(
                        totalDebt = totalDebt,
                        totalLimit = totalLimit,
                        overallUtilization = overallUtilization,
                        cardCount = creditCards.size
                    )
                }
                
                // Individual Cards
                items(creditCards, key = { it.id }) { card ->
                    CreditCardDetailItem(
                        card = card,
                        isExpanded = expandedCardId == card.id,
                        recentPayments = recentPayments[card.id] ?: emptyList(),
                        onClick = { 
                            expandedCardId = if (expandedCardId == card.id) null else card.id
                        },
                        onPayBill = { payingCard = card }
                    )
                }
                
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
    
    // Pay Bill Bottom Sheet
    payingCard?.let { card ->
        PayBillBottomSheet(
            card = card,
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
    cardCount: Int
) {
    val spacing = MaterialTheme.spacing
    val utilizationColor = when {
        overallUtilization <= 0.30 -> MaterialTheme.colorScheme.success
        overallUtilization <= 0.50 -> MaterialTheme.colorScheme.warning
        else -> MaterialTheme.colorScheme.critical
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(24.dp)
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
                        fontWeight = FontWeight.ExtraBold,
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
                            .clip(RoundedCornerShape(5.dp)),
                        color = utilizationColor,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
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
    onPayBill: () -> Unit
) {
    val spacing = MaterialTheme.spacing
    val utilizationColor = when {
        card.isUtilizationHealthy -> MaterialTheme.colorScheme.success
        card.isUtilizationWarning -> MaterialTheme.colorScheme.warning
        else -> MaterialTheme.colorScheme.critical
    }
    
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            // Card Header
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(utilizationColor.copy(alpha = 0.15f)),
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
                            fontWeight = FontWeight.Bold
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
                        fontWeight = FontWeight.ExtraBold,
                        color = if (card.currentBalance > 0) utilizationColor else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        card.utilizationPercent.formatPercent(),
                        style = MaterialTheme.typography.labelSmall,
                        color = utilizationColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Utilization Bar
            card.creditLimit?.let { limit ->
                if (limit > 0) {
                    LinearProgressIndicator(
                        progress = { card.utilizationPercent.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = utilizationColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = spacing.sm))
                    
                    // Quick Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = stringResource(R.string.credit_cards_available_label),
                            value = ((card.creditLimit ?: 0.0) - card.currentBalance).formatCurrency()
                        )
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
                    
                    // Recent Payments
                    if (recentPayments.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = spacing.xs))
                        Text(
                            stringResource(R.string.credit_cards_recent_payments_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        recentPayments.take(3).forEach { payment ->
                            PaymentHistoryItem(payment)
                        }
                    }
                    
                    // Action Buttons
                    HorizontalDivider(modifier = Modifier.padding(vertical = spacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        SparelyButton(
                            onClick = onPayBill,
                            modifier = Modifier.weight(1f)
                        ) {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.PAYMENTS,
                                contentDescription = null,
                                size = 18.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.credit_cards_pay_bill_action))
                        }
                    }
                }
            }
            
            // Expand hint when collapsed
            if (!isExpanded) {
                Text(
                    stringResource(R.string.credit_cards_expand_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayBillBottomSheet(
    card: PaymentMethod,
    mainAccountBalance: Double,
    onDismiss: () -> Unit,
    onPay: (amount: Double, note: String?, deductFromMainAccount: Boolean) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var deductFromMainAccount by remember { mutableStateOf(true) }
    val currentBalance = card.currentBalance
    val paymentAmount = amountText.toDoubleOrNull() ?: 0.0
    val hasInsufficientFunds = deductFromMainAccount && paymentAmount > mainAccountBalance
    val currencySymbol = stringResource(R.string.currency_symbol)
    
    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.credit_cards_pay_dialog_title, card.name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                ExpressiveCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = ExpressiveShapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.credit_cards_current_balance_label, currentBalance.formatCurrency()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SparelyTonalButton(
                                onClick = { amountText = currentBalance.formatCurrency("", 2) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.credit_cards_preset_full), style = MaterialTheme.typography.labelSmall)
                            }
                            SparelyTonalButton(
                                onClick = { amountText = minOf(25.0, currentBalance).formatCurrency("", 2) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.credit_cards_preset_min), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                
                SparelyTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.credit_cards_payment_amount_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Text(currencySymbol) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                SparelyTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.credit_cards_note_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                // Deduct from Main Account toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.ACCOUNT_BALANCE_WALLET,
                                contentDescription = null,
                                size = 20.dp,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.credit_cards_deduct_main_label),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            stringResource(R.string.credit_cards_main_available_label, mainAccountBalance.formatCurrency()),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasInsufficientFunds) MaterialTheme.colorScheme.error 
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = deductFromMainAccount,
                        onCheckedChange = { deductFromMainAccount = it }
                    )
                }
                
                if (hasInsufficientFunds) {
                    ExpressiveCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        shape = ExpressiveShapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.WARNING,
                                contentDescription = null,
                                size = 18.dp,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                stringResource(R.string.credit_cards_insufficient_funds_error),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SparelyTextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.credit_cards_cancel_action))
                }
                SparelyButton(
                    onClick = {
                        val amount = amountText.toDoubleOrNull()
                        if (amount != null && amount > 0 && !hasInsufficientFunds) {
                            onPay(amount, note.takeIf { it.isNotBlank() }, deductFromMainAccount)
                        }
                    },
                    enabled = amountText.toDoubleOrNull()?.let { it > 0 } == true && !hasInsufficientFunds,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.credit_cards_pay_action_confirm))
                }
            }
        }
    }
}

