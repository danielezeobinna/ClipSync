package com.danieleze.clipsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.content.Context
import androidx.core.content.edit
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.core.net.toUri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Silent sync mode - no UI
        if (intent.action == "SILENT_SYNC") {
            window.setBackgroundDrawableResource(android.R.color.transparent)
            setContent {
                SyncScreen {
                    // This runs after UI is shown
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    if (clip.isNotEmpty() && clip != ClipSyncService.lastClipContent) {
                        ClipSyncService.lastClipContent = clip
                        Thread {
                            try {
                                val body = clip.toRequestBody("text/plain".toMediaType())
                                val request = Request.Builder()
                                    .url("http://${ClipSyncService.windowsIp}:5765/clip")
                                    .post(body)
                                    .build()
                                OkHttpClient().newCall(request).execute()
                                runOnUiThread {
                                    android.widget.Toast.makeText(
                                        this,
                                        "Synced to Windows!",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    finish()
                                }
                            } catch (_: Exception) {
                                runOnUiThread { finish() }
                            }
                        }.start()
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
                                val result = withContext(Dispatchers.IO) {
                                    val client = OkHttpClient.Builder()
                                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                                        .build()
                                    val request = Request.Builder()
                                        .url("http://${ipText}:5765/clip")
                                        .build()
                                    val response = client.newCall(request).execute()
                                    response.isSuccessful
                                }
                                if (result) {
                                    statusText = "Connected"
                                    statusColor = Color(0xFF00CC66)
                                    isConnected = true
                                    saveIp(context, ipText)

                                    // Check notifications
                                    val notifManager = context.getSystemService(android.app.NotificationManager::class.java)
                                    if (!notifManager.areNotificationsEnabled()) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Please enable notifications for ClipSync to work properly",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        context.startActivity(intent)
                                        return@launch
                                    }

                                    if (checkOverlayPermission(context)) {
                                        val serviceIntent = Intent(context, ClipSyncService::class.java).apply {
                                            putExtra("windows_ip", ipText)
                                        }
                                        context.startForegroundService(serviceIntent)
                                        val overlayIntent = Intent(context, OverlayService::class.java).apply {
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
                colors = ButtonDefaults.buttonColors(containerColor = if (isConnected) Color(0xFFE74C3C) else Color(0xFF1e90ff)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isConnected) "Disconnect" else "Connect", color = Color.White, fontWeight = FontWeight.Bold)
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