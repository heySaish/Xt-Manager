package com.xtmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BottomBar(
    hasSelection: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onGoUp: () -> Unit,
    onCreateFolder: () -> Unit,
    onSyncPath: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        BottomAppBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasSelection) {
                    // Actions when items are selected
                    IconButton(onClick = onCopy) {
                        Icon(
                            imageVector = Icons.Default.CompareArrows,
                            contentDescription = "Copy to other pane",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    IconButton(onClick = onMove) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Move to other pane",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    IconButton(onClick = onRename) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    IconButton(onClick = onCompress) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Compress",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    IconButton(onClick = onExtract) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Extract",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    // Standard dynamic navigation actions: < > + ⇄ ↑
                    IconButton(
                        onClick = onGoBack,
                        enabled = canGoBack
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go back in history",
                            tint = if (canGoBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    
                    IconButton(
                        onClick = onGoForward,
                        enabled = canGoForward
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Go forward in history",
                            tint = if (canGoForward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        IconButton(onClick = onCreateFolder) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create folder or file",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    IconButton(onClick = onSyncPath) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Sync path",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    
                    IconButton(onClick = onGoUp) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Go up directory",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
