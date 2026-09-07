package com.xtmanager.core.filesystem

import android.util.Log
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.FileType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class LocalFileSystem(
    private val rootShellManager: com.xtmanager.core.root.RootShellManager = com.xtmanager.core.root.RootShellManager(),
    private val isRootEnabledProvider: () -> Boolean = { false }
) : FileSystem {

    private val inFlightScans = ConcurrentHashMap<String, CompletableDeferred<List<FileEntry>>>()

    class RawFileItem(
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val lastMod: Long,
        val isArchive: Boolean
    )

    class FsScanMetrics(
        val scanUs: Long,
        val sortUs: Long,
        val totalUs: Long,
        val count: Int
    )

    companion object {
        @JvmStatic
        var appContext: android.content.Context? = null

        private const val TAG = "XtFsMetrics"
        private var isNativeLoaded = false
        val isNativeEngineActive: Boolean
            get() = isNativeLoaded
        init {
            try {
                System.loadLibrary("xt_fs")
                isNativeLoaded = true
            } catch (_: Throwable) {
                try {
                    System.loadLibrary("native_fs")
                    isNativeLoaded = true
                } catch (_: Throwable) {
                    isNativeLoaded = false
                }
            }
        }

        @JvmStatic
        private external fun nativeListFiles(path: String): Array<RawFileItem>?

        @JvmStatic
        private external fun nativeGetLastMetrics(): FsScanMetrics?

        @JvmStatic
        external fun nativeCreateCancelToken(): Long

        @JvmStatic
        external fun nativeTriggerCancel(tokenId: Long)

        @JvmStatic
        external fun nativeFreeCancelToken(tokenId: Long)

        @JvmStatic
        external fun nativeListArchiveEntries(archivePath: String, virtualPrefix: String): Array<RawFileItem>?
    }

    override suspend fun list(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        // Check if path is an archive or inside an archive
        val archiveExts = listOf(
            ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".tbz", ".tar.xz", ".txz", ".tar.zst", ".tzst", ".tar.lz4", ".tlz4",
            ".zip", ".apk", ".7z", ".tar", ".gz", ".bz2", ".xz", ".zst", ".lz4",
            ".rar", ".cab", ".iso", ".cpio"
        )
        var archivePath: String? = null
        var virtualPrefix = ""

        for (ext in archiveExts) {
            val idx = path.lowercase().indexOf(ext)
            if (idx != -1) {
                val endIdx = idx + ext.length
                if (endIdx == path.length || path[endIdx] == '/') {
                    archivePath = path.substring(0, endIdx)
                    virtualPrefix = path.substring(endIdx).trimStart('/')
                    break
                }
            }
        }

        if (archivePath != null && isNativeLoaded) {
            val rawItems = nativeListArchiveEntries(archivePath, virtualPrefix)
            if (rawItems != null) {
                val basePath = if (path.endsWith("/")) path else "$path/"
                return@withContext rawItems.map { item ->
                    val type = when {
                        item.isDir -> FileType.DIRECTORY
                        item.isArchive -> FileType.ARCHIVE
                        else -> FileType.FILE
                    }
                    FileEntry(
                        name = item.name,
                        path = "$basePath${item.name}",
                        isDirectory = item.isDir,
                        size = item.size,
                        lastModified = item.lastMod,
                        type = type
                    )
                }
            }
        }

        val directory = File(path)

        val canonicalPath = try { directory.canonicalPath } catch (_: Exception) { directory.absolutePath }

        val cached = FileSystemCache.get(canonicalPath)
        if (cached != null) {
            Log.d(TAG, "Cache HIT for $canonicalPath (${cached.size} items)")
            return@withContext cached
        }

        // SingleFlight Request Collapsing: If a scan for canonicalPath is already in-flight, await its result
        val myDeferred = CompletableDeferred<List<FileEntry>>()
        val existingDeferred = inFlightScans.putIfAbsent(canonicalPath, myDeferred)

        if (existingDeferred != null) {
            Log.d(TAG, "⚡ In-Flight Scan Join -> Path: ${directory.name}")
            return@withContext existingDeferred.await()
        }

        try {
            val result = performScan(directory)
            FileSystemCache.put(canonicalPath, result)
            myDeferred.complete(result)
            return@withContext result
        } catch (e: Throwable) {
            myDeferred.completeExceptionally(e)
            throw e
        } finally {
            inFlightScans.remove(canonicalPath, myDeferred)
        }
    }

    private suspend fun performScan(directory: File): List<FileEntry> {
        val startTime = System.nanoTime()
        val path = directory.absolutePath

        if (isRootEnabledProvider()) {
            val rootEntries = rootShellManager.listDirectory(path)
            if (rootEntries.isNotEmpty()) {
                return rootEntries
            }
        }

        if (isNativeLoaded) {
            try {
                val rawItems = nativeListFiles(directory.absolutePath)
                if (rawItems != null && rawItems.isNotEmpty()) {
                    val metrics = nativeGetLastMetrics()
                    val basePath = if (directory.absolutePath.endsWith("/")) directory.absolutePath else "${directory.absolutePath}/"
                    val result = rawItems.map { item ->
                        val type = when {
                            item.isDir -> FileType.DIRECTORY
                            item.isArchive -> FileType.ARCHIVE
                            else -> FileType.FILE
                        }
                        FileEntry(
                            name = item.name,
                            path = "$basePath${item.name}",
                            isDirectory = item.isDir,
                            size = item.size,
                            lastModified = item.lastMod,
                            type = type
                        )
                    }

                    val totalMs = (System.nanoTime() - startTime) / 1_000_000.0
                    val rustScanMs = (metrics?.scanUs ?: 0L) / 1000.0
                    val rustSortMs = (metrics?.sortUs ?: 0L) / 1000.0
                    val logMsg = String.format(
                        "⚡ Rust Engine Scan -> Path: %s | Items: %d | Scan: %.2fms | Sort: %.2fms | Total: %.2fms",
                        directory.name, result.size, rustScanMs, rustSortMs, totalMs
                    )
                    Log.d(TAG, logMsg)
                    com.xtmanager.core.logger.AppLogger.d("RUST_FS", logMsg)
                    return result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Native scan error: ${e.message}")
            }
        }

        var filesList: Array<File>? = directory.listFiles()
        if (filesList == null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val stream = java.nio.file.Files.newDirectoryStream(directory.toPath())
                val nioFiles = stream.map { it.toFile() }.toTypedArray()
                stream.close()
                if (nioFiles.isNotEmpty()) {
                    filesList = nioFiles
                }
            } catch (_: Throwable) {}
        }

        if (filesList == null) {
            val knownNames = when (directory.absolutePath) {
                "/" -> listOf(
                    "apex", "bin", "bugreports", "config", "data", "dev", "etc",
                    "init", "linkerconfig", "mnt", "odm", "oem", "proc", "product",
                    "res", "sdcard", "storage", "sys", "system", "vendor"
                )
                "/storage" -> listOf("emulated", "self", "sdcard0", "0")
                "/storage/emulated" -> listOf("0")
                else -> emptyList()
            }
            val fallbackFiles = knownNames.map { File(directory, it) }.filter { it.exists() }
            if (fallbackFiles.isNotEmpty()) {
                filesList = fallbackFiles.toTypedArray()
            }
        }

        val finalFiles = filesList ?: emptyArray()
        return finalFiles.map { file ->
            val isDir = file.isDirectory || (file.exists() && !file.isFile)
            val type = when {
                isDir -> FileType.DIRECTORY
                isArchiveFile(file.name) -> FileType.ARCHIVE
                else -> FileType.FILE
            }
            FileEntry(
                name = file.name,
                path = file.absolutePath,
                isDirectory = isDir,
                size = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                type = type
            )
        }.distinctBy { it.path }.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenComparator { a, b -> naturalCompare(a.name, b.name) })
    }

    override suspend fun copy(
        source: String,
        destination: String,
        onProgress: (processed: Long, total: Long, currentFile: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val srcFile = File(source)
        val destFile = File(destination)

        val totalBytes = calculateTotalBytes(srcFile)
        var processedBytes = 0L

        copyRecursive(srcFile, destFile, totalBytes) { bytesCopied, currentFile ->
            processedBytes += bytesCopied
            onProgress(processedBytes, totalBytes, currentFile)
        }
    }

    override suspend fun move(
        source: String,
        destination: String,
        onProgress: (processed: Long, total: Long, currentFile: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val srcFile = File(source)
        val destFile = File(destination)

        // Try direct rename first (works on same mount point)
        if (srcFile.renameTo(destFile)) {
            val totalBytes = calculateTotalBytes(destFile)
            onProgress(totalBytes, totalBytes, destFile.name)
            return@withContext
        }

        // If rename fails, copy and delete
        copy(source, destination, onProgress)
        delete(source)
    }

    override suspend fun delete(
        path: String,
        onProgress: (processed: Long, total: Long, currentFile: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val file = File(path)
        val totalFiles = countFiles(file)
        var deletedFiles = 0L

        deleteRecursive(file) { currentFile ->
            deletedFiles++
            onProgress(deletedFiles, totalFiles, currentFile)
        }
    }

    override suspend fun mkdir(path: String) {
        withContext(Dispatchers.IO) {
            val directory = File(path)
            if (directory.exists()) {
                throw IOException("File or folder already exists: $path")
            }
            if (!directory.mkdirs() && !directory.mkdir()) {
                val ctx = appContext
                val safOk = ctx != null && SafStorageManager.mkdir(ctx, path)
                if (!safOk) {
                    try {
                        val proc = Runtime.getRuntime().exec(arrayOf("mkdir", "-p", directory.absolutePath))
                        proc.waitFor()
                    } catch (_: Exception) {
                        throw IOException("Failed to create directory: $path")
                    }
                }
            }
        }
    }

    override suspend fun createFile(path: String) {
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (file.exists()) {
                throw IOException("File or folder already exists: $path")
            }
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            if (!file.createNewFile()) {
                var created = false
                try {
                    FileOutputStream(file).use { }
                    created = true
                } catch (_: Exception) {
                    val ctx = appContext
                    if (ctx != null && SafStorageManager.createFile(ctx, path)) {
                        created = true
                    }
                }
                if (!created) {
                    try {
                        val proc = Runtime.getRuntime().exec(arrayOf("touch", file.absolutePath))
                        proc.waitFor()
                    } catch (_: Exception) {
                        throw IOException("Failed to create file: $path")
                    }
                }
            }
        }
    }

    override suspend fun rename(source: String, destination: String) {
        withContext(Dispatchers.IO) {
            val srcFile = File(source)
            val destFile = File(destination)
            if (destFile.exists()) {
                throw IOException("Destination file already exists: $destination")
            }
            if (!srcFile.renameTo(destFile)) {
                val ctx = appContext
                val safOk = ctx != null && SafStorageManager.rename(ctx, source, destFile.name)
                if (!safOk) {
                    throw IOException("Failed to rename $source to $destination")
                }
            }
        }
    }

    private fun naturalCompare(s1: String, s2: String): Int {
        var i = 0
        var j = 0
        val n1 = s1.length
        val n2 = s2.length

        while (i < n1 && j < n2) {
            val c1 = s1[i]
            val c2 = s2[j]

            if (c1.isDigit() && c2.isDigit()) {
                var num1 = 0L
                while (i < n1 && s1[i].isDigit()) {
                    num1 = num1 * 10 + (s1[i] - '0')
                    i++
                }
                var num2 = 0L
                while (j < n2 && s2[j].isDigit()) {
                    num2 = num2 * 10 + (s2[j] - '0')
                    j++
                }
                if (num1 != num2) {
                    return num1.compareTo(num2)
                }
            } else {
                val lc1 = c1.lowercaseChar()
                val lc2 = c2.lowercaseChar()
                if (lc1 != lc2) {
                    return lc1.compareTo(lc2)
                }
                i++
                j++
            }
        }
        return n1.compareTo(n2)
    }

    private fun isArchiveFile(fileName: String): Boolean {
        val archiveExtensions = listOf(
            ".zip", ".apk", ".7z", ".tar", ".gz", ".bz2", ".xz", ".zst", ".lz4",
            ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".tar.xz", ".txz", ".tar.zst", ".tzst", ".tar.lz4",
            ".rar", ".cab", ".iso", ".cpio"
        )
        return archiveExtensions.any { fileName.lowercase().endsWith(it) }
    }

    private fun calculateTotalBytes(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var total = 0L
        val children = file.listFiles() ?: return 0L
        for (child in children) {
            total += calculateTotalBytes(child)
        }
        return total
    }

    private fun countFiles(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return 1L
        var count = 1L // counting the directory itself
        val children = file.listFiles() ?: return count
        for (child in children) {
            count += countFiles(child)
        }
        return count
    }

    private fun copyRecursive(
        src: File,
        dest: File,
        totalBytes: Long,
        onProgressUpdate: (bytesCopied: Long, currentFile: String) -> Unit
    ) {
        if (src.isDirectory) {
            if (!dest.exists()) {
                if (!dest.mkdirs()) {
                    appContext?.let { SafStorageManager.mkdir(it, dest.absolutePath) }
                }
            }
            val children = src.listFiles() ?: return
            for (child in children) {
                copyRecursive(child, File(dest, child.name), totalBytes, onProgressUpdate)
            }
        } else {
            val parent = dest.parentFile
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    appContext?.let { SafStorageManager.mkdir(it, parent.absolutePath) }
                }
            }

            val input = try {
                FileInputStream(src)
            } catch (e: Exception) {
                appContext?.let { SafStorageManager.openInputStream(it, src.absolutePath) }
                    ?: throw e
            }

            val output = try {
                FileOutputStream(dest)
            } catch (e: Exception) {
                appContext?.let { SafStorageManager.openOutputStream(it, dest.absolutePath) }
                    ?: throw e
            }

            input.use { inStream ->
                output.use { outStream ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var lastUpdate = 0L
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        outStream.write(buffer, 0, bytesRead)
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 50) {
                            onProgressUpdate(bytesRead.toLong(), src.name)
                            lastUpdate = now
                        }
                    }
                    onProgressUpdate(0L, src.name)
                }
            }
        }
    }

    private fun deleteRecursive(file: File, onDeletedItem: (itemName: String) -> Unit) {
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteRecursive(child, onDeletedItem)
                }
            }
        }
        val name = file.name
        if (file.exists() && !file.delete()) {
            val ctx = appContext
            val safOk = ctx != null && SafStorageManager.delete(ctx, file.absolutePath)
            if (!safOk) {
                throw IOException("Failed to delete: ${file.absolutePath}")
            }
        }
        onDeletedItem(name)
    }
}
