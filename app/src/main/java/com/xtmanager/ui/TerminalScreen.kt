package com.xtmanager.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.xtmanager.runtime.AlpineManager
import com.xtmanager.runtime.TerminalBridge
import com.xtmanager.runtime.TerminalService

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("Initializing...") }
    var bridgeRef by remember { mutableStateOf<TerminalBridge?>(null) }

    val alpineManager = remember { AlpineManager(context) }

    // Start Foreground Service
    LaunchedEffect(Unit) {
        val serviceIntent = Intent(context, TerminalService::class.java)
        context.startService(serviceIntent)
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
                    IconButton(onClick = {
                        bridgeRef?.sendInput("clear\r")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Screen"
                        )
                    }
                    IconButton(onClick = {
                        bridgeRef?.destroy()
                        bridgeRef?.startAlpineSession { statusText = it }
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
                .background(Color(0xFF0F172A))
        ) {
            // WebView for Xterm.js
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            setBackgroundColor(0xFF0F172A.toInt())

                            val bridge = TerminalBridge(ctx, this, alpineManager)
                            bridgeRef = bridge
                            addJavascriptInterface(bridge, "AndroidTerminal")

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    bridge.startAlpineSession { status ->
                                        statusText = status
                                    }
                                }
                            }

                            loadUrl("file:///android_asset/terminal/index.html")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Keyboard Helper Keys Toolbar
            TerminalKeyboardToolbar(
                onKeyClick = { key ->
                    bridgeRef?.sendKey(key)
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            bridgeRef?.destroy()
        }
    }
}

@Composable
fun TerminalKeyboardToolbar(
    onKeyClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Surface(
        color = Color(0xFF1E293B),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TerminalKeyButton(label = "ESC", onClick = { onKeyClick("\u001b") })
            TerminalKeyButton(label = "TAB", onClick = { onKeyClick("\t") })
            TerminalKeyButton(label = "CTRL+C", onClick = { onKeyClick("\u0003") })
            TerminalKeyButton(label = "CTRL+Z", onClick = { onKeyClick("\u001a") })
            TerminalKeyButton(label = "CTRL+D", onClick = { onKeyClick("\u0004") })
            TerminalKeyButton(label = "▲", onClick = { onKeyClick("\u001b[A") })
            TerminalKeyButton(label = "▼", onClick = { onKeyClick("\u001b[B") })
            TerminalKeyButton(label = "◀", onClick = { onKeyClick("\u001b[D") })
            TerminalKeyButton(label = "▶", onClick = { onKeyClick("\u001b[C") })
            TerminalKeyButton(label = "|", onClick = { onKeyClick("|") })
            TerminalKeyButton(label = "/", onClick = { onKeyClick("/") })
            TerminalKeyButton(label = "~", onClick = { onKeyClick("~") })
            TerminalKeyButton(label = "-", onClick = { onKeyClick("-") })
        }
    }
}

@Composable
fun TerminalKeyButton(
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF334155),
            contentColor = Color(0xFFF8FAFC)
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp,
            vertical = 4.dp
        ),
        modifier = Modifier.height(32.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
