package com.example.sparely.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.res.stringResource
import com.sparely.app.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.ExpenseCategory

/**
 * Data class representing a quick template for expense creation.
 * Provides presets for common expense types to help users quickly fill out forms.
 */
data class ExpenseTemplate(
    val name: String,
    val category: ExpenseCategory,
    val descriptionHint: String? = null
)

/**
 * Predefined quick templates for common expense types.
 */
object QuickTemplates {
    val ALL = listOf(
        ExpenseTemplate("Groceries", ExpenseCategory.GROCERIES, "Grocery shopping"),
        ExpenseTemplate("Utilities", ExpenseCategory.UTILITIES, "Monthly utilities"),
        ExpenseTemplate("Dining", ExpenseCategory.DINING, "Restaurant/meal"),
        ExpenseTemplate("Transport", ExpenseCategory.TRANSPORTATION, "Gas/transit/uber"),
        ExpenseTemplate("Other", ExpenseCategory.OTHER)
    )
}

/**
 * Quick template selector component.
 * Shows preset expense templates as chips that auto-populate common fields.
 * Only shown when creating new expenses (not editing).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickTemplateSelector(
    onTemplateSelected: (ExpenseTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.expense_entry_quick_templates),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Single scrollable row keeps the form short instead of wrapping onto many lines
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(QuickTemplates.ALL) { template ->
                SparelyChip(
                    selected = false,
                    onClick = { onTemplateSelected(template) },
                    label = { Text(template.name) }
                )
            }
        }
    }
}
