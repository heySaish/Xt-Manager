package com.xtmanager.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ExtractDialog(
    archiveName: String,
    initialDestDir: String,
    onDismiss: () -> Unit,
    onExtract: (destDir: String, overwritePolicy: Int) -> Unit
) {
    var destinationDir by remember { mutableStateOf(initialDestDir) }
    var overwritePolicy by remember { mutableIntStateOf(0) } // 0: OVERWRITE, 1: SKIP, 2: RENAME_NEW

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Extract $archiveName",
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
                    text = "Destination Directory",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = destinationDir,
                    onValueChange = { destinationDir = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Overwrite Policy",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        0 to "Overwrite",
                        1 to "Skip",
                        2 to "Rename"
                    ).forEach { (policyValue, label) ->
                        FilterChip(
                            selected = overwritePolicy == policyValue,
                            onClick = { overwritePolicy = policyValue },
                            label = { Text(label) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (destinationDir.isNotBlank()) {
                        onExtract(destinationDir, overwritePolicy)
                        onDismiss()
                    }
                }
            ) {
                Text("Extract", fontWeight = FontWeight.Bold)
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
