package com.example.devmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.devmanager.data.local.entity.RecentFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY lastAccessedAt DESC LIMIT :limit")
    fun getRecentFiles(limit: Int = 20): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recent: RecentFileEntity)

    @Query("UPDATE recent_files SET lastAccessedAt = :timestamp, accessCount = accessCount + 1 WHERE path = :path")
    suspend fun touch(path: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM recent_files WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM recent_files WHERE lastAccessedAt < :before")
    suspend fun cleanOld(before: Long)
}
