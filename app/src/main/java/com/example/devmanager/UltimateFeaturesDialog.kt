package com.example.devmanager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UltimateFeaturesDialog(onDismiss: () -> Unit) {
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
                    title = { Text("Ultimate Hybrid Media Player Features") },
                    actions = {
                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                )
                
                LazyColumn(
                    contentPadding = PaddingValues(16.dp)
                ) {
                    item {
                        Text(
                            text = "Core Engine (20) & Video/Audio Base", 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        FeatureSwitch("Play / Pause / Stop / Resume", true)
                        FeatureSwitch("Next / Previous / Fast Forward / Rewind", true)
                        FeatureSwitch("Seek Bar & Frame-by-Frame Playback", true)
                        FeatureSwitch("Playback Speed Control", true)
                        FeatureSwitch("Pitch Control / Tempo Adjustment", true)
                        FeatureSwitch("A-B Repeat & Loop / Shuffle", true)
                        FeatureSwitch("Gapless & Crossfade Mode", true)
                        FeatureSwitch("Auto Play Next / Queue Playback", true)
                        FeatureSwitch("Continuous Playback", true)
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Video-Specific Features (20)", 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        FeatureSwitch("Fullscreen Mode & Aspect Ratio Control", true)
                        FeatureSwitch("Picture-in-Picture (PiP) / Mini Player", true)
                        FeatureSwitch("Screen Fit/Fill & Screen Zoom", true)
                        FeatureSwitch("Brightness Control (Swipe)", true)
                        FeatureSwitch("Gesture Controls (Double Tap, Pinch-to-Zoom)", true)
                        FeatureSwitch("360° / VR Video Support Engine", true)
                        FeatureSwitch("HDR / 4K / 8K Playback", true)
                        FeatureSwitch("High FPS Playback", true)
                        FeatureSwitch("Hardware/Software Decoding Acceleration", true)

                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Audio-Specific Features (18)", 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        FeatureSwitch("Volume Control / Mute (Swipe Volume)", true)
                        FeatureSwitch("Audio Boost & Parametric EQ", true)
                        FeatureSwitch("Surround Sound / Virtualizer / Reverb", true)
                        FeatureSwitch("Stereo/Mono Switch & Loudness Normalization", true)
                        FeatureSwitch("Hi-Res Audio / Lossless & Bit-Perfect Output", true)
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Subtitle & Audio Management (25)", 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        FeatureSwitch("Embedded/External Subtitles", true)
                        FeatureSwitch("Download / Search / Sync", true)
                        FeatureSwitch("Subtitle Styling / Dual Subtitles", true)
                        FeatureSwitch("Multiple Audio Tracks / Switching / Delay", true)

                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Playlist & Library Management (15)", 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        FeatureSwitch("Playlist Creation / Smart Playlists", true)
                        FeatureSwitch("Queue Management / Sorting", true)
                        FeatureSwitch("Import/Export & M3U Formatting", true)
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Streaming & Connectivity (20)", 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        FeatureSwitch("HTTP/HLS/DASH/RTSP Streaming Streams", true)
                        FeatureSwitch("IPTV/Podcasts/Live Streaming", true)
                        FeatureSwitch("LAN / SMB / FTP / WebDAV Access", true)
                        FeatureSwitch("Chromecast/AirPlay/Android TV Syncing", true)

                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Offline & Search & Environment (29)", 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        FeatureSwitch("Download & Offline Playback / Auto D/L", true)
                        FeatureSwitch("Universal / AI Voice Search / Filters", true)
                        FeatureSwitch("Custom Themes (Dark/AMOLED/Material UI)", true)
                        FeatureSwitch("Floating Controls & Sleep Timer", true)
                        FeatureSwitch("Supported Formats (MKV, MP4, FLAC, DSD)", true)
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureSwitch(name: String, defaultState: Boolean = false) {
    var checked by remember { mutableStateOf(defaultState) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = name, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { checked = it })
    }
}
