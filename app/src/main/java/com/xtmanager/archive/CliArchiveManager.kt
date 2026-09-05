package com.xtmanager.archive

import com.xtmanager.core.logger.AppLogger
import com.xtmanager.runtime.AlpineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader

class CliArchiveManager(
    private val alpineManager: AlpineManager? = null,
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

        val hasAlpine7z = alpineManager?.hasAlpineBinary("7z") == true || alpineManager?.hasAlpineBinary("7za") == true
        val binPath = findBinaryPath("7z") ?: findBinaryPath("7za")

        if (hasAlpine7z && alpineManager != null) {
            val 7zBin = if (alpineManager.hasAlpineBinary("7z")) "/usr/bin/7z" else "/usr/bin/7za"
            AppLogger.i("ARCHIVE", "⚡ Active Engine: 7z CLI (Alpine PRoot) for target '$output'")
            run7zCompressAlpine(alpineManager, 7zBin, sources, output, format, level, password) { progress, file ->
                onProgress(progress, "⚡ [7z Alpine] $file")
            }
            return@withContext
        }

        if (binPath != null) {
            AppLogger.i("ARCHIVE", "⚡ Active Engine: 7z CLI ($binPath) for target '$output'")
            run7zCompressDirect(binPath, sources, output, format, level, password) { progress, file ->
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

        val hasAlpine7z = alpineManager?.hasAlpineBinary("7z") == true || alpineManager?.hasAlpineBinary("7za") == true
        val binPath = findBinaryPath("7z") ?: findBinaryPath("7za")

        if (hasAlpine7z && alpineManager != null) {
            val 7zBin = if (alpineManager.hasAlpineBinary("7z")) "/usr/bin/7z" else "/usr/bin/7za"
            AppLogger.i("ARCHIVE", "⚡ Active Engine: 7z CLI (Alpine PRoot) extracting '$archive'")
            run7zExtractAlpine(alpineManager, 7zBin, archiveFile, destDir, password) { progress, file ->
                onProgress(progress, "⚡ [7z Alpine] $file")
            }
            return@withContext
        }

        if (binPath != null) {
            AppLogger.i("ARCHIVE", "⚡ Active Engine: 7z CLI ($binPath) extracting '$archive'")
            run7zExtractDirect(binPath, archiveFile, destDir, password) { progress, file ->
                onProgress(progress, "⚡ [7z CLI] $file")
            }
            return@withContext
        }

        AppLogger.w("ARCHIVE", "🐢 Active Engine: Java Core Fallback extracting '$archive'")
        fallbackManager.extract(archive, destination, password) { progress, file ->
            onProgress(progress, "🐢 [Java Core] $file")
        }
    }

    private fun build7zCompressArgs(
        bin: String,
        sources: List<String>,
        output: String,
        format: ArchiveFormat,
        level: Int,
        password: String?
    ): List<String> {
        val cmd = mutableListOf(bin, "a", "-bsp1", "-y", "-mx=$level")
        when (format) {
            ArchiveFormat.ZIP -> cmd.add("-tzip")
            ArchiveFormat.TAR -> cmd.add("-ttar")
            ArchiveFormat.TAR_GZ -> cmd.add("-ttar")
            ArchiveFormat.TAR_XZ -> cmd.add("-ttar")
            ArchiveFormat.SEVEN_Z -> cmd.add("-t7z")
        }
        if (!password.isNullOrEmpty()) {
            cmd.add("-p$password")
        }
        cmd.add(output)
        cmd.addAll(sources)
        return cmd
    }

    private fun run7zCompressAlpine(
        alpineManager: AlpineManager,
        7zBin: String,
        sources: List<String>,
        output: String,
        format: ArchiveFormat,
        level: Int,
        password: String?,
        onProgress: (Float, String) -> Unit
    ) {
        val cmd = build7zCompressArgs(7zBin, sources, output, format, level, password)
        AppLogger.d("ARCHIVE", "⚡ Executing Alpine PRoot: ${cmd.joinToString(" ")}")
        val pb = alpineManager.createAlpineProcessBuilder(cmd)
        executeProcessWithParser(pb, SevenZipOutputParser(), onProgress)
    }

    private fun run7zCompressDirect(
        binPath: String,
        sources: List<String>,
        output: String,
        format: ArchiveFormat,
        level: Int,
        password: String?,
        onProgress: (Float, String) -> Unit
    ) {
        val cmd = build7zCompressArgs(binPath, sources, output, format, level, password)
        AppLogger.d("ARCHIVE", "⚡ Executing Direct CLI: ${cmd.joinToString(" ")}")
        val pb = ProcessBuilder(cmd)
        executeProcessWithParser(pb, SevenZipOutputParser(), onProgress)
    }

    private fun run7zExtractAlpine(
        alpineManager: AlpineManager,
        7zBin: String,
        archiveFile: File,
        destDir: File,
        password: String?,
        onProgress: (Float, String) -> Unit
    ) {
        val cmd = mutableListOf(7zBin, "x", archiveFile.absolutePath, "-o${destDir.absolutePath}", "-bsp1", "-y")
        if (!password.isNullOrEmpty()) {
            cmd.add("-p$password")
        }
        AppLogger.d("ARCHIVE", "⚡ Executing Alpine PRoot: ${cmd.joinToString(" ")}")
        val pb = alpineManager.createAlpineProcessBuilder(cmd)
        executeProcessWithParser(pb, SevenZipOutputParser(), onProgress)
    }

    private fun run7zExtractDirect(
        binPath: String,
        archiveFile: File,
        destDir: File,
        password: String?,
        onProgress: (Float, String) -> Unit
    ) {
        val cmd = mutableListOf(binPath, "x", archiveFile.absolutePath, "-o${destDir.absolutePath}", "-bsp1", "-y")
        if (!password.isNullOrEmpty()) {
            cmd.add("-p$password")
        }
        AppLogger.d("ARCHIVE", "⚡ Executing Direct CLI: ${cmd.joinToString(" ")}")
        val pb = ProcessBuilder(cmd)
        executeProcessWithParser(pb, SevenZipOutputParser(), onProgress)
    }

    private fun executeProcessWithParser(
        pb: ProcessBuilder,
        parser: ArchiveOutputParser,
        onProgress: (Float, String) -> Unit
    ) {
        onProgress(0.05f, "Starting CLI archive engine...")
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

    private fun findBinaryPath(binaryName: String): String? {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", binaryName))
            val path = process.inputStream.bufferedReader().readLine()?.trim()
            if (process.waitFor() == 0 && !path.isNullOrEmpty() && File(path).exists()) {
                return path
            }
        } catch (_: Exception) {}

        val candidates = listOf(
            "/data/data/com.xtmanager/files/alpine/usr/bin/$binaryName",
            "/data/data/com.xtmanager/files/alpine/bin/$binaryName",
            "/data/data/com.xtmanager/files/alpine/usr/bin/7za",
            "/system/bin/$binaryName",
            "/system/xbin/$binaryName",
            "/vendor/bin/$binaryName",
            "/data/local/tmp/$binaryName"
        )
        return candidates.firstOrNull { File(it).exists() }
    }
}
