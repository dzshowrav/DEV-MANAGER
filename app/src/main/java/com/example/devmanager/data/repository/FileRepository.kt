package com.example.devmanager.data.repository

import com.example.devmanager.data.model.ConflictStrategy
import com.example.devmanager.data.model.FileItem
import com.example.devmanager.data.model.FileOperation
import kotlinx.coroutines.flow.Flow
import java.io.File

interface FileRepository {
    fun getFiles(path: String, showHidden: Boolean): Flow<List<FileItem>>
    suspend fun listFiles(path: String, showHidden: Boolean): List<FileItem>
    suspend fun createDirectory(parent: String, name: String): Result<File>
    suspend fun createFile(parent: String, name: String): Result<File>
    suspend fun rename(file: File, newName: String): Result<File>
    suspend fun delete(file: File): Result<Unit>
    suspend fun copy(source: File, destDir: String, strategy: ConflictStrategy = ConflictStrategy.ASK): Result<File>
    suspend fun move(source: File, destDir: String, strategy: ConflictStrategy = ConflictStrategy.ASK): Result<File>
    suspend fun getFileDetails(file: File): Map<String, String>
    suspend fun calculateMd5(file: File): String?
    suspend fun compressToZip(files: List<File>, outputPath: String): Result<File>
    suspend fun extractZip(zipFile: File, outputDir: String): Result<Unit>
    fun getStorageVolumes(): List<Pair<String, String>>
    fun getStorageInfo(path: String): Pair<Long, Long>
    fun formatSize(bytes: Long): String
    fun formatDate(timestamp: Long): String
    fun getFileCount(directory: File): Int
    fun getTotalSize(directory: File): Long
    suspend fun findLargeFiles(root: String, minSize: Long = 50L * 1024 * 1024): List<File>
    suspend fun findDuplicateFiles(root: String): List<List<File>>
    suspend fun findEmptyDirectories(root: String): List<File>
    suspend fun findJunkFiles(root: String): List<File>
    suspend fun analyzeStorage(root: String): StorageAnalysis
    fun getTrashDir(): File
    val operations: Flow<List<FileOperation>>
    suspend fun enqueueOperation(op: FileOperation)
    suspend fun updateOperation(op: FileOperation)
    suspend fun cancelOperation(id: String)
    fun resolveConflict(sourceName: String, destDir: String, strategy: ConflictStrategy): File
}

data class StorageAnalysis(
    val videoSize: Long = 0,
    val audioSize: Long = 0,
    val imageSize: Long = 0,
    val documentSize: Long = 0,
    val archiveSize: Long = 0,
    val apkSize: Long = 0,
    val otherSize: Long = 0,
    val totalFiles: Int = 0,
    val totalFolders: Int = 0
)
