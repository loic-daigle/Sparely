package com.example.sparely.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.toInputString
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.sparely.ui.components.SparelySheetHeader
import com.example.sparely.ui.components.SheetActions
import com.example.sparely.ui.components.SheetAmountField
import com.example.sparely.ui.components.SheetChoiceRow
import com.example.sparely.ui.components.SheetSectionLabel
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import com.example.sparely.ui.components.ExpressiveEmptyState
import com.example.sparely.ui.components.segmentSpacing
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.theme.ExpressiveShapes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.sparely.app.R
import com.example.sparely.domain.model.SavingsAccess
import com.example.sparely.domain.model.SavingsAccount
import com.example.sparely.domain.model.WithdrawalQuote
import com.example.sparely.domain.model.SavingsProductType
import com.example.sparely.ui.components.SparelyAlertDialog
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyTextField
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.utils.toSafeDouble
import com.example.sparely.ui.utils.formatCurrency
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import com.example.sparely.ui.theme.success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsAccountsScreen(
    savingsAccounts: List<SavingsAccount>,
    onNavigateBack: () -> Unit,
    onAddAccount: (SavingsAccount) -> Unit,
    onUpdateAccount: (SavingsAccount) -> Unit,
    onArchiveAccount: (Long) -> Unit,
    onAddInterest: (Long, Double) -> Unit,
    onViewHistory: (Long, String) -> Unit,
    mainAccountBalance: Double = 0.0,
    onCorrectBalance: (Long, Double) -> Unit = { _, _ -> },
    onDeposit: (Long, Double) -> Unit = { _, _ -> },
    onWithdraw: (Long, Double, Boolean) -> Unit = { _, _, _ -> },
    onRequestNotice: (Long) -> Unit = {},
    onCancelNotice: (Long) -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountToArchive by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountToUpdateBalance by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountToAddInterest by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountToDeposit by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountToWithdraw by remember { mutableStateOf<SavingsAccount?>(null) }
    val today = remember { java.time.LocalDate.now() }
    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance().apply {
            isGroupingUsed = false
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    // Calculate summary stats
    val totalBalance = remember(savingsAccounts) { savingsAccounts.sumOf { it.currentBalance } }
    val totalAccessible = remember(savingsAccounts, today) { savingsAccounts.sumOf { it.accessibleBalance(today) } }
    val totalInterest = remember(savingsAccounts) { savingsAccounts.sumOf { it.totalInterestEarned } }
    val avgApy = remember(savingsAccounts) {
        if (savingsAccounts.isNotEmpty()) savingsAccounts.map { it.annualPercentageYield }.average() else 0.0
    }

    val listState = rememberLazyListState()
    // The FAB shows its label at the top of the list and shrinks to an icon once scrolling starts
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                expanded = fabExpanded,
                icon = {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.ADD,
                        contentDescription = if (fabExpanded) null else stringResource(id = R.string.savings_add_account),
                        size = 24.dp
                    )
                },
                text = { Text(stringResource(id = R.string.savings_add_account)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            // Extra bottom padding so the last card can scroll clear of the FAB
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
        ) {
            // Overall Summary Card
            if (savingsAccounts.isNotEmpty()) {
                item {
                    OverallSavingsSummaryCard(
                        totalBalance = totalBalance,
                        accessibleBalance = totalAccessible,
                        totalInterest = totalInterest,
                        avgApy = avgApy,
                        accountCount = savingsAccounts.size
                    )
                }
            }

            if (savingsAccounts.isEmpty()) {
                item {
                    EmptySavingsCard(onAddAccount = { showAddDialog = true })
                }
            } else {
                // One segmented block: rounded ends, thin gaps between accounts
                itemsIndexed(savingsAccounts, key = { _, account -> account.id }) { index, account ->
                    EnhancedSavingsAccountCard(
                        account = account,
                        shape = segmentedShape(index, savingsAccounts.size, outer = 28.dp),
                        modifier = Modifier.segmentSpacing(index),
                        onEditClick = { accountToEdit = account },
                        onArchiveClick = { accountToArchive = account },
                        onUpdateBalanceClick = { accountToUpdateBalance = account },
                        onAddInterestClick = { accountToAddInterest = account },
                        onViewHistoryClick = { onViewHistory(account.id, account.name) },
                        onDepositClick = { accountToDeposit = account },
                        onWithdrawClick = { accountToWithdraw = account },
                        onRequestNoticeClick = { onRequestNotice(account.id) },
                        onCancelNoticeClick = { onCancelNotice(account.id) }
                    )
                }
            }
        }

        if (showAddDialog) {
            SavingsAccountDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { account ->
                    onAddAccount(account)
                    showAddDialog = false
                }
            )
        }

        accountToEdit?.let { account ->
            SavingsAccountDialog(
                initialAccount = account,
                isEdit = true,
                onDismiss = { accountToEdit = null },
                onConfirm = { updated ->
                    onUpdateAccount(updated)
                    accountToEdit = null
                }
            )
        }

        accountToArchive?.let { account ->
            SparelyBottomSheet(
                isOpen = true,
                onDismiss = { accountToArchive = null }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SparelySheetHeader(
                        title = stringResource(id = R.string.savings_archive_title),
                        subtitle = stringResource(id = R.string.savings_archive_confirm, account.name),
                        icon = MaterialSymbols.DELETE,
                        iconTint = MaterialTheme.colorScheme.error
                    )
                    SheetActions(
                        confirmLabel = stringResource(id = R.string.savings_archive_action),
                        onConfirm = {
                            onArchiveAccount(account.id)
                            accountToArchive = null
                        },
                        destructive = true,
                        dismissLabel = stringResource(id = android.R.string.cancel),
                        onDismiss = { accountToArchive = null }
                    )
                }
            }
        }

        accountToUpdateBalance?.let { account ->
            var newBalanceText by remember { mutableStateOf(account.currentBalance.toInputString()) }
            val newBalance = newBalanceText.toSafeDouble()
            val save = {
                if (newBalance != null) {
                    onCorrectBalance(account.id, newBalance)
                    accountToUpdateBalance = null
                }
            }
            SparelyBottomSheet(
                isOpen = true,
                onDismiss = { accountToUpdateBalance = null }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SparelySheetHeader(
                        title = stringResource(id = R.string.savings_update_balance_title),
                        subtitle = stringResource(id = R.string.savings_update_balance_desc, account.name),
                        icon = MaterialSymbols.EDIT
                    )
                    SheetAmountField(
                        value = newBalanceText,
                        onValueChange = { newBalanceText = it.filterCurrencyInput() },
                        label = stringResource(id = R.string.savings_current_balance),
                        onImeAction = save
                    )
                    SheetActions(
                        confirmLabel = stringResource(id = R.string.savings_update_balance_action),
                        onConfirm = save,
                        confirmEnabled = newBalance != null,
                        dismissLabel = stringResource(id = android.R.string.cancel),
                        onDismiss = { accountToUpdateBalance = null }
                    )
                }
            }
        }

        accountToDeposit?.let { account ->
            var amountText by remember { mutableStateOf("") }
            val amount = amountText.toSafeDouble()
            val tooMuch = amount != null && amount > mainAccountBalance + 0.005
            val closed = !account.acceptsDeposits(today)
            val canDeposit = amount != null && amount > 0 && !tooMuch && !closed
            val deposit = {
                if (canDeposit && amount != null) {
                    onDeposit(account.id, amount)
                    accountToDeposit = null
                }
            }
            SparelyBottomSheet(isOpen = true, onDismiss = { accountToDeposit = null }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SparelySheetHeader(
                        title = stringResource(id = R.string.savings_deposit_title),
                        subtitle = stringResource(id = R.string.savings_deposit_desc, account.name, mainAccountBalance.formatCurrency()),
                        icon = MaterialSymbols.ARROW_DOWNWARD
                    )
                    SheetAmountField(
                        value = amountText,
                        onValueChange = { amountText = it.filterCurrencyInput() },
                        label = stringResource(id = R.string.savings_amount),
                        isError = tooMuch || closed,
                        supportingText = when {
                            closed -> stringResource(id = R.string.savings_deposit_closed, account.maturityDate?.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) ?: "-")
                            tooMuch -> stringResource(id = R.string.savings_deposit_not_enough)
                            account.productType == SavingsProductType.TERM -> stringResource(id = R.string.savings_deposit_term_warning)
                            else -> null
                        },
                        onImeAction = deposit
                    )
                    SheetActions(
                        confirmLabel = stringResource(id = R.string.savings_deposit),
                        onConfirm = deposit,
                        confirmEnabled = canDeposit,
                        dismissLabel = stringResource(id = android.R.string.cancel),
                        onDismiss = { accountToDeposit = null }
                    )
                }
            }
        }

        accountToWithdraw?.let { account ->
            var amountText by remember { mutableStateOf("") }
            val amount = amountText.toSafeDouble()
            val quote = remember(account, amount) { amount?.let { account.quoteWithdrawal(it, today) } }
            val accessible = account.accessibleBalance(today)
            val allowed = quote as? WithdrawalQuote.Allowed
            val withdraw = {
                if (amount != null && allowed != null) {
                    onWithdraw(account.id, amount, allowed.isEarlyBreak)
                    accountToWithdraw = null
                }
            }
            SparelyBottomSheet(isOpen = true, onDismiss = { accountToWithdraw = null }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SparelySheetHeader(
                        title = stringResource(id = R.string.savings_withdraw_title),
                        subtitle = stringResource(id = R.string.savings_withdraw_desc, account.name, accessible.formatCurrency()),
                        icon = MaterialSymbols.ARROW_UPWARD
                    )
                    SheetAmountField(
                        value = amountText,
                        onValueChange = { amountText = it.filterCurrencyInput() },
                        label = stringResource(id = R.string.savings_amount),
                        isError = quote is WithdrawalQuote.Blocked,
                        supportingText = when (quote) {
                            is WithdrawalQuote.Blocked -> quote.reason
                            is WithdrawalQuote.Allowed -> stringResource(id = R.string.savings_you_receive, quote.net.formatCurrency())
                            null -> null
                        },
                        onImeAction = withdraw
                    )
                    // Breaking a term early costs money, so the penalty gets its own warning panel
                    if (allowed?.isEarlyBreak == true) {
                        Surface(
                            shape = ExpressiveShapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MaterialSymbolIcon(
                                    icon = MaterialSymbols.WARNING,
                                    contentDescription = null,
                                    size = 20.dp,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = stringResource(id = R.string.savings_early_penalty_warning, allowed.penalty.formatCurrency()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    SheetActions(
                        confirmLabel = stringResource(
                            id = if (allowed?.isEarlyBreak == true) R.string.savings_withdraw_pay_penalty else R.string.savings_withdraw
                        ),
                        onConfirm = withdraw,
                        confirmEnabled = allowed != null,
                        destructive = allowed?.isEarlyBreak == true,
                        dismissLabel = stringResource(id = android.R.string.cancel),
                        onDismiss = { accountToWithdraw = null }
                    )
                }
            }
        }

        // Add Interest Dialog
        accountToAddInterest?.let { account ->
            val estimatedInterest = remember(account) { account.estimatedAccruedInterest() }
            var interestAmountText by remember {
                mutableStateOf(if (estimatedInterest >= 0.01) estimatedInterest.toInputString() else "")
            }
            val interestAmount = interestAmountText.toSafeDouble()
            val addInterest = {
                if (interestAmount != null && interestAmount > 0) {
                    onAddInterest(account.id, interestAmount)
                    accountToAddInterest = null
                }
            }
            SparelyBottomSheet(
                isOpen = true,
                onDismiss = { accountToAddInterest = null }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SparelySheetHeader(
                        title = stringResource(id = R.string.savings_add_interest_title),
                        subtitle = stringResource(id = R.string.savings_add_interest_desc, account.name),
                        icon = MaterialSymbols.TRENDING_UP,
                        iconTint = MaterialTheme.colorScheme.success
                    )
                    SheetAmountField(
                        value = interestAmountText,
                        onValueChange = { interestAmountText = it.filterCurrencyInput() },
                        label = stringResource(id = R.string.savings_interest_amount),
                        // The estimate is pre-filled; say where it came from and what's been earned so far
                        supportingText = listOfNotNull(
                            if (estimatedInterest >= 0.01) stringResource(id = R.string.savings_interest_estimate_hint, estimatedInterest.formatCurrency()) else null,
                            stringResource(id = R.string.savings_interest_total, account.totalInterestEarned.formatCurrency())
                        ).joinToString("\n"),
                        onImeAction = addInterest
                    )
                    SheetActions(
                        confirmLabel = stringResource(id = R.string.savings_add_interest_action),
                        onConfirm = addInterest,
                        confirmEnabled = interestAmount?.let { it > 0 } == true,
                        dismissLabel = stringResource(id = android.R.string.cancel),
                        onDismiss = { accountToAddInterest = null }
                    )
                }
            }
        }
    }
}

@Composable
private fun OverallSavingsSummaryCard(
    totalBalance: Double,
    accessibleBalance: Double,
    totalInterest: Double,
    avgApy: Double,
    accountCount: Int
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    ExpressiveCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = ExpressiveShapes.large,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentPadding = 20.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.savings_total_hisa_balance),
                        style = MaterialTheme.typography.labelLarge,
                        color = onContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = totalBalance.formatCurrency(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = pluralStringResource(
                            id = R.plurals.savings_account_count,
                            count = accountCount,
                            accountCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer.copy(alpha = 0.8f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format("%.2f%%", avgApy),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(id = R.string.savings_avg_apy),
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.8f)
                    )
                }
            }

            if (totalBalance - accessibleBalance > 0.005) {
                HorizontalDivider(color = onContainer.copy(alpha = 0.12f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(id = R.string.savings_available_now),
                            style = MaterialTheme.typography.labelMedium,
                            color = onContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = accessibleBalance.formatCurrency(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(id = R.string.savings_locked_amount),
                            style = MaterialTheme.typography.labelMedium,
                            color = onContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = (totalBalance - accessibleBalance).formatCurrency(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            if (totalInterest > 0) {
                HorizontalDivider(color = onContainer.copy(alpha = 0.12f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.TRENDING_UP,
                            contentDescription = null,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = stringResource(id = R.string.savings_total_interest_earned),
                            style = MaterialTheme.typography.labelMedium,
                            color = onContainer.copy(alpha = 0.8f)
                        )
                    }
                    Text(
                        text = "+" + totalInterest.formatCurrency(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.success
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySavingsCard(onAddAccount: () -> Unit) {
    ExpressiveEmptyState(
        icon = MaterialSymbols.SAVINGS,
        title = stringResource(id = R.string.savings_no_accounts_title),
        description = stringResource(id = R.string.savings_no_accounts_desc),
        action = {
            SparelyButton(
                onClick = onAddAccount,
                icon = {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.ADD,
                        contentDescription = null,
                        size = 20.dp
                    )
                }
            ) {
                Text(stringResource(id = R.string.savings_add_account))
            }
        }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun EnhancedSavingsAccountCard(
    account: SavingsAccount,
    onEditClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onUpdateBalanceClick: () -> Unit,
    onAddInterestClick: () -> Unit,
    onViewHistoryClick: () -> Unit,
    onDepositClick: () -> Unit = {},
    onWithdrawClick: () -> Unit = {},
    onRequestNoticeClick: () -> Unit = {},
    onCancelNoticeClick: () -> Unit = {},
    shape: androidx.compose.ui.graphics.Shape = ExpressiveShapes.large,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance().apply {
            isGroupingUsed = false
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val maturityDate = account.maturityDate

    ExpressiveCard(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        containerColor = colorScheme.surfaceContainer,
        contentPadding = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = ExpressiveShapes.small,
                            color = colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                MaterialSymbolIcon(
                                    icon = MaterialSymbols.SAVINGS,
                                    contentDescription = null,
                                    size = 24.dp,
                                    tint = colorScheme.primary
                                )
                            }
                        }
                        Column {
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            account.institution?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "${account.annualPercentageYield}% APY",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = stringResource(id = R.string.savings_current_balance),
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = account.currentBalance.formatCurrency(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (account.totalInterestEarned > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(id = R.string.savings_interest_earned_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "+" + account.totalInterestEarned.formatCurrency(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.success
                            )
                        }
                    }
                }

                // Term / notice details
                if (account.productType != SavingsProductType.FLEXIBLE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (account.productType == SavingsProductType.TERM) {
                                Text(
                                    text = stringResource(id = R.string.savings_term_label, account.termMonths ?: "-"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(id = R.string.savings_start_label, account.termStartDate?.format(dateFormatter) ?: "-"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(id = R.string.savings_maturity_label, maturityDate?.format(dateFormatter) ?: "-"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            if (account.productType == SavingsProductType.NOTICE) {
                                Text(
                                    text = stringResource(id = R.string.savings_notice_label, account.noticeDays ?: 0),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            account.minWithdrawalAmount?.let { min ->
                                Text(
                                    text = stringResource(id = R.string.savings_min_withdraw_label, min.formatCurrency()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.primary
                                )
                            }
                            account.minRemainingBalance?.let { min ->
                                Text(
                                    text = stringResource(id = R.string.savings_keep_label, min.formatCurrency()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                // Lock status, interest outlook and what can be spent right now
                val today = remember { java.time.LocalDate.now() }
                val access = account.accessStatus(today)
                val accessible = account.accessibleBalance(today)
                val locked = account.lockedBalance(today)
                if (account.currentBalance > 0.0) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (locked > 0.005) {
                            val untilText = account.accessibleFrom(today)?.format(dateFormatter)
                            val lockText = when (access) {
                                SavingsAccess.LOCKED_TERM -> stringResource(id = R.string.savings_locked_until, untilText ?: "-")
                                SavingsAccess.NOTICE_NOT_REQUESTED -> stringResource(id = R.string.savings_notice_needed, account.noticeDays ?: 0)
                                SavingsAccess.NOTICE_PENDING -> stringResource(id = R.string.savings_notice_ready_on, untilText ?: "-")
                                SavingsAccess.AVAILABLE -> stringResource(id = R.string.savings_keeps_minimum, (account.minRemainingBalance ?: 0.0).formatCurrency())
                            }
                            Text(
                                text = lockText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.secondary
                            )
                        }
                        Text(
                            text = stringResource(
                                id = R.string.savings_available_to_withdraw,
                                accessible.formatCurrency()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        if (account.annualPercentageYield > 0.0) {
                            Text(
                                text = stringResource(
                                    id = R.string.savings_monthly_interest_est,
                                    account.projectedMonthlyInterest.formatCurrency(),
                                    account.projectedAnnualInterest.formatCurrency()
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        account.projectedMaturityValue(today)?.takeIf { access != SavingsAccess.AVAILABLE }?.let { value ->
                            Text(
                                text = stringResource(id = R.string.savings_projected_maturity, value.formatCurrency()),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SparelyButton(
                        onClick = onDepositClick,
                        modifier = Modifier.weight(1f),
                        enabled = account.acceptsDeposits(today)
                    ) { Text(stringResource(id = R.string.savings_deposit)) }
                    if (access == SavingsAccess.NOTICE_NOT_REQUESTED) {
                        SparelyTonalButton(onClick = onRequestNoticeClick, modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.savings_notice_request))
                        }
                    } else {
                        SparelyTonalButton(onClick = onWithdrawClick, modifier = Modifier.weight(1f)) {
                            Text(stringResource(id = R.string.savings_withdraw))
                        }
                    }
                }
                if (access == SavingsAccess.NOTICE_PENDING) {
                    SparelyTextButton(onClick = onCancelNoticeClick) {
                        Text(stringResource(id = R.string.savings_notice_cancel))
                    }
                }

                // Badges
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeText = when (account.productType) {
                        SavingsProductType.TERM -> {
                            val term = account.termMonths?.let { stringResource(R.string.savings_badge_term_months, it) } ?: stringResource(R.string.savings_badge_term)
                            val start = account.termStartDate?.toString()?.let { " • " + stringResource(R.string.savings_start_label, it) } ?: ""
                            val penalty = account.earlyWithdrawalPenaltyDays?.let { " • " + stringResource(R.string.savings_badge_penalty, it) } ?: ""
                            stringResource(id = R.string.savings_badge_locked, "$term$start$penalty")
                        }
                        SavingsProductType.NOTICE -> stringResource(id = R.string.savings_badge_notice, account.noticeDays ?: 30)
                        SavingsProductType.FLEXIBLE -> stringResource(id = R.string.savings_badge_flexible)
                    }
                    Surface(shape = CircleShape, color = colorScheme.primary.copy(alpha = 0.1f)) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    account.minWithdrawalAmount?.let { min ->
                        Surface(shape = CircleShape, color = colorScheme.secondary.copy(alpha = 0.1f)) {
                            Text(
                                text = stringResource(id = R.string.savings_min_withdraw_label, min.formatCurrency()),
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    account.minRemainingBalance?.let { min ->
                        Surface(shape = CircleShape, color = colorScheme.tertiary.copy(alpha = 0.1f)) {
                            Text(
                                text = stringResource(id = R.string.savings_keep_label, min.formatCurrency()),
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SparelyTonalButton(
                            onClick = onViewHistoryClick,
                            icon = { MaterialSymbolIcon(icon = MaterialSymbols.HISTORY, contentDescription = null, size = 20.dp) }
                        ) { Text(stringResource(id = R.string.savings_history)) }

                        SparelyTonalButton(
                            onClick = onAddInterestClick,
                            containerColor = colorScheme.tertiaryContainer,
                            contentColor = colorScheme.onTertiaryContainer,
                            icon = { MaterialSymbolIcon(icon = MaterialSymbols.TRENDING_UP, contentDescription = null, size = 20.dp) }
                        ) { Text(stringResource(id = R.string.savings_interest)) }
                    }

                    Box {
                        IconButton(onClick = { expanded = true }) {
                            MaterialSymbolIcon(
                                icon = MaterialSymbols.SETTINGS,
                                contentDescription = stringResource(id = R.string.savings_more_options),
                                size = 20.dp,
                                tint = colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.savings_edit_details)) },
                                onClick = { onEditClick(); expanded = false },
                                leadingIcon = { MaterialSymbolIcon(icon = MaterialSymbols.EDIT, contentDescription = null, size = 20.dp) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.savings_update_balance_action)) },
                                onClick = { onUpdateBalanceClick(); expanded = false },
                                leadingIcon = { MaterialSymbolIcon(icon = MaterialSymbols.ATTACH_MONEY, contentDescription = null, size = 20.dp) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.savings_archive_action_short)) },
                                onClick = { onArchiveClick(); expanded = false },
                                leadingIcon = { MaterialSymbolIcon(icon = MaterialSymbols.DELETE, contentDescription = null, size = 20.dp) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SavingsAccountDialog(
    initialAccount: SavingsAccount? = null,
    isEdit: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (SavingsAccount) -> Unit
) {
    var name by remember { mutableStateOf(initialAccount?.name ?: "") }
    var apyText by remember { mutableStateOf(if ((initialAccount?.annualPercentageYield ?: 0.0) > 0) initialAccount?.annualPercentageYield.toString() else "") }
    var bank by remember { mutableStateOf(initialAccount?.institution ?: "") }
    var notes by remember { mutableStateOf(initialAccount?.accountNotes ?: "") }

    var productType by remember { mutableStateOf(initialAccount?.productType ?: SavingsProductType.FLEXIBLE) }
    var termMonthsText by remember { mutableStateOf(initialAccount?.termMonths?.toString() ?: "") }
    var noticeDaysText by remember { mutableStateOf(initialAccount?.noticeDays?.toString() ?: "") }
    var termStartText by remember { mutableStateOf(initialAccount?.termStartDate?.toString() ?: java.time.LocalDate.now().toString()) }
    var graceDaysText by remember { mutableStateOf(initialAccount?.gracePeriodDays?.toString() ?: "") }
    var anniversaryDaysText by remember { mutableStateOf(initialAccount?.anniversaryWindowDays?.toString() ?: "") }
    var minWithdrawalText by remember { mutableStateOf(initialAccount?.minWithdrawalAmount?.takeIf { it > 0 }?.toInputString() ?: "") }
    var minRemainingText by remember { mutableStateOf(initialAccount?.minRemainingBalance?.takeIf { it > 0 }?.toInputString() ?: "") }
    var penaltyDaysText by remember { mutableStateOf(initialAccount?.earlyWithdrawalPenaltyDays?.toString() ?: "") }

    var productMenuExpanded by remember { mutableStateOf(false) }

    var showStartPicker by remember { mutableStateOf(false) }
    val digitsKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
    val moneyKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SparelySheetHeader(
                    title = if (isEdit) stringResource(id = R.string.savings_edit_account) else stringResource(id = R.string.savings_add_savings_account),
                    icon = MaterialSymbols.SAVINGS
                )

                // ── The account ──
                SparelyTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(id = R.string.savings_account_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SparelyTextField(
                        value = apyText,
                        onValueChange = { apyText = it.filterCurrencyInput() },
                        label = { Text(stringResource(id = R.string.savings_apy_label)) },
                        suffix = { Text("%") },
                        singleLine = true,
                        keyboardOptions = moneyKeyboard,
                        modifier = Modifier.weight(0.8f)
                    )
                    SparelyTextField(
                        value = bank,
                        onValueChange = { bank = it },
                        label = { Text(stringResource(id = R.string.savings_institution_optional)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1.2f)
                    )
                }

                // ── How the money can move ──
                SheetSectionLabel(stringResource(id = R.string.savings_product_type))
                SheetChoiceRow(
                    options = SavingsProductType.entries.toList(),
                    selected = productType,
                    onSelect = { productType = it },
                    label = { type ->
                        when (type) {
                            SavingsProductType.FLEXIBLE -> stringResource(id = R.string.savings_product_flexible)
                            SavingsProductType.TERM -> stringResource(id = R.string.savings_product_term)
                            SavingsProductType.NOTICE -> stringResource(id = R.string.savings_product_notice)
                        }
                    }
                )

                when (productType) {
                    SavingsProductType.TERM -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SparelyTextField(
                                value = termMonthsText,
                                onValueChange = { termMonthsText = it.filter { ch -> ch.isDigit() } },
                                label = { Text(stringResource(id = R.string.savings_term_length_months)) },
                                singleLine = true,
                                keyboardOptions = digitsKeyboard,
                                modifier = Modifier.weight(1f)
                            )
                            // Picked from a calendar rather than typed as yyyy-mm-dd
                            Box(modifier = Modifier.weight(1f)) {
                                SparelyTextField(
                                    value = termStartText,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(id = R.string.savings_start_date)) },
                                    trailingIcon = {
                                        MaterialSymbolIcon(icon = MaterialSymbols.CALENDAR_MONTH, contentDescription = null, size = 20.dp)
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                // Covers the read-only field so a tap anywhere opens the picker
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { showStartPicker = true }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SparelyTextField(
                                value = graceDaysText,
                                onValueChange = { graceDaysText = it.filter { ch -> ch.isDigit() } },
                                label = { Text(stringResource(id = R.string.savings_grace_window_days)) },
                                singleLine = true,
                                keyboardOptions = digitsKeyboard,
                                modifier = Modifier.weight(1f)
                            )
                            SparelyTextField(
                                value = anniversaryDaysText,
                                onValueChange = { anniversaryDaysText = it.filter { ch -> ch.isDigit() } },
                                label = { Text(stringResource(id = R.string.savings_anniversary_window_days)) },
                                singleLine = true,
                                keyboardOptions = digitsKeyboard,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SparelyTextField(
                            value = penaltyDaysText,
                            onValueChange = { penaltyDaysText = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(id = R.string.savings_penalty_days)) },
                            singleLine = true,
                            keyboardOptions = digitsKeyboard,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SavingsProductType.NOTICE -> {
                        SparelyTextField(
                            value = noticeDaysText,
                            onValueChange = { noticeDaysText = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(id = R.string.savings_notice_days)) },
                            singleLine = true,
                            keyboardOptions = digitsKeyboard,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SavingsProductType.FLEXIBLE -> Unit
                }

                // ── Withdrawal limits ──
                SheetSectionLabel(stringResource(id = R.string.savings_section_withdrawal_rules))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SparelyTextField(
                        value = minWithdrawalText,
                        onValueChange = { minWithdrawalText = it.filterCurrencyInput() },
                        label = { Text(stringResource(id = R.string.savings_min_withdraw_amount)) },
                        singleLine = true,
                        prefix = { Text(stringResource(id = R.string.currency_prefix)) },
                        keyboardOptions = moneyKeyboard,
                        modifier = Modifier.weight(1f)
                    )
                    SparelyTextField(
                        value = minRemainingText,
                        onValueChange = { minRemainingText = it.filterCurrencyInput() },
                        label = { Text(stringResource(id = R.string.savings_min_remaining_balance)) },
                        singleLine = true,
                        prefix = { Text(stringResource(id = R.string.currency_prefix)) },
                        keyboardOptions = moneyKeyboard,
                        modifier = Modifier.weight(1f)
                    )
                }

                SparelyTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(id = R.string.savings_notes_optional)) },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SheetActions(
                confirmLabel = if (isEdit) stringResource(id = R.string.save_changes) else stringResource(id = R.string.savings_create_account),
                onConfirm = {
                    val sanitizedName = name.trim()
                    val apy = apyText.toSafeDouble() ?: 0.0
                    val termMonths = termMonthsText.toIntOrNull()?.takeIf { it > 0 }
                    val termStart = runCatching { java.time.LocalDate.parse(termStartText.takeIf { it.isNotBlank() } ?: java.time.LocalDate.now().toString()) }.getOrNull()
                    val noticeDays = noticeDaysText.toIntOrNull()?.takeIf { it > 0 }
                    val graceDays = graceDaysText.toIntOrNull()?.takeIf { it > 0 }
                    val anniversaryDays = anniversaryDaysText.toIntOrNull()?.takeIf { it > 0 }
                    val minWithdrawal = minWithdrawalText.toSafeDouble()?.takeIf { it > 0 }
                    val minRemaining = minRemainingText.toSafeDouble()?.takeIf { it > 0 }
                    val penaltyDays = penaltyDaysText.toIntOrNull()?.takeIf { it > 0 }

                    val updated = (initialAccount ?: SavingsAccount(name = sanitizedName, annualPercentageYield = apy)).copy(
                        name = sanitizedName,
                        annualPercentageYield = apy,
                        institution = bank.ifBlank { null },
                        accountNotes = notes.ifBlank { null },
                        productType = productType,
                        termMonths = if (productType == SavingsProductType.TERM) termMonths else null,
                        termStartDate = if (productType == SavingsProductType.TERM) termStart else null,
                        noticeDays = if (productType == SavingsProductType.NOTICE) noticeDays else null,
                        gracePeriodDays = if (productType == SavingsProductType.TERM) graceDays else null,
                        anniversaryWindowDays = if (productType == SavingsProductType.TERM) anniversaryDays else null,
                        minWithdrawalAmount = minWithdrawal,
                        minRemainingBalance = minRemaining,
                        earlyWithdrawalPenaltyDays = if (productType == SavingsProductType.TERM) penaltyDays else null
                    )

                    onConfirm(updated)
                },
                confirmEnabled = name.isNotBlank(),
                dismissLabel = stringResource(id = android.R.string.cancel),
                onDismiss = onDismiss
            )
        }
    }

    if (showStartPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { java.time.LocalDate.parse(termStartText) }.getOrNull()
                ?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                SparelyTextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        termStartText = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showStartPicker = false
                }) {
                    Text(stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                SparelyTextButton(onClick = { showStartPicker = false }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

