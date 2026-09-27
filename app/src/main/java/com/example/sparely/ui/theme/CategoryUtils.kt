package com.example.sparely.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.sparely.domain.model.ExpenseCategory

/**
 * Shared utility functions for expense category icons and colors.
 * Used across BudgetScreen, HistoryScreen, and other expense-related UI.
 */

@Composable
fun getCategoryColor(category: ExpenseCategory): Color =
    getCategoryColor(category, MaterialTheme.colorScheme)

fun getCategoryColor(category: ExpenseCategory, colorScheme: ColorScheme): Color =
    colorScheme.categoryColor(category)

fun getCategoryIcon(category: ExpenseCategory): Int {
    return when (category) {
        ExpenseCategory.GROCERIES -> MaterialSymbols.SHOPPING_CART
        ExpenseCategory.DINING -> MaterialSymbols.RESTAURANT
        ExpenseCategory.TRANSPORTATION -> MaterialSymbols.DIRECTIONS_CAR
        ExpenseCategory.ENTERTAINMENT -> MaterialSymbols.CELEBRATION
        ExpenseCategory.UTILITIES -> MaterialSymbols.LIGHTBULB
        ExpenseCategory.HEALTH -> MaterialSymbols.HEALTH_AND_SAFETY
        ExpenseCategory.EDUCATION -> MaterialSymbols.SCHOOL
        ExpenseCategory.SHOPPING -> MaterialSymbols.SHOPPING_BAG
        ExpenseCategory.TRAVEL -> MaterialSymbols.FLIGHT
        ExpenseCategory.OTHER -> MaterialSymbols.INFO
    }
}
