package com.example.floatingwebview

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LinkRouterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentData = intent?.data
        if (intentData != null) {
            val url = intentData.toString()
            val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
            val savedBehavior = sharedPreferences.getString("behavior", "floating")

            if (savedBehavior == "inapp") {
                val inappIntent = Intent(this, Simpleweb::class.java).apply {
                    putExtra("url", url)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(inappIntent)
            } else {
                val floatingIntent = Intent(this, FloatingWebViewService::class.java).apply {
                    putExtra("url", url)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(floatingIntent)
                } else {
                    startService(floatingIntent)
                }
            }
        }
        finish()
    }
}
