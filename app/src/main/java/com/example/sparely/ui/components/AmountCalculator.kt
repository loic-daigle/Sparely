package com.example.sparely.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sparely.ui.utils.evaluateAmountExpression
import com.example.sparely.ui.utils.formatCurrency
import com.example.sparely.ui.utils.isAmountExpression
import com.example.sparely.ui.utils.roundToTwoDecimals
import com.example.sparely.ui.utils.toInputString
import com.sparely.app.R

/**
 * Keeps an amount field's cursor and selection while its text is a plain String owned by the
 * caller. Text that changes from outside the keyboard (a calculator key, the math settling into
 * its result) puts the cursor at the end, where String-backed fields would leave it behind.
 */
@Composable
fun rememberAmountFieldValue(text: String): MutableState<TextFieldValue> {
    val state = remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (state.value.text != text) state.value = TextFieldValue(text, TextRange(text.length))
    return state
}

private val OPERATOR_KEYS = listOf('+', '−', '×', '÷')

/**
 * Turns the typed math into its result ("12.50+8.99" → "21.49"). Text that is just a number, or
 * math that doesn't work out (a division by zero), comes back unchanged.
 */
fun resolveAmountExpression(text: String): String {
    if (!text.isAmountExpression()) return text
    return evaluateAmountExpression(text)?.roundToTwoDecimals()?.toInputString() ?: text
}

/**
 * Calls [onValueChange] with the result only when [text] holds math that works out, so settling
 * a plain number doesn't fire the field's change handler (which may clear errors or flags).
 */
fun settleAmountExpression(text: String, onValueChange: (String) -> Unit) {
    val resolved = resolveAmountExpression(text)
    if (resolved != text) onValueChange(resolved)
}

/**
 * Calculator keys under an amount field, shown while it has focus: + − × ÷ add an operator (a
 * second one in a row replaces the first) and = swaps the math for its result. Number pads rarely
 * have these keys, hence the row. While the text holds math, the result is previewed above the keys.
 */
@Composable
fun AmountCalculatorKeys(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
    currencySymbol: String = stringResource(R.string.currency_prefix)
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            val preview = if (value.isAmountExpression()) evaluateAmountExpression(value) else null
            if (preview != null) {
                Text(
                    text = "= " + preview.formatCurrency(currencySymbol),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val descriptions = listOf(
                    stringResource(R.string.calculator_add),
                    stringResource(R.string.calculator_subtract),
                    stringResource(R.string.calculator_multiply),
                    stringResource(R.string.calculator_divide)
                )
                OPERATOR_KEYS.forEachIndexed { index, operator ->
                    CalculatorKey(
                        symbol = operator.toString(),
                        description = descriptions[index],
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { onValueChange(appendOperator(value, operator)) }
                    )
                }
                CalculatorKey(
                    symbol = "=",
                    description = stringResource(R.string.calculator_equals),
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    onClick = { settleAmountExpression(value, onValueChange) }
                )
            }
        }
    }
}

/** Adds [operator] at the end; nothing to apply it to yet means no-op, and it replaces a trailing one. */
private fun appendOperator(value: String, operator: Char): String {
    val trimmed = value.trimEnd()
    if (trimmed.isEmpty()) return value
    val last = trimmed.last()
    return if (last.isDigit() || last == '.' || last == ',') trimmed + operator
    else if (trimmed.length > 1) trimmed.dropLast(1) + operator
    else value
}

@Composable
private fun CalculatorKey(
    symbol: String,
    description: String,
    container: Color,
    content: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
