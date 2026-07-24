package com.example.devmanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.devmanager.data.local.dao.BookmarkDao
import com.example.devmanager.data.local.dao.RecentFileDao
import com.example.devmanager.data.local.dao.TrashDao
import com.example.devmanager.data.local.entity.BookmarkEntity
import com.example.devmanager.data.local.entity.RecentFileEntity
import com.example.devmanager.data.local.entity.TrashEntity

@Database(
    entities = [BookmarkEntity::class, TrashEntity::class, RecentFileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun trashDao(): TrashDao
    abstract fun recentFileDao(): RecentFileDao
}
