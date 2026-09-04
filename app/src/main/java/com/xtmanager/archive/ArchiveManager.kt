package com.xtmanager.archive

interface ArchiveManager {
    suspend fun compress(
        sources: List<String>,
        output: String,
        format: ArchiveFormat,
        level: Int = 5,
        password: String? = null,
        onProgress: (progress: Float, currentFile: String) -> Unit = { _, _ -> }
    )

    suspend fun extract(
        archive: String,
        destination: String,
        password: String? = null,
        onProgress: (progress: Float, currentFile: String) -> Unit = { _, _ -> }
    )
}
