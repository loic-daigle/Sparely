package com.example.sparely.ui.splash

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sparely.ui.theme.PoppinsFontFamily
import com.example.sparely.ui.theme.RobotoFlexFamily
import com.sparely.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

internal val Spruce = Color(0xFF0F3D34)
internal val Mint = Color(0xFF8FE3C0)
internal val Gold = Color(0xFFF4CB6A)
internal val Ink = Color(0xFFEAF5F0)
internal val Muted = Color(0xFF9DBDB2)

private const val WORDMARK = "sparely"

/** How long the system splash takes to fade into the intro. */
const val SPLASH_FADE_MS = 200L

// Intro timeline, measured from the moment the system splash starts fading out. That splash is plain
// spruce with no icon (the system scales and places its icon its own way, so a copy drawn here never
// lined up with it), which makes its fade invisible and lets the whole logo animate here.
private const val BAR_START_MS = 100L
private const val BAR_STAGGER_MS = 150L
private const val BAR_DURATION_MS = 300
private const val COIN_START_MS = 550L
private const val COIN_FADE_MS = 220
private const val COIN_DROP_MS = 520
private const val SETTLE_START_MS = 1_000L
private const val LETTER_START_MS = 1_020L
private const val LETTER_STAGGER_MS = 50L
private const val LETTER_DURATION_MS = 280
private const val TAGLINE_START_MS = 1_400L
private const val TAGLINE_DURATION_MS = 300
private const val MIN_HOLD_MS = 1_750L
private const val EXIT_DURATION_MS = 350

// Measured from activity creation: the intro leaves at about 2.2s, or after MIN_HOLD_MS if the app
// took longer to load.
private const val EXIT_TARGET_MS = 2_200L

/** Android's overshoot interpolator (tension 2): the bars grow a little past full, then settle. */
private val BarOvershoot = Easing { fraction ->
    val t = fraction - 1f
    t * t * (3f * t + 2f) + 1f
}

/** How far above its resting place the coin starts, in the icon canvas's 108 units. */
private const val COIN_DROP_UNITS = 30f

/** Ease-out bounce: lands, then two small hops, like a coin settling on the bars. */
private val CoinBounce = Easing { fraction ->
    val n = 7.5625f
    val d = 2.75f
    when {
        fraction < 1f / d -> n * fraction * fraction
        fraction < 2f / d -> (fraction - 1.5f / d).let { n * it * it + 0.75f }
        fraction < 2.5f / d -> (fraction - 2.25f / d).let { n * it * it + 0.9375f }
        else -> (fraction - 2.625f / d).let { n * it * it + 0.984375f }
    }
}

// Box the mark is drawn in, centered on screen: the 108-unit adaptive icon canvas, as on the launcher.
private val ICON_SIZE = 200.dp

// If the system splash never calls its exit listener (no splash on this launch), start without it.
private const val HANDOFF_TIMEOUT_MS = 500L

// Once it has, it waits out the rest of its icon animation before fading; this only guards against a
// fade that never starts, and is longer than that animation.
private const val FADE_START_TIMEOUT_MS = 1_500L

/**
 * State shared between MainActivity's splash exit listener and [SplashIntro]: whether the system
 * splash is leaving and has started fading out.
 */
class SplashHandoff(val createdAtUptimeMs: Long) {
    /** The system splash called its exit listener; it may still be finishing its icon animation. */
    var exitRequested by mutableStateOf(false)
    /** The system splash started fading out over the intro. */
    var started by mutableStateOf(false)
}

/**
 * Brand intro shown on each fresh launcher open, right after the system splash (plain spruce, so the
 * handoff is invisible). It grows the bars, drops the coin, lifts the logo, writes out the wordmark
 * and tagline, then slides
 * up to reveal the app. When the app is locked it fades instead, into [AppLockScreen], which shares the
 * same layout so the logo and wordmark stay put. A tap anywhere skips straight to the exit.
 */
@Composable
fun SplashIntro(
    handoff: SplashHandoff,
    appReady: Boolean,
    locked: Boolean,
    onFinished: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val ready by rememberUpdatedState(appReady)
    val fadeOut by rememberUpdatedState(locked)
    val letterCount = WORDMARK.length + 1 // plus the gold "."

    val bars = remember { List(3) { Animatable(0f) } }
    val logoSettle = remember { Animatable(0f) }
    val coinDrop = remember { Animatable(0f) }
    val coinAlpha = remember { Animatable(0f) }
    val letters = remember { List(letterCount) { Animatable(0f) } }
    val tagline = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    var exiting by remember { mutableStateOf(false) }
    var exitByFade by remember { mutableStateOf(false) }

    fun startExit() {
        if (exiting) return
        exiting = true
        exitByFade = fadeOut
        scope.launch {
            exit.animateTo(1f, tween(EXIT_DURATION_MS, easing = FastOutSlowInEasing))
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        // Wait until startup work is done, then for the system splash to hand over.
        snapshotFlow { ready }.first { it }
        // The timeline must not start under a splash that is still animating: it would play out
        // unseen and the intro (or the lock screen it fades into) would appear already finished
        val listenerCalled = withTimeoutOrNull(HANDOFF_TIMEOUT_MS) {
            snapshotFlow { handoff.exitRequested }.first { it }
        } != null
        if (listenerCalled) {
            withTimeoutOrNull(FADE_START_TIMEOUT_MS) { snapshotFlow { handoff.started }.first { it } }
        }
        val introStart = SystemClock.uptimeMillis()
        val sinceCreate = introStart - handoff.createdAtUptimeMs
        // Always long enough for the coin, the rise and the wordmark; a slow start never cuts it short
        val holdMs = (EXIT_TARGET_MS - sinceCreate).coerceAtLeast(MIN_HOLD_MS)

        // The bars grow from the bottom one after the other
        bars.forEachIndexed { index, bar ->
            launch {
                delay(BAR_START_MS + index * BAR_STAGGER_MS)
                bar.animateTo(1f, tween(BAR_DURATION_MS, easing = BarOvershoot))
            }
        }
        // The coin falls onto the bars from above, fading in on the way, then the whole logo rises
        launch {
            delay(COIN_START_MS)
            launch { coinAlpha.animateTo(1f, tween(COIN_FADE_MS, easing = LinearEasing)) }
            coinDrop.animateTo(1f, tween(COIN_DROP_MS, easing = CoinBounce))
        }
        launch {
            delay(SETTLE_START_MS)
            logoSettle.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
        }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (exitByFade) alpha = 1f - exit.value else translationY = -size.height * exit.value
            }
            .background(Spruce)
            .pointerInput(Unit) { detectTapGestures { startExit() } }
    ) {
        BrandBackdrop(
            settle = logoSettle.value,
            barProgress = { bars[it].value },
            coinDrop = coinDrop.value,
            coinAlpha = coinAlpha.value,
            letterProgress = { letters[it].value }
        ) {
            Text(
                text = stringResource(R.string.splash_tagline),
                color = Muted,
                fontSize = 16.sp,
                // The intro is drawn outside SparelyTheme, so name the app's body font explicitly.
                fontFamily = RobotoFlexFamily,
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

/**
 * The logo and "sparely." wordmark laid out the way the intro leaves them: the mark centered on screen,
 * lifted and scaled by [settle], with the wordmark underneath. Each bar grows from its bottom edge by
 * [barProgress] and the coin drops in from above ([coinDrop] 0 → 1) while fading in ([coinAlpha]).
 * [below] goes under the wordmark. Shared by [SplashIntro] and [AppLockScreen].
 */
@Composable
internal fun BrandBackdrop(
    settle: Float = 1f,
    barProgress: (Int) -> Float = { 1f },
    coinDrop: Float = 1f,
    coinAlpha: Float = 1f,
    letterProgress: (Int) -> Float = { 1f },
    below: @Composable ColumnScope.() -> Unit = {}
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }
        val icon = with(density) {
            val side = ICON_SIZE.toPx()
            Rect((screenW - side) / 2f, (screenH - side) / 2f, (screenW + side) / 2f, (screenH + side) / 2f)
        }
        val iconSide = with(density) { icon.width.toDp() }
        val settleLift = with(density) { 48.dp.toPx() }

        // One layer for the whole mark, so it rises and shrinks as a single logo
        Box(
            modifier = Modifier
                .offset { IntOffset(icon.left.roundToInt(), icon.top.roundToInt()) }
                .size(iconSide)
                .graphicsLayer {
                    val s = 1f - 0.12f * settle
                    scaleX = s
                    scaleY = s
                    translationY = -settleLift * settle
                }
        ) {
            SparelyMark(barProgress = barProgress, coinDrop = coinDrop, coinAlpha = coinAlpha)
        }

        // Wordmark sits just under the visible part of the icon (the inner 2/3 of its canvas).
        val textTop = icon.center.y + icon.height / 3f - settleLift * settle
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
                (WORDMARK + ".").forEachIndexed { index, char ->
                    val t = letterProgress(index)
                    Text(
                        text = char.toString(),
                        style = style,
                        color = if (index == WORDMARK.length) Gold else Ink,
                        modifier = Modifier.graphicsLayer {
                            alpha = t
                            translationY = (1f - t) * 16.dp.toPx()
                        }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            below()
        }
    }
}

// The mark in its source coordinates (as in the launcher icon): bars of width 30 standing on y = 160,
// and the coin above the tallest one. The launcher foreground draws these at scale 0.38 on the 108-unit
// adaptive icon canvas, centered on (100, 89.5); the same placement is used here.
private val BAR_LEFTS = floatArrayOf(46f, 85f, 124f)
private val BAR_TOPS = floatArrayOf(110f, 82f, 56f)
private const val BAR_WIDTH = 30f
private const val BAR_BOTTOM = 160f
private const val COIN_X = 139f
private const val COIN_Y = 34f
private const val COIN_RADIUS = 15f
private const val MARK_SCALE = 0.38f
private const val MARK_CENTER_X = 100f
private const val MARK_CENTER_Y = 89.5f

/** The bars-and-coin mark drawn to fill its box, which stands for the 108-unit icon canvas. */
@Composable
private fun SparelyMark(barProgress: (Int) -> Float, coinDrop: Float, coinAlpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val unit = size.width / 108f * MARK_SCALE
        fun x(source: Float) = size.width / 2f + (source - MARK_CENTER_X) * unit
        fun y(source: Float) = size.height / 2f + (source - MARK_CENTER_Y) * unit

        BAR_LEFTS.forEachIndexed { index, left ->
            val fullHeight = BAR_BOTTOM - BAR_TOPS[index]
            val height = fullHeight * barProgress(index).coerceAtLeast(0f)
            if (height <= 0f) return@forEachIndexed
            val radius = minOf(BAR_WIDTH / 2f, height / 2f) * unit
            drawRoundRect(
                color = Mint,
                topLeft = Offset(x(left), y(BAR_BOTTOM - height)),
                size = Size(BAR_WIDTH * unit, height * unit),
                cornerRadius = CornerRadius(radius, radius)
            )
        }

        if (coinAlpha > 0f) {
            val lift = COIN_DROP_UNITS / MARK_SCALE * (1f - coinDrop)
            drawCircle(
                color = Gold,
                radius = COIN_RADIUS * unit,
                center = Offset(x(COIN_X), y(COIN_Y - lift)),
                alpha = coinAlpha.coerceIn(0f, 1f)
            )
        }
    }
}
