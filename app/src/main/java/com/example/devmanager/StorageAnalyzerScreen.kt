package com.example.devmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyzerScreen(
    viewModel: StorageAnalyzerViewModel,
    fileViewModel: FileManagerViewModel,
    onBack: () -> Unit
) {
    var showLargeFiles by remember { mutableStateOf(false) }
    var showEmptyFolders by remember { mutableStateOf(false) }
    var showDuplicates by remember { mutableStateOf(false) }
    var showJunkFiles by remember { mutableStateOf(false) }

    BackHandler {
        if (showLargeFiles) showLargeFiles = false
        else if (showEmptyFolders) showEmptyFolders = false
        else if (showDuplicates) showDuplicates = false
        else if (showJunkFiles) showJunkFiles = false
        else onBack()
    }
    
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    
    val storageRoot = fileViewModel.storageRoot

    LaunchedEffect(Unit) {
        viewModel.analyzeStorage(storageRoot)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Analyzer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.analyzeStorage(storageRoot) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isAnalyzing) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Analyzing Storage...", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(scanProgress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, modifier = Modifier.padding(horizontal = 32.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
                item {
                    StorageUsageChart(result = result, formatter = { fileViewModel.formatSize(it) })
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.cleanCache() }, modifier = Modifier.weight(1f)) {
                            Text("Clean App Cache")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    AnalyzerCategoryCard(
                        title = "Large Files",
                        subtitle = "${result.largeFiles.size} found",
                        icon = Icons.Default.Description,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        onClick = { showLargeFiles = !showLargeFiles }
                    )
                    if (showLargeFiles) {
                        Column {
                            result.largeFiles.forEach { file ->
                                FileMiniRow(file = file, formatter = { fileViewModel.formatSize(it) })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    AnalyzerCategoryCard(
                        title = "Junk Files",
                        subtitle = "${result.junkFiles.size} found (logs, tmp)",
                        icon = Icons.Default.DeleteSweep,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        onClick = { showJunkFiles = !showJunkFiles }
                    )
                    if (showJunkFiles) {
                        Column {
                            if (result.junkFiles.isNotEmpty()) {
                                TextButton(onClick = { viewModel.cleanFiles(result.junkFiles); showJunkFiles = false }) {
                                    Text("Clean All Junk", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            result.junkFiles.forEach { file ->
                                FileMiniRow(file = file, formatter = { fileViewModel.formatSize(it) })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    AnalyzerCategoryCard(
                        title = "Empty Folders",
                        subtitle = "${result.emptyFolders.size} found",
                        icon = Icons.Default.FolderOpen,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = { showEmptyFolders = !showEmptyFolders }
                    )
                    if (showEmptyFolders) {
                        Column {
                             if (result.emptyFolders.isNotEmpty()) {
                                TextButton(onClick = { viewModel.cleanFiles(result.emptyFolders); showEmptyFolders = false }) {
                                    Text("Delete All Empty Folders")
                                }
                            }
                            result.emptyFolders.forEach { file ->
                                FileMiniRow(file = file, formatter = { fileViewModel.formatSize(it) })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    AnalyzerCategoryCard(
                        title = "Duplicate Files",
                        subtitle = "${result.duplicates.size} groups found",
                        icon = Icons.Default.FilterNone,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = { showDuplicates = !showDuplicates }
                    )
                    if (showDuplicates) {
                        Column {
                            result.duplicates.forEachIndexed { index, list ->
                                Text("Group ${index + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                                list.forEach { file ->
                                    FileMiniRow(file = file, formatter = { fileViewModel.formatSize(it) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileMiniRow(file: File, formatter: (Long) -> String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(file.absolutePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(formatter(file.length()), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun AnalyzerCategoryCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, containerColor: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun StorageUsageChart(result: AnalyzerResult, formatter: (Long) -> String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Storage Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            val colors = listOf(
                Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6),
                Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFF4DB6AC), Color(0xFFA1887F)
            )
            
            val total = result.totalSize.coerceAtLeast(1L)
            
            Canvas(modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp))) {
                var currentX = 0f
                val w = size.width
                val h = size.height
                
                val segments = listOf(
                    result.videoSize to colors[0],
                    result.audioSize to colors[1],
                    result.imageSize to colors[2],
                    result.documentSize to colors[3],
                    result.archiveSize to colors[4],
                    result.apkSize to colors[5],
                    result.otherSize to colors[6]
                )
                
                segments.forEach { (size, color) ->
                    val width = (size.toFloat() / total) * w
                    if (width > 0) {
                        drawLine(
                            color = color,
                            start = Offset(currentX, h / 2),
                            end = Offset(currentX + width, h / 2),
                            strokeWidth = h,
                            cap = StrokeCap.Square
                        )
                        currentX += width
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Column {
                LegendItem("Videos", result.videoSize, colors[0], formatter)
                LegendItem("Audio", result.audioSize, colors[1], formatter)
                LegendItem("Images", result.imageSize, colors[2], formatter)
                LegendItem("Documents", result.documentSize, colors[3], formatter)
                LegendItem("Archives", result.archiveSize, colors[4], formatter)
                LegendItem("APKs", result.apkSize, colors[5], formatter)
                LegendItem("Other", result.otherSize, colors[6], formatter)
            }
        }
    }
}

@Composable
fun LegendItem(label: String, size: Long, color: Color, formatter: (Long) -> String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(formatter(size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
