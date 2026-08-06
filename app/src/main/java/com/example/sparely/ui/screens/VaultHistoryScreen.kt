package com.example.sparely.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.example.sparely.ui.components.ExpressiveCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sparely.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.VaultBalanceAdjustment
import com.example.sparely.domain.model.VaultAdjustmentType
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.utils.formatCurrency
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultHistoryScreen(
    vaultName: String,
    historyItems: List<com.example.sparely.domain.model.VaultHistoryItem>,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vault_history_title, vaultName)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        MaterialSymbolIcon(icon = MaterialSymbols.ARROW_BACK, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.HISTORY,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.vault_history_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.vault_history_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyItems) { item ->
                    HistoryItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(item: com.example.sparely.domain.model.VaultHistoryItem) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
    
    ExpressiveCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val (icon, color) = when (item) {
                is com.example.sparely.domain.model.HistoryContribution -> MaterialSymbols.ADD to MaterialTheme.colorScheme.primary
                is com.example.sparely.domain.model.HistoryAdjustment -> {
                    when (item.adjustment.type) {
                        VaultAdjustmentType.MANUAL_DEPOSIT -> MaterialSymbols.ADD to MaterialTheme.colorScheme.primary
                        VaultAdjustmentType.MANUAL_DEDUCTION -> MaterialSymbols.REMOVE to MaterialTheme.colorScheme.error
                        VaultAdjustmentType.MANUAL_EDIT -> MaterialSymbols.EDIT to MaterialTheme.colorScheme.tertiary
                        VaultAdjustmentType.AUTOMATIC_RECURRING_TRANSFER -> MaterialSymbols.SYNC to MaterialTheme.colorScheme.secondary
                    }
                }
            }

            MaterialSymbolIcon(
                icon = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
                size = 24.dp
            )
            
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
                         com.example.sparely.domain.model.VaultContributionSource.INTEREST -> "Interest Earned"
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
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
                    color = if (item.amount >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
