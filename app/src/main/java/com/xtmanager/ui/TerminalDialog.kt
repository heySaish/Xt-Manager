package com.xtmanager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalDialog(
    initialPath: String = "/storage/emulated/0",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sessionClient = remember {
        object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {}
            override fun onTitleChanged(changedSession: TerminalSession) {}
            override fun onSessionFinished(finishedSession: TerminalSession) {
                onDismiss()
            }
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("Terminal", text)
                clipboard?.setPrimaryClip(clip)
            }
            override fun onPasteTextFromClipboard(session: TerminalSession?) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = clipboard?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(context).toString()
                    if (text.isNotEmpty()) {
                        session?.write(text)
                    }
                }
            }
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
            override fun getTerminalCursorStyle(): Int? = null
            override fun logError(tag: String?, message: String?) {}
            override fun logWarn(tag: String?, message: String?) {}
            override fun logInfo(tag: String?, message: String?) {}
            override fun logDebug(tag: String?, message: String?) {}
            override fun logVerbose(tag: String?, message: String?) {}
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
        }
    }

    var terminalView: TerminalView? by remember { mutableStateOf(null) }
    var currentTextSize by remember { mutableStateOf(36) }

    val viewClient = remember {
        object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                if (scale < 0.9f || scale > 1.1f) {
                    val increase = scale > 1.0f
                    val delta = if (increase) 2 else -2
                    val newSize = (currentTextSize + delta).coerceIn(20, 72)
                    if (newSize != currentTextSize) {
                        currentTextSize = newSize
                        terminalView?.setTextSize(newSize)
                    }
                    return 1.0f
                }
                return scale
            }
            override fun onSingleTapUp(e: MotionEvent?) {
                terminalView?.let { view ->
                    view.requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
            override fun shouldEnforceCharBasedInput(): Boolean = false
            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
            override fun isTerminalViewSelected(): Boolean = true
            override fun copyModeChanged(copyMode: Boolean) {}
            override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false
            override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
            override fun onLongPress(event: MotionEvent?): Boolean = false
            override fun readControlKey(): Boolean = false
            override fun readAltKey(): Boolean = false
            override fun readShiftKey(): Boolean = false
            override fun readFnKey(): Boolean = false
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
            override fun onEmulatorSet() {}
            override fun logError(tag: String?, message: String?) {}
            override fun logWarn(tag: String?, message: String?) {}
            override fun logInfo(tag: String?, message: String?) {}
            override fun logDebug(tag: String?, message: String?) {}
            override fun logVerbose(tag: String?, message: String?) {}
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
        }
    }

    val terminalSession = remember {
        val shellPath = when {
            File("/data/data/com.termux/files/usr/bin/bash").canExecute() -> "/data/data/com.termux/files/usr/bin/bash"
            File("/data/data/com.termux/files/usr/bin/sh").canExecute() -> "/data/data/com.termux/files/usr/bin/sh"
            File("/system/bin/sh").canExecute() -> "/system/bin/sh"
            else -> "/system/bin/sh"
        }
        val cwd = if (File(initialPath).exists()) initialPath else "/storage/emulated/0"

        val argsList = arrayOf("-i")

        val envMap = System.getenv().toMutableMap()
        envMap["TERM"] = "xterm-256color"
        envMap["HOME"] = context.filesDir.absolutePath

        val termuxUsrDir = File("/data/data/com.termux/files/usr")
        if (termuxUsrDir.exists()) {
            envMap["PREFIX"] = termuxUsrDir.absolutePath
            val usrBinPath = "/data/data/com.termux/files/usr/bin"
            val currentPath = envMap["PATH"] ?: "/system/bin:/system/xbin"
            envMap["PATH"] = "$usrBinPath:$currentPath"
            val usrLibPath = "/data/data/com.termux/files/usr/lib"
            if (File(usrLibPath).exists()) {
                envMap["LD_LIBRARY_PATH"] = usrLibPath
            }
        } else {
            envMap.putIfAbsent("PATH", "/system/bin:/system/xbin:/vendor/bin")
        }

        val envList = envMap.map { "${it.key}=${it.value}" }.toTypedArray()

        TerminalSession(
            shellPath,
            cwd,
            argsList,
            envList,
            3000,
            sessionClient
        ).apply {
            updateSize(80, 24, 12, 24)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Terminal",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Terminal",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Terminal View Host
                AndroidView(
                    factory = { ctx ->
                        TerminalView(ctx, null).apply {
                            setTerminalViewClient(viewClient)
                            attachSession(terminalSession)
                            setTextSize(currentTextSize)
                            terminalView = this
                            isFocusable = true
                            isFocusableInTouchMode = true
                            requestFocus()
                            post {
                                val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                // Extra Keys Quick Toolbar (ESC, TAB, Arrow keys, Ctrl shortcuts, Paste)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TerminalKeyButton("ESC") { terminalSession.write("\u001b") }
                    TerminalKeyButton("TAB") { terminalSession.write("\t") }
                    TerminalKeyButton("CTRL+C") { terminalSession.write("\u0003") }
                    TerminalKeyButton("CTRL+Z") { terminalSession.write("\u001a") }
                    TerminalKeyButton("▲") { terminalSession.write("\u001b[A") }
                    TerminalKeyButton("▼") { terminalSession.write("\u001b[B") }
                    TerminalKeyButton("◄") { terminalSession.write("\u001b[D") }
                    TerminalKeyButton("►") { terminalSession.write("\u001b[C") }
                    TerminalKeyButton("/") { terminalSession.write("/") }
                    TerminalKeyButton("-") { terminalSession.write("-") }
                    TerminalKeyButton("|") { terminalSession.write("|") }
                    TerminalKeyButton("~") { terminalSession.write("~") }
                    TerminalKeyButton("PASTE") {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = clipboard?.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0).coerceToText(context).toString()
                            if (text.isNotEmpty()) {
                                terminalSession.write(text)
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            terminalSession.finishIfRunning()
        }
    }
}

@Composable
private fun TerminalKeyButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF2D2D2D),
        contentColor = Color.White,
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

