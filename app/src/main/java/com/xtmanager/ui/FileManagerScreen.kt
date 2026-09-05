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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.FileType
import com.xtmanager.core.model.Operation
import com.xtmanager.core.model.OperationStatus
import com.xtmanager.core.model.PaneState
import com.xtmanager.core.model.PaneType
import com.xtmanager.ui.dialogs.ConfirmDialog
import com.xtmanager.ui.dialogs.CreateDialog
import com.xtmanager.ui.dialogs.RenameDialog
import com.xtmanager.ui.dialogs.FileContextMenuDialog
import com.xtmanager.ui.dialogs.CompressDialog
import com.xtmanager.ui.dialogs.ExtractDialog
import com.xtmanager.ui.SettingsScreen
import androidx.compose.material.icons.filled.Settings
import com.xtmanager.viewmodel.FileManagerViewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    var showJumpToPathDialog by remember { mutableStateOf(false) }
    var showTerminal by remember { mutableStateOf(false) }
    var terminalInitialPath by remember { mutableStateOf<String?>(null) }
    var contextMenuTargetFile by remember { mutableStateOf<FileEntry?>(null) }
    var showSingleDeleteDialog by remember { mutableStateOf<FileEntry?>(null) }
    var showCompressDialogSources by remember { mutableStateOf<List<String>?>(null) }
    var showExtractDialogPath by remember { mutableStateOf<String?>(null) }

    var topMenuExpanded by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableStateOf(0L) }

    // Intercept Back button:
    // 1. Density Preview Screen open -> close density preview screen
    // 2. Settings Screen open -> close settings screen
    // 3. Selection mode active -> cancel selection
    // 4. Drawer open -> close drawer
    // 5. Can go back in history -> go back in history
    // 6. Has parent folder -> navigate to parent directory
    // 7. At Root directory level -> double back press confirmation to exit app
    BackHandler(enabled = true) {
        when {
            showDensityPreviewScreen -> {
                showDensityPreviewScreen = false
            }
            showSettingsScreen -> {
                showSettingsScreen = false
            }
            activeState.isSelectionMode -> {
                viewModel.clearSelection(activePane)
            }
            drawerState.isOpen -> {
                scope.launch { drawerState.close() }
            }
            activeState.canGoBack -> {
                viewModel.goBack(activePane)
            }
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
        ModalNavigationDrawer(
            drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Xt-manager",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "v0.1.0 (Alpha Release)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Group 1: Local Storage
                    Text(
                        text = "Local Storage",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                        label = { Text("Root (/)") },
                        selected = activeState.path == "/",
                        onClick = {
                            viewModel.navigateTo(activePane, "/")
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    
                    val storageVolumes = remember(context) { getStorageVolumesList(context) }
                    for (vol in storageVolumes) {
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = if (vol.isRemovable) Icons.Default.SdCard else Icons.Default.Folder,
                                    contentDescription = null
                                )
                            },
                            label = { Text(vol.name) },
                            selected = activeState.path == vol.path,
                            onClick = {
                                viewModel.navigateTo(activePane, vol.path)
                                scope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Group 2: Tools
                    Text(
                        text = "Tools",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                        label = { Text("Terminal") },
                        selected = showTerminal,
                        onClick = {
                            showTerminal = true
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))

                    // Active background operations quick button
                    if (operations.isNotEmpty()) {
                        Button(
                            onClick = { 
                                showOperationsDialog = true
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Active Operations (${operations.size})")
                        }
                    }
                }
            }
        }
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
                                        android.widget.Toast.makeText(context, "Path copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            ) {
                                Text(
                                    text = pathText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                                text = "Xt-manager",
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
                        // Background Operations Indicator Button
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
                        // Rename the first selected file
                        val selectedPath = activeState.selected.firstOrNull()
                        val file = activeState.files.firstOrNull { it.path == selectedPath }
                        if (file != null) {
                            showRenameDialog = file
                        }
                    },
                    onDelete = { showDeleteConfirmDialog = true },
                    onMenuClick = { scope.launch { drawerState.open() } }
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
                // Split Pane Layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    FilePane(
                        paneState = leftPaneState,
                        isActive = activePane == PaneType.LEFT,
                        densityScale = densityScale,
                        onFileClick = { file ->
                            viewModel.setActivePane(PaneType.LEFT)
                            if (leftPaneState.isSelectionMode) {
                                viewModel.toggleFileSelection(PaneType.LEFT, file.path)
                            } else {
                                val isArchive = file.type == FileType.ARCHIVE || 
                                    listOf(
                                        ".zip", ".apk", ".7z", ".tar", ".gz", ".bz2", ".xz", ".zst", ".lz4",
                                        ".tgz", ".tbz2", ".txz", ".tzst", ".tlz4", ".rar", ".cab", ".iso", ".cpio"
                                    ).any { file.name.lowercase().endsWith(it) }
                                if (file.isDirectory || isArchive) {
                                    viewModel.navigateTo(PaneType.LEFT, file.path)
                                } else {
                                    viewModel.toggleFileSelection(PaneType.LEFT, file.path)
                                }
                            }
                        },
                        onFileLongClick = { file ->
                            viewModel.setActivePane(PaneType.LEFT)
                            contextMenuTargetFile = file
                        },
                        onPathClick = { path ->
                            viewModel.setActivePane(PaneType.LEFT)
                            viewModel.navigateTo(PaneType.LEFT, path)
                        },
                        onPaneClick = { viewModel.setActivePane(PaneType.LEFT) },
                        onRefresh = { viewModel.refreshPane(PaneType.LEFT) },
                        onFileSwipe = { index ->
                            viewModel.setActivePane(PaneType.LEFT)
                            viewModel.handleSwipe(PaneType.LEFT, index)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 2.dp)
                    )
                    
                    FilePane(
                        paneState = rightPaneState,
                        isActive = activePane == PaneType.RIGHT,
                        densityScale = densityScale,
                        onFileClick = { file ->
                            viewModel.setActivePane(PaneType.RIGHT)
                            if (rightPaneState.isSelectionMode) {
                                viewModel.toggleFileSelection(PaneType.RIGHT, file.path)
                            } else {
                                val isArchive = file.type == FileType.ARCHIVE || 
                                    listOf(
                                        ".zip", ".apk", ".7z", ".tar", ".gz", ".bz2", ".xz", ".zst", ".lz4",
                                        ".tgz", ".tbz2", ".txz", ".tzst", ".tlz4", ".rar", ".cab", ".iso", ".cpio"
                                    ).any { file.name.lowercase().endsWith(it) }
                                if (file.isDirectory || isArchive) {
                                    viewModel.navigateTo(PaneType.RIGHT, file.path)
                                } else {
                                    viewModel.toggleFileSelection(PaneType.RIGHT, file.path)
                                }
                            }
                        },
                        onFileLongClick = { file ->
                            viewModel.setActivePane(PaneType.RIGHT)
                            contextMenuTargetFile = file
                        },
                        onPathClick = { path ->
                            viewModel.setActivePane(PaneType.RIGHT)
                            viewModel.navigateTo(PaneType.RIGHT, path)
                        },
                        onPaneClick = { viewModel.setActivePane(PaneType.RIGHT) },
                        onRefresh = { viewModel.refreshPane(PaneType.RIGHT) },
                        onFileSwipe = { index ->
                            viewModel.setActivePane(PaneType.RIGHT)
                            viewModel.handleSwipe(PaneType.RIGHT, index)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 2.dp)
                    )
                }

                // Floating Operations Progress Card Overlay
                val runningOps = operations.filter { it.status == com.xtmanager.core.model.OperationStatus.RUNNING || it.status == com.xtmanager.core.model.OperationStatus.PENDING }
                if (runningOps.isNotEmpty()) {
                    val activeOp = runningOps.first()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .clickable { showOperationsDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${activeOp.type.name}: ${File(activeOp.source).name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = activeOp.formattedProgress,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = activeOp.progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (activeOp.currentFileName.isNotEmpty()) "Processing: ${activeOp.currentFileName}" else "Processing...",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = activeOp.formattedProcessedSize,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
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
        AlertDialog(
            onDismissRequest = { showOperationsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Background Tasks")
                    IconButton(onClick = { showOperationsDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Box(modifier = Modifier.size(width = 300.dp, height = 400.dp)) {
                    if (operations.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No active operations", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(operations) { op ->
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${op.type.name}: ${File(op.source).name}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = op.status.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = when (op.status) {
                                                    OperationStatus.RUNNING -> MaterialTheme.colorScheme.primary
                                                    OperationStatus.COMPLETED -> Color.Green
                                                    OperationStatus.FAILED -> MaterialTheme.colorScheme.error
                                                    OperationStatus.CANCELLED -> Color(0xFFF59E0B)
                                                    else -> Color.Gray
                                                }
                                            )
                                            if (op.status == OperationStatus.RUNNING) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = { viewModel.cancelOperation(op.id) },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Cancel Operation",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (op.status == OperationStatus.RUNNING) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Processing: ${op.currentFileName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = op.progress,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(op.formattedProcessedSize, style = MaterialTheme.typography.bodySmall)
                                            Text(op.formattedProgress, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    if (op.status == OperationStatus.FAILED && op.error != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Error: ${op.error}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider()
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCompletedOperations()
                    }
                ) {
                    Text("Clear Completed")
                }
            }
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

    // Animated Terminal Screen Overlay (Right-to-Left Slide)
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

    // Animated Settings Screen Overlay (Right-to-Left Slide)
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
            densityScale = densityScale,
            onOpenDensityPreview = { showDensityPreviewScreen = true },
            onClose = { showSettingsScreen = false },
            modifier = Modifier.fillMaxSize()
        )
    }

    // Animated Pane Density Preview Screen Overlay (Right-to-Left Slide)
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
            currentDensity = densityScale,
            onDensityChange = { viewModel.setDensityScale(it) },
            onClose = { showDensityPreviewScreen = false },
            modifier = Modifier.fillMaxSize()
        )
    }

    // 2-Column Floating Context Menu Dialog on Long Press
    contextMenuTargetFile?.let { file ->
        FileContextMenuDialog(
            fileEntry = file,
            onDismiss = { contextMenuTargetFile = null },
            onCopy = {
                val destPath = inactiveState.path
                viewModel.copyPaths(listOf(file.path), destPath)
                showOperationsDialog = true
                contextMenuTargetFile = null
            },
            onMove = {
                val destPath = inactiveState.path
                viewModel.movePaths(listOf(file.path), destPath)
                showOperationsDialog = true
                contextMenuTargetFile = null
            },
            onDelete = {
                showSingleDeleteDialog = file
                contextMenuTargetFile = null
            },
            onRename = {
                showRenameDialog = file
                contextMenuTargetFile = null
            },
            onCompress = {
                showCompressDialogSources = listOf(file.path)
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
        modifier = modifier.fillMaxSize().padding(24.dp),
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
                text = "Xt-manager needs storage permissions to list, manage, and execute file operations on your device storage. Please grant the permission to proceed.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
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

data class StorageVolumeInfo(
    val name: String,
    val path: String,
    val isRemovable: Boolean
)

fun getStorageVolumesList(context: android.content.Context): List<StorageVolumeInfo> {
    val list = mutableListOf<StorageVolumeInfo>()

    // Internal Storage
    list.add(StorageVolumeInfo("Internal Storage", "/storage/emulated/0", isRemovable = false))

    try {
        val sm = context.getSystemService(android.content.Context.STORAGE_SERVICE) as? android.os.storage.StorageManager
        if (sm != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val volumes = sm.storageVolumes
            for (vol in volumes) {
                if (vol.state == android.os.Environment.MEDIA_MOUNTED || vol.state == android.os.Environment.MEDIA_MOUNTED_READ_ONLY) {
                    val dir = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        vol.directory?.absolutePath
                    } else {
                        try {
                            val getPathMethod = vol.javaClass.getMethod("getPath")
                            getPathMethod.invoke(vol) as? String
                        } catch (_: Exception) { null }
                    }

                    if (dir != null && dir != "/storage/emulated/0" && dir != "/storage/self/primary") {
                        val name = vol.getDescription(context) ?: if (vol.isRemovable) "External SD / USB" else "Storage"
                        if (list.none { it.path == dir }) {
                            list.add(StorageVolumeInfo(name, dir, isRemovable = vol.isRemovable))
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {}

    // Fallback scan of /storage directory
    try {
        val storageDir = File("/storage")
        if (storageDir.exists() && storageDir.isDirectory) {
            val children = storageDir.listFiles()
            if (children != null) {
                for (file in children) {
                    val name = file.name
                    if (name != "emulated" && name != "self" && file.isDirectory && file.canRead()) {
                        if (list.none { it.path == file.absolutePath }) {
                            val displayName = if (name.matches(Regex("[0-9A-FA-f]{4}-[0-9A-FA-f]{4}"))) "SD Card ($name)" else "Storage ($name)"
                            list.add(StorageVolumeInfo(displayName, file.absolutePath, isRemovable = true))
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {}

    return list
}

