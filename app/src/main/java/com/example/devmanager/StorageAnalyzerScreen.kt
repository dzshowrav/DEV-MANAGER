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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

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
                    Spacer(modifier = Modifier.height(16.dp))
                    StorageSunburstChart(rootNode = result.rootNode, formatter = { fileViewModel.formatSize(it) })
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

@Composable
fun StorageSunburstChart(rootNode: DirNode?, formatter: (Long) -> String) {
    if (rootNode == null) return
    var selectedNode by remember { mutableStateOf<DirNode?>(null) }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Folder Hierarchy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .size(300.dp)
                        .pointerInput(rootNode) {
                            val canvasSize = this.size
                            detectTapGestures { offset ->
                                val canvasWidth = canvasSize.width.toFloat()
                                val canvasHeight = canvasSize.height.toFloat()
                                val center = Offset(canvasWidth / 2f, canvasHeight / 2f)
                                val dx = offset.x - center.x
                                val dy = offset.y - center.y
                                val distance = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                if (angle < 0) angle += 360f
                                
                                val maxRadius = canvasWidth / 2f
                                val ringWidth = maxRadius / 4f
                                
                                if (distance < ringWidth) {
                                    selectedNode = rootNode
                                } else {
                                    val depth = (distance / ringWidth).toInt()
                                    if (depth in 1..3) {
                                        var clickedNode: DirNode? = null
                                        
                                        fun traverse(node: DirNode, currentDepth: Int, startAngle: Float, sweepAngle: Float) {
                                            if (currentDepth == depth) {
                                                if (angle >= startAngle && angle <= startAngle + sweepAngle) {
                                                    clickedNode = node
                                                }
                                            } else if (currentDepth < depth) {
                                                var currentStart = startAngle
                                                for (child in node.children) {
                                                    val childSweep = sweepAngle * (child.size.toFloat() / Math.max(1f, node.size.toFloat()))
                                                    traverse(child, currentDepth + 1, currentStart, childSweep)
                                                    currentStart += childSweep
                                                }
                                            }
                                        }
                                        traverse(rootNode, 0, 0f, 360f)
                                        clickedNode?.let { selectedNode = it }
                                    }
                                }
                            }
                        }
                ) {
                    val maxRadius = size.width / 2f
                    val ringWidth = maxRadius / 4f
                    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                    
                    val colors = listOf(
                        Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6),
                        Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFF4DB6AC)
                    )
                    
                    fun drawNode(node: DirNode, currentDepth: Int, startAngle: Float, sweepAngle: Float, colorIdx: Int) {
                        if (currentDepth > 3) return
                        
                        val radius = ringWidth * currentDepth
                        val color = colors[colorIdx % colors.size]
                        val alpha = if (selectedNode == null || selectedNode == node || selectedNode?.children?.contains(node) == true) 1f else 0.3f
                        
                        if (currentDepth == 0) {
                            drawCircle(color = color.copy(alpha = alpha), radius = ringWidth, center = center)
                        } else {
                            val rect = androidx.compose.ui.geometry.Rect(
                                center.x - radius - ringWidth/2, center.y - radius - ringWidth/2,
                                center.x + radius + ringWidth/2, center.y + radius + ringWidth/2
                            )
                            drawArc(
                                color = color.copy(alpha = alpha),
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = rect.topLeft,
                                size = rect.size,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringWidth)
                            )
                            
                            // Draw separator
                            drawArc(
                                color = Color.White.copy(alpha = 0.5f),
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = rect.topLeft,
                                size = rect.size,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = ringWidth)
                            )
                        }
                        
                        var currentStart = startAngle
                        node.children.forEachIndexed { i, child ->
                            val childSweep = sweepAngle * (child.size.toFloat() / Math.max(1L, node.size).toFloat())
                            drawNode(child, currentDepth + 1, currentStart, childSweep, colorIdx + i + 1)
                            currentStart += childSweep
                        }
                    }
                    
                    drawNode(rootNode, 0, 0f, 360f, 0)
                }
                
                // Overlay text
                val displayNode = selectedNode ?: rootNode
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha=0.7f), androidx.compose.foundation.shape.CircleShape).padding(8.dp)) {
                    Text(displayNode.name.ifEmpty { "Root" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max=80.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(formatter(displayNode.size), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
