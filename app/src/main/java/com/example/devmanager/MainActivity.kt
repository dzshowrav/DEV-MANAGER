package com.example.devmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder

import androidx.activity.enableEdgeToEdge

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
                add(AudioAlbumArtFetcher.Factory())
            }
            .build()
        Coil.setImageLoader(imageLoader)

        try {
            enableEdgeToEdge()
            setContent {
                val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
                MaterialTheme(colorScheme = colorScheme) {
                    val viewModel: FileManagerViewModel = viewModel()
                    FileManagerApp(viewModel)
                }
            }
        } catch (e: Exception) {
            setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.Text(text = "Crash: ${e.stackTraceToString()}")
                    }
                }
            }
        }
    }
}
