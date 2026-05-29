package com.example.devmanager

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun MXPlayerDialog(
    mediaPath: String,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var lastSavedPos by rememberSaveable { mutableLongStateOf(0L) }
    
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val view = LocalView.current
        val dialogWindow = remember(view) {
            var parent = view.parent
            while (parent != null && parent !is androidx.compose.ui.window.DialogWindowProvider) {
                parent = parent.parent
            }
            (parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        }

        LaunchedEffect(dialogWindow) {
            dialogWindow?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            MXPlayerContent(
                exoPlayer = exoPlayer,
                title = title,
                onDismiss = onDismiss,
                onPositionChange = { lastSavedPos = it },
                dialogWindow = dialogWindow
            )
        }
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

    // Keep UI metrics in sync
    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChange: Boolean) {
                isPlaying = isPlayingChange
            }
        }
        exoPlayer.addListener(listener)
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
            delay(3000)
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
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
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
                        .padding(16.dp),
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
                        modifier = Modifier.weight(1f)
                    )
                }

                // Center Play/Pause
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), color = Color.White)
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
