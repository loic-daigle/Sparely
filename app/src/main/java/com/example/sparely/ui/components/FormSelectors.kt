package com.example.sparely.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sparely.domain.model.ExpenseCategory
import com.example.sparely.domain.model.ExpenseType
import com.example.sparely.domain.model.RecurringFrequency
import com.example.sparely.domain.model.displayName
import com.sparely.app.R

/**
 * Centralized selector components for use in both expense and recurring expense forms.
 * These components provide consistent UI and behavior across different screens.
 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySelector(
    selected: ExpenseCategory,
    onSelect: (ExpenseCategory) -> Unit,
    isRequired: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isRequired) {
            RequiredFieldLabel(stringResource(R.string.expense_entry_category_title))
        } else {
            Text(stringResource(R.string.expense_entry_category_title), style = MaterialTheme.typography.titleSmall)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (category in ExpenseCategory.entries) {
                SparelyChip(
                    selected = selected == category,
                    onClick = { onSelect(category) },
                    label = { Text(category.displayName()) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseTypeSelector(
    selected: ExpenseType,
    onSelect: (ExpenseType) -> Unit,
    isRequired: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isRequired) {
            RequiredFieldLabel("Type")
        } else {
            Text("Type", style = MaterialTheme.typography.titleSmall)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (type in ExpenseType.entries) {
                SparelyChip(
                    selected = selected == type,
                    onClick = { onSelect(type) },
                    label = { Text(type.displayName()) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrequencySelector(
    selected: RecurringFrequency,
    onSelect: (RecurringFrequency) -> Unit,
    isRequired: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isRequired) {
            RequiredFieldLabel(stringResource(R.string.onboarding_financial_frequency_label))
        } else {
            Text(stringResource(R.string.onboarding_financial_frequency_label), style = MaterialTheme.typography.titleSmall)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (frequency in RecurringFrequency.entries) {
                SparelyChip(
                    selected = selected == frequency,
                    onClick = { onSelect(frequency) },
                    label = { Text(frequency.name) }
                )
            }
        }
    }
}
