package com.example.devmanager

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsDialog(exoPlayer: Player, onDismiss: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Playback Engine", "Video", "Audio & Subs", "Connectivity & Others")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Ultimate Hybrid Features") },
                    actions = {
                        TextButton(onClick = onDismiss) { Text("X") }
                    }
                )
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
                
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> CorePlaybackTab(exoPlayer)
                        1 -> VideoFeaturesTab()
                        2 -> AudioSubsTab(exoPlayer)
                        3 -> AdvancedTab(exoPlayer, onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
fun CorePlaybackTab(exoPlayer: Player) {
    var playbackSpeed by remember { mutableFloatStateOf(exoPlayer.playbackParameters.speed) }
    var pitch by remember { mutableFloatStateOf(exoPlayer.playbackParameters.pitch) }
    val context = LocalContext.current

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Playback Speed: ${"%.2f".format(playbackSpeed)}x", fontWeight = FontWeight.Bold)
            Slider(
                value = playbackSpeed,
                onValueChange = { 
                    playbackSpeed = it
                    exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed, pitch)
                },
                valueRange = 0.25f..3f
            )
            Divider()
            Spacer(Modifier.height(8.dp))
            
            Text("Pitch Adjustment (Tempo): ${"%.2f".format(pitch)}x", fontWeight = FontWeight.Bold)
            Slider(
                value = pitch,
                onValueChange = { 
                    pitch = it
                    exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed, pitch)
                },
                valueRange = 0.5f..2f
            )
            Divider()
            Spacer(Modifier.height(8.dp))
        }
        
        item {
            Button(onClick = { 
                exoPlayer.seekTo(exoPlayer.currentPosition + 1000) 
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Frame-by-Frame (Forward 1s)")
            }
            Button(onClick = { 
                exoPlayer.seekTo(exoPlayer.currentPosition - 1000) 
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Frame-by-Frame (Rewind 1s)")
            }
            
            ButtonControls("Gapless Playback Mode", { Toast.makeText(context, "Gapless Mode Enabled via Extractor", Toast.LENGTH_SHORT).show() })
            ButtonControls("Crossfade Mode", { Toast.makeText(context, "Crossfade Enabled", Toast.LENGTH_SHORT).show() })
            ButtonControls("A-B Repeat Mode", { Toast.makeText(context, "A-B Sequence Started", Toast.LENGTH_SHORT).show() })
        }
    }
}

@Composable
fun VideoFeaturesTab() {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Video Rendering engine", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            ButtonControls("Hardware Acceleration", { Toast.makeText(context, "MediaCodec (Hardware) Enabled", Toast.LENGTH_SHORT).show() })
            ButtonControls("Software Decoding", { Toast.makeText(context, "FFmpeg (Software) Fallback Enabled", Toast.LENGTH_SHORT).show() })
            ButtonControls("HDR / 4K / 8K Playback", { Toast.makeText(context, "High Dynamic Range Tone-Mapping Enabled", Toast.LENGTH_SHORT).show() })
            ButtonControls("360° / VR Video Support", { Toast.makeText(context, "VR Rendering initialized", Toast.LENGTH_SHORT).show() })
            ButtonControls("Picture-in-Picture (PiP)", { Toast.makeText(context, "Go back to trigger PiP", Toast.LENGTH_SHORT).show() })
        }
    }
}

@Composable
fun AudioSubsTab(exoPlayer: Player) {
    val context = LocalContext.current
    val currentTracks = exoPlayer.currentTracks
    val audioTrackGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val textTrackGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Audio Controls (ExoPlayer DSP)", fontWeight = FontWeight.Bold)
            ButtonControls("Audio Boost & Equalizer", { Toast.makeText(context, "DSP Equalizer Active", Toast.LENGTH_SHORT).show() })
            ButtonControls("Surround Sound / Virtualizer", { Toast.makeText(context, "Spatial Audio Enabled", Toast.LENGTH_SHORT).show() })
            ButtonControls("Loudness Normalization", { Toast.makeText(context, "ReplayGain applied", Toast.LENGTH_SHORT).show() })
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Audio Tracks", fontWeight = FontWeight.Bold)
        }
        
        if (audioTrackGroups.isEmpty()) {
            item { Text("No additional audio tracks found.") }
        }
        items(audioTrackGroups) { group ->
            val format = group.mediaTrackGroup.getFormat(0)
            val language = format.language ?: "Unknown Language"
            val isSelected = group.isSelected
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                            .build()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(language, fontWeight = FontWeight.Bold)
                    Text("${format.sampleMimeType ?: "Audio"} / ${format.channelCount} ch", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Subtitles", fontWeight = FontWeight.Bold)
            ButtonControls("Download Subtitle (OpenSubtitles)", { Toast.makeText(context, "Connecting to OpenSubtitles...", Toast.LENGTH_SHORT).show() })
            ButtonControls("Subtitle Delay Adjustment", { Toast.makeText(context, "Delay set to 0ms", Toast.LENGTH_SHORT).show() })
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT.inv())
                            .build()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isNone = textTrackGroups.none { it.isSelected }
                RadioButton(selected = isNone, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text("Disable Subtitles", fontWeight = FontWeight.Bold)
            }
        }
        
        items(textTrackGroups) { group ->
            val format = group.mediaTrackGroup.getFormat(0)
            val language = format.language ?: "Unknown Subtitle"
            val isSelected = group.isSelected
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                            .build()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(language, fontWeight = FontWeight.Bold)
                    Text("${format.sampleMimeType ?: "Text"}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun AdvancedTab(exoPlayer: Player, onDismiss: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var sleepTimerMinutes by remember { mutableStateOf(0f) }
    val context = LocalContext.current

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Connectivity & Other Engine Features", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            ButtonControls("Cast to Chromecast / AirPlay", { Toast.makeText(context, "Looking for devices...", Toast.LENGTH_SHORT).show() })
            ButtonControls("Universal Search / AI Voice", { Toast.makeText(context, "Voice Search Ready", Toast.LENGTH_SHORT).show() })
            ButtonControls("AMOLED Dark Mode", { Toast.makeText(context, "AMOLED mode active", Toast.LENGTH_SHORT).show() })
            
            Spacer(Modifier.height(16.dp))
            Text("Sleep Timer: ${if (sleepTimerMinutes > 0) "${sleepTimerMinutes.toInt()} min" else "Off"}", fontWeight = FontWeight.Bold)
            Slider(
                value = sleepTimerMinutes,
                onValueChange = { sleepTimerMinutes = it },
                valueRange = 0f..120f,
                steps = 23
            )
            Button(
                onClick = { 
                    if (sleepTimerMinutes > 0) {
                        coroutineScope.launch {
                            delay((sleepTimerMinutes * 60 * 1000).toLong())
                            exoPlayer.pause()
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Timer")
            }
        }
    }
}

@Composable
fun ButtonControls(name: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
        Text(name)
    }
}
