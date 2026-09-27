package com.example.sparely.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable

/**
 * Reusable bottom sheet modal component for consistent UI/UX across the app.
 * Replaces Dialog and AlertDialog for better mobile UX.
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

    val state = sheetState ?: rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state
    ) {
        content()
    }
}
