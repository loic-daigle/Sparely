@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.sparely.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.SavingsAccount
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.VaultContribution
import com.example.sparely.domain.model.VaultContributionSource
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.components.ExpressiveEmptyState
import com.example.sparely.ui.components.ExpressiveListHeader
import com.example.sparely.ui.components.SparelyAlertDialog
import com.example.sparely.ui.components.SparelyAmountTextField
import com.example.sparely.ui.components.SparelyBottomSheet
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelySheetHeader
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SheetActions
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.toInputString
import com.example.sparely.ui.utils.toSafeDouble
import com.sparely.app.R
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The transfers waiting to be made in the bank: the total with one button for all of them, then a
 * row per destination account with its bank account, a copy button for the amount and a done
 * button. A row opens to its transfers: swipe one right when it's made, left to cancel it, or tap it
 * to change its amount. Every action comes with an undo (the caller holds it back while the undo is
 * offered), so nothing asks for confirmation first.
 */
@Composable
fun VaultTransfersScreen(
    vaults: List<SmartVault>,
    savingsAccounts: List<SavingsAccount> = emptyList(),
    pendingContributions: List<VaultContribution>,
    onApproveGroup: (List<Long>) -> Unit,
    onCancelContribution: (Long) -> Unit = {},
    onUpdateContributionAmount: (Long, Double) -> Unit = { _, _ -> },
    onStartNotificationWorkflow: () -> Unit = {},
    onCopied: (String) -> Unit = {}
) {
    // Group by destination: a vault, or a savings account for HISA transfers
    val groups = remember(pendingContributions) {
        pendingContributions
            .groupBy { TransferDestination(it.vaultId, if (it.vaultId == null) it.savingsAccountId else null) }
            .entries.sortedByDescending { (_, list) -> list.sumOf { it.amount } }
    }
    var expanded by remember { mutableStateOf(emptySet<TransferDestination>()) }
    var editing by remember { mutableStateOf<VaultContribution?>(null) }
    var showHowItWorks by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    if (pendingContributions.isEmpty()) {
        // Nothing to do: the explanation is the page
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            ExpressiveEmptyState(
                icon = MaterialSymbols.CHECK_CIRCLE,
                title = stringResource(R.string.vault_transfers_empty_title),
                description = stringResource(R.string.vault_transfers_empty_desc)
            )
            HowItWorks()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)
    ) {
        item(key = "hero") {
            TransfersHero(
                total = pendingContributions.sumOf { it.amount },
                transferCount = pendingContributions.size,
                accountCount = groups.size,
                onAllDone = { onApproveGroup(pendingContributions.map { it.id }) },
                onGuide = onStartNotificationWorkflow,
                onHowItWorks = { showHowItWorks = true }
            )
        }
        item(key = "header") {
            ExpressiveListHeader(
                title = stringResource(R.string.vault_transfers_by_account),
                icon = MaterialSymbols.SWAP_HORIZ,
                trailing = groups.size.toString()
            )
        }
        groups.forEachIndexed { index, (destination, transfers) ->
            val isOpen = destination in expanded
            item(key = "dest_${destination.vaultId}_${destination.savingsAccountId}") {
                val total = transfers.sumOf { it.amount }
                DestinationRow(
                    name = destinationName(destination, vaults, savingsAccounts),
                    account = destinationAccount(destination, vaults, savingsAccounts),
                    sources = transfers.map { it.source }.distinct().map { sourceLabel(it) },
                    transferCount = transfers.size,
                    total = total,
                    isSavingsAccount = destination.savingsAccountId != null,
                    expanded = isOpen,
                    // Rows stack as one block; an open row joins its transfers below it
                    shape = segmentedShape(index, groups.size, outer = 24.dp).let { base ->
                        if (isOpen) segmentedTop(index) else base
                    },
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 2.dp),
                    onToggle = { expanded = if (isOpen) expanded - destination else expanded + destination },
                    onCopy = {
                        clipboard.setText(AnnotatedString(total.toInputString()))
                        onCopied(total.formatCurrency())
                    },
                    onDone = { onApproveGroup(transfers.map { it.id }) }
                )
            }
            if (isOpen) {
                val sorted = transfers.sortedByDescending { it.date }
                items(sorted, key = { "t_${it.id}" }) { transfer ->
                    val isLastOfGroup = transfer == sorted.last()
                    TransferRow(
                        transfer = transfer,
                        shape = if (isLastOfGroup && index == groups.size - 1) {
                            RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                        } else RoundedCornerShape(6.dp),
                        onDone = { onApproveGroup(listOf(transfer.id)) },
                        onCancel = { onCancelContribution(transfer.id) },
                        onClick = { editing = transfer }
                    )
                }
            }
        }
    }

    editing?.let { transfer ->
        EditTransferSheet(
            transfer = transfer,
            destination = destinationName(TransferDestination(transfer.vaultId, if (transfer.vaultId == null) transfer.savingsAccountId else null), vaults, savingsAccounts),
            onDismiss = { editing = null },
            onSave = { amount ->
                onUpdateContributionAmount(transfer.id, amount)
                editing = null
            },
            onCancelTransfer = {
                onCancelContribution(transfer.id)
                editing = null
            }
        )
    }

    if (showHowItWorks) {
        SparelyAlertDialog(
            onDismissRequest = { showHowItWorks = false },
            title = { Text(stringResource(R.string.vault_transfers_how_it_works)) },
            text = { HowItWorksSteps() },
            confirmButton = {
                SparelyTextButton(onClick = { showHowItWorks = false }) { Text(stringResource(R.string.action_close_short)) }
            }
        )
    }
}

/** Where a pending transfer goes: a vault, or (for HISA transfers) a savings account. */
private data class TransferDestination(val vaultId: Long?, val savingsAccountId: Long?)

@Composable
private fun destinationName(
    destination: TransferDestination,
    vaults: List<SmartVault>,
    savingsAccounts: List<SavingsAccount>
): String =
    vaults.find { it.id == destination.vaultId }?.name
        ?: savingsAccounts.find { it.id == destination.savingsAccountId }?.name
        ?: stringResource(R.string.vault_transfers_unknown_vault)

/** The real account the money goes to, as far as it's known: "Tangerine •••4821". */
private fun destinationAccount(
    destination: TransferDestination,
    vaults: List<SmartVault>,
    savingsAccounts: List<SavingsAccount>
): String? {
    fun lastDigits(number: String?) = number?.filter { it.isLetterOrDigit() }?.takeLast(4)?.takeIf { it.isNotEmpty() }?.let { "•••$it" }
    val vault = vaults.find { it.id == destination.vaultId }
    val account = savingsAccounts.find { it.id == destination.savingsAccountId }
    val parts = when {
        vault != null -> listOf(vault.accountType?.displayName, lastDigits(vault.accountNumber))
        account != null -> listOf(account.institution, lastDigits(account.accountNumber))
        else -> emptyList()
    }
    return parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotEmpty() }
}

@Composable
private fun sourceLabel(source: VaultContributionSource): String = stringResource(
    when (source) {
        VaultContributionSource.SAVING_TAX -> R.string.vault_transfers_source_saving_tax
        VaultContributionSource.INCOME -> R.string.vault_transfers_source_income
        VaultContributionSource.AUTO_DEPOSIT -> R.string.vault_transfers_source_auto_deposit
        VaultContributionSource.MANUAL -> R.string.vault_transfers_source_manual
        VaultContributionSource.TRANSFER -> R.string.vault_transfers_source_transfer
        VaultContributionSource.INTEREST -> R.string.vault_transfers_source_interest
    }
)

/** Top corners of a segmented row that has its transfers open below it. */
private fun segmentedTop(index: Int) =
    RoundedCornerShape(topStart = if (index == 0) 24.dp else 6.dp, topEnd = if (index == 0) 24.dp else 6.dp, bottomStart = 6.dp, bottomEnd = 6.dp)

@Composable
private fun TransfersHero(
    total: Double,
    transferCount: Int,
    accountCount: Int,
    onAllDone: () -> Unit,
    onGuide: () -> Unit,
    onHowItWorks: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = colors.primaryContainer,
        contentPadding = 20.dp
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.vault_transfers_hero_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        total.formatCurrency(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer
                    )
                    Text(
                        stringResource(
                            R.string.vault_transfers_hero_count,
                            pluralStringResource(R.plurals.vault_transfers_n_transfers, transferCount, transferCount),
                            pluralStringResource(R.plurals.vault_transfers_n_accounts, accountCount, accountCount)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                IconButton(onClick = onHowItWorks) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.INFO,
                        contentDescription = stringResource(R.string.vault_transfers_how_it_works),
                        size = 22.dp,
                        tint = colors.onPrimaryContainer
                    )
                }
            }
            SparelyButton(
                onClick = onAllDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                icon = { MaterialSymbolIcon(icon = MaterialSymbols.CHECK, contentDescription = null, size = 20.dp) }
            ) {
                Text(stringResource(R.string.vault_transfers_all_done))
            }
            SparelyTextButton(
                onClick = onGuide,
                modifier = Modifier.fillMaxWidth(),
                icon = { MaterialSymbolIcon(icon = MaterialSymbols.PLAY_ARROW, contentDescription = null, size = 18.dp) }
            ) {
                Text(stringResource(R.string.vault_transfers_guide))
            }
        }
    }
}

@Composable
private fun DestinationRow(
    name: String,
    account: String?,
    sources: List<String>,
    transferCount: Int,
    total: Double,
    isSavingsAccount: Boolean,
    expanded: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onDone: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = shape,
        color = colors.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                MaterialSymbolIcon(
                    icon = if (isSavingsAccount) MaterialSymbols.ACCOUNT_BALANCE else MaterialSymbols.SAVINGS,
                    contentDescription = null,
                    size = 22.dp,
                    tint = colors.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(account, sources.joinToString(", ").takeIf { it.isNotEmpty() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pluralStringResource(R.plurals.vault_transfers_n_transfers, transferCount, transferCount) +
                        if (expanded) " ▴" else " ▾",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    total.formatCurrency(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.CONTENT_COPY,
                            contentDescription = stringResource(R.string.vault_transfers_copy),
                            size = 18.dp
                        )
                    }
                    FilledIconButton(onClick = onDone, modifier = Modifier.size(40.dp)) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.CHECK,
                            contentDescription = stringResource(R.string.vault_transfers_mark_transferred),
                            size = 18.dp
                        )
                    }
                }
            }
        }
    }
}

/** One transfer under its open account: swipe right when made, left to cancel; tap to change it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferRow(
    transfer: VaultContribution,
    shape: androidx.compose.ui.graphics.Shape,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val dateFormat = remember {
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd"))
    }
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onDone()
                SwipeToDismissBoxValue.EndToStart -> onCancel()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            // The row leaves through the list (it's held back while the undo is offered)
            false
        }
    )
    SwipeToDismissBox(
        state = swipe,
        modifier = Modifier.padding(top = 2.dp),
        backgroundContent = {
            val style = when (swipe.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeStyle(colors.primary, colors.onPrimary, MaterialSymbols.CHECK, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> SwipeStyle(colors.errorContainer, colors.onErrorContainer, MaterialSymbols.CLOSE, Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> null
            }
            if (style != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(style.container)
                        .padding(horizontal = 24.dp),
                    contentAlignment = style.alignment
                ) {
                    MaterialSymbolIcon(icon = style.icon, contentDescription = null, tint = style.content)
                }
            }
        }
    ) {
        Surface(onClick = onClick, shape = shape, color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(start = 72.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        transfer.date.format(dateFormat),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        listOfNotNull(sourceLabel(transfer.source), transfer.note?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    transfer.amount.formatCurrency(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                MaterialSymbolIcon(
                    icon = MaterialSymbols.EDIT,
                    contentDescription = stringResource(R.string.vault_transfers_edit_amount),
                    size = 18.dp,
                    tint = colors.onSurfaceVariant
                )
            }
        }
    }
}

private data class SwipeStyle(
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
    val icon: Int,
    val alignment: Alignment
)

/** Change a transfer's amount (with the calculator keys), or cancel it. */
@Composable
private fun EditTransferSheet(
    transfer: VaultContribution,
    destination: String,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onCancelTransfer: () -> Unit
) {
    var amountText by remember(transfer.id) { mutableStateOf(transfer.amount.toInputString()) }
    val parsed = amountText.toSafeDouble()
    val isValid = parsed != null && parsed > 0
    val dateFormat = remember {
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd"))
    }
    SparelyBottomSheet(isOpen = true, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SparelySheetHeader(
                title = stringResource(R.string.vault_transfers_edit_amount),
                subtitle = listOfNotNull(destination, transfer.note?.takeIf { it.isNotBlank() }, transfer.date.format(dateFormat)).joinToString(" · "),
                icon = MaterialSymbols.EDIT
            )
            SparelyAmountTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text(stringResource(R.string.vault_amount_label)) },
                isError = !isValid,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = onCancelTransfer,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                MaterialSymbolIcon(icon = MaterialSymbols.DELETE, contentDescription = null, size = 18.dp, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.vault_transfers_cancel_transfer))
            }
            SheetActions(
                confirmLabel = stringResource(R.string.action_save),
                onConfirm = { parsed?.takeIf { it > 0 }?.let(onSave) },
                confirmEnabled = isValid,
                dismissLabel = stringResource(R.string.action_close_short),
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun HowItWorks() {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.vault_transfers_how_it_works),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HowItWorksSteps()
        }
    }
}

@Composable
private fun HowItWorksSteps() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(R.string.vault_transfers_step1, R.string.vault_transfers_step2, R.string.vault_transfers_step3)
            .forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "${i + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(stringResource(step), style = MaterialTheme.typography.bodyMedium)
                }
            }
    }
}
