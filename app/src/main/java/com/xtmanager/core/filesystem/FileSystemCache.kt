package com.xtmanager.core.filesystem

import com.xtmanager.core.model.FileEntry
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object FileSystemCache {
    private data class CacheEntry(
        val files: List<FileEntry>,
        val dirLastModified: Long,
        val cacheTimestamp: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun get(path: String): List<FileEntry>? {
        val entry = cache[path] ?: return null
        val dirFile = File(path)
        if (!dirFile.exists()) {
            cache.remove(path)
            return null
        }

        // Check if path modification time changed on disk
        val currentMod = dirFile.lastModified()
        if (currentMod != entry.dirLastModified) {
            cache.remove(path)
            return null
        }

        return entry.files
    }

    fun put(path: String, files: List<FileEntry>) {
        val dirFile = File(path)
        val modTime = if (dirFile.exists()) dirFile.lastModified() else 0L
        cache[path] = CacheEntry(
            files = files,
            dirLastModified = modTime,
            cacheTimestamp = System.currentTimeMillis()
        )
    }

    fun invalidate(path: String) {
        cache.remove(path)
    }

    fun clear() {
        cache.clear()
    }
}
