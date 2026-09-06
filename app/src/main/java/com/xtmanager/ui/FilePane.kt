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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
                        densityScale = densityScale
                    )
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }
            }
        }

        SmartScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp, top = 4.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun SmartScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItemsInfo = layoutInfo.visibleItemsInfo

    if (totalItems == 0 || visibleItemsInfo.isEmpty()) return

    val visibleCount = visibleItemsInfo.size
    // Smart Dynamic Condition: Only show scrollbar if total items > visible items (overflowing content)
    if (totalItems <= visibleCount) return

    val firstVisible = visibleItemsInfo.first()
    val isScrolling = listState.isScrollInProgress

    val alpha by animateFloatAsState(
        targetValue = if (isScrolling) 0.85f else 0.35f,
        animationSpec = tween(durationMillis = 300),
        label = "ScrollbarAlpha"
    )

    val viewportHeight = layoutInfo.viewportSize.height.toFloat()
    if (viewportHeight <= 0f) return

    val avgItemHeight = visibleItemsInfo.sumOf { it.size }.toFloat() / visibleCount
    val totalHeightEstimate = avgItemHeight * totalItems
    val currentScrollOffset = firstVisible.index * avgItemHeight + firstVisible.offset.coerceAtLeast(0)

    val thumbHeight = (viewportHeight * (viewportHeight / totalHeightEstimate)).coerceIn(40f, viewportHeight * 0.35f)
    val maxScrollOffset = (totalHeightEstimate - viewportHeight).coerceAtLeast(1f)
    val thumbOffset = ((currentScrollOffset / maxScrollOffset) * (viewportHeight - thumbHeight)).coerceIn(0f, viewportHeight - thumbHeight)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(4.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationY = thumbOffset
            }
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                shape = RoundedCornerShape(2.dp)
            )
    )
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
    densityScale: Float
) {
    if (!isAnimationEnabled) {
        FileRow(
            fileEntry = fileEntry,
            isSelected = isSelected,
            onClick = onClick,
            onLongClick = onLongClick,
            onSwipe = onSwipe,
            densityScale = densityScale
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
            densityScale = densityScale
        )
    }
}

