package com.example.devmanager.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.devmanager.data.model.FileItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileDetailedItem(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .animateContentSize()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Checkbox(
                    checked = true,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            FileIconBox(
                extension = item.extension,
                isDirectory = item.isDirectory,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (item.isDirectory) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    if (item.sizeLabel.isNotEmpty()) {
                        Text(
                            text = item.sizeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "  ·  ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = item.lastModifiedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (item.resolution != null) {
                Text(
                    text = item.resolution,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileCompactItem(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileIconBox(
            extension = item.extension,
            isDirectory = item.isDirectory,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (item.sizeLabel.isNotEmpty()) {
            Text(
                text = item.sizeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        modifier = modifier
            .padding(4.dp)
            .animateContentSize(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FileIconBox(
                    extension = item.extension,
                    isDirectory = item.isDirectory,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun FileListView(
    items: List<FileItem>,
    viewMode: com.example.devmanager.data.model.ViewMode,
    selectedFiles: Set<java.io.File>,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    when (viewMode) {
        com.example.devmanager.data.model.ViewMode.DETAILED -> {
            LazyColumn(modifier = modifier.fillMaxSize()) {
                items(items, key = { it.file.absolutePath }) { item ->
                    FileDetailedItem(
                        item = item,
                        isSelected = item.file in selectedFiles,
                        onClick = { onFileClick(item) },
                        onLongClick = { onFileLongClick(item) }
                    )
                }
            }
        }
        com.example.devmanager.data.model.ViewMode.COMPACT -> {
            LazyColumn(modifier = modifier.fillMaxSize()) {
                items(items, key = { it.file.absolutePath }) { item ->
                    FileCompactItem(
                        item = item,
                        isSelected = item.file in selectedFiles,
                        onClick = { onFileClick(item) },
                        onLongClick = { onFileLongClick(item) }
                    )
                }
            }
        }
        com.example.devmanager.data.model.ViewMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = modifier.fillMaxSize()
            ) {
                items(items, key = { it.file.absolutePath }) { item ->
                    FileGridItem(
                        item = item,
                        isSelected = item.file in selectedFiles,
                        onClick = { onFileClick(item) },
                        onLongClick = { onFileLongClick(item) }
                    )
                }
            }
        }
    }
}
