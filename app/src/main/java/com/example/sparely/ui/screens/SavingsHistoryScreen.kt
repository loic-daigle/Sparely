package com.example.sparely.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.Shape
import com.example.sparely.ui.components.ExpressiveEmptyState
import com.example.sparely.ui.components.SegmentedListItem
import com.example.sparely.ui.components.segmentSpacing
import com.example.sparely.ui.components.segmentedShape
import com.example.sparely.ui.theme.critical
import com.example.sparely.ui.theme.ExpressiveShapes
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
import com.example.sparely.ui.theme.success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsHistoryScreen(
    accountId: Long,
    accountName: String,
    viewModel: SparelyViewModel,
    onNavigateBack: () -> Unit
) {
    val transactions by viewModel.getAccountTransactions(accountId).collectAsState(initial = emptyList())
    val dateFormatter = com.example.sparely.ui.utils.LocalDates.mediumDateTime

    // The app's frame already pads for the system bars, so this screen's own bar and content
    // don't add them again (they left a gap above the title and below the list)
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
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
                ExpressiveEmptyState(
                    icon = MaterialSymbols.HISTORY,
                    title = stringResource(R.string.savings_history_empty_title),
                    description = stringResource(R.string.savings_history_empty_desc)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
            ) {
                // One segmented block: rounded ends, thin gaps between transactions
                itemsIndexed(transactions) { index, transaction ->
                    SavingsTransactionCard(
                        transaction = transaction,
                        dateFormatter = dateFormatter,
                        shape = segmentedShape(index, transactions.size),
                        modifier = Modifier.segmentSpacing(index)
                    )
                }
            }
        }
    }
}

@Composable
fun SavingsTransactionCard(
    transaction: SavingsAccountTransaction,
    dateFormatter: DateTimeFormatter,
    shape: Shape = ExpressiveShapes.large,
    modifier: Modifier = Modifier
) {
    val (icon, tint) = when (transaction.type) {
        SavingsAccountTransactionDisplayType.DEPOSIT ->
            MaterialSymbols.ARROW_DOWNWARD to MaterialTheme.colorScheme.success
        SavingsAccountTransactionDisplayType.WITHDRAWAL ->
            MaterialSymbols.ARROW_UPWARD to MaterialTheme.colorScheme.critical
        SavingsAccountTransactionDisplayType.INTEREST ->
            MaterialSymbols.TRENDING_UP to MaterialTheme.colorScheme.success
        SavingsAccountTransactionDisplayType.TRANSFER_IN ->
            MaterialSymbols.SWAP_HORIZ to MaterialTheme.colorScheme.success
        SavingsAccountTransactionDisplayType.TRANSFER_OUT ->
            MaterialSymbols.SWAP_HORIZ to MaterialTheme.colorScheme.critical
    }
    val isPositive = transaction.type == SavingsAccountTransactionDisplayType.DEPOSIT ||
        transaction.type == SavingsAccountTransactionDisplayType.INTEREST ||
        transaction.type == SavingsAccountTransactionDisplayType.TRANSFER_IN

    SegmentedListItem(
        title = transaction.description,
        subtitle = transaction.timestamp.format(dateFormatter),
        icon = icon,
        iconTint = tint,
        shape = shape,
        modifier = modifier,
        below = if (transaction.balanceAfter > 0) {
            {
                // Balance before -> after, indented under the text
                val previousBalance = if (isPositive) {
                    transaction.balanceAfter - transaction.amount
                } else {
                    transaction.balanceAfter + transaction.amount
                }
                Row(
                    modifier = Modifier.padding(start = 58.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = previousBalance.formatCurrency(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else null
    ) {
        Text(
            text = (if (isPositive) "+" else "-") + transaction.amount.formatCurrency(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isPositive) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.critical
        )
    }
}
