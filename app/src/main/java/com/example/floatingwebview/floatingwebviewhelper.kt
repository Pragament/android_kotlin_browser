package com.example.floatingwebview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

// utils/FloatingWebViewHelper.kt
object FloatingWebViewHelper {

    fun startFloatingWebView(context: Context, url: String, size: String = "medium") {
        val intent = Intent(context, FloatingWebViewService::class.java).apply {
            putExtra("url", url)
            putExtra("size", size)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun requestOverlayPermission(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivityForResult(intent, 1001)
    }
}
