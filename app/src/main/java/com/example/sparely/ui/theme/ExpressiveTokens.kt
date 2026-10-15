package com.example.sparely.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.example.sparely.domain.model.ExpenseCategory

/**
 * Money colors: saved/positive (success, Sparely mint-green), goals and caution (warning, Sparely gold)
 * and alerts (critical, red). They are fixed brand values, chosen for light or dark, so they mean the
 * same thing in every theme, including wallpaper colors. See Color.kt.
 */
val ColorScheme.success: Color
    get() = if (isDark) SavedDark else SavedLight

val ColorScheme.onSuccess: Color
    get() = if (isDark) OnSavedDark else OnSavedLight

val ColorScheme.successContainer: Color
    get() = if (isDark) SavedContainerDark else SavedContainerLight

val ColorScheme.onSuccessContainer: Color
    get() = if (isDark) OnSavedContainerDark else OnSavedContainerLight

val ColorScheme.warning: Color
    get() = if (isDark) GoalDark else GoalLight

val ColorScheme.onWarning: Color
    get() = if (isDark) OnGoalDark else OnGoalLight

val ColorScheme.warningContainer: Color
    get() = if (isDark) GoalContainerDark else GoalContainerLight

val ColorScheme.onWarningContainer: Color
    get() = if (isDark) OnGoalContainerDark else OnGoalContainerLight

val ColorScheme.critical: Color
    get() = if (isDark) AlertDark else AlertLight

val ColorScheme.onCritical: Color
    get() = if (isDark) OnAlertDark else OnAlertLight

val ColorScheme.criticalContainer: Color
    get() = if (isDark) AlertContainerDark else AlertContainerLight

val ColorScheme.onCriticalContainer: Color
    get() = if (isDark) OnAlertContainerDark else OnAlertContainerLight

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
