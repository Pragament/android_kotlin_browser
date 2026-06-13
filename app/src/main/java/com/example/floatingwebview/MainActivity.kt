package com.example.floatingwebview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity is kept only to handle the overlay permission request flow
 * used by FloatingWebViewService. The old home screen UI has been removed.
 *
 * The app launcher entry point is now Simpleweb (see AndroidManifest.xml).
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Immediately redirect to Simpleweb — MainActivity is no longer a home screen
        launchSimpleweb()
        finish()
    }

    private fun launchSimpleweb() {
        val intent = Intent(this, Simpleweb::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    /**
     * Starts FloatingWebViewService for a given URL.
     * Called externally (e.g. from link routing flows).
     */
    fun startFloatingWindow(urlInput: String) {
        val sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val behavior = sharedPreferences.getString("behavior", "floating")

        if (behavior == "inapp") {
            val intent = Intent(this, Simpleweb::class.java).apply {
                putExtra("url", urlInput)
            }
            startActivity(intent)
        } else {
            val intent = Intent(this, FloatingWebViewService::class.java).apply {
                putExtra("url", urlInput)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        @Suppress("DEPRECATION")
        startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission required.", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
    }
}
