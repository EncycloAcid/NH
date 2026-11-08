package com.nh.hoopscoach.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
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
 * The app's motion: Material's emphasized curves, a wave that reveals a screen's cards from the top
 * down when it opens, numbers that roll when they change, and a pulse for a moment that matters.
 * Everything runs through Compose's animation clock, so it follows the phone's animation setting
 * (off or reduced animations skip it).
 */
object Motion {
    /** Material 3's emphasized curves: a confident start and a long, soft landing. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/** When the screen on display opened (uptime millis), so its cards know whether to wave in or just appear. */
val LocalScreenStart = compositionLocalOf { 0L }

fun now() = android.os.SystemClock.uptimeMillis()

/**
 * A card's entrance: it rises 18dp and fades in. While the screen is opening, cards lower down start
 * a little later, so the screen fills in as a wave from the top; a card that scrolls in later just
 * rises quickly with no wait.
 */
fun Modifier.reveal(): Modifier = composed {
    val start = LocalScreenStart.current
    val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    val p = remember { Animatable(0f) }
    var y by remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }
    LaunchedEffect(Unit) {
        val opening = now() - start < 700
        if (opening) {
            val top = withTimeoutOrNullSafe { snapshotFlow { y }.first { it >= 0f } } ?: 0f
            delay((top / screenH).coerceIn(0f, 1f).times(260).toLong())
            p.animateTo(1f, tween(460, easing = Motion.EmphasizedDecel))
        } else p.animateTo(1f, tween(260, easing = Motion.EmphasizedDecel))
    }
    this.onGloballyPositioned { if (y < 0f) y = it.positionInWindow().y }
        .graphicsLayer {
            val v = p.value
            alpha = v
            translationY = (1f - v) * rise
            val s = 0.985f + 0.015f * v
            scaleX = s; scaleY = s
        }
}

private suspend fun <T> withTimeoutOrNullSafe(block: suspend () -> T): T? = kotlinx.coroutines.withTimeoutOrNull(120) { block() }

/** A short bounce whenever [key] changes (not the first time): a score, a new result. */
fun Modifier.pulseOn(key: Any?, amount: Float = 0.14f): Modifier = composed {
    val s = remember { Animatable(1f) }
    var first by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(key) {
        if (first) { first = false; return@LaunchedEffect }
        if (key == null) return@LaunchedEffect
        launch {
            s.snapTo(1f + amount)
            s.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    graphicsLayer { scaleX = s.value; scaleY = s.value }
}

/** Text that rolls when it changes: up for a bigger number, down for a smaller one. */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text, modifier = modifier, label = "roll",
        transitionSpec = {
            val up = (targetState.filter(Char::isDigit).toLongOrNull() ?: 0L) >= (initialState.filter(Char::isDigit).toLongOrNull() ?: 0L)
            (slideInVertically(tween(320, easing = Motion.Emphasized)) { if (up) it else -it } + fadeIn(tween(220))) togetherWith
                (slideOutVertically(tween(260, easing = Motion.EmphasizedAccel)) { if (up) -it else it } + fadeOut(tween(160)))
        },
    ) { Text(it, style = style, color = color, maxLines = 1) }
}

/** A gentle, endless glow: the icon on a big-moment card breathes. */
@Composable
fun breathe(min: Float = 0.92f, max: Float = 1.06f, periodMs: Int = 1600): Float {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "breathe")
    val v by t.animateFloat(min, max, androidx.compose.animation.core.infiniteRepeatable(tween(periodMs, easing = Motion.Emphasized), androidx.compose.animation.core.RepeatMode.Reverse), label = "b")
    return v
}

/** A card that springs in: big moments, coaching calls, results. Plays once per [key]. */
fun Modifier.popIn(key: Any? = Unit): Modifier = composed {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { p.animateTo(1f, spring(dampingRatio = 0.66f, stiffness = 380f)) }
    graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        val sc = 0.9f + 0.1f * v
        scaleX = sc; scaleY = sc
        translationY = (1f - v) * 40f
    }
}

/**
 * A soft band of light that crosses a feature card once, just after it rises into place, the way
 * light catches a trophy. Plays when the card first appears; quick and subtle.
 */
fun Modifier.sheen(): Modifier = composed {
    val start = LocalScreenStart.current
    val p = remember { Animatable(-0.4f) }
    LaunchedEffect(Unit) {
        if (now() - start > 700) { p.snapTo(1.4f); return@LaunchedEffect }
        delay(380)
        p.animateTo(1.4f, tween(1100, easing = Motion.Emphasized))
    }
    drawWithContent {
        drawContent()
        val x = p.value
        if (x > -0.39f && x < 1.39f) {
            val w = size.width
            val band = w * 0.35f
            val cx = w * x
            drawRect(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                    start = androidx.compose.ui.geometry.Offset(cx - band, 0f), end = androidx.compose.ui.geometry.Offset(cx + band, size.height),
                ),
            )
        }
    }
}
