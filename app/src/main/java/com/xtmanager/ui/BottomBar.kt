package com.xtmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BottomAppBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
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
                        contentDescription = "Rename"
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
                        contentDescription = "Compress"
                    )
                }

                IconButton(onClick = onExtract) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Extract"
                    )
                }
            } else {
                // Standard navigation actions
                IconButton(
                    onClick = onGoBack,
                    enabled = canGoBack
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Go back in history"
                    )
                }
                
                IconButton(
                    onClick = onGoForward,
                    enabled = canGoForward
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Go forward in history"
                    )
                }
                
                IconButton(onClick = onCreateFolder) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create folder"
                    )
                }
                
                IconButton(onClick = onGoUp) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Go up directory"
                    )
                }
                
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "More options"
                    )
                }
            }
        }
    }
}
