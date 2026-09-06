package com.xtmanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.PaneState

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilePane(
    paneState: PaneState,
    isActive: Boolean,
    onFileClick: (FileEntry) -> Unit,
    onFileLongClick: (FileEntry) -> Unit,
    onPathClick: (String) -> Unit,
    onPaneClick: () -> Unit,
    onRefresh: () -> Unit,
    onFileSwipe: (Int) -> Unit = {},
    densityScale: Float = 1.0f,
    isAnimationEnabled: Boolean = true,
    showThumbnails: Boolean = true,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }
    
    val interactionSource = remember { MutableInteractionSource() }
    val state = rememberPullToRefreshState()

    LaunchedEffect(state.isRefreshing) {
        if (state.isRefreshing) {
            onRefresh()
            kotlinx.coroutines.delay(200)
            state.endRefresh()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .pointerInput(onPaneClick) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.type == PointerEventType.Press) {
                            onPaneClick()
                        }
                    }
                }
            },
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = if (isActive) 1.dp else 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(state.nestedScrollConnection)
            ) {
                // Sleek compact curved wave pull-to-refresh header without icon
                val offsetPx = state.verticalOffset
                val density = LocalDensity.current

                if (offsetPx > 0f) {
                    val heightDp = with(density) { (offsetPx * 0.45f).coerceAtMost(120f).toDp() }
                    val waveColor = MaterialTheme.colorScheme.primaryContainer

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heightDp)
                            .align(Alignment.TopCenter)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val height = size.height

                            val path = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(width, 0f)
                                lineTo(width, height * 0.2f)
                                quadraticBezierTo(
                                    x1 = width / 2f,
                                    y1 = height * 0.75f,
                                    x2 = 0f,
                                    y2 = height * 0.2f
                                )
                                close()
                            }

                            drawPath(
                                path = path,
                                color = waveColor
                            )
                        }
                    }
                }

                FilePaneListContent(
                    targetPath = paneState.path,
                    paneState = paneState,
                    verticalOffset = state.verticalOffset,
                    densityScale = densityScale,
                    isAnimationEnabled = isAnimationEnabled,
                    onPathClick = onPathClick,
                    onFileClick = onFileClick,
                    onFileLongClick = onFileLongClick,
                    onFileSwipe = onFileSwipe
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilePaneListContent(
    targetPath: String,
    paneState: PaneState,
    verticalOffset: Float,
    densityScale: Float,
    isAnimationEnabled: Boolean,
    onPathClick: (String) -> Unit,
    onFileClick: (FileEntry) -> Unit,
    onFileLongClick: (FileEntry) -> Unit,
    onFileSwipe: (Int) -> Unit
) {
    val navTimestamp = remember(targetPath) { System.currentTimeMillis() }
    val listState = rememberLazyListState()
    val isScrolling = listState.isScrollInProgress

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = verticalOffset
                }
        ) {
            // "Go Up" directory item (..)
            if (targetPath != "/" && targetPath != "") {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val parentFile = java.io.File(targetPath).parent
                                if (parentFile != null) {
                                    onPathClick(parentFile)
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Go up",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "..",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Parent Directory",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }
            }

            if (paneState.files.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Empty Directory",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = paneState.files,
                    key = { _, file -> file.path },
                    contentType = { _, file -> if (file.isDirectory) 1 else 0 }
                ) { index, file ->
                    CascadeAnimatedFileRow(
                        fileEntry = file,
                        index = index,
                        navTimestamp = navTimestamp,
                        targetPath = targetPath,
                        isScrolling = isScrolling,
                        isAnimationEnabled = isAnimationEnabled,
                        isSelected = paneState.selected.contains(file.path),
                        onClick = { onFileClick(file) },
                        onLongClick = { onFileLongClick(file) },
                        onSwipe = { onFileSwipe(index) },
                        densityScale = densityScale,
                        showThumbnails = showThumbnails
                    )
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }
            }
        }

        FastScrollbar(
            listState = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun FastScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount

    // Requirement: Show fast scrollbar ONLY if files/folders count >= 90
    if (totalItems < 90) return

    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var isDragging by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }

    val isScrollInProgress = listState.isScrollInProgress

    // Requirement: Auto-hide after 2 seconds (2000ms) of inactivity
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, isScrollInProgress, isDragging) {
        if (isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(2000L)
            if (!listState.isScrollInProgress && !isDragging) {
                isVisible = false
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "FastScrollbarAlpha"
    )

    if (alpha <= 0.01f && !isVisible) return

    // Requirement: Highlight when grabbed/dragged
    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 6.dp,
        animationSpec = tween(durationMillis = 150),
        label = "FastScrollbarWidth"
    )

    val thumbColor = if (isDragging) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }

    val firstVisibleIndex = listState.firstVisibleItemIndex
    val scrollFraction = (firstVisibleIndex.toFloat() / (totalItems - 1).coerceAtLeast(1)).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp) // Generous touch target area on the right edge
            .graphicsLayer { this.alpha = alpha }
            .pointerInput(totalItems) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        isVisible = true
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        val fraction = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                        val targetIndex = (fraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex)
                        }
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                        val targetIndex = (fraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex)
                        }
                    }
                )
            }
    ) {
        val containerHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightDp = 48.dp
        val density = LocalDensity.current
        val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
        val maxOffsetPx = (containerHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val thumbOffsetPx = scrollFraction * maxOffsetPx

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbOffsetPx.toInt()) }
                .size(width = thumbWidth, height = thumbHeightDp)
                .background(
                    color = thumbColor,
                    shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                )
        )
    }
}

@Composable
private fun CascadeAnimatedFileRow(
    fileEntry: FileEntry,
    index: Int,
    navTimestamp: Long,
    targetPath: String,
    isScrolling: Boolean,
    isAnimationEnabled: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipe: () -> Unit,
    densityScale: Float,
    showThumbnails: Boolean = true
) {
    if (!isAnimationEnabled) {
        FileRow(
            fileEntry = fileEntry,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
            onSwipe = onSwipe,
            densityScale = densityScale,
            showThumbnails = showThumbnails
        )
        return
    }

    val animatable = remember(targetPath) { Animatable(0f) }

    LaunchedEffect(targetPath, fileEntry.path, isScrolling) {
        val elapsed = System.currentTimeMillis() - navTimestamp
        if (elapsed > 200L || isScrolling) {
            animatable.snapTo(1f)
        } else {
            val delayMs = (index * 20).coerceAtMost(180)
            if (delayMs > 0) {
                kotlinx.coroutines.delay(delayMs.toLong())
            }
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 180,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    val alphaVal = animatable.value
    val transYVal = (1f - animatable.value) * 16f

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = alphaVal
            translationY = transYVal
        }
    ) {
        FileRow(
            fileEntry = fileEntry,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
            onSwipe = onSwipe,
            densityScale = densityScale,
            showThumbnails = showThumbnails
        )
    }
}

