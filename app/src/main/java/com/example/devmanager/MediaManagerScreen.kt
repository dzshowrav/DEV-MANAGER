package com.example.devmanager

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaManagerScreen(
    viewModel: MediaManagerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val images by viewModel.images.collectAsStateWithLifecycle()
    val imageAlbums by viewModel.imageAlbums.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val videoAlbums by viewModel.videoAlbums.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Images, 1: Videos, 2: Music, 3: Playlists
    var activeAlbumName by remember { mutableStateOf<String?>(null) } // Album filter
    
    // Feature Detail Views
    var activeImageIndex by remember { mutableStateOf<Int?>(null) }
    var showMetadataItem by remember { mutableStateOf<MediaItem?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var activePlaylistDetails by remember { mutableStateOf<Playlist?>(null) }

    fun playMediaWithIntent(item: MediaItem) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(item.uri, item.mimeType)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "No app found to play this media.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler {
        if (activeImageIndex != null) {
            activeImageIndex = null
        } else if (activeAlbumName != null) {
            activeAlbumName = null
        } else if (activePlaylistDetails != null) {
            activePlaylistDetails = null
        } else {
            onBack()
        }
    }

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
                    Text(
                        if (activeAlbumName != null) "Album: $activeAlbumName" else "Media Center"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeAlbumName != null) {
                            activeAlbumName = null
                        } else if (activePlaylistDetails != null) {
                            activePlaylistDetails = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAllMedia() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                }
            )
        },
        bottomBar = {
            if (activePlaylistDetails == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; activeAlbumName = null },
                        icon = { Icon(Icons.Default.Image, contentDescription = null) },
                        label = { Text("Images") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; activeAlbumName = null },
                        icon = { Icon(Icons.Default.Movie, contentDescription = null) },
                        label = { Text("Videos") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2; activeAlbumName = null },
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                        label = { Text("Music") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3; activeAlbumName = null },
                        icon = { Icon(Icons.Default.QueueMusic, contentDescription = null) },
                        label = { Text("Playlists") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Discovering media files...")
                    }
                }
            } else {
                when (selectedTab) {
                    0 -> { // Images tab
                        if (activeAlbumName != null) {
                            val albumItems = imageAlbums.find { it.name == activeAlbumName }?.items ?: emptyList()
                            ImageGridView(
                                items = albumItems,
                                onSelect = { item ->
                                    val idx = images.indexOfFirst { it.id == item.id }
                                    if (idx != -1) activeImageIndex = idx
                                },
                                onInfo = { showMetadataItem = it }
                            )
                        } else {
                            ImageAlbumnsView(
                                albums = imageAlbums,
                                allItems = images,
                                onAlbumSelect = { activeAlbumName = it.name },
                                onSelectAll = { activeImageIndex = 0 }
                            )
                        }
                    }
                    1 -> { // Videos tab
                        if (activeAlbumName != null) {
                            val albumItems = videoAlbums.find { it.name == activeAlbumName }?.items ?: emptyList()
                            VideoGridView(
                                items = albumItems,
                                onSelect = { playMediaWithIntent(it) },
                                onInfo = { showMetadataItem = it }
                            )
                        } else {
                            VideoAlbumsView(
                                albums = videoAlbums,
                                onAlbumSelect = { activeAlbumName = it.name }
                            )
                        }
                    }
                    2 -> { // Music tab
                        SoundTracksListView(
                            tracks = audioTracks,
                            onPlayTrack = { playMediaWithIntent(it) },
                            onInfo = { showMetadataItem = it },
                            playlists = playlists,
                            onAddToPlaylist = { playlist, track ->
                                viewModel.addTrackToPlaylist(playlist.id, track.path)
                            }
                        )
                    }
                    3 -> { // Playlists tab
                        if (activePlaylistDetails != null) {
                            PlaylistDetailScreen(
                                playlist = activePlaylistDetails!!,
                                allTracks = audioTracks,
                                onPlayTrack = { playMediaWithIntent(it) },
                                onRemoveTrack = { path ->
                                    viewModel.removeTrackFromPlaylist(activePlaylistDetails!!.id, path)
                                    // Refresh specific playlists item detail view state
                                    val updated = playlists.find { it.id == activePlaylistDetails!!.id }
                                    if (updated != null) {
                                        activePlaylistDetails = updated
                                    } else {
                                        activePlaylistDetails = null
                                    }
                                },
                                onBack = { activePlaylistDetails = null }
                            )
                        } else {
                            PlaylistsDashboardView(
                                playlists = playlists,
                                allTracks = audioTracks,
                                onCreatePlaylist = { showCreatePlaylistDialog = true },
                                onOpenPlaylist = { activePlaylistDetails = it },
                                onDeletePlaylist = { viewModel.deletePlaylist(it.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Image Interactive Slideshow Visualizer
    if (activeImageIndex != null) {
        ImageSlideshowVisualizer(
            items = images,
            startIndex = activeImageIndex!!,
            onDismiss = { activeImageIndex = null },
            onInfo = { showMetadataItem = it }
        )
    }

    // Detailed Metadata Specs Dialog Display
    if (showMetadataItem != null) {
        MediaMetadataDialog(
            item = showMetadataItem!!,
            viewModel = viewModel,
            onDismiss = { showMetadataItem = null }
        )
    }

    // Dialog Create Playlist
    if (showCreatePlaylistDialog) {
        var pName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = pName,
                    onValueChange = { pName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createPlaylist(pName)
                        showCreatePlaylistDialog = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ------------------- IMAGES SYSTEM VIEWS -------------------

@Composable
fun ImageAlbumnsView(
    albums: List<AlbumItem>,
    allItems: List<MediaItem>,
    onAlbumSelect: (AlbumItem) -> Unit,
    onSelectAll: () -> Unit
) {
    if (albums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No photos available on device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Option All Items Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable { onSelectAll() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("All Photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${allItems.size} images matches scan directories", style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }

        Text("Photo Albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums) { alb ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumSelect(alb) }
                ) {
                    Column {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(alb.items.first().path)
                                .crossfade(true)
                                .build(),
                            contentDescription = alb.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(alb.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${alb.itemCount} photos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageGridView(
    items: List<MediaItem>,
    onSelect: (MediaItem) -> Unit,
    onInfo: (MediaItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.path)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onSelect(item) }
                )
                // Short Metadata icon overlay
                IconButton(
                    onClick = { onInfo(item) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(28.dp)
                        .padding(4.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ------------------- VIDEOS TAB VIEWS -------------------

@Composable
fun VideoAlbumsView(
    albums: List<AlbumItem>,
    onAlbumSelect: (AlbumItem) -> Unit
) {
    if (albums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No videos detected on local storage.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Video Albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums) { alb ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumSelect(alb) }
                ) {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(alb.items.first().path)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = alb.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = Color.White.copy(alpha=0.8f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(alb.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${alb.itemCount} clips", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoGridView(
    items: List<MediaItem>,
    onSelect: (MediaItem) -> Unit,
    onInfo: (MediaItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(item) }
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.path)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = java.text.SimpleDateFormat("mm:ss", java.util.Locale.US).format(java.util.Date(item.duration)),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        IconButton(
                            onClick = { onInfo(item) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(32.dp)
                                .padding(4.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.White)
                        }
                    }
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(item.displayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ------------------- MUSIC MUSIC SOUND VIEWS -------------------

@Composable
fun SoundTracksListView(
    tracks: List<MediaItem>,
    onPlayTrack: (MediaItem) -> Unit,
    onInfo: (MediaItem) -> Unit,
    playlists: List<Playlist>,
    onAddToPlaylist: (Playlist, MediaItem) -> Unit
) {
    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No music elements detected in device storage directories.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var selectedTrackForPlaylist by remember { mutableStateOf<MediaItem?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tracks) { song ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    onClick = { onPlayTrack(song) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist ?: "Unknown Artist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        IconButton(onClick = { selectedTrackForPlaylist = song }) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to playlist")
                        }

                        IconButton(onClick = { onInfo(song) }) {
                            Icon(Icons.Default.Info, contentDescription = "Specs")
                        }
                    }
                }
            }
        }

        // Add track bottom sheet or selector popup dialog
        if (selectedTrackForPlaylist != null) {
            Dialog(
                onDismissRequest = { selectedTrackForPlaylist = null }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("Add to Playlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                        if (playlists.isEmpty()) {
                            Text("No playlists configured. Create one in the Playlists tab.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            playlists.forEach { play ->
                                Text(
                                    text = play.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onAddToPlaylist(play, selectedTrackForPlaylist!!)
                                            selectedTrackForPlaylist = null
                                        }
                                        .padding(vertical = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                HorizontalDivider()
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = { selectedTrackForPlaylist = null },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }
}

// ------------------- PLAYLIST DASHBOARD & SPECIFICS -------------------

@Composable
fun PlaylistsDashboardView(
    playlists: List<Playlist>,
    allTracks: List<MediaItem>,
    onCreatePlaylist: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreatePlaylist) {
                Icon(Icons.Default.Add, contentDescription = "Create Playlist")
            }
        }
    ) { paddingVals ->
        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingVals),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.3f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No configured playlists", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Assemble files custom lists by creating one", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingVals),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(playlists) { play ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPlaylist(play) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(play.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${play.trackPaths.size} tracks total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onDeletePlaylist(play) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    allTracks: List<MediaItem>,
    onPlayTrack: (MediaItem) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onBack: () -> Unit
) {
    // Collect the files matches in list
    val matchedTracks = remember(playlist, allTracks) {
        playlist.trackPaths.mapNotNull { path ->
            allTracks.find { it.path == path }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(playlist.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Playlist • ${matchedTracks.size} tracks available", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (matchedTracks.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No songs details matched. Navigate to music tab to load files.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(matchedTracks) { song ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        onClick = { onPlayTrack(song) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(song.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist ?: "Unknown Artist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            IconButton(onClick = { onRemoveTrack(song.path) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------- IMMERSIVE SLIDESHOW ENGINE -------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageSlideshowVisualizer(
    items: List<MediaItem>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onInfo: (MediaItem) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { items.size })
    var autoplaySeconds by remember { mutableIntStateOf(0) } // 0 means false, otherwise seconds index
    var isAutoplayRunning by remember { mutableStateOf(false) }

    // Auto advancing handler coroutine
    LaunchedEffect(isAutoplayRunning, autoplaySeconds) {
        if (isAutoplayRunning && autoplaySeconds > 0) {
            while (isAutoplayRunning) {
                delay(autoplaySeconds * 1000L)
                val target = if (pagerState.currentPage < items.size - 1) {
                    pagerState.currentPage + 1
                } else {
                    0
                }
                pagerState.animateScrollToPage(target)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Main Photo Pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val item = items[page]
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.path)
                                .build(),
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Control Top Toolbar overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    val currentItem = items.getOrNull(pagerState.currentPage)
                    if (currentItem != null) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${items.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Row {
                        IconButton(onClick = { currentItem?.let { onInfo(it) } }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                        }
                    }
                }

                // Bottom Overlay: Control Slidershow Controls
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (pagerState.currentPage > 0) pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = Color.White)
                            }

                            // Autoplay Play/Pause Button
                            FilledIconButton(
                                onClick = {
                                    if (autoplaySeconds == 0) {
                                        autoplaySeconds = 3 // default 3s setup
                                    }
                                    isAutoplayRunning = !isAutoplayRunning
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isAutoplayRunning) Color.Red else Color.Green,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = if (isAutoplayRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Autoplay"
                                )
                            }

                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (pagerState.currentPage < items.size - 1) pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Triggering autoplay frequency indices
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Slideshow Delay:", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            val options = listOf(2, 5, 10)
                            options.forEach { opt ->
                                FilterChip(
                                    selected = autoplaySeconds == opt,
                                    onClick = { 
                                        autoplaySeconds = opt 
                                        isAutoplayRunning = true
                                    },
                                    label = { Text("${opt}s") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        labelColor = Color.White,
                                        selectedLabelColor = Color.Black,
                                        selectedLeadingIconColor = Color.Black
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------- AUXILIARY COMPONENT STATICS -------------------

@Composable
fun MediaMetadataDialog(
    item: MediaItem,
    viewModel: MediaManagerViewModel,
    onDismiss: () -> Unit
) {
    val metaBytes = remember(item) { viewModel.extractRichMetadata(item) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Media Metadata Specs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    metaBytes.forEach { (key, value) ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(key, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSecs = durationMs / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
}

private fun sizeIndex(size: androidx.compose.ui.unit.Dp): androidx.compose.ui.Modifier {
    return androidx.compose.ui.Modifier.size(size)
}
