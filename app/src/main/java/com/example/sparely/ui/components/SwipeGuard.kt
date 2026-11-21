package com.example.sparely.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Whether a list's rows may be swiped now: not while the list scrolls or flings, nor for a moment
 * after it stops, so a finger that catches the list mid-scroll (or lands a little sideways) doesn't
 * delete or duplicate a row by accident.
 */
@Composable
fun rememberRowSwipeEnabled(listState: LazyListState, settleMillis: Long = 350): Boolean {
    val scrolling = listState.isScrollInProgress
    var settled by remember { mutableStateOf(true) }
    LaunchedEffect(scrolling) {
        if (scrolling) {
            settled = false
        } else {
            delay(settleMillis)
            settled = true
        }
    }
    return settled && !scrolling
}

/** How far a row has to be dragged before letting go swipes it: 40 % of its width (the default is 56 dp). */
val RowSwipeThreshold: (Float) -> Float = { totalDistance -> totalDistance * 0.4f }
