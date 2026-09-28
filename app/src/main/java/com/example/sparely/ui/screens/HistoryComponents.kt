package com.example.sparely.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.sparely.domain.model.displayName
import com.example.sparely.ui.theme.ExpressiveShapes
import com.sparely.app.R
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.Expense
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.getCategoryColor
import com.example.sparely.ui.theme.getCategoryIcon

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn



@Composable
fun HistoryDateHeader(
    date: java.time.LocalDate,
    dailyTotal: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = dailyTotal.formatCurrency(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}



/**
 * Compact expense row for the per-store history dialog. The store is the dialog's subject, so the
 * row leads with the description and date; actions live in an overflow menu.
 */
@Composable
fun StoreHistoryExpenseRow(
    expense: Expense,
    onClick: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRefund: () -> Unit = {}
) {
    val categoryColor = getCategoryColor(expense.category)
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = ExpressiveShapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                MaterialSymbolIcon(
                    icon = getCategoryIcon(expense.category),
                    contentDescription = null,
                    tint = categoryColor,
                    size = 20.dp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.description.ifBlank { stringResource(R.string.history_unnamed_expense) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${expense.date.format(dateFormatter)} · ${expense.category.displayName()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = expense.amount.formatCurrency(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                if (expense.refundedAmount > 0) {
                    Text(
                        text = stringResource(
                            if (expense.isRefunded) R.string.history_card_refunded_badge
                            else R.string.history_card_partial_refund_badge
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Full 48dp touch target for the overflow menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.MORE_VERT,
                        contentDescription = stringResource(R.string.history_more_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 20.dp
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            MaterialSymbolIcon(icon = MaterialSymbols.EDIT, contentDescription = null, size = 20.dp)
                        }
                    )
                    if (!expense.isRefunded) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_action_refund)) },
                            onClick = {
                                showMenu = false
                                onRefund()
                            },
                            leadingIcon = {
                                MaterialSymbolIcon(icon = MaterialSymbols.UNDO, contentDescription = null, size = 20.dp)
                            }
                        )
                    }
                    // Destructive action last
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            MaterialSymbolIcon(icon = MaterialSymbols.DELETE, contentDescription = null, size = 20.dp)
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        }
    }
}


@Composable
fun StoreHistoryDialog(
    store: com.example.sparely.domain.model.Store,
    history: List<Expense>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    stores: List<com.example.sparely.domain.model.Store>,
    onEditExpense: (Expense) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onRefundExpense: (Expense) -> Unit,
    brandfetchClientId: String?
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = store.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        // Summary: how often and how much you spend here
                        Text(
                            text = if (isLoading || history.isEmpty()) {
                                stringResource(R.string.history_store_history_subtitle)
                            } else {
                                pluralStringResource(
                                    R.plurals.history_store_summary,
                                    history.size,
                                    history.size,
                                    history.sumOf { it.amount }.formatCurrency()
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        MaterialSymbolIcon(icon = MaterialSymbols.CLOSE, contentDescription = stringResource(R.string.action_close))
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                } else if (history.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.history_store_no_expenses), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history, key = { it.id }) { expense ->
                            StoreHistoryExpenseRow(
                                expense = expense,
                                // Tapping a row opens it for editing, like the main list
                                onClick = { onEditExpense(expense) },
                                onEdit = { onEditExpense(expense) },
                                onDelete = { onDeleteExpense(expense) },
                                onRefund = { onRefundExpense(expense) }
                            )
                        }
                    }
                }
            }
        }
    }
}
