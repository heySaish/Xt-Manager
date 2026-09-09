package com.xtmanager.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatternSelectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pattern by remember { mutableStateOf("*.zip") }
    val presetPatterns = listOf("*.zip", "*.apk", "*.png", "*.jpg", "IMG_*", "lib*.so", "*.tar.gz", "*")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Pattern Selection",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter a wildcard pattern to select matching files (e.g. *.zip, IMG_*, lib*.so):",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Wildcard Pattern") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Quick Presets:",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetPatterns.forEach { preset ->
                        FilterChip(
                            selected = pattern == preset,
                            onClick = { pattern = preset },
                            label = { Text(preset) },
                            modifier = Modifier.padding(end = 6.dp, bottom = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pattern.isNotBlank()) {
                        onConfirm(pattern.trim())
                    }
                    onDismiss()
                }
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
