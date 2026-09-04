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

class LocalArchiveManager : ArchiveManager {

    override suspend fun compress(
        sources: List<String>,
        output: String,
        format: ArchiveFormat,
        level: Int,
        password: String?,
        onProgress: (progress: Float, currentFile: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (format != ArchiveFormat.ZIP) {
            throw IOException("Currently only ZIP format is supported natively.")
        }
        
        onProgress(0.1f, "Preparing ZIP archive...")
        val outputFile = File(output)
        val filesToCompress = mutableListOf<File>()
        
        for (sourcePath in sources) {
            val file = File(sourcePath)
            if (file.exists()) {
                filesToCompress.add(file)
            }
        }
        
        if (filesToCompress.isEmpty()) {
            throw IOException("No valid files to compress.")
        }

        try {
            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                val totalFiles = countFiles(filesToCompress)
                var processed = 0
                
                for (file in filesToCompress) {
                    addFileToZip(file, file.name, zos, totalFiles) { currentFile ->
                        processed++
                        onProgress(0.1f + (processed.toFloat() / totalFiles) * 0.9f, currentFile)
                    }
                }
            }
        } catch (e: Exception) {
            throw IOException("Compression failed: ${e.localizedMessage}")
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

        if (!archive.lowercase().endsWith(".zip")) {
            // If it's not a ZIP, try using system command for TAR
            if (archive.lowercase().endsWith(".tar") || archive.lowercase().endsWith(".tar.gz") || archive.lowercase().endsWith(".tgz")) {
                extractTarUsingSystem(archiveFile, destDir, onProgress)
                return@withContext
            }
            throw IOException("Unsupported archive format.")
        }

        try {
            onProgress(0.1f, "Opening ZIP archive...")
            val totalSize = archiveFile.length()
            var extractedBytes = 0L

            ZipInputStream(FileInputStream(archiveFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val newFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        // Create parent dirs if they don't exist
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos ->
                            val buffer = ByteArray(4096)
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
            onProgress(1.0f, "Extraction completed successfully!")
        } catch (e: Exception) {
            throw IOException("Extraction failed: ${e.localizedMessage}")
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
                val buffer = ByteArray(4096)
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

    private fun extractTarUsingSystem(archive: File, destination: File, onProgress: (Float, String) -> Unit) {
        try {
            onProgress(0.3f, "Extracting TAR archive using system tool...")
            val pb = ProcessBuilder(
                "tar",
                "-xf",
                archive.absolutePath,
                "-C",
                destination.absolutePath
            ).start()
            val code = pb.waitFor()
            if (code != 0) {
                throw IOException("System tar command returned non-zero code $code")
            }
            onProgress(1.0f, "Extraction completed successfully!")
        } catch (e: Exception) {
            throw IOException("TAR extraction failed: ${e.localizedMessage}")
        }
    }
}
