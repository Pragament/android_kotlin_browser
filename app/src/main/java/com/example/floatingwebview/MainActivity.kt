package com.example.floatingwebview

import VisitedPageAdapter
import VisitedPageAdapter1
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Patterns
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.floatingwebview.databinding.ActivityMainBinding
import com.example.floatingwebview.history.HistoryActivity
import com.example.floatingwebview.home.HomeViewModel
import com.example.floatingwebview.home.HomeViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.URLEncoder


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: HomeViewModel
    private lateinit var adapter: VisitedPageAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init ViewModel
        val dao = (application as YourApplication).database.visitedPageDao()
        viewModel = ViewModelProvider(this, HomeViewModelFactory(dao))[HomeViewModel::class.java]

        // Setup RecyclerView
        adapter = VisitedPageAdapter { page ->
//            binding.urlEditText.setText(page.url)
            startFloatingWebView(page.url, page.title)
        }
        binding.moreOptions.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
        binding.recentRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recentRecyclerView.adapter = adapter

        // Observe recent visited pages
        lifecycleScope.launch {
            viewModel.recentPages5.collectLatest {
                adapter.submitList(it)
            }
        }

        // Start floating view on click
         var lastClickTime = 0L

        binding.startButton.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 1000) return@setOnClickListener // Ignore double clicks
            lastClickTime = currentTime

            if (Settings.canDrawOverlays(this)) {
                val input = binding.urlEditText.text.toString().trim()
                if (input.isNotEmpty()) {
                    fetchTitleAndLaunch(input)
                    binding.urlEditText.text.clear()
                }else{
                    Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
                }
            } else {
                requestOverlayPermission()
            }
        }

    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
    }
    var count=0;
    // Extract title using WebView before launching
    private fun fetchTitleAndLaunch(input: String) {

        try{
            if(count==0){
                count++;
        val url = if (Patterns.WEB_URL.matcher(input).matches() || input.startsWith("http")) {
            input
        } else {
            "https://www.google.com/search?q=" + URLEncoder.encode(input, "UTF-8")
        }

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, urlStr: String?) {
                val title = view?.title ?: ""
                viewModel.saveVisitedPage(url, title)
                startFloatingWebView(url, title)
            }
        }
        webView.loadUrl(url)
            }
        }catch (e: Exception){

        }finally {
            count--;
        }
    }

    private fun startFloatingWebView(urlInput: String, title: String) {
        val intent = Intent(this, FloatingWebViewService::class.java).apply {
            putExtra("url", urlInput)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                val input = binding.urlEditText.text.toString().trim()
                if (input.isNotEmpty()) {
                    fetchTitleAndLaunch(input)
                }
            } else {
                Toast.makeText(this, "Overlay permission required.", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
    }
}
