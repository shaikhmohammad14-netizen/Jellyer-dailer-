package com.jellyglass.dialer

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LogEntry(val number: String, val name: String?, val type: Int, val date: Long, val duration: Long)
data class Contact(val name: String, val number: String, val starred: Boolean)

fun has(ctx: Context, perm: String) =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

fun loadCallLog(ctx: Context): List<LogEntry> {
    if (!has(ctx, Manifest.permission.READ_CALL_LOG)) return emptyList()
    val out = ArrayList<LogEntry>()
    try {
        val args = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, 300)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        }
        val proj = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
        ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, proj, args, null)?.use { c ->
            while (c.moveToNext()) {
                out.add(LogEntry(c.getString(0) ?: "", c.getString(1), c.getInt(2), c.getLong(3), c.getLong(4)))
            }
        }
    } catch (_: Exception) {
    }
    return out
}

fun loadContacts(ctx: Context): List<Contact> {
    if (!has(ctx, Manifest.permission.READ_CONTACTS)) return emptyList()
    val out = ArrayList<Contact>()
    try {
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proj, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                out.add(Contact(c.getString(0) ?: "", c.getString(1) ?: "", c.getInt(2) == 1))
            }
        }
    } catch (_: Exception) {
    }
    return out
}

fun placeCall(ctx: Context, number: String) {
    if (number.isBlank()) return
    try {
        ctx.getSystemService(TelecomManager::class.java)
            .placeCall(Uri.fromParts("tel", number, null), Bundle())
    } catch (e: SecurityException) {
        Toast.makeText(ctx, "Allow the Phone permission first", Toast.LENGTH_SHORT).show()
    }
}

/** On-device heuristics. Fast, private, offline. */
object Spam {
    data class Result(val score: Float, val label: String, val reason: String)

    fun check(number: String): Result {
        val clean = number.replace(" ", "").replace("-", "")
        val d = clean.filter { it.isDigit() }
        if (d.isEmpty()) return Result(.35f, "Suspicious", "Hidden or private number")
        var s = 0f
        val why = ArrayList<String>()
        if (Regex("^(\\+?91)?140\\d{6,}").containsMatchIn(clean)) { s += .75f; why.add("telemarketing prefix 140") }
        if (Regex("(\\d)\\1{6,}").containsMatchIn(d)) { s += .35f; why.add("repeated digits") }
        if (d.length >= 14) { s += .3f; why.add("unusually long") }
        if (d.length in 3..5) { s += .15f; why.add("short code") }
        if (clean.startsWith("+") && Regex("^\\+(1900|1976|224|225|233|234|375)").containsMatchIn(clean)) { s += .5f; why.add("known scam-call region") }
        val label = when {
            s >= .7f -> "Likely spam"
            s >= .35f -> "Suspicious"
            else -> "Looks normal"
        }
        return Result(s.coerceAtMost(1f), label, if (why.isEmpty()) "No spam patterns found" else why.joinToString(", "))
    }
}

/** Replies and drafts through the online model when allowed, with an offline fallback. */
object AiEngine {
    suspend fun complete(prompt: String, fallback: String): String = withContext(Dispatchers.IO) {
        val engine = Store.aiEngine
        if (engine != 0) {
            if (Store.apiKey.isBlank()) {
                if (engine == 1) return@withContext "Add your API key in Settings to use online AI."
            } else {
                try {
                    return@withContext callApi(prompt)
                } catch (e: Exception) {
                    if (engine == 1) return@withContext "Online AI failed: ${e.message}"
                }
            }
        }
        fallback
    }

    private fun callApi(prompt: String): String {
        val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-api-key", Store.apiKey)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        val body = JSONObject()
            .put("model", Store.model)
            .put("max_tokens", 400)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val txt = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw RuntimeException("HTTP $code")
        return JSONObject(txt).getJSONArray("content").getJSONObject(0).getString("text")
    }
}

object QuickReply {
    val defaults = listOf(
        "Can't talk right now. I'll call you back soon.",
        "I'm in a meeting. Please message me.",
        "Driving at the moment. I'll call you later."
    )

    fun send(ctx: Context, number: String, text: String): Boolean {
        if (number.isBlank() || !has(ctx, Manifest.permission.SEND_SMS)) {
            Toast.makeText(ctx, "Allow SMS permission to send quick replies", Toast.LENGTH_SHORT).show()
            return false
        }
        return try {
            val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.sendTextMessage(number, null, text, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
