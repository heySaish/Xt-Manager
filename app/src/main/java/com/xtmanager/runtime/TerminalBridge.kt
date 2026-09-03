package com.xtmanager.runtime

import android.content.Context
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

class TerminalBridge(
    private val context: Context,
    private val webView: WebView,
    private val alpineManager: AlpineManager
) {
    companion object {
        private const val TAG = "TerminalBridge"
    }

    private var process: Process? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isRunning = false
    private var readThread: Thread? = null

    fun startAlpineSession(onStatusUpdate: (String) -> Unit = {}) {
        if (isRunning) return

        thread {
            try {
                onStatusUpdate("Initializing Alpine Linux...")
                val ready = alpineManager.setupAlpineEnvironment { status ->
                    onStatusUpdate(status)
                }

                if (!ready) {
                    onStatusUpdate("Alpine setup failed.")
                    return@thread
                }

                onStatusUpdate("Starting Alpine process...")
                process = alpineManager.startAlpineProcess()
                outputStream = process?.outputStream
                inputStream = process?.inputStream
                isRunning = true

                onStatusUpdate("Alpine Linux Active")

                // Start reading process output
                readThread = thread(start = true, name = "AlpineOutputReader") {
                    val buffer = ByteArray(8192)
                    try {
                        while (isRunning) {
                            val length = inputStream?.read(buffer) ?: -1
                            if (length <= 0) break
                            val bytes = buffer.copyOf(length)
                            val b64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            webView.post {
                                webView.evaluateJavascript(
                                    "if (window.writeTerminalDataBase64) window.writeTerminalDataBase64('$b64Data');",
                                    null
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading process output", e)
                    } finally {
                        onStatusUpdate("Alpine Process Terminated")
                        isRunning = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Alpine session", e)
                onStatusUpdate("Error: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun sendInput(data: String) {
        if (!isRunning || outputStream == null) return
        try {
            outputStream?.write(data.toByteArray(Charsets.UTF_8))
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write input to process", e)
        }
    }

    @JavascriptInterface
    fun onResize(cols: Int, rows: Int) {
        Log.d(TAG, "Terminal resized: cols=$cols, rows=$rows")
    }

    @JavascriptInterface
    fun onReady() {
        Log.d(TAG, "WebView Terminal JS is ready")
        if (!isRunning) {
            startAlpineSession()
        }
    }

    fun sendKey(key: String) {
        sendInput(key)
    }

    fun destroy() {
        isRunning = false
        try {
            process?.destroyForcibly()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying process", e)
        }
        process = null
        outputStream = null
        inputStream = null
    }
}
