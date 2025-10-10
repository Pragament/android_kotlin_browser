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
        val intent = Intent(context, FloatingWebActivity::class.java).apply {
            putExtra("url", url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }


    fun requestOverlayPermission(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivityForResult(intent, 1001)
    }
}
