package com.example.sparely.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sparely.ui.theme.ExpressiveShapes
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.sparely.app.R

/**
 * Reusable bottom sheet modal component for consistent UI/UX across the app.
 * Replaces Dialog and AlertDialog for better mobile UX.
 *
 * Opens fully expanded (forms shouldn't stop half-way and jump when a field gets focus) and keeps
 * its content above the keyboard. It closes by swiping down or tapping outside, so sheets don't
 * need a close button of their own.
 *
 * @param isOpen Whether the bottom sheet is visible
 * @param onDismiss Callback when the sheet is dismissed
 * @param sheetState The sheet state (optional, creates if not provided)
 * @param content The content to display in the sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparelyBottomSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    sheetState: SheetState? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!isOpen) return

    val state = sheetState ?: rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            content()
        }
    }
}

@Composable
private fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 12.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
    )
}

/**
 * Top of a sheet: a tonal [icon] tile and [title] with an optional [subtitle]. [iconTint] sets the
 * tile's colour (error for destructive sheets, say).
 */
@Composable
fun SparelySheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        icon?.let {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(ExpressiveShapes.small)
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                MaterialSymbolIcon(icon = it, contentDescription = null, size = 24.dp, tint = iconTint)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() }
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Small heading that splits a long sheet into groups ("Details", "Withdrawal rules", ...). */
@Composable
fun SheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = 4.dp, top = 8.dp)
            .semantics { heading() }
    )
}

/**
 * The money being entered, shown large and centred so it's the obvious thing to fill in. Opens the
 * number pad by itself when [autoFocus] is set.
 */
@Composable
fun SheetAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    currencySymbol: String = stringResource(R.string.currency_prefix),
    isError: Boolean = false,
    supportingText: String? = null,
    autoFocus: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    showCalculator: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    var fieldValue by rememberAmountFieldValue(value)
    LaunchedEffect(Unit) {
        if (autoFocus) focusRequester.requestFocus()
    }
    val borderColor by animateColorAsState(if (isError) colors.error else Color.Transparent, label = "amountBorder")
    // Long amounts step down a size so they stay on one line
    val amountStyle = (if (value.length > 9) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall)
        .copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = if (isError) colors.error else colors.onSurface)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val amountWidth = with(density) {
        textMeasurer.measure(value.ifEmpty { "0" }, amountStyle, maxLines = 1).size.width.toDp() + 8.dp
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.large)
            .background(colors.surfaceContainerHigh)
            .border(2.dp, borderColor, ExpressiveShapes.large)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = currencySymbol,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )
            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    fieldValue = newValue
                    if (newValue.text != value) onValueChange(newValue.text)
                },
                singleLine = true,
                textStyle = amountStyle,
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onDone = { settleAmountExpression(value, onValueChange); onImeAction() },
                    onNext = { settleAmountExpression(value, onValueChange); onImeAction() }
                ),
                modifier = Modifier
                    // Sized to the digits plus room for the cursor, so the symbol stays beside them.
                    // An intrinsic width left the text a hair wider than the field, which then
                    // scrolled sideways when dragged
                    .width(amountWidth)
                    .widthIn(min = 40.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        // Leaving the field settles any math into its result
                        if (isFocused && !state.isFocused) settleAmountExpression(value, onValueChange)
                        isFocused = state.isFocused
                    }
                    .semantics { contentDescription = label },
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.Center) {
                        if (value.isEmpty()) {
                            Text("0", style = amountStyle.copy(color = colors.onSurfaceVariant.copy(alpha = 0.4f)))
                        }
                        innerTextField()
                    }
                }
            )
        }
        supportingText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) colors.error else colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (showCalculator) {
            AmountCalculatorKeys(
                value = value,
                onValueChange = onValueChange,
                visible = isFocused,
                currencySymbol = currencySymbol
            )
        }
    }
}

/** Full-width setting row: title, optional description and a switch; tapping anywhere toggles it. */
@Composable
fun SheetToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // The row handles the toggle; the switch only mirrors it
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

/**
 * Pick one of a few [options] from a segmented pill row, instead of a dropdown that hides them.
 * Meant for two to four short labels.
 */
@Composable
fun <T> SheetChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val container by animateColorAsState(if (isSelected) colors.secondaryContainer else Color.Transparent, label = "choice")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(container)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * The sheet's buttons: the main action, with an optional secondary one beside it. [destructive]
 * paints the main action in the error colour.
 */
@Composable
fun SheetActions(
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (dismissLabel != null && onDismiss != null) {
            SparelyTonalButton(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text(dismissLabel, maxLines = 1)
            }
        }
        SparelyButton(
            onClick = onConfirm,
            enabled = confirmEnabled,
            containerColor = if (destructive) colors.error else colors.primary,
            contentColor = if (destructive) colors.onError else colors.onPrimary,
            modifier = Modifier
                .weight(if (dismissLabel != null && onDismiss != null) 1.4f else 1f)
                .height(56.dp)
        ) {
            Text(confirmLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A date shown like a filled text field: [label] over [value], calendar icon on the right. The whole
 * tile is the tap target that opens a picker, so it reads as an input rather than a bare button.
 */
@Composable
fun SheetDateField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            MaterialSymbolIcon(
                icon = com.example.sparely.ui.theme.MaterialSymbols.CALENDAR_MONTH,
                contentDescription = null,
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
