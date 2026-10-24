@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.sparely.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.SavingsAccess
import com.example.sparely.domain.model.SavingsAccount
import com.example.sparely.domain.model.SavingsProductType
import com.example.sparely.domain.model.WithdrawalQuote
import com.example.sparely.ui.components.ExpressiveEmptyState
import com.example.sparely.ui.components.ExpressiveListHeader
import com.example.sparely.ui.components.SegmentedListItem
import com.example.sparely.ui.components.SheetActions
import com.example.sparely.ui.components.SheetAmountField
import com.example.sparely.ui.components.SheetChoiceRow
import com.example.sparely.ui.components.SheetSectionLabel
import com.example.sparely.ui.components.SingleLineText
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelySheetHeader
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyTextField
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.components.segmentSpacing
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.theme.ExpressiveShapes
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.utils.filterAmountExpressionInput
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.toInputString
import com.example.sparely.ui.utils.toSafeDouble
import com.sparely.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** An APY as people read it in their language: "2.50%", "2,50 %". */
internal fun formatApy(apy: Double): String {
    val locale = java.util.Locale.getDefault()
    val number = String.format(locale, "%.2f", apy)
    return if (locale.language == "fr") "$number %" else "$number%"
}

private val savingsDate: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@DrawableRes
private fun SavingsAccount.icon(): Int = when (productType) {
    SavingsProductType.FLEXIBLE -> MaterialSymbols.SAVINGS
    SavingsProductType.TERM -> MaterialSymbols.LOCK
    SavingsProductType.NOTICE -> MaterialSymbols.SCHEDULE
}

@Composable
private fun SavingsProductType.label(): String = when (this) {
    SavingsProductType.FLEXIBLE -> stringResource(R.string.savings_badge_flexible)
    SavingsProductType.TERM -> stringResource(R.string.sav_kind_term)
    SavingsProductType.NOTICE -> stringResource(R.string.sav_kind_notice)
}

/** Why part of the money can't be taken out right now, or null when all of it can. */
@Composable
private fun lockText(account: SavingsAccount, today: LocalDate): String? {
    if (account.lockedBalance(today) <= 0.005) return null
    val until = account.accessibleFrom(today)?.format(savingsDate) ?: "-"
    return when (account.accessStatus(today)) {
        SavingsAccess.LOCKED_TERM -> stringResource(R.string.savings_locked_until, until)
        SavingsAccess.NOTICE_NOT_REQUESTED -> stringResource(R.string.savings_notice_needed, account.noticeDays ?: 0)
        SavingsAccess.NOTICE_PENDING -> stringResource(R.string.savings_notice_ready_on, until)
        SavingsAccess.AVAILABLE -> stringResource(R.string.savings_keeps_minimum, (account.minRemainingBalance ?: 0.0).formatCurrency())
    }
}

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
    var openId by remember { mutableStateOf<Long?>(null) }
    var accountToEdit by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountToArchive by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountToUpdateBalance by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountToAddInterest by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountToDeposit by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountToWithdraw by remember { mutableStateOf<SavingsAccount?>(null) }
    val today = remember { LocalDate.now() }

    val accounts = remember(savingsAccounts) { savingsAccounts.sortedByDescending { it.currentBalance } }
    val totalBalance = accounts.sumOf { it.currentBalance }
    val totalAccessible = accounts.sumOf { it.accessibleBalance(today) }

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
            // Extra bottom padding so the last row can scroll clear of the FAB
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
        ) {
            if (accounts.isEmpty()) {
                item(key = "empty") { EmptySavingsCard(onAddAccount = { showAddDialog = true }) }
            } else {
                item(key = "hero") {
                    SavingsHero(
                        totalBalance = totalBalance,
                        locked = totalBalance - totalAccessible,
                        monthlyInterest = accounts.sumOf { it.projectedMonthlyInterest },
                        interestEarned = accounts.sumOf { it.totalInterestEarned },
                        accountCount = accounts.size
                    )
                }
                item(key = "header") {
                    ExpressiveListHeader(
                        title = stringResource(R.string.sav_accounts_header),
                        icon = MaterialSymbols.ACCOUNT_BALANCE,
                        trailing = accounts.size.toString()
                    )
                }
                itemsIndexed(accounts, key = { _, account -> account.id }) { index, account ->
                    SavingsAccountRow(
                        account = account,
                        today = today,
                        shape = segmentedShape(index, accounts.size),
                        modifier = Modifier.segmentSpacing(index),
                        onClick = { openId = account.id }
                    )
                }
            }
        }

        // Looked up each time so the sheet shows the balance after a deposit or a correction
        accounts.find { it.id == openId }?.let { account ->
            SavingsAccountSheet(
                account = account,
                today = today,
                onDismiss = { openId = null },
                onDeposit = { accountToDeposit = account },
                onWithdraw = { accountToWithdraw = account },
                onRequestNotice = { onRequestNotice(account.id) },
                onCancelNotice = { onCancelNotice(account.id) },
                onAddInterest = { accountToAddInterest = account },
                onCorrectBalance = { accountToUpdateBalance = account },
                onHistory = {
                    openId = null
                    onViewHistory(account.id, account.name)
                },
                onEdit = { accountToEdit = account },
                onArchive = { accountToArchive = account }
            )
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
                            openId = null
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
                        onValueChange = { newBalanceText = it.filterAmountExpressionInput() },
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
                        onValueChange = { amountText = it.filterAmountExpressionInput() },
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
                        onValueChange = { amountText = it.filterAmountExpressionInput() },
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
                        onValueChange = { interestAmountText = it.filterAmountExpressionInput() },
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

/** The total in every account, what part is locked, and what the interest brings in. */
@Composable
private fun SavingsHero(
    totalBalance: Double,
    locked: Double,
    monthlyInterest: Double,
    interestEarned: Double,
    accountCount: Int
) {
    val colors = MaterialTheme.colorScheme
    val onContainer = colors.onPrimaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = colors.primaryContainer,
        contentColor = onContainer
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(
                    stringResource(R.string.sav_hero_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = onContainer.copy(alpha = 0.8f)
                )
                SingleLineText(
                    text = totalBalance.formatCurrency(),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = onContainer
                )
                Text(
                    pluralStringResource(R.plurals.savings_account_count, accountCount, accountCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = 0.8f)
                )
            }
            if (monthlyInterest >= 0.01 || interestEarned > 0.0 || locked > 0.005) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (monthlyInterest >= 0.01) {
                        HeroPill(MaterialSymbols.TRENDING_UP, stringResource(R.string.sav_hero_monthly_interest, monthlyInterest.formatCurrency()))
                    }
                    if (interestEarned > 0.0) {
                        HeroPill(MaterialSymbols.SAVINGS, stringResource(R.string.sav_hero_earned, interestEarned.formatCurrency()))
                    }
                    if (locked > 0.005) {
                        HeroPill(MaterialSymbols.LOCK, stringResource(R.string.sav_hero_locked, locked.formatCurrency()))
                    }
                }
            }
        }
    }
}

/** One account: what kind it is, its bank and rate, whether money is locked, its balance. */
@Composable
private fun SavingsAccountRow(
    account: SavingsAccount,
    today: LocalDate,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val lock = lockText(account, today)
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = colors.surfaceContainer) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                MaterialSymbolIcon(icon = account.icon(), contentDescription = null, size = 22.dp, tint = colors.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    account.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(account.institution, stringResource(R.string.sav_rate, formatApy(account.annualPercentageYield))).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                lock?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(account.currentBalance.formatCurrency(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (account.projectedMonthlyInterest >= 0.01) {
                    Text(
                        stringResource(R.string.sav_per_month, account.projectedMonthlyInterest.formatCurrency()),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.success
                    )
                }
            }
        }
    }
}

/**
 * Everything about one account: its balance and what can be taken out, deposit and withdraw,
 * its terms, and the less frequent actions (interest, history, correct the balance, edit, archive).
 */
@Composable
private fun SavingsAccountSheet(
    account: SavingsAccount,
    today: LocalDate,
    onDismiss: () -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onRequestNotice: () -> Unit,
    onCancelNotice: () -> Unit,
    onAddInterest: () -> Unit,
    onCorrectBalance: () -> Unit,
    onHistory: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val access = account.accessStatus(today)
    val lock = lockText(account, today)

    SparelyBottomSheet(isOpen = true, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SparelySheetHeader(
                title = account.name,
                subtitle = listOfNotNull(account.institution, account.productType.label()).joinToString(" · "),
                icon = account.icon()
            )

            // Balance, rate, and what's locked
            Surface(shape = ExpressiveShapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.savings_current_balance), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
                    Text(account.currentBalance.formatCurrency(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.sav_rate, formatApy(account.annualPercentageYield)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    lock?.let {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MaterialSymbolIcon(icon = MaterialSymbols.LOCK, contentDescription = null, size = 16.dp, tint = colors.onPrimaryContainer)
                            Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(stringResource(R.string.sav_tile_available), account.accessibleBalance(today).formatCurrency(decimals = 0), Modifier.weight(1f).fillMaxHeight())
                StatTile(stringResource(R.string.sav_tile_earned), account.totalInterestEarned.formatCurrency(decimals = 0), Modifier.weight(1f).fillMaxHeight())
                StatTile(stringResource(R.string.sav_tile_per_month), account.projectedMonthlyInterest.formatCurrency(), Modifier.weight(1f).fillMaxHeight())
            }

            // Moving money
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SparelyButton(
                    onClick = onDeposit,
                    modifier = Modifier.weight(1f),
                    enabled = account.acceptsDeposits(today),
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.ARROW_DOWNWARD, contentDescription = null, size = 18.dp) }
                ) { Text(stringResource(R.string.savings_deposit)) }
                if (access == SavingsAccess.NOTICE_NOT_REQUESTED) {
                    SparelyTonalButton(onClick = onRequestNotice, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.savings_notice_request), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    SparelyTonalButton(
                        onClick = onWithdraw,
                        modifier = Modifier.weight(1f),
                        icon = { MaterialSymbolIcon(icon = MaterialSymbols.ARROW_UPWARD, contentDescription = null, size = 18.dp) }
                    ) { Text(stringResource(R.string.savings_withdraw)) }
                }
            }
            if (access == SavingsAccess.NOTICE_PENDING) {
                SparelyTextButton(onClick = onCancelNotice) { Text(stringResource(R.string.savings_notice_cancel)) }
            }

            // Its terms, only the ones that apply
            val details = buildList {
                if (account.productType == SavingsProductType.TERM) {
                    account.termMonths?.let { add(stringResource(R.string.sav_d_term) to pluralStringResource(R.plurals.sav_months, it, it)) }
                    account.termStartDate?.let { add(stringResource(R.string.sav_d_start) to it.format(savingsDate)) }
                    account.maturityDate?.let { add(stringResource(R.string.sav_d_maturity) to it.format(savingsDate)) }
                    account.projectedMaturityValue(today)?.let { add(stringResource(R.string.sav_d_at_maturity) to it.formatCurrency()) }
                    account.earlyWithdrawalPenaltyDays?.let { add(stringResource(R.string.sav_d_penalty) to pluralStringResource(R.plurals.sav_days_interest, it, it)) }
                }
                if (account.productType == SavingsProductType.NOTICE) {
                    account.noticeDays?.let { add(stringResource(R.string.sav_d_notice) to pluralStringResource(R.plurals.sav_days, it, it)) }
                }
                account.minWithdrawalAmount?.let { add(stringResource(R.string.sav_d_min_withdraw) to it.formatCurrency()) }
                account.minRemainingBalance?.let { add(stringResource(R.string.sav_d_keep) to it.formatCurrency()) }
                if (account.annualPercentageYield > 0.0) add(stringResource(R.string.sav_d_per_year) to account.projectedAnnualInterest.formatCurrency())
            }
            if (details.isNotEmpty()) {
                ExpressiveListHeader(title = stringResource(R.string.sav_details_header), icon = MaterialSymbols.INFO)
                Column {
                    details.forEachIndexed { index, (label, value) ->
                        SegmentedListItem(
                            title = label,
                            shape = segmentedShape(index, details.size),
                            modifier = Modifier.segmentSpacing(index),
                            containerColor = colors.surfaceContainerHigh,
                            trailing = { Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
            account.accountNotes?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SparelyTonalButton(
                    onClick = onAddInterest,
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.TRENDING_UP, contentDescription = null, size = 18.dp) }
                ) { Text(stringResource(R.string.sav_add_interest)) }
                SparelyTonalButton(
                    onClick = onHistory,
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.HISTORY, contentDescription = null, size = 18.dp) }
                ) { Text(stringResource(R.string.savings_history)) }
                SparelyTonalButton(
                    onClick = onCorrectBalance,
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.ATTACH_MONEY, contentDescription = null, size = 18.dp) }
                ) { Text(stringResource(R.string.savings_update_balance_action)) }
                SparelyTonalButton(
                    onClick = onEdit,
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.EDIT, contentDescription = null, size = 18.dp) }
                ) { Text(stringResource(R.string.action_edit_short)) }
            }

            OutlinedButton(
                onClick = onArchive,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error)
            ) {
                MaterialSymbolIcon(icon = MaterialSymbols.DELETE, contentDescription = null, size = 18.dp, tint = colors.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.savings_archive_action_short))
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

