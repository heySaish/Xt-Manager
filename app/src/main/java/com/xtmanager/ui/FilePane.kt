package com.xtmanager.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.FileType
import com.xtmanager.core.model.PaneState

@Composable
fun FilePane(
    paneState: PaneState,
    isActive: Boolean,
    onFileClick: (FileEntry) -> Unit,
    onFileLongClick: (FileEntry) -> Unit,
    onPathClick: (String) -> Unit,
    onPaneClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }
    
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPaneClick
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = if (isActive) 1.dp else 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Path Bar
            PathBar(
                path = paneState.path,
                onPathClick = onPathClick,
                modifier = Modifier.fillMaxWidth()
            )
            
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            
            Box(modifier = Modifier.weight(1f)) {
                if (paneState.files.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Empty Directory",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // "Go Up" directory item (..)
                        if (paneState.path != "/" && paneState.path != "") {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val parentFile = java.io.File(paneState.path).parent
                                            if (parentFile != null) {
                                                onPathClick(parentFile)
                                            }
                                        }
                                        .padding(vertical = 8.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Go up",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "..",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 12.dp)
                                    )
                                }
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            }
                        }

                        items(paneState.files) { file ->
                            FileRow(
                                fileEntry = file,
                                isSelected = paneState.selected.contains(file.path),
                                onClick = { onFileClick(file) },
                                onLongClick = { onFileLongClick(file) }
                            )
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        }
                    }
                }
            }
        }
    }
}
