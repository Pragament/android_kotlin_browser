package com.example.floatingwebview

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.floatingwebview.BrowserRepository.BrowserData
import com.example.floatingwebview.history.HistoryActivity
import com.example.floatingwebview.home.AppDatabase
import com.example.floatingwebview.home.HomeViewModel
import com.example.floatingwebview.home.HomeViewModelFactory
import com.example.floatingwebview.home.VisitedPage
import com.example.floatingwebview.home.VisitedPageDao
import com.example.floatingwebview.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.floatingactionbutton.FloatingActionButton

class Simpleweb : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlEditText: AutoCompleteTextView
    private lateinit var goButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var homeButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var browserPickButton: ImageButton
    private lateinit var moreOptionsButton: ImageButton
    private lateinit var toggleHistoryButton: ImageButton
    private lateinit var historyShortcutsRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var floatButton: FloatingActionButton
    private lateinit var fullscreenContainer: FrameLayout
    private lateinit var mainContentLayout: LinearLayout

    private val STORAGE_PERMISSION_CODE = 1
    private var downloadUrl: String? = null
    private var downloadUserAgent: String? = null
    private var downloadContentDisposition: String? = null
    private var downloadMimetype: String? = null
    private var lastVisitedUrl: String? = null

    // For Fullscreen Video support
    private var customView: View? = null
    private var customViewCallback: android.webkit.WebChromeClient.CustomViewCallback? = null

    // AI-assisted: history shortcuts visibility state
    private var isHistoryVisible = false

    private lateinit var visitedPageDao: VisitedPageDao
    private lateinit var viewModel: HomeViewModel
    private lateinit var historyShortcutsAdapter: VisitedPageAdapter
    private val activityScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Helper to read the saved homepage URL (default: google.com)
    private fun getSavedHomepage(): String {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(SettingsActivity.KEY_HOMEPAGE, SettingsActivity.DEFAULT_HOMEPAGE)
            ?: SettingsActivity.DEFAULT_HOMEPAGE
    }

    private fun getJavaScriptEnabled(): Boolean {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("inapp_js_enabled", true)
    }

    private fun setJavaScriptEnabled(enabled: Boolean) {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("inapp_js_enabled", enabled).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.simple_web_view)

        visitedPageDao = AppDatabase.getInstance(applicationContext).visitedPageDao()

        // --- View bindings ---
        webView = findViewById(R.id.webView)
        urlEditText = findViewById(R.id.urlEditText)
        goButton = findViewById(R.id.goButton)
        backButton = findViewById(R.id.backButton)
        forwardButton = findViewById(R.id.forwardButton)
        homeButton = findViewById(R.id.homeButton)
        refreshButton = findViewById(R.id.refreshButton)
        browserPickButton = findViewById(R.id.browserPickButton)
        moreOptionsButton = findViewById(R.id.moreOptionsButton)
        toggleHistoryButton = findViewById(R.id.toggleHistoryButton)
        historyShortcutsRecyclerView = findViewById(R.id.historyShortcutsRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        floatButton = findViewById(R.id.floatButton)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        mainContentLayout = findViewById(R.id.mainContentLayout)

        // --- WebView settings ---
        webView.settings.javaScriptEnabled = getJavaScriptEnabled()
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        // Enable WebAuthn/Passkey support
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            WebSettingsCompat.setWebAuthenticationSupport(
                webView.settings,
                WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
            )
        }

        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback

                mainContentLayout.visibility = View.GONE
                fullscreenContainer.visibility = View.VISIBLE
                fullscreenContainer.addView(view)

                setFullscreen(true)
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                if (customView == null) return

                fullscreenContainer.removeView(customView)
                fullscreenContainer.visibility = View.GONE

                customView = null
                customViewCallback = null

                mainContentLayout.visibility = View.VISIBLE

                setFullscreen(false)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null) {
                    if (isLoginPage(url)) {
                        ChromeCustomTabHelper.openUrl(this@Simpleweb, url)
                        return true
                    }
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        return false
                    }
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        return true
                    }
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                urlEditText.setText(url ?: "")
                updateNavigationButtons()

                val actualUrl = url ?: return
                if (actualUrl == lastVisitedUrl) return
                lastVisitedUrl = actualUrl

                val title = view?.title ?: actualUrl
                val faviconUrl = "https://www.google.com/s2/favicons?domain=${Uri.parse(actualUrl).host}&sz=64"
                saveVisitedPage(actualUrl, title, faviconUrl)
            }
        }

        // Handle downloads
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                downloadFile(url, userAgent, contentDisposition, mimetype)
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                    downloadFile(url, userAgent, contentDisposition, mimetype)
                } else {
                    this.downloadUrl = url
                    this.downloadUserAgent = userAgent
                    this.downloadContentDisposition = contentDisposition
                    this.downloadMimetype = mimetype
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        STORAGE_PERMISSION_CODE
                    )
                }
            }
        }

        // --- AI-assisted: Load URL from intent OR fall back to custom homepage ---
        val passedUrl = intent.getStringExtra("url")
        val startUrl = passedUrl ?: getSavedHomepage()
        webView.loadUrl(startUrl)
        urlEditText.setText(startUrl)

        // --- Navigation Buttons ---
        backButton.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
                updateNavigationButtons()
            }
        }

        forwardButton.setOnClickListener {
            if (webView.canGoForward()) {
                webView.goForward()
                updateNavigationButtons()
            }
        }

        // AI-assisted: Home button now loads the custom homepage instead of opening MainActivity
        homeButton.setOnClickListener {
            val homepage = getSavedHomepage()
            webView.loadUrl(homepage)
        }

        refreshButton.setOnClickListener {
            webView.reload()
        }

        // EditText focus hides nav buttons
        urlEditText.setOnFocusChangeListener { _, hasFocus ->
            val visibility = if (hasFocus) View.GONE else View.VISIBLE
            backButton.visibility = visibility
            forwardButton.visibility = visibility
            homeButton.visibility = visibility
            refreshButton.visibility = visibility
            browserPickButton.visibility = visibility
            moreOptionsButton.visibility = visibility
            toggleHistoryButton.visibility = visibility
        }

        // Handle Auto-Complete history suggestions
        val autocompleteUrls = mutableListOf<String>()
        val autocompleteAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, autocompleteUrls)
        urlEditText.setAdapter(autocompleteAdapter)

        lifecycleScope.launch {
            visitedPageDao.getUniqueUrlsSortedByRecent().collectLatest { list ->
                autocompleteUrls.clear()
                autocompleteUrls.addAll(list)
                autocompleteAdapter.notifyDataSetChanged()
            }
        }

        urlEditText.setOnItemClickListener { parent, _, position, _ ->
            val selectedUrl = parent.getItemAtPosition(position) as String
            urlEditText.setText(selectedUrl)
            urlEditText.clearFocus()
            hideKeyboard()
            webView.loadUrl(selectedUrl)
        }

        // Soft keyboard GO/Enter action listener
        urlEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                goButton.performClick()
                true
            } else {
                false
            }
        }

        goButton.setOnClickListener {
            val input = urlEditText.text.toString().trim()
            if (input.isNotBlank()) {
                val finalUrl = convertInputToUrl(input)
                urlEditText.setText(finalUrl)
                urlEditText.clearFocus()
                hideKeyboard()
                webView.loadUrl(finalUrl)
            }
        }

        browserPickButton.setOnClickListener {
            val currentUrl = webView.url ?: getSavedHomepage()
            showBrowserPicker(this, currentUrl)
        }

        // --- AI-assisted: 3-dot overflow menu → History / Settings / JS toggle ---
        moreOptionsButton.setOnClickListener { view ->
            showWebviewMoreOptions(view)
        }

        // --- Float FAB click listener ---
        floatButton.setOnClickListener {
            val currentUrl = webView.url ?: getSavedHomepage()
            startFloatingWindow(currentUrl)
        }

        // --- AI-assisted: Toggle history shortcuts strip ---
        toggleHistoryButton.setOnClickListener {
            isHistoryVisible = !isHistoryVisible
            historyShortcutsRecyclerView.visibility =
                if (isHistoryVisible) View.VISIBLE else View.GONE
        }

        // --- Setup history shortcuts RecyclerView ---
        setupHistoryShortcuts()
    }

    /**
     * AI-assisted: Sets up the horizontal recent history strip below the toolbar.
     * Reuses VisitedPageAdapter (same as MainActivity) and observes the Room DB.
     */
    private fun setupHistoryShortcuts() {
        val dao = (application as YourApplication).database.visitedPageDao()
        viewModel = ViewModelProvider(
            this,
            HomeViewModelFactory(dao)
        )[HomeViewModel::class.java]

        historyShortcutsAdapter = VisitedPageAdapter { page ->
            webView.loadUrl(page.url)
            // Hide strip after tapping a shortcut
            isHistoryVisible = false
            historyShortcutsRecyclerView.visibility = View.GONE
        }

        historyShortcutsRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        historyShortcutsRecyclerView.adapter = historyShortcutsAdapter

        lifecycleScope.launch {
            viewModel.recentPages5.collectLatest { list ->
                historyShortcutsAdapter.submitList(list.reversed())
            }
        }
    }

    /**
     * AI-assisted: Shows a popup menu with History, Settings, and JS Toggle options on the WebView page.
     */
    private fun showWebviewMoreOptions(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add(0, 1, 0, getString(R.string.menu_history))
        popup.menu.add(0, 2, 1, getString(R.string.menu_settings))

        val isJsEnabled = webView.settings.javaScriptEnabled
        popup.menu.add(0, 3, 2, "JavaScript: ${if (isJsEnabled) "ON" else "OFF"}")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }
                2 -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                3 -> {
                    val isEnabled = !webView.settings.javaScriptEnabled
                    webView.settings.javaScriptEnabled = isEnabled
                    setJavaScriptEnabled(isEnabled)
                    webView.reload()
                    Toast.makeText(
                        this,
                        if (isEnabled) "JavaScript Enabled" else "JavaScript Disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun startFloatingWindow(url: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please grant Overlay Permission to float the browser.", Toast.LENGTH_LONG).show()
        } else {
            val intent = Intent(this, FloatingWebViewService::class.java).apply {
                putExtra("url", url)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            finish()
        }
    }

    private fun setFullscreen(enable: Boolean) {
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.hide(
                    android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars()
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(
                    android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars()
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    private fun saveVisitedPage(url: String, title: String, faviconUrl: String) {
        val page = VisitedPage(url = url, title = title, faviconUrl = faviconUrl, timestamp = System.currentTimeMillis())
        activityScope.launch {
            try {
                visitedPageDao.insert(page)
            } catch (e: Exception) {
                Log.e("Simpleweb", "Error saving visited page", e)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted. Starting download...", Toast.LENGTH_SHORT).show()
                if (downloadUrl != null) {
                    downloadFile(downloadUrl!!, downloadUserAgent, downloadContentDisposition, downloadMimetype)
                }
            } else {
                Toast.makeText(this, "Permission Denied. Download cannot proceed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadFile(url: String, userAgent: String?, contentDisposition: String?, mimetype: String?) {
        val request = DownloadManager.Request(Uri.parse(url))
        request.setMimeType(mimetype)
        val cookies = CookieManager.getInstance().getCookie(url)
        request.addRequestHeader("cookie", cookies)
        request.addRequestHeader("User-Agent", userAgent)
        request.setDescription("Downloading file...")
        request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            URLUtil.guessFileName(url, contentDisposition, mimetype)
        )
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(applicationContext, "Downloading File", Toast.LENGTH_LONG).show()
    }

    fun showBrowserPicker(context: Context, url: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val repo = BrowserRepository(context)
            val browsers = withContext(Dispatchers.IO) {
                repo.getBrowsers(url.toUri())
            }

            if (browsers.isEmpty()) {
                Toast.makeText(context, "No browsers found", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val adapter = object : ArrayAdapter<BrowserData>(context, R.layout.browser_item_list, browsers) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: LayoutInflater.from(context)
                        .inflate(R.layout.browser_item_list, parent, false)
                    val browser = getItem(position)
                    view.findViewById<ImageView>(R.id.browserIcon).setImageDrawable(browser?.icon)
                    view.findViewById<TextView>(R.id.browserName).text = browser?.label ?: "Unknown"
                    return view
                }
            }

            val listView = ListView(context).apply { this.adapter = adapter }

            val dialog = AlertDialog.Builder(context)
                .setTitle("Open with...")
                .setView(listView)
                .setCancelable(true)
                .create()

            listView.setOnItemClickListener { _, _, position, _ ->
                val selectedBrowser = browsers[position]
                val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                    setPackage(selectedBrowser.packageName)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                context.startActivity(intent)
                dialog.dismiss()
            }

            dialog.show()
        }
    }

    private fun convertInputToUrl(input: String): String {
        val cleanInput = input.trim()
        if (cleanInput.isEmpty()) return ""

        if (cleanInput.startsWith("http://", ignoreCase = true) ||
            cleanInput.startsWith("https://", ignoreCase = true)) {
            return cleanInput
        }

        val matcher = Patterns.WEB_URL.matcher(cleanInput)
        if (matcher.matches()) {
            return "https://$cleanInput"
        }

        return "https://www.google.com/search?q=${Uri.encode(cleanInput)}"
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlEditText.windowToken, 0)
    }

    private fun updateNavigationButtons() {
        backButton.visibility = if (webView.canGoBack()) View.VISIBLE else View.GONE
        forwardButton.visibility = if (webView.canGoForward()) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        CookieManager.getInstance().flush()
    }

    /**
     * Detects login/authentication pages to open in Chrome Custom Tabs for passkey support.
     */
    private fun isLoginPage(url: String): Boolean {
        val loginPatterns = listOf(
            "/login", "/signin", "/sign-in", "/sign_in",
            "/authenticate", "/auth/", "/oauth", "/sso/",
            "/accounts/login", "/session/new", "login.php",
            "signin.php", "passkey=true"
        )
        val lowerUrl = url.lowercase()
        return loginPatterns.any { lowerUrl.contains(it) }
    }
}
