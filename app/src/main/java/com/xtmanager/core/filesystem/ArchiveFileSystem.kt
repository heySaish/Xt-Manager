package com.xtmanager.core.filesystem

import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ArchiveFileSystem(
    val archivePath: String
) : FileSystem {

    override suspend fun list(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val virtualPrefix = if (path.startsWith(archivePath)) {
            val rel = path.substring(archivePath.length).trimStart('/')
            rel
        } else {
            path.trimStart('/')
        }

        val rawItems = LocalFileSystem.nativeListArchiveEntries(archivePath, virtualPrefix)
            ?: return@withContext emptyList()

        rawItems.map { item ->
            val itemPath = if (virtualPrefix.isEmpty()) {
                "$archivePath/${item.name}"
            } else {
                "$archivePath/$virtualPrefix/${item.name}"
            }

            val type = when {
                item.isDir -> FileType.DIRECTORY
                item.isArchive -> FileType.ARCHIVE
                else -> FileType.FILE
            }

            FileEntry(
                name = item.name,
                path = itemPath,
                isDirectory = item.isDir,
                size = item.size,
                lastModified = item.lastMod,
                type = type
            )
        }
    }

    override suspend fun copy(
        source: String,
        destination: String,
        onProgress: (processed: Long, total: Long, currentFile: String) -> Unit
    ) {
        throw IOException("Archive filesystem is read-only. Extract archive to copy contents.")
    }

    override suspend fun move(
        source: String,
        destination: String,
        onProgress: (processed: Long, total: Long, currentFile: String) -> Unit
    ) {
        throw IOException("Archive filesystem is read-only.")
    }

    override suspend fun delete(
        path: String,
        onProgress: (processed: Long, total: Long, currentFile: String) -> Unit
    ) {
        throw IOException("Archive filesystem is read-only.")
    }

    override suspend fun mkdir(path: String) {
        throw IOException("Archive filesystem is read-only.")
    }

    override suspend fun createFile(path: String) {
        throw IOException("Archive filesystem is read-only.")
    }

    override suspend fun rename(source: String, destination: String) {
        throw IOException("Archive filesystem is read-only.")
    }
}
