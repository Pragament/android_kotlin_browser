package com.example.floatingwebview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.floatingwebview.databinding.SimpleWebViewBinding // Ensure you have View Binding enabled

class Simpleweb : AppCompatActivity() {

    private lateinit var binding: SimpleWebViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SimpleWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupClickListeners()

        // Register the EditText for the context menu
        registerForContextMenu(binding.urlEditText)

        handleInitialIntent()
    }

    // Creates the context menu when the view is long-pressed
    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (v?.id == binding.urlEditText.id) {
            menu?.setHeaderTitle("Text Actions")
            // Add menu items: menu.add(groupId, itemId, order, title)
            menu?.add(0, 1, 0, "Copy")
            menu?.add(0, 2, 1, "Copy All")
            menu?.add(0, 3, 2, "Paste")
        }
    }

    // Handles clicks on the context menu items
    override fun onContextItemSelected(item: MenuItem): Boolean {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        when (item.itemId) {
            1 -> { // Copy selected text
                val start = binding.urlEditText.selectionStart
                val end = binding.urlEditText.selectionEnd
                if (start != end) {
                    val selectedText = binding.urlEditText.text.substring(start, end)
                    clipboard.setPrimaryClip(ClipData.newPlainText("Copied Text", selectedText))
                    Toast.makeText(this, "Text copied", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            2 -> { // Copy all text
                val allText = binding.urlEditText.text.toString()
                if (allText.isNotEmpty()) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Copied Text", allText))
                    Toast.makeText(this, "All text copied", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            3 -> { // Paste text
                clipboard.primaryClip?.getItemAt(0)?.text?.let {
                    val start = binding.urlEditText.selectionStart.coerceAtLeast(0)
                    val end = binding.urlEditText.selectionEnd.coerceAtLeast(0)
                    binding.urlEditText.text.replace(start, end, it)
                    Toast.makeText(this, "Text pasted", Toast.LENGTH_SHORT).show()
                }
                return true
            }
            else -> return super.onContextItemSelected(item)
        }
    }

    private fun setupWebView() {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                binding.urlEditText.setText(url ?: "")
                updateNavigationButtons()
            }
        }
    }

    private fun setupClickListeners() {
        binding.goButton.setOnClickListener {
            val input = binding.urlEditText.text.toString().trim()
            if (input.isNotBlank()) {
                val finalUrl = convertInputToUrl(input)
                binding.urlEditText.setText(finalUrl)
                binding.urlEditText.clearFocus()
                hideKeyboard()
                binding.webView.loadUrl(finalUrl)
            }
        }
        binding.backButton.setOnClickListener { if (binding.webView.canGoBack()) binding.webView.goBack() }
        binding.forwardButton.setOnClickListener { if (binding.webView.canGoForward()) binding.webView.goForward() }
        binding.refreshButton.setOnClickListener { binding.webView.reload() }
        binding.homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            finish()
        }
        binding.floatweb.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startFloatingWebView(binding.webView.url ?: "https://www.google.com")
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(intent)
            }
        }
    }

    private fun handleInitialIntent() {
        val urlToLoad = intent?.getStringExtra("url") ?: "https://www.google.com"
        binding.webView.loadUrl(urlToLoad)
        binding.urlEditText.setText(urlToLoad)
    }

    private fun startFloatingWebView(url: String) {
        val intent = Intent(this, FloatingWebViewService::class.java).apply { putExtra("url", url) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun convertInputToUrl(input: String): String {
        val cleanInput = input.lowercase().trim()
        val isDomain = Regex("""\.[a-z]{2,}""").containsMatchIn(cleanInput)
        return if (isDomain) {
            "https://www.${cleanInput.removePrefix("https://").removePrefix("http://").removePrefix("www.")}"
        } else {
            "https://www.google.com/search?q=${Uri.encode(cleanInput)}"
        }
    }

    private fun updateNavigationButtons() {
        binding.backButton.visibility = if (binding.webView.canGoBack()) View.VISIBLE else View.GONE
        binding.forwardButton.visibility = if (binding.webView.canGoForward()) View.VISIBLE else View.GONE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.urlEditText.windowToken, 0)
    }
}