package com.example.devmanager.ui.filemanager

import android.app.Application
import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.devmanager.data.local.entity.RecentFileEntity
import com.example.devmanager.data.model.FileItem
import com.example.devmanager.data.model.MediaCategory
import com.example.devmanager.data.model.SortType
import com.example.devmanager.data.model.StorageInfo
import com.example.devmanager.data.model.StorageVolumeInfo
import com.example.devmanager.data.model.ViewMode
import com.example.devmanager.data.repository.BookmarkRepository
import com.example.devmanager.data.repository.FileRepository
import com.example.devmanager.data.repository.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val fileRepository: FileRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val trashRepository: TrashRepository,
    private val application: Application
) : ViewModel() {

    val storageRoot = Environment.getExternalStorageDirectory().absolutePath

    private val _currentPath = MutableStateFlow(
        savedStateHandle.get<String>("currentPath") ?: storageRoot
    )
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _currentCategory = MutableStateFlow(MediaCategory.NONE)
    val currentCategory: StateFlow<MediaCategory> = _currentCategory.asStateFlow()

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortType = MutableStateFlow(SortType.NAME)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    private val _sortDescending = MutableStateFlow(false)
    val sortDescending: StateFlow<Boolean> = _sortDescending.asStateFlow()

    private val _showHiddenFiles = MutableStateFlow(false)
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.DETAILED)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _storageVolumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val storageVolumes: StateFlow<List<StorageVolumeInfo>> = _storageVolumes.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<File>>(emptySet())
    val selectedFiles: StateFlow<Set<File>> = _selectedFiles.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    val bookmarks = bookmarkRepository.getAllBookmarks()
    val trashItems = trashRepository.getAllTrash()

    init {
        loadVolumes()
        loadFiles()
    }

    fun setPermissionGranted(granted: Boolean) {}

    fun navigateTo(path: String) {
        _currentPath.value = path
        savedStateHandle["currentPath"] = path
        _currentCategory.value = MediaCategory.NONE
        loadFiles()
        trackRecentAccess(path)
    }

    fun navigateUp() {
        val parent = File(_currentPath.value).parent
        if (parent != null) navigateTo(parent)
    }

    fun selectCategory(category: MediaCategory) {
        _currentCategory.value = category
        loadCategoryFiles(category)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun setSortType(type: SortType) {
        _sortType.value = type
        applyFilters()
    }

    fun setSortDescending(descending: Boolean) {
        _sortDescending.value = descending
        applyFilters()
    }

    fun toggleHiddenFiles() {
        _showHiddenFiles.value = !_showHiddenFiles.value
        loadFiles()
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    fun selectFile(file: File) {
        val current = _selectedFiles.value.toMutableSet()
        if (current.contains(file)) current.remove(file) else current.add(file)
        _selectedFiles.value = current
    }

    fun clearSelection() { _selectedFiles.value = emptySet() }

    fun selectAll() {
        _selectedFiles.value = _files.value.map { it.file }.toSet()
    }

    fun refresh() { loadFiles() }

    fun isBookmark(path: String) = bookmarkRepository.isBookmarked(path)

    fun toggleBookmark(path: String, label: String) {
        viewModelScope.launch { bookmarkRepository.toggle(path, label) }
    }

    fun moveToTrash() {
        viewModelScope.launch {
            val selected = _selectedFiles.value.toList()
            var count = 0
            for (file in selected) {
                if (trashRepository.moveToTrash(file)) count++
            }
            _selectedFiles.value = emptySet()
            _toastEvent.emit("$count items moved to trash")
            loadFiles()
        }
    }

    fun restoreFromTrash(item: com.example.devmanager.data.local.entity.TrashEntity) {
        viewModelScope.launch {
            if (trashRepository.restore(item)) {
                _toastEvent.emit("Restored")
                loadFiles()
            }
        }
    }

    fun deletePermanently(item: com.example.devmanager.data.local.entity.TrashEntity) {
        viewModelScope.launch {
            if (trashRepository.deletePermanently(item)) {
                _toastEvent.emit("Deleted permanently")
                loadFiles()
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            trashRepository.emptyTrash()
            _toastEvent.emit("Trash emptied")
        }
    }

    fun isTrashPath(path: String): Boolean {
        return path == fileRepository.getTrashDir().absolutePath
    }

    val trashDirPath: String get() = fileRepository.getTrashDir().absolutePath

    fun createDirectory(name: String) {
        viewModelScope.launch {
            val result = fileRepository.createDirectory(_currentPath.value, name)
            result.onSuccess { _toastEvent.emit("Folder created"); loadFiles() }
                .onFailure { _toastEvent.emit(it.message ?: "Failed") }
        }
    }

    fun createFile(name: String) {
        viewModelScope.launch {
            val result = fileRepository.createFile(_currentPath.value, name)
            result.onSuccess { _toastEvent.emit("File created"); loadFiles() }
                .onFailure { _toastEvent.emit(it.message ?: "Failed") }
        }
    }

    fun renameFile(file: File, newName: String) {
        viewModelScope.launch {
            val result = fileRepository.rename(file, newName)
            result.onSuccess { _toastEvent.emit("Renamed"); loadFiles() }
                .onFailure { _toastEvent.emit(it.message ?: "Failed") }
        }
    }

    fun deleteFiles() {
        viewModelScope.launch {
            val selected = _selectedFiles.value.toList()
            for (file in selected) fileRepository.delete(file)
            _selectedFiles.value = emptySet()
            _toastEvent.emit("${selected.size} items deleted")
            loadFiles()
        }
    }

    fun copySelected(cut: Boolean = false) {
        _clipboardFiles.value = _selectedFiles.value.toList()
        _isCut.value = cut
        _toastEvent.emit("${_selectedFiles.value.size} items ${if (cut) "cut" else "copied"}")
        clearSelection()
    }

    private val _clipboardFiles = MutableStateFlow<List<File>>(emptyList())
    val clipboardFiles: StateFlow<List<File>> = _clipboardFiles.asStateFlow()

    private val _isCut = MutableStateFlow(false)
    val isCut: StateFlow<Boolean> = _isCut.asStateFlow()

    fun pasteFiles() {
        viewModelScope.launch {
            val files = _clipboardFiles.value
            val dest = _currentPath.value
            val isCut = _isCut.value
            for (file in files) {
                if (isCut) fileRepository.move(file, dest, com.example.devmanager.data.model.ConflictStrategy.RENAME)
                else fileRepository.copy(file, dest, com.example.devmanager.data.model.ConflictStrategy.RENAME)
            }
            _clipboardFiles.value = emptyList()
            _toastEvent.emit("Paste complete")
            loadFiles()
        }
    }

    fun formatSize(bytes: Long) = fileRepository.formatSize(bytes)
    fun formatDate(timestamp: Long) = fileRepository.formatDate(timestamp)
    fun getFileDetails(file: File) = fileRepository.getFileDetails(file)
    fun calculateMd5(file: File) = fileRepository.calculateMd5(file)

    fun handleFileClick(file: File) {
        val extension = file.extension.lowercase()
        val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        val docExtensions = listOf("pdf", "docx", "doc", "xlsx", "xls", "csv", "txt", "log", "md", "xml", "html", "css", "json")

        if (file.isDirectory) {
            navigateTo(file.absolutePath)
        } else if (extension in imageExtensions) {
            _imageViewerFile.value = file
        } else if (extension in docExtensions) {
            openDocumentViewer(file)
        } else if (extension == "apk") {
            installApk(file)
        } else {
            _imageViewerFile.value = file
        }
    }

    // Image Viewer
    private val _imageViewerFile = MutableStateFlow<File?>(null)
    val imageViewerFile: StateFlow<File?> = _imageViewerFile.asStateFlow()

    fun closeImageViewer() { _imageViewerFile.value = null }

    // Text Editor
    private val _textEditorFile = MutableStateFlow<File?>(null)
    val textEditorFile: StateFlow<File?> = _textEditorFile.asStateFlow()

    private val _textEditorContent = MutableStateFlow("")
    val textEditorContent: StateFlow<String> = _textEditorContent.asStateFlow()

    fun openTextEditor(file: File) {
        _textEditorFile.value = file
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try { _textEditorContent.value = file.readText() }
                catch (_: Exception) {}
            }
        }
    }

    fun updateTextEditorContent(content: String) { _textEditorContent.value = content }

    fun saveTextFile() {
        viewModelScope.launch {
            val file = _textEditorFile.value ?: return@launch
            withContext(Dispatchers.IO) {
                try { file.writeText(_textEditorContent.value) } catch (_: Exception) {}
            }
            _toastEvent.emit("Saved")
        }
    }

    fun closeTextEditor() { _textEditorFile.value = null }

    // Document Viewer
    private val _docViewerFile = MutableStateFlow<File?>(null)
    val docViewerFile: StateFlow<File?> = _docViewerFile.asStateFlow()

    private val _docLines = MutableStateFlow<List<com.example.devmanager.ui.documentviewer.DocLine>>(emptyList())
    val docLines: StateFlow<List<com.example.devmanager.ui.documentviewer.DocLine>> = _docLines.asStateFlow()

    private val _excelSheets = MutableStateFlow<List<com.example.devmanager.ui.documentviewer.ExcelSheet>>(emptyList())
    val excelSheets: StateFlow<List<com.example.devmanager.ui.documentviewer.ExcelSheet>> = _excelSheets.asStateFlow()

    private val _pdfPageCount = MutableStateFlow(0)
    val pdfPageCount: StateFlow<Int> = _pdfPageCount.asStateFlow()

    fun openDocumentViewer(file: File) {
        _isLoading.value = true
        _docViewerFile.value = file
        val extension = file.extension.lowercase()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    when (extension) {
                        "docx", "doc" -> {
                            _docLines.value = com.example.devmanager.ui.documentviewer.DocumentParser.parseDocx(file)
                        }
                        "xlsx", "xls" -> {
                            _excelSheets.value = com.example.devmanager.ui.documentviewer.DocumentParser.parseXlsx(file)
                        }
                        "csv" -> {
                            _excelSheets.value = listOf(parseCsvToSheet(file))
                        }
                        "pdf" -> {
                            val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                            val renderer = android.graphics.pdf.PdfRenderer(fd)
                            _pdfPageCount.value = renderer.pageCount
                            renderer.close(); fd.close()
                        }
                    }
                } catch (e: Exception) { e.printStackTrace()
                } finally { _isLoading.value = false }
            }
        }
    }

    fun closeDocumentViewer() {
        _docViewerFile.value = null
        _docLines.value = emptyList()
        _excelSheets.value = emptyList()
        _pdfPageCount.value = 0
    }

    private fun parseCsvToSheet(file: File): com.example.devmanager.ui.documentviewer.ExcelSheet {
        val cells = mutableMapOf<String, String>()
        var maxRow = 1; var maxCol = 1
        try {
            file.readLines().forEachIndexed { row, line ->
                line.split(",").forEachIndexed { col, value ->
                    val key = "${row}_${col}"
                    cells[key] = value.trim().trim('"')
                    maxRow = row + 1; maxCol = maxOf(maxCol, col + 1)
                }
            }
        } catch (_: Exception) {}
        return com.example.devmanager.ui.documentviewer.ExcelSheet(
            name = file.name, cells = cells, maxRow = maxRow, maxCol = maxCol
        )
    }

    private fun installApk(file: File) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(
                androidx.core.content.FileProvider.getUriForFile(
                    application, "${application.packageName}.provider", file
                ),
                "application/vnd.android.package-archive"
            )
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        application.startActivity(intent)
    }

    private fun loadFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val path = _currentPath.value
                val showHidden = _showHiddenFiles.value
                var items = fileRepository.listFiles(path, showHidden)
                items = applySortAndFilter(items)
                _files.value = items
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }

    private fun loadCategoryFiles(category: MediaCategory) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = withContext(Dispatchers.IO) {
                    val root = Environment.getExternalStorageDirectory()
                    val allFiles = mutableListOf<FileItem>()
                    val queue = ArrayDeque<File>(); queue.add(root)
                    val maxFiles = 500
                    val extMap = mapOf(
                        MediaCategory.IMAGES to setOf("jpg", "jpeg", "png", "gif", "webp", "bmp"),
                        MediaCategory.VIDEOS to setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm"),
                        MediaCategory.MUSIC to setOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a")
                    )
                    val targetExts = extMap[category] ?: emptySet()
                    while (queue.isNotEmpty() && allFiles.size < maxFiles) {
                        val dir = queue.removeFirst()
                        try {
                            dir.listFiles()?.forEach { file ->
                                if (file.isDirectory && !file.name.startsWith(".") && !file.name.startsWith(".")) {
                                    queue.add(file)
                                } else if (file.extension.lowercase() in targetExts) {
                                    allFiles.add(FileItem(
                                        file = file, name = file.name, isDirectory = false,
                                        sizeLabel = fileRepository.formatSize(file.length()), size = file.length(),
                                        lastModified = file.lastModified(),
                                        lastModifiedLabel = fileRepository.formatDate(file.lastModified()),
                                        extension = file.extension.lowercase()
                                    ))
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    allFiles.sortedByDescending { it.lastModified }
                }
                _files.value = items
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }

    private fun applySortAndFilter(items: List<FileItem>): List<FileItem> {
        val query = _searchQuery.value.lowercase()
        var result = if (query.isNotEmpty()) items.filter {
            it.name.lowercase().contains(query)
        } else items

        val type = _sortType.value
        val desc = _sortDescending.value
        result = when (type) {
            SortType.NAME -> if (desc) result.sortedByDescending { it.name.lowercase() }
                             else result.sortedBy { it.name.lowercase() }
            SortType.SIZE -> if (desc) result.sortedByDescending { it.size }
                             else result.sortedBy { it.size }
            SortType.DATE -> if (desc) result.sortedByDescending { it.lastModified }
                             else result.sortedBy { it.lastModified }
            SortType.TYPE -> if (desc) result.sortedByDescending { it.extension }
                             else result.sortedBy { it.extension }
        }
        return result
    }

    private fun applyFilters() {
        loadFiles()
    }

    private fun loadVolumes() {
        viewModelScope.launch {
            val volumes = withContext(Dispatchers.IO) {
                fileRepository.getStorageVolumes().map { (path, name) ->
                    val info = fileRepository.getStorageInfo(path)
                    StorageVolumeInfo(
                        name = name, path = path,
                        isPrimary = path == Environment.getExternalStorageDirectory().absolutePath,
                        isRemovable = path.startsWith("/storage/") && path != Environment.getExternalStorageDirectory().absolutePath,
                        totalSpace = info.first, freeSpace = info.second
                    )
                }
            }
            _storageVolumes.value = volumes
        }
    }

    private fun trackRecentAccess(path: String) {
    }

    // Storage Cleaner
    fun analyzeStorage(onResult: (com.example.devmanager.data.repository.StorageAnalysis) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = withContext(Dispatchers.IO) {
                fileRepository.analyzeStorage(storageRoot)
            }
            onResult(result)
            _isLoading.value = false
        }
    }

    fun findLargeFiles(onResult: (List<File>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                fileRepository.findLargeFiles(storageRoot)
            }
            onResult(result)
        }
    }

    fun findJunkFiles(onResult: (List<File>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                fileRepository.findJunkFiles(storageRoot)
            }
            onResult(result)
        }
    }

    fun findEmptyDirs(onResult: (List<File>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                fileRepository.findEmptyDirectories(storageRoot)
            }
            onResult(result)
        }
    }

    fun findDuplicateFiles(onResult: (List<List<File>>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                fileRepository.findDuplicateFiles(storageRoot)
            }
            onResult(result)
        }
    }

    // Batch Rename
    fun batchRename(files: List<File>, pattern: String, replaceWith: String, useRegex: Boolean = false, numbering: String? = null) {
        viewModelScope.launch {
            var count = 0
            files.sortedBy { it.name }.forEachIndexed { index, file ->
                var newName = if (useRegex) file.name.replace(Regex(pattern), replaceWith)
                              else file.name.replace(pattern, replaceWith)
                if (numbering != null) {
                    val num = if (numbering.contains("#")) {
                        val digits = numbering.count { it == '#' }
                        String.format("%0${digits}d", index + 1)
                    } else "${index + 1}"
                    val base = newName.substringBeforeLast(".")
                    val ext = newName.substringAfterLast(".", "")
                    newName = if (ext.isNotEmpty()) "${base}_$num.$ext" else "${base}_$num"
                }
                val result = fileRepository.rename(file, newName)
                if (result.isSuccess) count++
            }
            viewModelScope.launch { _toastEvent.emit("$count files renamed") }
            loadFiles()
        }
    }

    fun shareFiles(files: List<File>) {
        val uris = files.map { file ->
            androidx.core.content.FileProvider.getUriForFile(
                application, "${application.packageName}.provider", file
            )
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
            type = "*/*"
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        application.startActivity(android.content.Intent.createChooser(intent, "Share files"))
    }
}
