package com.jellyglass.dialer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*

/** App settings, backed by SharedPreferences and observable by Compose. */
object Store {
    private var sp: SharedPreferences? = null

    var themeMode by mutableIntStateOf(0)      // 0 auto, 1 light, 2 dark
    var accent by mutableIntStateOf(0)
    var wallpaper by mutableIntStateOf(0)      // 0..4 presets, 5 custom
    var customHue by mutableFloatStateOf(20f)
    var callStyle by mutableIntStateOf(0)      // 0 iOS, 1 ColorOS, 2 One UI
    var glass by mutableFloatStateOf(0.18f)
    var jelly by mutableFloatStateOf(1f)
    var aiEngine by mutableIntStateOf(2)       // 0 on-device, 1 online, 2 hybrid
    var apiKey by mutableStateOf("")
    var model by mutableStateOf("claude-haiku-5-5")
    var autoBlock by mutableStateOf(true)
    var spamBadge by mutableStateOf(true)
    var haptics by mutableStateOf(true)
    var debug by mutableStateOf(false)

    fun init(c: Context) {
        if (sp != null) return
        val p = c.applicationContext.getSharedPreferences("jg", Context.MODE_PRIVATE)
        sp = p
        themeMode = p.getInt("themeMode", 0)
        accent = p.getInt("accent", 0)
        wallpaper = p.getInt("wallpaper", 0)
        customHue = p.getFloat("customHue", 20f)
        callStyle = p.getInt("callStyle", 0)
        glass = p.getFloat("glass", 0.18f)
        jelly = p.getFloat("jelly", 1f)
        aiEngine = p.getInt("aiEngine", 2)
        apiKey = p.getString("apiKey", "") ?: ""
        model = p.getString("model", "claude-haiku-5-5") ?: "claude-haiku-5-5"
        autoBlock = p.getBoolean("autoBlock", true)
        spamBadge = p.getBoolean("spamBadge", true)
        haptics = p.getBoolean("haptics", true)
        debug = p.getBoolean("debug", false)
    }

    fun save() {
        sp?.edit()
            ?.putInt("themeMode", themeMode)
            ?.putInt("accent", accent)
            ?.putInt("wallpaper", wallpaper)
            ?.putFloat("customHue", customHue)
            ?.putInt("callStyle", callStyle)
            ?.putFloat("glass", glass)
            ?.putFloat("jelly", jelly)
            ?.putInt("aiEngine", aiEngine)
            ?.putString("apiKey", apiKey)
            ?.putString("model", model)
            ?.putBoolean("autoBlock", autoBlock)
            ?.putBoolean("spamBadge", spamBadge)
            ?.putBoolean("haptics", haptics)
            ?.putBoolean("debug", debug)
            ?.apply()
    }
}

/** Number passed in from a tel: link or ACTION_DIAL. */
object Launch {
    var number by mutableStateOf<String?>(null)
}
