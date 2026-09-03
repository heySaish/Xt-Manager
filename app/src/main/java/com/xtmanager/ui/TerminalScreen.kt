package com.xtmanager.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.xtmanager.runtime.AlpineManager
import com.xtmanager.runtime.NativeTerminalClient
import com.xtmanager.runtime.TerminalService
import kotlin.concurrent.thread

import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onClose()
    }
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("Initializing...") }
    var currentSession by remember { mutableStateOf<TerminalSession?>(null) }
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    var clientRef by remember { mutableStateOf<NativeTerminalClient?>(null) }

    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }

    val alpineManager = remember { AlpineManager(context) }

    fun showKeyboard() {
        terminalViewRef?.let { view ->
            view.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        }
    }

    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }

    // Start Foreground Service & Check Battery Optimization (Don't kill my app)
    LaunchedEffect(Unit) {
        val serviceIntent = Intent(context, TerminalService::class.java)
        context.startService(serviceIntent)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            showBatteryOptimizationDialog = true
        }
    }

    if (showBatteryOptimizationDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryOptimizationDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = "Battery Optimization",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Disable Battery Optimization",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "To prevent Android from killing background terminal processes, please disable battery optimization for Xt-Manager (dontkillmyapp.com)."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatteryOptimizationDialog = false
                        try {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (ex: Exception) {
                                Toast.makeText(context, "Unable to open battery settings", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Disable Optimization")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryOptimizationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Alpine Terminal",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Alpine Linux Shell",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (statusText.contains("Active")) Color(0xFF22C55E) else Color(0xFFEAB308)
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = statusText,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showKeyboard() }) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Show Keyboard"
                        )
                    }
                    IconButton(onClick = {
                        currentSession?.write("clear\r")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Screen"
                        )
                    }
                    IconButton(onClick = {
                        currentSession?.finishIfRunning()
                        val client = NativeTerminalClient(context)
                        clientRef = client
                        terminalViewRef?.post {
                            val session = alpineManager.createAlpineTerminalSession(client)
                            currentSession = session
                            terminalViewRef?.attachSession(session)
                            statusText = "Alpine Linux Active"
                            showKeyboard()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart Alpine"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .background(Color(0xFF0F172A))
        ) {
            // Native Termux TerminalView
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        val client = NativeTerminalClient(ctx, onSessionFinishedCallback = {
                            statusText = "Alpine Process Terminated"
                        })
                        clientRef = client

                        val view = TerminalView(ctx, null).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setTerminalViewClient(client)
                            setTextSize(36) // Default text size
                            isFocusable = true
                            isFocusableInTouchMode = true
                            requestFocus()
                        }
                        client.terminalView = view
                        terminalViewRef = view

                        thread {
                            val ready = alpineManager.setupAlpineEnvironment { status ->
                                statusText = status
                            }

                            if (ready) {
                                view.post {
                                    val session = alpineManager.createAlpineTerminalSession(client)
                                    currentSession = session
                                    view.attachSession(session)
                                    statusText = "Alpine Linux Active"
                                    showKeyboard()
                                }
                            } else {
                                statusText = "Alpine Setup Failed"
                            }
                        }

                        view
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Termux-Style Extra Keys Bar
            TermuxExtraKeysToolbar(
                isCtrlActive = isCtrlActive,
                isAltActive = isAltActive,
                onToggleCtrl = {
                    val newState = !isCtrlActive
                    isCtrlActive = newState
                    clientRef?.isCtrlActive = newState
                },
                onToggleAlt = {
                    val newState = !isAltActive
                    isAltActive = newState
                    clientRef?.isAltActive = newState
                },
                onToggleKeyboard = { showKeyboard() },
                onKeyClick = { key ->
                    currentSession?.write(key)
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            currentSession?.finishIfRunning()
        }
    }
}

@Composable
fun TermuxExtraKeysToolbar(
    isCtrlActive: Boolean,
    isAltActive: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onKeyClick: (String) -> Unit
) {
    val scrollState1 = rememberScrollState()
    val scrollState2 = rememberScrollState()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: ESC, ⌨, HOME, ↑, END, PGUP
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState1)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TermuxKeyButton(label = "ESC", isActive = false, onClick = { onKeyClick("\u001b") })
                TermuxKeyButton(label = "⌨", isActive = false, onClick = onToggleKeyboard)
                TermuxKeyButton(label = "HOME", isActive = false, onClick = { onKeyClick("\u001b[1~") })
                TermuxKeyButton(label = "↑", isActive = false, onClick = { onKeyClick("\u001b[A") })
                TermuxKeyButton(label = "END", isActive = false, onClick = { onKeyClick("\u001b[4~") })
                TermuxKeyButton(label = "PGUP", isActive = false, onClick = { onKeyClick("\u001b[5~") })
            }

            // Row 2: TAB, CTRL, ALT, ←, ↓, →, PGDN
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState2)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TermuxKeyButton(label = "TAB", isActive = false, onClick = { onKeyClick("\t") })
                TermuxKeyButton(label = "CTRL", isActive = isCtrlActive, onClick = onToggleCtrl)
                TermuxKeyButton(label = "ALT", isActive = isAltActive, onClick = onToggleAlt)
                TermuxKeyButton(label = "←", isActive = false, onClick = { onKeyClick("\u001b[D") })
                TermuxKeyButton(label = "↓", isActive = false, onClick = { onKeyClick("\u001b[B") })
                TermuxKeyButton(label = "→", isActive = false, onClick = { onKeyClick("\u001b[C") })
                TermuxKeyButton(label = "PGDN", isActive = false, onClick = { onKeyClick("\u001b[6~") })
            }
        }
    }
}

@Composable
fun TermuxKeyButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (isActive) 8.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier.height(34.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}
