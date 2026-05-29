package com.example.devmanager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerApp(viewModel: FileManagerViewModel) {
    val context = LocalContext.current
    val permissionGranted by viewModel.permissionGranted.collectAsStateWithLifecycle()
    
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            viewModel.setPermissionGranted(Environment.isExternalStorageManager())
        } else {
            viewModel.setPermissionGranted(true)
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            viewModel.setPermissionGranted(Environment.isExternalStorageManager())
        }
    }
    
    if (!permissionGranted) {
        PermissionScreen {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${context.packageName}")
                    permissionLauncher.launch(intent)
                } catch (e: Exception) {
                    permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        }
        return
    }

    val textEditorFile by viewModel.textEditorFile.collectAsStateWithLifecycle()
    if (textEditorFile != null) {
        TextEditorScreen(viewModel)
        return
    }

    var showStorageAnalyzer by remember { mutableStateOf(false) }
    var showAppManager by remember { mutableStateOf(false) }
    var showMediaManager by remember { mutableStateOf(false) }
    var showNetworkManager by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }

    if (showStorageAnalyzer) {
        StorageAnalyzerScreen(
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
            fileViewModel = viewModel,
            onBack = { showStorageAnalyzer = false }
        )
        return
    }

    if (showAppManager) {
        AppManagerScreen(
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
            onBack = { showAppManager = false }
        )
        return
    }

    if (showMediaManager) {
        MediaManagerScreen(
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
            onBack = { showMediaManager = false }
        )
        return
    }

    if (showNetworkManager) {
        NetworkManagerScreen(
            viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
            onBack = { showNetworkManager = false }
        )
        return
    }

    val imageViewerFile by viewModel.imageViewerFile.collectAsStateWithLifecycle()
    if (imageViewerFile != null) {
        ImageViewerScreen(viewModel, file = imageViewerFile!!)
        return
    }

    val documentViewerFile by viewModel.documentViewerFile.collectAsStateWithLifecycle()
    if (documentViewerFile != null) {
        DocumentViewerScreen(file = documentViewerFile!!) {
            viewModel.closeDocumentViewer()
        }
        return
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(viewModel, onNavigate = {
                scope.launch { drawerState.close() }
            }, onOpenAnalyzer = {
                scope.launch { 
                    drawerState.close() 
                    showStorageAnalyzer = true
                }
            }, onOpenAppManager = {
                scope.launch { 
                    drawerState.close() 
                    showAppManager = true
                }
            }, onOpenMediaManager = {
                scope.launch { 
                    drawerState.close() 
                    showMediaManager = true
                }
            }, onOpenNetworkManager = {
                scope.launch { 
                    drawerState.close() 
                    showNetworkManager = true
                }
            }, onOpenSettings = {
                scope.launch { 
                    drawerState.close() 
                    showSettings = true
                }
            })
        }
    ) {
        MainScreen(viewModel, onOpenDrawer = {
            scope.launch { drawerState.open() }
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: FileManagerViewModel, onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val currentPath by viewModel.currentPath.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortType by viewModel.sortType.collectAsStateWithLifecycle()
    val showHiddenFiles by viewModel.showHiddenFiles.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()

    val currentCategory by viewModel.currentCategory.collectAsStateWithLifecycle()

    val storageVolumes by viewModel.storageVolumes.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val clipboardFiles by viewModel.clipboardFiles.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var isCreateFolder by remember { mutableStateOf(true) }
    var fileOptionsSelected by remember { mutableStateOf<FileItem?>(null) }
    var fileToRename by remember { mutableStateOf<FileItem?>(null) }
    var fileDetailsSelected by remember { mutableStateOf<FileItem?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showZipDialog by remember { mutableStateOf(false) }

    val storageRoot = Environment.getExternalStorageDirectory().absolutePath
    BackHandler(enabled = currentPath != storageRoot || currentCategory != MediaCategory.NONE || isSearchActive || selectedFiles.isNotEmpty()) {
        if (selectedFiles.isNotEmpty()) {
            viewModel.clearSelection()
        } else if (isSearchActive) {
            isSearchActive = false
            viewModel.setSearchQuery("")
        } else if (currentCategory != MediaCategory.NONE) {
            viewModel.selectCategory(MediaCategory.NONE)
        } else {
            viewModel.navigateUp()
        }
    }

    Scaffold(
        topBar = {
            if (selectedFiles.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selectedFiles.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, "Cancel Selection")
                        }
                    },
                    actions = {
                        val isTrash = viewModel.isTrashPath(currentPath)
                        if (isTrash) {
                            IconButton(onClick = { viewModel.restoreFromTrash() }) { Icon(Icons.Default.Restore, "Restore") }
                            IconButton(onClick = { viewModel.deleteFiles() }) { Icon(Icons.Default.DeleteForever, "Delete Permanently") }
                        } else {
                            IconButton(onClick = { viewModel.copySelected(cut = false) }) { Icon(Icons.Default.ContentCopy, "Copy") }
                            IconButton(onClick = { viewModel.copySelected(cut = true) }) { Icon(Icons.Default.ContentCut, "Cut") }
                            IconButton(onClick = { viewModel.moveToTrash() }) { Icon(Icons.Default.Delete, "Move to Trash") }
                        }
                        Box {
                            var showBatchMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showBatchMenu = true }) { Icon(Icons.Default.MoreVert, "More Options") }
                            DropdownMenu(expanded = showBatchMenu, onDismissRequest = { showBatchMenu = false }) {
                                DropdownMenuItem(text = { Text("Select All") }, onClick = { viewModel.selectAll(); showBatchMenu = false })
                                DropdownMenuItem(text = { Text("Compress (ZIP)") }, onClick = { showZipDialog = true; showBatchMenu = false })
                                DropdownMenuItem(text = { Text("Share") }, onClick = { 
                                    shareMultiple(context, selectedFiles.toList())
                                    showBatchMenu = false
                                })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            } else if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search here...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearchActive = false
                            viewModel.setSearchQuery("") 
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close Search") }
                    }
                )
            } else {
                TopAppBar(
                    title = { 
                        if (currentCategory != MediaCategory.NONE) {
                            Text(
                                text = when (currentCategory) {
                                    MediaCategory.IMAGES -> "Images Folders"
                                    MediaCategory.VIDEOS -> "Videos Folders"
                                    MediaCategory.MUSIC -> "Music Folders"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.titleLarge
                            )
                        } else {
                            val currentVolume = storageVolumes.find { currentPath.startsWith(it.path) }
                            val currentRootPath = currentVolume?.path ?: viewModel.storageRoot
                            val currentRootName = currentVolume?.name ?: "Internal Storage"
                            
                            PathBreadcrumbs(
                                currentPath = currentPath,
                                storageRoot = currentRootPath,
                                rootName = currentRootName,
                                onPathSelect = { viewModel.navigateTo(it) }
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, "Menu") }
                    },
                    actions = {
                        if (viewModel.isTrashPath(currentPath) && files.isNotEmpty()) {
                            IconButton(onClick = { viewModel.emptyTrash() }) { Icon(Icons.Default.DeleteSweep, "Empty Trash") }
                        }
                        IconButton(onClick = { isSearchActive = true }) { Icon(Icons.Default.Search, "Search") }
                        IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, "Sort & View") }
                        Box {
                            var showGlobalMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showGlobalMenu = true }) { Icon(Icons.Default.MoreVert, "More Options") }
                            DropdownMenu(expanded = showGlobalMenu, onDismissRequest = { showGlobalMenu = false }) {
                                DropdownMenuItem(text = { Text("Select All") }, onClick = { viewModel.selectAll(); showGlobalMenu = false })
                                DropdownMenuItem(text = { Text("Create .nomedia") }, onClick = { viewModel.createNoMedia(); android.widget.Toast.makeText(context, ".nomedia created", android.widget.Toast.LENGTH_SHORT).show(); showGlobalMenu = false })
                                DropdownMenuItem(text = { Text("Clear App Cache") }, onClick = { 
                                    context.cacheDir.deleteRecursively()
                                    android.widget.Toast.makeText(context, "Cache Cleared", android.widget.Toast.LENGTH_SHORT).show()
                                    showGlobalMenu = false 
                                })
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (clipboardFiles.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.pasteFiles() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        icon = { Icon(Icons.Default.ContentPaste, "Paste") },
                        text = { Text("Paste (${clipboardFiles.size})") },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                var fabExpanded by remember { mutableStateOf(false) }
                if (fabExpanded) {
                    SmallFloatingActionButton(onClick = { showCreateDialog = true; isCreateFolder = false; fabExpanded = false }, modifier = Modifier.padding(bottom = 8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, "New File")
                    }
                    SmallFloatingActionButton(onClick = { showCreateDialog = true; isCreateFolder = true; fabExpanded = false }, modifier = Modifier.padding(bottom = 8.dp)) {
                        Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_custom_folder), contentDescription = "New Folder", tint = Color.Unspecified, modifier = Modifier.size(24.dp))
                    }
                }
                if (selectedFiles.isEmpty()) {
                    FloatingActionButton(onClick = { fabExpanded = !fabExpanded }, containerColor = MaterialTheme.colorScheme.primary) {
                        Icon(if (fabExpanded) Icons.Default.Close else Icons.Default.Add, "Add")
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (files.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(if (isSearchActive) "No matches found" else "This folder is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                        if (viewMode == ViewMode.DETAILED || viewMode == ViewMode.COMPACT) {
                            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                            FastScrollWrapper(
                                listState = listState,
                                labelProvider = { index -> files.getOrNull(index)?.name ?: "" }
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 120.dp)) {
                                    items(files, key = { it.file.absolutePath }) { item ->
                                        val isSelected = selectedFiles.contains(item.file)
                                        val isSelectMode = selectedFiles.isNotEmpty()
                                        FileListItem(
                                            item = item,
                                            isSelected = isSelected,
                                            isSelectMode = isSelectMode,
                                            isCompact = (viewMode == ViewMode.COMPACT),
                                            onClick = {
                                                if (isSelectMode) viewModel.toggleSelection(item.file)
                                                else handleFileClick(context, item, viewModel)
                                            },
                                            onLongClick = {
                                                if (!isSelectMode) viewModel.toggleSelection(item.file)
                                            },
                                            onMoreClick = { fileOptionsSelected = item }
                                        )
                                    }
                                }
                            }
                        } else if (viewMode == ViewMode.GRID) {
                            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp, start=8.dp, end=8.dp)) {
                                items(files, key = { it.file.absolutePath }) { item ->
                                    val isSelected = selectedFiles.contains(item.file)
                                    val isSelectMode = selectedFiles.isNotEmpty()
                                    FileGridItem(
                                        item = item,
                                        isSelected = isSelected,
                                        isSelectMode = isSelectMode,
                                        onClick = {
                                            if (isSelectMode) viewModel.toggleSelection(item.file)
                                            else handleFileClick(context, item, viewModel)
                                        },
                                        onLongClick = {
                                            if (!isSelectMode) viewModel.toggleSelection(item.file)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
    // Dialogs mapped appropriately
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (isCreateFolder) "New Folder" else "New File") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        if (isCreateFolder) viewModel.createFolder(name.trim()) else viewModel.createFile(name.trim())
                        showCreateDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } }
        )
    }
    
    if (showZipDialog) {
        var zipName by remember { mutableStateOf("archive") }
        AlertDialog(
            onDismissRequest = { showZipDialog = false },
            title = { Text("Compress Files") },
            text = { OutlinedTextField(value = zipName, onValueChange = { zipName = it }, label = { Text("Archive Name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (zipName.isNotBlank()) {
                        viewModel.zipSelected(zipName.trim())
                        showZipDialog = false
                    }
                }) { Text("Compress") }
            },
            dismissButton = { TextButton(onClick = { showZipDialog = false }) { Text("Cancel") } }
        )
    }
    
    if (fileToRename != null) {
        var newName by remember { mutableStateOf(fileToRename!!.name) }
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("New Name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank() && newName != fileToRename!!.name) viewModel.renameFile(fileToRename!!.file, newName.trim())
                    fileToRename = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { fileToRename = null }) { Text("Cancel") } }
        )
    }

    if (fileDetailsSelected != null) {
        var md5 by remember { mutableStateOf("Calculating...") }
        var fullSize by remember { mutableStateOf("Calculating...") }
        
        LaunchedEffect(fileDetailsSelected) {
            fullSize = viewModel.formatSize(viewModel.calculateFullSize(fileDetailsSelected!!.file))
            if (!fileDetailsSelected!!.isDirectory && fileDetailsSelected!!.size < 100 * 1024 * 1024) { // only hash files < 100MB
                md5 = viewModel.calculateHash(fileDetailsSelected!!.file, "MD5")
            } else {
                md5 = "N/A"
            }
        }
        
        AlertDialog(
            onDismissRequest = { fileDetailsSelected = null },
            title = { Text("Details") },
            text = {
                Column {
                    Text("Name: ${fileDetailsSelected!!.name}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Path: ${fileDetailsSelected!!.file.absolutePath}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Size: $fullSize")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Contains: ${fileDetailsSelected!!.sizeLabel}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Last Modified: ${fileDetailsSelected!!.lastModifiedLabel}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("MD5 Hash: $md5", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = { TextButton(onClick = { fileDetailsSelected = null }) { Text("Close") } }
        )
    }

    if (showSortMenu) {
        ModalBottomSheet(onDismissRequest = { showSortMenu = false }, sheetState = rememberModalBottomSheetState()) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("View", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    ViewModeOption("Detailed", Icons.AutoMirrored.Filled.ViewList, viewMode == ViewMode.DETAILED) {
                        viewModel.setViewMode(ViewMode.DETAILED)
                    }
                    ViewModeOption("Compact", Icons.Default.ViewHeadline, viewMode == ViewMode.COMPACT) {
                        viewModel.setViewMode(ViewMode.COMPACT)
                    }
                    ViewModeOption("Grid", Icons.Default.GridView, viewMode == ViewMode.GRID) {
                        viewModel.setViewMode(ViewMode.GRID)
                    }
                    ViewModeOption("Hidden", Icons.Default.VisibilityOff, showHiddenFiles) {
                        viewModel.setShowHiddenFiles(!showHiddenFiles)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Sort", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    val sortDescending by viewModel.sortDescending.collectAsStateWithLifecycle()
                    SortModeOption("Name", Icons.Default.SortByAlpha, sortType == SortType.NAME) {
                        viewModel.setSortType(SortType.NAME)
                    }
                    SortModeOption("Size", Icons.AutoMirrored.Filled.Sort, sortType == SortType.SIZE) {
                        viewModel.setSortType(SortType.SIZE)
                    }
                    SortModeOption("Date", Icons.Default.CalendarToday, sortType == SortType.DATE) {
                        viewModel.setSortType(SortType.DATE)
                    }
                    SortModeOption("Type", Icons.Default.InsertDriveFile, sortType == SortType.TYPE) {
                        viewModel.setSortType(SortType.TYPE)
                    }
                    SortModeOption("Desc", Icons.Default.SwapVert, sortDescending) {
                        viewModel.toggleSortDescending()
                    }
                }
            }
        }
    }

    if (fileOptionsSelected != null) {
        ModalBottomSheet(onDismissRequest = { fileOptionsSelected = null }, sheetState = rememberModalBottomSheetState()) {
            val isBookmarked = viewModel.isBookmarked(fileOptionsSelected!!.file.absolutePath)
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = fileOptionsSelected!!.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Open") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                    modifier = Modifier.clickable { handleFileClick(context, fileOptionsSelected!!, viewModel); fileOptionsSelected = null }
                )
                if (fileOptionsSelected!!.extension in listOf("txt", "log", "md", "csv", "json")) {
                    ListItem(
                        headlineContent = { Text("Open as Text") },
                        leadingContent = { Icon(Icons.Default.TextFields, null) },
                        modifier = Modifier.clickable { viewModel.openTextEditor(fileOptionsSelected!!.file); fileOptionsSelected = null }
                    )
                }
                if (fileOptionsSelected!!.extension == "zip") {
                    ListItem(
                        headlineContent = { Text("Extract Zip") },
                        leadingContent = { Icon(Icons.Default.UploadFile, null) },
                        modifier = Modifier.clickable { viewModel.unzipFile(fileOptionsSelected!!.file); fileOptionsSelected = null }
                    )
                }
                ListItem(
                    headlineContent = { Text(if (isBookmarked) "Unbookmark" else "Bookmark") },
                    leadingContent = { Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null) },
                    modifier = Modifier.clickable { viewModel.toggleBookmark(fileOptionsSelected!!.file.absolutePath); fileOptionsSelected = null }
                )
                ListItem(headlineContent = { Text("Copy Path") }, leadingContent = { Icon(Icons.Default.ContentCopy, null) }, modifier = Modifier.clickable { 
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Path", fileOptionsSelected!!.file.absolutePath))
                    android.widget.Toast.makeText(context, "Path Copied", android.widget.Toast.LENGTH_SHORT).show()
                    fileOptionsSelected = null
                })
                ListItem(headlineContent = { Text("Duplicate") }, leadingContent = { Icon(Icons.Default.ContentCopy, null) }, modifier = Modifier.clickable { viewModel.duplicateFile(fileOptionsSelected!!.file); fileOptionsSelected = null })
                ListItem(headlineContent = { Text("Edit selection mode") }, leadingContent = { Icon(Icons.Default.Checklist, null) }, modifier = Modifier.clickable { viewModel.toggleSelection(fileOptionsSelected!!.file); fileOptionsSelected = null })
                ListItem(headlineContent = { Text("Rename") }, leadingContent = { Icon(Icons.Default.Edit, null) }, modifier = Modifier.clickable { fileToRename = fileOptionsSelected; fileOptionsSelected = null })
                ListItem(headlineContent = { Text("Share") }, leadingContent = { Icon(Icons.Default.Share, null) }, modifier = Modifier.clickable { shareFile(context, fileOptionsSelected!!.file); fileOptionsSelected = null })
                ListItem(headlineContent = { Text("Details") }, leadingContent = { Icon(Icons.Default.Info, null) }, modifier = Modifier.clickable { fileDetailsSelected = fileOptionsSelected; fileOptionsSelected = null })
                if (viewModel.isTrashPath(currentPath)) {
                    ListItem(headlineContent = { Text("Restore") }, leadingContent = { Icon(Icons.Default.Restore, null) }, modifier = Modifier.clickable { viewModel.restoreFromTrash(setOf(fileOptionsSelected!!.file)); fileOptionsSelected = null })
                    ListItem(headlineContent = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clickable { viewModel.deleteFiles(setOf(fileOptionsSelected!!.file)); fileOptionsSelected = null })
                } else {
                    ListItem(headlineContent = { Text("Move to Trash", color = MaterialTheme.colorScheme.error) }, leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clickable { viewModel.moveToTrash(setOf(fileOptionsSelected!!.file)); fileOptionsSelected = null })
                }
            }
        }
    }
}

@Composable
fun ViewModeOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(8.dp)) {
        Icon(icon, contentDescription = label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(4.dp))
        if(selected) {
            Box(Modifier.height(3.dp).width(40.dp).background(MaterialTheme.colorScheme.primary))
        } else {
            Box(Modifier.height(3.dp).width(40.dp).background(Color.Transparent))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SortModeOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(8.dp)) {
        Icon(icon, contentDescription = label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(4.dp))
        if(selected) {
            Box(Modifier.height(3.dp).width(40.dp).background(MaterialTheme.colorScheme.primary))
        } else {
            Box(Modifier.height(3.dp).width(40.dp).background(Color.Transparent))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun DrawerContent(viewModel: FileManagerViewModel, onNavigate: () -> Unit, onOpenAnalyzer: () -> Unit = {}, onOpenAppManager: () -> Unit = {}, onOpenMediaManager: () -> Unit = {}, onOpenNetworkManager: () -> Unit = {}, onOpenSettings: () -> Unit = {}) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val storageVolumes by viewModel.storageVolumes.collectAsStateWithLifecycle()
    val currentCategory by viewModel.currentCategory.collectAsStateWithLifecycle()
    
    ModalDrawerSheet {
        LazyColumn(modifier = Modifier.padding(vertical = 16.dp)) {
            item {
                Text("DEV MANAGER", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.primary)
                HorizontalDivider()
                
                Text("Storage", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
            }
            
            items(storageVolumes) { volume ->
                val icon = if (volume.isRemovable) Icons.Default.SdCard else Icons.Default.Storage
                NavigationDrawerItem(
                    label = { 
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(volume.name, style = MaterialTheme.typography.labelLarge)
                            if (volume.totalSpace > 0) {
                                val used = volume.totalSpace - volume.freeSpace
                                val progress = used.toFloat() / volume.totalSpace.toFloat()
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(viewModel.formatSize(used), style = MaterialTheme.typography.labelSmall)
                                    Text(viewModel.formatSize(volume.totalSpace), style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                LinearProgressIndicator(
                                    progress = { progress }, 
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    },
                    icon = { Icon(icon, null) },
                    selected = false,
                    onClick = { viewModel.navigateTo(volume.path); onNavigate() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
            
            item {
                HorizontalDivider()
                
                Text("Library", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                NavigationDrawerItem(label = { Text("Downloads") }, icon = { Icon(Icons.Default.Download, null) }, selected = false, onClick = { viewModel.navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath); onNavigate() }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                NavigationDrawerItem(label = { Text("Images") }, icon = { Icon(Icons.Default.Image, null) }, selected = currentCategory == MediaCategory.IMAGES, onClick = { viewModel.selectCategory(MediaCategory.IMAGES); onNavigate() }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                NavigationDrawerItem(label = { Text("Videos") }, icon = { Icon(Icons.Default.Movie, null) }, selected = currentCategory == MediaCategory.VIDEOS, onClick = { viewModel.selectCategory(MediaCategory.VIDEOS); onNavigate() }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                NavigationDrawerItem(label = { Text("Music") }, icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }, selected = currentCategory == MediaCategory.MUSIC, onClick = { viewModel.selectCategory(MediaCategory.MUSIC); onNavigate() }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                NavigationDrawerItem(label = { Text("Documents") }, icon = { Icon(Icons.AutoMirrored.Filled.Article, null) }, selected = false, onClick = { viewModel.navigateTo(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath); onNavigate() }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                
                HorizontalDivider()
                
                Text("Tools", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                NavigationDrawerItem(label = { Text("App Manager") }, icon = { Icon(Icons.Default.Apps, null) }, selected = false, onClick = onOpenAppManager, modifier = Modifier.padding(horizontal = 12.dp))
                NavigationDrawerItem(label = { Text("Storage Analyzer") }, icon = { Icon(Icons.Default.Analytics, null) }, selected = false, onClick = onOpenAnalyzer, modifier = Modifier.padding(horizontal = 12.dp))
                NavigationDrawerItem(label = { Text("Media Manager") }, icon = { Icon(Icons.Default.PermMedia, null) }, selected = false, onClick = onOpenMediaManager, modifier = Modifier.padding(horizontal = 12.dp))
                NavigationDrawerItem(label = { Text("Network Center") }, icon = { Icon(Icons.Default.Public, null) }, selected = false, onClick = onOpenNetworkManager, modifier = Modifier.padding(horizontal = 12.dp))
                NavigationDrawerItem(label = { Text("Trash Bin") }, icon = { Icon(Icons.Default.Delete, null) }, selected = false, onClick = { viewModel.navigateTo(viewModel.trashDirPath); onNavigate() }, modifier = Modifier.padding(horizontal = 12.dp))
                NavigationDrawerItem(label = { Text("Settings") }, icon = { Icon(Icons.Default.Settings, null) }, selected = false, onClick = onOpenSettings, modifier = Modifier.padding(horizontal = 12.dp))
                
                if (bookmarks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Text("Bookmarks", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
            }
            
            items(bookmarks.toList()) { bmPath ->
                val bmFile = File(bmPath)
                NavigationDrawerItem(label = { Text(bmFile.name) }, icon = { Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_custom_folder), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp)) }, selected = false, onClick = { viewModel.navigateTo(bmPath); onNavigate() }, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(viewModel: FileManagerViewModel) {
    val file by viewModel.textEditorFile.collectAsStateWithLifecycle()
    val content by viewModel.textEditorContent.collectAsStateWithLifecycle()
    
    BackHandler { viewModel.closeTextEditor() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file?.name ?: "Text Editor") },
                navigationIcon = { IconButton(onClick = { viewModel.closeTextEditor() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close") } },
                actions = { IconButton(onClick = { viewModel.saveTextFile() }) { Icon(Icons.Default.Save, "Save") } }
            )
        }
    ) { padding ->
        TextField(
            value = content,
            onValueChange = { viewModel.updateTextEditorContent(it) },
            modifier = Modifier.fillMaxSize().padding(padding),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}

fun handleFileClick(context: android.content.Context, item: FileItem, viewModel: FileManagerViewModel) {
    if (item.isDirectory) {
        viewModel.navigateTo(item.file.absolutePath)
    } else {
        val extension = item.extension.lowercase()
        val imageExtensions = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        val docExtensions = listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx")
        
        if (extension in imageExtensions) {
            viewModel.openImageViewer(item.file)
        } else if (extension in docExtensions) {
            viewModel.openDocumentViewer(item.file)
        } else if (extension in listOf("txt", "log", "md", "csv", "json")) {
            viewModel.openTextEditor(item.file)
        } else if (extension == "apk") {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(i)
                        android.widget.Toast.makeText(context, "Grant unknown source installation permission and try again", android.widget.Toast.LENGTH_LONG).show()
                        return
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", item.file)
                val i = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(i)
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Failed to start APK installer : ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", item.file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, getMimeType(item.file.absolutePath))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

fun shareFile(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(file.absolutePath) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share File"))
    } catch (e: Exception) {}
}

fun shareMultiple(context: android.content.Context, files: List<File>) {
    try {
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.provider", it) }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Files"))
    } catch (e: Exception) {}
}

fun getMimeType(url: String): String? {
    var type: String? = null
    val extension = MimeTypeMap.getFileExtensionFromUrl(url.replace(" ", "%20"))
    if (extension != null) {
        type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    }
    return type ?: "*/*"
}

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_custom_folder), contentDescription = null, modifier = Modifier.size(72.dp), tint = Color.Unspecified)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Storage Access Required", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("This file manager needs full access to your device storage to read, move, and manage your files.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Grant Permission", fontSize = 16.sp) }
        }
    }
}

@Composable
fun PathBreadcrumbs(currentPath: String, storageRoot: String, rootName: String, onPathSelect: (String) -> Unit) {
    var relPath = currentPath.removePrefix(storageRoot)
    if (relPath.startsWith("/")) relPath = relPath.substring(1)
    val parts = if (relPath.isEmpty()) emptyList() else relPath.split("/")
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(parts.size) {
        if (parts.isNotEmpty() || listState.firstVisibleItemIndex > 0) {
            listState.animateScrollToItem(parts.size)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().padding(end = 4.dp), 
        verticalAlignment = Alignment.CenterVertically
    ) {
        item { BreadcrumbItem(rootName, onClick = { onPathSelect(storageRoot) }) }
        items(parts.size) { index ->
            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            val subPath = storageRoot + "/" + parts.take(index + 1).joinToString("/")
            BreadcrumbItem(parts[index], onClick = { onPathSelect(subPath) })
        }
    }
}

@Composable
fun BreadcrumbItem(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = Color.Transparent) {
        Text(text = label, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(item: FileItem, isSelected: Boolean, isSelectMode: Boolean, isCompact: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onMoreClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = if (isCompact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = 8.dp))
        }
        
        FileIconBox(item = item, size = if (isCompact) 32.dp else 40.dp, iconSize = if (isCompact) 18.dp else 24.dp)
        
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name, 
                    style = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, 
                    fontWeight = FontWeight.Medium, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isCompact && !item.isDirectory && item.extension.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                        Text(text = item.extension.uppercase(), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 9.sp)
                    }
                }
            }
            if (!isCompact) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!item.isDirectory || item.sizeLabel != "0 items") {
                            Text(text = item.sizeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!item.isDirectory && (item.extension.isNotBlank() || item.resolution != null)) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                        if (!item.isDirectory && item.extension.isNotBlank()) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                                Text(text = item.extension.uppercase(), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 9.sp)
                            }
                        }
                        if (item.resolution != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
                                Text(text = item.resolution, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 9.sp)
                            }
                        }
                    }
                    Text(text = item.lastModifiedLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
            }
        }
        if (!isSelectMode) {
            IconButton(onClick = onMoreClick) { Icon(Icons.Default.MoreVert, "More Options", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(item: FileItem, isSelected: Boolean, isSelectMode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FileIconBox(item = item, size = 64.dp, iconSize = 32.dp)
            if (isSelectMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.offset(y = (-12).dp).scale(0.8f))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(text = item.name, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun FileIconBox(item: FileItem, size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp) {
    val imageExtensions = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    val videoExtensions = listOf("mp4", "mkv", "avi", "mov", "webm", "3gp")
    val audioExtensions = listOf("mp3", "flac", "wav", "ogg", "m4a", "aac")
    val isMedia = item.extension.lowercase() in (imageExtensions + videoExtensions + audioExtensions)

    if (!item.isDirectory && isMedia) {
        coil.compose.AsyncImage(
            model = item.file,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(size).clip(if (size > 50.dp) RoundedCornerShape(8.dp) else CircleShape)
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(if (item.isDirectory) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (item.isDirectory) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_custom_folder),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(size * 0.75f) // Reduced size by 25% for a more professional look
                )
            } else {
                Icon(
                    imageVector = getFileIcon(item), 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.onSecondaryContainer, 
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

fun getFileIcon(item: FileItem) = when (item.extension) {
    "mp4", "mkv", "avi", "mov", "webm", "flv" -> Icons.Default.VideoLibrary
    "mp3", "wav", "m4a", "flac", "ogg", "aac" -> Icons.Default.AudioFile
    "pdf" -> Icons.Default.PictureAsPdf
    "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
    "doc", "docx", "txt", "rtf", "md", "csv", "log" -> Icons.Default.Description
    "apk" -> Icons.Default.Android
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(viewModel: FileManagerViewModel, file: File) {
    BackHandler {
        viewModel.closeImageViewer()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeImageViewer() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            coil.compose.AsyncImage(
                model = file,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }
    }
}
