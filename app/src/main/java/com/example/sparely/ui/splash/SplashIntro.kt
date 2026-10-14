package com.example.sparely.ui.splash

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sparely.ui.theme.PoppinsFontFamily
import com.sparely.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private val Spruce = Color(0xFF0F3D34)
private val Gold = Color(0xFFF4CB6A)
private val Ink = Color(0xFFEAF5F0)
private val Muted = Color(0xFF9DBDB2)

/** How long the system splash takes to fade into the intro. */
const val SPLASH_FADE_MS = 200L

// Intro timeline, measured from the moment the system splash starts fading out.
private const val LETTER_START_MS = 150L
private const val LETTER_STAGGER_MS = 50L
private const val LETTER_DURATION_MS = 280
private const val TAGLINE_START_MS = 600L
private const val TAGLINE_DURATION_MS = 300
private const val MIN_HOLD_MS = 900L
private const val EXIT_DURATION_MS = 350

// Measured from activity creation: the intro leaves at about 1.6s and is gone by 2.5s at the latest.
private const val EXIT_TARGET_MS = 1_600L
private const val EXIT_LATEST_MS = 2_150L

// If the system splash never hands over (no splash on this launch), start without it.
private const val HANDOFF_TIMEOUT_MS = 500L

/**
 * State shared between MainActivity's splash exit listener and [SplashIntro]: where the system splash
 * drew its icon, and whether it has started fading out.
 */
class SplashHandoff(val createdAtUptimeMs: Long) {
    /** Bounds of the splash icon view in window pixels; null if the system didn't provide one. */
    var iconBounds by mutableStateOf<Rect?>(null)
    var started by mutableStateOf(false)
}

/**
 * Brand intro shown once per cold start, right after the system splash. It draws the same icon in the
 * same place as the splash so the handoff is invisible, writes out the wordmark and tagline, then slides
 * up to reveal the app. A tap anywhere skips straight to the exit.
 */
@Composable
fun SplashIntro(
    handoff: SplashHandoff,
    appReady: Boolean,
    onFinished: () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val ready by rememberUpdatedState(appReady)
    val wordmark = "sparely"
    val letterCount = wordmark.length + 1 // plus the gold "."

    val logoSettle = remember { Animatable(0f) }
    val letters = remember { List(letterCount) { Animatable(0f) } }
    val tagline = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var exiting by remember { mutableStateOf(false) }

    fun startExit() {
        if (exiting) return
        exiting = true
        scope.launch {
            exit.animateTo(1f, tween(EXIT_DURATION_MS, easing = FastOutSlowInEasing))
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        // Wait until startup work is done, then for the system splash to hand over.
        snapshotFlow { ready }.first { it }
        withTimeoutOrNull(HANDOFF_TIMEOUT_MS) { snapshotFlow { handoff.started }.first { it } }
        val introStart = SystemClock.uptimeMillis()
        val sinceCreate = introStart - handoff.createdAtUptimeMs
        val holdMs = (EXIT_TARGET_MS - sinceCreate)
            .coerceAtLeast(MIN_HOLD_MS)
            .coerceAtMost((EXIT_LATEST_MS - sinceCreate).coerceAtLeast(LETTER_START_MS))

        launch { logoSettle.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
        letters.forEachIndexed { index, letter ->
            launch {
                delay(LETTER_START_MS + index * LETTER_STAGGER_MS)
                letter.animateTo(1f, tween(LETTER_DURATION_MS, easing = FastOutSlowInEasing))
            }
        }
        launch {
            delay(TAGLINE_START_MS)
            tagline.animateTo(1f, tween(TAGLINE_DURATION_MS))
        }
        launch { progress.animateTo(1f, tween(holdMs.toInt(), easing = LinearEasing)) }
        delay(holdMs)
        startExit()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = -size.height * exit.value }
            .background(Spruce)
            .pointerInput(Unit) { detectTapGestures { startExit() } }
    ) {
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }
        // Same box the system splash drew its icon in; centered 160dp if it didn't report one.
        val icon = handoff.iconBounds ?: with(density) {
            val side = 160.dp.toPx()
            Rect((screenW - side) / 2f, (screenH - side) / 2f, (screenW + side) / 2f, (screenH + side) / 2f)
        }
        val iconSide = with(density) { icon.width.toDp() }
        val settleLift = with(density) { 48.dp.toPx() }

        Image(
            painter = painterResource(R.drawable.ic_splash_icon_static),
            contentDescription = null,
            modifier = Modifier
                .offset { IntOffset(icon.left.roundToInt(), icon.top.roundToInt()) }
                .size(iconSide)
                .graphicsLayer {
                    val s = 1f - 0.12f * logoSettle.value
                    scaleX = s
                    scaleY = s
                    translationY = -settleLift * logoSettle.value
                }
        )

        // Wordmark and tagline sit just under the visible part of the icon (the inner 2/3 of its canvas).
        val textTop = icon.center.y + icon.height / 3f - settleLift * logoSettle.value
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, textTop.roundToInt()) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                val style = TextStyle(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    letterSpacing = (-0.5).sp
                )
                (wordmark + ".").forEachIndexed { index, char ->
                    val t = letters[index].value
                    Text(
                        text = char.toString(),
                        style = style,
                        color = if (index == wordmark.length) Gold else Ink,
                        modifier = Modifier.graphicsLayer {
                            alpha = t
                            translationY = (1f - t) * 16.dp.toPx()
                        }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                color = Muted,
                fontSize = 16.sp,
                modifier = Modifier.graphicsLayer { alpha = tagline.value }
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .fillMaxWidth(progress.value)
                .height(2.dp)
                .background(Gold)
        )
    }
}
