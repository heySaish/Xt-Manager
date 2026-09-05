package com.xtmanager.core.logger

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val logBuffer = mutableListOf<String>()
    private val _logsFlow = MutableStateFlow<List<String>>(emptyList())
    val logsFlow: StateFlow<List<String>> = _logsFlow.asStateFlow()

    @Synchronized
    fun log(tag: String, message: String) {
        val time = dateFormat.format(Date())
        val entry = "[$time] [$tag] $message"
        android.util.Log.d(tag, message)
        
        logBuffer.add(entry)
        if (logBuffer.size > 2000) {
            logBuffer.removeAt(0)
        }
        _logsFlow.value = logBuffer.toList()
    }

    fun d(tag: String, message: String) = log(tag, message)
    fun i(tag: String, message: String) = log(tag, "ℹ️ $message")
    fun w(tag: String, message: String) = log(tag, "⚠️ $message")
    fun e(tag: String, message: String) = log(tag, "❌ $message")

    fun getAllLogs(): String {
        return if (logBuffer.isEmpty()) {
            "No active operation logs captured yet.\nPerform copy, move, or delete operations to view real-time engine telemetry."
        } else {
            logBuffer.joinToString("\n")
        }
    }

    fun clear() {
        logBuffer.clear()
        _logsFlow.value = emptyList()
    }
}
