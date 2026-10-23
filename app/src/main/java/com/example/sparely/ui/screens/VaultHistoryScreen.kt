package com.example.sparely.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import com.example.sparely.ui.components.ExpressiveEmptyState
import com.example.sparely.ui.components.segmentSpacing
import com.example.sparely.ui.components.segmentedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.example.sparely.ui.components.ExpressiveCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import com.example.sparely.ui.components.ExpressiveListHeader
import com.example.sparely.ui.components.SheetActions
import com.example.sparely.ui.components.SparelyAmountTextField
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelySheetHeader
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyTonalButton
import com.example.sparely.ui.utils.toInputString
import com.example.sparely.ui.utils.toSafeDouble
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sparely.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.VaultBalanceAdjustment
import com.example.sparely.domain.model.VaultAdjustmentType
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.success
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.utils.formatCurrency
import java.time.format.DateTimeFormatter

/**
 * A vault's page: how it's doing, add, withdraw, edit and adjust its balance, then its history.
 * The vaults list opens here when a vault is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultHistoryScreen(
    vaultName: String,
    historyItems: List<com.example.sparely.domain.model.VaultHistoryItem>,
    onNavigateBack: () -> Unit,
    vault: com.example.sparely.domain.model.SmartVault? = null,
    existingVaults: List<com.example.sparely.domain.model.SmartVault> = emptyList(),
    monthlyIncome: Double = 0.0,
    pending: Double = 0.0,
    onDeposit: (Double, String?, Boolean) -> Unit = { _, _, _ -> },
    onWithdraw: (Double, String?, Boolean) -> Unit = { _, _, _ -> },
    onUpdateVault: (com.example.sparely.domain.model.SmartVault) -> Unit = {},
    onDeleteVault: () -> Unit = {},
    onBalanceOverride: (Double, String?) -> Unit = { _, _ -> },
    onOpenTransfers: () -> Unit = {}
) {
    var showDeposit by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vault?.name ?: vaultName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        MaterialSymbolIcon(icon = MaterialSymbols.ARROW_BACK, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (vault != null) {
                        IconButton(onClick = { showEditor = true }) {
                            MaterialSymbolIcon(icon = MaterialSymbols.EDIT, contentDescription = stringResource(R.string.vault_management_edit_desc))
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
        ) {
            if (vault != null) {
                item(key = "summary") {
                    VaultSummaryCard(vault = vault, pending = pending, onOpenTransfers = onOpenTransfers)
                }
                item(key = "actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SparelyButton(
                            onClick = { showDeposit = true },
                            modifier = Modifier.weight(1f),
                            icon = { MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null, size = 18.dp) }
                        ) { Text(stringResource(R.string.vault_action_add_short), maxLines = 1) }
                        SparelyTonalButton(
                            onClick = { showWithdraw = true },
                            modifier = Modifier.weight(1f),
                            icon = { MaterialSymbolIcon(icon = MaterialSymbols.REMOVE, contentDescription = null, size = 18.dp) }
                        ) { Text(stringResource(R.string.vault_action_withdraw_short), maxLines = 1) }
                    }
                    SparelyTextButton(
                        onClick = { showAdjust = true },
                        modifier = Modifier.fillMaxWidth(),
                        icon = { MaterialSymbolIcon(icon = MaterialSymbols.EDIT, contentDescription = null, size = 18.dp) }
                    ) { Text(stringResource(R.string.vault_detail_adjust)) }
                }
            }
            item(key = "history_header") {
                ExpressiveListHeader(
                    title = stringResource(R.string.vault_action_history_short),
                    icon = MaterialSymbols.HISTORY,
                    trailing = historyItems.size.takeIf { it > 0 }?.toString()
                )
            }
            if (historyItems.isEmpty()) {
                item(key = "empty") {
                    ExpressiveEmptyState(
                        icon = MaterialSymbols.HISTORY,
                        title = stringResource(R.string.vault_history_empty_title),
                        description = stringResource(R.string.vault_history_empty_desc)
                    )
                }
            } else {
                // One segmented block: rounded ends, thin gaps between entries
                itemsIndexed(historyItems) { index, item ->
                    HistoryItemCard(
                        item = item,
                        shape = segmentedShape(index, historyItems.size),
                        modifier = Modifier.segmentSpacing(index)
                    )
                }
            }
        }
    }

    if (vault != null) {
        if (showDeposit || showWithdraw) {
            val isDeposit = showDeposit
            ManualAdjustmentDialog(
                vaultName = vault.name,
                currentBalance = vault.currentBalance,
                isDeposit = isDeposit,
                defaultAffectMainAccount = if (isDeposit) vault.defaultManualDepositDeductFromMain else vault.defaultManualWithdrawalCreditMain,
                onConfirm = { amount, reason, affectMain ->
                    if (isDeposit) onDeposit(amount, reason, affectMain) else onWithdraw(amount, reason, affectMain)
                    showDeposit = false
                    showWithdraw = false
                },
                onDismiss = {
                    showDeposit = false
                    showWithdraw = false
                }
            )
        }
        if (showEditor) {
            SmartVaultEditorDialog(
                vault = vault,
                existingVaults = existingVaults,
                monthlyIncome = monthlyIncome,
                onSave = {
                    onUpdateVault(it)
                    showEditor = false
                },
                onDelete = {
                    showEditor = false
                    showDelete = true
                },
                onDismiss = { showEditor = false }
            )
        }
        if (showDelete) {
            DeleteConfirmationDialog(
                vault = vault,
                onConfirm = {
                    showDelete = false
                    onDeleteVault()
                },
                onDismiss = { showDelete = false }
            )
        }
        if (showAdjust) {
            val note = stringResource(R.string.vault_balance_edited_note)
            AdjustBalanceSheet(
                vault = vault,
                onDismiss = { showAdjust = false },
                onSave = { newBalance ->
                    onBalanceOverride(newBalance, note)
                    showAdjust = false
                }
            )
        }
    }
}

/** The vault at a glance: icon, status, balance against target with its bar, what's on the way. */
@Composable
private fun VaultSummaryCard(
    vault: com.example.sparely.domain.model.SmartVault,
    pending: Double,
    onOpenTransfers: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val status = vaultStatus(vault)
    val accent = if (status.color == colors.onSurfaceVariant) colors.primary else status.color
    val progress = if (vault.targetAmount > 0) (vault.currentBalance / vault.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    ExpressiveCard(modifier = Modifier.fillMaxWidth(), containerColor = colors.primaryContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.surface.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialSymbolIcon(icon = vault.displayIcon(), contentDescription = null, size = 22.dp, tint = accent)
                }
                Text(
                    status.text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    vault.currentBalance.formatCurrency(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer
                )
                if (vault.targetAmount > 0) {
                    Text(
                        " / " + vault.targetAmount.formatCurrency(decimals = 0),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
            if (vault.targetAmount > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = colors.primary,
                    trackColor = colors.onPrimaryContainer.copy(alpha = 0.12f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
            if (pending > 0.005) {
                SparelyTextButton(
                    onClick = onOpenTransfers,
                    icon = { MaterialSymbolIcon(icon = MaterialSymbols.SWAP_HORIZ, contentDescription = null, size = 18.dp) }
                ) { Text(stringResource(R.string.vault_hero_on_the_way, pending.formatCurrency())) }
            }
        }
    }
}

/** Set the balance to what the account really holds; recorded in the history. */
@Composable
private fun AdjustBalanceSheet(
    vault: com.example.sparely.domain.model.SmartVault,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var text by remember { mutableStateOf(vault.currentBalance.toInputString()) }
    val parsed = text.toSafeDouble()
    val isValid = parsed != null && parsed >= 0
    SparelyBottomSheet(isOpen = true, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SparelySheetHeader(
                title = stringResource(R.string.vault_detail_adjust),
                subtitle = stringResource(R.string.vault_detail_adjust_desc),
                icon = MaterialSymbols.EDIT
            )
            SparelyAmountTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.vault_current_balance_label)) },
                prefix = { Text(stringResource(R.string.currency_prefix)) },
                isError = !isValid,
                modifier = Modifier.fillMaxWidth()
            )
            SheetActions(
                confirmLabel = stringResource(R.string.action_save),
                onConfirm = { parsed?.takeIf { it >= 0 }?.let(onSave) },
                confirmEnabled = isValid,
                dismissLabel = stringResource(R.string.action_cancel),
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: com.example.sparely.domain.model.VaultHistoryItem,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val (icon, color) = when (item) {
                is com.example.sparely.domain.model.HistoryContribution -> MaterialSymbols.ADD to MaterialTheme.colorScheme.success
                is com.example.sparely.domain.model.HistoryAdjustment -> {
                    when (item.adjustment.type) {
                        VaultAdjustmentType.MANUAL_DEPOSIT -> MaterialSymbols.ADD to MaterialTheme.colorScheme.success
                        VaultAdjustmentType.MANUAL_DEDUCTION -> MaterialSymbols.REMOVE to MaterialTheme.colorScheme.critical
                        VaultAdjustmentType.MANUAL_EDIT -> MaterialSymbols.EDIT to MaterialTheme.colorScheme.tertiary
                        VaultAdjustmentType.AUTOMATIC_RECURRING_TRANSFER -> MaterialSymbols.SYNC to MaterialTheme.colorScheme.success
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                MaterialSymbolIcon(icon = icon, contentDescription = null, tint = color, size = 22.dp)
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val title = when (item) {
                    is com.example.sparely.domain.model.HistoryContribution -> when(item.contribution.source) {
                         com.example.sparely.domain.model.VaultContributionSource.INCOME -> stringResource(R.string.vault_source_allocation)
                         com.example.sparely.domain.model.VaultContributionSource.SAVING_TAX -> stringResource(R.string.vault_source_tax)
                         com.example.sparely.domain.model.VaultContributionSource.AUTO_DEPOSIT -> stringResource(R.string.vault_source_schedule)
                         com.example.sparely.domain.model.VaultContributionSource.MANUAL -> stringResource(R.string.vault_source_manual)
                         com.example.sparely.domain.model.VaultContributionSource.TRANSFER -> stringResource(R.string.vault_adjust_type_transfer)
                         com.example.sparely.domain.model.VaultContributionSource.INTEREST -> stringResource(R.string.savings_interest_earned_label)
                    }
                    is com.example.sparely.domain.model.HistoryAdjustment -> when (item.adjustment.type) {
                        VaultAdjustmentType.MANUAL_DEPOSIT -> stringResource(R.string.vault_manual_deposit_title)
                        VaultAdjustmentType.MANUAL_DEDUCTION -> stringResource(R.string.vault_manual_withdrawal_title)
                        VaultAdjustmentType.MANUAL_EDIT -> stringResource(R.string.vault_manual_edit_title)
                        VaultAdjustmentType.AUTOMATIC_RECURRING_TRANSFER -> stringResource(R.string.vault_automatic_transfer_title)
                    }
                }
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                if (item.description?.isNotBlank() == true) {
                    Text(
                        text = item.description ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = item.date.atZone(java.time.ZoneId.systemDefault()).format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (item is com.example.sparely.domain.model.HistoryAdjustment) {
                    val oldBalance = item.balanceAfter - item.amount
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.vault_history_previous),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = oldBalance.formatCurrency(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        MaterialSymbolIcon(icon = MaterialSymbols.ARROW_FORWARD,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = item.balanceAfter.formatCurrency(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                val amountText = if (item.amount > 0) "+${item.amount.formatCurrency()}" 
                                 else "-${kotlin.math.abs(item.amount).formatCurrency()}"
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (item.amount >= 0) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.critical
                )
            }
        }
    }
}
