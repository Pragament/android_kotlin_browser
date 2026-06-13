package com.example.floatingwebview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat

/**
 * Helper object for launching Chrome Custom Tabs with passkey support
 */
object ChromeCustomTabHelper {

    private const val TAG = "ChromeCustomTab"

    /**
     * Opens a URL in Chrome Custom Tab with full passkey/WebAuthn support
     */
    fun openUrl(context: Context, url: String) {
        try {
            Log.d(TAG, "Opening URL in Chrome Custom Tab: $url")
            
            val colorSchemeParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(ContextCompat.getColor(context, R.color.purple_500))
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorSchemeParams)
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .build()

            // Add FLAG_ACTIVITY_NEW_TASK when launching from a Service
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            customTabsIntent.launchUrl(context, Uri.parse(url))
            Log.d(TAG, "Chrome Custom Tab launched successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Chrome Custom Tab: ${e.message}", e)
            Toast.makeText(context, "Failed to open Chrome: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
