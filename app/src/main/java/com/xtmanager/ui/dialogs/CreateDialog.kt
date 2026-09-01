package com.xtmanager.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun CreateDialog(
    title: String = "Create New",
    currentPath: String = "",
    onDismiss: () -> Unit,
    onCreateFile: (name: String) -> Unit,
    onCreateFolder: (name: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val alreadyExists = remember(name, currentPath) {
        if (name.isBlank() || currentPath.isBlank()) false
        else File(currentPath, name.trim()).exists()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = alreadyExists,
                    supportingText = {
                        if (alreadyExists) {
                            Text(
                                text = "File or folder already exists",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        if (name.isNotBlank() && !alreadyExists) {
                            onCreateFile(name.trim())
                        }
                    },
                    enabled = name.isNotBlank() && !alreadyExists
                ) {
                    Text("File")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = {
                        if (name.isNotBlank() && !alreadyExists) {
                            onCreateFolder(name.trim())
                        }
                    },
                    enabled = name.isNotBlank() && !alreadyExists
                ) {
                    Text("Folder")
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}


