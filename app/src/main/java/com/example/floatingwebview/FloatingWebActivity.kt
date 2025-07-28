package com.example.floatingwebview

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class FloatingWebActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This flag ensures the window is focusable and gets input
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        val webView = WebView(this)
        setContentView(webView)

        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            isFocusable = true
            isFocusableInTouchMode = true
            isLongClickable = true
            isHapticFeedbackEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
        }

        val url = intent.getStringExtra("url") ?: "https://www.google.com"
        webView.loadUrl(url)
    }
}

