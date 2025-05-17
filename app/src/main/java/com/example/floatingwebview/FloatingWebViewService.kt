package com.example.floatingwebview

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import com.example.floatingwebview.databinding.FloatingWebViewLayoutBinding

class FloatingWebViewService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingWebView: View
    private lateinit var binding: FloatingWebViewLayoutBinding

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url") ?: "https://www.google.com"
        val size = intent?.getStringExtra("size") ?: "medium"
        showFloatingWebView(url, size)
        return START_NOT_STICKY
    }

    private fun showFloatingWebView(url: String, size: String = "medium") {
//        val themedContext = ContextThemeWrapper(applicationContext,
//            android.R.style.Theme_DeviceDefault_Light)
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        binding = FloatingWebViewLayoutBinding.inflate(inflater)
        floatingWebView = binding.root

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
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        binding.webView.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        setupWebView(binding.webView, url)
        setupCloseButton(binding.closeButton)
        setupDragListener()

        binding.root.setBackgroundColor(Color.WHITE)
        binding.headerView.setBackgroundColor(Color.parseColor("#6200EE"))
        binding.webView.setBackgroundColor(Color.WHITE)

        windowManager.addView(floatingWebView, params)
    }

    private fun setupWebView(webView: WebView, url: String) {
        WebView.setWebContentsDebuggingEnabled(true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl(url)
    }

    private fun setupCloseButton(closeButton: ImageButton) {
        closeButton.setOnClickListener {
            stopSelf()
        }
    }

    private fun setupDragListener() {
        var initialX: Int
        var initialY: Int
        var initialTouchX: Float
        var initialTouchY: Float

        @SuppressLint("ClickableViewAccessibility")
        binding.headerView.setOnTouchListener { view, event ->
            val params = floatingWebView.layoutParams as WindowManager.LayoutParams

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    view.tag = listOf(initialX, initialY, initialTouchX, initialTouchY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.performClick()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val (initialX, initialY, initialTouchX, initialTouchY) =
                        view.tag as? List<Float> ?: return@setOnTouchListener false

                    params.x = initialX.toInt() + (event.rawX - initialTouchX).toInt()
                    params.y = initialY.toInt() + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingWebView, params)
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingWebView.isInitialized) {
            windowManager.removeView(floatingWebView)
        }
    }
}