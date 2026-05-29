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

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

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

        ThemeManager.init(this)

        try {
            enableEdgeToEdge()
            setContent {
                val themeColor by ThemeManager.currentTheme.collectAsStateWithLifecycle()
                val darkTheme = isSystemInDarkTheme()
                
                val context = androidx.compose.ui.platform.LocalContext.current
                val colorScheme = when {
                    themeColor == ThemeColor.DEFAULT && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
                        if (darkTheme) androidx.compose.material3.dynamicDarkColorScheme(context)
                        else androidx.compose.material3.dynamicLightColorScheme(context)
                    }
                    else -> {
                        val baseColor = when (themeColor) {
                            ThemeColor.BLUE -> androidx.compose.ui.graphics.Color(0xFF2196F3)
                            ThemeColor.RED -> androidx.compose.ui.graphics.Color(0xFFF44336)
                            ThemeColor.GREEN -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            ThemeColor.PURPLE -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
                            ThemeColor.ORANGE -> androidx.compose.ui.graphics.Color(0xFFFF9800)
                            ThemeColor.DEFAULT -> androidx.compose.ui.graphics.Color(0xFF6750A4)
                            else -> androidx.compose.ui.graphics.Color(0xFF6750A4)
                        }
                        if (darkTheme) darkColorScheme(primary = baseColor)
                        else lightColorScheme(primary = baseColor)
                    }
                }

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
