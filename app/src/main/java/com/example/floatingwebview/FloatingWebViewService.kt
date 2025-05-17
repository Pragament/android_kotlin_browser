package com.example.floatingwebview

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.floatingwebview.databinding.FloatingWebViewLayoutBinding

class FloatingWebViewService : Service() {
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val activeWindows = mutableMapOf<Int, Pair<View, WindowManager.LayoutParams>>()
    private var nextWindowId = 0

    private val CHANNEL_ID = "FloatingWebViewChannel"
    private val NOTIFICATION_ID = 101

    override fun onBind(intent: Intent?): IBinder? = null

//    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d("FloatingWebView", "Service created")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating WebView Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for Floating WebView service"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating WebView")
            .setContentText("Displaying floating web content")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

//    private fun startForegroundService() {
//        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle("Floating WebView Service")
//            .setContentText("Displaying floating web views")
//            .setSmallIcon(R.drawable.ic_notification)
//            .setPriority(NotificationCompat.PRIORITY_LOW)
//            .build()
//
//        startForeground(NOTIFICATION_ID, notification)
//    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val url = intent?.getStringExtra("url") ?: "https://www.google.com"
            val size = intent?.getStringExtra("size") ?: "medium"
            showFloatingWebView(url, size)
        } catch (e: Exception) {
            Log.e("FloatingWebView", "Error starting service", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun showFloatingWebView(url: String, size: String = "medium") {
        val windowId = nextWindowId++
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val binding = FloatingWebViewLayoutBinding.inflate(inflater)
        val rootView = binding.root

        val displayMetrics = resources.displayMetrics
        val (width, height) = when (size) {
            "small" -> Pair(
                (displayMetrics.widthPixels * 0.5).toInt(),
                (displayMetrics.heightPixels * 0.4).toInt()
            )
            "medium" -> Pair(
                (displayMetrics.widthPixels * 0.7).toInt(),
                (displayMetrics.heightPixels * 0.6).toInt()
            )
            "large" -> Pair(
                (displayMetrics.widthPixels * 0.9).toInt(),
                (displayMetrics.heightPixels * 0.8).toInt()
            )
            else -> Pair(
                (displayMetrics.widthPixels * 0.7).toInt(),
                (displayMetrics.heightPixels * 0.6).toInt()
            )
        }

        val params = WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100 + (nextWindowId * 30) // Offset each new window slightly
            y = 100 + (nextWindowId * 30)
        }

        setupWebView(binding.webView, url)
        setupCloseButton(binding.closeButton, windowId)
        setupDragListener(binding.headerView, windowId)

        try {
            windowManager.addView(rootView, params)
            activeWindows[windowId] = Pair(rootView, params)
            Log.d("FloatingWebView", "Window $windowId created")
        } catch (e: Exception) {
            Log.e("FloatingWebView", "Cannot add window", e)
            binding.webView.destroy()
        }
    }

    private fun setupWebView(webView: WebView, url: String) {
        try {
            WebView.setWebContentsDebuggingEnabled(true)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    Log.e("WebView", "Error loading $failingUrl: $description")
                }
            }

            webView.loadUrl(url)
        } catch (e: Exception) {
            Log.e("WebView", "WebView setup error", e)
            webView.loadData("<h1>Error loading page</h1>", "text/html",
                "UTF-8")
        }
    }

    private fun setupCloseButton(closeButton: ImageButton, windowId: Int) {
        closeButton.setOnClickListener {
            removeWindow(windowId)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener(headerView: View, windowId: Int) {
        headerView.setOnTouchListener { view, event ->
            val windowPair = activeWindows[windowId] ?: return@setOnTouchListener false
            val params = windowPair.second

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.tag = listOf(params.x, params.y, event.rawX, event.rawY)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    (view.tag as? List<Float>)
                        ?.let { (initialX, initialY, initialTouchX, initialTouchY) ->
                        params.x = initialX.toInt() + (event.rawX - initialTouchX).toInt()
                        params.y = initialY.toInt() + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(windowPair.first, params)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun removeWindow(windowId: Int) {
        try {
            activeWindows[windowId]?.let { (view, _) ->
                windowManager.removeView(view)
                (view.findViewById<WebView>(R.id.webView))?.destroy()
                activeWindows.remove(windowId)
                Log.d("FloatingWebView", "Window $windowId removed")
            }

            if (activeWindows.isEmpty()) {
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e("FloatingWebView", "Error removing window", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeWindows.keys.toList().forEach { windowId ->
            removeWindow(windowId)
        }
        Log.d("FloatingWebView", "Service destroyed")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        activeWindows.keys.firstOrNull()?.let { removeWindow(it) }
    }
}