package com.xtmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.SelectAll
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
    onSelectAll: () -> Unit = {},
    onInvertSelect: () -> Unit = {},
    onCancelSelection: () -> Unit = {},
    onPatternSelect: () -> Unit = {},
    onPlaceholderClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    scale: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val containerHeight = (58.dp * scale).coerceIn(42.dp, 96.dp)
    val iconSize = (22.dp * scale).coerceIn(16.dp, 36.dp)
    val iconButtonSize = (42.dp * scale).coerceIn(30.dp, 64.dp)
    val horizontalPadding = (10.dp * scale).coerceIn(4.dp, 20.dp)
    val verticalPadding = (3.dp * scale).coerceIn(1.dp, 10.dp)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding),
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(containerHeight)
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasSelection) {
                    // Actions in exact order: 1. Select All, 2. Invert Select, 3. Cancel, 4. Pattern Selection, 5. Placeholder
                    IconButton(
                        onClick = onSelectAll,
                        modifier = Modifier.size(iconButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = "Select All",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(iconSize)
                        )
                    }

                    IconButton(
                        onClick = onInvertSelect,
                        modifier = Modifier.size(iconButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipToBack,
                            contentDescription = "Invert Select",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(iconSize)
                        )
                    }

                    IconButton(
                        onClick = onCancelSelection,
                        modifier = Modifier.size(iconButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Selection",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(iconSize)
                        )
                    }

                    IconButton(
                        onClick = onPatternSelect,
                        modifier = Modifier.size(iconButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Pattern Selection",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(iconSize)
                        )
                    }

                    IconButton(
                        onClick = onPlaceholderClick,
                        modifier = Modifier.size(iconButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = "Placeholder Action",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(iconSize)
                        )
                    }
                } else {
                    // Standard dynamic navigation actions: < > + ⇄ ↑
                    IconButton(
                        onClick = onGoBack,
                        enabled = canGoBack,
                        modifier = Modifier.size(iconButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go back in history",
                            tint = if (canGoBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    
                    IconButton(
                        onClick = onGoForward,
                        enabled = canGoForward,
                        modifier = Modifier.size(iconButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Go forward in history",
                            tint = if (canGoForward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(12.dp * scale),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        IconButton(
                            onClick = onCreateFolder,
                            modifier = Modifier.size(iconButtonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create folder or file",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }

                    IconButton(
                        onClick = onSyncPath,
                        modifier = Modifier.size(iconButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Sync path",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                    
                    IconButton(
                        onClick = onGoUp,
                        modifier = Modifier.size(iconButtonSize)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Go up directory",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }
            }
        }
    }
}
}
