package com.xtmanager.archive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class CliArchiveManager(
    private val fallbackManager: LocalArchiveManager = LocalArchiveManager()
) : ArchiveManager {

    override suspend fun compress(
        sources: List<String>,
        output: String,
        format: ArchiveFormat,
        level: Int,
        password: String?,
        onProgress: (progress: Float, currentFile: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext

        // Try 7z CLI execution first if available
        val has7z = isBinaryAvailable("7z")
        if (has7z) {
            run7zCompress(sources, output, format, level, password, onProgress)
            return@withContext
        }

        // Fallback to native Zip/Tar engine
        fallbackManager.compress(sources, output, format, level, password, onProgress)
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

        val has7z = isBinaryAvailable("7z")
        if (has7z) {
            run7zExtract(archiveFile, destDir, password, onProgress)
            return@withContext
        }

        // Fallback to native Zip/Tar engine
        fallbackManager.extract(archive, destination, password, onProgress)
    }

    private fun run7zCompress(
        sources: List<String>,
        output: String,
        format: ArchiveFormat,
        level: Int,
        password: String?,
        onProgress: (Float, String) -> Unit
    ) {
        val cmd = mutableListOf("7z", "a", "-bsp1", "-y")
        
        // Compression level -mx0 to -mx9
        cmd.add("-mx=$level")

        // Format
        when (format) {
            ArchiveFormat.ZIP -> cmd.add("-tzip")
            ArchiveFormat.TAR -> cmd.add("-ttar")
            ArchiveFormat.TAR_GZ -> cmd.add("-ttar")
            ArchiveFormat.TAR_XZ -> cmd.add("-ttar")
            ArchiveFormat.SEVEN_Z -> cmd.add("-t7z")
        }

        // Password protection
        if (!password.isNullOrEmpty()) {
            cmd.add("-p$password")
        }

        cmd.add(output)
        cmd.addAll(sources)

        executeProcessWithParser(cmd, SevenZipOutputParser(), onProgress)
    }

    private fun run7zExtract(
        archiveFile: File,
        destDir: File,
        password: String?,
        onProgress: (Float, String) -> Unit
    ) {
        val cmd = mutableListOf("7z", "x", archiveFile.absolutePath, "-o${destDir.absolutePath}", "-bsp1", "-y")
        if (!password.isNullOrEmpty()) {
            cmd.add("-p$password")
        }

        executeProcessWithParser(cmd, SevenZipOutputParser(), onProgress)
    }

    private fun executeProcessWithParser(
        cmd: List<String>,
        parser: ArchiveOutputParser,
        onProgress: (Float, String) -> Unit
    ) {
        onProgress(0.05f, "Starting CLI archive engine...")
        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        val process = pb.start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String? = reader.readLine()
        while (line != null) {
            val event = parser.parseLine(line)
            if (event is ProgressEvent.Progressing) {
                onProgress(event.percentage, event.currentItem)
            }
            line = reader.readLine()
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw java.io.IOException("CLI operation failed with exit code $exitCode")
        }
        onProgress(1.0f, "Operation completed successfully!")
    }

    private fun isBinaryAvailable(binaryName: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", binaryName))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
}
