package com.danieleze.clipsync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ClipSyncService : Service() {

    companion object {
        var isRunning = false
        var lastClipContent = ""
        var windowsIp = ""
        const val CHANNEL_ID = "clipsync_channel"
        const val NOTIF_ID = 1
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "DISCONNECT") {
            stopSelf()
            return START_NOT_STICKY
        }
        windowsIp = intent?.getStringExtra("windows_ip") ?: ""
        isRunning = true
        startForeground(NOTIF_ID, buildNotification("Connecting to $windowsIp..."))
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        serviceScope.launch {
            while (isRunning) {
                try {
                    val request = Request.Builder()
                        .url("http://$windowsIp:5765/clip")
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val content = response.body?.string() ?: ""
                        if (content != lastClipContent && content.isNotEmpty()) {
                            lastClipContent = content
                            // Auto-copy to clipboard on main thread
                            withContext(Dispatchers.Main) {
                                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ClipSync", content))
                            }
                        }
                        updateNotification("Connected to $windowsIp")
                    }
                } catch (_: Exception) {
                    updateNotification("Disconnected — retrying...")
                }
                delay(2000)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "ClipSync", NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val disconnectIntent = Intent(this, ClipSyncService::class.java).apply {
            action = "DISCONNECT"
        }
        val disconnectPending = PendingIntent.getService(
            this, 0, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        @Suppress("DEPRECATION")
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ClipSync")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .addAction(android.R.drawable.ic_delete, "Disconnect", disconnectPending)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }
}