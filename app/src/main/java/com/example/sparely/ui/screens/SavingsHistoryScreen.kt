package com.example.sparely.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.SavingsAccountTransaction
import com.example.sparely.domain.model.SavingsAccountTransactionDisplayType
import com.example.sparely.ui.SparelyViewModel
import com.example.sparely.ui.components.ExpressiveCard
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.sparely.app.R
import com.example.sparely.ui.utils.formatCurrency
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsHistoryScreen(
    accountId: Long,
    accountName: String,
    viewModel: SparelyViewModel,
    onNavigateBack: () -> Unit
) {
    val transactions by viewModel.getAccountTransactions(accountId).collectAsState(initial = emptyList())
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(text = "$accountName History")
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.ARROW_BACK, 
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (transactions.isEmpty()) {
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
                        size = 64.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "No Transaction History",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Deposits, withdrawals, and interest will appear here",
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
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
            ) {
                items(transactions) { transaction ->
                    SavingsTransactionCard(transaction = transaction, dateFormatter = dateFormatter)
                }
            }
        }
    }
}

@Composable
fun SavingsTransactionCard(
    transaction: SavingsAccountTransaction,
    dateFormatter: DateTimeFormatter
) {
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
            // Icon based on type
            val iconTintPair: Pair<Int, androidx.compose.ui.graphics.Color> = when (transaction.type) {
                SavingsAccountTransactionDisplayType.DEPOSIT -> 
                    MaterialSymbols.ARROW_DOWNWARD to MaterialTheme.colorScheme.primary
                SavingsAccountTransactionDisplayType.WITHDRAWAL -> 
                    MaterialSymbols.ARROW_UPWARD to MaterialTheme.colorScheme.error
                SavingsAccountTransactionDisplayType.INTEREST -> 
                    MaterialSymbols.TRENDING_UP to MaterialTheme.colorScheme.tertiary
                SavingsAccountTransactionDisplayType.TRANSFER_IN -> 
                    MaterialSymbols.SWAP_HORIZ to MaterialTheme.colorScheme.primary
                SavingsAccountTransactionDisplayType.TRANSFER_OUT -> 
                    MaterialSymbols.SWAP_HORIZ to MaterialTheme.colorScheme.error
            }
            val (icon, tint) = iconTintPair

            MaterialSymbolIcon(
                icon = icon,
                contentDescription = null,
                tint = tint,
                size = 24.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = transaction.timestamp.format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Show balance transition
                if (transaction.balanceAfter > 0) {
                    val previousBalance = when (transaction.type) {
                        SavingsAccountTransactionDisplayType.DEPOSIT,
                        SavingsAccountTransactionDisplayType.INTEREST,
                        SavingsAccountTransactionDisplayType.TRANSFER_IN -> 
                            transaction.balanceAfter - transaction.amount
                        SavingsAccountTransactionDisplayType.WITHDRAWAL,
                        SavingsAccountTransactionDisplayType.TRANSFER_OUT -> 
                            transaction.balanceAfter + transaction.amount
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Balance:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = previousBalance.formatCurrency(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        MaterialSymbolIcon(
                            icon = MaterialSymbols.ARROW_FORWARD,
                            contentDescription = null,
                            size = 12.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = transaction.balanceAfter.formatCurrency(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Amount
            Column(horizontalAlignment = Alignment.End) {
                val isPositive = transaction.type == SavingsAccountTransactionDisplayType.DEPOSIT ||
                                 transaction.type == SavingsAccountTransactionDisplayType.INTEREST ||
                                 transaction.type == SavingsAccountTransactionDisplayType.TRANSFER_IN
                
                val amountText = if (isPositive) {
                    "+${transaction.amount.formatCurrency()}"
                } else {
                    "-${transaction.amount.formatCurrency()}"
                }
                
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
