package com.example.sparely.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sparely.app.R
import com.example.sparely.domain.logic.KnownTitle
import com.example.sparely.ui.utils.formatCurrency

/** Previously used titles matching what's being typed; tapping one refills the form from its last use. */
@Composable
fun TitleSuggestionRow(
    suggestions: List<KnownTitle>,
    currencySymbol: String,
    onSuggestionSelected: (KnownTitle) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(suggestions, key = { it.title }) { known ->
            val description = stringResource(R.string.expense_entry_title_suggestion_cd, known.title)
            SparelyChip(
                selected = false,
                onClick = { onSuggestionSelected(known) },
                label = { Text("${known.title} · ${known.lastAmount.formatCurrency(currencySymbol)}") },
                modifier = Modifier.semantics { contentDescription = description }
            )
        }
    }
}

/** "Filled from your last …" line with an undo, shown after a typed title refilled the form. */
@Composable
fun TitleAutofillHint(
    title: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.expense_entry_title_autofilled, title),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        SparelyTextButton(onClick = onUndo) {
            Text(stringResource(R.string.expense_entry_title_autofill_undo))
        }
    }
}
