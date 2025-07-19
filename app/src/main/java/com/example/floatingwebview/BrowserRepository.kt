package com.example.floatingwebview

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri

class BrowserRepository(private val context: Context) {

    fun getBrowsers(uri: Uri): List<BrowserData> {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val flags = PackageManager.MATCH_ALL

        val pm = context.packageManager
        return pm.queryIntentActivities(intent, flags).map { info ->
            BrowserData(
                label = info.loadLabel(pm).toString(),
                packageName = info.activityInfo.packageName,
                icon = info.loadIcon(pm)
            )
        }
    }

    data class BrowserData(
        val label: String,
        val packageName: String,
        val icon: Drawable
    )
}