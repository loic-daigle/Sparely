package com.example.sparely.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.ExpenseItem
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.filterIntegerInput
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.roundToTwoDecimals
import com.example.sparely.ui.utils.toSafeDouble
import com.sparely.app.R

/** Whether a row's price field holds the price of one unit or of the whole line. */
private enum class PriceMode { EACH, TOTAL }

@Composable
fun ExpenseItemsList(
    items: List<ExpenseItem>,
    onItemsChanged: (List<ExpenseItem>) -> Unit,
    currencySymbol: String = "$"
) {
    // Stable identity per row, so removing a row doesn't hand its typed text to the next one
    // (plain values: the items list changing is what recomposes, these only need to be remembered)
    val nextKey = remember { longArrayOf(0L) }
    val rowKeys = remember { ArrayList<Long>() }
    while (rowKeys.size < items.size) rowKeys.add(nextKey[0]++)
    while (rowKeys.size > items.size) rowKeys.removeAt(rowKeys.lastIndex)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, item ->
            key(rowKeys[index]) {
                ExpenseItemRow(
                    item = item,
                    currencySymbol = currencySymbol,
                    onUpdate = { updated ->
                        onItemsChanged(items.toMutableList().also { it[index] = updated })
                    },
                    onRemove = {
                        rowKeys.removeAt(index)
                        onItemsChanged(items.toMutableList().also { it.removeAt(index) })
                    }
                )
            }
        }

        SparelyTonalButton(
            onClick = {
                onItemsChanged(items + ExpenseItem(expenseId = 0, name = "", unitPrice = 0.0, quantity = 1, totalPrice = 0.0))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null)
            Text(stringResource(R.string.expense_items_add))
        }
    }
}

@Composable
private fun ExpenseItemRow(
    item: ExpenseItem,
    currencySymbol: String,
    onUpdate: (ExpenseItem) -> Unit,
    onRemove: () -> Unit
) {
    // The fields keep exactly what was typed ("12," stays "12,"); numbers are only parsed out of it,
    // never formatted back in while typing
    var priceMode by remember { mutableStateOf(PriceMode.TOTAL) }
    var quantityText by remember { mutableStateOf(item.quantity.coerceAtLeast(1).toString()) }
    var priceText by remember {
        mutableStateOf(if (item.totalPrice > 0.0) item.totalPrice.formatCurrency("") else "")
    }

    val quantity = quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1

    /** Rebuilds the item from the typed price, read per [mode], and [qty]. */
    fun update(text: String, mode: PriceMode, qty: Int) {
        val price = text.toSafeDouble()?.coerceAtLeast(0.0) ?: 0.0
        val total = if (mode == PriceMode.EACH) (price * qty).roundToTwoDecimals() else price
        val each = if (mode == PriceMode.EACH) price else total / qty
        onUpdate(item.copy(quantity = qty, unitPrice = each, totalPrice = total))
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SparelyTextField(
                    value = item.name,
                    onValueChange = { onUpdate(item.copy(name = it)) },
                    label = { Text(stringResource(R.string.expense_items_name)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    )
                )
                IconButton(onClick = onRemove) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.DELETE,
                        contentDescription = stringResource(R.string.expense_items_remove),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SparelyTextField(
                    value = quantityText,
                    onValueChange = { text ->
                        quantityText = text.filterIntegerInput(maxLength = 4)
                        // A line total stays put and the unit price follows; a unit price multiplies
                        update(priceText, priceMode, quantityText.toIntOrNull()?.coerceAtLeast(1) ?: 1)
                    },
                    label = { Text(stringResource(R.string.expense_items_qty)) },
                    modifier = Modifier.weight(0.3f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                SparelyTextField(
                    value = priceText,
                    onValueChange = { text ->
                        priceText = text.filterCurrencyInput()
                        update(priceText, priceMode, quantity)
                    },
                    label = {
                        Text(
                            stringResource(
                                if (priceMode == PriceMode.EACH) R.string.expense_items_price_each
                                else R.string.expense_items_price_total
                            )
                        )
                    },
                    modifier = Modifier.weight(0.7f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    prefix = { Text(currencySymbol) }
                )
            }
            // With one unit, "each" and "total" are the same number, so the choice only appears for more
            if (quantity > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PriceMode.entries.forEach { mode ->
                        SparelyChip(
                            selected = priceMode == mode,
                            onClick = {
                                if (priceMode != mode) {
                                    // Show the same line in the other form instead of reinterpreting the number
                                    val shown = if (mode == PriceMode.EACH) item.unitPrice else item.totalPrice
                                    priceText = if (shown > 0.0) shown.formatCurrency("") else ""
                                    priceMode = mode
                                }
                            },
                            label = {
                                Text(
                                    stringResource(
                                        if (mode == PriceMode.EACH) R.string.expense_items_mode_each
                                        else R.string.expense_items_mode_total
                                    )
                                )
                            }
                        )
                    }
                }
                if (item.totalPrice > 0.0) {
                    Text(
                        text = stringResource(
                            R.string.expense_items_breakdown,
                            quantity,
                            item.unitPrice.formatCurrency(currencySymbol),
                            item.totalPrice.formatCurrency(currencySymbol)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
