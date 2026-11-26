package com.jellyglass.dialer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

val LocalDark = compositionLocalOf { false }

val Accents = listOf(0xFF2F6BFF, 0xFFFF3B7F, 0xFF22C55E, 0xFFFF9500, 0xFF8B5CF6, 0xFF00C2D1).map { Color(it) }
val WallNames = listOf("Aurora", "Sunset", "Ocean", "Mint", "Mono", "Custom")

private fun cl(vararg v: Long) = v.map { Color(it) }

private val LIGHT = listOf(
    cl(0xFFFF9AC1, 0xFF7FB2FF, 0xFFFFD37A, 0xFFEFE9FF),
    cl(0xFFFFB36B, 0xFFFF6F91, 0xFFFFD6A5, 0xFFFFF0E6),
    cl(0xFF5EE7DF, 0xFF4FACFE, 0xFFB8F1FF, 0xFFE6F7FF),
    cl(0xFFA8F0C6, 0xFF7DD3FC, 0xFFFEF08A, 0xFFEFFFF6),
    cl(0xFFD4D4D8, 0xFFA1A1AA, 0xFFF4F4F5, 0xFFFAFAFA)
)
private val DARK = listOf(
    cl(0xFF0EA5A4, 0xFF5B3DF5, 0xFFD02BD0, 0xFF050612),
    cl(0xFFFF6A3D, 0xFFC0145F, 0xFF6D28D9, 0xFF0D0509),
    cl(0xFF06B6D4, 0xFF2563EB, 0xFF14B8A6, 0xFF020813),
    cl(0xFF10B981, 0xFF0891B2, 0xFF84CC16, 0xFF020B07),
    cl(0xFF6B7280, 0xFF374151, 0xFF9CA3AF, 0xFF060606)
)

/** Returns [blob1, blob2, blob3, base] colors. */
fun palette(dark: Boolean, idx: Int = Store.wallpaper): List<Color> {
    if (idx >= 5) {
        val h = Store.customHue
        fun hh(o: Float) = (h + o + 360f) % 360f
        return if (dark) listOf(
            Color.hsv(hh(0f), .9f, .7f), Color.hsv(hh(50f), .85f, .75f),
            Color.hsv(hh(-50f), .8f, .7f), Color.hsv(hh(0f), .6f, .06f)
        ) else listOf(
            Color.hsv(hh(0f), .45f, 1f), Color.hsv(hh(50f), .45f, 1f),
            Color.hsv(hh(-50f), .4f, 1f), Color.hsv(hh(0f), .08f, .98f)
        )
    }
    return (if (dark) DARK else LIGHT)[idx]
}

@Composable fun tx(): Color = if (LocalDark.current) Color.White else Color(0xFF15141C)
@Composable fun mu(): Color = tx().copy(alpha = .6f)
@Composable fun accent(): Color = Accents[Store.accent.coerceIn(0, Accents.lastIndex)]

@Composable
fun Wallpaper(dark: Boolean, content: @Composable BoxScope.() -> Unit) {
    val p = palette(dark)
    val t = rememberInfiniteTransition(label = "wall")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(
        Modifier.fillMaxSize().background(p[3]).drawBehind {
            val w = size.width
            val h = size.height
            fun blob(c: Color, cx: Float, cy: Float, r: Float) {
                drawCircle(
                    brush = Brush.radialGradient(listOf(c.copy(alpha = if (dark) .75f else .9f), Color.Transparent), center = Offset(cx, cy), radius = r),
                    radius = r, center = Offset(cx, cy)
                )
            }
            blob(p[0], w * (.1f + .15f * a), h * (.1f + .1f * a), w * .95f)
            blob(p[1], w * (.95f - .15f * a), h * (.95f - .1f * a), w * 1.05f)
            blob(p[2], w * (.35f + .2f * a), h * (.5f + .1f * a), w * .65f)
        },
        content = content
    )
}

@Composable
fun Modifier.glass(shape: Shape = CircleShape): Modifier {
    val dark = LocalDark.current
    val a = (Store.glass * (if (dark) 0.8f else 1.8f)).coerceIn(0f, 1f)
    return this
        .clip(shape)
        .background(Color.White.copy(alpha = a))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .26f), Color.Transparent)))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .4f else .9f), Color.White.copy(alpha = .1f))), shape)
}

/** Squash-and-spring press effect. Keep it first in the modifier chain. */
@Composable
fun Modifier.jellyClick(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sx by animateFloatAsState(if (pressed) 1f - .14f * Store.jelly else 1f, spring(dampingRatio = .35f, stiffness = 450f), label = "sx")
    val sy by animateFloatAsState(if (pressed) 1f - .09f * Store.jelly else 1f, spring(dampingRatio = .35f, stiffness = 450f), label = "sy")
    return this
        .graphicsLayer { scaleX = sx; scaleY = sy }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
fun T(s: String, size: Int = 15, color: Color = tx(), weight: FontWeight? = null, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(s, modifier = modifier, color = color, fontSize = size.sp, fontWeight = weight, textAlign = align)
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(36.dp)).padding(16.dp), content = content)
}

@Composable
fun PillBtn(text: String, color: Color, modifier: Modifier = Modifier, h: Int = 56, onClick: () -> Unit) {
    Box(modifier.jellyClick(onClick).height(h.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        T(text, 16, Color.White, FontWeight.SemiBold)
    }
}

@Composable
fun GlassPill(text: String, modifier: Modifier = Modifier, h: Int = 52, onClick: () -> Unit) {
    Box(modifier.jellyClick(onClick).height(h.dp).glass(), contentAlignment = Alignment.Center) {
        T(text, 15, Color.White)
    }
}

@Composable
fun Avatar(name: String, size: Int = 46) {
    val hue = ((name.hashCode() and 0x7fffffff) % 360).toFloat()
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color.hsv(hue, .55f, .85f)), contentAlignment = Alignment.Center) {
        T(name.trim().take(1).uppercase().ifEmpty { "?" }, (size * 0.4).toInt(), Color.White, FontWeight.Bold)
    }
}

@Composable
fun Seg(options: List<String>, sel: Int, onSel: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().glass().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == sel
            val sc by animateFloatAsState(if (on) 1.04f else 1f, spring(dampingRatio = .45f, stiffness = 500f), label = "seg")
            Box(
                Modifier.weight(1f).graphicsLayer { scaleX = sc; scaleY = sc }
                    .clip(CircleShape)
                    .background(if (on) Color.White.copy(alpha = .42f) else Color.Transparent)
                    .clickable { onSel(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) { T(o, 13, if (on) tx() else mu()) }
        }
    }
}

@Composable
fun GlassSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (on) 20.dp else 0.dp, spring(dampingRatio = .4f, stiffness = 450f), label = "swx")
    val bg by animateColorAsState(if (on) Color(0xFF22C55E) else Color.Gray.copy(alpha = .4f), label = "swc")
    Box(Modifier.size(52.dp, 32.dp).clip(CircleShape).background(bg).clickable { onChange(!on) }.padding(3.dp)) {
        Box(Modifier.offset(x = x).size(26.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 15, modifier = Modifier.weight(1f))
        GlassSwitch(on, onChange)
    }
}

/** Liquid glass slider: the thumb stretches while you drag. Value is 0..1. */
@Composable
fun GlassSlider(value: Float, onChange: (Float) -> Unit) {
    var w by remember { mutableFloatStateOf(1f) }
    var drag by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (drag) 1.4f else 1f, spring(dampingRatio = .4f, stiffness = 400f), label = "sl")
    val half = with(LocalDensity.current) { 23.dp.toPx() }
    Box(
        Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 23.dp)
            .onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / w).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = true; onChange((it.x / w).coerceIn(0f, 1f)) },
                    onDragEnd = { drag = false },
                    onDragCancel = { drag = false }
                ) { change, _ -> change.consume(); onChange((change.position.x / w).coerceIn(0f, 1f)) }
            }
    ) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(10.dp).clip(CircleShape).background(Color.Gray.copy(alpha = .35f))) {
            Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight().background(accent()))
        }
        Box(
            Modifier.align(Alignment.CenterStart)
                .offset { IntOffset((value.coerceIn(0f, 1f) * w - half).roundToInt(), 0) }
                .graphicsLayer { scaleX = sc; scaleY = 1f + (sc - 1f) * .4f }
                .size(46.dp, 32.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = .8f))
                .border(1.dp, Color.White, CircleShape)
        )
    }
}
