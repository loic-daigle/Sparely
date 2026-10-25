package com.example.sparely.ui.components

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable as navComposable

/** The app's shared-transition layout, around the navigation host. Null in previews and tests. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The animation of the page being shown, from its navigation destination. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * A navigation destination that passes its animation on to the page, so rows and pages can use
 * [containerTransform] without the scope being handed down through every screen.
 */
fun NavGraphBuilder.composable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    navComposable(route = route, arguments = arguments) { entry ->
        val scope: AnimatedContentScope = this
        CompositionLocalProvider(LocalNavAnimatedScope provides scope) {
            scope.content(entry)
        }
    }
}

/**
 * The container transform of docs/DESIGN.md §6: a row and the page it opens carry the same [key],
 * and the row grows into the page (and back into the row on back, predictive back included).
 * [shape] clips the moving container, the row's own shape. Does nothing outside the app's frame.
 */
@Composable
fun Modifier.containerTransform(key: String, shape: Shape = RectangleShape): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@containerTransform.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animated,
            clipInOverlayDuringTransition = OverlayClip(shape)
        )
    }
}
