package com.example.sparely.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.example.sparely.domain.model.ExpenseCategory

/**
 * Semantic expressive color roles derived from the active Material 3 color scheme.
 * This keeps status/category colors aligned with dynamic color while preserving meaning.
 */
val ColorScheme.success: Color
    get() = tertiary

val ColorScheme.onSuccess: Color
    get() = onTertiary

val ColorScheme.successContainer: Color
    get() = tertiaryContainer

val ColorScheme.onSuccessContainer: Color
    get() = onTertiaryContainer

val ColorScheme.warning: Color
    get() = secondary

val ColorScheme.onWarning: Color
    get() = onSecondary

val ColorScheme.warningContainer: Color
    get() = secondaryContainer

val ColorScheme.onWarningContainer: Color
    get() = onSecondaryContainer

val ColorScheme.critical: Color
    get() = error

val ColorScheme.onCritical: Color
    get() = onError

val ColorScheme.criticalContainer: Color
    get() = errorContainer

val ColorScheme.onCriticalContainer: Color
    get() = onErrorContainer

fun ColorScheme.categoryColor(category: ExpenseCategory): Color {
    return when (category) {
        ExpenseCategory.GROCERIES -> success
        ExpenseCategory.DINING -> critical
        ExpenseCategory.TRANSPORTATION -> primary
        ExpenseCategory.ENTERTAINMENT -> tertiary
        ExpenseCategory.UTILITIES -> secondary
        ExpenseCategory.HEALTH -> primary
        ExpenseCategory.EDUCATION -> tertiary
        ExpenseCategory.SHOPPING -> secondary
        ExpenseCategory.TRAVEL -> primary
        ExpenseCategory.OTHER -> outline
    }
}

object ExpressiveMotionTokens {
    const val QuickDurationMillis: Int = 250
    const val StandardDurationMillis: Int = 500
    const val EmphasizedDurationMillis: Int = 900
    const val SlowDurationMillis: Int = 1200

    val EmphasizedEasing: Easing = FastOutSlowInEasing
}
