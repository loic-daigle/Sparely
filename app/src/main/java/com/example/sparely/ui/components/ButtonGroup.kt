package com.example.sparely.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.sparely.ui.theme.MaterialSymbolIcon

/** One button of a [SparelyButtonGroup]. The [emphasized] one is filled with the primary colour. */
data class ButtonGroupItem(
    val label: String,
    @DrawableRes val icon: Int? = null,
    val emphasized: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * A page's two to four main actions as connected buttons (docs/DESIGN.md §2): fully rounded at the
 * ends, small corners where they meet, and a pressed button's corners round out, as Material 3
 * Expressive buttons do.
 */
@Composable
fun SparelyButtonGroup(items: List<ButtonGroupItem>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, item ->
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val inner by animateDpAsState(if (pressed) 24.dp else 8.dp, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "corner")
            val start = if (index == 0) 24.dp else inner
            val end = if (index == items.lastIndex) 24.dp else inner
            val colors = MaterialTheme.colorScheme
            Surface(
                onClick = item.onClick,
                enabled = item.enabled,
                interactionSource = interaction,
                shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
                color = if (item.emphasized) colors.primary else colors.secondaryContainer,
                contentColor = if (item.emphasized) colors.onPrimary else colors.onSecondaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.icon?.let { MaterialSymbolIcon(icon = it, contentDescription = null, size = 18.dp) }
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
