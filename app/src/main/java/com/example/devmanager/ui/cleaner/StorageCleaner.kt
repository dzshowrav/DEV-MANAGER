package com.example.devmanager.ui.cleaner

import android.content.Context
import android.os.Environment
import com.example.devmanager.data.repository.FileRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CleanerResult(
    val cacheSize: Long = 0,
    val cacheFiles: Int = 0,
    val junkSize: Long = 0,
    val junkFiles: Int = 0,
    val largeFiles: List<File> = emptyList(),
    val emptyDirs: List<File> = emptyList(),
    val apkFiles: List<File> = emptyList()
)

class StorageCleaner(private val context: Context) {

    private val repo = FileRepositoryImpl()

    suspend fun scan(): CleanerResult = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        var cacheSize = 0L; var cacheFiles = 0
        var junkSize = 0L; var junkFiles = 0
        val largeFiles = mutableListOf<File>()
        val emptyDirs = mutableListOf<File>()
        val apkFiles = mutableListOf<File>()

        val queue = ArrayDeque<File>(); queue.add(root)
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        if (file.name == "cache" || file.name == "code_cache") {
                            val size = repo.getTotalSize(file)
                            cacheSize += size; cacheFiles++
                        } else if (!file.name.startsWith(".")) queue.add(file)
                    } else {
                        val ext = file.extension.lowercase()
                        val name = file.name.lowercase()
                        if (ext in com.example.devmanager.util.FileUtils.junkExtensions || name in com.example.devmanager.util.FileUtils.junkNames) {
                            junkSize += file.length(); junkFiles++
                        }
                        if (file.length() > 100L * 1024 * 1024) largeFiles.add(file)
                        if (ext == "apk") apkFiles.add(file)
                    }
                }
            } catch (_: Exception) {}
        }
        val emptyCheck = repo.findEmptyDirectories(root.absolutePath)
        CleanerResult(cacheSize, cacheFiles, junkSize, junkFiles, largeFiles, emptyCheck, apkFiles)
    }

    suspend fun cleanCache(): Int = withContext(Dispatchers.IO) {
        var count = 0
        val queue = ArrayDeque<File>(); queue.add(Environment.getExternalStorageDirectory())
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory && (file.name == "cache" || file.name == "code_cache")) {
                        com.example.devmanager.util.FileUtils.deleteRecursively(file)
                        count++
                    } else if (file.isDirectory) queue.add(file)
                }
            } catch (_: Exception) {}
        }
        count
    }

    suspend fun cleanJunk(): Int = withContext(Dispatchers.IO) {
        var count = 0
        val root = Environment.getExternalStorageDirectory()
        val queue = ArrayDeque<File>(); queue.add(root)
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory && !file.name.startsWith(".")) queue.add(file)
                    else {
                        val ext = file.extension.lowercase()
                        val name = file.name.lowercase()
                        if (ext in com.example.devmanager.util.FileUtils.junkExtensions || name in com.example.devmanager.util.FileUtils.junkNames) {
                            file.delete(); count++
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        count
    }
}
