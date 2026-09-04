package com.xtmanager.archive

sealed class ProgressEvent {
    data class Progressing(
        val percentage: Float,          // 0.0f to 1.0f
        val currentItem: String,       // Current file/item name being processed
        val bytesProcessed: Long = 0,   // Processed bytes
        val totalBytes: Long = 0,       // Total bytes
        val speedBytesPerSec: Long = 0  // Speed in bytes/sec
    ) : ProgressEvent()

    data class Completed(val message: String = "Operation completed successfully") : ProgressEvent()
    data class Failed(val error: String) : ProgressEvent()
}
