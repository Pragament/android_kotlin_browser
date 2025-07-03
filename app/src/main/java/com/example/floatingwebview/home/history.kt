package com.example.floatingwebview.home

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "visited_pages")
data class VisitedPage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val faviconUrl: String = "", // 🆕 Add this field
    val timestamp: Long = System.currentTimeMillis()
)
