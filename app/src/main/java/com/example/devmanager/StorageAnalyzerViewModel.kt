package com.example.devmanager

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.os.Environment

data class AnalyzerResult(
    val totalSize: Long = 0,
    val audioSize: Long = 0,
    val videoSize: Long = 0,
    val imageSize: Long = 0,
    val documentSize: Long = 0,
    val archiveSize: Long = 0,
    val apkSize: Long = 0,
    val otherSize: Long = 0,
    val largeFiles: List<File> = emptyList(),
    val emptyFolders: List<File> = emptyList(),
    val duplicates: List<List<File>> = emptyList(),
    val junkFiles: List<File> = emptyList(), // temp, log, cache
    val rootNode: DirNode? = null
)

class StorageAnalyzerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing = _isAnalyzing.asStateFlow()
    
    private val _result = MutableStateFlow(AnalyzerResult())
    val result = _result.asStateFlow()
    
    private val _scanProgress = MutableStateFlow("")
    val scanProgress = _scanProgress.asStateFlow()

    fun analyzeStorage(root: String) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _result.value = AnalyzerResult()
            withContext(Dispatchers.IO) {
                var total = 0L
                var count = 0
                var audio = 0L
                var video = 0L
                var image = 0L
                var doc = 0L
                var archive = 0L
                var apk = 0L
                var other = 0L
                
                val largeFiles = mutableListOf<File>()
                val emptyFolders = mutableListOf<File>()
                val junkFiles = mutableListOf<File>()
                
                // For duplicates (we approximate by size and name to save time)
                val sizeMap = mutableMapOf<Long, MutableList<File>>()

                val stack = mutableListOf<File>(File(root))
                while (stack.isNotEmpty()) {
                    val curr = stack.removeAt(stack.size - 1)
                    val files = curr.listFiles()
                    if (files == null) continue
                    if (files.isEmpty()) {
                        emptyFolders.add(curr)
                        continue
                    }
                    for (file in files) {
                        try {
                            if (file.isDirectory) {
                                if (!file.name.startsWith(".")) { // ignore hidden folders like .android
                                    stack.add(file)
                                }
                            } else {
                                val len = file.length()
                                total += len
                                count++
                                val ext = file.extension.lowercase()
                                
                                when (ext) {
                                    "mp3", "wav", "m4a", "flac", "ogg", "aac" -> audio += len
                                    "mp4", "mkv", "avi", "mov", "webm", "flv" -> video += len
                                    "jpg", "jpeg", "png", "gif", "bmp", "webp" -> image += len
                                    "pdf", "doc", "docx", "txt", "rtf", "md", "csv" -> doc += len
                                    "zip", "rar", "7z", "tar", "gz" -> archive += len
                                    "apk" -> apk += len
                                    else -> other += len
                                }
                                
                                if (len > 50 * 1024 * 1024) { // > 50MB
                                    largeFiles.add(file)
                                }
                                
                                if (ext == "log" || ext == "tmp" || file.name == "Thumbs.db" || file.name == ".nomedia" || ext == "bak") {
                                    junkFiles.add(file)
                                }
                                
                                if (len > 0) {
                                    sizeMap.getOrPut(len) { mutableListOf() }.add(file)
                                }
                                
                                if (count % 100 == 0) { // update progress periodically
                                    _scanProgress.value = file.absolutePath
                                }
                            }
                        } catch (e: Exception) {
                            // skip
                        }
                    }
                }
                
                largeFiles.sortByDescending { it.length() }
                
                val duplicates = sizeMap.values.filter { it.size > 1 }.map { group ->
                    // further verify by name if needed, but for now exact same size is a strong hint,
                    // let's exact group by name + size
                    group.groupBy { it.name }.values.filter { it.size > 1 }
                }.flatten()

                val rootDir = File(root)
                val rootNode = buildDirTree(rootDir, 0, 3) // depth=3 to keep it performant for the sunburst chart

                _result.value = AnalyzerResult(
                    totalSize = total,
                    audioSize = audio,
                    videoSize = video,
                    imageSize = image,
                    documentSize = doc,
                    archiveSize = archive,
                    apkSize = apk,
                    otherSize = other,
                    largeFiles = largeFiles.take(50), // top 50
                    emptyFolders = emptyFolders.take(50), // top 50
                    duplicates = duplicates.take(20), // top 20 groups
                    junkFiles = junkFiles,
                    rootNode = rootNode
                )
            }
            _isAnalyzing.value = false
        }
    }
    
    fun cleanFiles(files: List<File>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                files.forEach {
                    if (it.isDirectory) it.deleteRecursively() else it.delete()
                }
            }
            // Re-analyze or just manual update?
            // To keep it simple, we could clear the result list locally or let user re-scan
        }
    }

    private fun buildDirTree(dir: File, currentDepth: Int, maxDepth: Int): DirNode {
        var totalSize = 0L
        val children = mutableListOf<DirNode>()
        if (currentDepth <= maxDepth) {
            val files = dir.listFiles()
            if (files != null) {
                for (f in files) {
                    if (f.isDirectory) {
                        try {
                            if (!f.name.startsWith(".")) {
                                val childNode = buildDirTree(f, currentDepth + 1, maxDepth)
                                if (childNode.size > 0) {
                                    children.add(childNode)
                                    totalSize += childNode.size
                                }
                            }
                        } catch (e: Exception) {}
                    } else {
                        val size = f.length()
                        totalSize += size
                    }
                }
            }
        } else {
            // we stop diving, just recursively calculate rest size
            totalSize = calculateDirSizeFast(dir)
        }
        
        children.sortByDescending { it.size }
        return DirNode(dir.name, totalSize, children)
    }

    private fun calculateDirSizeFast(dir: File): Long {
        var size = 0L
        val stack = mutableListOf(dir)
        while (stack.isNotEmpty()) {
            val curr = stack.removeAt(stack.size - 1)
            val files = curr.listFiles()
            if (files != null) {
                for (f in files) {
                    if (f.isDirectory) stack.add(f) else size += f.length()
                }
            }
        }
        return size
    }

    fun cleanCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
               try {
                   val cacheDir = getApplication<Application>().cacheDir
                   cacheDir.deleteRecursively()
                   val extCacheDir = getApplication<Application>().externalCacheDir
                   extCacheDir?.deleteRecursively()
               } catch (e: Exception) {}
            }
        }
    }
}
