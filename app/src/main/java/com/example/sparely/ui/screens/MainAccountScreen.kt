package com.example.sparely.ui.screens

import androidx.compose.foundation.layout.*
import com.example.sparely.ui.utils.toSafeDouble
import com.example.sparely.ui.utils.filterAmountExpressionInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import com.example.sparely.ui.components.SparelySheetHeader
import com.example.sparely.ui.components.SheetActions
import com.example.sparely.ui.components.SheetAmountField
import com.example.sparely.ui.components.SheetSectionLabel
import com.example.sparely.ui.components.SparelyChip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.sparely.ui.components.ExpressiveEmptyState
import com.example.sparely.ui.components.ExpressiveListHeader
import com.example.sparely.ui.components.segmentSpacing
import com.example.sparely.ui.components.segmentedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.SparelyExpressiveDropdown
import com.example.sparely.ui.components.SparelyTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sparely.data.local.MainAccountTransactionType
import com.example.sparely.domain.model.MainAccountTransaction
import androidx.compose.foundation.text.KeyboardOptions
import com.example.sparely.ui.components.SparelyAlertDialog
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.theme.SparelyBrandSurface
import com.example.sparely.ui.theme.warning
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.MaterialSymbolIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.sparely.ui.components.SparelyTonalButton
import com.sparely.app.R
import androidx.compose.ui.text.style.TextOverflow
import com.example.sparely.domain.model.IncomeCategory
import com.example.sparely.ui.utils.localizedName
import com.example.sparely.ui.utils.DateUtils
import com.example.sparely.ui.utils.formatCurrency
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAccountScreen(
    currentBalance: Double,
    transactions: List<MainAccountTransaction>,
    onDeposit: (Double, String, com.example.sparely.domain.model.IncomeCategory?) -> Unit,
    onWithdraw: (Double, String) -> Unit,
    onAdjust: (Double, String) -> Unit,
    onNavigateBack: () -> Unit,
    onTransactionNavigate: (MainAccountTransaction) -> Unit = {}
) {
    var showDepositDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(MainAccountFilter.ALL) }

    val filteredTransactions = remember(transactions, selectedFilter) {
        val base = when (selectedFilter) {
            MainAccountFilter.ALL -> transactions
            MainAccountFilter.INCOME -> transactions.filter { 
                it.type == MainAccountTransactionType.DEPOSIT ||
                (it.type == MainAccountTransactionType.HISA_TRANSFER && it.amount >= 0)
            }
            MainAccountFilter.EXPENSE -> transactions.filter { 
                it.type == MainAccountTransactionType.WITHDRAWAL || 
                it.type == MainAccountTransactionType.EXPENSE || 
                it.type == MainAccountTransactionType.VAULT_CONTRIBUTION ||
                it.type == MainAccountTransactionType.CREDIT_CARD_PAYMENT ||
                (it.type == MainAccountTransactionType.HISA_TRANSFER && it.amount < 0)
            }
        }
        base.sortedByDescending { it.timestamp }
    }

    // This month's money in and out, so the balance has context
    val (monthIn, monthOut) = remember(transactions) {
        val now = java.time.YearMonth.now()
        val thisMonth = transactions.filter { java.time.YearMonth.from(it.timestamp) == now }
        val incoming = thisMonth.filter { it.isIncoming() }.sumOf { kotlin.math.abs(it.amount) }
        val outgoing = thisMonth.filter { it.isOutgoing() }.sumOf { kotlin.math.abs(it.amount) }
        incoming to outgoing
    }

    Scaffold(
        // The app's frame already pads for the system bars; padding again left a gap
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // Content padding (not Modifier.padding) so cards don't clip while scrolling
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
        ) {
            // Balance Card
            item(key = "balance") {
                // Same signature card as the dashboard: Sparely spruce and mint in every theme.
                SparelyBrandSurface {
                    ExpressiveCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = MaterialTheme.shapes.large,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentPadding = 24.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.main_account_current_balance),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentBalance.formatCurrency(),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (monthIn > 0 || monthOut > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(
                                        text = stringResource(R.string.main_account_month_in, monthIn.formatCurrency()),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.success
                                    )
                                    Text(
                                        text = stringResource(R.string.main_account_month_out, monthOut.formatCurrency()),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action Buttons
            item(key = "actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SparelyButton(
                        onClick = { showDepositDialog = true },
                        modifier = Modifier.weight(1f),
                        icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, null, modifier = Modifier.size(18.dp)) }
                    ) {
                        Text(stringResource(R.string.vault_deposit))
                    }
                    
                    SparelyTonalButton(
                        onClick = { showWithdrawDialog = true },
                        modifier = Modifier.weight(1f),
                        icon = { MaterialSymbolIcon(icon = MaterialSymbols.REMOVE, null, modifier = Modifier.size(18.dp)) }
                    ) {
                        Text(stringResource(R.string.vault_withdraw))
                    }

                    // Correcting the balance is rarer, so it gets a round icon button beside the two actions
                    FilledTonalIconButton(
                        onClick = { showAdjustDialog = true },
                        modifier = Modifier.size(50.dp)
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.EDIT,
                            contentDescription = stringResource(R.string.main_account_adjust_balance),
                            size = 20.dp
                        )
                    }
                }
            }

            // Transaction History Header
            item(key = "header_history") {
                ExpressiveListHeader(
                    title = stringResource(R.string.main_account_transaction_history),
                    icon = MaterialSymbols.RECEIPT,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Filter Row
            item(key = "filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        MainAccountFilter.ALL to stringResource(R.string.filter_all),
                        MainAccountFilter.INCOME to stringResource(R.string.filter_income),
                        MainAccountFilter.EXPENSE to stringResource(R.string.filter_expenses)
                    )

                    filters.forEach { (filter, label) ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(label) },
                            leadingIcon = if (selectedFilter == filter) {
                                { MaterialSymbolIcon(icon = MaterialSymbols.CHECK, contentDescription = null, size = 18.dp) }
                            } else null
                        )
                    }
                }
            }

            if (filteredTransactions.isEmpty()) {
                item(key = "empty") {
                    ExpressiveEmptyState(
                        icon = MaterialSymbols.RECEIPT,
                        title = if (transactions.isEmpty()) stringResource(R.string.main_account_no_transactions)
                                else stringResource(R.string.main_account_no_filter_match),
                        description = null,
                        action = if (transactions.isNotEmpty()) {
                            {
                                SparelyTonalButton(onClick = { selectedFilter = MainAccountFilter.ALL }) {
                                    Text(stringResource(R.string.main_account_show_all))
                                }
                            }
                        } else null
                    )
                }
            } else {
                // One segmented block: rounded ends, thin gaps between transactions
                itemsIndexed(filteredTransactions, key = { _, transaction -> transaction.id }) { index, transaction ->
                    TransactionItem(
                        transaction = transaction,
                        onClick = { onTransactionNavigate(transaction) },
                        shape = segmentedShape(index, filteredTransactions.size),
                        modifier = Modifier.segmentSpacing(index)
                    )
                }
            }

        }
    }

    if (showDepositDialog) {
        TransactionDialog(
            title = stringResource(R.string.main_account_deposit_funds),
            icon = MaterialSymbols.ADD,
            positiveLabel = stringResource(R.string.vault_deposit),
            descriptionLabel = stringResource(R.string.income_source_label),
            isIncome = true,
            onDismiss = { showDepositDialog = false },
            onConfirm = { amount, description, category ->
                onDeposit(amount, description, category)
                showDepositDialog = false
            }
        )
    }

    if (showWithdrawDialog) {
        TransactionDialog(
            title = stringResource(R.string.main_account_withdraw_funds),
            icon = MaterialSymbols.REMOVE,
            positiveLabel = stringResource(R.string.vault_withdraw),
            descriptionLabel = stringResource(R.string.vault_reason_label),
            availableBalance = currentBalance,
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { amount, description, _ ->
                onWithdraw(amount, description)
                showWithdrawDialog = false
            }
        )
    }

    if (showAdjustDialog) {
        AdjustBalanceDialog(
            currentBalance = currentBalance,
            onDismiss = { showAdjustDialog = false },
            onConfirm = { newBalance, reason ->
                onAdjust(newBalance, reason)
                showAdjustDialog = false
            }
        )
    }
}

@Composable
private fun TransactionItem(
    transaction: MainAccountTransaction,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier
) {
    ExpressiveCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Money colors: in = saved green, to vaults = goal gold, out = alert red.
            val (icon, color) = when (transaction.type) {
                MainAccountTransactionType.DEPOSIT -> MaterialSymbols.ARROW_DOWNWARD to MaterialTheme.colorScheme.success
                MainAccountTransactionType.WITHDRAWAL -> MaterialSymbols.ARROW_UPWARD to MaterialTheme.colorScheme.critical
                MainAccountTransactionType.EXPENSE -> MaterialSymbols.SHOPPING_CART to MaterialTheme.colorScheme.critical
                MainAccountTransactionType.VAULT_CONTRIBUTION -> MaterialSymbols.SAVINGS to MaterialTheme.colorScheme.warning
                MainAccountTransactionType.ADJUSTMENT -> MaterialSymbols.EDIT to MaterialTheme.colorScheme.outline
                MainAccountTransactionType.CREDIT_CARD_PAYMENT -> MaterialSymbols.CREDIT_CARD to MaterialTheme.colorScheme.critical
                MainAccountTransactionType.HISA_TRANSFER -> {
                    if (transaction.amount >= 0) MaterialSymbols.ACCOUNT_BALANCE_WALLET to MaterialTheme.colorScheme.success
                    else MaterialSymbols.ACCOUNT_BALANCE to MaterialTheme.colorScheme.critical
                }
            }
            
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = color.copy(alpha = 0.14f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbolIcon(
                        icon = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp), // Larger icon
                        size = 24.dp
                    )
                }
            }

            // Transaction Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (transaction.type == MainAccountTransactionType.DEPOSIT && transaction.incomeCategory != null) {
                    Text(
                        text = transaction.incomeCategory.localizedName(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = DateUtils.formatDate(transaction.timestamp.toLocalDate()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Amount
            Column(
                horizontalAlignment = Alignment.End
            ) {
                val sign = when (transaction.type) {
                    MainAccountTransactionType.DEPOSIT -> "+"
                    MainAccountTransactionType.WITHDRAWAL, 
                    MainAccountTransactionType.EXPENSE,
                    MainAccountTransactionType.VAULT_CONTRIBUTION,
                    MainAccountTransactionType.CREDIT_CARD_PAYMENT -> "-"
                    MainAccountTransactionType.ADJUSTMENT -> ""
                    MainAccountTransactionType.HISA_TRANSFER -> if (transaction.amount >= 0) "+" else "-"
                }
                val amountColor = when (transaction.type) {
                    MainAccountTransactionType.DEPOSIT -> MaterialTheme.colorScheme.success
                    MainAccountTransactionType.WITHDRAWAL,
                    MainAccountTransactionType.EXPENSE,
                    MainAccountTransactionType.CREDIT_CARD_PAYMENT -> MaterialTheme.colorScheme.critical
                    MainAccountTransactionType.VAULT_CONTRIBUTION -> MaterialTheme.colorScheme.warning
                    MainAccountTransactionType.ADJUSTMENT -> MaterialTheme.colorScheme.onSurface
                    MainAccountTransactionType.HISA_TRANSFER -> if (transaction.amount >= 0) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.critical
                }
                
                // Amount is shown unsigned with our own sign: negative stored amounts
                // (outgoing savings transfers) used to render as "--12.00", without a currency symbol
                Text(
                    // The sign is shown explicitly, so display the magnitude (older credit card
                    // payments were stored as negative amounts and rendered as "--50.00").
                    text = if (transaction.type == MainAccountTransactionType.ADJUSTMENT) {
                        transaction.amount.formatCurrency()
                    } else {
                        "$sign${kotlin.math.abs(transaction.amount).formatCurrency()}"
                    },
                    style = MaterialTheme.typography.titleMedium, // Larger amount
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Text(
                    text = transaction.balanceAfter.formatCurrency(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDialog(
    title: String,
    icon: Int,
    positiveLabel: String,
    descriptionLabel: String,
    isIncome: Boolean = false,
    // When set (withdrawals), warn if the amount is more than the available balance
    availableBalance: Double? = null,
    onDismiss: () -> Unit,
    onConfirm: (Double, String, com.example.sparely.domain.model.IncomeCategory?) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<com.example.sparely.domain.model.IncomeCategory?>(com.example.sparely.domain.model.IncomeCategory.SALARY) }
    val amountValue = amount.toSafeDouble()
    val overBalance = availableBalance != null && amountValue != null && amountValue > availableBalance
    val confirm = {
        if (amountValue != null && amountValue > 0) {
            onConfirm(amountValue, description.ifEmpty { title }, selectedCategory)
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
            SparelySheetHeader(
                title = title,
                subtitle = availableBalance?.let { stringResource(R.string.vault_current, it.formatCurrency()) },
                icon = icon,
                iconTint = if (isIncome) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.critical
            )

            SheetAmountField(
                value = amount,
                onValueChange = { amount = it.filterAmountExpressionInput() },
                label = stringResource(R.string.vault_amount_label),
                isError = overBalance,
                supportingText = if (overBalance && availableBalance != null) {
                    stringResource(R.string.main_account_withdraw_over_balance, availableBalance.formatCurrency())
                } else null,
                imeAction = ImeAction.Next,
                onImeAction = {}
            )

            if (isIncome) {
                // Every source visible at once: one tap instead of opening a menu
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetSectionLabel(stringResource(R.string.main_account_income_category))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(com.example.sparely.domain.model.IncomeCategory.entries.toList()) { category ->
                            SparelyChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category.localizedName()) },
                                leadingIcon = {
                                    MaterialSymbolIcon(
                                        icon = when (category) {
                                            com.example.sparely.domain.model.IncomeCategory.SALARY -> MaterialSymbols.PAYMENTS
                                            com.example.sparely.domain.model.IncomeCategory.FREELANCE -> MaterialSymbols.WORK
                                            com.example.sparely.domain.model.IncomeCategory.GIFT -> MaterialSymbols.CELEBRATION
                                            com.example.sparely.domain.model.IncomeCategory.INVESTMENT -> MaterialSymbols.TRENDING_UP
                                            com.example.sparely.domain.model.IncomeCategory.OTHER -> MaterialSymbols.LIST
                                        },
                                        contentDescription = null,
                                        size = 18.dp
                                    )
                                }
                            )
                        }
                    }
                }
            }

            SparelyTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(descriptionLabel) },
                placeholder = { Text(stringResource(R.string.main_account_optional_note)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            SheetActions(
                confirmLabel = positiveLabel,
                onConfirm = confirm,
                confirmEnabled = amountValue != null && amountValue > 0,
                dismissLabel = stringResource(R.string.common_cancel),
                onDismiss = onDismiss
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustBalanceDialog(
    currentBalance: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var newBalance by remember { mutableStateOf(currentBalance.formatCurrency("", 2)) }
    var reason by remember { mutableStateOf("") }
    val context = LocalContext.current
    val balanceValue = newBalance.toSafeDouble()
    val canAdjust = balanceValue != null && balanceValue >= 0 && balanceValue != currentBalance
    val confirm = {
        if (canAdjust && balanceValue != null) {
            onConfirm(balanceValue, reason.ifEmpty { context.getString(R.string.main_account_adjustment_default_reason) })
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
            SparelySheetHeader(
                title = stringResource(R.string.main_account_adjust_balance),
                subtitle = stringResource(R.string.vault_current, currentBalance.formatCurrency()),
                icon = MaterialSymbols.EDIT
            )

            // Show the effect before committing: an adjustment rewrites the balance
            val delta = balanceValue?.minus(currentBalance)
            SheetAmountField(
                value = newBalance,
                onValueChange = { newBalance = it.filterAmountExpressionInput() },
                label = stringResource(R.string.main_account_new_balance),
                supportingText = delta?.takeIf { kotlin.math.abs(it) >= 0.005 }?.let {
                    stringResource(
                        R.string.main_account_adjust_delta,
                        (if (it > 0) "+" else "-") + kotlin.math.abs(it).formatCurrency()
                    )
                },
                imeAction = ImeAction.Next,
                onImeAction = {}
            )

            SparelyTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text(stringResource(R.string.vault_reason_label)) },
                placeholder = { Text(stringResource(R.string.main_account_adjust_reason_hint)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            SheetActions(
                confirmLabel = stringResource(R.string.main_account_adjust_button),
                onConfirm = confirm,
                confirmEnabled = canAdjust,
                dismissLabel = stringResource(R.string.common_cancel),
                onDismiss = onDismiss
            )
        }
    }
}


private fun MainAccountTransaction.isIncoming(): Boolean =
    type == MainAccountTransactionType.DEPOSIT ||
        (type == MainAccountTransactionType.HISA_TRANSFER && amount >= 0)

private fun MainAccountTransaction.isOutgoing(): Boolean =
    type == MainAccountTransactionType.WITHDRAWAL ||
        type == MainAccountTransactionType.EXPENSE ||
        type == MainAccountTransactionType.VAULT_CONTRIBUTION ||
        type == MainAccountTransactionType.CREDIT_CARD_PAYMENT ||
        (type == MainAccountTransactionType.HISA_TRANSFER && amount < 0)

private enum class MainAccountFilter {
    ALL, INCOME, EXPENSE
}

