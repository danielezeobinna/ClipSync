@file:Suppress("UsePropertyAccessSyntax")

package com.danieleze.clipsync

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.os.*
import okhttp3.*
import java.util.concurrent.TimeUnit

class ClipSyncService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        var isRunning = false
        var windowsIp = ""
        var lastClipContent = ""
        var isConnected = false
        const val CHANNEL_ID = "clipsync_channel"
        const val NOTIF_ID = 1

        @Volatile
        private var socket: WebSocket? = null
        fun send(text: String): Boolean = socket?.send(text) ?: false
    }

    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private var retryDelay = 1000L
    private var stopped = false

    private fun connect() {
        if (stopped) return
        val request = Request.Builder().url("ws://$windowsIp:5765").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                retryDelay = 1000L
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.isNotEmpty() && text != lastClipContent) {
                    lastClipContent = text
                    handler.post {
                        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("ClipSync", text))
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = onDrop()
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                onDrop()
        })
    }

    private fun onDrop() {
        isConnected = false
        socket = null
        if (stopped) return
        handler.postDelayed({ connect() }, retryDelay)
        retryDelay = (retryDelay * 2).coerceAtMost(30000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        windowsIp = intent?.getStringExtra("windows_ip") ?: windowsIp
        isRunning = true
        startForeground(NOTIF_ID, buildNotification())

        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ClipSync::WakeLock"
            ).also {
                it.setReferenceCounted(false)
                @SuppressLint("WakelockTimeout")
                it.acquire() // no timeout
            }
        }
        stopped = false
        handler.removeCallbacksAndMessages(null)
        socket?.cancel()
        socket = null
        retryDelay = 1000L
        connect()
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "System Updates",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ClipSync is running")
            .setContentText(null)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()
    }

    override fun onDestroy() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        socket?.cancel()
        socket = null
        isConnected = false
        isRunning = false
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}