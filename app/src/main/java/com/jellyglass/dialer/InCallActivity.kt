package com.jellyglass.dialer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        val preview = intent.getBooleanExtra("preview", false)
        setContent { CompositionLocalProvider(LocalDark provides true) { CallRoot(preview) { finishAndRemoveTask() } } }
    }
}

class CallActions(val answer: () -> Unit, val decline: () -> Unit, val end: () -> Unit, val keypad: () -> Unit)
class Ctl(val glyph: String, val label: String, val on: Boolean, val click: () -> Unit)

fun controls(ctx: Context, st: Int, a: CallActions) = listOf(
    Ctl("\uD83C\uDFA4", "Mute", CallManager.muted) { CallManager.toggleMute() },
    Ctl("\u2317", "Keypad", false) { a.keypad() },
    Ctl("\uD83D\uDD0A", "Speaker", CallManager.speaker) { CallManager.toggleSpeaker() },
    Ctl("\uFF0B", "Add call", false) { ctx.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
    Ctl("\u23F8", "Hold", st == Call.STATE_HOLDING) { CallManager.toggleHold() },
    Ctl("\u263A", "Contacts", false) { ctx.startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
)

@Composable
fun statusText(st: Int): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val start = CallManager.current?.details?.connectTimeMillis ?: 0L
    return when (st) {
        Call.STATE_ACTIVE -> if (start > 0) { val s = ((now - start) / 1000).coerceAtLeast(0); "%02d:%02d".format(s / 60, s % 60) } else "00:00"
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_DISCONNECTED -> "Call ended"
        Call.STATE_RINGING -> "Incoming call"
        else -> "Calling\u2026"
    }
}

@Composable
fun CallRoot(preview: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val tick = CallManager.tick
    val call = CallManager.current
    val st = if (call != null) call.state else if (preview) Call.STATE_RINGING else Call.STATE_DISCONNECTED
    val number = if (call != null) CallManager.number(call) else "+91 98765 43210"
    val name = if (call != null) CallManager.displayName(ctx, call) else "Riya"
    var keys by remember { mutableStateOf(false) }
    LaunchedEffect(call == null, tick) { if (call == null && !preview) { delay(700); onClose() } }
    val act = CallActions(
        answer = { if (preview) onClose() else CallManager.answer() },
        decline = { if (preview) onClose() else CallManager.decline() },
        end = { if (preview) onClose() else CallManager.end() },
        keypad = { keys = true }
    )
    val ringing = st == Call.STATE_RINGING
    val style = Store.callStyle
    CallBackdrop(style) {
        if (ringing) when (style) {
            0 -> IosIncoming(name, number, act)
            1 -> OppoIncoming(name, number, act)
            else -> OneIncoming(name, number, act)
        } else when (style) {
            0 -> IosCall(name, number, st, act)
            1 -> OppoCall(name, number, st, act)
            else -> OneCall(name, number, st, act)
        }
        if (keys) DtmfPad { keys = false }
        if (Store.debug) T("state=$st number=$number", 11, Color.Yellow, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp))
    }
}

@Composable
fun CallBackdrop(style: Int, content: @Composable BoxScope.() -> Unit) {
    when (style) {
        0 -> Wallpaper(true) { Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)), content = content) }
        1 -> {
            val p = palette(true)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p[1], p[0], p[3]))), content = content)
        }
        else -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF14151C), Color(0xFF0B0B10)))), content = content)
    }
}

@Composable
fun SpamBadge(number: String) {
    val ctx = LocalContext.current
    if (!Store.spamBadge) return
    val r = remember(number) { Spam.check(number) }
    val known = remember(number) { CallManager.lookup(ctx, number) != null }
    if (known || r.score < .35f) return
    Box(Modifier.padding(top = 10.dp).clip(CircleShape).background(Color(0xFFFF453A).copy(alpha = .85f)).padding(horizontal = 14.dp, vertical = 6.dp)) {
        T("\u26A0 " + r.label + " \u00B7 " + r.reason, 12, Color.White)
    }
}

@Composable
fun QuickReplies(number: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickReply.defaults.forEach { t ->
            GlassPill(t, Modifier.fillMaxWidth(), 46) { QuickReply.send(ctx, number, t); onDone() }
        }
    }
}

@Composable
fun Pulse(content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(1f, 2.3f, infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing)), label = "s")
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(110.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = ((2.3f - s) / 1.3f).coerceIn(0f, 1f) }.border(2.dp, Color.White, CircleShape))
        content()
    }
}

@Composable
fun Chevron() {
    val t = rememberInfiniteTransition(label = "chev")
    val a by t.animateFloat(.2f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    T("\u2303", 24, Color.White.copy(alpha = a))
}

@Composable
fun SlideToAnswer(onDone: () -> Unit) {
    val dens = LocalDensity.current
    var w by remember { mutableFloatStateOf(0f) }
    val off = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val maxX = (w - with(dens) { 68.dp.toPx() }).coerceAtLeast(1f)
    Box(Modifier.fillMaxWidth().height(68.dp).glass().onSizeChanged { w = it.width.toFloat() }) {
        T("Slide to answer", 15, Color.White.copy(alpha = .9f), modifier = Modifier.align(Alignment.Center))
        Box(
            Modifier.padding(6.dp).offset { IntOffset(off.value.roundToInt(), 0) }.size(56.dp).clip(CircleShape)
                .background(Color(0xFF22C55E))
                .pointerInput(maxX) {
                    detectHorizontalDragGestures(
                        onDragEnd = { scope.launch { if (off.value > maxX * .75f) onDone() else off.animateTo(0f, spring(dampingRatio = .4f, stiffness = 300f)) } },
                        onDragCancel = { scope.launch { off.animateTo(0f) } }
                    ) { change, dx -> change.consume(); scope.launch { off.snapTo((off.value + dx).coerceIn(0f, maxX)) } }
                },
            contentAlignment = Alignment.Center
        ) { T("\u279C", 22, Color.White) }
    }
}

// ---------- Incoming: iOS ----------
@Composable
fun IosIncoming(name: String, number: String, a: CallActions) {
    var replies by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        T(name, 42, Color.White, FontWeight.Light)
        T("mobile", 16, Color.White.copy(alpha = .7f), modifier = Modifier.padding(top = 4.dp))
        SpamBadge(number)
        Spacer(Modifier.weight(1f))
        if (replies) { QuickReplies(number) { a.decline() }; Spacer(Modifier.height(12.dp)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassPill("\u23F0 Remind me", Modifier.weight(1f)) { a.decline() }
            GlassPill("\u2709 Message", Modifier.weight(1f)) { replies = !replies }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillBtn("Decline", Color(0xFFFF453A), Modifier.weight(1f), 62) { a.decline() }
            PillBtn("Accept", Color(0xFF22C55E), Modifier.weight(1f), 62) { a.answer() }
        }
    }
}

// ---------- Incoming: ColorOS ----------
@Composable
fun OppoIncoming(name: String, number: String, a: CallActions) {
    var replies by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Pulse { Avatar(name, 110) }
        Spacer(Modifier.height(36.dp))
        T(name, 32, Color.White, FontWeight.Medium)
        T("Incoming call \u00B7 Mobile", 15, Color.White.copy(alpha = .75f), modifier = Modifier.padding(top = 6.dp))
        SpamBadge(number)
        Spacer(Modifier.weight(1f))
        if (replies) { QuickReplies(number) { a.decline() }; Spacer(Modifier.height(12.dp)) }
        GlassPill("\u2709 Reply with message", Modifier.fillMaxWidth()) { replies = !replies }
        Spacer(Modifier.height(14.dp))
        SlideToAnswer { a.answer() }
        Spacer(Modifier.height(14.dp))
        PillBtn("Decline", Color(0xFFFF453A), Modifier.fillMaxWidth(), 56) { a.decline() }
    }
}

// ---------- Incoming: One UI ----------
@Composable
fun OneIncoming(name: String, number: String, a: CallActions) {
    var replies by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        T(name, 34, Color.White, FontWeight.Medium)
        T(number.ifBlank { "Unknown" } + " \u00B7 Mobile", 14, Color.White.copy(alpha = .7f), modifier = Modifier.padding(top = 6.dp))
        SpamBadge(number)
        Spacer(Modifier.height(28.dp))
        Avatar(name, 140)
        Spacer(Modifier.weight(1f))
        if (replies) { QuickReplies(number) { a.decline() }; Spacer(Modifier.height(12.dp)) }
        GlassPill("\u2709 Send message", Modifier.padding(bottom = 26.dp)) { replies = !replies }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Chevron()
                Box(Modifier.jellyClick { a.answer() }.size(84.dp).clip(CircleShape).background(Color(0xFF22C55E)), contentAlignment = Alignment.Center) { T("\u260E", 34, Color.White) }
                T("Answer", 13, Color.White, modifier = Modifier.padding(top = 8.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Chevron()
                Box(Modifier.jellyClick { a.decline() }.size(84.dp).clip(CircleShape).background(Color(0xFFFF453A)), contentAlignment = Alignment.Center) {
                    T("\u260E", 34, Color.White, modifier = Modifier.graphicsLayer { rotationZ = 135f })
                }
                T("Decline", 13, Color.White, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

// ---------- In call: iOS ----------
@Composable
fun IosCall(name: String, number: String, st: Int, a: CallActions) {
    val ctx = LocalContext.current
    val c = controls(ctx, st, a)
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        T(name, 36, Color.White, FontWeight.Light)
        T(statusText(st), 17, Color.White.copy(alpha = .75f), modifier = Modifier.padding(top = 4.dp))
        SpamBadge(number)
        Spacer(Modifier.weight(1f))
        c.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { b ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.jellyClick(b.click).size(76.dp).clip(CircleShape)
                                .background(if (b.on) Color.White else Color.White.copy(alpha = .2f)),
                            contentAlignment = Alignment.Center
                        ) { T(b.glyph, 26, if (b.on) Color.Black else Color.White) }
                        T(b.label, 12, Color.White, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.jellyClick { a.end() }.size(76.dp).clip(CircleShape).background(Color(0xFFFF453A)), contentAlignment = Alignment.Center) {
            T("\u260E", 30, Color.White, modifier = Modifier.graphicsLayer { rotationZ = 135f })
        }
    }
}

// ---------- In call: ColorOS ----------
@Composable
fun OppoCall(name: String, number: String, st: Int, a: CallActions) {
    val ctx = LocalContext.current
    val c = controls(ctx, st, a)
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().glass().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(name, 52)
            Column(Modifier.weight(1f)) {
                T(name, 18, Color.White, FontWeight.SemiBold)
                T(statusText(st), 13, Color.White.copy(alpha = .75f))
            }
        }
        SpamBadge(number)
        Spacer(Modifier.weight(1f))
        c.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { b ->
                    Box(
                        Modifier.weight(1f).jellyClick(b.click).height(56.dp).clip(CircleShape)
                            .background(if (b.on) Color.White else Color.White.copy(alpha = .2f)),
                        contentAlignment = Alignment.Center
                    ) { T(b.glyph + "  " + b.label, 15, if (b.on) Color.Black else Color.White) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PillBtn("End call", Color(0xFFFF453A), Modifier.fillMaxWidth(), 60) { a.end() }
    }
}

// ---------- In call: One UI ----------
@Composable
fun OneCall(name: String, number: String, st: Int, a: CallActions) {
    val ctx = LocalContext.current
    val c = controls(ctx, st, a)
    val five = listOf(c[3], c[4], c[2], c[0], c[1])
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))
        T(name, 34, Color.White, FontWeight.Medium)
        T(statusText(st), 16, Color.White.copy(alpha = .7f), modifier = Modifier.padding(top = 6.dp))
        SpamBadge(number)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().glass().padding(10.dp), horizontalArrangement = Arrangement.SpaceAround) {
            five.forEach { b ->
                Box(
                    Modifier.jellyClick(b.click).size(52.dp).clip(CircleShape).background(if (b.on) Color.White else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) { T(b.glyph, 22, if (b.on) Color.Black else Color.White) }
            }
        }
        Spacer(Modifier.height(18.dp))
        PillBtn("End call", Color(0xFFFF453A), Modifier.fillMaxWidth(), 60) { a.end() }
    }
}

@Composable
fun DtmfPad(onClose: () -> Unit) {
    val handler = remember { Handler(Looper.getMainLooper()) }
    val keys = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf('*', '0', '#'))
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).clickable { onClose() }, contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).glass(RoundedCornerShape(40.dp)).padding(16.dp)) {
            keys.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.weight(1f).jellyClick {
                                val call = CallManager.current
                                call?.playDtmfTone(k)
                                handler.postDelayed({ call?.stopDtmfTone() }, 150)
                            }.height(60.dp).glass(),
                            contentAlignment = Alignment.Center
                        ) { T(k.toString(), 24, Color.White) }
                    }
                }
            }
            PillBtn("Hide", Color.White.copy(alpha = .2f), Modifier.fillMaxWidth().padding(top = 6.dp), 48) { onClose() }
        }
    }
}
