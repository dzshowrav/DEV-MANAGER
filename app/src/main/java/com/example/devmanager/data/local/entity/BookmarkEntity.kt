package com.example.devmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val path: String,
    val label: String,
    val addedAt: Long = System.currentTimeMillis()
)
