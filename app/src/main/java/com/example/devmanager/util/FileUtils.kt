package com.example.devmanager.util

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {
    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    fun formatDate(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

    val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")
    val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm")
    val audioExtensions = setOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a")
    val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md")
    val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz")
    val codeExtensions = setOf("kt", "java", "py", "js", "ts", "html", "css", "xml", "json", "sql", "sh")
    val junkExtensions = setOf("log", "tmp", "bak", "cache")
    val junkNames = setOf("thumbs.db", ".ds_store", ".nomedia")

    fun getExtensionCategory(ext: String): String = when {
        ext in imageExtensions -> "Image"
        ext in videoExtensions -> "Video"
        ext in audioExtensions -> "Audio"
        ext in documentExtensions -> "Document"
        ext in archiveExtensions -> "Archive"
        ext in codeExtensions -> "Code"
        ext == "apk" -> "APK"
        else -> "Other"
    }

    fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        file.delete()
    }

    fun copyRecursively(source: File, dest: File) {
        if (source.isDirectory) {
            dest.mkdirs()
            source.listFiles()?.forEach { copyRecursively(it, File(dest, it.name)) }
        } else source.copyTo(dest, overwrite = true)
    }
}
