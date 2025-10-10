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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.*
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
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
import org.json.JSONObject

class FloatingWebViewService : Service() {

    @Volatile
    private var lastTouchedRootView: View? = null

    @Volatile
    private var lastTouchedWebView: WebView? = null

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val activeWindows = mutableMapOf<Int, Pair<View, WindowManager.LayoutParams>>()
    private var nextWindowId = 0
    private val openedUrls = mutableSetOf<String>()
    private val CHANNEL_ID = "FloatingWebViewChannel"
    private val NOTIFICATION_ID = 101

    private var isJavaScriptEnabled = true
    private lateinit var visitedPageDao: VisitedPageDao
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var openurlmacther = ""
    var count = 0

    private var lastVisitedUrl: String? = null
    private var currentSelectionPopup: PopupWindow? = null

    // Javascript bridge for selection
    private inner class SelectionBridge {
        @JavascriptInterface
        fun onDebug(msg: String?) {
            Log.d("FWV", "JS> $msg")
        }

        @JavascriptInterface
        fun onSelection(selectionJson: String?) {
            if (selectionJson.isNullOrBlank()) return
            Log.d("FWV", "SelectionBridge.onSelection -> $selectionJson")
            Handler(Looper.getMainLooper()).post {
                try {
                    val obj = JSONObject(selectionJson)
                    val left = obj.optDouble("left", 0.0).toFloat()
                    val top = obj.optDouble("top", 0.0).toFloat()
                    val width = obj.optDouble("width", 0.0).toFloat()
                    val height = obj.optDouble("height", 0.0).toFloat()
                    val text = obj.optString("text", "")

                    if (text.isNotEmpty()) {
                        showSelectionPopup(lastTouchedRootView, lastTouchedWebView, left, top, width, height, text)
                    }
                } catch (e: Exception) {
                    Log.e("FWV", "onSelection parse error", e)
                }
            }
        }

        @JavascriptInterface
        fun onTextSelected(text: String?) {
            if (text.isNullOrBlank()) return
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Copied Text", text))
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "Copied: ${text.take(200)}", Toast.LENGTH_SHORT).show()
            }
            Log.d("FWV", "SelectionBridge.onTextSelected -> ${text.take(200)}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        visitedPageDao = AppDatabase.getInstance(applicationContext).visitedPageDao()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Floating WebView Service Channel", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Channel for Floating WebView service"
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
            openurlmacther = url
            if (openedUrls.contains(url)) return START_NOT_STICKY
            openedUrls.add(url)
            showFloatingWebView(url, size)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun showFloatingWebView(url: String, size: String = "medium") {
        count++
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100 + (nextWindowId * 30)
            y = 100 + (nextWindowId * 30)
        }

        setupWebView(binding.webView, url, rootView, params, windowManager, this)
        setupHomeButton(binding.homeButton, binding.webView, this, windowId, windowManager)
        setupCloseButton(binding.closeButton, windowId)
        setupDragListener(binding.headerView, windowId)
        setupMoreOptionsButton(binding.moreOptionsButton, binding.webView)
        setupResizeListener(binding.resizeHandle, rootView, params)
        setupJsToggle(binding.jsToggle, binding.jsStatusIcon, binding.webView)
        binding.jsStatusIcon.setBackgroundResource(if (isJavaScriptEnabled) R.drawable.circle_green else R.drawable.circle_red)

        try {
            windowManager.addView(rootView, params)
            binding.webView.requestFocus()
            binding.webView.isFocusable = true
            binding.webView.isFocusableInTouchMode = true
            binding.webView.isLongClickable = true
            binding.webView.isHapticFeedbackEnabled = true
            activeWindows[windowId] = Pair(rootView, params)
        } catch (e: Exception) {
            binding.webView.destroy()
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
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

        // Add Javascript bridge
        webView.addJavascriptInterface(SelectionBridge(), "AndroidSelection")

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.isLongClickable = true
        webView.isHapticFeedbackEnabled = true

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        // Track touches
        webView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchedRootView = rootView
                    lastTouchedWebView = webView

                    // Make window focusable
                    if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
                        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                        try { windowManager.updateViewLayout(rootView, params) } catch (_: Exception) {}
                    }

                    v.post {
                        v.requestFocus()
                        v.isFocusable = true
                        v.isFocusableInTouchMode = true
                    }

                    // Dismiss any existing popup
                    currentSelectionPopup?.dismiss()
                }
            }
            false
        }

        // Handle long clicks - IMPORTANT: return false to allow WebView selection
        webView.setOnLongClickListener {
            lastTouchedRootView = rootView
            lastTouchedWebView = webView

            if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                try { windowManager.updateViewLayout(rootView, params) } catch (_: Exception) {}
            }
            webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

            // Return false to allow WebView's native selection to work
            false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val actualUrl = url ?: return
                if (actualUrl == lastVisitedUrl) return
                lastVisitedUrl = actualUrl

                val title = view?.title ?: actualUrl
                val faviconUrl = "https://www.google.com/s2/favicons?domain=${Uri.parse(actualUrl).host}&sz=64"
                saveVisitedPage(actualUrl, title, faviconUrl)

                // Inject selection detection script
                val js = """
(function() {
  if (window._fwv_sel_injected) return;
  window._fwv_sel_injected = true;
  
  try { AndroidSelection.onDebug('injection-start'); } catch(e){}
  
  var lastSelection = '';
  var selectionTimeout = null;
  
  function getSelectionInfo() {
    try {
      var sel = window.getSelection();
      if (!sel || sel.rangeCount === 0) return null;
      var text = sel.toString().trim();
      if (!text || text.length === 0) return null;
      
      var range = sel.getRangeAt(0);
      var rect = range.getBoundingClientRect();
      
      return {
        left: rect.left + window.pageXOffset,
        top: rect.top + window.pageYOffset,
        width: rect.width,
        height: rect.height,
        text: text
      };
    } catch(e) { 
      try { AndroidSelection.onDebug('getSelectionInfo error: ' + e.message); } catch(_){}
      return null; 
    }
  }
  
  function notifySelection() {
    var info = getSelectionInfo();
    if (info && info.text !== lastSelection) {
      lastSelection = info.text;
      try {
        AndroidSelection.onSelection(JSON.stringify(info));
        AndroidSelection.onDebug('selection-sent len=' + info.text.length);
      } catch(e) {
        AndroidSelection.onDebug('notify error: ' + e.message);
      }
    } else if (!info) {
      lastSelection = '';
    }
  }
  
  // Listen to selection changes with debouncing
  document.addEventListener('selectionchange', function() {
    if (selectionTimeout) clearTimeout(selectionTimeout);
    selectionTimeout = setTimeout(function() {
      notifySelection();
    }, 300);
  }, {passive: true});
  
  // Also check on touchend/mouseup
  document.addEventListener('touchend', function() {
    setTimeout(notifySelection, 350);
  }, {passive: true});
  
  document.addEventListener('mouseup', function() {
    setTimeout(notifySelection, 350);
  }, {passive: true});
  
  try { AndroidSelection.onDebug('injection-done'); } catch(e){}
})();
""".trimIndent()

                // Inject with multiple attempts to ensure it works
                view?.postDelayed({
                    view.evaluateJavascript(js, null)
                    Log.d("FWV", "Selection script injected")
                }, 300)

                view?.postDelayed({
                    view.evaluateJavascript(js, null)
                }, 1000)
            }
        }

        webView.loadUrl(url)
    }

    private fun showSelectionPopup(
        anchorRoot: View?,
        webView: WebView?,
        rectLeft: Float,
        rectTop: Float,
        rectWidth: Float,
        rectHeight: Float,
        selectedText: String
    ) {
        if (anchorRoot == null || webView == null) {
            Log.w("FWV", "showSelectionPopup: anchorRoot or webView is null")
            return
        }

        Log.d("FWV", "showSelectionPopup: textLen=${selectedText.length} left=$rectLeft top=$rectTop")

        // Dismiss any existing popup
        currentSelectionPopup?.dismiss()

        val inflater = LayoutInflater.from(this)
        val popupView = inflater.inflate(R.layout.selection_popup, null)
        val copyBtn = popupView.findViewById<TextView>(R.id.sel_copy)
        val selectAllBtn = popupView.findViewById<TextView>(R.id.sel_select_all)

        // Measure popup
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupW = popupView.measuredWidth
        val popupH = popupView.measuredHeight

        // Get WebView location on screen
        val webViewLoc = IntArray(2)
        webView.getLocationOnScreen(webViewLoc)

        // Calculate popup position (center above selection)
        val screenX = webViewLoc[0] + rectLeft.toInt()
        val screenY = webViewLoc[1] + rectTop.toInt()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Position centered above selection
        var showX = (screenX + rectWidth / 2 - popupW / 2).toInt()
        var showY = (screenY - popupH - 20).toInt()

        // Keep within screen bounds
        showX = showX.coerceIn(10, screenWidth - popupW - 10)
        showY = showY.coerceIn(10, screenHeight - popupH - 10)

        Log.d("FWV", "Popup position: x=$showX y=$showY webViewLoc=${webViewLoc.contentToString()}")

        val popup = PopupWindow(
            popupView,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = false
            elevation = 12f
            animationStyle = android.R.style.Animation_Dialog
        }

        currentSelectionPopup = popup

        try {
            popup.showAtLocation(anchorRoot, Gravity.NO_GRAVITY, showX, showY)
            Log.d("FWV", "Popup shown successfully")
        } catch (e: Exception) {
            Log.e("FWV", "Failed to show popup", e)
            try {
                popup.showAtLocation(anchorRoot, Gravity.CENTER, 0, 0)
            } catch (e2: Exception) {
                Log.e("FWV", "Failed to show popup at center", e2)
            }
        }

        copyBtn.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Copied Text", selectedText))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            popup.dismiss()
        }

        selectAllBtn.setOnClickListener {
            webView.evaluateJavascript(
                "(function(){return document.body.innerText || document.documentElement.innerText;})()") { allText ->
                val text = try {
                    JSONObject("{\"v\":$allText}").getString("v")
                } catch (e: Exception) {
                    allText?.trim('"') ?: ""
                }
                if (text.isNotEmpty()) {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Copied Text", text))
                    Toast.makeText(this, "All text copied", Toast.LENGTH_SHORT).show()
                }
                popup.dismiss()
            }
        }

        // Auto-dismiss after 8 seconds
        popupView.postDelayed({
            if (popup.isShowing) popup.dismiss()
        }, 8000)

        popup.setOnDismissListener {
            currentSelectionPopup = null
        }
    }

    private fun saveVisitedPage(url: String, title: String, faviconUrl: String) {
        val page = VisitedPage(url = url, title = title, faviconUrl = faviconUrl, timestamp = System.currentTimeMillis())
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
            openedUrls.clear()
        }
    }

    private fun setupHomeButton(homeButton: ImageButton, webView: WebView, context: Context, windowId: Int, windowManager: WindowManager) {
        homeButton.setOnClickListener {
            val currentUrl = webView.url ?: "https://www.google.com"
            activeWindows[windowId]?.first?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
                activeWindows.remove(windowId)
            }
            openedUrls.clear()
            val intent = Intent(context, Simpleweb::class.java)
            intent.putExtra("url", currentUrl)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
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
            val backItem = popup.menu.findItem(R.id.go_back)
            val forwardItem = popup.menu.findItem(R.id.go_forward)
            backItem.isEnabled = webView.canGoBack()
            forwardItem.isEnabled = webView.canGoForward()

            val parentView = moreButton.rootView
            val windowId = activeWindows.entries.find { it.value.first == parentView }?.key
            val params = activeWindows[windowId]?.second
            params?.let {
                it.flags = it.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                windowManager.updateViewLayout(parentView, it)
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.go_back -> { if (webView.canGoBack()) webView.goBack(); true }
                    R.id.go_forward -> { if (webView.canGoForward()) webView.goForward(); true }
                    R.id.reload -> { webView.reload(); true }
                    R.id.new_tab -> { showFloatingWebView(webView.url ?: "https://www.google.com", "medium"); true }
                    R.id.copy_all -> {
                        webView.evaluateJavascript("(function(){return document.body.innerText;})()") { allText ->
                            val text = allText?.trim('"') ?: ""
                            if (text.isNotEmpty()) {
                                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Copied Text", text))
                                Toast.makeText(this, "Page text copied", Toast.LENGTH_SHORT).show()
                            }
                        }
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
            jsStatusIndicator.setBackgroundResource(if (isJavaScriptEnabled) R.drawable.circle_green else R.drawable.circle_red)
            Toast.makeText(this, "JavaScript ${if (isJavaScriptEnabled) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeWindow(windowId: Int) {
        try {
            currentSelectionPopup?.dismiss()
            activeWindows[windowId]?.let { (view, _) ->
                windowManager.removeView(view)
                (view.findViewById<WebView>(R.id.webView))?.destroy()
                activeWindows.remove(windowId)
                openedUrls.clear()
            }
            if (activeWindows.isEmpty()) stopSelf()
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        currentSelectionPopup?.dismiss()
        CookieManager.getInstance().flush()
        activeWindows.keys.toList().forEach { removeWindow(it) }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        activeWindows.keys.firstOrNull()?.let { removeWindow(it) }
    }
}