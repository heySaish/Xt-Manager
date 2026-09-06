package com.xtmanager.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.FileType
import com.xtmanager.core.thumbnail.ThumbnailManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    fileEntry: FileEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipe: () -> Unit = {},
    densityScale: Float = 1.0f,
    showThumbnails: Boolean = true,
    fileNameMaxLines: Int = 2,
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
    // Swipe limit matching image displacement (max 42.dp)
    val maxSwipePx = remember(density) { with(density) { 42.dp.toPx() } }
    val minSwipeThresholdPx = remember(density) { with(density) { 24.dp.toPx() } }

    val swipeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val context = LocalContext.current
    val isThumbSupported = remember(fileEntry.path, fileEntry.lastModified, showThumbnails) {
        showThumbnails && ThumbnailManager.isThumbnailSupported(fileEntry)
    }

    var thumbnailBitmap by remember(fileEntry.path, fileEntry.lastModified) {
        val cacheKey = ThumbnailManager.buildCacheKey(fileEntry.path, fileEntry.lastModified, fileEntry.size)
        mutableStateOf(ThumbnailManager.getFromMemoryCache(cacheKey))
    }

    if (isThumbSupported && thumbnailBitmap == null) {
        LaunchedEffect(fileEntry.path, fileEntry.lastModified) {
            val bmp = ThumbnailManager.getThumbnail(context, fileEntry)
            if (bmp != null) {
                thumbnailBitmap = bmp
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .graphicsLayer {
                translationX = swipeOffset.value
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalX = 0f
                    var totalY = 0f
                    var isHorizontalGesture = false
                    var swipeTriggered = false

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) {
                            val dragAmount = change.positionChange()
                            totalX += dragAmount.x
                            totalY += dragAmount.y

                            val absX = kotlin.math.abs(totalX)
                            val absY = kotlin.math.abs(totalY)

                            if (!isHorizontalGesture && absX > 8f) {
                                if (absX > absY * 1.3f) {
                                    isHorizontalGesture = true
                                }
                            }

                            if (isHorizontalGesture) {
                                change.consume()
                                val targetOffset = totalX.coerceIn(-maxSwipePx, maxSwipePx)
                                coroutineScope.launch {
                                    swipeOffset.snapTo(targetOffset)
                                }

                                if (!swipeTriggered && absX >= minSwipeThresholdPx) {
                                    swipeTriggered = true
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    onSwipe()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Animate back smoothly when finger is released
                    coroutineScope.launch {
                        swipeOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
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
        val thumb = thumbnailBitmap
        if (thumb != null) {
            Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(4.dp))
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(iconSize)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileEntry.name,
                style = titleStyle,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = fileNameMaxLines,
                overflow = TextOverflow.Ellipsis
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
