package com.xtmanager.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.PaneState
import com.xtmanager.core.model.PaneType
import com.xtmanager.ui.FilePane

@Composable
fun DualPaneView(
    leftPaneState: PaneState,
    rightPaneState: PaneState,
    activePane: PaneType,
    densityScale: Float,
    folderAnimationEnabled: Boolean,
    folderAnimationStyle: String = "slide",
    showThumbnails: Boolean,
    fileNameMaxLines: Int,
    activePaneHighlightEnabled: Boolean,
    onFileClick: (PaneType, FileEntry) -> Unit,
    onFileLongClick: (PaneType, FileEntry) -> Unit,
    onPathClick: (PaneType, String) -> Unit,
    onPaneClick: (PaneType) -> Unit,
    onRefresh: () -> Unit,
    onFileSwipe: (PaneType, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
    ) {
        FilePane(
            paneState = leftPaneState,
            isActive = activePane == PaneType.LEFT,
            densityScale = densityScale,
            isAnimationEnabled = folderAnimationEnabled,
            folderAnimationStyle = folderAnimationStyle,
            showThumbnails = showThumbnails,
            fileNameMaxLines = fileNameMaxLines,
            activePaneHighlightEnabled = activePaneHighlightEnabled,
            onFileClick = { file -> onFileClick(PaneType.LEFT, file) },
            onFileLongClick = { file -> onFileLongClick(PaneType.LEFT, file) },
            onPathClick = { path -> onPathClick(PaneType.LEFT, path) },
            onPaneClick = { onPaneClick(PaneType.LEFT) },
            onRefresh = onRefresh,
            onFileSwipe = { index -> onFileSwipe(PaneType.LEFT, index) },
            modifier = Modifier
                .weight(1f)
                .padding(end = 2.dp)
        )
        
        FilePane(
            paneState = rightPaneState,
            isActive = activePane == PaneType.RIGHT,
            densityScale = densityScale,
            isAnimationEnabled = folderAnimationEnabled,
            folderAnimationStyle = folderAnimationStyle,
            showThumbnails = showThumbnails,
            fileNameMaxLines = fileNameMaxLines,
            activePaneHighlightEnabled = activePaneHighlightEnabled,
            onFileClick = { file -> onFileClick(PaneType.RIGHT, file) },
            onFileLongClick = { file -> onFileLongClick(PaneType.RIGHT, file) },
            onPathClick = { path -> onPathClick(PaneType.RIGHT, path) },
            onPaneClick = { onPaneClick(PaneType.RIGHT) },
            onRefresh = onRefresh,
            onFileSwipe = { index -> onFileSwipe(PaneType.RIGHT, index) },
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp)
        )
    }
}
