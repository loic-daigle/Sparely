package com.example.sparely.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Sparely's haptics, which follow Settings > Appearance > Vibration: a light confirm when money is
 * saved or moved. Off, every call does nothing.
 */
class SparelyHaptics(private val feedback: HapticFeedback?) {
    fun confirm() {
        feedback?.performHapticFeedback(HapticFeedbackType.Confirm)
    }
}

/** Provided by SparelyApp from the setting; screens drawn outside it get no haptics. */
val LocalSparelyHaptics = staticCompositionLocalOf { SparelyHaptics(null) }

@Composable
fun rememberSparelyHaptics(enabled: Boolean): SparelyHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(enabled, feedback) { SparelyHaptics(feedback.takeIf { enabled }) }
}
