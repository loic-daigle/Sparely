package com.example.sparely.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sparely.ui.theme.ExpressiveShapes
import com.example.sparely.ui.theme.MaterialSymbolIcon

/*
 * Building blocks for the app's segmented lists: related rows stack as one rounded block, split by
 * thin gaps, with large corners only at the block's ends (as in history, settings and vaults).
 */

/** Gap between the rows of a segmented group. */
val SegmentGap: Dp = 2.dp

/**
 * Shape for row [index] of [count] in a segmented group: [outer] corners at the group's ends,
 * [inner] corners where rows meet.
 */
fun segmentedShape(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 6.dp): Shape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Space above row [index] of a segmented group in a list that has no spacing of its own. */
fun Modifier.segmentSpacing(index: Int): Modifier = padding(top = if (index == 0) 0.dp else SegmentGap)

/** Heading above a group: optional tonal icon, title, and an optional note (a count or total) on the right. */
@Composable
fun ExpressiveListHeader(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    trailing: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon?.let {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                MaterialSymbolIcon(
                    icon = it,
                    contentDescription = null,
                    size = 18.dp,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One row of a segmented list: a tonal [icon] badge, [title] over [subtitle], and [trailing]
 * content (usually an amount) on the right. Pass [shape] from [segmentedShape].
 */
@Composable
fun SegmentedListItem(
    title: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when {
                    leading != null -> leading()
                    icon != null -> Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(iconTint.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MaterialSymbolIcon(icon = icon, contentDescription = null, size = 22.dp, tint = iconTint)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                trailing?.invoke(this)
            }
            below?.let {
                Spacer(modifier = Modifier.height(12.dp))
                it()
            }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = containerColor) { content() }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = containerColor) { content() }
    }
}

/** Centered empty state: icon tile, title, description and an optional call to action. */
@Composable
fun ExpressiveEmptyState(
    @DrawableRes icon: Int,
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(ExpressiveShapes.large)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            MaterialSymbolIcon(
                icon = icon,
                contentDescription = null,
                size = 36.dp,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        action?.let {
            Spacer(modifier = Modifier.height(8.dp))
            it()
        }
    }
}
