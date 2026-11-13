package com.nh.hoopscoach.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shared motion language: Material emphasized curves, a top-down entrance wave for cards,
 * numbers that roll, and a short pulse for moments that matter. Compose's animator duration
 * scale is respected (reduced / off animations skip the feel).
 */
object Motion {
    /** Confident start, long soft landing. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Soft settle for pills, nav, and presses — less rubber-band than a punchy bounce. */
    val Settle = spring<Float>(dampingRatio = 0.82f, stiffness = 480f)
    val Press = spring<Float>(dampingRatio = 0.72f, stiffness = 700f)
    val Pop = spring<Float>(dampingRatio = 0.78f, stiffness = 420f)
    val Pulse = spring<Float>(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow)
    val Enter = tween<Float>(520, easing = EmphasizedDecel)
    val EnterQuick = tween<Float>(280, easing = EmphasizedDecel)
}

/** When the screen on display opened (uptime millis), so its cards know whether to wave in or just appear. */
val LocalScreenStart = compositionLocalOf { 0L }

fun now() = android.os.SystemClock.uptimeMillis()

/**
 * Card entrance: rises and fades in. During the first ~700ms of a screen open, lower cards
 * wait a beat so the page fills as a wave from the top; later scroll-ins just rise quickly.
 */
fun Modifier.reveal(): Modifier = composed {
    val start = LocalScreenStart.current
    val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val rise = with(LocalDensity.current) { 22.dp.toPx() }
    val p = remember { Animatable(0f) }
    var y by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(Unit) {
        val opening = now() - start < 700
        if (opening) {
            val top = withTimeoutOrNullSafe { snapshotFlow { y }.first { it >= 0f } } ?: 0f
            delay((top / screenH).coerceIn(0f, 1f).times(300).toLong())
            p.animateTo(1f, Motion.Enter)
        } else {
            p.animateTo(1f, Motion.EnterQuick)
        }
    }
    this.onGloballyPositioned { if (y < 0f) y = it.positionInWindow().y }
        .graphicsLayer {
            val v = p.value
            alpha = v
            translationY = (1f - v) * rise
            val s = 0.97f + 0.03f * v
            scaleX = s
            scaleY = s
        }
}

private suspend fun <T> withTimeoutOrNullSafe(block: suspend () -> T): T? =
    kotlinx.coroutines.withTimeoutOrNull(120) { block() }

/** A short bounce whenever [key] changes (not the first time): a score, a new result. */
fun Modifier.pulseOn(key: Any?, amount: Float = 0.1f): Modifier = composed {
    val s = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (key == null) return@LaunchedEffect
        launch {
            s.snapTo(1f + amount.coerceIn(0.04f, 0.16f))
            s.animateTo(1f, Motion.Pulse)
        }
    }
    graphicsLayer {
        scaleX = s.value
        scaleY = s.value
    }
}

/** Text that rolls when it changes: up for a bigger number, down for a smaller one. */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        label = "roll",
        transitionSpec = {
            val up = (targetState.filter(Char::isDigit).toLongOrNull() ?: 0L) >=
                (initialState.filter(Char::isDigit).toLongOrNull() ?: 0L)
            (slideInVertically(tween(300, easing = Motion.Emphasized)) { if (up) it / 2 else -it / 2 } +
                fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                (slideOutVertically(tween(220, easing = Motion.EmphasizedAccel)) { if (up) -it / 2 else it / 2 } +
                    fadeOut(tween(140)))
        },
    ) { Text(it, style = style, color = color, maxLines = 1) }
}

/** A gentle, endless glow: the icon on a big-moment card breathes. */
@Composable
fun breathe(min: Float = 0.94f, max: Float = 1.05f, periodMs: Int = 1800): Float {
    val t = rememberInfiniteTransition(label = "breathe")
    val v by t.animateFloat(
        min,
        max,
        infiniteRepeatable(tween(periodMs, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "b",
    )
    return v
}

/** A card that springs in: big moments, coaching calls, results. Plays once per [key]. */
fun Modifier.popIn(key: Any? = Unit): Modifier = composed {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { p.animateTo(1f, Motion.Pop) }
    graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        val sc = 0.94f + 0.06f * v
        scaleX = sc
        scaleY = sc
        translationY = (1f - v) * 28f
    }
}

/**
 * A soft band of light that crosses a feature card once after it rises — light catching a trophy.
 * Plays on first appearance; quick and subtle.
 */
fun Modifier.sheen(): Modifier = composed {
    val start = LocalScreenStart.current
    val p = remember { Animatable(-0.4f) }
    LaunchedEffect(Unit) {
        if (now() - start > 700) {
            p.snapTo(1.4f)
            return@LaunchedEffect
        }
        delay(420)
        p.animateTo(1.4f, tween(960, easing = Motion.Emphasized))
    }
    drawWithContent {
        drawContent()
        val x = p.value
        if (x > -0.39f && x < 1.39f) {
            val w = size.width
            val band = w * 0.32f
            val cx = w * x
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent),
                    start = Offset(cx - band, 0f),
                    end = Offset(cx + band, size.height),
                ),
            )
        }
    }
}
