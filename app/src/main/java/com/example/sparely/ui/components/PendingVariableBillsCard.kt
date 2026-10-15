package com.example.sparely.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.PendingVariableBill
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols
import com.example.sparely.ui.theme.warning
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.toSafeDouble
import com.sparely.app.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Variable-amount bills that came due and wait for the user to confirm what they actually cost.
 * Each row starts from the predicted amount; confirming records the bill and moves it to its next
 * due date. Shown on the dashboard and the Recurring page while any bill is waiting.
 */
@Composable
fun PendingVariableBillsCard(
    bills: List<PendingVariableBill>,
    currencySymbol: String,
    onConfirm: (recurringExpenseId: Long, amount: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    ExpressiveCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.RECEIPT,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.warning,
                    size = 20.dp
                )
                Text(
                    text = stringResource(R.string.pending_bills_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(R.string.pending_bills_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            bills.forEachIndexed { index, bill ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PendingBillRow(bill = bill, currencySymbol = currencySymbol, onConfirm = onConfirm)
            }
        }
    }
}

@Composable
private fun PendingBillRow(
    bill: PendingVariableBill,
    currencySymbol: String,
    onConfirm: (Long, Double) -> Unit
) {
    val id = bill.recurringExpense.id
    var amountText by rememberSaveable(id) { mutableStateOf(String.format(Locale.US, "%.2f", bill.predictedAmount)) }
    val amount = amountText.toSafeDouble()?.takeIf { it.isFinite() && it > 0 }
    val since = bill.waitingSince.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = bill.recurringExpense.description,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.pending_bills_expected, bill.predictedAmount.formatCurrency(currencySymbol), since),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text(stringResource(R.string.pending_bills_amount_label)) },
                prefix = { Text(currencySymbol) },
                singleLine = true,
                isError = amount == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { amount?.let { onConfirm(id, it) } },
                enabled = amount != null
            ) {
                Text(stringResource(R.string.pending_bills_confirm))
            }
        }
    }
}
