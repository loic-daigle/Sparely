package com.example.sparely.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.ceil

/** Below this, a label wraps to a second line instead of shrinking further (too small to read). */
private const val MIN_LABEL_SCALE = 0.8f

/**
 * Keeps a button label on one line by scaling it down slightly when it's a bit too wide (French
 * labels run longer than English). If it would need to shrink below [MIN_LABEL_SCALE], it wraps
 * normally instead. Works for any label content, so callers keep passing a plain Text.
 */
@Composable
internal fun FitLabel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = { Box { content() } }, modifier = modifier) { measurables, constraints ->
        val label = measurables.first()
        val available = constraints.maxWidth
        // A label built on a subcomposition (SingleLineText is a BoxWithConstraints) can't report
        // its natural width and throws; it already shrinks itself to fit, so lay it out as is
        val natural = if (available == Constraints.Infinity) {
            0
        } else {
            try {
                label.maxIntrinsicWidth(constraints.maxHeight)
            } catch (_: IllegalStateException) {
                0
            }
        }
        val scale = if (natural > available) available.toFloat() / natural else 1f

        if (scale >= 1f || scale < MIN_LABEL_SCALE) {
            // Fits as is, or too long to shrink nicely: normal layout (wraps if needed).
            val placeable = label.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            val placeable = label.measure(Constraints(maxHeight = constraints.maxHeight))
            val height = ceil(placeable.height * scale).toInt()
            layout(available, height) {
                placeable.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}
