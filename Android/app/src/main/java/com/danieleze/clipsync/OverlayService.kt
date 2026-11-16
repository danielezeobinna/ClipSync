package com.danieleze.clipsync

import android.annotation.SuppressLint
import android.app.Service
import android.content.*
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.*
import kotlin.math.abs
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private var windowsIp = ""

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    companion object {
        var isRunning = false
        var windowsIpStatic = ""
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startClipboardWatcher()
    }

    private fun startClipboardWatcher() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener {
            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (clip.isNotEmpty() && clip != ClipSyncService.lastClipContent) {
                ClipSyncService.lastClipContent = clip
                ClipSyncService.send(clip)
                Thread {
                    try {
                        val body = clip.toRequestBody("text/plain".toMediaType())
                        val request = Request.Builder()
                            .url("http://${ClipSyncService.windowsIp}:5765/clip")
                            .post(body)
                            .build()
                        OkHttpClient().newCall(request).execute()
                    } catch (_: Exception) {
                    }
                }.start()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        windowsIp = intent?.getStringExtra("windows_ip") ?: windowsIpStatic
        windowsIpStatic = windowsIp
        showOverlay()
        isRunning = true
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 200
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                OverlayIcon()
            }
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var downTime = 0L

        composeView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    downTime = System.currentTimeMillis()
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (initialTouchX - event.rawX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        isDragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(composeView, params)
                    }
                }

                MotionEvent.ACTION_UP -> {
                    val tapDuration = System.currentTimeMillis() - downTime
                    if (!isDragging) {
                        if (tapDuration < 500) {
                            val intent =
                                Intent(applicationContext, MainActivity::class.java).apply {
                                    action = "SILENT_SYNC"
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_NO_HISTORY or
                                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                                }
                            startActivity(intent)
                        } else {
                            // Long press → open app
                            openMainActivity()
                        }
                    }
                }
            }
            true
        }

        overlayView = composeView
        windowManager.addView(overlayView, params)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        if (::overlayView.isInitialized) windowManager.removeView(overlayView)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun OverlayIcon() {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(Color(0xFF070C23), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.overlay),
            contentDescription = "ClipSync",
            modifier = Modifier.size(36.dp)
        )
    }
}