package com.xtmanager.archive

import com.xtmanager.core.logger.AppLogger
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

        val has7z = isBinaryAvailable("7z")
        if (has7z) {
            AppLogger.i("ARCHIVE", "⚡ Active Engine: 7z CLI Engine for target '$output'")
            run7zCompress(sources, output, format, level, password) { progress, file ->
                onProgress(progress, "⚡ [7z CLI] $file")
            }
            return@withContext
        }

        AppLogger.w("ARCHIVE", "🐢 Active Engine: Java Core Fallback (7z binary not found) for target '$output'")
        fallbackManager.compress(sources, output, format, level, password) { progress, file ->
            onProgress(progress, "🐢 [Java Core] $file")
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

        val has7z = isBinaryAvailable("7z")
        if (has7z) {
            AppLogger.i("ARCHIVE", "⚡ Active Engine: 7z CLI Engine extracting '$archive'")
            run7zExtract(archiveFile, destDir, password) { progress, file ->
                onProgress(progress, "⚡ [7z CLI] $file")
            }
            return@withContext
        }

        AppLogger.w("ARCHIVE", "🐢 Active Engine: Java Core Fallback extracting '$archive'")
        fallbackManager.extract(archive, destination, password) { progress, file ->
            onProgress(progress, "🐢 [Java Core] $file")
        }
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

        AppLogger.d("ARCHIVE", "⚡ Executing command: ${cmd.joinToString(" ")}")
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

        AppLogger.d("ARCHIVE", "⚡ Executing command: ${cmd.joinToString(" ")}")
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

        val reader = InputStreamReader(process.inputStream)
        val sb = StringBuilder()
        var charCode = reader.read()
        var lastReportTime = 0L

        while (charCode != -1) {
            val ch = charCode.toChar()
            if (ch == '\r' || ch == '\n') {
                if (sb.isNotEmpty()) {
                    val line = sb.toString()
                    val event = parser.parseLine(line)
                    if (event is ProgressEvent.Progressing) {
                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 50) { // throttle to max 20 updates/sec for smooth rendering
                            onProgress(event.percentage, event.currentItem)
                            AppLogger.d("ARCHIVE", "⚡ [7z CLI ${String.format("%.0f%%", event.percentage * 100)}] ${event.currentItem}")
                            lastReportTime = now
                        }
                    }
                    sb.clear()
                }
            } else {
                sb.append(ch)
            }
            charCode = reader.read()
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            AppLogger.e("ARCHIVE", "❌ CLI operation failed with exit code $exitCode")
            throw java.io.IOException("CLI operation failed with exit code $exitCode")
        }
        AppLogger.i("ARCHIVE", "✅ CLI operation completed successfully!")
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
