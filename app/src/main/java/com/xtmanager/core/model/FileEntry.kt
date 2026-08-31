package com.xtmanager.core.model

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val type: FileType,
    val isSelected: Boolean = false
) {
    val formattedSize: String = if (isDirectory) "" else computeFormattedSize(size)
    val formattedDate: String = computeFormattedDate(lastModified)

    companion object {
        private fun computeFormattedSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            return String.format(
                java.util.Locale.US,
                "%.2f %s",
                size / Math.pow(1024.0, digitGroups.toDouble()),
                units[digitGroups]
            )
        }

        private fun computeFormattedDate(lastModified: Long): String {
            val sdf = java.text.SimpleDateFormat("yy-MM-dd HH:mm", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(lastModified))
        }
    }
}
