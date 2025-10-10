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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.floatingwebview.BrowserRepository.BrowserData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class Simpleweb : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlEditText: EditText
    private lateinit var goButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var homeButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var browserPickButton: ImageButton

    private val STORAGE_PERMISSION_CODE = 1
    private var downloadUrl: String? = null
    private var downloadUserAgent: String? = null
    private var downloadContentDisposition: String? = null
    private var downloadMimetype: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.simple_web_view)

        // Initialize Views
        webView = findViewById(R.id.webView)
        urlEditText = findViewById(R.id.urlEditText)
        goButton = findViewById(R.id.goButton)
        backButton = findViewById(R.id.backButton)
        forwardButton = findViewById(R.id.forwardButton)
        homeButton = findViewById(R.id.homeButton)
        refreshButton = findViewById(R.id.refreshButton)
        browserPickButton = findViewById(R.id.browserPickButton)

        // WebView settings
        webView.settings.javaScriptEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView,true)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                urlEditText.setText(url ?: "")
                updateNavigationButtons() // 👈 Auto hide/show buttons
            }
        }

        // Handle downloads
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                downloadFile(url, userAgent, contentDisposition, mimetype)
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    downloadFile(url, userAgent, contentDisposition, mimetype)
                } else {
                    this.downloadUrl = url
                    this.downloadUserAgent = userAgent
                    this.downloadContentDisposition = contentDisposition
                    this.downloadMimetype = mimetype
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
                }
            }
        }

        // Load passed URL or Google by default
        val passedUrl = intent.getStringExtra("url") ?: "https://www.google.com"
        webView.loadUrl(passedUrl)
        urlEditText.setText(passedUrl)

        // Button: Back
        backButton.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
                updateNavigationButtons()
            }
        }

        // Button: Forward
        forwardButton.setOnClickListener {
            if (webView.canGoForward()) {
                webView.goForward()
                updateNavigationButtons()
            }
        }

        // Button: Home
        homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        // Button: Refresh
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
        }

        // Button: Go
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

        // Button: Browser Picker
        browserPickButton.setOnClickListener {
            val currentUrl = webView.url ?: "https://www.google.com"
            showBrowserPicker(this, currentUrl)
        }

        val intentData = intent?.data
        if (intentData != null) {
            val url = intentData.toString()
            startFloatingWebView(url)
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted. Starting download...", Toast.LENGTH_SHORT).show()
                if(downloadUrl != null) {
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
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype))
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

            val listView = ListView(context).apply {
                this.adapter = adapter
            }

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
        val isDomain = Regex(""".\[a-z]{2,}""").containsMatchIn(cleanInput)

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

    private fun startFloatingWebView(url: String) {
        val intent = Intent(this, FloatingWebViewService::class.java).apply {
            putExtra("url", url)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
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

}
