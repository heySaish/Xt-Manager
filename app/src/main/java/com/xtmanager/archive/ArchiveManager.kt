package com.xtmanager.archive

interface ArchiveManager {
    suspend fun compress(
        sources: List<String>,
        output: String,
        format: ArchiveFormat,
        onProgress: (progress: Float, currentFile: String) -> Unit = { _, _ -> }
    )

    suspend fun extract(
        archive: String,
        destination: String,
        onProgress: (progress: Float, currentFile: String) -> Unit = { _, _ -> }
    )
}
