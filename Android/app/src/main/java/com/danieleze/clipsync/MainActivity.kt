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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

@Composable
fun ClipSyncDialog() {
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("Not connected") }
    var statusColor by remember { mutableStateOf(Color(0xFFAAAAAA)) }


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

            Spacer(modifier = Modifier.height(16.dp))

            // Connect Button

            Button(
                onClick = {
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
                                saveIp(context, ipText)
                            } else {
                                statusText = "Failed to connect"
                                statusColor = Color(0xFFE74C3C)
                            }
                        } catch (_: Exception) {
                            statusText = "Cannot reach Windows app"
                            statusColor = Color(0xFFE74C3C)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1e90ff)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Connect", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}