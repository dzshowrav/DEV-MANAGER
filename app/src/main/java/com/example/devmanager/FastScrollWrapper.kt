package com.example.devmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun FastScrollWrapper(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    labelProvider: ((Int) -> String)? = null,
    content: @Composable () -> Unit
) {
    var containerHeight by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    val totalItems = listState.layoutInfo.totalItemsCount
    val showFastScroll = totalItems > 20

    Box(modifier = modifier.onGloballyPositioned { containerHeight = it.size.height }) {
        content()

        if (showFastScroll) {
            val visibleTotal = listState.layoutInfo.visibleItemsInfo.size
            if (visibleTotal > 0 && totalItems > visibleTotal) {
                // Calculate scroll proportion based on first visible item if not dragging
                val scrollProportion = if (isDragging) {
                    dragProgress
                } else {
                    val firstVisible = listState.firstVisibleItemIndex
                    firstVisible.toFloat() / max(1, totalItems - 1).toFloat()
                }

                // Handle size and pos of the thumb
                val thumbRatio = visibleTotal.toFloat() / totalItems.toFloat()
                val thumbHeightPx = max(100f, containerHeight * thumbRatio)
                val trackHeightPx = containerHeight - thumbHeightPx
                
                val offsetPx = scrollProportion * trackHeightPx
                val offsetDp = with(androidx.compose.ui.platform.LocalDensity.current) { offsetPx.toDp() }
                val thumbHeightDp = with(androidx.compose.ui.platform.LocalDensity.current) { thumbHeightPx.toDp() }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp)
                        .offset(y = offsetDp)
                        .size(width = 16.dp, height = thumbHeightDp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        .pointerInput(totalItems) {
                            detectDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (trackHeightPx > 0) {
                                        val yPos = change.position.y - (thumbHeightPx / 2)
                                        dragProgress = (yPos / trackHeightPx).coerceIn(0f, 1f)
                                        val targetIndex = (dragProgress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                                        coroutineScope.launch {
                                            listState.scrollToItem(targetIndex)
                                        }
                                    }
                                }
                            )
                        }
                )

                if (isDragging && labelProvider != null) {
                    val targetIndex = (dragProgress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                    val label = labelProvider(targetIndex)
                    if (label.isNotEmpty()) {
                        val letter = label.first().uppercaseChar()
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 24.dp)
                                .offset(y = offsetDp + (thumbHeightDp / 2) - 24.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = letter.toString(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
