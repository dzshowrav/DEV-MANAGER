package com.example.devmanager.data.model

import java.io.File

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeLabel: String,
    val size: Long,
    val lastModified: Long,
    val lastModifiedLabel: String,
    val extension: String,
    val resolution: String? = null
)

enum class SortType { NAME, SIZE, DATE, TYPE }
enum class ViewMode { DETAILED, COMPACT, GRID }
enum class MediaCategory { NONE, IMAGES, VIDEOS, MUSIC }

data class StorageInfo(val totalSpace: Long, val freeSpace: Long)

data class StorageVolumeInfo(
    val name: String,
    val path: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val totalSpace: Long = 0L,
    val freeSpace: Long = 0L
)

data class FileOperation(
    val id: String,
    val type: OperationType,
    val source: File,
    val destination: File? = null,
    val totalBytes: Long = 0L,
    val progressBytes: Long = 0L,
    val totalItems: Int = 1,
    val completedItems: Int = 0,
    val status: OperationStatus = OperationStatus.QUEUED,
    val error: String? = null
)

enum class OperationType { COPY, MOVE, DELETE, ZIP, EXTRACT }
enum class OperationStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

enum class ConflictStrategy { ASK, SKIP, REPLACE, RENAME, KEEP_BOTH }
