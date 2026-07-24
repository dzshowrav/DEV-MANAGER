package com.example.devmanager.data.repository

import android.os.Environment
import android.os.StatFs
import com.example.devmanager.data.model.ConflictStrategy
import com.example.devmanager.data.model.FileItem
import com.example.devmanager.data.model.FileOperation
import com.example.devmanager.data.model.OperationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepositoryImpl @Inject constructor() : FileRepository {

    private val _operations = MutableStateFlow<List<FileOperation>>(emptyList())
    override val operations: Flow<List<FileOperation>> = _operations.asStateFlow()

    override fun getFiles(path: String, showHidden: Boolean): Flow<List<FileItem>> {
        val dir = File(path)
        val items = if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.filter { showHidden || !it.name.startsWith(".") }?.map { toFileItem(it) }
                ?: emptyList()
        } else emptyList()
        return MutableStateFlow(items).asStateFlow()
    }

    override suspend fun listFiles(path: String, showHidden: Boolean): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.filter { showHidden || !it.name.startsWith(".") }?.map { toFileItem(it) }
                ?: emptyList()
        } else emptyList()
    }

    override suspend fun createDirectory(parent: String, name: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dir = File(parent, name)
            if (dir.mkdirs()) Result.success(dir) else Result.failure(Exception("Failed to create directory"))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun createFile(parent: String, name: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val file = File(parent, name)
            if (file.createNewFile()) Result.success(file) else Result.failure(Exception("File already exists"))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun rename(file: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val dest = File(file.parent, newName)
            if (file.renameTo(dest)) Result.success(dest) else Result.failure(Exception("Rename failed"))
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun delete(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            deleteRecursively(file)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun copy(source: File, destDir: String, strategy: ConflictStrategy): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val resolved = resolveConflict(source.name, destDir, strategy)
                copyRecursively(source, resolved)
                Result.success(resolved)
            } catch (e: Exception) { Result.failure(e) }
        }

    override suspend fun move(source: File, destDir: String, strategy: ConflictStrategy): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val resolved = resolveConflict(source.name, destDir, strategy)
                if (source.renameTo(resolved)) Result.success(resolved)
                else {
                    copyRecursively(source, resolved)
                    deleteRecursively(source)
                    Result.success(resolved)
                }
            } catch (e: Exception) { Result.failure(e) }
        }

    override suspend fun getFileDetails(file: File): Map<String, String> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, String>()
        map["name"] = file.name
        map["path"] = file.absolutePath
        map["size"] = formatSize(file.length())
        map["modified"] = formatDate(file.lastModified())
        map["isDirectory"] = if (file.isDirectory) "Yes" else "No"
        if (!file.isDirectory) {
            map["extension"] = file.extension.ifEmpty { "(none)" }
            if (file.length() < 100L * 1024 * 1024) {
                calculateMd5(file)?.let { map["md5"] = it }
            }
        }
        map
    }

    override suspend fun calculateMd5(file: File): String? = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) { null }
    }

    override suspend fun compressToZip(files: List<File>, outputPath: String): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val zipFile = File(outputPath)
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    for (file in files) {
                        addToZipEntry(file, file.name, zos)
                    }
                }
                Result.success(zipFile)
            } catch (e: Exception) { Result.failure(e) }
        }

    override suspend fun extractZip(zipFile: File, outputDir: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                ZipInputStream(FileInputStream(zipFile)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(outputDir, entry.name)
                        if (entry.isDirectory) outFile.mkdirs()
                        else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                        }
                        entry = zis.nextEntry
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) { Result.failure(e) }
        }

    override fun getStorageVolumes(): List<Pair<String, String>> {
        val volumes = mutableListOf<Pair<String, String>>()
        val primary = Environment.getExternalStorageDirectory()
        volumes.add(primary.absolutePath to (primary.name.ifEmpty { "Internal" }))
        try {
            val path = "/storage"
            val dir = File(path)
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.absolutePath != primary.absolutePath) {
                        volumes.add(file.absolutePath to (file.name.ifEmpty { "External" }))
                    }
                }
            }
        } catch (_: Exception) {}
        return volumes
    }

    override fun getStorageInfo(path: String): Pair<Long, Long> {
        return try {
            val stat = StatFs(path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            total to free
        } catch (_: Exception) { 1L to 1L }
    }

    override fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    override fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    override fun getFileCount(directory: File): Int {
        return directory.listFiles()?.size ?: 0
    }

    override fun getTotalSize(directory: File): Long {
        return if (directory.isDirectory) {
            directory.listFiles()?.sumOf {
                if (it.isDirectory) getTotalSize(it) else it.length()
            } ?: 0L
        } else directory.length()
    }

    override suspend fun findLargeFiles(root: String, minSize: Long): List<File> = withContext(Dispatchers.IO) {
        val result = mutableListOf<File>()
        val queue = ArrayDeque<File>()
        queue.add(File(root))
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) queue.add(file)
                    else if (file.length() >= minSize) result.add(file)
                }
            } catch (_: Exception) {}
        }
        result.sortedByDescending { it.length() }
    }

    override suspend fun findDuplicateFiles(root: String): List<List<File>> = withContext(Dispatchers.IO) {
        val sizeMap = mutableMapOf<Long, MutableList<File>>()
        val queue = ArrayDeque<File>()
        queue.add(File(root))
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) queue.add(file)
                    else sizeMap.getOrPut(file.length()) { mutableListOf() }.add(file)
                }
            } catch (_: Exception) {}
        }
        sizeMap.filter { it.value.size > 1 }.map { it.value }
    }

    override suspend fun findEmptyDirectories(root: String): List<File> = withContext(Dispatchers.IO) {
        val result = mutableListOf<File>()
        val queue = ArrayDeque<File>()
        queue.add(File(root))
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            try {
                val children = dir.listFiles()
                if (children.isNullOrEmpty()) result.add(dir)
                else children.filter { it.isDirectory }.forEach { queue.add(it) }
            } catch (_: Exception) {}
        }
        result
    }

    override suspend fun findJunkFiles(root: String): List<File> = withContext(Dispatchers.IO) {
        val junkExtensions = setOf("log", "tmp", "bak", "cache")
        val junkNames = setOf("thumbs.db", ".ds_store", ".nomedia")
        val result = mutableListOf<File>()
        val queue = ArrayDeque<File>()
        queue.add(File(root))
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) queue.add(file)
                    else if (file.extension.lowercase() in junkExtensions || file.name.lowercase() in junkNames) {
                        result.add(file)
                    }
                }
            } catch (_: Exception) {}
        }
        result
    }

    override suspend fun analyzeStorage(root: String): StorageAnalysis = withContext(Dispatchers.IO) {
        var videoSize = 0L; var audioSize = 0L; var imageSize = 0L
        var documentSize = 0L; var archiveSize = 0L; var apkSize = 0L; var otherSize = 0L
        var totalFiles = 0; var totalFolders = 0
        val videoExt = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm")
        val audioExt = setOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a")
        val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")
        val docExt = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv")
        val archiveExt = setOf("zip", "rar", "7z", "tar", "gz")
        val queue = ArrayDeque<File>(); queue.add(File(root))
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            try {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) { totalFolders++; queue.add(file) }
                    else {
                        totalFiles++
                        val size = file.length()
                        val ext = file.extension.lowercase()
                        when {
                            ext in videoExt -> videoSize += size
                            ext in audioExt -> audioSize += size
                            ext in imageExt -> imageSize += size
                            ext in docExt -> documentSize += size
                            ext in archiveExt -> archiveSize += size
                            ext == "apk" -> apkSize += size
                            else -> otherSize += size
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        StorageAnalysis(videoSize, audioSize, imageSize, documentSize, archiveSize, apkSize, otherSize, totalFiles, totalFolders)
    }

    override fun getTrashDir(): File {
        val trash = File(Environment.getExternalStorageDirectory(), ".dzdev_trash")
        if (!trash.exists()) trash.mkdirs()
        return trash
    }

    override suspend fun enqueueOperation(op: FileOperation) {
        _operations.value = _operations.value + op
    }

    override suspend fun updateOperation(op: FileOperation) {
        _operations.value = _operations.value.map { if (it.id == op.id) op else it }
    }

    override suspend fun cancelOperation(id: String) {
        _operations.value = _operations.value.map {
            if (it.id == id) it.copy(status = OperationStatus.CANCELLED) else it
        }
    }

    override fun resolveConflict(sourceName: String, destDir: String, strategy: ConflictStrategy): File {
        val destFile = File(destDir, sourceName)
        if (!destFile.exists()) return destFile
        return when (strategy) {
            ConflictStrategy.REPLACE -> { destFile.delete(); destFile }
            ConflictStrategy.RENAME -> {
                var counter = 1
                var newFile: File
                do {
                    val parts = sourceName.split(".")
                    val base = if (parts.size > 1) parts.dropLast(1).joinToString(".") else sourceName
                    val ext = if (parts.size > 1) ".${parts.last()}" else ""
                    newFile = File(destDir, "${base}_($counter)$ext")
                    counter++
                } while (newFile.exists())
                newFile
            }
            ConflictStrategy.KEEP_BOTH -> {
                val baseName = sourceName.substringBeforeLast(".")
                val ext = sourceName.substringAfterLast(".", "")
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val newName = if (ext.isNotEmpty()) "${baseName}_$stamp.$ext" else "${baseName}_$stamp"
                File(destDir, newName)
            }
            else -> destFile
        }
    }

    private suspend fun copyRecursively(source: File, dest: File) {
        if (source.isDirectory) {
            dest.mkdirs()
            source.listFiles()?.forEach { child ->
                copyRecursively(child, File(dest, child.name))
            }
        } else {
            source.copyTo(dest, overwrite = true)
        }
    }

    private suspend fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    private fun addToZipEntry(file: File, entryName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            if (!entryName.endsWith("/")) zos.putNextEntry(ZipEntry("$entryName/"))
            else zos.putNextEntry(ZipEntry(entryName))
            zos.closeEntry()
            file.listFiles()?.forEach { child ->
                addToZipEntry(child, "$entryName/${child.name}", zos)
            }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    private fun toFileItem(file: File): FileItem {
        return FileItem(
            file = file,
            name = file.name,
            isDirectory = file.isDirectory,
            sizeLabel = if (file.isDirectory) "" else formatSize(file.length()),
            size = file.length(),
            lastModified = file.lastModified(),
            lastModifiedLabel = formatDate(file.lastModified()),
            extension = if (file.isDirectory) "" else file.extension.lowercase(),
            resolution = null
        )
    }
}
