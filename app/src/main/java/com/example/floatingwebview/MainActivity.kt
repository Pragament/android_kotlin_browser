package com.example.floatingwebview

import VisitedPageAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.floatingwebview.databinding.ActivityMainBinding
import com.example.floatingwebview.history.HistoryActivity
import com.example.floatingwebview.home.HomeViewModel
import com.example.floatingwebview.home.HomeViewModelFactory
import com.example.floatingwebview.settings.SettingsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


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
            openLink(page.url)
        }
        binding.moreOptions.setOnClickListener { view ->
            showMoreOptions(view)
        }
        binding.recentRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.recentRecyclerView.adapter = adapter

        // Observe recent visited pages
        lifecycleScope.launch {
            viewModel.recentPages5.collectLatest { list ->
                adapter.submitList(list.reversed())
            }
        }


        // Start floating view on click
        var lastClickTime = 0L

        binding.startButton.setOnClickListener {

          Toast.makeText(this,"oopen",Toast.LENGTH_SHORT).show()
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 2000) return@setOnClickListener // Ignore double clicks
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

    private fun showMoreOptions(view: View) {

        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.main_screen_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when(menuItem.itemId) {
                R.id.menu_history -> {
                    val intent = Intent(this, HistoryActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.menu_settings -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
        popup.show()
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
                val url = convertInputToUrl(input)

                val webView = WebView(this)
                webView.settings.javaScriptEnabled = true
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, urlStr: String?) {
                        val title = view?.title ?: ""
                        viewModel.saveVisitedPage(url, title)
                        openLink(url)
                    }
                }
                webView.loadUrl(url)
                count--
            }
        }catch (e: Exception){

        }
    }
    private fun convertInputToUrl(input: String): String {
        val cleanInput = input.lowercase().trim()
        val isDomain = Regex("""\.[a-z]{2,}""").containsMatchIn(cleanInput)

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

    private fun openLink(urlInput: String) {

        val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val behavior = sharedPreferences.getString("behavior", "floating")

        if(behavior == "inapp") {
            val intent = Intent(this, Simpleweb::class.java).apply {
                putExtra("url", urlInput)
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, FloatingWebViewService::class.java).apply {
                putExtra("url", urlInput)
            }
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
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