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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupMenu
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

class Simpleweb : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlEditText: EditText
    private lateinit var goButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var homeButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var browserPickButton: ImageButton
    private lateinit var moreOptionsButton: ImageButton
    private lateinit var toggleHistoryButton: ImageButton
    private lateinit var historyShortcutsRecyclerView: RecyclerView

    private val STORAGE_PERMISSION_CODE = 1
    private var downloadUrl: String? = null
    private var downloadUserAgent: String? = null
    private var downloadContentDisposition: String? = null
    private var downloadMimetype: String? = null
    private var lastVisitedUrl: String? = null

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

        // --- WebView settings ---
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webChromeClient = android.webkit.WebChromeClient()

        // Enable WebAuthn/Passkey support
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            WebSettingsCompat.setWebAuthenticationSupport(
                webView.settings,
                WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
            )
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

        // --- AI-assisted: 3-dot overflow menu → History / Settings ---
        moreOptionsButton.setOnClickListener { view ->
            showWebviewMoreOptions(view)
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
     * AI-assisted: Shows a popup menu with History and Settings options on the WebView page.
     */
    private fun showWebviewMoreOptions(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add(0, 1, 0, getString(R.string.menu_history))
        popup.menu.add(0, 2, 1, getString(R.string.menu_settings))
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
                else -> false
            }
        }
        popup.show()
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
        val cleanInput = input.lowercase().trim()
        val isDomain = Regex(""".[a-z]{2,}""").containsMatchIn(cleanInput)

        return if (isDomain) {
            val cleaned = cleanInput
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
            "https://www.$cleaned"
        } else {
            "https://www.google.com/search?q=${Uri.encode(cleanInput)}"
        }
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
