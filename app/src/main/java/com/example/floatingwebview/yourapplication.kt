package com.example.floatingwebview

import android.app.Application
import com.example.floatingwebview.home.AppDatabase

class YourApplication : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
    }
}