package com.nh.hoopscoach.ui

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Gutter = 18.dp

@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, color: Color = C.text, size: TextUnit = 30.sp, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = size, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun BackHeader(title: String, sub: String? = null, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = Gutter, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = C.text)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 13.sp, color = C.sub, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, color: Color = C.card, padding: PaddingValues = PaddingValues(16.dp), onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.clip(RoundedCornerShape(16.dp)).background(color)
    Column((if (onClick != null) m.clickable(onClick = onClick) else m).padding(padding), content = content)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.weight(1f))
        if (action != null) {
            Row(Modifier.clickable { onAction?.invoke() }, verticalAlignment = Alignment.CenterVertically) {
                Text(action, color = C.orangeText, fontSize = 14.sp)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.orangeText, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier, color: Color = C.text, size: TextUnit = 22.sp) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = HoopsType.labelSmall, color = C.sub, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = size, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
fun StatGrid(cells: List<Triple<String, String, Color>>, columns: Int = 5, size: TextUnit = 22.sp, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in cells.chunked(columns)) {
            Row(Modifier.fillMaxWidth()) {
                for ((l, v, c) in row) StatCell(l, v, Modifier.weight(1f), c, size)
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, scroll: Boolean = false, fontSize: TextUnit = 15.sp) {
    val inner = @Composable {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val m = if (scroll) Modifier else Modifier
            Box(
                m.then(if (!scroll) Modifier.fillMaxWidth() else Modifier)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) C.orange else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = if (scroll) 16.dp else 4.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (sel) Color.Black else C.sub, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, fontSize = 15.sp, maxLines = 1)
            }
        }
    }
    if (scroll) {
        Row(
            modifier.padding(horizontal = Gutter).clip(RoundedCornerShape(12.dp)).background(C.card).horizontalScroll(rememberScrollState()).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) { inner() }
    } else {
        Row(modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.card).padding(4.dp)) {
            options.forEachIndexed { i, label ->
                val sel = i == selected
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (sel) C.orange else Color.Transparent).clickable { onSelect(i) }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (sel) Color.Black else C.sub, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, fontSize = fontSize, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, bg: Color = color.copy(alpha = 0.16f), size: TextUnit = 12.sp) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(text, color = color, fontSize = size, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = when { !enabled -> C.card2; primary -> C.orange; else -> C.orangeDim }
    val fg = when { !enabled -> C.dim; primary -> Color.Black; else -> C.orangeText }
    Box(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SmallButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = when { !enabled -> C.card2; primary -> C.orange; else -> C.orangeDim }
    val fg = when { !enabled -> C.dim; primary -> Color.Black; else -> C.orangeText }
    Box(
        modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(10.dp)).background(bg).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun Divider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(C.line))

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = C.orange, track: Color = C.card2, height: Dp = 6.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(height)).background(color))
    }
}

@Composable
fun RatingNum(v: Int, size: TextUnit = 26.sp, modifier: Modifier = Modifier) =
    Text(v.toString(), color = ratingColor(v), fontSize = size, fontWeight = FontWeight.SemiBold, modifier = modifier, textAlign = TextAlign.End)

@Composable
fun WLBadge(won: Boolean) {
    Box(Modifier.size(30.dp).clip(CircleShape).background(if (won) C.greenDim else C.redDim), contentAlignment = Alignment.Center) {
        Text(if (won) "W" else "L", color = if (won) C.green else C.red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Chevron() = Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.dim, modifier = Modifier.size(20.dp))

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(text, color = C.sub, fontSize = 14.sp, modifier = modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 18.dp), textAlign = TextAlign.Center)
}

@Composable
fun Stars(n: Int, size: TextUnit = 14.sp) {
    Row {
        repeat(5) { i -> Text("★", color = if (i < n) C.yellow else C.card2, fontSize = size) }
    }
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = C.text) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, color = C.sub, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
    Divider(Modifier.padding(horizontal = Gutter))
}

@Composable
fun Outlined(modifier: Modifier = Modifier, color: Color = C.orange, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(C.card).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(16.dp), content = content)
}

@Suppress("unused")
@Composable
fun HSpacer(w: Dp) = Spacer(Modifier.width(w))

fun f1(v: Double) = "%.1f".format(v)
fun pct(v: Double) = "%.1f%%".format(v * 100)
fun money(k: Int): String = when {
    k >= 1000 -> "$" + "%.1fM".format(k / 1000.0)
    else -> "$${k}K"
}

@Composable
fun PlusButton(label: String = "+", enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(if (enabled) C.orangeDim else C.card2).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) C.orangeText else C.dim, fontSize = 24.sp, fontWeight = FontWeight.Medium) }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HoopsSlider(
    value: Float, onValueChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier,
    enabled: Boolean = true, active: Color = C.orange, onFinished: () -> Unit = {},
) {
    androidx.compose.material3.Slider(
        value = value, onValueChange = onValueChange, valueRange = range, enabled = enabled, modifier = modifier,
        onValueChangeFinished = onFinished,
        thumb = {
            Box(Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (enabled) Color.White else C.dim))
        },
        track = { st ->
            val frac = ((st.value - st.valueRange.start) / (st.valueRange.endInclusive - st.valueRange.start)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(C.card2)) {
                Box(Modifier.fillMaxWidth(frac).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (enabled) active else C.dim))
            }
        },
    )
}
