package com.danieleze.clipsync

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ClipSyncService : Service() {
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    companion object {
        var isRunning = false
        var windowsIp = ""
        var lastClipContent = ""
        var isConnected = false
        const val CHANNEL_ID = "clipsync_channel"
        const val NOTIF_ID = 1
    }

    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val syncRunnable = object : Runnable {
        override fun run() {
            syncNow()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        windowsIp = intent?.getStringExtra("windows_ip") ?: windowsIp
        isRunning = true
        startForeground(NOTIF_ID, buildNotification())
        handler.post(syncRunnable)

        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "ClipSync::WakeLock"
            ).also {
                it.setReferenceCounted(false)
                @SuppressLint("WakelockTimeout")
                it.acquire() // no timeout
            }
        }
        return START_STICKY
    }

    private fun syncNow() {
        // Read Android clipboard on main thread
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val androidClip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""

        Thread {
            // Windows → Android
            try {
                val request = Request.Builder()
                    .url("http://$windowsIp:5765/clip")
                    .build()
                val response = client.newCall(request).execute()
                isConnected = response.isSuccessful
                if (response.isSuccessful) {
                    val content = response.body?.string() ?: ""
                    if (content.isNotEmpty() && content != lastClipContent) {
                        lastClipContent = content
                        handler.post {
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("ClipSync", content)
                            )
                        }
                    }
                }
            } catch (_: Exception) {}

            // Android → Windows
            if (androidClip.isNotEmpty() && androidClip != lastClipContent) {
                try {
                    val body = androidClip.toRequestBody("text/plain".toMediaType())
                    val request = Request.Builder()
                        .url("http://$windowsIp:5765/clip")
                        .post(body)
                        .build()
                    client.newCall(request).execute()
                    lastClipContent = androidClip
                } catch (_: Exception) {}
            }
        }.start()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)

        // 1. Channel Level (The most important part)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "System Updates", // Use a generic but honest name
            NotificationManager.IMPORTANCE_MIN // No icon in status bar, no sound
        ).apply {
            setShowBadge(false) // Don't show the dot on the app icon
            lockscreenVisibility = Notification.VISIBILITY_SECRET // Hide from lockscreen
        }
        manager.createNotificationChannel(channel)

        // 2. Notification Level
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Transparent .png icon
            .setContentTitle("ClipSync is running")
            .setContentText(null)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(syncRunnable)
        isRunning = false
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}