package com.example.sparely.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sparely.ui.theme.PoppinsFontFamily
import com.example.sparely.ui.theme.MaterialSymbolIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun SparelyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    colors: TextFieldColors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp), // M3 Expressive text field shape
        colors = colors,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        supportingText = supportingText,
        prefix = prefix,
        readOnly = readOnly,
        enabled = enabled
    )
}

@Composable
fun SparelyChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { 
             ProvideTextStyle(
                 value = MaterialTheme.typography.labelLarge.copy(
                     fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                 )
             ) {
                 label()
             }
        },
        modifier = modifier,
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(100.dp), // Full pill
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

/**
 * Material 3 Expressive Primary Button
 * Features: 50dp height, labelLarge typography, 20dp icons, full pill shape
 */
@Composable
fun SparelyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon?.invoke()
            ProvideTextStyle(
                value = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold
                )
            ) {
                content()
            }
        }
    }
}

/**
 * Material 3 Expressive Tonal Button
 * For secondary actions with subtle emphasis
 */
@Composable
fun SparelyTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon?.invoke()
            ProvideTextStyle(
                value = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold
                )
            ) {
                content()
            }
        }
    }
}

/**
 * Material 3 Expressive Text Button
 * For tertiary/low-emphasis actions
 */
@Composable
fun SparelyTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon?.invoke()
            ProvideTextStyle(
                value = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold
                )
            ) {
                content()
            }
        }
    }
}

/**
 * Material 3 Expressive Outlined Button
 * For secondary actions with border
 */
@Composable
fun SparelyOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 50.dp),
        enabled = enabled,
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon?.invoke()
            ProvideTextStyle(
                value = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold
                )
            ) {
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f, fill = false)) {
                    content()
                }
            }
        }
    }
}

/**
 * Sparely-styled AlertDialog wrapper.
 * Applies consistent shape, container color, and title typography across all dialogs.
 */
@Composable
fun SparelyAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title?.let {
            {
                ProvideTextStyle(
                    value = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    title()
                }
            }
        },
        text = text,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBoxScope.SparelyDropdownAnchor(
    value: String,
    onValueChange: (String) -> Unit,
    expanded: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = true,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false
) {
    SparelyTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        isError = isError,
        colors = ExposedDropdownMenuDefaults.textFieldColors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
    )
}

/**
 * Material 3 Expressive Dropdown Menu
 * Features: 28dp rounded corners, floating container, item selection with pill background
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SparelyExpressiveDropdown(
    options: List<T>,
    selectedOption: T?,
    onOptionSelected: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    optionIcon: ((T) -> Any)? = null,
    supportingText: (@Composable (T) -> String?)? = null,
    trailingContent: (@Composable (T) -> Unit)? = null,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = it }
        ) {
            SparelyDropdownAnchor(
                value = selectedOption?.let { optionLabel(it) } ?: "",
                onValueChange = {},
                expanded = expanded,
                label = label ?: "",
                leadingIcon = selectedOption?.let { option ->
                    optionIcon?.let { iconProvider ->
                        {
                            val icon = iconProvider(option)
                            when (icon) {
                                is Int -> MaterialSymbolIcon(icon = icon, contentDescription = null)
                                is ImageVector -> Icon(icon, contentDescription = null)
                            }
                        }
                    }
                },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )

            SparelyDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    SparelyDropdownMenuItem(
                        text = {
                            Column {
                                Text(optionLabel(option))
                                val text = supportingText?.invoke(option)
                                if (text != null) {
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        leadingIcon = optionIcon?.let { iconProvider ->
                            {
                                val icon = iconProvider(option)
                                when (icon) {
                                    is Int -> MaterialSymbolIcon(icon = icon, contentDescription = null)
                                    is ImageVector -> Icon(icon, contentDescription = null)
                                }
                            }
                        },
                        trailingIcon = trailingContent?.let { content -> { content(option) } },
                        isSelected = option == selectedOption,
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
                extraContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBoxScope.SparelyDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    MaterialTheme(
        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(28.dp))
    ) {
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            content = content
        )
    }
}

/**
 * Expressive Menu Item for dropdowns.
 * Features: pill-shaped background when selected, Poppins font.
 */
@Composable
fun SparelyDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    isSelected: Boolean = false
) {
    DropdownMenuItem(
        text = {
            ProvideTextStyle(
                value = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            ) {
                text()
            }
        },
        onClick = onClick,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .then(
                if (isSelected) Modifier.background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            ),
        colors = MenuDefaults.itemColors(
            textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            leadingIconColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
fun ExpressiveMetricPill(
    label: String,
    value: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        color = containerColor,
        shape = CircleShape,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = contentColor
            )
        }
    }
}