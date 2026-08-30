package com.xtmanager.core.filesystem

import com.xtmanager.core.model.FileEntry

interface FileSystem {
    suspend fun list(path: String): List<FileEntry>
    
    suspend fun copy(
        source: String, 
        destination: String, 
        onProgress: (processed: Long, total: Long, currentFile: String) -> Unit = { _, _, _ -> }
    )
    
    suspend fun move(
        source: String, 
        destination: String, 
        onProgress: (processed: Long, total: Long, currentFile: String) -> Unit = { _, _, _ -> }
    )
    
    suspend fun delete(
        path: String, 
        onProgress: (processed: Long, total: Long, currentFile: String) -> Unit = { _, _, _ -> }
    )
    
    suspend fun mkdir(path: String)
    
    suspend fun rename(source: String, destination: String)
}
