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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
    folderAnimationStyle: String = "slide",
    showThumbnails: Boolean = true,
    fileNameMaxLines: Int = 2,
    activePaneHighlightEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isActive && activePaneHighlightEnabled) {
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
        color = if (isActive && activePaneHighlightEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = if (isActive && activePaneHighlightEnabled) 1.dp else 0.dp
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

                val activeStyle = if (isAnimationEnabled) folderAnimationStyle else "none"

                if (activeStyle == "slide" || activeStyle == "fade") {
                    AnimatedContent(
                        targetState = paneState.path,
                        transitionSpec = {
                            if (activeStyle == "slide") {
                                (slideInHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(180)))
                                    .togetherWith(slideOutHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(180)))
                            } else {
                                fadeIn(animationSpec = tween(180)).togetherWith(fadeOut(animationSpec = tween(180)))
                            }
                        },
                        label = "PaneFolderTransition"
                    ) { targetPath ->
                        FilePaneListContent(
                            targetPath = targetPath,
                            paneState = paneState,
                            verticalOffset = state.verticalOffset,
                            densityScale = densityScale,
                            isAnimationEnabled = false,
                            showThumbnails = showThumbnails,
                            fileNameMaxLines = fileNameMaxLines,
                            onPathClick = onPathClick,
                            onFileClick = onFileClick,
                            onFileLongClick = onFileLongClick,
                            onFileSwipe = onFileSwipe
                        )
                    }
                } else {
                    FilePaneListContent(
                        targetPath = paneState.path,
                        paneState = paneState,
                        verticalOffset = state.verticalOffset,
                        densityScale = densityScale,
                        isAnimationEnabled = activeStyle == "cascade",
                        showThumbnails = showThumbnails,
                        fileNameMaxLines = fileNameMaxLines,
                        onPathClick = onPathClick,
                        onFileClick = onFileClick,
                        onFileLongClick = onFileLongClick,
                        onFileSwipe = onFileSwipe
                    )
                }
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
    showThumbnails: Boolean = true,
    fileNameMaxLines: Int = 2,
    onPathClick: (String) -> Unit,
    onFileClick: (FileEntry) -> Unit,
    onFileLongClick: (FileEntry) -> Unit,
    onFileSwipe: (Int) -> Unit
) {
    val navTimestamp = remember(targetPath) { System.currentTimeMillis() }

    // Cache scroll positions (index, scrollOffset) per path
    val scrollPositions = remember { mutableMapOf<String, Pair<Int, Int>>() }

    // Track previous path to detect navigation direction (forward into subfolder vs backward to parent)
    val previousPathRef = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(targetPath) {
        val prevPath = previousPathRef.value
        if (prevPath != null && prevPath != targetPath) {
            val formattedParent = if (targetPath.endsWith("/")) targetPath else "$targetPath/"
            val isNavigatingUp = prevPath.startsWith(formattedParent)
            if (!isNavigatingUp) {
                // Forward navigation into a subfolder -> reset subfolder scroll position to top (0, 0)
                scrollPositions[targetPath] = Pair(0, 0)
            }
        }
        previousPathRef.value = targetPath
    }

    val (savedIndex, savedOffset) = scrollPositions[targetPath] ?: Pair(0, 0)
    val listState = remember(targetPath) {
        LazyListState(
            firstVisibleItemIndex = savedIndex,
            firstVisibleItemScrollOffset = savedOffset
        )
    }

    // Save scroll position for current targetPath as user scrolls without triggering recompositions
    LaunchedEffect(targetPath, listState) {
        snapshotFlow { Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .distinctUntilChanged()
            .collect { (idx, offset) ->
                scrollPositions[targetPath] = Pair(idx, offset)
            }
    }

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
                    key = { index, file -> "${file.path}_$index" },
                    contentType = { _, file -> if (file.isDirectory) 1 else 0 }
                ) { index, file ->
                    CascadeAnimatedFileRow(
                        fileEntry = file,
                        index = index,
                        navTimestamp = navTimestamp,
                        targetPath = targetPath,
                        isAnimationEnabled = isAnimationEnabled,
                        isScrollInProgress = listState.isScrollInProgress,
                        isSelected = paneState.selected.contains(file.path),
                        onClick = { onFileClick(file) },
                        onLongClick = { onFileLongClick(file) },
                        onSwipe = { onFileSwipe(index) },
                        densityScale = densityScale,
                        showThumbnails = showThumbnails,
                        fileNameMaxLines = fileNameMaxLines
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
    val totalItems by remember { derivedStateOf { listState.layoutInfo.totalItemsCount } }

    // Requirement: Show fast scrollbar ONLY if files/folders count >= 90
    if (totalItems < 90) return

    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var isDragging by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }

    // Auto-hide after 2 seconds (2000ms) of inactivity, using snapshotFlow to avoid keying on offset changes
    LaunchedEffect(listState, isDragging) {
        snapshotFlow { listState.isScrollInProgress || isDragging }
            .collect { active ->
                if (active) {
                    isVisible = true
                } else {
                    delay(2000L)
                    if (!listState.isScrollInProgress && !isDragging) {
                        isVisible = false
                    }
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

    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
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
    isAnimationEnabled: Boolean,
    isScrollInProgress: Boolean = false,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipe: () -> Unit,
    densityScale: Float,
    showThumbnails: Boolean = true,
    fileNameMaxLines: Int = 2
) {
    if (!isAnimationEnabled) {
        FileRow(
            fileEntry = fileEntry,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
            onSwipe = onSwipe,
            densityScale = densityScale,
            showThumbnails = showThumbnails,
            fileNameMaxLines = fileNameMaxLines
        )
        return
    }

    val animatable = remember(targetPath) { Animatable(0f) }

    LaunchedEffect(targetPath, fileEntry.path) {
        if (animatable.value < 1f) {
            val isInitialNav = System.currentTimeMillis() - navTimestamp < 250L
            if (isScrollInProgress || !isInitialNav) {
                animatable.snapTo(1f)
            } else {
                val delayMs = (index * 15L).coerceAtMost(180L)
                if (delayMs > 0) {
                    kotlinx.coroutines.delay(delayMs)
                }
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 150,
                        easing = FastOutSlowInEasing
                    )
                )
            }
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
            showThumbnails = showThumbnails,
            fileNameMaxLines = fileNameMaxLines
        )
    }
}
