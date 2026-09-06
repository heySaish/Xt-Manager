package com.xtmanager.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.FileType

import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    fileEntry: FileEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipe: () -> Unit = {},
    densityScale: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val icon = when (fileEntry.type) {
        FileType.DIRECTORY -> Icons.Default.Folder
        FileType.ARCHIVE -> Icons.Default.Inventory
        FileType.FILE -> Icons.Default.Description
    }

    val iconColor = when (fileEntry.type) {
        FileType.DIRECTORY -> MaterialTheme.colorScheme.primary
        FileType.ARCHIVE -> MaterialTheme.colorScheme.secondary
        FileType.FILE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val verticalPadding = (4.dp * densityScale).coerceIn(1.dp, 12.dp)
    val iconSize = (22.dp * densityScale).coerceIn(16.dp, 32.dp)
    val titleFontSize = (14 * densityScale).sp
    val labelFontSize = (11 * densityScale).sp

    val typography = MaterialTheme.typography
    val titleStyle = remember(densityScale, typography.bodyMedium) {
        typography.bodyMedium.copy(fontSize = titleFontSize)
    }

    val labelStyle = remember(densityScale, typography.labelSmall) {
        typography.labelSmall.copy(fontSize = labelFontSize)
    }

    val density = LocalDensity.current
    val minSwipeThresholdPx = remember(density) { with(density) { 35.dp.toPx() } }
    var totalDragAmount by remember { mutableFloatStateOf(0f) }
    var swipeTriggered by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalX = 0f
                    var totalY = 0f
                    var swipeTriggered = false

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            val dragAmount = change.positionChange()
                            totalX += dragAmount.x
                            totalY += dragAmount.y

                            if (!swipeTriggered && kotlin.math.abs(totalX) >= minSwipeThresholdPx && kotlin.math.abs(totalX) > kotlin.math.abs(totalY) * 1.4f) {
                                swipeTriggered = true
                                change.consume()
                                onSwipe()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(vertical = verticalPadding, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(iconSize)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileEntry.name,
                style = titleStyle,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            
            Row(modifier = Modifier.padding(top = 1.dp)) {
                Text(
                    text = fileEntry.formattedDate,
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                if (!fileEntry.isDirectory) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = fileEntry.formattedSize,
                        style = labelStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

