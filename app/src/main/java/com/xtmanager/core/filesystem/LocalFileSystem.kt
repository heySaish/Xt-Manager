package com.xtmanager.core.filesystem

import android.util.Log
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class LocalFileSystem : FileSystem {

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
    }

    override suspend fun list(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            return@withContext emptyList()
        }

        val cached = FileSystemCache.get(directory.absolutePath)
        if (cached != null) {
            Log.d(TAG, "Cache HIT for ${directory.absolutePath} (${cached.size} items)")
            return@withContext cached
        }

        val startTime = System.nanoTime()

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
                    Log.d(TAG, String.format(
                        "Rust Scan -> Path: %s | Items: %d | RustScan: %.2fms | RustSort: %.2fms | Total: %.2fms",
                        directory.name, result.size, rustScanMs, rustSortMs, totalMs
                    ))

                    FileSystemCache.put(directory.absolutePath, result)
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Native scan error: ${e.message}")
            }
        }

        var filesList: Array<File>? = directory.listFiles()
        if (filesList == null) {
            val shellFiles = mutableListOf<File>()
            try {
                val process = Runtime.getRuntime().exec(arrayOf("ls", "-1a", directory.absolutePath))
                val reader = process.inputStream.bufferedReader()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val name = line!!.trim()
                    if (name.isNotEmpty() && name != "." && name != "..") {
                        val child = File(directory, name)
                        if (child.exists()) {
                            shellFiles.add(child)
                        }
                    }
                }
                process.waitFor()
            } catch (_: Exception) {}

            if (shellFiles.isNotEmpty()) {
                filesList = shellFiles.toTypedArray()
            } else {
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
        }

        val finalFiles = filesList ?: emptyArray()
        return@withContext finalFiles.map { file ->
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
        }.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenComparator { a, b -> naturalCompare(a.name, b.name) })
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
                try {
                    val proc = Runtime.getRuntime().exec(arrayOf("mkdir", "-p", directory.absolutePath))
                    proc.waitFor()
                } catch (_: Exception) {
                    throw IOException("Failed to create directory: $path")
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
                try {
                    FileOutputStream(file).use { }
                } catch (_: Exception) {
                    val proc = Runtime.getRuntime().exec(arrayOf("touch", file.absolutePath))
                    proc.waitFor()
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
                throw IOException("Failed to rename $source to $destination")
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
        val archiveExtensions = listOf(".zip", ".tar", ".gz", ".xz", ".bz2", ".7z", ".rar", ".tgz")
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
                dest.mkdirs()
            }
            val children = src.listFiles() ?: return
            for (child in children) {
                copyRecursive(child, File(dest, child.name), totalBytes, onProgressUpdate)
            }
        } else {
            // Copy file content with buffer and progress callbacks
            val parent = dest.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            FileInputStream(src).use { input ->
                FileOutputStream(dest).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        onProgressUpdate(bytesRead.toLong(), src.name)
                    }
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
            throw IOException("Failed to delete: ${file.absolutePath}")
        }
        onDeletedItem(name)
    }
}
