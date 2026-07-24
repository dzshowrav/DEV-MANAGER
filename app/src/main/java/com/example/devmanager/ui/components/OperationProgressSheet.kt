package com.example.devmanager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.devmanager.data.model.FileOperation
import com.example.devmanager.data.model.OperationStatus

@Composable
fun OperationProgressSheet(
    operations: List<FileOperation>,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = operations.isNotEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth().padding(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Operations", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn {
                    items(operations) { op ->
                        OperationItem(op = op, onCancel = { onCancel(op.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationItem(
    op: FileOperation,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when (op.status) {
            OperationStatus.QUEUED -> Icons.Default.HourglassEmpty
            OperationStatus.RUNNING -> Icons.Default.HourglassEmpty
            OperationStatus.COMPLETED -> Icons.Default.CheckCircle
            OperationStatus.FAILED -> Icons.Default.Error
            OperationStatus.CANCELLED -> Icons.Default.Cancel
            OperationStatus.PAUSED -> Icons.Default.HourglassEmpty
        }
        val tint = when (op.status) {
            OperationStatus.COMPLETED -> MaterialTheme.colorScheme.primary
            OperationStatus.FAILED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }
        Icon(icon, null, tint = tint, modifier = Modifier.padding(end = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("${op.type.name} ${op.source.name}", style = MaterialTheme.typography.bodySmall)
            if (op.totalBytes > 0) {
                val progress = if (op.totalBytes > 0) op.progressBytes.toFloat() / op.totalBytes else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
            }
            if (op.status == OperationStatus.RUNNING || op.status == OperationStatus.QUEUED) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Cancel, "Cancel", modifier = Modifier.height(20.dp)) }
            }
        }
    }
}
