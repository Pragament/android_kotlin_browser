package com.example.floatingwebview.home

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitedPageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: VisitedPage)

    @Query("SELECT * FROM visited_pages GROUP BY id ORDER BY timestamp DESC ")
    fun getRecentUniquePages(): Flow<List<VisitedPage>>

    @Query("SELECT * FROM visited_pages ORDER BY timestamp DESC")
    fun getAllPages(): Flow<List<VisitedPage>>

    @Query("DELETE FROM visited_pages WHERE id = :id")
    suspend fun deletePage(id: Int)

    @Query("DELETE FROM visited_pages")
    suspend fun clearAll()


    @Query("""
    SELECT *
    FROM visited_pages AS visited_page
    WHERE visited_page.timestamp = (
        SELECT MAX(latest_visit.timestamp)
        FROM visited_pages AS latest_visit
        WHERE latest_visit.url = visited_page.url
    )
    AND visited_page.id = (
        SELECT MAX(latest_visit_with_same_timestamp.id)
        FROM visited_pages AS latest_visit_with_same_timestamp
        WHERE latest_visit_with_same_timestamp.url = visited_page.url
          AND latest_visit_with_same_timestamp.timestamp = visited_page.timestamp
    )
    ORDER BY visited_page.timestamp DESC, visited_page.id DESC
    LIMIT 5
""")
    fun getRecentUniquePages5(): Flow<List<VisitedPage>>

    @Query("SELECT url FROM visited_pages GROUP BY url ORDER BY MAX(timestamp) DESC")
    fun getUniqueUrlsSortedByRecent(): Flow<List<String>>
}

