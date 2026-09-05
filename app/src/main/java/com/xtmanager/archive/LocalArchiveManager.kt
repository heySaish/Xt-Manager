package com.xtmanager.archive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * LocalArchiveManager - Java/Kotlin fallback engine.
 * Only handles .zip format. Throws explicit error for all non-ZIP formats when Alpine binaries are missing.
 */
class LocalArchiveManager : ArchiveManager {

    override suspend fun compress(
        sources: List<String>,
        output: String,
        format: ArchiveFormat,
        level: Int,
        password: String?,
        onProgress: (progress: Float, currentFile: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val outputFile = File(output)
        val filesToCompress = sources.map { File(it) }.filter { it.exists() }
        
        if (filesToCompress.isEmpty()) {
            throw IOException("No valid files to compress.")
        }

        if (format != ArchiveFormat.ZIP) {
            val reqBinary = when (format) {
                ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_XZ -> "tar"
                ArchiveFormat.SEVEN_Z -> "7z"
                ArchiveFormat.ZIP -> "zip"
            }
            throw IOException("Binary '$reqBinary' not found in Alpine environment. Format not supported by fallback engine.")
        }

        onProgress(0.1f, "Preparing ZIP archive via Java Fallback...")
        try {
            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                zos.setLevel(level.coerceIn(1, 9))
                val totalFiles = countFiles(filesToCompress)
                var processed = 0
                
                for (file in filesToCompress) {
                    addFileToZip(file, file.name, zos, totalFiles) { currentFile ->
                        processed++
                        onProgress(0.1f + (processed.toFloat() / totalFiles) * 0.9f, currentFile)
                    }
                }
            }
            onProgress(1.0f, "ZIP Compression completed successfully!")
        } catch (e: Exception) {
            throw IOException(e.localizedMessage ?: "Java ZIP compression failed")
        }
    }

    override suspend fun extract(
        archive: String,
        destination: String,
        password: String?,
        onProgress: (progress: Float, currentFile: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val archiveFile = File(archive)
        val destDir = File(destination)
        if (!destDir.exists()) destDir.mkdirs()

        val archiveLower = archive.lowercase()
        if (!archiveLower.endsWith(".zip")) {
            val reqBinary = when {
                archiveLower.endsWith(".tar") || archiveLower.endsWith(".tar.gz") || archiveLower.endsWith(".tgz") || archiveLower.endsWith(".tar.xz") -> "tar"
                archiveLower.endsWith(".7z") || archiveLower.endsWith(".rar") -> "7z"
                else -> "7z"
            }
            throw IOException("Binary '$reqBinary' not found in Alpine environment. Format not supported by fallback engine.")
        }

        try {
            onProgress(0.1f, "Extracting ZIP archive via Java Fallback...")
            val totalSize = archiveFile.length()
            var extractedBytes = 0L

            ZipInputStream(FileInputStream(archiveFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val newFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                                extractedBytes += len
                                if (totalSize > 0) {
                                    onProgress(0.1f + (extractedBytes.toFloat() / totalSize) * 0.9f, entry.name)
                                }
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            onProgress(1.0f, "ZIP Extraction completed successfully!")
        } catch (e: Exception) {
            throw IOException(e.localizedMessage ?: "Java ZIP extraction failed")
        }
    }

    private fun addFileToZip(file: File, path: String, zos: ZipOutputStream, total: Int, onProcessed: (String) -> Unit) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            if (children.isEmpty()) {
                zos.putNextEntry(ZipEntry(path + "/"))
                zos.closeEntry()
            } else {
                for (child in children) {
                    addFileToZip(child, path + "/" + child.name, zos, total, onProcessed)
                }
            }
        } else {
            FileInputStream(file).use { fis ->
                val zipEntry = ZipEntry(path)
                zos.putNextEntry(zipEntry)
                val buffer = ByteArray(8192)
                var len: Int
                while (fis.read(buffer).also { len = it } > 0) {
                    zos.write(buffer, 0, len)
                }
                zos.closeEntry()
            }
            onProcessed(file.name)
        }
    }

    private fun countFiles(files: List<File>): Int {
        var count = 0
        for (file in files) {
            count += countFilesRecursive(file)
        }
        return count
    }

    private fun countFilesRecursive(file: File): Int {
        if (file.isFile) return 1
        var count = 0
        val list = file.listFiles() ?: return 0
        for (f in list) {
            count += countFilesRecursive(f)
        }
        return count
    }
}
