package com.example.sparely.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.sparely.domain.model.ExpenseCategory

/**
 * Semantic color definitions for consistent use throughout the app.
 * All colors are derived from Material 3 theme to support dynamic color.
 */

// Status/Semantic Colors (using existing Material 3 semantic slots)
@Composable
fun getStatusColor(status: StatusType): Color = MaterialTheme.colorScheme.getStatusColor(status)

@Composable
fun getActionColor(action: ActionType): Color = MaterialTheme.colorScheme.getActionColor(action)

fun ColorScheme.getStatusColor(status: StatusType): Color = when (status) {
    StatusType.SUCCESS -> success
    StatusType.WARNING -> warning
    StatusType.ERROR -> critical
    StatusType.INFO -> primary
    StatusType.NEUTRAL -> outline
    StatusType.PENDING -> warning
    StatusType.COMPLETED -> success
}

fun ColorScheme.getActionColor(action: ActionType): Color = when (action) {
    ActionType.ADD -> success
    ActionType.DELETE -> critical
    ActionType.EDIT -> primary
    ActionType.CLOSE -> outline
    ActionType.NAVIGATE -> primary
    ActionType.SAVE -> success
    ActionType.CANCEL -> outline
    ActionType.UNDO -> secondary
}

enum class StatusType {
    SUCCESS, WARNING, ERROR, INFO, NEUTRAL, PENDING, COMPLETED
}

enum class ActionType {
    ADD, DELETE, EDIT, CLOSE, NAVIGATE, SAVE, CANCEL, UNDO
}

/**
 * Semantic icon definitions for consistent icons across the app.
 */
fun getStatusIcon(status: StatusType): Int = when (status) {
    StatusType.SUCCESS -> MaterialSymbols.CHECK_CIRCLE
    StatusType.WARNING -> MaterialSymbols.WARNING
    StatusType.ERROR -> MaterialSymbols.WARNING
    StatusType.INFO -> MaterialSymbols.INFO
    StatusType.NEUTRAL -> MaterialSymbols.INFO
    StatusType.PENDING -> MaterialSymbols.SCHEDULE
    StatusType.COMPLETED -> MaterialSymbols.CHECK_CIRCLE
}

fun getActionIcon(action: ActionType): Int = when (action) {
    ActionType.ADD -> MaterialSymbols.ADD
    ActionType.DELETE -> MaterialSymbols.DELETE
    ActionType.EDIT -> MaterialSymbols.EDIT
    ActionType.CLOSE -> MaterialSymbols.CLOSE
    ActionType.NAVIGATE -> MaterialSymbols.ARROW_FORWARD
    ActionType.SAVE -> MaterialSymbols.CHECK
    ActionType.CANCEL -> MaterialSymbols.CLOSE
    ActionType.UNDO -> MaterialSymbols.REFRESH
}

/**
 * Get tinted version of category color for backgrounds/containers
 */
fun ColorScheme.categoryColorVariant(category: ExpenseCategory, alpha: Float = 0.1f): Color {
    return categoryColor(category).copy(alpha = alpha)
}

/**
 * Get on-color version for text on category backgrounds
 */
fun ColorScheme.onCategoryColor(category: ExpenseCategory): Color {
    return when (category) {
        ExpenseCategory.GROCERIES -> onSuccess
        ExpenseCategory.DINING -> onCritical
        ExpenseCategory.TRANSPORTATION -> onPrimary
        ExpenseCategory.ENTERTAINMENT -> onTertiary
        ExpenseCategory.UTILITIES -> onSecondary
        ExpenseCategory.HEALTH -> onPrimary
        ExpenseCategory.EDUCATION -> onTertiary
        ExpenseCategory.SHOPPING -> onSecondary
        ExpenseCategory.TRAVEL -> onPrimary
        ExpenseCategory.OTHER -> onSurface
    }
}
