package com.example.floatingwebview

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.floatingwebview.databinding.FloatingWebViewLayoutBinding
import com.example.floatingwebview.home.AppDatabase
import com.example.floatingwebview.home.VisitedPage
import com.example.floatingwebview.home.VisitedPageDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// ... (package and imports remain unchanged)

class FloatingWebViewService : Service() {
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val activeWindows = mutableMapOf<Int, Pair<View, WindowManager.LayoutParams>>()
    private var nextWindowId = 0
    private val openedUrls = mutableSetOf<String>()
    var openurlmacther=""
    private val CHANNEL_ID = "FloatingWebViewChannel"
    private val NOTIFICATION_ID = 101

    // Global toggle flag
    private var isJavaScriptEnabled = true
    private lateinit var visitedPageDao: VisitedPageDao
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        visitedPageDao = AppDatabase.getInstance(applicationContext).visitedPageDao()
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val url = intent?.getStringExtra("url") ?: "https://www.google.com"
            val size = intent?.getStringExtra("size") ?: "medium"
            showFloatingWebView(url, size)
        } catch (e: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }
var count=0;
    var opentab=true
    private fun showFloatingWebView(url: String, size: String = "medium") {
        if (opentab&&openedUrls.contains(openurlmacther)) {
            Log.d("FloatingWebView", "URL already opened: $url")
            return
        }
        opentab=true;
        if(count==0){
            openurlmacther=url
            count++;
        }
        openedUrls.add(url)
        count++;
        val windowId = nextWindowId++
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val binding = FloatingWebViewLayoutBinding.inflate(inflater)
        val rootView = binding.root

        val displayMetrics = resources.displayMetrics
        val (width, height) = when (size) {
            "small" -> Pair((displayMetrics.widthPixels * 0.5).toInt(), (displayMetrics.heightPixels * 0.4).toInt())
            "medium" -> Pair((displayMetrics.widthPixels * 0.7).toInt(), (displayMetrics.heightPixels * 0.6).toInt())
            "large" -> Pair((displayMetrics.widthPixels * 0.9).toInt(), (displayMetrics.heightPixels * 0.8).toInt())
            else -> Pair((displayMetrics.widthPixels * 0.7).toInt(), (displayMetrics.heightPixels * 0.6).toInt())
        }

        val params = WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100 + (nextWindowId * 30)
            y = 100 + (nextWindowId * 30)
        }

        // Get the WindowManager and Context
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val context = this // or applicationContext if not in an Activity/Service

        setupWebView(
            binding.webView, url, rootView, params,
            windowManager = windowManager,
            context = context
        )
        setupCloseButton(binding.closeButton, windowId)
        setupDragListener(binding.headerView, windowId)
        setupMoreOptionsButton(binding.moreOptionsButton, binding.webView)
        setupResizeListener(binding.resizeHandle, rootView, params)
        setupJsToggle(binding.jsToggle, binding.jsStatusIcon, binding.webView)
        val drawableRes = if (isJavaScriptEnabled) R.drawable.circle_green else R.drawable.circle_red
        binding.jsStatusIcon.setBackgroundResource(drawableRes)

        try {
            windowManager.addView(rootView, params)
            activeWindows[windowId] = Pair(rootView, params)
        } catch (e: Exception) {
            binding.webView.destroy()
        }
    }
    private var lastVisitedUrl: String? = null
    private fun setupWebView(
        webView: WebView,
        url: String,
        rootView: View,
        params: WindowManager.LayoutParams,
        windowManager: WindowManager,
        context: Context
    ) {
        WebView.setWebContentsDebuggingEnabled(true)
        webView.settings.apply {
            javaScriptEnabled = isJavaScriptEnabled
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
        }

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.isLongClickable = true
        webView.isHapticFeedbackEnabled = true

        webView.webViewClient = WebViewClient()
        webView.loadUrl(url)

        // Remove FLAG_NOT_FOCUSABLE on user interaction
        webView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
                if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    windowManager.updateViewLayout(rootView, params)
                    v.post {
                        v.requestFocus()
                        val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
            false
        }


        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                val actualUrl = url ?: return

                // 🔁 Don't save if it's the same as last visited
                if (actualUrl == lastVisitedUrl) return

                lastVisitedUrl = actualUrl

                val title = view?.title ?: actualUrl
                val faviconUrl = "https://www.google.com/s2/favicons?domain=${Uri.parse(actualUrl).host}&sz=64"

                saveVisitedPage(actualUrl, title, faviconUrl)
            }
        }


        webView.setOnLongClickListener {
            if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                windowManager.updateViewLayout(rootView, params)
            }
            webView.requestFocus()
            false // Let the default text selection ActionMode handle long press
        }

        // Remove the setOnFocusChangeListener that resets FLAG_NOT_FOCUSABLE after 3 seconds!
        // If you want to restore FLAG_NOT_FOCUSABLE, do it only when the WebView actually loses focus:
        webView.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                // Only set FLAG_NOT_FOCUSABLE when focus is lost
                if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) {
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    try {
                        windowManager.updateViewLayout(rootView, params)
                    } catch (_: Exception) {}
                }
            }
        }

        // DO NOT add setOnCreateContextMenuListener for text selection!
        // The default selection handles and floating ActionMode will appear automatically.
    }

    private fun saveVisitedPage(url: String, title: String, faviconUrl: String) {
        val currentTimestamp = System.currentTimeMillis()
        val page = VisitedPage(url = url, title = title, faviconUrl = faviconUrl, timestamp = currentTimestamp)

        serviceScope.launch {
            try {
                visitedPageDao.insert(page)
            } catch (e: Exception) {
                Log.e("FloatingWebViewService", "Error saving visited page", e)
            }
        }
    }

    private fun setupCloseButton(closeButton: ImageButton, windowId: Int) {
        closeButton.setOnClickListener {
            removeWindow(windowId)
        }
    }

    private fun setupDragListener(headerView: View, windowId: Int) {
        headerView.setOnTouchListener { view, event ->
            val windowPair = activeWindows[windowId] ?: return@setOnTouchListener false
            val params = windowPair.second
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.tag = listOf(params.x.toFloat(), params.y.toFloat(), event.rawX, event.rawY)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    (view.tag as? List<Float>)?.let { (initialX, initialY, initialTouchX, initialTouchY) ->
                        params.x = (initialX + (event.rawX - initialTouchX)).toInt()
                        params.y = (initialY + (event.rawY - initialTouchY)).toInt()
                        windowManager.updateViewLayout(windowPair.first, params)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupMoreOptionsButton(moreButton: ImageButton, webView: WebView) {
        moreButton.setOnClickListener {
            val popup = PopupMenu(this, moreButton)
            popup.menuInflater.inflate(R.menu.webview_options_menu, popup.menu)

            val parentView = moreButton.rootView
            val windowId = activeWindows.entries.find { it.value.first == parentView }?.key
            val params = activeWindows[windowId]?.second

            params?.let {
                it.flags = it.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                windowManager.updateViewLayout(parentView, it)
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.go_back -> {
                        if (webView.canGoBack()) webView.goBack()
                        else Toast.makeText(this, "No previous page", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.go_forward -> {
                        if (webView.canGoForward()) webView.goForward()
                        else Toast.makeText(this, "No forward page", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.reload -> {
                        webView.reload()
                        true
                    }
                    R.id.new_tab -> {
                        opentab=false
                        showFloatingWebView(webView.url ?: "https://www.google.com", "medium")
                        true
                    }
                    else -> false
                }
            }

            popup.setOnDismissListener {
                params?.let {
                    it.flags = it.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    windowManager.updateViewLayout(parentView, it)
                }
            }

            popup.show()
        }
    }

    private fun setupResizeListener(resizeHandle: ImageView, rootView: View, params: WindowManager.LayoutParams) {
        resizeHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialWidth = 0
            private var initialHeight = 0
            private var initialX = 0f
            private var initialY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialWidth = params.width
                        initialHeight = params.height
                        initialX = event.rawX
                        initialY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialX).toInt()
                        val dy = (event.rawY - initialY).toInt()
                        params.width = (initialWidth + dx).coerceAtLeast(300)
                        params.height = (initialHeight + dy).coerceAtLeast(300)
                        windowManager.updateViewLayout(rootView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun setupJsToggle(jsToggle: LinearLayout, jsStatusIndicator: View, webView: WebView) {
        jsToggle.setOnClickListener {
            isJavaScriptEnabled = !isJavaScriptEnabled
            webView.settings.javaScriptEnabled = isJavaScriptEnabled
            webView.reload()

            val drawableRes = if (isJavaScriptEnabled) {
                R.drawable.circle_green
            } else {
                R.drawable.circle_red
            }
            jsStatusIndicator.setBackgroundResource(drawableRes)

            Toast.makeText(
                this,
                "JavaScript ${if (isJavaScriptEnabled) "Enabled" else "Disabled"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun removeWindow(windowId: Int) {
        try {
            activeWindows[windowId]?.let { (view, _) ->
                windowManager.removeView(view)
                (view.findViewById<WebView>(R.id.webView))?.destroy()
                activeWindows.remove(windowId)
            }
            if (activeWindows.isEmpty()) stopSelf()
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        activeWindows.keys.toList().forEach { removeWindow(it) }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        activeWindows.keys.firstOrNull()?.let { removeWindow(it) }
    }
}
