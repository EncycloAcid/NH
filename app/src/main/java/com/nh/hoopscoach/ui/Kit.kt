package com.nh.hoopscoach.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Team

val Gutter = 16.dp
private val CardShape = RoundedCornerShape(20.dp)

// ---------- layout ----------

@Composable
fun ScreenHeader(eyebrow: String?, title: String, sub: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = Gutter + 4.dp, end = Gutter, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) Text(eyebrow.uppercase(), style = T.label, color = P.accent)
            Text(title.uppercase(), style = T.title, color = P.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun BackBar(title: String, sub: String? = null, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = Gutter, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = P.ink)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = T.head, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = Gutter + 4.dp, end = Gutter + 4.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = T.label, color = P.sub, modifier = Modifier.weight(1f))
        if (action != null) Text(action, style = T.bodyB.copy(fontSize = 14.sp), color = P.accent, modifier = Modifier.clickable { onAction?.invoke() }.padding(4.dp))
    }
}

/** A rounded surface on the page background. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), color: Color = P.surface, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.padding(horizontal = Gutter).fillMaxWidth().clip(CardShape).background(color)
    Column((if (onClick != null) m.clickable(onClick = onClick) else m).padding(padding), content = content)
}

/** Grouped list: rows separated by inset hairlines inside one panel. */
@Composable
fun <T> GroupList(items: List<T>, modifier: Modifier = Modifier, inset: Dp = 16.dp, row: @Composable (T) -> Unit) {
    if (items.isEmpty()) return
    Panel(modifier, padding = PaddingValues(0.dp)) {
        items.forEachIndexed { i, it ->
            row(it)
            if (i < items.lastIndex) Hairline(Modifier.padding(start = inset))
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(P.line))

@Composable
fun ListRow(onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 11.dp), content: @Composable RowScope.() -> Unit) {
    val m = if (onClick != null) modifier.fillMaxWidth().clickable(onClick = onClick) else modifier.fillMaxWidth()
    Row(m.padding(padding), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = P.ink, style: TextStyle = T.num) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = style, color = color, maxLines = 1)
        Text(label.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.sub, maxLines = 1)
    }
}

@Composable
fun StatRow(stats: List<Triple<String, String, Color?>>, modifier: Modifier = Modifier, style: TextStyle = T.num) {
    Row(modifier.fillMaxWidth()) {
        stats.forEach { (l, v, c) -> Stat(l, v, Modifier.weight(1f), c ?: P.ink, style) }
    }
}

@Composable
fun Empty(text: String, modifier: Modifier = Modifier) {
    Text(text, style = T.body, color = P.sub, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp))
}

// ---------- controls ----------

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, scroll: Boolean = false) {
    val track = Modifier.clip(RoundedCornerShape(14.dp)).background(if (P.dark) P.surface else Color(0xFFE6E8EC)).padding(3.dp)
    @Composable
    fun chip(i: Int, label: String, m: Modifier) {
        val sel = i == selected
        Box(
            m.clip(RoundedCornerShape(11.dp)).background(if (sel) (if (P.dark) P.surface2 else Color.White) else Color.Transparent)
                .clickable { onSelect(i) }.padding(horizontal = if (scroll) 14.dp else 4.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = T.bodyB.copy(fontSize = if (!scroll && options.size >= 5) 13.sp else 14.sp), color = if (sel) P.ink else P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (scroll) {
        Row(modifier.padding(horizontal = Gutter).horizontalScroll(rememberScrollState()).then(track), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEachIndexed { i, l -> chip(i, l, Modifier) }
        }
    } else {
        Row(modifier.padding(horizontal = Gutter).fillMaxWidth().then(track)) {
            // Five or more tabs share the width by label length so none gets cut off.
            options.forEachIndexed { i, l -> chip(i, l, Modifier.weight(if (options.size >= 5) l.length + 4f else 1f)) }
        }
    }
}

@Composable
fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, l ->
            val sel = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (sel) P.accent else P.surface)
                    .border(1.dp, if (sel) P.accent else P.line, RoundedCornerShape(50)).clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 7.dp),
            ) { Text(l, style = T.bodyB.copy(fontSize = 13.sp), color = if (sel) P.onAccent else P.sub) }
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 50.dp).clip(RoundedCornerShape(14.dp)).background(if (enabled) P.accent else P.surface2)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.head.copy(fontSize = 16.sp), color = if (enabled) P.onAccent else P.faint, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 50.dp).clip(RoundedCornerShape(14.dp)).background(P.accentSoft)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.head.copy(fontSize = 16.sp), color = if (enabled) P.accent else P.faint, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
fun SmallButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = when { !enabled -> P.surface2; danger -> P.bad.copy(alpha = 0.12f); primary -> P.accent; else -> P.accentSoft }
    val fg = when { !enabled -> P.faint; danger -> P.bad; primary -> P.onAccent; else -> P.accent }
    Box(
        modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(11.dp)).background(bg).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.bodyB.copy(fontSize = 14.sp), color = fg, maxLines = 1) }
}

@Composable
fun IconCircle(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(P.surface).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(if (filled) color else color.copy(alpha = 0.13f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, style = T.label.copy(fontSize = 10.sp, letterSpacing = 0.6.sp), color = if (filled) onColor(color) else color, maxLines = 1)
    }
}

@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier, color: Color = P.accent, track: Color = P.surface2, height: Dp = 6.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(height)).background(color))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoopsSlider(value: Float, onValueChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier, enabled: Boolean = true, onFinished: () -> Unit = {}) {
    val accent = P.accent
    val track = P.surface2
    val thumb = if (P.dark) P.ink else Color.White
    val border = P.line
    Slider(
        value = value, onValueChange = onValueChange, valueRange = range, enabled = enabled, modifier = modifier, onValueChangeFinished = onFinished,
        thumb = { Box(Modifier.size(22.dp).clip(CircleShape).background(thumb).border(1.dp, border, CircleShape)) },
        track = { st ->
            val frac = ((st.value - st.valueRange.start) / (st.valueRange.endInclusive - st.valueRange.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(track)) {
                Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (enabled) accent else P.faint))
            }
        },
    )
}

/** A field-like button that opens a menu of options with descriptions. */
@Composable
fun Picker(label: String, current: String, options: List<Pair<String, String>>, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.surface).border(1.dp, P.line, RoundedCornerShape(14.dp))
                .clickable { open = true }.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(label.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.sub)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.KeyboardArrowDown, null, tint = P.sub, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(open, { open = false }, Modifier.background(P.surface)) {
            options.forEachIndexed { i, (l, b) ->
                DropdownMenuItem(
                    text = { Column { Text(l, style = T.bodyB, color = P.ink); Text(b, style = T.small, color = P.sub) } },
                    onClick = { open = false; onPick(i) },
                )
            }
        }
    }
}

@Composable
fun Chevron() = Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = P.faint, modifier = Modifier.size(20.dp))

// ---------- sports graphics ----------

/** The school's logo on a light plate, or a monogram in school colors when there's no logo file. */
@Composable
fun TeamBadge(t: Team, size: Dp, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val logo = remember(t.abbr) { Logos.get(ctx, t.abbr) }
    if (logo != null) {
        // Logos are drawn for light backgrounds, so they sit on a light plate in both themes.
        val plate = if (P.dark) Color(0xFFF1F2F4) else Color.White
        val ringColor = P.line
        Canvas(modifier.size(size)) {
            val r = this.size.minDimension / 2
            drawCircle(plate, r)
            drawCircle(ringColor, r - 0.5f, style = Stroke(width = 1f))
            val inset = this.size.width * 0.15f
            val side = this.size.width - inset * 2
            drawImage(
                logo, srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                srcSize = androidx.compose.ui.unit.IntSize(logo.width, logo.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(inset.toInt(), inset.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(side.toInt(), side.toInt()),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            )
        }
        return
    }
    val primary = Color(t.primary)
    val secondary = Color(t.secondary)
    val ring = if (contrast(primary, secondary) > 1.6f) secondary else onColor(primary)
    val text = if (contrast(primary, secondary) >= 3f) secondary else onColor(primary)
    val label = t.abbr
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fs = with(density) { (size * when (label.length) { 1 -> 0.52f; 2 -> 0.44f; 3 -> 0.36f; else -> 0.3f }).toSp() }
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(primary, r)
        drawCircle(ring, r * 0.84f, style = Stroke(width = r * 0.07f))
        val layout = measurer.measure(label, TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = fs, color = text))
        drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
    }
}

/** Jersey-shaped chip with the player's number in team colors. */
@Composable
fun Jersey(number: Int, primary: Color, secondary: Color, size: Dp, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fs = with(density) { (size * 0.36f).toSp() }
    val trim = if (contrast(primary, secondary) > 1.5f) secondary else onColor(primary)
    val num = if (contrast(primary, secondary) >= 2.6f) secondary else onColor(primary)
    val plate = P.surface2
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(plate, w / 2)
        val p = Path().apply {
            moveTo(w * 0.30f, h * 0.16f)
            quadraticTo(w * 0.5f, h * 0.34f, w * 0.70f, h * 0.16f)
            lineTo(w * 0.80f, h * 0.20f)
            quadraticTo(w * 0.78f, h * 0.38f, w * 0.86f, h * 0.44f)
            lineTo(w * 0.80f, h * 0.86f)
            lineTo(w * 0.20f, h * 0.86f)
            lineTo(w * 0.14f, h * 0.44f)
            quadraticTo(w * 0.22f, h * 0.38f, w * 0.20f, h * 0.20f)
            close()
        }
        drawPath(p, primary)
        drawPath(p, trim, style = Stroke(width = w * 0.035f))
        val layout = measurer.measure(number.toString(), TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = fs, color = num))
        drawText(layout, topLeft = Offset(w / 2 - layout.size.width / 2f, h * 0.56f - layout.size.height / 2f))
    }
}

/** Rating ring: the arc fills toward 99 and takes the tier color. */
@Composable
fun Ring(value: Int, size: Dp = 44.dp, modifier: Modifier = Modifier, label: String? = null, textSize: TextUnit = 17.sp) {
    val c = tier(value)
    val track = P.surface2
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = this.size.width * 0.09f
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(c, 135f, 270f * (value.coerceIn(0, 99) / 99f), false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = T.numSm.copy(fontSize = textSize, lineHeight = textSize), color = P.ink)
            if (label != null) Text(label, style = T.label.copy(fontSize = 8.sp, letterSpacing = 0.6.sp), color = P.sub)
        }
    }
}

/** Initials avatar for coaches. */
@Composable
fun CoachBadge(first: String, last: String, size: Dp, modifier: Modifier = Modifier, color: Color = P.accentSoft, textColor: Color = P.accent) {
    Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        val fs = with(LocalDensity.current) { (size * 0.38f).toSp() }
        Text("${first.firstOrNull() ?: ' '}${last.firstOrNull() ?: ' '}", style = T.numSm.copy(fontSize = fs, lineHeight = fs), color = textColor)
    }
}

@Composable
fun Stars(n: Int, size: TextUnit = 13.sp) {
    Row { repeat(5) { i -> Text("★", color = if (i < n) P.gold else P.line, fontSize = size) } }
}

@Composable
fun ResultDot(won: Boolean, size: Dp = 26.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(if (won) P.good.copy(alpha = 0.14f) else P.bad.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Text(if (won) "W" else "L", style = T.numSm.copy(fontSize = 15.sp), color = if (won) P.good else P.bad)
    }
}

/** Side-by-side comparison bar for "tale of the tape" rows. */
@Composable
fun Compare(label: String, a: Int, b: Int, aColor: Color, bColor: Color) {
    val total = (a + b).coerceAtLeast(1).toFloat()
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$a", style = T.numSm, color = if (a >= b) P.ink else P.sub, modifier = Modifier.width(36.dp))
            Text(label.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.sub, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("$b", style = T.numSm, color = if (b >= a) P.ink else P.sub, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
            Box(Modifier.weight(a / total).height(6.dp).background(aColor))
            Spacer(Modifier.width(3.dp))
            Box(Modifier.weight(b / total).height(6.dp).background(bColor))
        }
    }
}

@Composable
fun Expandable(title: String, initiallyOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(initiallyOpen) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = T.bodyB, color = P.accent, modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = P.accent, modifier = Modifier.size(20.dp))
        }
        if (open) content()
    }
}

/** Distinct team color for charts: falls back to the secondary color if the primary is too close to the background. */
@Composable
fun teamInk(t: Team): Color {
    val p = Color(t.primary)
    val s = Color(t.secondary)
    val bg = P.surface
    return when {
        contrast(p, bg) >= 1.8f -> p
        contrast(s, bg) >= 1.8f -> s
        else -> P.sub
    }
}

fun f1(v: Double) = "%.1f".format(v)
fun pct(v: Double) = "%.1f%%".format(v * 100)
fun money(k: Int): String = when {
    k == 0 -> "$0"
    k >= 1000 -> "$" + "%.2fM".format(k / 1000.0).replace(".00M", "M").replace(Regex("(\\.\\d)0M"), "$1M")
    else -> "$${k}K"
}
fun ordinal(n: Int): String = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }
fun grade(v: Int): String = when {
    v >= 88 -> "A+"; v >= 84 -> "A"; v >= 80 -> "A-"; v >= 77 -> "B+"; v >= 73 -> "B"; v >= 70 -> "B-"
    v >= 66 -> "C+"; v >= 62 -> "C"; v >= 58 -> "C-"; v >= 54 -> "D+"; v >= 50 -> "D"; else -> "F"
}
fun prestigeLabel(p: Int) = when {
    p >= 90 -> "Blueblood"; p >= 78 -> "Power program"; p >= 64 -> "Contender"; p >= 50 -> "Rising"; p >= 35 -> "Rebuilding"; else -> "Underdog"
}
