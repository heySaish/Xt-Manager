package com.xtmanager.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.xtmanager.ui.dialogs.PatternSelectionDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.FileType
import com.xtmanager.core.model.OperationStatus
import com.xtmanager.core.model.PaneType
import com.xtmanager.ui.components.DualPaneView
import com.xtmanager.ui.dialogs.ApkInstallDialog
import com.xtmanager.ui.dialogs.CompressDialog
import com.xtmanager.ui.dialogs.ConfirmDialog
import com.xtmanager.ui.dialogs.CreateDialog
import com.xtmanager.ui.dialogs.ExtractDialog
import com.xtmanager.ui.dialogs.FileContextMenuDialog
import com.xtmanager.ui.dialogs.LogViewerDialog
import com.xtmanager.ui.dialogs.OperationProgressCard
import com.xtmanager.ui.dialogs.OperationProgressDialog
import com.xtmanager.ui.dialogs.RenameDialog
import com.xtmanager.ui.drawer.AppNavigationDrawer
import com.xtmanager.viewmodel.FileManagerViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel,
    isPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val leftPaneState by viewModel.leftPaneState.collectAsState()
    val rightPaneState by viewModel.rightPaneState.collectAsState()
    val activePane by viewModel.activePane.collectAsState()
    val operations by viewModel.operations.collectAsState()
    
    val showHiddenFiles by viewModel.showHiddenFiles.collectAsState()
    val densityScale by viewModel.densityScale.collectAsState()
    val bottomBarScale by viewModel.bottomBarScale.collectAsState()
    val folderAnimationEnabled by viewModel.folderAnimationEnabled.collectAsState()
    val folderAnimationStyle by viewModel.folderAnimationStyle.collectAsState()
    val naturalSort by viewModel.naturalSort.collectAsState()
    val showThumbnails by viewModel.showThumbnails.collectAsState()
    val fileNameMaxLines by viewModel.fileNameMaxLines.collectAsState()
    val activePaneHighlightEnabled by viewModel.activePaneHighlightEnabled.collectAsState()
    val isRootEnabled by viewModel.isRootEnabled.collectAsState()

    val activeState = if (activePane == PaneType.LEFT) leftPaneState else rightPaneState
    val inactivePane = if (activePane == PaneType.LEFT) PaneType.RIGHT else PaneType.LEFT
    val inactiveState = if (activePane == PaneType.LEFT) rightPaneState else leftPaneState

    val context = LocalContext.current

    // Dialog trigger states
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<FileEntry?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showOperationsDialog by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showDensityPreviewScreen by remember { mutableStateOf(false) }
    var showBottomBarSizePreviewScreen by remember { mutableStateOf(false) }
    var showDisplayThemeSettingsScreen by remember { mutableStateOf(false) }
    var showJumpToPathDialog by remember { mutableStateOf(false) }
    var showTerminal by remember { mutableStateOf(false) }
    var terminalInitialPath by remember { mutableStateOf<String?>(null) }
    var contextMenuTargetFile by remember { mutableStateOf<FileEntry?>(null) }
    var showSingleDeleteDialog by remember { mutableStateOf<FileEntry?>(null) }
    var showCompressDialogSources by remember { mutableStateOf<List<String>?>(null) }
    var showExtractDialogPath by remember { mutableStateOf<String?>(null) }
    var showApkInstallDialog by remember { mutableStateOf<FileEntry?>(null) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showPatternSelectionDialog by remember { mutableStateOf(false) }

    var topMenuExpanded by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableStateOf(0L) }

    // Intercept Back button
    BackHandler(enabled = true) {
        when {
            showPatternSelectionDialog -> showPatternSelectionDialog = false
            showBottomBarSizePreviewScreen -> showBottomBarSizePreviewScreen = false
            showDensityPreviewScreen -> showDensityPreviewScreen = false
            showDisplayThemeSettingsScreen -> showDisplayThemeSettingsScreen = false
            showSettingsScreen -> showSettingsScreen = false
            showJumpToPathDialog -> showJumpToPathDialog = false
            activeState.isSelectionMode -> viewModel.clearSelection(activePane)
            drawerState.isOpen -> scope.launch { drawerState.close() }
            File(activeState.path).parentFile != null && activeState.path != "/" -> {
                val parentPath = File(activeState.path).parentFile?.absolutePath ?: "/"
                viewModel.navigateTo(activePane, parentPath)
            }
            else -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressTime = currentTime
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AppNavigationDrawer(
            drawerState = drawerState,
            activePath = activeState.path,
            operationsCount = operations.size,
            showTerminal = showTerminal,
            onNavigateTo = { path -> viewModel.navigateTo(activePane, path) },
            onOpenTerminal = { showTerminal = true },
            onOpenOperations = { showOperationsDialog = true }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            if (isPermissionGranted) {
                                val pathText = remember(activeState.path) {
                                    val p = activeState.path
                                    if (p.endsWith("/")) p else "$p/"
                                }
                                val folderCount = activeState.files.count { it.isDirectory }
                                val fileCount = activeState.files.count { !it.isDirectory }
                                val diskInfo = remember(activeState.path) {
                                    getDiskInfo(activeState.path)
                                }

                                val selectedCount = activeState.selected.size
                                val subtitleText = if (selectedCount > 0) {
                                    "Selected: $selectedCount  Folders: $folderCount  Files: $fileCount  Disk: $diskInfo"
                                } else {
                                    "Folders: $folderCount  Files: $fileCount  Disk: $diskInfo"
                                }

                                Column(
                                    modifier = Modifier.combinedClickable(
                                        onClick = { showJumpToPathDialog = true },
                                        onLongClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Path", activeState.path)
                                            clipboard?.setPrimaryClip(clip)
                                            Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                ) {
                                    StartEllipsisText(
                                        text = pathText,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Text(
                                    text = "XT Manager",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            if (activeState.isSelectionMode || activeState.selected.isNotEmpty()) {
                                // 1. Select All
                                IconButton(onClick = { viewModel.selectAll(activePane) }) {
                                    Icon(
                                        imageVector = Icons.Default.SelectAll,
                                        contentDescription = "Select All",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // 2. Invert Select
                                IconButton(onClick = { viewModel.invertSelection(activePane) }) {
                                    Icon(
                                        imageVector = Icons.Default.FlipToBack,
                                        contentDescription = "Invert Select",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // 3. Cancel
                                IconButton(onClick = { viewModel.clearSelection(activePane) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel Selection",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }

                                // 4. Pattern Selection (Smart extension extraction or dialog fallback)
                                IconButton(onClick = {
                                    if (activeState.selected.isNotEmpty()) {
                                        val selectedFile = activeState.files.firstOrNull { activeState.selected.contains(it.path) }
                                        if (selectedFile != null) {
                                            val ext = selectedFile.name.substringAfterLast('.', "")
                                            if (ext.isNotEmpty() && ext != selectedFile.name) {
                                                val pattern = "*.$ext"
                                                viewModel.selectByPattern(activePane, pattern)
                                                Toast.makeText(context, "Auto-selected matching $pattern", Toast.LENGTH_SHORT).show()
                                            } else {
                                                showPatternSelectionDialog = true
                                            }
                                        } else {
                                            showPatternSelectionDialog = true
                                        }
                                    } else {
                                        showPatternSelectionDialog = true
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Pattern Selection",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // 5. Placeholder (Reserved for future smart filters like date/size)
                                IconButton(onClick = {
                                    Toast.makeText(context, "Placeholder button (Reserved for future smart filters)", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Extension,
                                        contentDescription = "Placeholder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            val activeOpsCount = operations.count { it.status == OperationStatus.RUNNING }
                            if (activeOpsCount > 0) {
                                IconButton(onClick = { showOperationsDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Build, 
                                        contentDescription = "Operations",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            IconButton(onClick = {
                                terminalInitialPath = activeState.path
                                showTerminal = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = "Open in Terminal",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(onClick = { topMenuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            
                            DropdownMenu(
                                expanded = topMenuExpanded,
                                onDismissRequest = { topMenuExpanded = false }
                            ) {
                                if (activeState.isSelectionMode || activeState.selected.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("1. Select All")
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectAll(activePane)
                                            topMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.FlipToBack, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("2. Invert Select")
                                            }
                                        },
                                        onClick = {
                                            viewModel.invertSelection(activePane)
                                            topMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("3. Cancel Selection")
                                            }
                                        },
                                        onClick = {
                                            viewModel.clearSelection(activePane)
                                            topMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("4. Pattern Selection")
                                            }
                                        },
                                        onClick = {
                                            showPatternSelectionDialog = true
                                            topMenuExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("5. Placeholder Action")
                                            }
                                        },
                                        onClick = {
                                            Toast.makeText(context, "Placeholder button (Reserved for future smart filters)", Toast.LENGTH_SHORT).show()
                                            topMenuExpanded = false
                                        }
                                    )
                                    androidx.compose.material3.Divider()
                                }
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (showHiddenFiles) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (showHiddenFiles) "Hide Hidden Files" else "Show Hidden Files")
                                        }
                                    },
                                    onClick = {
                                        viewModel.toggleShowHiddenFiles()
                                        topMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Background Tasks")
                                        }
                                    },
                                    onClick = {
                                        showOperationsDialog = true
                                        topMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("View Telemetry Logs")
                                        }
                                    },
                                    onClick = {
                                        showLogsDialog = true
                                        topMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Settings")
                                        }
                                    },
                                    onClick = {
                                        showSettingsScreen = true
                                        topMenuExpanded = false
                                    }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                bottomBar = {
                    BottomBar(
                        hasSelection = activeState.selected.isNotEmpty(),
                        canGoBack = activeState.canGoBack,
                        canGoForward = activeState.canGoForward,
                        onGoBack = { viewModel.goBack(activePane) },
                        onGoForward = { viewModel.goForward(activePane) },
                        onGoUp = {
                            val parentFile = File(activeState.path).parent
                            if (parentFile != null) {
                                viewModel.navigateTo(activePane, parentFile)
                            }
                        },
                        onCreateFolder = { showCreateDialog = true },
                        onSyncPath = { viewModel.navigateTo(inactivePane, activeState.path) },
                        onCopy = { 
                            viewModel.copySelected(activePane)
                            showOperationsDialog = true
                        },
                        onMove = { 
                            viewModel.moveSelected(activePane)
                            showOperationsDialog = true
                        },
                        onRename = {
                            val selectedPath = activeState.selected.firstOrNull()
                            val file = activeState.files.firstOrNull { it.path == selectedPath }
                            if (file != null) {
                                showRenameDialog = file
                            }
                        },
                        onDelete = { showDeleteConfirmDialog = true },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        scale = bottomBarScale
                    )
                }
            ) { paddingValues ->
                if (!isPermissionGranted) {
                    PermissionRequestScreen(
                        onRequestPermission = onRequestPermission,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .padding(4.dp)
                    ) {
                        DualPaneView(
                            leftPaneState = leftPaneState,
                            rightPaneState = rightPaneState,
                            activePane = activePane,
                            densityScale = densityScale,
                            folderAnimationEnabled = folderAnimationEnabled,
                            folderAnimationStyle = folderAnimationStyle,
                            showThumbnails = showThumbnails,
                            fileNameMaxLines = fileNameMaxLines,
                            activePaneHighlightEnabled = activePaneHighlightEnabled,
                            onFileClick = { pane, file ->
                                viewModel.setActivePane(pane)
                                val state = if (pane == PaneType.LEFT) leftPaneState else rightPaneState
                                if (state.isSelectionMode) {
                                    viewModel.toggleFileSelection(pane, file.path)
                                } else if (file.name.lowercase().endsWith(".apk")) {
                                    showApkInstallDialog = file
                                } else {
                                    val isArchive = file.type == FileType.ARCHIVE || 
                                        listOf(
                                            ".zip", ".7z", ".tar", ".gz", ".bz2", ".xz", ".zst", ".lz4",
                                            ".tgz", ".tbz2", ".txz", ".tzst", ".tlz4", ".rar", ".cab", ".iso", ".cpio"
                                        ).any { file.name.lowercase().endsWith(it) }
                                    if (file.isDirectory || isArchive) {
                                        viewModel.navigateTo(pane, file.path)
                                    } else {
                                        viewModel.toggleFileSelection(pane, file.path)
                                    }
                                }
                            },
                            onFileLongClick = { pane, file ->
                                viewModel.setActivePane(pane)
                                val state = if (pane == PaneType.LEFT) leftPaneState else rightPaneState
                                if (state.selected.isNotEmpty() && !state.selected.contains(file.path)) {
                                    viewModel.toggleFileSelection(pane, file.path)
                                }
                                contextMenuTargetFile = file
                            },
                            onPathClick = { pane, path ->
                                viewModel.setActivePane(pane)
                                viewModel.navigateTo(pane, path)
                            },
                            onPaneClick = { pane -> viewModel.setActivePane(pane) },
                            onRefresh = { viewModel.refreshBothPanes() },
                            onFileSwipe = { pane, index ->
                                viewModel.setActivePane(pane)
                                viewModel.handleSwipe(pane, index)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        OperationProgressCard(
                            operations = operations,
                            onClick = { showOperationsDialog = true }
                        )
                    }
                }
            }
        }

        // --- DIALOGS IMPLEMENTATION ---

        // Create file / folder dialog
        if (showCreateDialog) {
            CreateDialog(
                title = "Create New",
                currentPath = activeState.path,
                onDismiss = { showCreateDialog = false },
                onCreateFile = { name ->
                    viewModel.createFile(activePane, name)
                    showCreateDialog = false
                },
                onCreateFolder = { name ->
                    viewModel.createDirectory(activePane, name)
                    showCreateDialog = false
                }
            )
        }

        // Rename dialog
        showRenameDialog?.let { file ->
            RenameDialog(
                initialName = file.name,
                onDismiss = { showRenameDialog = null },
                onConfirm = { newName ->
                    viewModel.renameFile(activePane, file.path, newName)
                    showRenameDialog = null
                }
            )
        }

        // Delete confirm dialog
        if (showDeleteConfirmDialog) {
            val selectedCount = activeState.selected.size
            ConfirmDialog(
                title = "Delete Files",
                message = "Are you sure you want to delete $selectedCount items? This action cannot be undone.",
                confirmText = "Delete",
                onDismiss = { showDeleteConfirmDialog = false },
                onConfirm = {
                    viewModel.deleteSelected(activePane)
                    showDeleteConfirmDialog = false
                    showOperationsDialog = true
                }
            )
        }

        // Operations Manager list Dialog
        if (showOperationsDialog) {
            OperationProgressDialog(
                operations = operations,
                onDismiss = { showOperationsDialog = false },
                onCancelOperation = { id -> viewModel.cancelOperation(id) },
                onClearCompleted = { viewModel.clearCompletedOperations() },
                onViewLogs = { showLogsDialog = true }
            )
        }

        // Live Log Viewer Dialog
        if (showLogsDialog) {
            LogViewerDialog(
                onDismissRequest = { showLogsDialog = false }
            )
        }

        // Jump to Path Dialog
        if (showJumpToPathDialog) {
            var editedPath by remember { mutableStateOf(activeState.path) }
            val focusRequester = remember { FocusRequester() }

            AlertDialog(
                onDismissRequest = { showJumpToPathDialog = false },
                title = { Text("Jump to Path") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = editedPath,
                            onValueChange = { editedPath = it },
                            label = { Text("Enter Directory Path") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val path = editedPath.trim()
                            if (path.isNotEmpty()) {
                                viewModel.navigateTo(activePane, path)
                                showJumpToPathDialog = false
                            }
                        }
                    ) {
                        Text("Jump")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showJumpToPathDialog = false }) {
                        Text("Cancel")
                    }
                }
            )

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }

        // Animated Terminal Screen Overlay
        AnimatedVisibility(
            visible = showTerminal,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300))
        ) {
            TerminalScreen(
                onClose = { showTerminal = false },
                initialPath = terminalInitialPath,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Animated Settings Screen Overlay
        AnimatedVisibility(
            visible = showSettingsScreen,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300))
        ) {
            SettingsScreen(
                showHiddenFiles = showHiddenFiles,
                onToggleShowHiddenFiles = { viewModel.toggleShowHiddenFiles() },
                folderAnimationEnabled = folderAnimationEnabled,
                onToggleFolderAnimation = { viewModel.toggleFolderAnimation() },
                naturalSort = naturalSort,
                onToggleNaturalSort = { viewModel.toggleNaturalSort() },
                densityScale = densityScale,
                onOpenDensityPreview = { showDensityPreviewScreen = true },
                bottomBarScale = bottomBarScale,
                onOpenBottomBarSizePreview = { showBottomBarSizePreviewScreen = true },
                onOpenDisplayThemeSettings = { showDisplayThemeSettingsScreen = true },
                isRootEnabled = isRootEnabled,
                onToggleRootAccess = { enable ->
                    viewModel.toggleRootAccess(enable) { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onClose = { showSettingsScreen = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Animated Display & Theme Sub-Settings Screen Overlay
        AnimatedVisibility(
            visible = showDisplayThemeSettingsScreen,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300))
        ) {
            DisplayThemeSettingsScreen(
                showHiddenFiles = showHiddenFiles,
                onToggleShowHiddenFiles = { viewModel.toggleShowHiddenFiles() },
                folderAnimationEnabled = folderAnimationEnabled,
                onToggleFolderAnimation = { viewModel.toggleFolderAnimation() },
                folderAnimationStyle = folderAnimationStyle,
                onSetFolderAnimationStyle = { style -> viewModel.setFolderAnimationStyle(style) },
                naturalSort = naturalSort,
                onToggleNaturalSort = { viewModel.toggleNaturalSort() },
                densityScale = densityScale,
                onOpenDensityPreview = { showDensityPreviewScreen = true },
                bottomBarScale = bottomBarScale,
                onOpenBottomBarSizePreview = { showBottomBarSizePreviewScreen = true },
                showThumbnails = showThumbnails,
                onToggleShowThumbnails = { viewModel.toggleShowThumbnails() },
                fileNameMaxLines = fileNameMaxLines,
                onSetFileNameMaxLines = { lines -> viewModel.setFileNameMaxLines(lines) },
                activePaneHighlightEnabled = activePaneHighlightEnabled,
                onToggleActivePaneHighlight = { viewModel.toggleActivePaneHighlight() },
                onClose = { showDisplayThemeSettingsScreen = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Animated Pane Density Preview Screen Overlay
        AnimatedVisibility(
            visible = showDensityPreviewScreen,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300))
        ) {
            PaneDensityPreviewScreen(
                currentScale = densityScale,
                onApply = { viewModel.setDensityScale(it) },
                onClose = { showDensityPreviewScreen = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Animated Bottom Bar Size Preview Screen Overlay
        AnimatedVisibility(
            visible = showBottomBarSizePreviewScreen,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300))
        ) {
            BottomBarSizePreviewScreen(
                currentScale = bottomBarScale,
                onApply = { viewModel.setBottomBarScale(it) },
                onClose = { showBottomBarSizePreviewScreen = false },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Context Menu Dialog
        contextMenuTargetFile?.let { file ->
            val selectedPaths = if (activeState.selected.contains(file.path) || activeState.selected.isNotEmpty()) {
                activeState.selected.ifEmpty { setOf(file.path) }
            } else {
                setOf(file.path)
            }
            val selectedCount = selectedPaths.size

            FileContextMenuDialog(
                fileEntry = file,
                selectedCount = selectedCount,
                onDismiss = { contextMenuTargetFile = null },
                onCopy = {
                    val destPath = inactiveState.path
                    viewModel.copyPaths(selectedPaths.toList(), destPath)
                    showOperationsDialog = true
                    contextMenuTargetFile = null
                },
                onMove = {
                    val destPath = inactiveState.path
                    viewModel.movePaths(selectedPaths.toList(), destPath)
                    showOperationsDialog = true
                    contextMenuTargetFile = null
                },
                onDelete = {
                    if (selectedCount > 1) {
                        showDeleteConfirmDialog = true
                    } else {
                        showSingleDeleteDialog = file
                    }
                    contextMenuTargetFile = null
                },
                onRename = {
                    showRenameDialog = file
                    contextMenuTargetFile = null
                },
                onCompress = {
                    showCompressDialogSources = selectedPaths.toList()
                    contextMenuTargetFile = null
                },
                onExtractHere = {
                    viewModel.enqueueExtract(file.path, activeState.path)
                    showOperationsDialog = true
                    contextMenuTargetFile = null
                },
                onExtractTo = {
                    showExtractDialogPath = file.path
                    contextMenuTargetFile = null
                },
                onOpenArchive = {
                    viewModel.navigateTo(activePane, file.path)
                    contextMenuTargetFile = null
                }
            )
        }

        // Compress Dialog
        showCompressDialogSources?.let { sources ->
            val defaultName = if (sources.size == 1) {
                val srcFile = File(sources[0])
                "${srcFile.nameWithoutExtension}.zip"
            } else {
                "archive.zip"
            }
            CompressDialog(
                initialName = defaultName,
                onDismiss = { showCompressDialogSources = null },
                onCompress = { archiveName, format, level ->
                    val destPath = File(activeState.path, archiveName).absolutePath
                    viewModel.enqueueCompress(sources, destPath, format, level)
                    showOperationsDialog = true
                    showCompressDialogSources = null
                }
            )
        }

        // Extract Dialog
        showExtractDialogPath?.let { archivePath ->
            val archiveFile = File(archivePath)
            val defaultDest = File(activeState.path, archiveFile.nameWithoutExtension).absolutePath
            ExtractDialog(
                archiveName = archiveFile.name,
                initialDestDir = defaultDest,
                onDismiss = { showExtractDialogPath = null },
                onExtract = { destDir, overwritePolicy ->
                    viewModel.enqueueExtract(archivePath, destDir, overwritePolicy)
                    showOperationsDialog = true
                    showExtractDialogPath = null
                }
            )
        }

        // APK Installation & Info Dialog
        showApkInstallDialog?.let { apkFile ->
            ApkInstallDialog(
                fileEntry = apkFile,
                onDismiss = { showApkInstallDialog = null },
                onViewArchive = {
                    viewModel.navigateTo(activePane, apkFile.path)
                }
            )
        }

        // Pattern Selection Dialog
        if (showPatternSelectionDialog) {
            PatternSelectionDialog(
                onDismiss = { showPatternSelectionDialog = false },
                onConfirm = { pattern ->
                    viewModel.selectByPattern(activePane, pattern)
                    Toast.makeText(context, "Selected pattern: $pattern", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Single item Delete Confirmation Dialog
        showSingleDeleteDialog?.let { file ->
            ConfirmDialog(
                title = "Delete ${file.name}",
                message = "Are you sure you want to delete '${file.name}'? This action cannot be undone.",
                confirmText = "Delete",
                onDismiss = { showSingleDeleteDialog = null },
                onConfirm = {
                    viewModel.deletePaths(listOf(file.path))
                    showSingleDeleteDialog = null
                    showOperationsDialog = true
                }
            )
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = "Storage Access Required",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Storage Access Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "XT Manager needs storage permissions to list, manage, and execute file operations on your device storage. Please grant the permission to proceed.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Storage, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant Storage Access")
            }
        }
    }
}

private fun getDiskInfo(path: String): String {
    return try {
        val file = File(path)
        val target = if (file.exists()) file else File("/storage/emulated/0")
        val stat = android.os.StatFs(target.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong

        val availFormatted = formatBytesToGB(availableBytes)
        val totalFormatted = formatBytesToGB(totalBytes)

        "$availFormatted/$totalFormatted"
    } catch (_: Exception) {
        "0.00G/0.00G"
    }
}

private fun formatBytesToGB(bytes: Long): String {
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.2fG", gb)
}

@Composable
fun StartEllipsisText(
    text: String,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    fontWeight: FontWeight? = FontWeight.Bold,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val maxWidthPx = with(density) { constraints.maxWidth.toFloat() }

        val displayText = remember(text, maxWidthPx, style, fontWeight) {
            if (maxWidthPx <= 0f) return@remember text

            val fullResult = textMeasurer.measure(
                text = text,
                style = style.copy(fontWeight = fontWeight ?: FontWeight.Normal)
            )

            if (fullResult.size.width <= maxWidthPx) {
                text
            } else {
                val prefix = "…"
                var low = 1
                var high = text.length
                var bestFitting = prefix + text.takeLast(low)

                while (low <= high) {
                    val mid = (low + high) / 2
                    val candidate = prefix + text.takeLast(mid)
                    val candidateResult = textMeasurer.measure(
                        text = candidate,
                        style = style.copy(fontWeight = fontWeight ?: FontWeight.Normal)
                    )
                    if (candidateResult.size.width <= maxWidthPx) {
                        bestFitting = candidate
                        low = mid + 1
                    } else {
                        high = mid - 1
                    }
                }
                bestFitting
            }
        }

        Text(
            text = displayText,
            style = style,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}
