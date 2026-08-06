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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.ExpenseItem
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.utils.filterCurrencyInput
import com.example.sparely.ui.utils.toSafeDoubleOrZero
import com.sparely.app.R

@Composable
fun ExpenseItemsList(
    items: List<ExpenseItem>,
    onItemsChanged: (List<ExpenseItem>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Items / Products",
            style = MaterialTheme.typography.titleSmall
        )

        items.forEachIndexed { index, item ->
            ExpenseItemRow(
                item = item,
                onUpdate = { updated ->
                    val newlist = items.toMutableList()
                    newlist[index] = updated
                    onItemsChanged(newlist)
                },
                onRemove = {
                    val newlist = items.toMutableList()
                    newlist.removeAt(index)
                    onItemsChanged(newlist)
                }
            )
        }

        SparelyTonalButton(
            onClick = {
                onItemsChanged(items + ExpenseItem(expenseId = 0, name = "", unitPrice = 0.0, quantity = 1))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            MaterialSymbolIcon(icon = MaterialSymbols.ADD, contentDescription = null)
            Text("Add Item")
        }
    }
}

@Composable
private fun ExpenseItemRow(
    item: ExpenseItem,
    onUpdate: (ExpenseItem) -> Unit,
    onRemove: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SparelyTextField(
                    value = item.name,
                    onValueChange = { onUpdate(item.copy(name = it)) },
                    label = { Text("Product Name") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                IconButton(onClick = onRemove) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.DELETE,
                        contentDescription = "Remove Item",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SparelyTextField(
                    value = if (item.quantity > 0) item.quantity.toString() else "",
                    onValueChange = { 
                        val qty = it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0
                        val currentTotal = when {
                            item.totalPrice > 0 -> item.totalPrice
                            item.unitPrice > 0 -> item.unitPrice * qty
                            else -> 0.0
                        }
                        val eachPrice = if (qty > 0) currentTotal / qty else item.unitPrice
                        onUpdate(item.copy(quantity = qty, unitPrice = eachPrice, totalPrice = currentTotal))
                    },
                    label = { Text("Qty") },
                    modifier = Modifier.weight(0.3f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                SparelyTextField(
                    value = when {
                        item.totalPrice > 0.0 -> item.totalPrice.formatCurrency("")
                        item.unitPrice > 0.0 -> (item.unitPrice * item.quantity.coerceAtLeast(1)).formatCurrency("")
                        else -> ""
                    },
                    onValueChange = {
                        val totalPrice = it.filterCurrencyInput().toSafeDoubleOrZero()
                        val qty = item.quantity.coerceAtLeast(1)
                        val eachPrice = if (qty > 0) totalPrice / qty else totalPrice
                        onUpdate(item.copy(unitPrice = eachPrice, totalPrice = totalPrice))
                    },
                    label = { Text("Total price") },
                    modifier = Modifier.weight(0.7f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("$") }
                )
            }
            if (item.quantity > 1 && item.totalPrice > 0) {
                Text(
                    text = "≈ " + item.unitPrice.formatCurrency() + " each",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
