package com.xtmanager.core.filesystem

import android.content.Context
import android.util.Log
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

object SnapshotCacheManager {
    private const val TAG = "SnapshotCacheManager"
    private const val MAGIC_HEADER = 0x5854534E // "XTSN"
    private const val SCHEMA_VERSION = 1
    private const val MAX_CACHE_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB Cap
    private const val BUFFER_SIZE = 64 * 1024 // 64 KB Buffer

    private val pathLocks = ConcurrentHashMap<String, Mutex>()

    data class SnapshotResult(
        val files: List<FileEntry>,
        val dirLastModified: Long
    )

    private fun getSnapshotsDir(context: Context): File {
        val dir = File(context.cacheDir, "snapshots")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getSnapshotFile(context: Context, path: String): File {
        val hash = path.hashCode().toUInt().toString(16)
        return File(getSnapshotsDir(context), "snap_$hash.snap")
    }

    suspend fun readSnapshot(context: Context?, path: String): SnapshotResult? = withContext(Dispatchers.IO) {
        if (context == null) return@withContext null
        val file = getSnapshotFile(context, path)
        if (!file.exists() || file.length() == 0L) return@withContext null

        val mutex = pathLocks.computeIfAbsent(path) { Mutex() }
        mutex.withLock {
            try {
                DataInputStream(BufferedInputStream(FileInputStream(file), BUFFER_SIZE)).use { dis ->
                    val magic = dis.readInt()
                    if (magic != MAGIC_HEADER) {
                        Log.w(TAG, "Magic header mismatch for $path")
                        file.delete()
                        return@withContext null
                    }

                    val version = dis.readInt()
                    if (version != SCHEMA_VERSION) {
                        Log.w(TAG, "Schema version mismatch ($version vs $SCHEMA_VERSION) for $path")
                        file.delete()
                        return@withContext null
                    }

                    val dirLastModified = dis.readLong()
                    val count = dis.readInt()
                    if (count < 0 || count > 100_000) {
                        file.delete()
                        return@withContext null
                    }

                    val list = ArrayList<FileEntry>(count)
                    for (i in 0 until count) {
                        val name = dis.readUTF()
                        val filePath = dis.readUTF()
                        val isDir = dis.readBoolean()
                        val size = dis.readLong()
                        val lastMod = dis.readLong()
                        val typeOrdinal = dis.readInt()
                        val type = FileType.entries.getOrNull(typeOrdinal) ?: FileType.FILE

                        list.add(
                            FileEntry(
                                name = name,
                                path = filePath,
                                isDirectory = isDir,
                                size = size,
                                lastModified = lastMod,
                                type = type
                            )
                        )
                    }
                    return@withContext SnapshotResult(list, dirLastModified)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read snapshot for $path: ${e.message}")
                file.delete()
                return@withContext null
            }
        }
    }

    suspend fun writeSnapshot(
        context: Context?,
        path: String,
        files: List<FileEntry>,
        dirLastModified: Long
    ) = withContext(Dispatchers.IO) {
        if (context == null) return@withContext
        val snapshotsDir = getSnapshotsDir(context)
        val file = getSnapshotFile(context, path)

        val mutex = pathLocks.computeIfAbsent(path) { Mutex() }
        mutex.withLock {
            try {
                val tempFile = File(snapshotsDir, "${file.name}.tmp")
                DataOutputStream(BufferedOutputStream(FileOutputStream(tempFile), BUFFER_SIZE)).use { dos ->
                    dos.writeInt(MAGIC_HEADER)
                    dos.writeInt(SCHEMA_VERSION)
                    dos.writeLong(dirLastModified)
                    dos.writeInt(files.size)

                    for (item in files) {
                        dos.writeUTF(item.name)
                        dos.writeUTF(item.path)
                        dos.writeBoolean(item.isDirectory)
                        dos.writeLong(item.size)
                        dos.writeLong(item.lastModified)
                        dos.writeInt(item.type.ordinal)
                    }
                    dos.flush()
                }

                if (tempFile.exists()) {
                    if (file.exists()) file.delete()
                    tempFile.renameTo(file)
                }

                pruneCacheIfNeeded(snapshotsDir)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write snapshot for $path: ${e.message}")
            }
        }
    }

    private fun pruneCacheIfNeeded(snapshotsDir: File) {
        try {
            val files = snapshotsDir.listFiles() ?: return
            var totalSize = files.sumOf { it.length() }

            if (totalSize > MAX_CACHE_SIZE_BYTES) {
                val sorted = files.sortedBy { it.lastModified() }
                for (f in sorted) {
                    if (totalSize <= MAX_CACHE_SIZE_BYTES) break
                    val len = f.length()
                    if (f.delete()) {
                        totalSize -= len
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cache pruning error: ${e.message}")
        }
    }
}
