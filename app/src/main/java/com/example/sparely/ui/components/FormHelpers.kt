package com.example.sparely.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sparely.ui.theme.MaterialSymbolIcon
import com.example.sparely.ui.theme.MaterialSymbols

/**
 * Utility components for form field styling, help text, and section organization.
 * These components help create a clear visual hierarchy in expense/subscription forms.
 */

/**
 * Displays a required field label with a red asterisk.
 * Provides visual indication that a field is required.
 */
@Composable
fun RequiredFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    helpText: String? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = "*",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error
        )
        if (helpText != null) {
            IconButton(
                onClick = {},
                modifier = Modifier.size(20.dp),
                enabled = false
            ) {
                MaterialSymbolIcon(
                    icon = MaterialSymbols.INFO,
                    contentDescription = helpText,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Displays descriptive help text below a form field.
 * Uses labelSmall typography and muted color for subtle guidance.
 */
@Composable
fun FieldDescription(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 2.dp)
    )
}

/**
 * Displays an "Optional" badge for non-critical form fields.
 * Helps users understand field importance at a glance.
 */
@Composable
fun OptionalChip(modifier: Modifier = Modifier) {
    SparelyChip(
        selected = false,
        onClick = {},
        enabled = false,
        label = {
            Text(
                "Optional",
                style = MaterialTheme.typography.labelSmall
            )
        },
        modifier = modifier
    )
}

/**
 * Section header with optional collapse functionality.
 * Used to organize form fields into logical groups.
 *
 * @param title Section title
 * @param helpText Optional tooltip text (shown on info icon hover)
 * @param isCollapsible Whether this section can be collapsed
 * @param isExpanded Current expansion state
 * @param onToggle Callback when section is collapsed/expanded
 * @param modifier Modifier for the header
 * @param content The content of the section (not rendered by this composable, but conceptually it follows)
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    helpText: String? = null,
    isCollapsible: Boolean = false,
    isExpanded: Boolean = true,
    onToggle: ((Boolean) -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (helpText != null) {
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(20.dp),
                    enabled = false
                ) {
                    MaterialSymbolIcon(
                        icon = MaterialSymbols.INFO,
                        contentDescription = helpText,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (isCollapsible && onToggle != null) {
            IconButton(
                onClick = { onToggle(!isExpanded) },
                modifier = Modifier.size(32.dp)
            ) {
                MaterialSymbolIcon(
                    icon = if (isExpanded) MaterialSymbols.ARROW_DROP_UP else MaterialSymbols.ARROW_DROP_DOWN,
                    contentDescription = if (isExpanded) "Collapse section" else "Expand section",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Wraps a section with header and optional collapse support.
 * Simplifies creation of organized form sections.
 */
@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    helpText: String? = null,
    isCollapsible: Boolean = false,
    defaultExpanded: Boolean = true,
    content: @Composable (isExpanded: Boolean) -> Unit
) {
    var isExpanded = remember { mutableStateOf(defaultExpanded) }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = title,
            helpText = helpText,
            isCollapsible = isCollapsible,
            isExpanded = isExpanded.value,
            onToggle = { isExpanded.value = it }
        )

        if (!isCollapsible || isExpanded.value) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content(isExpanded.value)
            }
        }
    }
}
