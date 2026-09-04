package com.xtmanager.ui.dialogs

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtmanager.core.filesystem.LocalFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logsText by remember { mutableStateOf("Fetching logs...") }
    var isSaving by remember { mutableStateOf(false) }

    val isRustEngineActive = remember { LocalFileSystem.isNativeEngineActive }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            logsText = fetchAppLogs()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Settings & Debug Logs")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Engine Status Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRustEngineActive)
                            Color(0x1F4CAF50)
                        else
                            Color(0x1FFF9800)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isRustEngineActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isRustEngineActive) Color(0xFF2E7D32) else Color(0xFFE65100),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isRustEngineActive) "⚡ Rust FS Engine Active" else "🐢 Kotlin Fallback Active",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isRustEngineActive) Color(0xFF1B5E20) else Color(0xFFBF360C)
                            )
                            Text(
                                text = if (isRustEngineActive)
                                    "Architecture: arm64-v8a (libxt_fs.so)"
                                else
                                    "Native library not loaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "System Debug Logs:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Scrollable Monospace Logs Viewer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    val verticalScroll = rememberScrollState()
                    val horizontalScroll = rememberScrollState()

                    Text(
                        text = logsText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF00FF66),
                        modifier = Modifier
                            .verticalScroll(verticalScroll)
                            .horizontalScroll(horizontalScroll)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
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
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isSaving) "Saving..." else "Save Logs")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
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
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())

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
