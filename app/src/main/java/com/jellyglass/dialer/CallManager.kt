package com.jellyglass.dialer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallScreeningService
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.compose.runtime.*
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.compose.ui.graphics.toArgb

/** Shared call state used by the service, notifications and the call screens. */
object CallManager {
    var current by mutableStateOf<Call?>(null)
    var tick by mutableIntStateOf(0)
    var muted by mutableStateOf(false)
    var speaker by mutableStateOf(false)
    var service: InCallService? = null

    fun number(call: Call): String = call.details.handle?.schemeSpecificPart ?: ""

    fun displayName(ctx: Context, call: Call): String {
        val n = number(call)
        val cached = call.details.callerDisplayName
        if (!cached.isNullOrBlank()) return cached
        return lookup(ctx, n) ?: n.ifBlank { "Unknown" }
    }

    fun lookup(ctx: Context, number: String): String? {
        if (number.isBlank() || !has(ctx, Manifest.permission.READ_CONTACTS)) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun answer() { current?.answer(VideoProfile.STATE_AUDIO_ONLY) }
    fun decline() { current?.reject(false, null) }
    fun end() { current?.disconnect() }
    fun toggleHold() {
        val c = current ?: return
        if (c.state == Call.STATE_HOLDING) c.unhold() else c.hold()
    }
    fun toggleMute() { service?.setMuted(!muted) }
    fun toggleSpeaker() {
        service?.setAudioRoute(if (speaker) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }
}

object CallNotifier {
    const val CH_IN = "jg_incoming"
    const val CH_ON = "jg_ongoing"
    const val ID = 4242
    const val ANSWER = "com.jellyglass.dialer.ANSWER"
    const val DECLINE = "com.jellyglass.dialer.DECLINE"
    const val HANGUP = "com.jellyglass.dialer.HANGUP"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val a = NotificationChannel(CH_IN, "Incoming calls", NotificationManager.IMPORTANCE_HIGH)
        a.setSound(null, null)
        a.enableVibration(false)
        a.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(a)
        val b = NotificationChannel(CH_ON, "Ongoing calls", NotificationManager.IMPORTANCE_LOW)
        b.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(b)
    }

    fun build(ctx: Context, call: Call): Notification {
        val name = CallManager.displayName(ctx, call)
        val person = Person.Builder().setName(name).setImportant(true).build()
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        fun act(a: String) = PendingIntent.getBroadcast(
            ctx, a.hashCode(), Intent(ctx, CallActionReceiver::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val ringing = call.state == Call.STATE_RINGING
        val style = if (ringing) NotificationCompat.CallStyle.forIncomingCall(person, act(DECLINE), act(ANSWER))
        else NotificationCompat.CallStyle.forOngoingCall(person, act(HANGUP))
        val b = NotificationCompat.Builder(ctx, if (ringing) CH_IN else CH_ON)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(name)
            .setContentText(if (ringing) "Incoming call" else "Ongoing call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setStyle(style)
            .setColor(Accents[Store.accent.coerceIn(0, Accents.lastIndex)].toArgb2())
        if (ringing) {
            b.setPriority(NotificationCompat.PRIORITY_HIGH).setFullScreenIntent(open, true)
        } else {
            val t = call.details.connectTimeMillis
            if (t > 0) b.setWhen(t).setShowWhen(true).setUsesChronometer(true)
        }
        return b.build()
    }

    private fun androidx.compose.ui.graphics.Color.toArgb2(): Int = this.toArgb()
}

class CallService : InCallService() {
    private val cb = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            CallManager.tick++
            update(call)
            if (state == Call.STATE_ACTIVE) launchUi()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) { CallManager.tick++ }
    }

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        CallNotifier.channels(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.service = this
        call.registerCallback(cb)
        CallManager.current = call
        CallManager.tick++
        update(call)
        if (call.state != Call.STATE_RINGING) launchUi()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(cb)
        val next = calls.lastOrNull { it !== call }
        CallManager.current = next
        CallManager.tick++
        if (next == null) {
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
            getSystemService(NotificationManager::class.java).cancel(CallNotifier.ID)
        } else {
            update(next)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.muted = audioState?.isMuted ?: false
        CallManager.speaker = audioState?.route == CallAudioState.ROUTE_SPEAKER
    }

    override fun onDestroy() {
        CallManager.service = null
        CallManager.current = null
        super.onDestroy()
    }

    private fun update(call: Call) {
        val n = CallNotifier.build(this, call)
        try {
            startForeground(CallNotifier.ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } catch (e: Exception) {
            try { getSystemService(NotificationManager::class.java).notify(CallNotifier.ID, n) } catch (_: Exception) {}
        }
    }

    private fun launchUi() {
        try {
            startActivity(
                Intent(this, InCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: Exception) {
        }
    }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CallNotifier.ANSWER -> CallManager.answer()
            CallNotifier.DECLINE -> CallManager.decline()
            CallNotifier.HANGUP -> CallManager.end()
        }
    }
}

/** Blocks likely spam from numbers that are not in your contacts (needs the Call screening role). */
class ScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        Store.init(this)
        val number = details.handle?.schemeSpecificPart ?: ""
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val block = incoming && Store.autoBlock && number.isNotBlank() &&
            Spam.check(number).score >= 0.7f && CallManager.lookup(this, number) == null
        val r = CallResponse.Builder()
        if (block) r.setDisallowCall(true).setRejectCall(true).setSkipNotification(false)
        respondToCall(details, r.build())
    }
}
