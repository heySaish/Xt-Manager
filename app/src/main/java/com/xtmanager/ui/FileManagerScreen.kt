package com.xtmanager.ui

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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xtmanager.archive.ArchiveFormat
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.FileType
import com.xtmanager.core.model.Operation
import com.xtmanager.core.model.OperationStatus
import com.xtmanager.core.model.PaneState
import com.xtmanager.core.model.PaneType
import com.xtmanager.ui.dialogs.ConfirmDialog
import com.xtmanager.ui.dialogs.CreateDialog
import com.xtmanager.ui.dialogs.RenameDialog
import com.xtmanager.viewmodel.FileManagerViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val leftPaneState by viewModel.leftPaneState.collectAsState()
    val rightPaneState by viewModel.rightPaneState.collectAsState()
    val activePane by viewModel.activePane.collectAsState()
    val operations by viewModel.operations.collectAsState()
    
    val showHiddenFiles by viewModel.showHiddenFiles.collectAsState()

    val activeState = if (activePane == PaneType.LEFT) leftPaneState else rightPaneState
    val inactivePane = if (activePane == PaneType.LEFT) PaneType.RIGHT else PaneType.LEFT
    val inactiveState = if (activePane == PaneType.LEFT) rightPaneState else leftPaneState

    // Dialog trigger states
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<FileEntry?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showExtractDialog by remember { mutableStateOf<FileEntry?>(null) }
    var showOperationsDialog by remember { mutableStateOf(false) }
    
    var topMenuExpanded by remember { mutableStateOf(false) }
    var showTerminalDialog by remember { mutableStateOf(false) }

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
                    
                    Text(
                        text = "Local Storage",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                        label = { Text("Root (/) ") },
                        selected = activeState.path == "/",
                        onClick = {
                            viewModel.navigateTo(activePane, "/")
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Storage Directory") },
                        selected = activeState.path == "/storage/emulated/0",
                        onClick = {
                            viewModel.navigateTo(activePane, "/storage/emulated/0")
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                        label = { Text("Termux Home") },
                        selected = activeState.path == "/data/data/com.termux/files/home",
                        onClick = {
                            viewModel.navigateTo(activePane, "/data/data/com.termux/files/home")
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Open Terminal button
                    Button(
                        onClick = {
                            showTerminalDialog = true
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        enabled = true
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Terminal")
                    }

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
                    title = { Text("Xt-manager") },
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
                            viewModel.refreshPane(PaneType.LEFT)
                            viewModel.refreshPane(PaneType.RIGHT)
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                    onCopy = { viewModel.copySelected(activePane) },
                    onMove = { viewModel.moveSelected(activePane) },
                    onRename = {
                        // Rename the first selected file
                        val selectedPath = activeState.selected.firstOrNull()
                        val file = activeState.files.firstOrNull { it.path == selectedPath }
                        if (file != null) {
                            showRenameDialog = file
                        }
                    },
                    onDelete = { showDeleteConfirmDialog = true },
                    onCompress = { showCompressDialog = true },
                    onExtract = {
                        val selectedPath = activeState.selected.firstOrNull()
                        val file = activeState.files.firstOrNull { it.path == selectedPath }
                        if (file != null && file.type == FileType.ARCHIVE) {
                            showExtractDialog = file
                        }
                    },
                    onMenuClick = { scope.launch { drawerState.open() } }
                )
            }
        ) { paddingValues ->
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
                        onFileClick = { file ->
                            viewModel.setActivePane(PaneType.LEFT)
                            if (file.isDirectory) {
                                viewModel.navigateTo(PaneType.LEFT, file.path)
                            } else {
                                viewModel.toggleFileSelection(PaneType.LEFT, file.path)
                            }
                        },
                        onFileLongClick = { file ->
                            viewModel.setActivePane(PaneType.LEFT)
                            viewModel.toggleFileSelection(PaneType.LEFT, file.path)
                        },
                        onPathClick = { path ->
                            viewModel.setActivePane(PaneType.LEFT)
                            viewModel.navigateTo(PaneType.LEFT, path)
                        },
                        onPaneClick = { viewModel.setActivePane(PaneType.LEFT) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 2.dp)
                    )
                    
                    FilePane(
                        paneState = rightPaneState,
                        isActive = activePane == PaneType.RIGHT,
                        onFileClick = { file ->
                            viewModel.setActivePane(PaneType.RIGHT)
                            if (file.isDirectory) {
                                viewModel.navigateTo(PaneType.RIGHT, file.path)
                            } else {
                                viewModel.toggleFileSelection(PaneType.RIGHT, file.path)
                            }
                        },
                        onFileLongClick = { file ->
                            viewModel.setActivePane(PaneType.RIGHT)
                            viewModel.toggleFileSelection(PaneType.RIGHT, file.path)
                        },
                        onPathClick = { path ->
                            viewModel.setActivePane(PaneType.RIGHT)
                            viewModel.navigateTo(PaneType.RIGHT, path)
                        },
                        onPaneClick = { viewModel.setActivePane(PaneType.RIGHT) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 2.dp)
                    )
                }
            }
        }
    }

    // --- DIALOGS IMPLEMENTATION ---

    // Create folder dialog
    if (showCreateDialog) {
        CreateDialog(
            title = "New Folder",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
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
            }
        )
    }

    // Compress Dialog
    if (showCompressDialog) {
        var archiveName by remember { mutableStateOf("archive.zip") }
        var selectedFormat by remember { mutableStateOf(ArchiveFormat.ZIP) }
        
        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text("Compress Files") },
            text = {
                Column {
                    OutlinedTextField(
                        value = archiveName,
                        onValueChange = { archiveName = it },
                        label = { Text("Archive Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Select Format:", style = MaterialTheme.typography.titleSmall)
                    
                    ArchiveFormat.values().forEach { format ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    selectedFormat = format
                                    val baseName = File(archiveName).nameWithoutExtension
                                    val ext = when (format) {
                                        ArchiveFormat.ZIP -> "zip"
                                        ArchiveFormat.TAR -> "tar"
                                        ArchiveFormat.TAR_GZ -> "tar.gz"
                                        ArchiveFormat.TAR_XZ -> "tar.xz"
                                        ArchiveFormat.SEVEN_Z -> "7z"
                                    }
                                    archiveName = "$baseName.$ext"
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedFormat == format,
                                onClick = { 
                                    selectedFormat = format
                                    val baseName = File(archiveName).nameWithoutExtension
                                    val ext = when (format) {
                                        ArchiveFormat.ZIP -> "zip"
                                        ArchiveFormat.TAR -> "tar"
                                        ArchiveFormat.TAR_GZ -> "tar.gz"
                                        ArchiveFormat.TAR_XZ -> "tar.xz"
                                        ArchiveFormat.SEVEN_Z -> "7z"
                                    }
                                    archiveName = "$baseName.$ext"
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = format.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (archiveName.isNotBlank()) {
                            viewModel.compressSelected(activePane, archiveName, selectedFormat)
                            showCompressDialog = false
                        }
                    }
                ) {
                    Text("Compress")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompressDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Extract Dialog
    showExtractDialog?.let { file ->
        var destDir by remember { mutableStateOf(inactiveState.path) }
        
        AlertDialog(
            onDismissRequest = { showExtractDialog = null },
            title = { Text("Extract Archive") },
            text = {
                Column {
                    Text("Archive: ${file.name}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = destDir,
                        onValueChange = { destDir = it },
                        label = { Text("Extract To") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (destDir.isNotBlank()) {
                            viewModel.extractSelected(activePane, file.path, destDir)
                            showExtractDialog = null
                        }
                    }
                ) {
                    Text("Extract")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtractDialog = null }) {
                    Text("Cancel")
                }
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
                                        Text(
                                            text = op.status.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when (op.status) {
                                                OperationStatus.RUNNING -> MaterialTheme.colorScheme.primary
                                                OperationStatus.COMPLETED -> Color.Green
                                                OperationStatus.FAILED -> MaterialTheme.colorScheme.error
                                                else -> Color.Gray
                                            }
                                        )
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

    // Terminal Dialog
    if (showTerminalDialog) {
        TerminalDialog(
            initialPath = activeState.path,
            onDismiss = { showTerminalDialog = false }
        )
    }
}
