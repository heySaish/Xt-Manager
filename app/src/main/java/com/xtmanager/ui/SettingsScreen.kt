package com.xtmanager.ui

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtmanager.core.filesystem.LocalFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onClose()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showHiddenFiles by remember { mutableStateOf(false) }
    var naturalSort by remember { mutableStateOf(true) }
    var logsText by remember { mutableStateOf("Fetching logs...") }
    var isSaving by remember { mutableStateOf(false) }
    var showLogsConsole by remember { mutableStateOf(false) }

    val isRustEngineActive = remember { LocalFileSystem.isNativeEngineActive }

    fun refreshLogs() {
        scope.launch {
            withContext(Dispatchers.IO) {
                logsText = fetchAppLogs()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshLogs()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Group 1: File Engine & Sorting
            SettingsGroupCard(title = "File Engine & Display") {
                // Item 1: Rust FS Status
                SettingsRowItem(
                    icon = Icons.Default.Memory,
                    iconBgColor = if (isRustEngineActive) Color(0xFF10B981) else Color(0xFFF59E0B),
                    title = if (isRustEngineActive) "Rust Native FS Engine" else "Kotlin Fallback FS",
                    subtitle = if (isRustEngineActive) "High-speed JNI engine active (libxt_fs.so)" else "Native library fallback mode",
                    trailingWidget = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isRustEngineActive) Color(0x2010B981) else Color(0x20F59E0B)
                        ) {
                            Text(
                                text = if (isRustEngineActive) "⚡ Active" else "🐢 Fallback",
                                color = if (isRustEngineActive) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Item 2: Natural Alphanumeric Sort
                SettingsRowItem(
                    icon = Icons.Default.SortByAlpha,
                    iconBgColor = Color(0xFF3B82F6),
                    title = "Natural Alphanumeric Sort",
                    subtitle = "Order files humanly (e.g. 1, 2, 10, 100 instead of 1, 10, 100)",
                    trailingWidget = {
                        Switch(
                            checked = naturalSort,
                            onCheckedChange = { naturalSort = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = Color.Transparent
                            )
                        )
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Item 3: Show Hidden Files
                SettingsRowItem(
                    icon = Icons.Default.Visibility,
                    iconBgColor = Color(0xFF8B5CF6),
                    title = "Show Hidden Files",
                    subtitle = "Display dotfiles and hidden system entries",
                    trailingWidget = {
                        Switch(
                            checked = showHiddenFiles,
                            onCheckedChange = { showHiddenFiles = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = Color.Transparent
                            )
                        )
                    }
                )
            }

            // Group 2: Terminal & Environment
            SettingsGroupCard(title = "Terminal & Shell") {
                SettingsRowItem(
                    icon = Icons.Default.Terminal,
                    iconBgColor = Color(0xFF06B6D4),
                    title = "Alpine Linux Environment",
                    subtitle = "proot sandbox with Bash shell primary fallback to /bin/sh",
                    trailingWidget = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            // Group 3: Diagnostics & Logs
            SettingsGroupCard(title = "Diagnostics & System Logs") {
                // Item 1: Save Debug Logs
                SettingsRowItem(
                    icon = Icons.Default.Save,
                    iconBgColor = Color(0xFFEC4899),
                    title = "Save Debug Logs",
                    subtitle = "Export logcat to /sdcard/Download/xt_manager_debug.log",
                    onClick = {
                        if (!isSaving) {
                            isSaving = true
                            scope.launch {
                                val success = saveLogsToDownloads(context, logsText, isRustEngineActive)
                                isSaving = false
                                val msg = if (success)
                                    "Logs saved to Download/xt_manager_debug.log"
                                else
                                    "Failed to save logs"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    trailingWidget = {
                        Text(
                            text = if (isSaving) "Saving..." else "Save",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Item 2: Toggle Live Log Viewer
                SettingsRowItem(
                    icon = Icons.Default.BugReport,
                    iconBgColor = Color(0xFF6366F1),
                    title = "Live Logcat Viewer",
                    subtitle = if (showLogsConsole) "Tap to hide live log console" else "Tap to expand live system logs",
                    onClick = {
                        showLogsConsole = !showLogsConsole
                        if (showLogsConsole) refreshLogs()
                    },
                    trailingWidget = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                if (showLogsConsole) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Console:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = { refreshLogs() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        val verticalScroll = rememberScrollState()
                        val horizontalScroll = rememberScrollState()

                        Text(
                            text = logsText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF22C55E),
                            modifier = Modifier
                                .verticalScroll(verticalScroll)
                                .horizontalScroll(horizontalScroll)
                        )
                    }
                }
            }

            // Group 4: About Xt-Manager
            SettingsGroupCard(title = "About Xt-Manager") {
                SettingsRowItem(
                    icon = Icons.Default.Info,
                    iconBgColor = Color(0xFF64748B),
                    title = "Xt-Manager",
                    subtitle = "Version 1.0.0 (arm64-v8a) • Native Rust Core",
                    trailingWidget = {
                        Text(
                            text = "v1.0.0",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    iconBgColor: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailingWidget: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBgColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconBgColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }

        if (trailingWidget != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingWidget()
        }
    }
}

private fun fetchAppLogs(): String {
    return try {
        val process = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-s", "XtFsMetrics:V", "com.xtmanager:V", "*:E")
        )
        val log = process.inputStream.bufferedReader().use { it.readText() }
        if (log.trim().isEmpty()) {
            "No active logcat entries found.\nApp initialized cleanly."
        } else {
            log.takeLast(4000)
        }
    } catch (e: Exception) {
        "Failed to read logcat: ${e.message}"
    }
}

private suspend fun saveLogsToDownloads(
    context: Context,
    logsContent: String,
    isRustActive: Boolean
): Boolean = withContext(Dispatchers.IO) {
    try {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: File("/sdcard/Download")

        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val logFile = File(downloadDir, "xt_manager_debug.log")
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val header = """
            ==================================================
            XT-MANAGER DEBUG LOG DUMP
            Timestamp: $timestamp
            Engine Status: ${if (isRustActive) "RUST NATIVE ENGINE (ACTIVE)" else "KOTLIN FALLBACK"}
            ==================================================
            
        """.trimIndent()

        logFile.writeText(header + logsContent)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
