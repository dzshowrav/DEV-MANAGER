package com.example.devmanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Folder
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun FileIconBox(
    extension: String,
    isDirectory: Boolean,
    modifier: Modifier = Modifier
) {
    val icon = getFileIcon(extension, isDirectory)
    val tint = when {
        isDirectory -> MaterialTheme.colorScheme.primary
        extension in imageExt -> MaterialTheme.colorScheme.tertiary
        extension in videoExt -> MaterialTheme.colorScheme.error
        extension in audioExt -> MaterialTheme.colorScheme.secondary
        extension == "pdf" -> MaterialTheme.colorScheme.error
        extension in codeExt -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

private val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")
private val videoExt = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm")
private val audioExt = setOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a")
private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz")
private val codeExt = setOf("kt", "java", "py", "js", "ts", "html", "css", "xml", "json", "sql", "sh")

private fun getFileIcon(ext: String, isDir: Boolean): ImageVector {
    if (isDir) return Icons.AutoMirrored.Filled.Folder
    return when (ext) {
        in imageExt -> Icons.Default.Image
        in videoExt -> Icons.Default.VideoFile
        in audioExt -> Icons.Default.AudioFile
        "pdf" -> Icons.Default.PictureAsPdf
        "doc", "docx" -> Icons.Default.Description
        "xls", "xlsx", "csv" -> Icons.Default.Description
        "ppt", "pptx" -> Icons.Default.Description
        "apk" -> Icons.Default.Description
        in archiveExt -> Icons.Default.Archive
        "txt", "md" -> Icons.AutoMirrored.Filled.Article
        in codeExt -> Icons.Default.Code
        else -> Icons.Default.InsertDriveFile
    }
}
