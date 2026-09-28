package com.example.sparely.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.toInputString
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.sparely.domain.model.SavingsAccount
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsAccountsScreen(
    savingsAccounts: List<SavingsAccount>,
    onNavigateBack: () -> Unit,
    onAddAccount: (SavingsAccount) -> Unit,
    onUpdateAccount: (SavingsAccount) -> Unit,
    onArchiveAccount: (Long) -> Unit,
    onAddInterest: (Long, Double) -> Unit,
    onViewHistory: (Long, String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountToArchive by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountToUpdateBalance by remember { mutableStateOf<SavingsAccount?>(null) }
    var accountToAddInterest by remember { mutableStateOf<SavingsAccount?>(null) }
    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance().apply {
            isGroupingUsed = false
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    // Calculate summary stats
    val totalBalance = remember(savingsAccounts) { savingsAccounts.sumOf { it.currentBalance } }
    val totalInterest = remember(savingsAccounts) { savingsAccounts.sumOf { it.totalInterestEarned } }
    val avgApy = remember(savingsAccounts) {
        if (savingsAccounts.isNotEmpty()) savingsAccounts.map { it.annualPercentageYield }.average() else 0.0
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.ADD,
                        contentDescription = stringResource(id = R.string.savings_add_account),
                        size = 24.dp
                    )
                    Text(stringResource(id = R.string.savings_add_account), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overall Summary Card
            if (savingsAccounts.isNotEmpty()) {
                item {
                    OverallSavingsSummaryCard(
                        totalBalance = totalBalance,
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
                items(savingsAccounts, key = { it.id }) { account ->
                    EnhancedSavingsAccountCard(
                        account = account,
                        onEditClick = { accountToEdit = account },
                        onArchiveClick = { accountToArchive = account },
                        onUpdateBalanceClick = { accountToUpdateBalance = account },
                        onAddInterestClick = { accountToAddInterest = account },
                        onViewHistoryClick = { onViewHistory(account.id, account.name) }
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
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.savings_archive_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.savings_archive_confirm, account.name),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SparelyTonalButton(
                            onClick = { accountToArchive = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(id = android.R.string.cancel))
                        }
                        SparelyButton(
                            onClick = {
                                onArchiveAccount(account.id)
                                accountToArchive = null
                            },
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text(stringResource(id = R.string.savings_archive_action))
                        }
                    }
                }
            }
        }

        accountToUpdateBalance?.let { account ->
            var newBalanceText by remember { mutableStateOf(account.currentBalance.toInputString()) }
            SparelyBottomSheet(
                isOpen = true,
                onDismiss = { accountToUpdateBalance = null }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.savings_update_balance_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.savings_update_balance_desc, account.name),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    SparelyTextField(
                        value = newBalanceText,
                        onValueChange = { newBalanceText = it.filterCurrencyInput() },
                        label = { Text(stringResource(id = R.string.savings_current_balance)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SparelyTonalButton(
                            onClick = { accountToUpdateBalance = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(id = android.R.string.cancel))
                        }
                        SparelyButton(
                            onClick = {
                                val newBalance = newBalanceText.toSafeDouble()
                                if (newBalance != null) {
                                    onUpdateAccount(account.copy(currentBalance = newBalance))
                                    accountToUpdateBalance = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(id = R.string.savings_update_balance_action))
                        }
                    }
                }
            }
        }

        // Add Interest Dialog
        accountToAddInterest?.let { account ->
            var interestAmountText by remember { mutableStateOf("") }
            SparelyBottomSheet(
                isOpen = true,
                onDismiss = { accountToAddInterest = null }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.savings_add_interest_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.savings_add_interest_desc, account.name),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(
                            id = R.string.savings_interest_total,
                            account.totalInterestEarned.formatCurrency()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SparelyTextField(
                        value = interestAmountText,
                        onValueChange = { interestAmountText = it.filterCurrencyInput() },
                        label = { Text(stringResource(id = R.string.savings_interest_amount)) },
                        singleLine = true,
                        prefix = { Text("$") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SparelyTonalButton(
                            onClick = { accountToAddInterest = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(id = android.R.string.cancel))
                        }
                        SparelyButton(
                            onClick = {
                                val interestAmount = interestAmountText.toSafeDouble()
                                if (interestAmount != null && interestAmount > 0) {
                                    onAddInterest(account.id, interestAmount)
                                    accountToAddInterest = null
                                }
                            },
                            enabled = interestAmountText.toSafeDouble()?.let { it > 0 } == true,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(id = R.string.savings_add_interest_action))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverallSavingsSummaryCard(
    totalBalance: Double,
    totalInterest: Double,
    avgApy: Double,
    accountCount: Int
) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = totalBalance.formatCurrency(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = pluralStringResource(
                            id = R.plurals.savings_account_count,
                            count = accountCount,
                            accountCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (totalInterest > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "+" + totalInterest.formatCurrency(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySavingsCard(onAddAccount: () -> Unit) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = 32.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MaterialSymbolIcon(
                icon = MaterialSymbols.SAVINGS,
                contentDescription = null,
                size = 64.dp,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                text = stringResource(id = R.string.savings_no_accounts_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(id = R.string.savings_no_accounts_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            SparelyButton(
                onClick = onAddAccount,
                modifier = Modifier.fillMaxWidth(0.6f),
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
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun EnhancedSavingsAccountCard(
    account: SavingsAccount,
    onEditClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onUpdateBalanceClick: () -> Unit,
    onAddInterestClick: () -> Unit,
    onViewHistoryClick: () -> Unit
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
    val maturityDate = remember(account.termStartDate, account.termMonths) {
        if (account.termStartDate != null && account.termMonths != null)
            runCatching { account.termStartDate.plusMonths(account.termMonths.toLong()) }.getOrNull()
        else null
    }

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        containerColor = colorScheme.surfaceContainerHigh,
        contentPadding = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colorScheme.primary.copy(alpha = 0.05f),
                                colorScheme.surface.copy(alpha = 0.5f)
                            )
                        )
                    )
            )
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
                            shape = CircleShape,
                            color = colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(48.dp)
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
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
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
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.tertiary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${account.annualPercentageYield}% APY",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.tertiary,
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
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold
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
                                color = colorScheme.tertiary
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

                HorizontalDivider(color = colorScheme.onSurface.copy(alpha = 0.1f))

                // Badges
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeText = when (account.productType) {
                        SavingsProductType.TERM -> {
                            val term = account.termMonths?.let { "$it mo" } ?: "Term"
                            val start = account.termStartDate?.toString()?.let { " • Start $it" } ?: ""
                            val penalty = account.earlyWithdrawalPenaltyDays?.let { " • Penalty ${it}d interest" } ?: ""
                            stringResource(id = R.string.savings_badge_locked, "$term$start$penalty")
                        }
                        SavingsProductType.NOTICE -> stringResource(id = R.string.savings_badge_notice, account.noticeDays ?: 30)
                        SavingsProductType.FLEXIBLE -> stringResource(id = R.string.savings_badge_flexible)
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = colorScheme.primary.copy(alpha = 0.08f)) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    account.minWithdrawalAmount?.let { min ->
                        Surface(shape = RoundedCornerShape(8.dp), color = colorScheme.secondary.copy(alpha = 0.08f)) {
                            Text(
                                text = stringResource(id = R.string.savings_min_withdraw_label, min.formatCurrency()),
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    account.minRemainingBalance?.let { min ->
                        Surface(shape = RoundedCornerShape(8.dp), color = colorScheme.tertiary.copy(alpha = 0.08f)) {
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

    SparelyBottomSheet(
        isOpen = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
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
                    text = if (isEdit) stringResource(id = R.string.savings_edit_account) else stringResource(id = R.string.savings_add_savings_account),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                SparelyTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(id = R.string.savings_account_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SparelyTextField(
                    value = apyText,
                    onValueChange = { apyText = it.filterCurrencyInput() },
                    label = { Text(stringResource(id = R.string.savings_apy_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SparelyTextField(
                    value = bank,
                    onValueChange = { bank = it },
                    label = { Text(stringResource(id = R.string.savings_institution_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SparelyTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(id = R.string.savings_notes_optional)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Box {
                    SparelyTextField(
                        value = when (productType) {
                            SavingsProductType.FLEXIBLE -> stringResource(id = R.string.savings_product_flexible)
                            SavingsProductType.TERM -> stringResource(id = R.string.savings_product_term)
                            SavingsProductType.NOTICE -> stringResource(id = R.string.savings_product_notice)
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.savings_product_type)) },
                        trailingIcon = {
                            IconButton(onClick = { productMenuExpanded = !productMenuExpanded }) {
                                MaterialSymbolIcon(
                                    icon = if (productMenuExpanded) MaterialSymbols.ARROW_DROP_UP else MaterialSymbols.ARROW_DROP_DOWN,
                                    contentDescription = null,
                                    size = 20.dp
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { productMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = productMenuExpanded,
                        onDismissRequest = { productMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.savings_product_flexible)) },
                            onClick = {
                                productType = SavingsProductType.FLEXIBLE
                                productMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.savings_product_term)) },
                            onClick = {
                                productType = SavingsProductType.TERM
                                productMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.savings_product_notice)) },
                            onClick = {
                                productType = SavingsProductType.NOTICE
                                productMenuExpanded = false
                            }
                        )
                    }
                }

                when (productType) {
                    SavingsProductType.TERM -> {
                        SparelyTextField(
                            value = termMonthsText,
                            onValueChange = { termMonthsText = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(id = R.string.savings_term_length_months)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        SparelyTextField(
                            value = termStartText,
                            onValueChange = { termStartText = it },
                            label = { Text(stringResource(id = R.string.savings_start_date)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        SparelyTextField(
                            value = graceDaysText,
                            onValueChange = { graceDaysText = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(id = R.string.savings_grace_window_days)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        SparelyTextField(
                            value = anniversaryDaysText,
                            onValueChange = { anniversaryDaysText = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(id = R.string.savings_anniversary_window_days)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        SparelyTextField(
                            value = penaltyDaysText,
                            onValueChange = { penaltyDaysText = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(id = R.string.savings_penalty_days)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SavingsProductType.NOTICE -> {
                        SparelyTextField(
                            value = noticeDaysText,
                            onValueChange = { noticeDaysText = it.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(id = R.string.savings_notice_days)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SavingsProductType.FLEXIBLE -> Unit
                }

                SparelyTextField(
                    value = minWithdrawalText,
                    onValueChange = { minWithdrawalText = it.filterCurrencyInput() },
                    label = { Text(stringResource(id = R.string.savings_min_withdraw_amount)) },
                    singleLine = true,
                    prefix = { Text("$") },
                    modifier = Modifier.fillMaxWidth()
                )

                SparelyTextField(
                    value = minRemainingText,
                    onValueChange = { minRemainingText = it.filterCurrencyInput() },
                    label = { Text(stringResource(id = R.string.savings_min_remaining_balance)) },
                    singleLine = true,
                    prefix = { Text("$") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SparelyButton(
                    onClick = {
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
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isEdit) stringResource(id = R.string.save_changes) else stringResource(id = R.string.savings_create_account))
                }
                SparelyTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            }
        }
    }
}

