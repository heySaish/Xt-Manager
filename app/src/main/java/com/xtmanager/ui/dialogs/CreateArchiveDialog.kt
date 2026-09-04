package com.xtmanager.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtmanager.archive.ArchiveFormat
import java.io.File

@Composable
fun CreateArchiveDialog(
    initialItemName: String,
    onDismiss: () -> Unit,
    onConfirm: (archiveName: String, format: ArchiveFormat, level: Int, password: String?) -> Unit
) {
    val cleanBaseName = remember(initialItemName) {
        val f = File(initialItemName)
        if (f.extension.isNotEmpty()) f.nameWithoutExtension else f.name
    }

    var selectedFormat by remember { mutableStateOf(ArchiveFormat.ZIP) }
    var compressionLevel by remember { mutableIntStateOf(5) } // Normal level (5)
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    fun getExtension(format: ArchiveFormat): String = when (format) {
        ArchiveFormat.ZIP -> "zip"
        ArchiveFormat.TAR -> "tar"
        ArchiveFormat.TAR_GZ -> "tar.gz"
        ArchiveFormat.TAR_XZ -> "tar.xz"
        ArchiveFormat.SEVEN_Z -> "7z"
    }

    var archiveName by remember { mutableStateOf("$cleanBaseName.${getExtension(selectedFormat)}") }

    fun updateFormat(format: ArchiveFormat) {
        selectedFormat = format
        val ext = getExtension(format)
        archiveName = "$cleanBaseName.$ext"
    }

    var showFormatDropdown by remember { mutableStateOf(false) }
    var showLevelDropdown by remember { mutableStateOf(false) }

    val levelLabels = listOf(
        0 to "Store (0)",
        1 to "Fastest (1)",
        3 to "Fast (3)",
        5 to "Normal (5)",
        7 to "Maximum (7)",
        9 to "Ultra (9)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create archive",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Filename Input Field
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    label = { Text("Filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Format & Compression Level Dropdowns Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Format Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        Column {
                            Text(
                                text = "Format",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Surface(
                                onClick = { showFormatDropdown = true },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = getExtension(selectedFormat),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showFormatDropdown,
                            onDismissRequest = { showFormatDropdown = false }
                        ) {
                            ArchiveFormat.values().forEach { fmt ->
                                DropdownMenuItem(
                                    text = { Text(getExtension(fmt)) },
                                    onClick = {
                                        updateFormat(fmt)
                                        showFormatDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Compression Level Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        Column {
                            Text(
                                text = "Level",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Surface(
                                onClick = { showLevelDropdown = true },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val currentLabel = levelLabels.firstOrNull { it.first == compressionLevel }?.second ?: "Normal"
                                    Text(
                                        text = currentLabel,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showLevelDropdown,
                            onDismissRequest = { showLevelDropdown = false }
                        ) {
                            levelLabels.forEach { (lvl, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        compressionLevel = lvl
                                        showLevelDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Password Field (Optional)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (no encryption if empty)") },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (archiveName.isNotBlank()) {
                        onConfirm(
                            archiveName.trim(),
                            selectedFormat,
                            compressionLevel,
                            password.ifBlank { null }
                        )
                    }
                }
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}
