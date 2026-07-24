package com.example.devmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trash_items")
data class TrashEntity(
    @PrimaryKey val originalPath: String,
    val trashPath: String,
    val deletedAt: Long = System.currentTimeMillis(),
    val size: Long = 0L
)
