package com.xtmanager.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CompressDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onCompress: (archiveName: String, format: String, level: Int) -> Unit
) {
    var archiveName by remember { mutableStateOf(initialName) }
    var selectedFormat by remember { mutableStateOf("zip") }
    var compressionLevel by remember { mutableFloatStateOf(5f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Compress Files",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Archive Name",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Archive Format",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val formats = listOf(
                        "zip" to "ZIP",
                        "7z" to "7Z",
                        "tar.gz" to "TAR.GZ",
                        "tar.bz2" to "TAR.BZ2",
                        "tar.xz" to "TAR.XZ",
                        "tar.zst" to "TAR.ZST",
                        "tar.lz4" to "TAR.LZ4"
                    )
                    items(formats.size) { idx ->
                        val (formatKey, label) = formats[idx]
                        FilterChip(
                            selected = selectedFormat == formatKey,
                            onClick = {
                                selectedFormat = formatKey
                                val cleanBase = archiveName.replace(Regex("\\.(zip|7z|tar\\.gz|tgz|tar\\.bz2|tbz2|tar\\.xz|txz|tar\\.zst|tzst|tar\\.lz4|tlz4)$", RegexOption.IGNORE_CASE), "")
                                archiveName = "$cleanBase.$formatKey"
                            },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Compression Level: ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = when (compressionLevel.toInt()) {
                            0, 1 -> "Fast (1)"
                            in 2..6 -> "Normal (${compressionLevel.toInt()})"
                            else -> "Best (${compressionLevel.toInt()})"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = compressionLevel,
                    onValueChange = { compressionLevel = it },
                    valueRange = 1f..9f,
                    steps = 7
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (archiveName.isNotBlank()) {
                        onCompress(archiveName, selectedFormat, compressionLevel.toInt())
                        onDismiss()
                    }
                }
            ) {
                Text("Compress", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}
