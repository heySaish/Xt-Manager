package com.xtmanager.core.model

enum class OperationType {
    COPY,
    MOVE,
    DELETE
}

enum class OperationStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

data class Operation(
    val id: String,
    val type: OperationType,
    val source: String,
    val destination: String,
    val progress: Float = 0f, // 0.0 to 1.0
    val status: OperationStatus = OperationStatus.PENDING,
    val currentFileName: String = "",
    val error: String? = null,
    val processedBytes: Long = 0,
    val totalBytes: Long = 0
) {
    val formattedProgress: String
        get() = String.format("%.0f%%", progress * 100)

    val formattedProcessedSize: String
        get() {
            return "${formatSize(processedBytes)} / ${formatSize(totalBytes)}"
        }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        if (digitGroups >= units.size) return "$bytes B"
        return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
