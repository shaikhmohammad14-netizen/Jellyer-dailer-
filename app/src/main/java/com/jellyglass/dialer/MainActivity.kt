package com.jellyglass.dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        enableEdgeToEdge()
        handle(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        val d = i?.data
        if (d != null && d.scheme == "tel") Launch.number = d.schemeSpecificPart
    }
}

@Composable
fun App() {
    val dark = Store.themeMode == 2 || (Store.themeMode == 0 && isSystemInDarkTheme())
    CompositionLocalProvider(LocalDark provides dark) {
        Wallpaper(dark) { Root() }
    }
}

private val TITLES = listOf("Favorites", "Recents", "Keypad", "AI", "Settings")
private val GLYPHS = listOf("\u2605", "\u21BA", "\u2317", "\u2726", "\u2699")

@Composable
fun Root() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val perms = remember(refresh) { has(ctx, Manifest.permission.READ_CONTACTS) && has(ctx, Manifest.permission.READ_CALL_LOG) }
    val log by produceState(emptyList<LogEntry>(), refresh) { value = withContext(Dispatchers.IO) { loadCallLog(ctx) } }
    val contacts by produceState(emptyList<Contact>(), refresh) { value = withContext(Dispatchers.IO) { loadContacts(ctx) } }
    LaunchedEffect(Launch.number) { if (Launch.number != null) tab = 2 }
    val ask = {
        val list = mutableListOf(
            Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG, Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(list.toTypedArray())
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            T(TITLES[tab], 32, weight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 10.dp))
            if (!perms && tab <= 2) {
                GlassCard(Modifier.padding(bottom = 10.dp)) {
                    T("Allow access", 16, weight = FontWeight.SemiBold)
                    T("Contacts, call log, phone and SMS permissions let the dialer show recents, place calls and send quick replies.", 13, mu(), modifier = Modifier.padding(vertical = 6.dp))
                    PillBtn("Grant access", accent(), Modifier.fillMaxWidth(), 48) { ask() }
                }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> FavoritesScreen(contacts)
                    1 -> RecentsScreen(log)
                    2 -> KeypadScreen(contacts)
                    3 -> AiScreen(log)
                    else -> SettingsScreen(refresh)
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)
                .fillMaxWidth().glass().padding(6.dp)
        ) {
            TITLES.forEachIndexed { i, t ->
                val on = i == tab
                Column(
                    Modifier.weight(1f).jellyClick { tab = i }.clip(CircleShape)
                        .background(if (on) Color.White.copy(alpha = .42f) else Color.Transparent)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    T(GLYPHS[i], 19, if (on) accent() else mu())
                    T(t, 10, if (on) accent() else mu())
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(contacts: List<Contact>) {
    val ctx = LocalContext.current
    val favs = contacts.filter { it.starred }.distinctBy { it.name }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (favs.isNotEmpty()) {
            items(favs.chunked(3)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { c ->
                        Column(
                            Modifier.weight(1f).jellyClick { placeCall(ctx, c.number) }.glass().padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Avatar(c.name, 56)
                            T(c.name, 13, modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp), align = TextAlign.Center)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            item { GlassCard { T("Star a contact in your Contacts app and it shows up here.", 14, mu()) } }
        }
        item { T("All contacts", 14, mu(), modifier = Modifier.padding(start = 8.dp, top = 6.dp)) }
        items(contacts.distinctBy { it.name + it.number }) { c ->
            ListRow(c.name, c.number, c.name) { placeCall(ctx, c.number) }
        }
    }
}

@Composable
fun ListRow(title: String, sub: String, avatarName: String, titleColor: Color = tx(), onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().jellyClick(onClick).glass().padding(start = 9.dp, end = 20.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Avatar(avatarName)
        Column(Modifier.weight(1f)) {
            T(title, 16, titleColor, FontWeight.SemiBold)
            T(sub, 13, mu())
        }
    }
}

@Composable
fun RecentsScreen(log: List<LogEntry>) {
    val ctx = LocalContext.current
    var f by remember { mutableIntStateOf(0) }
    val shown = when (f) {
        1 -> log.filter { it.type == CallLog.Calls.MISSED_TYPE }
        2 -> log.filter { Spam.check(it.number).score >= .35f && it.name == null }
        else -> log
    }
    Column {
        Seg(listOf("All", "Missed", "Spam"), f, { f = it }, Modifier.padding(bottom = 10.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (shown.isEmpty()) item { GlassCard { T("Nothing here yet.", 14, mu()) } }
            items(shown) { e ->
                val kind = when (e.type) {
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    CallLog.Calls.REJECTED_TYPE -> "Declined"
                    CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                    CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
                    else -> "Call"
                }
                val whenTxt = DateUtils.getRelativeTimeSpanString(e.date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                val title = e.name?.takeIf { it.isNotBlank() } ?: e.number.ifBlank { "Unknown" }
                val spam = if (Store.spamBadge && e.name == null) Spam.check(e.number) else null
                val sub = kind + " \u00B7 " + whenTxt + (if (spam != null && spam.score >= .35f) " \u00B7 \u26A0 " + spam.label else "")
                ListRow(title, sub, title, if (e.type == CallLog.Calls.MISSED_TYPE) Color(0xFFFF453A) else tx()) { placeCall(ctx, e.number) }
            }
        }
    }
}

@Composable
fun KeypadScreen(contacts: List<Contact>) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var digits by remember { mutableStateOf("") }
    LaunchedEffect(Launch.number) { Launch.number?.let { digits = it; Launch.number = null } }
    val d = digits.filter { it.isDigit() }
    val match = if (d.length >= 2) contacts.firstOrNull { it.number.filter { c -> c.isDigit() }.contains(d) } else null
    val keys = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"), listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"), listOf("*" to "", "0" to "+", "#" to "")
    )
    Column(Modifier.fillMaxSize().padding(bottom = 100.dp), verticalArrangement = Arrangement.Bottom) {
        T(digits.ifEmpty { " " }, 38, weight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
        T(if (match != null) "Call " + match.name else " ", 14, accent(), FontWeight.SemiBold, Modifier.fillMaxWidth().padding(vertical = 6.dp), TextAlign.Center)
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (k, sub) ->
                    Row(
                        Modifier.weight(1f).jellyClick {
                            digits += k
                            if (Store.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }.height(64.dp).glass(),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                    ) {
                        T(k, 26)
                        if (sub.isNotEmpty()) T(sub, 9, mu(), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.weight(1f).jellyClick { placeCall(ctx, match?.number ?: digits) }.height(64.dp)
                    .clip(CircleShape).background(Color(0xFF22C55E)),
                contentAlignment = Alignment.Center
            ) { T("\u260E", 26, Color.White) }
            Box(Modifier.weight(1f).jellyClick { digits = digits.dropLast(1) }.height(64.dp), contentAlignment = Alignment.Center) {
                T("\u232B", 24, mu())
            }
        }
    }
}

@Composable
fun AiScreen(log: List<LogEntry>) {
    val scope = rememberCoroutineScope()
    var number by remember { mutableStateOf("") }
    var spamOut by remember { mutableStateOf("") }
    var replies by remember { mutableStateOf("") }
    var insight by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            T("AI engine", 16, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Seg(listOf("On-device", "Online", "Hybrid"), Store.aiEngine, { Store.aiEngine = it; Store.save() })
            T(
                when (Store.aiEngine) {
                    0 -> "Private and offline. Uses built-in spam rules only."
                    1 -> "Uses the online model. Add your API key in Settings."
                    else -> "Online model when a key is set, built-in rules otherwise."
                }, 13, mu()
            )
        }
        GlassCard {
            T("Spam checker", 16, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            GlassField(number, { number = it }, "Enter a phone number")
            Spacer(Modifier.height(8.dp))
            PillBtn(if (busy) "Checking\u2026" else "Check number", accent(), Modifier.fillMaxWidth(), 48) {
                if (number.isBlank() || busy) return@PillBtn
                val r = Spam.check(number)
                val base = r.label + ": " + r.reason
                busy = true
                scope.launch {
                    val ai = AiEngine.complete(
                        "In two short sentences, say whether the phone number $number looks like a spam or scam call. Local rule result: $base.",
                        base
                    )
                    spamOut = ai
                    busy = false
                }
            }
            if (spamOut.isNotEmpty()) T(spamOut, 14, modifier = Modifier.padding(top = 10.dp))
        }
        GlassCard {
            T("Smart replies", 16, weight = FontWeight.SemiBold)
            T("Draft polite texts to send when you decline a call.", 13, mu(), modifier = Modifier.padding(vertical = 6.dp))
            PillBtn("Write replies", accent(), Modifier.fillMaxWidth(), 48) {
                if (busy) return@PillBtn
                busy = true
                scope.launch {
                    replies = AiEngine.complete(
                        "Write 3 short, polite text messages (under 12 words each) to send when declining a phone call. One per line, no numbering.",
                        QuickReply.defaults.joinToString("\n")
                    )
                    busy = false
                }
            }
            if (replies.isNotEmpty()) T(replies, 14, modifier = Modifier.padding(top = 10.dp))
        }
        GlassCard {
            T("Recents insights", 16, weight = FontWeight.SemiBold)
            T("Summarise your recent calls and what needs a reply.", 13, mu(), modifier = Modifier.padding(vertical = 6.dp))
            PillBtn("Summarise", accent(), Modifier.fillMaxWidth(), 48) {
                if (busy) return@PillBtn
                val recent = log.take(20)
                val missed = recent.count { it.type == CallLog.Calls.MISSED_TYPE }
                val fb = "${recent.size} recent calls, $missed missed."
                busy = true
                scope.launch {
                    val lines = recent.joinToString("\n") { (it.name ?: it.number) + " type=" + it.type + " duration=" + it.duration + "s" }
                    insight = AiEngine.complete("Summarise these recent calls in 3 short bullet points and say who to call back:\n$lines", fb)
                    busy = false
                }
            }
            if (insight.isNotEmpty()) T(insight, 14, modifier = Modifier.padding(top = 10.dp))
        }
        T(
            "Note: Android does not let third-party apps record or listen to call audio, so live transcripts are not possible. AI here works on numbers, recents and text.",
            12, mu(), modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
fun GlassField(value: String, onChange: (String) -> Unit, hint: String, password: Boolean = false) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = tx(), fontSize = 15.sp), cursorBrush = SolidColor(accent()),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().glass().padding(horizontal = 18.dp, vertical = 13.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) T(hint, 15, mu())
            inner()
        }
    )
}

private fun open(ctx: Context, i: Intent) {
    try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "This screen is not available on your phone", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SettingsScreen(refresh: Int) {
    val ctx = LocalContext.current
    val pkg = ctx.packageName
    val rm = ctx.getSystemService(RoleManager::class.java)
    var tick by remember { mutableIntStateOf(0) }
    val dialerHeld = remember(refresh, tick) { rm.isRoleHeld(RoleManager.ROLE_DIALER) }
    val screenHeld = remember(refresh, tick) { rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    fun change(f: () -> Unit) { f(); Store.save() }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            T("Phone", 16, weight = FontWeight.SemiBold)
            T(if (dialerHeld) "Jelly Dialer is your default phone app." else "Make Jelly Dialer your default phone app so it can show incoming and in-call screens.", 13, mu(), modifier = Modifier.padding(vertical = 6.dp))
            if (!dialerHeld) PillBtn("Set as default dialer", accent(), Modifier.fillMaxWidth(), 48) {
                if (rm.isRoleAvailable(RoleManager.ROLE_DIALER)) roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            }
            Spacer(Modifier.height(8.dp))
            if (!screenHeld) PillBtn("Enable spam call screening", Color(0xFF8B5CF6), Modifier.fillMaxWidth(), 48) {
                if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            } else T("Spam call screening is on.", 13, mu())
            ToggleRow("Auto-block likely spam", Store.autoBlock) { change { Store.autoBlock = it } }
            ToggleRow("Show spam badges", Store.spamBadge) { change { Store.spamBadge = it } }
            ToggleRow("Haptic feedback", Store.haptics) { change { Store.haptics = it } }
        }
        GlassCard {
            T("Appearance", 16, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Seg(listOf("Light", "Auto", "Dark"), when (Store.themeMode) { 1 -> 0; 2 -> 2; else -> 1 }, { change { Store.themeMode = when (it) { 0 -> 1; 2 -> 2; else -> 0 } } })
            T("Theme color", 13, mu(), modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Accents.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(38.dp).jellyClick { change { Store.accent = i } }.clip(CircleShape).background(c)
                            .border(if (Store.accent == i) 3.dp else 1.dp, Color.White, CircleShape)
                    )
                }
            }
            T("Background", 13, mu(), modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Seg(WallNames.take(3), Store.wallpaper.takeIf { it < 3 } ?: -1, { change { Store.wallpaper = it } })
            Spacer(Modifier.height(6.dp))
            Seg(WallNames.drop(3), (Store.wallpaper - 3).takeIf { it >= 0 } ?: -1, { change { Store.wallpaper = it + 3 } })
            if (Store.wallpaper == 5) {
                T("Custom color", 13, mu(), modifier = Modifier.padding(top = 10.dp))
                GlassSlider(Store.customHue / 360f, { change { Store.customHue = it * 360f } })
            }
        }
        GlassCard {
            T("Call screen style", 16, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Seg(listOf("iOS", "ColorOS", "One UI"), Store.callStyle, { change { Store.callStyle = it } })
            Spacer(Modifier.height(10.dp))
            PillBtn("Preview incoming call", accent(), Modifier.fillMaxWidth(), 48) {
                ctx.startActivity(Intent(ctx, InCallActivity::class.java).putExtra("preview", true))
            }
            T("Incoming calls also appear in your phone's own call banner or island, because Jelly Dialer posts a standard call notification.", 12, mu(), modifier = Modifier.padding(top = 8.dp))
        }
        GlassCard {
            T("Liquid glass", 16, weight = FontWeight.SemiBold)
            T("Transparency", 13, mu(), modifier = Modifier.padding(top = 8.dp))
            GlassSlider(Store.glass / .4f, { change { Store.glass = it * .4f } })
            T("Jelly bounce", 13, mu())
            GlassSlider(Store.jelly / 2f, { change { Store.jelly = it * 2f } })
        }
        GlassCard {
            T("AI", 16, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Seg(listOf("On-device", "Online", "Hybrid"), Store.aiEngine, { change { Store.aiEngine = it } })
            Spacer(Modifier.height(8.dp))
            GlassField(Store.apiKey, { change { Store.apiKey = it } }, "Anthropic API key", password = true)
            Spacer(Modifier.height(8.dp))
            GlassField(Store.model, { change { Store.model = it } }, "Model name")
        }
        GlassCard {
            T("System settings", 16, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val links = listOf<Pair<String, () -> Intent>>(
                "Sound and vibration" to { Intent(Settings.ACTION_SOUND_SETTINGS) },
                "Ringtone" to { Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE) },
                "Call notifications" to { Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg) },
                "Full-screen call alerts" to {
                    if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg"))
                    else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                },
                "Blocked numbers" to { ctx.getSystemService(TelecomManager::class.java).createManageBlockedNumbersIntent() },
                "SIM and calling accounts" to { Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS) },
                "Voicemail" to { Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL) },
                "Default apps" to { Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS) },
                "Network and Wi-Fi calling" to { Intent(Settings.ACTION_WIRELESS_SETTINGS) },
                "Display" to { Intent(Settings.ACTION_DISPLAY_SETTINGS) },
                "Battery optimisation" to { Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
                "App info" to { Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")) }
            )
            links.forEach { (label, mk) ->
                Row(Modifier.fillMaxWidth().jellyClick { try { open(ctx, mk()) } catch (_: Exception) {} }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(label, 15, modifier = Modifier.weight(1f))
                    T("\u203A", 20, mu())
                }
            }
        }
        GlassCard {
            T("Developer", 16, weight = FontWeight.SemiBold)
            ToggleRow("Show call debug info", Store.debug) { change { Store.debug = it } }
        }
    }
}
