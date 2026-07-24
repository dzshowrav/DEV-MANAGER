package com.example.devmanager.ui.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.devmanager.data.model.FileItem
import com.example.devmanager.data.model.MediaCategory
import com.example.devmanager.data.model.SortType
import com.example.devmanager.data.model.ViewMode
import com.example.devmanager.ui.components.FileListView
import com.example.devmanager.ui.components.PathBreadcrumbs
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel,
    onOpenTextEditor: (String) -> Unit = {},
    onOpenImageViewer: (String) -> Unit = {},
    onOpenDocumentViewer: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val sortDescending by viewModel.sortDescending.collectAsState()
    val showHiddenFiles by viewModel.showHiddenFiles.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val currentCategory by viewModel.currentCategory.collectAsState()
    val storageVolumes by viewModel.storageVolumes.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val clipboardFiles by viewModel.clipboardFiles.collectAsState()
    val isCut by viewModel.isCut.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState(initial = emptyList())
    val textEditorFile by viewModel.textEditorFile.collectAsState()
    val imageViewerFile by viewModel.imageViewerFile.collectAsState()

    // Image Viewer overlay
    if (imageViewerFile != null) {
        com.example.devmanager.ui.imageviewer.ImageViewerScreen(
            viewModel = viewModel,
            filePath = imageViewerFile!!.absolutePath,
            onBack = { viewModel.closeImageViewer() }
        )
        return
    }

    // Text Editor overlay
    if (textEditorFile != null) {
        com.example.devmanager.ui.texteditor.TextEditorScreen(
            viewModel = viewModel,
            filePath = textEditorFile!!.absolutePath,
            onBack = { viewModel.closeTextEditor() }
        )
        return
    }

    var showSortMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var isCreateFolder by remember { mutableStateOf(true) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val storageRoot = Environment.getExternalStorageDirectory().absolutePath

    BackHandler(enabled = currentPath != storageRoot || currentCategory != MediaCategory.NONE || isSearchActive || selectedFiles.isNotEmpty()) {
        if (selectedFiles.isNotEmpty()) viewModel.clearSelection()
        else if (isSearchActive) { isSearchActive = false; viewModel.setSearchQuery("") }
        else if (currentCategory != MediaCategory.NONE) viewModel.selectCategory(MediaCategory.NONE)
        else viewModel.navigateUp()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                LazyColumn(modifier = Modifier.padding(vertical = 16.dp)) {
                    item {
                        Text("DEV MANAGER", style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider()
                        Text("Storage", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                    }
                    items(storageVolumes) { volume ->
                        val icon = if (volume.isRemovable) Icons.Default.Apps else Icons.Default.Apps
                        NavigationDrawerItem(
                            label = { Text(volume.name) },
                            icon = { Icon(if (volume.isRemovable) Icons.Default.Movie else Icons.Default.Storage, null) },
                            selected = false,
                            onClick = { viewModel.navigateTo(volume.path); scope.launch { drawerState.close() } },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                    item {
                        HorizontalDivider()
                        Text("Library", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                        NavigationDrawerItem(label = { Text("Downloads") }, icon = { Icon(Icons.Default.Download, null) },
                            selected = false, onClick = {
                                viewModel.navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
                                scope.launch { drawerState.close() }
                            }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                        NavigationDrawerItem(label = { Text("Images") }, icon = { Icon(Icons.Default.Image, null) },
                            selected = currentCategory == MediaCategory.IMAGES, onClick = {
                                viewModel.selectCategory(MediaCategory.IMAGES); scope.launch { drawerState.close() }
                            }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                        NavigationDrawerItem(label = { Text("Videos") }, icon = { Icon(Icons.Default.Movie, null) },
                            selected = currentCategory == MediaCategory.VIDEOS, onClick = {
                                viewModel.selectCategory(MediaCategory.VIDEOS); scope.launch { drawerState.close() }
                            }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                        NavigationDrawerItem(label = { Text("Music") }, icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                            selected = currentCategory == MediaCategory.MUSIC, onClick = {
                                viewModel.selectCategory(MediaCategory.MUSIC); scope.launch { drawerState.close() }
                            }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                        NavigationDrawerItem(label = { Text("Documents") }, icon = { Icon(Icons.AutoMirrored.Filled.Article, null) },
                            selected = false, onClick = {
                                viewModel.navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath)
                                scope.launch { drawerState.close() }
                            }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                        HorizontalDivider()
                        NavigationDrawerItem(label = { Text("Trash Bin") }, icon = { Icon(Icons.Default.Delete, null) },
                            selected = false, onClick = {
                                viewModel.navigateTo(viewModel.trashDirPath); scope.launch { drawerState.close() }
                            }, modifier = Modifier.padding(horizontal = 12.dp))
                        if (bookmarks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Text("Bookmarks", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                        }
                    }
                    items(bookmarks) { bm ->
                        NavigationDrawerItem(label = { Text(bm.label) }, icon = { Icon(Icons.Default.Restore, null) },
                            selected = false, onClick = {
                                viewModel.navigateTo(bm.path); scope.launch { drawerState.close() }
                            }, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (selectedFiles.isNotEmpty()) {
                    SelectionTopBar(
                        count = selectedFiles.size,
                        isTrash = viewModel.isTrashPath(currentPath),
                        onClose = { viewModel.clearSelection() },
                        onCopy = { viewModel.copySelected(cut = false) },
                        onCut = { viewModel.copySelected(cut = true) },
                        onDelete = { viewModel.moveToTrash() },
                        onDeletePermanent = { viewModel.deleteFiles() },
                        onRestore = {}
                    )
                } else if (isSearchActive) {
                    SearchTopBar(
                        query = searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        onClose = { isSearchActive = false; viewModel.setSearchQuery("") }
                    )
                } else {
                    MainTopBar(
                        title = File(currentPath).name.ifEmpty { "DEV MANAGER" },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onSearchClick = { isSearchActive = true },
                        viewMode = viewMode,
                        onViewModeChange = { viewModel.setViewMode(it) },
                        showSortMenu = showSortMenu,
                        onSortMenuToggle = { showSortMenu = it },
                        sortType = sortType,
                        sortDescending = sortDescending,
                        onSortTypeChange = { viewModel.setSortType(it) },
                        onSortDescendingToggle = { viewModel.setSortDescending(!sortDescending) },
                        onRefresh = { viewModel.refresh() },
                        onCreateFolder = { isCreateFolder = true; showCreateDialog = true },
                        onCreateFile = { isCreateFolder = false; showCreateDialog = true }
                    )
                }
            },
            bottomBar = {
                if (clipboardFiles.isNotEmpty()) {
                    PasteBottomBar(
                        count = clipboardFiles.size,
                        isCut = isCut,
                        onPaste = { viewModel.pasteFiles() },
                        onClear = { /* clipboard cleared in VM */ }
                    )
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                if (!isSearchActive && currentCategory == MediaCategory.NONE) {
                    PathBreadcrumbs(
                        path = currentPath,
                        onNavigate = { viewModel.navigateTo(it) }
                    )
                }
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (files.isEmpty() && !isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No files", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    FileListView(
                        items = files,
                        viewMode = viewMode,
                        selectedFiles = selectedFiles,
                        onFileClick = { item ->
                            if (selectedFiles.isNotEmpty()) viewModel.selectFile(item.file)
                            else viewModel.handleFileClick(item.file)
                        },
                        onFileLongClick = { item ->
                            if (selectedFiles.isEmpty()) viewModel.selectFile(item.file)
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        com.example.devmanager.ui.components.CreateDialog(
            isFolder = isCreateFolder,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                if (isCreateFolder) viewModel.createDirectory(name)
                else viewModel.createFile(name)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    title: String,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    showSortMenu: Boolean,
    onSortMenuToggle: (Boolean) -> Unit,
    sortType: SortType,
    sortDescending: Boolean,
    onSortTypeChange: (SortType) -> Unit,
    onSortDescendingToggle: () -> Unit,
    onRefresh: () -> Unit,
    onCreateFolder: () -> Unit,
    onCreateFile: () -> Unit
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, "Menu") } },
        actions = {
            IconButton(onClick = onSearchClick) { Icon(Icons.Default.Search, "Search") }
            Box {
                var showMore by remember { mutableStateOf(false) }
                IconButton(onClick = { showMore = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                    DropdownMenuItem(text = { Text("New Folder") }, onClick = { showMore = false; onCreateFolder() })
                    DropdownMenuItem(text = { Text("New File") }, onClick = { showMore = false; onCreateFile() })
                    DropdownMenuItem(text = { Text("Refresh") }, onClick = { showMore = false; onRefresh() })
                }
            }
            Box {
                IconButton(onClick = { onSortMenuToggle(true) }) { Icon(Icons.Default.Sort, "Sort") }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { onSortMenuToggle(false) }) {
                    DropdownMenuItem(text = { Text("Name") }, onClick = { onSortTypeChange(SortType.NAME); onSortMenuToggle(false) })
                    DropdownMenuItem(text = { Text("Size") }, onClick = { onSortTypeChange(SortType.SIZE); onSortMenuToggle(false) })
                    DropdownMenuItem(text = { Text("Date") }, onClick = { onSortTypeChange(SortType.DATE); onSortMenuToggle(false) })
                    DropdownMenuItem(text = { Text("Type") }, onClick = { onSortTypeChange(SortType.TYPE); onSortMenuToggle(false) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(if (sortDescending) "Ascending" else "Descending") },
                        onClick = { onSortDescendingToggle(); onSortMenuToggle(false) })
                }
            }
            IconButton(onClick = {
                onViewModeChange(when (viewMode) {
                    ViewMode.DETAILED -> ViewMode.COMPACT
                    ViewMode.COMPACT -> ViewMode.GRID
                    ViewMode.GRID -> ViewMode.DETAILED
                })
            }) {
                Icon(
                    when (viewMode) {
                        ViewMode.DETAILED -> Icons.Default.ViewList
                        ViewMode.COMPACT -> Icons.Default.ViewList
                        ViewMode.GRID -> Icons.Default.ViewModule
                    }, "View"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search files...") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    isTrash: Boolean,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onDeletePermanent: () -> Unit,
    onRestore: () -> Unit
) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cancel") } },
        actions = {
            if (isTrash) {
                IconButton(onClick = onRestore) { Icon(Icons.Default.Restore, "Restore") }
                IconButton(onClick = onDeletePermanent) { Icon(Icons.Default.DeleteForever, "Delete Permanently") }
            } else {
                IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, "Copy") }
                IconButton(onClick = onCut) { Icon(Icons.Default.ContentCut, "Cut") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Move to Trash") }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    )
}

@Composable
private fun PasteBottomBar(
    count: Int,
    isCut: Boolean,
    onPaste: () -> Unit,
    onClear: () -> Unit
) {
    androidx.compose.material3.BottomAppBar(
        containerColor = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${if (isCut) "Cut" else "Copy"}: $count items",
                modifier = Modifier.weight(1f))
            androidx.compose.material3.FilledTonalButton(onClick = onPaste) {
                Icon(Icons.Default.ContentPaste, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Paste")
            }
        }
    }
}
