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
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.floatingwebview.databinding.FloatingWebViewLayoutBinding
import com.example.floatingwebview.home.AppDatabase
import com.example.floatingwebview.home.VisitedPage
import com.example.floatingwebview.home.VisitedPageDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


class FloatingWebViewService : Service() {
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val activeWindows = mutableMapOf<Int, Pair<View, WindowManager.LayoutParams>>()
    private var nextWindowId = 0
    private val openedUrls = mutableSetOf<String>()

    private val CHANNEL_ID = "FloatingWebViewChannel"
    private val NOTIFICATION_ID = 101
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url") ?: "https://www.google.com"
        if (openedUrls.contains(url) && activeWindows.isNotEmpty()) {
            return START_NOT_STICKY
        }
        openedUrls.add(url)
        showFloatingWebView(url)
        return START_NOT_STICKY
    }

    private fun handleCopyAllText(webView: WebView, mode: ActionMode?) {
        webView.evaluateJavascript("(function(){return document.body.innerText;})()") { result ->
            if (result != null && result.length > 2) {
                val allText = result.substring(1, result.length - 1).replace("\\n", "\n").replace("\\\"", "\"")
                if (allText.isNotEmpty()) {
                    copyTextToClipboard("Copied Page Text", allText)
                } else {
                    Toast.makeText(this, "No text to copy from page", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "No text to copy from page", Toast.LENGTH_SHORT).show()
            }
        }
        mode?.finish()
    }

    private fun copyTextToClipboard(label: String, text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("ClickableViewAccessibility", "deprecation")
    private fun showFloatingWebView(url: String) {
        val windowId = nextWindowId++
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val binding = FloatingWebViewLayoutBinding.inflate(inflater)
        val rootView = binding.root

        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.9).toInt()
        val height = (displayMetrics.heightPixels * 0.7).toInt()

        // --- THIS IS THE FIX ---
        // The FLAG_NOT_FOCUSABLE has been removed. This allows the WebView
        // to correctly process long-press and selection gestures.

        val params = WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // IMPORTANT: Ensure FLAG_NOT_FOCUSABLE is NOT here
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }
        // --- END OF FIX ---

        setupWebView(binding.webView, url, rootView, params)
        setupCloseButton(binding.closeButton, windowId)
        setupDragListener(binding.headerView, windowId, params)
        setupMoreOptionsButton(binding.moreOptionsButton, binding.webView)
        setupResizeListener(binding.resizeHandle, rootView, params)
        setupJsToggle(binding.jsToggle, binding.jsStatusIcon, binding.webView)

        windowManager.addView(rootView, params)
        activeWindows[windowId] = Pair(rootView, params)
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView(
        webView: WebView,
        url: String,
        rootView: View,
        params: WindowManager.LayoutParams
    ) {
        // Set up custom text selection context menu
        setupCustomActionMode(webView)

        webView.settings.apply {
            javaScriptEnabled = isJavaScriptEnabled
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val title = view?.title ?: ""
                saveVisitedPage(url ?: "", title, "")
            }
        }

        webView.loadUrl(url)

    }

    private fun setupCustomActionMode(webView: WebView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // Use reflection to safely set actionModeCallback
                val actionModeCallback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        // Add custom menu items
                        menu?.add(0, 1, 0, "Copy All Text")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                        menu?.add(0, 2, 0, "Copy Selected")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)

                        // You can also add paste functionality if needed
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        if (clipboard.hasPrimaryClip()) {
                            menu?.add(0, 3, 0, "Paste")?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                        }

                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        return false
                    }

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        when (item?.itemId) {
                            1 -> { // Copy All Text
                                handleCopyAllText(webView, mode)
                                return true
                            }
                            2 -> { // Copy Selected Text
                                handleCopySelectedText(webView, mode)
                                return true
                            }
                            3 -> { // Paste
                                handlePasteText(webView, mode)
                                return true
                            }
                        }
                        return false
                    }

                    override fun onDestroyActionMode(mode: ActionMode?) {}
                }

                // Use reflection to set the actionModeCallback
                val method = WebView::class.java.getMethod("setActionModeCallback", ActionMode.Callback::class.java)
                method.invoke(webView, actionModeCallback)

            } catch (e: Exception) {
                Log.w("FloatingWebViewService", "Could not set custom action mode callback", e)
                // Fallback: Add a long-press listener for custom menu
                setupFallbackTextMenu(webView)
            }
        } else {
            // For older Android versions, use alternative approach
            setupFallbackTextMenu(webView)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFallbackTextMenu(webView: WebView) {
        webView.setOnLongClickListener { view ->
            // By posting the menu to the message queue, this listener can
            // finish instantly, allowing the WebView's default text
            // selection to start.
            view.post {
                val popup = PopupMenu(this, view)
                popup.menu.add(0, 1, 0, "Copy All Text")
                popup.menu.add(0, 2, 0, "Copy Selected Text")

                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                if (clipboard.hasPrimaryClip()) {
                    popup.menu.add(0, 3, 0, "Paste")
                }

                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> { handleCopyAllText(webView, null); true }
                        2 -> { handleCopySelectedText(webView, null); true }
                        3 -> { handlePasteText(webView, null); true }
                        else -> false
                    }
                }
                popup.show()
            }

            // Return false to allow other long-click listeners to run.
            false
        }
    }

    private fun handleCopySelectedText(webView: WebView, mode: ActionMode?) {
        webView.evaluateJavascript("(function(){return window.getSelection().toString();})()") { result ->
            // The result of getSelection() is raw text, not a JSON string.
            // The fix is to simply remove the surrounding quotes, if they exist.
            val selectedText = result?.removeSurrounding("\"")

            if (!selectedText.isNullOrEmpty()) {
                copyTextToClipboard("Selected Text", selectedText)
            } else {
                Toast.makeText(this, "No text selected", Toast.LENGTH_SHORT).show()
            }
        }
        mode?.finish()
    }

    private fun handlePasteText(webView: WebView, mode: ActionMode?) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.isNotEmpty()) {
                // Escape the text for JavaScript
                val escapedText = text.replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
                webView.evaluateJavascript("(function(){document.execCommand('insertText', false, '$escapedText');})();", null)
                Toast.makeText(this, "Text pasted", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No text in clipboard", Toast.LENGTH_SHORT).show()
        }
        mode?.finish()
    }

    private fun removeWindow(windowId: Int) {
        activeWindows[windowId]?.let { (view, _) ->
            (view.findViewById<WebView>(R.id.webView))?.destroy()
            windowManager.removeView(view)
            activeWindows.remove(windowId)
            openedUrls.clear()
        }
        if (activeWindows.isEmpty()) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeWindows.keys.toList().forEach { removeWindow(it) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Floating WebView", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating WebView Active")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

    private fun saveVisitedPage(url: String, title: String, faviconUrl: String) {
        if (url.isBlank()) return
        val page = VisitedPage(url = url, title = title, faviconUrl = faviconUrl, timestamp = System.currentTimeMillis())
        serviceScope.launch {
            try {
                visitedPageDao.insert(page)
            } catch (e: Exception) {
                Log.e("FloatingWebViewService", "Error saving page", e)
            }
        }
    }

    private fun setupCloseButton(closeButton: ImageButton, windowId: Int) {
        closeButton.setOnClickListener { removeWindow(windowId) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener(headerView: View, windowId: Int, params: WindowManager.LayoutParams) {
        headerView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(activeWindows[windowId]?.first, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun setupMoreOptionsButton(
        moreButton: ImageButton,
        webView: WebView
    ) {
        moreButton.setOnClickListener {
            val popup = PopupMenu(this, moreButton)
            popup.menuInflater.inflate(R.menu.webview_options_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.reload -> webView.reload().let { true }
                    else -> false
                }
            }
            popup.show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupResizeListener(
        resizeHandle: ImageView,
        rootView: View,
        params: WindowManager.LayoutParams
    ) {
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
                        params.width = (initialWidth + (event.rawX - initialX)).toInt().coerceAtLeast(400)
                        params.height = (initialHeight + (event.rawY - initialY)).toInt().coerceAtLeast(400)
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
            val drawableRes = if (isJavaScriptEnabled) R.drawable.circle_green else R.drawable.circle_red
            jsStatusIndicator.setBackgroundResource(drawableRes)
            Toast.makeText(this, "JavaScript ${if (isJavaScriptEnabled) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
        }
    }
}