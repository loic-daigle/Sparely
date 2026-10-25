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
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.sparely.ui.components.ExpressiveListHeader
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

/** Money that came into the account: deposits, interest and transfers in. */
private val SavingsAccountTransaction.isIncoming: Boolean
    get() = type == SavingsAccountTransactionDisplayType.DEPOSIT ||
        type == SavingsAccountTransactionDisplayType.INTEREST ||
        type == SavingsAccountTransactionDisplayType.TRANSFER_IN

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SavingsHistoryScreen(
    accountId: Long,
    accountName: String,
    viewModel: SparelyViewModel,
    onNavigateBack: () -> Unit
) {
    val transactions by viewModel.getAccountTransactions(accountId).collectAsState(initial = emptyList())
    val dateFormatter = com.example.sparely.ui.utils.LocalDates.mediumDateTime
    val newestFirst = remember(transactions) { transactions.sortedByDescending { it.timestamp } }
    // The balance after the last change of each day, oldest first, for the headline's line
    val series = remember(transactions) {
        transactions.sortedBy { it.timestamp }
            .associate { it.timestamp.toLocalDate() to it.balanceAfter }
            .toList()
    }
    val byMonth = remember(newestFirst) { newestFirst.groupBy { java.time.YearMonth.from(it.timestamp) } }

    // The app's frame already pads for the system bars, so this screen's own bar and content
    // don't add them again (they left a gap above the title and below the list). Titled with the
    // account, like every detail page (docs/DESIGN.md §6)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                scrollBehavior = scrollBehavior,
                title = { Text(text = accountName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
            ) {
                item(key = "hero") {
                    SavingsHistoryHero(
                        balance = newestFirst.first().balanceAfter,
                        series = series,
                        interest = transactions.filter { it.type == SavingsAccountTransactionDisplayType.INTEREST }.sumOf { it.amount },
                        movements = transactions.size
                    )
                }
                // A group per month, newest first, with the month's net change beside its name
                byMonth.forEach { (month, items) ->
                    item(key = "month_$month") {
                        val net = items.sumOf { if (it.isIncoming) it.amount else -it.amount }
                        ExpressiveListHeader(
                            title = month.atDay(1).format(com.example.sparely.ui.utils.LocalDates.monthYear)
                                .replaceFirstChar { it.titlecase() },
                            trailing = (if (net >= 0) "+" else "−") + kotlin.math.abs(net).formatCurrency(),
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                    itemsIndexed(items, key = { _, t -> "t_${t.id}_${t.timestamp}" }) { index, transaction ->
                        SavingsTransactionCard(
                            transaction = transaction,
                            dateFormatter = dateFormatter,
                            shape = segmentedShape(index, items.size),
                            modifier = Modifier.segmentSpacing(index)
                        )
                    }
                }
            }
        }
    }
}

/** The page's headline: the balance now, its line over time, the interest earned and how many movements. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavingsHistoryHero(
    balance: Double,
    series: List<Pair<java.time.LocalDate, Double>>,
    interest: Double,
    movements: Int
) {
    val colors = MaterialTheme.colorScheme
    val ink = colors.onPrimaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveShapes.large,
        color = colors.primaryContainer,
        contentColor = ink
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(
                    stringResource(R.string.main_account_current_balance),
                    style = MaterialTheme.typography.labelLarge,
                    color = ink.copy(alpha = 0.8f)
                )
                Text(
                    balance.formatCurrency(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            if (series.size >= 2) {
                BalanceLine(series = series, target = null, lineColor = ink, ink = ink)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (interest > 0.005) {
                    HeroPill(MaterialSymbols.TRENDING_UP, stringResource(R.string.sav_hero_earned, interest.formatCurrency()))
                }
                HeroPill(MaterialSymbols.HISTORY, pluralStringResource(R.plurals.sav_history_movements, movements, movements))
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
    val isPositive = transaction.isIncoming

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
            text = (if (isPositive) "+" else "−") + transaction.amount.formatCurrency(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isPositive) MaterialTheme.colorScheme.success else MaterialTheme.colorScheme.critical
        )
    }
}
