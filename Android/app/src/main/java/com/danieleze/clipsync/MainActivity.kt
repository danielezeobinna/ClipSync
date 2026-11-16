package com.danieleze.clipsync

import android.os.Bundle
import android.content.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import okhttp3.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Silent sync mode - no UI
        if (intent.action == "SILENT_SYNC") {
            window.setBackgroundDrawableResource(android.R.color.transparent)
            setContent {
                SyncScreen {
                    // This runs after UI is shown
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    if (clip.isNotEmpty() && clip != ClipSyncService.lastClipContent) {
                        ClipSyncService.lastClipContent = clip
                        val sent = ClipSyncService.send(clip)
                        android.widget.Toast.makeText(
                            this,
                            if (sent) "Synced to Windows!" else "Not connected",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    } else {
                        android.widget.Toast.makeText(
                            this,
                            "Nothing new to sync",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
            }
            return
        }

        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContent {
            ClipSyncDialog()
        }
    }
}

suspend fun testConnection(ip: String): Boolean = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    val result = CompletableDeferred<Boolean>()
    val ws = client.newWebSocket(
        Request.Builder().url("ws://$ip:5765").build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                result.complete(true)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                result.complete(false)
            }
        }
    )
    val ok = withTimeoutOrNull(5.seconds) { result.await() } ?: false
    ws.cancel()
    client.dispatcher.executorService.shutdown()
    ok
}

fun saveIp(context: Context, ip: String) {
    val prefs = context.getSharedPreferences("clipsync", Context.MODE_PRIVATE)
    prefs.edit { putString("windows_ip", ip) }
}

fun loadIp(context: Context): String {
    val prefs = context.getSharedPreferences("clipsync", Context.MODE_PRIVATE)
    return prefs.getString("windows_ip", "") ?: ""
}

fun checkOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

fun requestOverlayPermission(activity: MainActivity) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${activity.packageName}".toUri()
    )
    activity.startActivity(intent)
}

@Composable
fun ClipSyncDialog() {
    val scope = rememberCoroutineScope()
    var statusText by remember {
        mutableStateOf(
            when {
                !OverlayService.isRunning -> "Not connected"
                ClipSyncService.isConnected -> "Connected"
                else -> "Connection lost"
            }
        )
    }
    var statusColor by remember {
        mutableStateOf(
            when {
                !OverlayService.isRunning -> Color(0xFFAAAAAA)
                ClipSyncService.isConnected -> Color(0xFF00CC66)
                else -> Color(0xFFE74C3C)
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1a1a2e),
        modifier = Modifier.padding(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ClipSync",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusText,
                fontSize = 13.sp,
                color = statusColor
            )

            Spacer(modifier = Modifier.height(20.dp))

            // IP Input
            val context = androidx.compose.ui.platform.LocalContext.current
            var ipText by remember { mutableStateOf(loadIp(context)) }
            var isConnected by remember { mutableStateOf(ClipSyncService.isRunning) }

            OutlinedTextField(
                value = ipText,
                onValueChange = { ipText = it },
                label = { Text("Windows IP Address", color = Color(0xFFAAAAAA)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF1e90ff),
                    unfocusedBorderColor = Color(0xFF555555),
                    cursorColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(
                visible = isConnected,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Text(
                        text = "💡 Tap the overlay icon to sync Android → Windows. Hold to open the app.",
                        fontSize = 11.sp,
                        color = Color(0xFF888888),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "💡 Tap a text field first before pasting to help the app detect where to paste.",
                        fontSize = 11.sp,
                        color = Color(0xFF888888),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Connect Button

            Button(
                onClick = {
                    if (isConnected) {
                        context.stopService(Intent(context, OverlayService::class.java))
                        val stopIntent = Intent(context, OverlayService::class.java)
                        context.stopService(stopIntent)
                        val stopService = Intent(context, ClipSyncService::class.java).apply {
                            action = "STOP"
                        }
                        context.startService(stopService)
                        isConnected = false
                        statusText = "Not connected"
                        statusColor = Color(0xFFAAAAAA)
                    } else {
                        scope.launch {
                            statusText = "Connecting..."
                            statusColor = Color(0xFFFFAA00)
                            try {
                                val result = testConnection(ipText)
                                if (result) {
                                    statusText = "Connected"
                                    statusColor = Color(0xFF00CC66)
                                    isConnected = true
                                    saveIp(context, ipText)

                                    // Check notifications
                                    val notifManager =
                                        context.getSystemService(android.app.NotificationManager::class.java)
                                    if (!notifManager.areNotificationsEnabled()) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Please enable notifications for ClipSync to work properly",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        val intent =
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(
                                                    Settings.EXTRA_APP_PACKAGE,
                                                    context.packageName
                                                )
                                            }
                                        context.startActivity(intent)
                                        return@launch
                                    }

                                    if (checkOverlayPermission(context)) {
                                        val serviceIntent =
                                            Intent(context, ClipSyncService::class.java).apply {
                                                putExtra("windows_ip", ipText)
                                            }
                                        context.startForegroundService(serviceIntent)
                                        val overlayIntent =
                                            Intent(context, OverlayService::class.java).apply {
                                                putExtra("windows_ip", ipText)
                                            }
                                        context.startService(overlayIntent)
                                    } else {
                                        requestOverlayPermission(context as MainActivity)
                                    }
                                } else {
                                    statusText = "Failed to connect"
                                    statusColor = Color(0xFFE74C3C)
                                }
                            } catch (e: Exception) {
                                statusText = "Error: ${e.message}"
                                statusColor = Color(0xFFE74C3C)
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) Color(
                        0xFFE74C3C
                    ) else Color(0xFF1e90ff)
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    if (isConnected) "Disconnect" else "Connect",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SyncScreen(onReady: () -> Unit) {
    LaunchedEffect(Unit) {
        onReady()
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1a1a2e),
        modifier = Modifier.padding(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Syncing...",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}