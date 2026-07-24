package com.example.devmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.devmanager.data.local.entity.TrashEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    fun getAllTrash(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash_items WHERE originalPath = :originalPath")
    suspend fun getTrashItem(originalPath: String): TrashEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TrashEntity)

    @Query("DELETE FROM trash_items WHERE originalPath = :originalPath")
    suspend fun deleteByOriginalPath(originalPath: String)

    @Query("DELETE FROM trash_items")
    suspend fun clearAll()
}
