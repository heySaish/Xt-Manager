package com.xtmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayThemeSettingsScreen(
    showHiddenFiles: Boolean = false,
    onToggleShowHiddenFiles: () -> Unit = {},
    folderAnimationEnabled: Boolean = true,
    onToggleFolderAnimation: () -> Unit = {},
    folderAnimationStyle: String = "slide",
    onSetFolderAnimationStyle: (String) -> Unit = {},
    naturalSort: Boolean = true,
    onToggleNaturalSort: () -> Unit = {},
    densityScale: Float = 1.0f,
    onOpenDensityPreview: () -> Unit = {},
    bottomBarScale: Float = 1.0f,
    onOpenBottomBarSizePreview: () -> Unit = {},
    showThumbnails: Boolean = true,
    onToggleShowThumbnails: () -> Unit = {},
    fileNameMaxLines: Int = 2,
    onSetFileNameMaxLines: (Int) -> Unit = {},
    activePaneHighlightEnabled: Boolean = true,
    onToggleActivePaneHighlight: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAnimationStyleDialog by remember { mutableStateOf(false) }

    BackHandler {
        onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Display & Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Group: Display Density & Action Bar Customization
            SettingsGroupCard(title = "Sizing & Layout Customization") {
                // Item 1: Pane Display Density / Item Size
                SettingsRowItem(
                    icon = Icons.Default.AspectRatio,
                    iconBgColor = Color(0xFF10B981),
                    title = "Pane Display Density / Item Size",
                    subtitle = "Adjust row height, icon scale & visible items count (${String.format(Locale.US, "%.2f", densityScale)}x)",
                    onClick = { onOpenDensityPreview() },
                    trailingWidget = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Item 2: Bottom Action Bar Size
                SettingsRowItem(
                    icon = Icons.Default.ViewStream,
                    iconBgColor = Color(0xFF8B5CF6),
                    title = "Bottom Action Bar Size",
                    subtitle = "Adjust action bar size & icon scale (${String.format(Locale.US, "%.2f", bottomBarScale)}x)",
                    onClick = { onOpenBottomBarSizePreview() },
                    trailingWidget = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Group: Behavior & View Preferences
            SettingsGroupCard(title = "View & Animation Preferences") {
                // Item 3: Folder Navigation Animation
                SettingsRowItem(
                    icon = Icons.Default.Animation,
                    iconBgColor = Color(0xFF6366F1),
                    title = "Folder Navigation Animation",
                    subtitle = "Toggle entrance animation when navigating folders",
                    trailingWidget = {
                        Switch(
                            checked = folderAnimationEnabled,
                            onCheckedChange = { onToggleFolderAnimation() }
                        )
                    }
                )

                if (folderAnimationEnabled) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Item 3b: Animation Style Choice
                    val styleLabel = when (folderAnimationStyle.lowercase()) {
                        "cascade" -> "Cascade (Staggered Entrance)"
                        "fade" -> "Fade (Smooth Fade In)"
                        "none" -> "None (Instant)"
                        else -> "Slide (Clean Horizontal Slide)"
                    }
                    SettingsRowItem(
                        icon = Icons.Default.FolderSpecial,
                        iconBgColor = Color(0xFF8B5CF6),
                        title = "Animation Style",
                        subtitle = styleLabel,
                        onClick = { showAnimationStyleDialog = true },
                        trailingWidget = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Item 4: Show Hidden Files
                SettingsRowItem(
                    icon = Icons.Default.Visibility,
                    iconBgColor = Color(0xFF3B82F6),
                    title = "Show Hidden Files & Folders",
                    subtitle = "Display dotfiles starting with '.' in file browser",
                    trailingWidget = {
                        Switch(
                            checked = showHiddenFiles,
                            onCheckedChange = { onToggleShowHiddenFiles() }
                        )
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Item 5: Natural Sorting
                SettingsRowItem(
                    icon = Icons.Default.SortByAlpha,
                    iconBgColor = Color(0xFFF59E0B),
                    title = "Natural Alphanumeric Sorting",
                    subtitle = "Sort numerical filenames naturally (e.g. file2 before file10)",
                    trailingWidget = {
                        Switch(
                            checked = naturalSort,
                            onCheckedChange = { onToggleNaturalSort() }
                        )
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Item 6: Load File Thumbnails
                SettingsRowItem(
                    icon = Icons.Default.Image,
                    iconBgColor = Color(0xFFEC4899),
                    title = "Load File Thumbnails",
                    subtitle = "Display image, video, and APK icon previews in file browser",
                    trailingWidget = {
                        Switch(
                            checked = showThumbnails,
                            onCheckedChange = { onToggleShowThumbnails() }
                        )
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Item 7: Filename Max Lines (1 line vs 2 lines)
                SettingsRowItem(
                    icon = Icons.Default.WrapText,
                    iconBgColor = Color(0xFF10B981),
                    title = "Filename Max Lines",
                    subtitle = if (fileNameMaxLines > 1) "Wrap long file names to 2 lines" else "Truncate file names on single line",
                    trailingWidget = {
                        FilterChip(
                            selected = fileNameMaxLines > 1,
                            onClick = { onSetFileNameMaxLines(if (fileNameMaxLines > 1) 1 else 2) },
                            label = { Text(if (fileNameMaxLines > 1) "2 Lines" else "1 Line") }
                        )
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Item 8: Active Pane Highlighting
                SettingsRowItem(
                    icon = Icons.Default.Highlight,
                    iconBgColor = Color(0xFF6366F1),
                    title = "Active Pane Highlighting",
                    subtitle = "Highlight the active file pane with a colored border & elevation",
                    trailingWidget = {
                        Switch(
                            checked = activePaneHighlightEnabled,
                            onCheckedChange = { onToggleActivePaneHighlight() }
                        )
                    }
                )
            }
        }
    }

    if (showAnimationStyleDialog) {
        val styles = listOf(
            "slide" to "Slide (Clean Horizontal Slide)",
            "cascade" to "Cascade (Staggered Entrance)",
            "fade" to "Fade (Smooth Fade In)",
            "none" to "None (Instant)"
        )
        AlertDialog(
            onDismissRequest = { showAnimationStyleDialog = false },
            title = { Text("Select Animation Style", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    styles.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetFolderAnimationStyle(key)
                                    showAnimationStyleDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = folderAnimationStyle.equals(key, ignoreCase = true),
                                onClick = {
                                    onSetFolderAnimationStyle(key)
                                    showAnimationStyleDialog = false
                                }
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAnimationStyleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
