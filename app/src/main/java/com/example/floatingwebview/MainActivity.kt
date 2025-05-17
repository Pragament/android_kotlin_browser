package com.example.floatingwebview

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.floatingwebview.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startButton.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startFloatingWebView()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // New way for Android 11+
            startActivity(intent)
        } else {
            // Legacy way for older versions
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }

        // Remove the 'return' statement - let the activity continue
    }

    private fun startFloatingWebView() {
        try {
            val intent = Intent(this, FloatingWebViewService::class.java)
                .apply {
                putExtra("url", binding.urlEditText.text.toString())
                putExtra("size", when (binding.sizeRadioGroup.checkedRadioButtonId) {
                    R.id.smallSize -> "small"
                    R.id.mediumSize -> "medium"
                    R.id.largeSize -> "large"
                    else -> "medium"
                })
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting web view: ${e.message}",
                Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingWebView()
            } else {
                Toast.makeText(
                    this,
                    "Overlay permission is required to display the floating web view",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
    }
}