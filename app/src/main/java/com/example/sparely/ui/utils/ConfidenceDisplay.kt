package com.example.sparely.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sparely.app.R
import com.example.sparely.domain.model.SuggestionConfidence

@Composable
fun SuggestionConfidence.displayName(): String = when (this) {
    SuggestionConfidence.HIGH -> stringResource(R.string.confidence_high)
    SuggestionConfidence.MEDIUM -> stringResource(R.string.confidence_medium)
    SuggestionConfidence.LOW -> stringResource(R.string.confidence_low)
}
