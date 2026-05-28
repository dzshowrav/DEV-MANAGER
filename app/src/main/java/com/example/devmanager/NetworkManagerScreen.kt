package com.example.devmanager

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.text.style.TextAlign
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkManagerScreen(
    viewModel: NetworkManagerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val connectedProfile by viewModel.connectedProfile.collectAsStateWithLifecycle()
    val remoteFiles by viewModel.remoteFiles.collectAsStateWithLifecycle()
    val currentRemotePath by viewModel.currentRemotePath.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanningProgress by viewModel.scanningProgress.collectAsStateWithLifecycle()
    val discoveredServers by viewModel.discoveredServers.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var editProfileActive by remember { mutableStateOf<NetProfile?>(null) }
    var showScanDialog by remember { mutableStateOf(false) }

    // Text Editor state
    var editingFileState by remember { mutableStateOf<RemoteFile?>(null) }
    var fileContentState by remember { mutableStateOf("") }
    var isSavingTextState by remember { mutableStateOf(false) }

    // Media stream state
    var streamingVideoFile by remember { mutableStateOf<RemoteFile?>(null) }
    var streamingAudioFile by remember { mutableStateOf<RemoteFile?>(null) }

    // Toast Collector
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            if (msg.isNotEmpty()) {
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Network Center", fontWeight = FontWeight.Bold)
                        if (connectedProfile != null) {
                            Text(
                                text = "Active: ${connectedProfile!!.label} (${currentRemotePath})",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text("LAN Discovery & Multi-Protocols", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (connectedProfile != null) {
                            if (currentRemotePath != "/" && currentRemotePath.isNotEmpty()) {
                                val parent = currentRemotePath.substringBeforeLast("/")
                                val resolvedParent = if (parent.isEmpty()) "/" else parent
                                viewModel.loadRemoteFiles(resolvedParent)
                            } else {
                                viewModel.disconnect()
                            }
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (connectedProfile == null) {
                        IconButton(onClick = { showScanDialog = true; viewModel.startNetworkDiscovery() }) {
                            Icon(Icons.Default.Radar, contentDescription = "Scan LAN Subnet")
                        }
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Profile")
                        }
                    } else {
                        IconButton(onClick = { viewModel.loadRemoteFiles(currentRemotePath) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { viewModel.disconnect() }) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = "Disconnect", tint = Color.Red)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isConnecting) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeWidth = 4.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Shaking hands with host server...", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else if (connectedProfile != null) {
                // ACTIVE REMOTE FILE EXPLORER VIEW
                Column(modifier = Modifier.fillMaxSize()) {
                    // Current Path Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentRemotePath,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Directory Tools / Actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var isFolderNamePromptOpen by remember { mutableStateOf(false) }
                        if (isFolderNamePromptOpen) {
                            var proposedFolderName by remember { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = { isFolderNamePromptOpen = false },
                                title = { Text("New Remote Directory") },
                                text = {
                                    OutlinedTextField(
                                        value = proposedFolderName,
                                        onValueChange = { proposedFolderName = it },
                                        label = { Text("Directory Name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (proposedFolderName.isNotEmpty()) {
                                                viewModel.createRemoteDirectory(proposedFolderName)
                                                isFolderNamePromptOpen = false
                                            }
                                        }
                                    ) {
                                        Text("Create")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { isFolderNamePromptOpen = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }

                        Button(
                            onClick = { isFolderNamePromptOpen = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New Folder")
                        }
                    }

                    // File List Pane
                    if (remoteFiles.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.3f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No storage nodes found inside this path.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(remoteFiles) { rf ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (rf.isDirectory) MaterialTheme.colorScheme.secondaryContainer.copy(alpha=0.6f) else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (rf.isDirectory) {
                                                    viewModel.loadRemoteFiles(rf.path)
                                                } else {
                                                    // File open options (Edit or Stream)
                                                    val ext = rf.extension.lowercase(Locale.ROOT)
                                                    if (ext in listOf("txt", "json", "xml", "yaml", "ini", "properties", "gradle", "kt", "kt", "html", "css", "md", "js", "sh")) {
                                                        scope.launch {
                                                            isSavingTextState = true
                                                            editingFileState = rf
                                                            fileContentState = viewModel.readRemoteText(rf)
                                                            isSavingTextState = false
                                                        }
                                                    } else if (ext in listOf("mp4", "mkv", "3gp", "avi")) {
                                                        streamingVideoFile = rf
                                                    } else if (ext in listOf("mp3", "wav", "m4a", "ogg")) {
                                                        streamingAudioFile = rf
                                                    } else {
                                                        android.widget.Toast.makeText(context, "${rf.name} cannot be open directly. Format unsupported.", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                rf.isDirectory -> Icons.Default.Folder
                                                rf.extension.lowercase() in listOf("mp3", "wav") -> Icons.Default.MusicNote
                                                rf.extension.lowercase() in listOf("mp4", "mkv") -> Icons.Default.Movie
                                                else -> Icons.Default.InsertDriveFile
                                            },
                                            contentDescription = null,
                                            tint = if (rf.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(28.dp)
                                        )

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = rf.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!rf.isDirectory) {
                                                Text(
                                                    text = "${viewModel.formatSize(rf.size)} • File type: ${rf.extension.uppercase()}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                Text(
                                                    text = "Directory",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        IconButton(onClick = { viewModel.deleteRemoteFile(rf) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete remote node", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // SAVED CONNECTIONS HOME SCREEN INDEX
                Column(modifier = Modifier.fillMaxSize()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.NetworkWifi, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Local Devices & Discovery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Map live servers and open active ports on your subnet layout.", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { showScanDialog = true; viewModel.startNetworkDiscovery() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Scan LAN", fontSize = 12.sp)
                            }
                        }
                    }

                    Text(
                        text = "Saved Remote Connections",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    if (profiles.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.3f))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("No servers configured", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Assemble profiles using FTP, SFTP, WebDAV, SMB or NFS protocols to sync assets.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(24.dp))
                                Button(onClick = { showCreateDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add Connection Profile")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(profiles) { prof ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.connectProfile(prof) }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = prof.protocol.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(prof.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = "${prof.host}:${prof.port}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        IconButton(onClick = { editProfileActive = prof }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = MaterialTheme.colorScheme.primary)
                                        }

                                        IconButton(onClick = { viewModel.deleteProfile(prof.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Profile", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // CREATE OR EDIT PROFILE ALERTVIEW MODEL DIRECT PORT
    if (showCreateDialog || editProfileActive != null) {
        val editingProfile = editProfileActive
        var label by remember { mutableStateOf(editingProfile?.label ?: "") }
        var protocol by remember { mutableStateOf(editingProfile?.protocol ?: NetProtocol.FTP) }
        var host by remember { mutableStateOf(editingProfile?.host ?: "") }
        var portStr by remember { mutableStateOf(editingProfile?.port?.toString() ?: "21") }
        var username by remember { mutableStateOf(editingProfile?.username ?: "") }
        var password by remember { mutableStateOf(editingProfile?.password ?: "") }
        var domain by remember { mutableStateOf(editingProfile?.domain ?: "") }
        var rootPath by remember { mutableStateOf(editingProfile?.remoteRoot ?: "/") }

        Dialog(
            onDismissRequest = { showCreateDialog = false; editProfileActive = null }
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (editingProfile != null) "Edit Connection" else "Add Remote Connection",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Profile Label (e.g., Linux SFTP)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Protocol selector dropdown row
                    Text("Protocol Setup:", fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NetProtocol.values().forEach { prot ->
                            FilterChip(
                                selected = protocol == prot,
                                onClick = {
                                    protocol = prot
                                    portStr = when (prot) {
                                        NetProtocol.FTP -> "21"
                                        NetProtocol.FTPS -> "21"
                                        NetProtocol.SFTP -> "22"
                                        NetProtocol.WEBDAV -> "80"
                                        NetProtocol.SMB -> "445"
                                        NetProtocol.NFS -> "2049"
                                    }
                                },
                                label = { Text(prot.name, fontSize = 10.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Host address") },
                            singleLine = true,
                            modifier = Modifier.weight(1.5f)
                        )
                        OutlinedTextField(
                            value = portStr,
                            onValueChange = { portStr = it },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (protocol == NetProtocol.SMB) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = domain,
                            onValueChange = { domain = it },
                            label = { Text("Domain / Workgroup") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = rootPath,
                        onValueChange = { rootPath = it },
                        label = { Text("Remote Root Path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showCreateDialog = false; editProfileActive = null }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (label.isNotEmpty() && host.isNotEmpty()) {
                                    val safePort = portStr.toIntOrNull() ?: 21
                                    val prof = NetProfile(
                                        id = editingProfile?.id ?: UUID.randomUUID().toString(),
                                        label = label,
                                        protocol = protocol,
                                        host = host,
                                        port = safePort,
                                        username = username,
                                        password = password,
                                        domain = domain,
                                        remoteRoot = rootPath
                                    )
                                    viewModel.saveProfile(prof)
                                    showCreateDialog = false
                                    editProfileActive = null
                                }
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    // SUBNET/LAN DISCOVERY RADAR DIALOG SHOWPORT
    if (showScanDialog) {
        Dialog(
            onDismissRequest = { showScanDialog = false }
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .height(480.dp)
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Subnet Scanner Active", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { viewModel.startNetworkDiscovery() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry Scan")
                            }
                        }
                    }

                    if (scanningProgress.isNotEmpty()) {
                        Text(
                            text = scanningProgress,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text("Active Hosts Found on LAN:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (discoveredServers.isEmpty() && !isScanning) {
                            item {
                                Text("No servers identified on subnet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            items(discoveredServers) { srv ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(srv.hostname, fontWeight = FontWeight.Bold)
                                            Text(srv.ip, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Open Protocols found:", style = MaterialTheme.typography.labelSmall)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            srv.detectedProtocols.forEach { prot ->
                                                SuggestionChip(
                                                    onClick = {
                                                        // Populate details directly into creation box
                                                        showScanDialog = false
                                                        val finalPort = when (prot) {
                                                            NetProtocol.FTP -> 21
                                                            NetProtocol.FTPS -> 21
                                                            NetProtocol.SFTP -> 22
                                                            NetProtocol.WEBDAV -> 80
                                                            NetProtocol.SMB -> 445
                                                            NetProtocol.NFS -> 2049
                                                        }
                                                        editProfileActive = NetProfile(
                                                            label = "Live ${srv.hostname} ${prot.name}",
                                                            protocol = prot,
                                                            host = srv.ip,
                                                            port = finalPort
                                                        )
                                                    },
                                                    label = { Text(prot.name, fontSize = 9.sp) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { showScanDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close Scan")
                    }
                }
            }
        }
    }

    // FULL REMOTE TEXT IDE / CODE EDITOR SHEET
    if (editingFileState != null && !isSavingTextState) {
        val rf = editingFileState!!
        var textValue by remember(fileContentState) { mutableStateOf(fileContentState) }

        Dialog(
            onDismissRequest = { editingFileState = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Editor TopBar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { editingFileState = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Editor")
                        }

                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                text = rf.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("Remote Code Syncing Enabled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }

                        Button(
                            onClick = {
                                viewModel.updateRemoteText(rf, textValue)
                                editingFileState = null
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Remote")
                        }
                    }

                    // Main Text area inside editor
                    TextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        placeholder = { Text("No content inside remote node") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp
                        )
                    )
                }
            }
        }
    }

    // EXOPLAYER SYSTEM STREAM PLAYERS
    if (streamingVideoFile != null) {
        VideoPlayerDialog(
            mediaItem = MediaItem(
                id = 0L,
                uri = Uri.parse(viewModel.getExoPlayerStreamUrl(streamingVideoFile!!)),
                path = viewModel.getExoPlayerStreamUrl(streamingVideoFile!!),
                displayName = streamingVideoFile!!.name,
                album = "Remote Video",
                artist = null,
                duration = 0L,
                size = streamingVideoFile!!.size,
                dateAdded = 0L,
                width = 0,
                height = 0,
                mimeType = "video/mp4"
            ),
            onDismiss = { streamingVideoFile = null }
        )
    }

    if (streamingAudioFile != null) {
        val rf = streamingAudioFile!!
        SoundPlayerOverlay(
            track = MediaItem(
                id = 0L,
                uri = Uri.parse(viewModel.getExoPlayerStreamUrl(rf)),
                path = viewModel.getExoPlayerStreamUrl(rf),
                displayName = rf.name,
                album = "Remote Audio",
                artist = "Remote Server Stream",
                duration = 0L,
                size = rf.size,
                dateAdded = 0L,
                width = 0,
                height = 0,
                mimeType = "audio/mpeg"
            ),
            allTracks = emptyList(),
            onDismiss = { streamingAudioFile = null },
            onNext = {},
            onPrev = {}
        )
    }
}
