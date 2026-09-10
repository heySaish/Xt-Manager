package com.xtmanager.ui.drawer

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class StorageVolumeInfo(
    val name: String,
    val path: String,
    val isRemovable: Boolean
)

fun getStorageVolumesList(context: Context): List<StorageVolumeInfo> {
    val list = mutableListOf<StorageVolumeInfo>()

    // Internal Storage
    val primaryPath = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
    val normalizedPrimary = primaryPath.trimEnd('/')

    list.add(StorageVolumeInfo("Internal Storage", normalizedPrimary, isRemovable = false))

    try {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val volumes = sm.storageVolumes
            for (vol in volumes) {
                if (vol.state == Environment.MEDIA_MOUNTED || vol.state == Environment.MEDIA_MOUNTED_READ_ONLY) {
                    val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        vol.directory?.absolutePath
                    } else {
                        try {
                            val getPathMethod = vol.javaClass.getMethod("getPath")
                            getPathMethod.invoke(vol) as? String
                        } catch (_: Exception) { null }
                    }

                    if (dir != null) {
                        val normDir = dir.trimEnd('/')
                        if (normDir != normalizedPrimary && normDir != "/storage/emulated/0" && normDir != "/storage/self/primary") {
                            val name = vol.getDescription(context) ?: if (vol.isRemovable) "External SD / USB" else "Storage"
                            if (list.none { it.path.trimEnd('/') == normDir }) {
                                list.add(StorageVolumeInfo(name, normDir, isRemovable = vol.isRemovable))
                            }
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
                    val normPath = file.absolutePath.trimEnd('/')
                    if (name != "emulated" && name != "self" && file.isDirectory && file.canRead()) {
                        if (list.none { it.path.trimEnd('/') == normPath }) {
                            val displayName = if (name.matches(Regex("[0-9A-FA-f]{4}-[0-9A-FA-f]{4}"))) "SD Card ($name)" else "Storage ($name)"
                            list.add(StorageVolumeInfo(displayName, normPath, isRemovable = true))
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {}

    return list.distinctBy { it.path.trimEnd('/') }
}

@Composable
fun AppNavigationDrawer(
    drawerState: DrawerState,
    activePath: String,
    operationsCount: Int,
    showTerminal: Boolean,
    onNavigateTo: (String) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenOperations: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "XT Manager",
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
                        selected = activePath == "/",
                        onClick = {
                            onNavigateTo("/")
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    
                    val storageVolumes by produceState<List<StorageVolumeInfo>>(initialValue = emptyList(), context) {
                        value = withContext(Dispatchers.IO) { getStorageVolumesList(context) }
                    }
                    for (vol in storageVolumes) {
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = if (vol.isRemovable) Icons.Default.SdCard else Icons.Default.Folder,
                                    contentDescription = null
                                )
                            },
                            label = { Text(vol.name) },
                            selected = activePath == vol.path,
                            onClick = {
                                onNavigateTo(vol.path)
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
                            onOpenTerminal()
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))

                    // Active background operations quick button
                    if (operationsCount > 0) {
                        Button(
                            onClick = { 
                                onOpenOperations()
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Active Operations ($operationsCount)")
                        }
                    }
                }
            }
        },
        content = content
    )
}
