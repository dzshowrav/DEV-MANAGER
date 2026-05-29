package com.example.devmanager

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.PlaybackParams
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun MXPlayerDialog(
    mediaPath: String,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var lastSavedPos by remember { mutableLongStateOf(0L) }
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(mediaPath)))
            if (lastSavedPos > 0L) {
                seekTo(lastSavedPos)
            }
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Capture hardware back button
    BackHandler(onBack = {
        onDismiss()
    })

    // Put it inside a Box that ignores all system Window insets and draws over everything
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // This is key: ignore all insets to support full immersive view
    ) {
        val window = (context as? Activity)?.window
        
        // When Player is active, we go immersive
        LaunchedEffect(window) {
            window?.let { w ->
                WindowCompat.setDecorFitsSystemWindows(w, false)
                val controller = WindowCompat.getInsetsController(w, w.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        
        MXPlayerContent(
            exoPlayer = exoPlayer,
            title = title,
            onDismiss = onDismiss,
            onPositionChange = { lastSavedPos = it },
            dialogWindow = window
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MXPlayerContent(
    exoPlayer: ExoPlayer,
    title: String,
    onDismiss: () -> Unit,
    onPositionChange: (Long) -> Unit,
    dialogWindow: android.view.Window?
) {
    val context = LocalContext.current
    val window = dialogWindow ?: (context as? Activity)?.window
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    // Zoom & Pan Setup
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Swipe gestures
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }
    
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0f) }
    
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableFloatStateOf(0f) }

    // Extended Features State
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Keep UI metrics in sync
    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChange: Boolean) {
                isPlaying = isPlayingChange
            }
            override fun onRepeatModeChanged(rMode: Int) {
                repeatMode = rMode
            }
        }
        exoPlayer.addListener(listener)
        exoPlayer.repeatMode = repeatMode
        while (true) {
            if (!isSeeking) {
                currentPosition = exoPlayer.currentPosition
                onPositionChange(currentPosition)
            }
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Toggle system bars based on controls visibility
    val view = LocalView.current
    LaunchedEffect(showControls, window) {
        window?.let { w ->
            val insetsController = WindowCompat.getInsetsController(w, view)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (showControls) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Restore activity system bars when player is fully exited
            (context as? Activity)?.window?.let { w ->
                val insetsController = WindowCompat.getInsetsController(w, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Tap gestures
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        if (offset.x > screenWidth / 2) {
                            // Forward 10s
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                        } else {
                            // Rewind 10s
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                        }
                    }
                )
            }
            // Transform & Drag Gestures (Combining Swipe and Zoom)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    // Handle Pinch-to-Zoom
                    if (zoom != 1f || scale > 1f) {
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        val maxOffsetX = (size.width * (scale - 1)) / 2f
                        val maxOffsetY = (size.height * (scale - 1)) / 2f
                        offsetX = (offsetX + pan.x * scale).coerceIn(-maxOffsetX, maxOffsetX)
                        offsetY = (offsetY + pan.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                        
                        // If scaled down back to 1, reset pan
                        if (scale == 1f) {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
            }
            // Swipe Control for Brightness, Volume, Seek
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (scale == 1f) { // Only do swipe controls if not zoomed
                            val screenWidth = size.width
                            val screenHeight = size.height
                            // We can use start offset to see what we are controlling
                        }
                    },
                    onDragEnd = {
                        if (isSeeking) {
                            exoPlayer.seekTo(seekPosition)
                            isSeeking = false
                        }
                        showVolumeIndicator = false
                        showBrightnessIndicator = false
                    },
                    onDragCancel = {
                        isSeeking = false
                        showVolumeIndicator = false
                        showBrightnessIndicator = false
                    },
                    onDrag = { change, dragAmount ->
                        if (scale > 1f) return@detectDragGestures // Pan is handled by transform

                        change.consume()
                        val isHorizontal = abs(dragAmount.x) > abs(dragAmount.y)

                        if (!isSeeking && !showVolumeIndicator && !showBrightnessIndicator) {
                            // Lock into a gesture type
                            if (isHorizontal) {
                                isSeeking = true
                                seekPosition = exoPlayer.currentPosition
                            } else {
                                if (change.position.x < size.width / 2) {
                                    showBrightnessIndicator = true
                                    brightnessLevel = window?.attributes?.screenBrightness ?: 0.5f
                                    if (brightnessLevel < 0) brightnessLevel = 0.5f // Default
                                } else {
                                    showVolumeIndicator = true
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                                }
                            }
                        }

                        if (isSeeking) {
                            val seekDiff = (dragAmount.x * 100).toLong() // 1 pixel = 100ms
                            seekPosition = (seekPosition + seekDiff).coerceIn(0L, duration)
                        } else if (showBrightnessIndicator) {
                            val brightnessDiff = -dragAmount.y / size.height
                            brightnessLevel = (brightnessLevel + brightnessDiff).coerceIn(0f, 1f)
                            window?.let {
                                val attrs = it.attributes
                                attrs.screenBrightness = brightnessLevel
                                it.attributes = attrs
                            }
                        } else if (showVolumeIndicator) {
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val volDiff = -dragAmount.y / size.height
                            volumeLevel = (volumeLevel + volDiff).coerceIn(0f, 1f)
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                (volumeLevel * maxVol).toInt(),
                                0
                            )
                        }
                    }
                )
            }
    ) {
        // Player Surface
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Custom controls
                    this.resizeMode = resizeMode
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
            }
        )

        // On-screen Indicators (Seek, Vol, Brightness)
        if (isSeeking) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "${formatTime(seekPosition)} / ${formatTime(duration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
        
        if (showVolumeIndicator) {
            Indicator(Icons.Default.VolumeUp, volumeLevel, Modifier.align(Alignment.Center))
        }

        if (showBrightnessIndicator) {
            Indicator(Icons.Default.BrightnessMedium, brightnessLevel, Modifier.align(Alignment.Center))
        }

        // Custom Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showControls = false }
                        )
                    }
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .pointerInput(Unit) {
                            detectTapGestures { /* consume tap */ }
                        }
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    
                    // Audio Track / Subtitles
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Outlined.Audiotrack, contentDescription = "Audio Tracks", tint = Color.White)
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Outlined.Subtitles, contentDescription = "Subtitles", tint = Color.White)
                    }
                    
                    // Speed Control
                    IconButton(onClick = {
                        playbackSpeed = if (playbackSpeed >= 2f) 0.5f else playbackSpeed + 0.25f
                        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color.White)
                            Text("${playbackSpeed}x", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // Settings Button
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Settings", tint = Color.White)
                    }
                }

                // Center Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .pointerInput(Unit) { detectTapGestures { /* consume tap */ } },
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { exoPlayer.seekToPreviousMediaItem() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    
                    IconButton(
                        onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.FastRewind, "Rewind 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    IconButton(
                        onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.FastForward, "Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    
                    IconButton(
                        onClick = { exoPlayer.seekToNextMediaItem() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }

                // Bottom Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .pointerInput(Unit) {
                            detectTapGestures { /* consume tap */ }
                        }
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatTime(currentPosition), color = Color.White)
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Repeat Mode
                            IconButton(onClick = {
                                repeatMode = when(repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                exoPlayer.repeatMode = repeatMode
                            }) {
                                val ri = when(repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                    else -> Icons.Default.Repeat
                                }
                                val rt = if(repeatMode == Player.REPEAT_MODE_OFF) Color.Gray else Color.White
                                Icon(ri, "Repeat", tint = rt)
                            }

                            // Crop / Mode
                            IconButton(onClick = {
                                resizeMode = when(resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            }) {
                                Icon(if(resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Resize", tint = Color.White)
                            }
                        }

                        Text(formatTime(duration), color = Color.White)
                    }
                    
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { exoPlayer.seekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.LightGray
                        )
                    )
                }
            }
        }
    }
    
    // 147 Features Checklist & Advanced Options Dialog
    if (showSettingsDialog) {
        UltimateFeaturesDialog(onDismiss = { showSettingsDialog = false })
    }
}

@Composable
fun Indicator(icon: androidx.compose.ui.graphics.vector.ImageVector, level: Float, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { level },
            modifier = Modifier.width(100.dp).height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.DarkGray
        )
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d", m, s)
}
