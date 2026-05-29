package com.example.devmanager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    viewModel: AppManagerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isScanningApks by viewModel.isScanningApks.collectAsStateWithLifecycle()
    val userApps by viewModel.userApps.collectAsStateWithLifecycle()
    val systemApps by viewModel.systemApps.collectAsStateWithLifecycle()
    val apkFiles by viewModel.apkFiles.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var activeAppDetails by remember { mutableStateOf<AppInfo?>(null) }
    var inSelectionMode by remember { mutableStateOf(false) }
    
    BackHandler {
        if (inSelectionMode) {
            inSelectionMode = false
            viewModel.clearSelection()
        } else if (activeAppDetails != null) {
            activeAppDetails = null
        } else {
            onBack()
        }
    }

    var launcherRef: androidx.activity.result.ActivityResultLauncher<Intent>? by remember { mutableStateOf(null) }

    // Launcher for handling standard uninstallation and batch uninstallations
    val uninstallLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (inSelectionMode) {
            launcherRef?.let { viewModel.processNextUninstall(context, it) }
        } else {
            viewModel.loadApps()
        }
    }

    LaunchedEffect(uninstallLauncher) {
        launcherRef = uninstallLauncher
    }

    // Handle toast messages
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            if (msg.isNotEmpty()) {
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Refresh apps when tab shifts or initially
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 2 && apkFiles.isEmpty()) {
            viewModel.scanApkFiles()
        } else {
            viewModel.loadApps()
        }
    }

    val filteredUserApps = remember(userApps, searchQuery) {
        if (searchQuery.isEmpty()) userApps else {
            userApps.filter { it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
        }
    }

    val filteredSystemApps = remember(systemApps, searchQuery) {
        if (searchQuery.isEmpty()) systemApps else {
            systemApps.filter { it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
        }
    }

    val filteredApkFiles = remember(apkFiles, searchQuery) {
        if (searchQuery.isEmpty()) apkFiles else {
            apkFiles.filter { it.label.contains(searchQuery, ignoreCase = true) || (it.packageName ?: "").contains(searchQuery, ignoreCase = true) }
        }
    }

    // Toggle batch selection mode automatically
    LaunchedEffect(selectedPackages) {
        if (selectedPackages.isEmpty()) {
            inSelectionMode = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Manager") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (inSelectionMode) {
                            viewModel.clearSelection()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = if (inSelectionMode) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (inSelectionMode) {
                        IconButton(onClick = {
                            val allList = if (selectedTabIndex == 0) filteredUserApps else filteredSystemApps
                            allList.forEach { viewModel.toggleSelection(it.packageName) }
                        }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                    } else {
                        IconButton(onClick = {
                            if (selectedTabIndex == 2) {
                                viewModel.scanApkFiles()
                            } else {
                                viewModel.loadApps()
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = inSelectionMode && selectedPackages.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedPackages.size} Apps Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.batchExtractSelected() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Backup", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Backup")
                            }
                            if (selectedTabIndex == 0) {
                                Button(
                                    onClick = {
                                        viewModel.startBatchUninstall(selectedPackages.toList(), context, uninstallLauncher)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Uninstall", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Uninstall")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Search View
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search apps or package names...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Dynamic Tabs
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0; viewModel.clearSelection() },
                    text = { Text("User Apps") },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1; viewModel.clearSelection() },
                    text = { Text("System Apps") },
                    icon = { Icon(Icons.Default.SettingsApplications, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2; viewModel.clearSelection() },
                    text = { Text("APKs") },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                )
            }

            // Main Content Body based on selection
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading || isScanningApks) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(if (isScanningApks) "Scanning storage for APKs..." else "Loading applications...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    when (selectedTabIndex) {
                        0 -> AppListTabContent(
                            apps = filteredUserApps,
                            selectedPackages = selectedPackages,
                            inSelectionMode = inSelectionMode,
                            onToggleSelect = { pkg ->
                                if (!inSelectionMode) inSelectionMode = true
                                viewModel.toggleSelection(pkg)
                            },
                            onAppSelect = { activeAppDetails = it },
                            viewModel = viewModel
                        )
                        1 -> AppListTabContent(
                            apps = filteredSystemApps,
                            selectedPackages = selectedPackages,
                            inSelectionMode = inSelectionMode,
                            onToggleSelect = { pkg ->
                                if (!inSelectionMode) inSelectionMode = true
                                viewModel.toggleSelection(pkg)
                            },
                            onAppSelect = { activeAppDetails = it },
                            viewModel = viewModel
                        )
                        2 -> LocalApkTabContent(
                            apkFiles = filteredApkFiles,
                            onInstall = { apk ->
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        if (!context.packageManager.canRequestPackageInstalls()) {
                                            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(i)
                                            android.widget.Toast.makeText(context, "Grant unknown source installation permission and try again", android.widget.Toast.LENGTH_LONG).show()
                                            return@LocalApkTabContent
                                        }
                                    }

                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        apk.file
                                    )
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
                            },
                            onShare = { apk ->
                                val i = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/vnd.android.package-archive"
                                    putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.provider", apk.file))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(i, "Share APK"))
                            },
                            onDelete = { viewModel.deleteApkFile(it) },
                            onScanRefresh = { viewModel.scanApkFiles() },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    // Modal dialogue for complete diagnostic specifications
    if (activeAppDetails != null) {
        AppDetailsDialog(
            app = activeAppDetails!!,
            viewModel = viewModel,
            onDismiss = { activeAppDetails = null },
            uninstallLauncher = uninstallLauncher
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppListTabContent(
    apps: List<AppInfo>,
    selectedPackages: Set<String>,
    inSelectionMode: Boolean,
    onToggleSelect: (String) -> Unit,
    onAppSelect: (AppInfo) -> Unit,
    viewModel: AppManagerViewModel
) {
    if (apps.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No applications found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    FastScrollWrapper(
        listState = listState,
        labelProvider = { index -> apps.getOrNull(index)?.name ?: "" }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                val isSelected = selectedPackages.contains(app.packageName)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (inSelectionMode) {
                                onToggleSelect(app.packageName)
                            } else {
                                onAppSelect(app)
                            }
                        },
                        onLongClick = {
                            onToggleSelect(app.packageName)
                        }
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconBitmap = remember(app.packageName) { app.iconBitmap?.asImageBitmap() }
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = "App Icon",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Icon(
                            Icons.Default.Android,
                            contentDescription = "App Icon",
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "v${app.versionName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Text(
                                text = viewModel.formatSize(app.size),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    if (inSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect(app.packageName) },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    } else {
                        IconButton(onClick = { onAppSelect(app) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Details")
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun LocalApkTabContent(
    apkFiles: List<ApkFileItem>,
    onInstall: (ApkFileItem) -> Unit,
    onShare: (ApkFileItem) -> Unit,
    onDelete: (ApkFileItem) -> Unit,
    onScanRefresh: () -> Unit,
    viewModel: AppManagerViewModel
) {
    if (apkFiles.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No local APK files found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Scan directories downloads, documents, Bluetooth or device storage roots sequentially to discover installable application installers recursively.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(300.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onScanRefresh) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Device Storage")
                }
            }
        }
        return
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    FastScrollWrapper(
        listState = listState,
        labelProvider = { index -> apkFiles.getOrNull(index)?.label ?: "" }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apkFiles, key = { it.file.absolutePath }) { apk ->
                Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconBitmap = remember(apk.file.absolutePath) { apk.iconBitmap?.asImageBitmap() }
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = "APK Icon",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Icon(
                            Icons.Default.Android,
                            contentDescription = "APK Icon",
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = apk.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = apk.packageName ?: "Unknown Package",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "v${apk.versionName ?: "N/A"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Text(
                                text = viewModel.formatSize(apk.size),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            // App installation status
                            Text(
                                text = if (apk.isInstalled) "Installed" else "Not Installed",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (apk.isInstalled) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { onInstall(apk) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Download, contentDescription = "Install APK", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { onShare(apk) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "Share APK", tint = MaterialTheme.colorScheme.secondary)
                        }
                        IconButton(onClick = { onDelete(apk) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete APK", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun AppDetailsDialog(
    app: AppInfo,
    viewModel: AppManagerViewModel,
    onDismiss: () -> Unit,
    uninstallLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Custom Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconBmp = remember(app.packageName) { app.iconBitmap?.asImageBitmap() }
                    if (iconBmp != null) {
                        Image(
                            bitmap = iconBmp,
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Icon(
                            Icons.Default.Android,
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Version: ${app.versionName} (Build ${app.versionCode})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Specs specs lists and diagnostics scrolls
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Technical Highlights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    // Specs grid parameters
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpecDataCard(title = "App Size", value = viewModel.formatSize(app.size), icon = Icons.Default.InsertDriveFile, modifier = Modifier.weight(1f))
                        SpecDataCard(title = "Min SDK Compatibility", value = "Android ${app.minSdk}", icon = Icons.Default.VerifiedUser, modifier = Modifier.weight(1f))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpecDataCard(title = "Target Android", value = "API ${app.targetSdk}", icon = Icons.Default.Crop32, modifier = Modifier.weight(1f))
                        SpecDataCard(title = "Source Engine", value = if (app.isSystemApp) "System Config" else "User Package", icon = Icons.Default.Build, modifier = Modifier.weight(1f))
                    }

                    // Times log info
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("First Installed:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Text(viewModel.formatTime(app.installTime), style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Latest System Update:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Text(viewModel.formatTime(app.updateTime), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // Collapsible details of permissions
                    if (app.permissions.isNotEmpty()) {
                        Text("Requested Permissions (${app.permissions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                app.permissions.forEach { perm ->
                                    val shortPerm = perm.substringAfterLast(".")
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = shortPerm, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                // Operations CTA bar bottom UI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Try to launch launch intent
                    val canLaunch = context.packageManager.getLaunchIntentForPackage(app.packageName) != null
                    Button(
                        onClick = {
                            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                onDismiss()
                            }
                        },
                        enabled = canLaunch,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Launch")
                    }

                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${app.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "System Settings", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }

                    // Backup APK option
                    IconButton(
                        onClick = {
                            viewModel.extractApk(app)
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Backup App APK", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }

                    // Share APK option
                    IconButton(
                        onClick = {
                            viewModel.shareApk(app, context)
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share App APK", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }

                    // Uninstall option (hide or disable for system apps)
                    if (!app.isSystemApp) {
                        Button(
                            onClick = {
                                viewModel.uninstallApp(app.packageName, context, uninstallLauncher)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Uninstall")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpecDataCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
