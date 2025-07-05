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
    SELECT * FROM visited_pages
    WHERE url IN (
        SELECT url
        FROM visited_pages
        GROUP BY url
        ORDER BY COUNT(*) DESC
        LIMIT 5
    )
    AND id IN (
        SELECT MAX(id)
        FROM visited_pages
        GROUP BY url
    )
""")
    fun getRecentUniquePages5(): Flow<List<VisitedPage>>
}
