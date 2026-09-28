package com.example.sparely.ui.screens

import com.example.sparely.ui.utils.filterCurrencyInput
import androidx.compose.foundation.background
import com.example.sparely.ui.utils.toSafeDouble
import com.example.sparely.ui.utils.toInputString
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.MaterialSymbolIcon
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.sparely.ui.components.ExpressiveCard
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.SavingsAccount
import com.example.sparely.domain.model.SmartVault
import com.example.sparely.domain.model.VaultContribution
import com.example.sparely.domain.model.VaultContributionSource
import com.example.sparely.ui.components.SparelyAlertDialog
import com.example.sparely.ui.components.SparelyButton
import com.example.sparely.ui.components.SparelyTextButton
import com.example.sparely.ui.components.SparelyTonalButton
import com.sparely.app.R
import com.example.sparely.ui.theme.ExpressiveShapes
import com.example.sparely.ui.components.SparelyTextField
import com.example.sparely.ui.utils.formatCurrency
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun VaultTransfersScreen(
    vaults: List<SmartVault>,
    savingsAccounts: List<SavingsAccount> = emptyList(),
    pendingContributions: List<VaultContribution>,
    onApproveContribution: (Long) -> Unit,
    onApproveGroup: (List<Long>) -> Unit = { ids -> ids.forEach(onApproveContribution) },
    onCancelContribution: (Long) -> Unit = {},
    onUpdateContributionAmount: (Long, Double) -> Unit = { _, _ -> },
    onStartNotificationWorkflow: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    // Removed local TopAppBar - using global SparelyTopBar instead
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (pendingContributions.isEmpty()) {
            item {
                    EmptyStateCard()
                }
            } else {
                item {
                    SummaryCard(pendingContributions, vaults, savingsAccounts)
                }
                
                item {
                    NotificationWorkflowButton(onStartWorkflow = onStartNotificationWorkflow)
                }

                
                
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.vault_transfers_pending_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                val grouped = pendingContributions.groupBy { it.vaultId }
                items(grouped.entries.toList(), key = { it.key ?: -1L }) { (vaultId, contributionsForVault) ->
                    val vault = vaults.find { it.id == vaultId }
                    // Resolve display name: for HISA transfers (vaultId==null) look up the savings account
                    val displayName = if (vault != null) {
                        vault.name
                    } else {
                        val accountId = contributionsForVault.firstOrNull()?.savingsAccountId
                        savingsAccounts.find { it.id == accountId }?.name
                            ?: stringResource(R.string.vault_transfers_unknown_vault)
                    }
                    val isHisaTransfer = vaultId == null && contributionsForVault.any { it.savingsAccountId != null }
                    AggregatedPendingContributionCard(
                        vault = vault,
                        displayName = displayName,
                        isHisaTransfer = isHisaTransfer,
                        contributions = contributionsForVault,
                        onApproveAll = { ids -> onApproveGroup(ids) },
                        onApproveIndividual = onApproveContribution,
                        onCancelIndividual = onCancelContribution,
                        onUpdateAmount = onUpdateContributionAmount
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                InstructionsCard()
            }
        }
    }


@Composable
private fun EmptyStateCard() {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ExpressiveShapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = ExpressiveShapes.medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(80.dp)
            ) {
                 Box(contentAlignment = Alignment.Center) {
                     MaterialSymbolIcon(
                         icon = MaterialSymbols.CHECK_CIRCLE,
                         contentDescription = null,
                         tint = MaterialTheme.colorScheme.primary,
                         size = 40.dp
                     )
                 }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.vault_transfers_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.vault_transfers_empty_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    pendingContributions: List<VaultContribution>,
    vaults: List<SmartVault>,
    savingsAccounts: List<SavingsAccount> = emptyList()
) {
    val totalPending = pendingContributions.sumOf { it.amount }
    val vaultBreakdown = pendingContributions
        .groupBy { it.vaultId }
        .mapValues { (_, contributions) -> contributions.sumOf { it.amount } }
    
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primary, // Hero style
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = ExpressiveShapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.vault_transfers_ready),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
                Text(
                    text = totalPending.formatCurrency(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                vaultBreakdown.entries.take(4).forEach { (vaultId, amount) ->
                    val vault = vaults.find { it.id == vaultId }
                    // Resolve name: vault name or savings account name for HISA transfers
                    val entryName = vault?.name
                        ?: pendingContributions.firstOrNull { it.vaultId == vaultId }?.savingsAccountId
                            ?.let { accountId -> savingsAccounts.find { it.id == accountId }?.name }
                    if (entryName != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entryName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = amount.formatCurrency(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (vaultBreakdown.size > 4) {
                    Text(
                        text = stringResource(R.string.vault_transfers_more_vaults, vaultBreakdown.size - 4),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AggregatedPendingContributionCard(
    vault: SmartVault?,
    displayName: String = vault?.name ?: "Unknown",
    isHisaTransfer: Boolean = false,
    contributions: List<VaultContribution>,
    onApproveAll: (List<Long>) -> Unit,
    onApproveIndividual: (Long) -> Unit,
    onCancelIndividual: (Long) -> Unit = {},
    onUpdateAmount: (Long, Double) -> Unit = { _, _ -> }
) {
    var expanded by remember { mutableStateOf(false) }
    var showConfirmAll by remember { mutableStateOf(false) }
    var editingContributionId by remember { mutableStateOf<Long?>(null) }
    
    if (editingContributionId != null) {
        val contribution = contributions.find { it.id == editingContributionId }
        if (contribution != null) {
            EditAmountDialog(
                initialAmount = contribution.amount,
                onDismiss = { editingContributionId = null },
                onConfirm = { newAmount ->
                    onUpdateAmount(contribution.id, newAmount)
                    editingContributionId = null
                }
            )
        }
    }

    val totalAmount = contributions.sumOf { it.amount }
    val formatter = DateTimeFormatter.ofPattern("MMM dd")
    val sources = contributions.groupBy { it.source }

    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ExpressiveShapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                 Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                     // Vault Icon / Placeholder
                     Surface(
                         shape = ExpressiveShapes.small,
                         color = MaterialTheme.colorScheme.surface, 
                         modifier = Modifier.size(56.dp)
                     ) {
                         Box(contentAlignment = Alignment.Center) {
                             MaterialSymbolIcon(
                                 icon = if (isHisaTransfer) MaterialSymbols.ACCOUNT_BALANCE else MaterialSymbols.SAVINGS,
                                 contentDescription = null,
                                 tint = MaterialTheme.colorScheme.primary,
                                 size = 28.dp
                             )
                         }
                     }
                     Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.vault_transfers_pending_count, contributions.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                 }
                Text(
                    text = totalAmount.formatCurrency(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val sortedSources = sources.entries.sortedByDescending { it.value.sumOf(VaultContribution::amount) }
                for ((source, entries) in sortedSources) {
                    val sourceTotal = entries.sumOf { it.amount }
                    val sourceLabel = when (source) {
                        VaultContributionSource.SAVING_TAX -> stringResource(R.string.vault_transfers_source_saving_tax)
                        VaultContributionSource.INCOME -> stringResource(R.string.vault_transfers_source_income)
                        VaultContributionSource.AUTO_DEPOSIT -> stringResource(R.string.vault_transfers_source_auto_deposit)
                        VaultContributionSource.MANUAL -> stringResource(R.string.vault_transfers_source_manual)
                        VaultContributionSource.TRANSFER -> stringResource(R.string.vault_transfers_source_transfer)
                        VaultContributionSource.INTEREST -> "Interest"
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = ExpressiveShapes.extraSmall
                    ) {
                        Text(
                            text = "$sourceLabel: ${sourceTotal.formatCurrency()}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SparelyButton(
                    onClick = { showConfirmAll = true },
                    modifier = Modifier.weight(1f),
                    icon = {
                        MaterialSymbolIcon(icon = MaterialSymbols.CHECK_CIRCLE, contentDescription = null, size = 18.dp)
                    }
                ) {
                    Text(stringResource(R.string.vault_transfers_transfer_all))
                }
                SparelyTonalButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (expanded) stringResource(R.string.vault_transfers_hide_details) else stringResource(R.string.vault_transfers_view_details))
                }
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val sortedContributions = contributions.sortedByDescending { it.date }
                    for (contribution in sortedContributions) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                             Column(modifier = Modifier.weight(1f)) {
                                 Text(
                                     text = contribution.date.format(formatter),
                                     style = MaterialTheme.typography.bodyMedium,
                                     fontWeight = FontWeight.SemiBold
                                 )
                                  Text(
                                    text = when (contribution.source) {
                                            VaultContributionSource.SAVING_TAX -> stringResource(R.string.vault_transfers_source_saving_tax)
                                            else -> stringResource(R.string.vault_transfers_contribution)
                                        } + (contribution.note?.let { " • $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                 )
                             }
                             Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                 Text(
                                     text = contribution.amount.formatCurrency(),
                                     style = MaterialTheme.typography.bodyMedium,
                                     fontWeight = FontWeight.Bold
                                 )
                                 IconButton(onClick = { onApproveIndividual(contribution.id) }, modifier = Modifier.size(32.dp)) {
                                     MaterialSymbolIcon(icon = MaterialSymbols.CHECK, contentDescription = "Approve", size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                                 }
                                 IconButton(onClick = { editingContributionId = contribution.id }, modifier = Modifier.size(32.dp)) {
                                     MaterialSymbolIcon(icon = MaterialSymbols.EDIT, contentDescription = "Edit Amount", size = 20.dp, tint = MaterialTheme.colorScheme.primary)
                                 }
                                 IconButton(onClick = { onCancelIndividual(contribution.id) }, modifier = Modifier.size(32.dp)) {
                                     MaterialSymbolIcon(icon = MaterialSymbols.CLOSE, contentDescription = "Cancel", size = 20.dp, tint = MaterialTheme.colorScheme.error)
                                 }
                             }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmAll) {
        SparelyAlertDialog(
            onDismissRequest = { showConfirmAll = false },
            title = { Text(stringResource(R.string.vault_transfers_confirm_title)) },
            text = {
                Text(stringResource(
                    R.string.vault_transfers_confirm_desc,
                    totalAmount.formatCurrency(),
                    vault?.name ?: stringResource(R.string.vault_transfers_unknown_vault),
                    contributions.size
                ))
            },
            confirmButton = {
                SparelyButton(
                    onClick = {
                        onApproveAll(contributions.map { it.id })
                        showConfirmAll = false
                    }
                ) {
                    Text(stringResource(R.string.vault_transfers_yes_transferred))
                }
            },
            dismissButton = {
                SparelyTextButton(onClick = { showConfirmAll = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun InstructionsCard() {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ExpressiveShapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                   shape = ExpressiveShapes.small,
                   color = MaterialTheme.colorScheme.tertiaryContainer,
                   modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbolIcon(icon = MaterialSymbols.INFO,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            size = 20.dp
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.vault_transfers_how_it_works),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            InstructionStep(
                number = "1",
                text = stringResource(R.string.vault_transfers_step1)
            )
            InstructionStep(
                number = "2",
                text = stringResource(R.string.vault_transfers_step2)
            )
            InstructionStep(
                number = "3",
                text = stringResource(R.string.vault_transfers_step3)
            )
        }
    }
}

@Composable
private fun InstructionStep(number: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.tertiary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun NotificationWorkflowButton(onStartWorkflow: () -> Unit) {
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        shape = ExpressiveShapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .clickable(onClick = onStartWorkflow),
             horizontalArrangement = Arrangement.SpaceBetween,
             verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = stringResource(R.string.vault_transfers_smart_workflow),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                 Text(
                    text = stringResource(R.string.vault_transfers_workflow_guide),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            MaterialSymbolIcon(
                icon = MaterialSymbols.ARROW_FORWARD,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun EditAmountDialog(
    initialAmount: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf(initialAmount.toInputString()) }
    
    SparelyAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Amount") },
        text = {
            SparelyTextField(
                value = amountText,
                onValueChange = { amountText = it.filterCurrencyInput() },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
         confirmButton = {
            SparelyTextButton(
                onClick = {
                    val newAmount = amountText.toSafeDouble()
                    if (newAmount != null && newAmount > 0) {
                        onConfirm(newAmount)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            SparelyTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
