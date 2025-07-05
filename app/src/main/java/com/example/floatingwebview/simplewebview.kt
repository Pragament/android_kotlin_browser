package com.example.floatingwebview



import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import java.net.URLEncoder

class Simpleweb : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlEditText: EditText
    private lateinit var goButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var homeButton: ImageButton
    private lateinit var refreshButton: ImageButton
    private lateinit var floatWebButton: ImageButton

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
        floatWebButton = findViewById(R.id.floatweb)

        // WebView settings
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()

        // Load passed URL or Google by default
        val passedUrl = intent.getStringExtra("url") ?: "https://www.google.com"
        webView.loadUrl(passedUrl)
        urlEditText.setText(passedUrl)

        // Update EditText when URL changes
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                urlEditText.setText(url ?: "")
            }
        }

        // Button: Back
        backButton.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }

        // Button: Forward
        forwardButton.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
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
            floatWebButton.visibility=visibility
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

        // Button: Float WebView
        floatWebButton.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                val currentUrl = webView.url ?: "https://www.google.com"
                startFloatingWebView(currentUrl)
            } else {
                Toast.makeText(this, "Please enable overlay permission", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(intent)
            }
        }
    }

    private fun convertInputToUrl(input: String): String {
        val cleanInput = input.lowercase().trim()
        val isDomain = Regex("""\.[a-z]{2,}""").containsMatchIn(cleanInput)

        return if (isDomain) {
            val cleaned = cleanInput
                .removePrefix("http://")
                .removePrefix("https://")
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
}
