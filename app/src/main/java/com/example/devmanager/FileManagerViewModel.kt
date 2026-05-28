package com.example.devmanager

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever

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

fun getMediaResolution(file: File, extension: String): String? {
    try {
        if (extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")) {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                return "${options.outWidth} x ${options.outHeight}"
            }
        }
    } catch (e: Exception) {
        // Ignore
    }
    return null
}

enum class SortType { NAME, SIZE, DATE, TYPE }
enum class ViewMode { DETAILED, COMPACT, GRID }

data class StorageInfo(val totalSpace: Long, val freeSpace: Long)

data class StorageVolumeInfo(
    val name: String,
    val path: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val totalSpace: Long = 0L,
    val freeSpace: Long = 0L
)

enum class MediaCategory { NONE, IMAGES, VIDEOS, MUSIC }

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    val storageRoot = Environment.getExternalStorageDirectory().absolutePath

    private val sharedPrefs = application.getSharedPreferences("fm_prefs", Context.MODE_PRIVATE)

    private val _currentPath = MutableStateFlow(storageRoot)
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _currentCategory = MutableStateFlow(MediaCategory.NONE)
    val currentCategory: StateFlow<MediaCategory> = _currentCategory.asStateFlow()

    private val _allFiles = MutableStateFlow<List<FileItem>>(emptyList())
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted = _permissionGranted.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortType = MutableStateFlow(
        sharedPrefs.getString("sort_type", null)?.let {
            runCatching { SortType.valueOf(it) }.getOrNull()
        } ?: SortType.NAME
    )
    val sortType = _sortType.asStateFlow()

    private val _sortDescending = MutableStateFlow(sharedPrefs.getBoolean("sort_descending", false))
    val sortDescending = _sortDescending.asStateFlow()

    private val _showHiddenFiles = MutableStateFlow(sharedPrefs.getBoolean("show_hidden", false))
    val showHiddenFiles = _showHiddenFiles.asStateFlow()

    private val _viewMode = MutableStateFlow(
        sharedPrefs.getString("view_mode", null)?.let {
            runCatching { ViewMode.valueOf(it) }.getOrNull()
        } ?: ViewMode.DETAILED
    )
    val viewMode = _viewMode.asStateFlow()

    private val _storageInfo = MutableStateFlow(StorageInfo(1L, 1L))
    val storageInfo = _storageInfo.asStateFlow()

    private val _storageVolumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val storageVolumes = _storageVolumes.asStateFlow()

    // Multi-select features
    private val _selectedFiles = MutableStateFlow<Set<File>>(emptySet())
    val selectedFiles = _selectedFiles.asStateFlow()

    // Batch Copy/Cut
    private val _clipboardFiles = MutableStateFlow<List<File>>(emptyList())
    val clipboardFiles = _clipboardFiles.asStateFlow()
    private val _isCut = MutableStateFlow(false)
    val isCut = _isCut.asStateFlow()

    // Bookmarks
    private val _bookmarks = MutableStateFlow<Set<String>>(
        sharedPrefs.getStringSet("bookmarks", emptySet()) ?: emptySet()
    )
    val bookmarks = _bookmarks.asStateFlow()

    // Text Editor Mode
    private val _textEditorFile = MutableStateFlow<File?>(null)
    val textEditorFile = _textEditorFile.asStateFlow()
    private val _textEditorContent = MutableStateFlow("")
    val textEditorContent = _textEditorContent.asStateFlow()

    // Built-in Internal Image Viewer Mode
    private val _imageViewerFile = MutableStateFlow<File?>(null)
    val imageViewerFile = _imageViewerFile.asStateFlow()

    fun openImageViewer(file: File) {
        _imageViewerFile.value = file
    }
    
    fun closeImageViewer() {
        _imageViewerFile.value = null
    }

    // Media Player Mode
    private val _mediaPlayerFile = MutableStateFlow<File?>(null)
    val mediaPlayerFile = _mediaPlayerFile.asStateFlow()

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    fun setPermissionGranted(granted: Boolean) {
        _permissionGranted.value = granted
        if (granted) {
            updateStorageVolumes()
            updateStorageInfo()
            loadFiles(_currentPath.value)
        }
    }

    private fun updateStorageVolumes() {
        try {
            val volumes = mutableListOf<StorageVolumeInfo>()
            val storageManager = getApplication<Application>().getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
            val context = getApplication<Application>()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                for (volume in storageManager.storageVolumes) {
                    volume.directory?.let { dir ->
                        volumes.add(
                            StorageVolumeInfo(
                                name = volume.getDescription(context) ?: "Storage",
                                path = dir.absolutePath,
                                isPrimary = volume.isPrimary,
                                isRemovable = volume.isRemovable
                            )
                        )
                    }
                }
            } else {
                val dirs = context.getExternalFilesDirs(null)
                dirs.forEach { file ->
                    if (file != null) {
                        val path = file.absolutePath
                        val rootPath = path.substringBefore("/Android/data/")
                        val isPrimary = rootPath == storageRoot
                        val name = if (isPrimary) "Internal Storage" else "SD Card / USB"
                        volumes.add(StorageVolumeInfo(name, rootPath, isPrimary, !isPrimary))
                    }
                }
            }
            val finalVolumes = volumes.distinctBy { it.path }.map {
                var total = 0L
                var free = 0L
                try {
                    val stat = android.os.StatFs(it.path)
                    total = stat.totalBytes
                    free = stat.availableBytes
                } catch (e: Exception) {
                    // Ignore
                }
                it.copy(totalSpace = total, freeSpace = free)
            }
            _storageVolumes.value = finalVolumes
        } catch (e: Exception) {
            e.printStackTrace()
            _storageVolumes.value = listOf(StorageVolumeInfo("Internal Storage", storageRoot, true, false))
        }
    }

    private fun updateStorageInfo() {
        try {
            val file = File(_currentPath.value)
            var pathForStat = _currentPath.value
            // StatFs sometimes fails on regular files, need to use a directory
            if (!file.exists() && file.parentFile != null) {
                pathForStat = file.parentFile!!.absolutePath
            } else if (file.isFile && file.parentFile != null) {
                pathForStat = file.parentFile!!.absolutePath
            }
            val stat = StatFs(pathForStat)
            val total = stat.totalBytes
            val free = stat.availableBytes
            _storageInfo.value = StorageInfo(total, free)
        } catch (e: Exception) {
            // Fallback or ignore
        }
    }

    fun navigateTo(path: String) {
        val file = File(path)
        if (file.exists() && file.isDirectory) {
            _currentCategory.value = MediaCategory.NONE
            _currentPath.value = file.absolutePath
            _searchQuery.value = ""
            _selectedFiles.value = emptySet()
            updateStorageInfo()
            loadFiles(file.absolutePath)
        }
    }

    fun navigateUp() {
        val currentFile = File(_currentPath.value)
        val parent = currentFile.parentFile
        val isRoot = _storageVolumes.value.any { it.path.equals(currentFile.absolutePath, ignoreCase = true) } ||
                     currentFile.absolutePath.equals(storageRoot, ignoreCase = true) ||
                     currentFile.absolutePath.equals("/storage/emulated/0", ignoreCase = true)

        if (parent != null && !isRoot) {
            navigateTo(parent.absolutePath)
        }
    }

    val trashDirPath: String get() = trashDir.absolutePath

    private fun getTrashOriginalName(trashName: String): String {
        return trashName.substringAfter("_", trashName)
    }

    fun loadFiles(path: String) {
        if (_currentCategory.value != MediaCategory.NONE) {
            selectCategory(_currentCategory.value)
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            val isTrash = isTrashPath(path)
            val newFiles = withContext(Dispatchers.IO) {
                try {
                    val dir = File(path)
                    if (dir.exists() && dir.isDirectory) {
                        val listFiles = try {
                            dir.listFiles() ?: emptyArray()
                        } catch (e: Exception) {
                            emptyArray()
                        }
                        listFiles.mapNotNull { file ->
                            try {
                                val sizeLabel = if (file.isDirectory) {
                                    val count = try {
                                        file.list()?.size ?: 0
                                    } catch (e: Exception) {
                                        0
                                    }
                                    "$count items"
                                } else {
                                    formatSize(file.length())
                                }
                                FileItem(
                                    file = file,
                                    name = if (isTrash) getTrashOriginalName(file.name) else file.name,
                                    isDirectory = file.isDirectory,
                                    sizeLabel = sizeLabel,
                                    size = file.length(),
                                    lastModified = file.lastModified(),
                                    lastModifiedLabel = try { dateFormat.format(Date(file.lastModified())) } catch (e: Exception) { "" },
                                    extension = file.extension.lowercase(),
                                    resolution = getMediaResolution(file, file.extension.lowercase())
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                    } else {
                        emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            _allFiles.value = newFiles
            applyFilters()
            _isLoading.value = false
        }
    }

    private fun applyFilters() {
        var filteredList = _allFiles.value

        if (!_showHiddenFiles.value) {
            filteredList = filteredList.filter { !it.name.startsWith(".") }
        }

        val query = _searchQuery.value.trim().lowercase()
        if (query.isNotEmpty()) {
            filteredList = filteredList.filter { it.name.lowercase().contains(query) }
        }

        var sortedList = when (_sortType.value) {
            SortType.NAME -> filteredList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            SortType.SIZE -> filteredList.sortedWith(compareBy({ !it.isDirectory }, { it.size }))
            SortType.DATE -> filteredList.sortedWith(compareBy({ !it.isDirectory }, { it.lastModified }))
            SortType.TYPE -> filteredList.sortedWith(compareBy({ !it.isDirectory }, { it.extension }))
        }
        
        if (_sortDescending.value) {
            val (dirs, files) = sortedList.partition { it.isDirectory }
            sortedList = dirs.reversed() + files.reversed()
        }

        _files.value = sortedList
    }

    // Settings
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun setSortType(type: SortType) {
        _sortType.value = type
        sharedPrefs.edit().putString("sort_type", type.name).apply()
        applyFilters()
    }

    fun toggleSortDescending() {
        _sortDescending.value = !_sortDescending.value
        sharedPrefs.edit().putBoolean("sort_descending", _sortDescending.value).apply()
        applyFilters()
    }

    fun setShowHiddenFiles(show: Boolean) {
        _showHiddenFiles.value = show
        sharedPrefs.edit().putBoolean("show_hidden", show).apply()
        applyFilters()
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        sharedPrefs.edit().putString("view_mode", mode.name).apply()
    }

    // Multi-Selection
    fun toggleSelection(file: File) {
        val current = _selectedFiles.value.toMutableSet()
        if (current.contains(file)) {
            current.remove(file)
        } else {
            current.add(file)
        }
        _selectedFiles.value = current
    }

    fun selectAll() {
        _selectedFiles.value = _files.value.map { it.file }.toSet()
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    fun copySelected(cut: Boolean) {
        _clipboardFiles.value = _selectedFiles.value.toList()
        _isCut.value = cut
        clearSelection()
    }
    
    fun clearClipboard() {
        _clipboardFiles.value = emptyList()
    }

    fun pasteFiles() {
        if (_clipboardFiles.value.isEmpty()) return
        val currentDestPath = _currentPath.value

        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                for (src in _clipboardFiles.value) {
                    if (!src.exists()) continue
                    val dest = File(currentDestPath, src.name)
                    if (dest.absolutePath == src.absolutePath) continue

                    if (_isCut.value) {
                        if (src.isDirectory) src.copyRecursively(dest, true) else src.copyTo(dest, true)
                        if (src.isDirectory) src.deleteRecursively() else src.delete()
                    } else {
                        if (src.isDirectory) src.copyRecursively(dest, true) else src.copyTo(dest, true)
                    }
                }
            }
            if (_isCut.value) _clipboardFiles.value = emptyList()
            loadFiles(currentDestPath)
            updateStorageInfo()
        }
    }

    private val trashDir by lazy { 
        val dir = File(storageRoot, ".dzdev_trash")
        if (!dir.exists()) dir.mkdirs()
        dir
    }
    
    // Original paths for trash
    private val trashPaths: MutableMap<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        val json = sharedPrefs.getString("trash_paths", "{}")
        try {
            val arr = org.json.JSONObject(json)
            arr.keys().forEach {
                map[it] = arr.getString(it)
            }
        } catch(e: Exception) {}
        map
    }

    private fun saveTrashPaths() {
        val json = org.json.JSONObject(trashPaths as Map<*, *>).toString()
        sharedPrefs.edit().putString("trash_paths", json).apply()
    }

    fun isTrashPath(path: String) = path == trashDir.absolutePath

    fun moveToTrash(targets: Set<File> = _selectedFiles.value) {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                targets.forEach { file ->
                    val trashName = "${System.currentTimeMillis()}_${file.name}"
                    val dest = File(trashDir, trashName)
                    try {
                        if (file.renameTo(dest)) {
                            trashPaths[trashName] = file.absolutePath
                        }
                    } catch(e: Exception) {}
                }
                saveTrashPaths()
            }
            clearSelection()
            loadFiles(_currentPath.value)
            updateStorageInfo()
        }
    }

    fun restoreFromTrash(targets: Set<File> = _selectedFiles.value) {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                targets.forEach { file ->
                    val originalPath = trashPaths[file.name]
                    if (originalPath != null) {
                        try {
                            if (file.renameTo(File(originalPath))) {
                                trashPaths.remove(file.name)
                            }
                        } catch(e: Exception) {}
                    }
                }
                saveTrashPaths()
            }
            clearSelection()
            loadFiles(_currentPath.value)
            updateStorageInfo()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                trashDir.listFiles()?.forEach { it.deleteRecursively() }
                trashPaths.clear()
                saveTrashPaths()
            }
            loadFiles(_currentPath.value)
            updateStorageInfo()
        }
    }

    // Single/Batch Actions
    fun deleteFiles(targets: Set<File> = _selectedFiles.value) {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                targets.forEach { file ->
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            }
            clearSelection()
            loadFiles(_currentPath.value)
            updateStorageInfo()
        }
    }

    fun duplicateFile(file: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val newName = "Copy_of_${file.name}"
                    val dest = File(file.parent, newName)
                    if (file.isDirectory) {
                        file.copyRecursively(dest)
                    } else {
                        file.copyTo(dest)
                    }
                } catch (e: Exception) {}
            }
            loadFiles(_currentPath.value)
        }
    }

    fun renameFile(file: File, newName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                file.renameTo(File(file.parent, newName))
            }
            loadFiles(_currentPath.value)
        }
    }

    fun createNoMedia() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val noMedia = File(_currentPath.value, ".nomedia")
                    if (!noMedia.exists()) noMedia.createNewFile()
                } catch (e: Exception) {}
            }
            loadFiles(_currentPath.value)
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                File(_currentPath.value, name).mkdir()
            }
            loadFiles(_currentPath.value)
        }
    }

    fun createFile(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                File(_currentPath.value, name).createNewFile()
            }
            loadFiles(_currentPath.value)
        }
    }

    // Zip Compression & Extraction
    fun zipSelected(zipName: String) {
        val targets = _selectedFiles.value.toList()
        if (targets.isEmpty()) return
        val destFile = File(_currentPath.value, "$zipName.zip")
        
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                try {
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(destFile))).use { zos ->
                        targets.forEach { file ->
                            zipFileRec(file, file.name, zos)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            clearSelection()
            loadFiles(_currentPath.value)
        }
    }

    private fun zipFileRec(fileToZip: File, fileName: String, zos: ZipOutputStream) {
        if (fileToZip.isDirectory) {
            val children = fileToZip.listFiles()
            if (children.isNullOrEmpty()) {
                zos.putNextEntry(ZipEntry(if (fileName.endsWith("/")) fileName else "$fileName/"))
                zos.closeEntry()
            } else {
                for (childFile in children) {
                    zipFileRec(childFile, "$fileName/${childFile.name}", zos)
                }
            }
        } else {
            FileInputStream(fileToZip).use { fis ->
                val zipEntry = ZipEntry(fileName)
                zos.putNextEntry(zipEntry)
                val bytes = ByteArray(4096)
                var length: Int
                while (fis.read(bytes).also { length = it } >= 0) {
                    zos.write(bytes, 0, length)
                }
            }
        }
    }

    fun unzipFile(zipFile: File) {
        val targetFolder = File(zipFile.parent, zipFile.nameWithoutExtension)
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) {
                try {
                    if (!targetFolder.exists()) targetFolder.mkdir()
                    ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                        var zipEntry = zis.nextEntry
                        while (zipEntry != null) {
                            val newFile = File(targetFolder, zipEntry.name)
                            if (zipEntry.isDirectory) {
                                newFile.mkdirs()
                            } else {
                                newFile.parentFile?.mkdirs()
                                FileOutputStream(newFile).use { fos ->
                                    val buffer = ByteArray(4096)
                                    var len: Int
                                    while (zis.read(buffer).also { len = it } > 0) {
                                        fos.write(buffer, 0, len)
                                    }
                                }
                            }
                            zipEntry = zis.nextEntry
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            loadFiles(_currentPath.value)
        }
    }

    // Hashes & Utils
    suspend fun calculateFullSize(file: File): Long = withContext(Dispatchers.IO) {
        if (file.isFile) return@withContext file.length()
        var size = 0L
        file.walkTopDown().forEach { if (it.isFile) size += it.length() }
        size
    }

    suspend fun calculateHash(file: File, algorithm: String = "MD5"): String = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance(algorithm)
            FileInputStream(file).use { fis ->
                val byteArray = ByteArray(4096)
                var bytesCount: Int
                while (fis.read(byteArray).also { bytesCount = it } != -1) {
                    digest.update(byteArray, 0, bytesCount)
                }
            }
            val bytes = digest.digest()
            val sb = StringBuilder()
            for (i in bytes.indices) {
                sb.append(((bytes[i].toInt() and 0xff) + 0x100).toString(16).substring(1))
            }
            sb.toString()
        } catch (e: Exception) {
            "Error"
        }
    }

    // Bookmarks
    fun isBookmarked(path: String) = _bookmarks.value.contains(path)
    
    fun toggleBookmark(path: String) {
        val current = _bookmarks.value.toMutableSet()
        if (current.contains(path)) current.remove(path) else current.add(path)
        _bookmarks.value = current
        sharedPrefs.edit().putStringSet("bookmarks", current).apply()
    }

    // Text Editor
    fun openMediaPlayer(file: File) {
        _mediaPlayerFile.value = file
    }

    fun closeMediaPlayer() {
        _mediaPlayerFile.value = null
    }

    fun openTextEditor(file: File) {
        viewModelScope.launch {
            _isLoading.value = true
            val text = withContext(Dispatchers.IO) {
                try { file.readText() } catch (e: Exception) { "" }
            }
            _textEditorContent.value = text
            _textEditorFile.value = file
            _isLoading.value = false
        }
    }
    
    fun updateTextEditorContent(content: String) {
        _textEditorContent.value = content
    }

    fun saveTextFile() {
        val df = _textEditorFile.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    df.writeText(_textEditorContent.value)
                } catch (e: Exception) {}
            }
            _textEditorFile.value = null
        }
    }

    fun closeTextEditor() {
        _textEditorFile.value = null
        _textEditorContent.value = ""
    }

    fun selectCategory(category: MediaCategory) {
        _currentCategory.value = category
        if (category == MediaCategory.NONE) {
            navigateTo(storageRoot)
        } else {
            viewModelScope.launch {
                _isLoading.value = true
                _searchQuery.value = ""
                _selectedFiles.value = emptySet()
                
                val folders = scanMediaFolders(category)
                _allFiles.value = folders
                applyFilters()
                _isLoading.value = false
            }
        }
    }

    suspend fun scanMediaFolders(category: MediaCategory): List<FileItem> = withContext(Dispatchers.IO) {
        if (category == MediaCategory.NONE) return@withContext emptyList()
        
        val matchedFolders = mutableSetOf<File>()
        val extensions = when (category) {
            MediaCategory.IMAGES -> listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")
            MediaCategory.VIDEOS -> listOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v")
            MediaCategory.MUSIC -> listOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "wma", "amr", "ape", "mid")
            else -> return@withContext emptyList()
        }

        val volumes = _storageVolumes.value.ifEmpty {
            listOf(StorageVolumeInfo("Internal Storage", storageRoot, true, false))
        }

        for (volume in volumes) {
            val root = File(volume.path)
            if (!root.exists() || !root.isDirectory) continue
            
            try {
                root.walkTopDown()
                    .onEnter { dir ->
                        val name = dir.name
                        if (name.startsWith(".")) return@onEnter false
                        if (name.equals("Android", ignoreCase = true)) return@onEnter false
                        true
                    }
                    .forEach { file ->
                        if (file.isDirectory) {
                            try {
                                val filesInDir = file.listFiles()
                                if (filesInDir != null) {
                                    val hasMedia = filesInDir.any { child ->
                                        child.isFile && child.extension.lowercase() in extensions && !child.name.startsWith(".")
                                    }
                                    if (hasMedia) {
                                        matchedFolders.add(file)
                                    }
                                }
                            } catch (e: Exception) {
                                // skip permission denied or other exceptions
                            }
                        }
                    }
            } catch (e: Exception) {
                // Ignore traverse error
            }
        }

        matchedFolders.mapNotNull { folder ->
            try {
                val filesInDir = folder.listFiles() ?: emptyArray()
                val mediaCount = filesInDir.count { child ->
                    child.isFile && child.extension.lowercase() in extensions && !child.name.startsWith(".")
                }
                if (mediaCount == 0) return@mapNotNull null
                
                val itemLabel = when (category) {
                    MediaCategory.IMAGES -> "$mediaCount image" + if (mediaCount > 1) "s" else ""
                    MediaCategory.VIDEOS -> "$mediaCount video" + if (mediaCount > 1) "s" else ""
                    MediaCategory.MUSIC -> "$mediaCount audio file" + if (mediaCount > 1) "s" else ""
                    else -> "$mediaCount items"
                }
                
                FileItem(
                    file = folder,
                    name = folder.name,
                    isDirectory = true,
                    sizeLabel = itemLabel,
                    size = 0L,
                    lastModified = folder.lastModified(),
                    lastModifiedLabel = try { dateFormat.format(Date(folder.lastModified())) } catch (e: Exception) { "" },
                    extension = "folder"
                )
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.name.lowercase() }
    }

    fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
