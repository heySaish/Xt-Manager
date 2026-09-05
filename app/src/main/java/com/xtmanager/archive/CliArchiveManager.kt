package com.xtmanager.archive

import com.xtmanager.core.logger.AppLogger
import com.xtmanager.runtime.AlpineManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader

class CliArchiveManager(
    private val alpineManager: AlpineManager? = null,
    private val localArchiveManager: LocalArchiveManager = LocalArchiveManager()
) : ArchiveManager {

    private val router: ArchiveRouter? = alpineManager?.let { ArchiveRouter(it) }

    override suspend fun compress(
        sources: List<String>,
        output: String,
        format: ArchiveFormat,
        level: Int,
        password: String?,
        onProgress: (progress: Float, currentFile: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext

        val engine = router?.resolveEngine(format, isExtract = false) ?: ArchiveEngine.JAVA_FALLBACK

        if (engine == ArchiveEngine.ALPINE_PROOT && alpineManager != null) {
            val reqBinary = router!!.getRequiredBinaryForFormat(format, isExtract = false)
            AppLogger.i("ARCHIVE", "⚡ [Primary Route] Active Engine: Alpine PRoot CLI ($reqBinary) for target '$output'")
            
            runAlpineCompress(alpineManager, reqBinary, sources, output, format, level, password) { progress, file ->
                onProgress(progress, "⚡ [Alpine $reqBinary] $file")
            }
            return@withContext
        }

        // Secondary Route: Java Fallback (or throw error for non-ZIP)
        AppLogger.w("ARCHIVE", "🐢 [Secondary Route] Active Engine: Java Fallback for target '$output'")
        localArchiveManager.compress(sources, output, format, level, password) { progress, file ->
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

        val engine = router?.resolveEngineForFile(archive) ?: ArchiveEngine.JAVA_FALLBACK

        if (engine == ArchiveEngine.ALPINE_PROOT && alpineManager != null) {
            val reqBinary = router!!.getRequiredBinaryForFile(archive)
            AppLogger.i("ARCHIVE", "⚡ [Primary Route] Active Engine: Alpine PRoot CLI ($reqBinary) extracting '$archive'")
            
            runAlpineExtract(alpineManager, reqBinary, archiveFile, destDir, password) { progress, file ->
                onProgress(progress, "⚡ [Alpine $reqBinary] $file")
            }
            return@withContext
        }

        // Secondary Route: Java Fallback (or throw error for non-ZIP)
        AppLogger.w("ARCHIVE", "🐢 [Secondary Route] Active Engine: Java Fallback extracting '$archive'")
        localArchiveManager.extract(archive, destination, password) { progress, file ->
            onProgress(progress, "🐢 [Java Core] $file")
        }
    }

    private fun runAlpineCompress(
        alpineManager: AlpineManager,
        binary: String,
        sources: List<String>,
        output: String,
        format: ArchiveFormat,
        level: Int,
        password: String?,
        onProgress: (Float, String) -> Unit
    ) {
        val outputFile = File(output)
        outputFile.parentFile?.mkdirs()

        val xtArcScript = File(alpineManager.filesDir, "xt-arc.sh")
        val cmd = mutableListOf<String>()
        val parser: ArchiveOutputParser = SevenZipOutputParser()

        if (xtArcScript.exists()) {
            val formatStr = when (format) {
                ArchiveFormat.ZIP -> "zip"
                ArchiveFormat.TAR -> "tar"
                ArchiveFormat.TAR_GZ -> "tar.gz"
                ArchiveFormat.TAR_XZ -> "tar.xz"
                ArchiveFormat.SEVEN_Z -> "7z"
            }
            cmd.addAll(listOf("sh", xtArcScript.absolutePath, "compress", formatStr, outputFile.absolutePath))
            cmd.addAll(sources)
        } else {
            val firstFile = File(sources.first())
            val parentDir = firstFile.parentFile?.absolutePath ?: "."
            val relativeBasenames = sources.map { File(it).name }
            val binPath = if (binary == "7z" && !alpineManager.hasAlpineBinary("7z") && alpineManager.hasAlpineBinary("7za")) "7za" else binary

            if (binPath == "7z" || binPath == "7za") {
                cmd.addAll(listOf(binPath, "a", "-bsp1", "-y", "-mx=$level", "-mmt=on"))
                when (format) {
                    ArchiveFormat.ZIP -> cmd.add("-tzip")
                    ArchiveFormat.TAR -> cmd.add("-ttar")
                    ArchiveFormat.TAR_GZ -> cmd.add("-ttar")
                    ArchiveFormat.TAR_XZ -> cmd.add("-ttar")
                    ArchiveFormat.SEVEN_Z -> cmd.add("-t7z")
                }
                if (!password.isNullOrEmpty()) cmd.add("-p$password")
                cmd.add(outputFile.absolutePath)
                cmd.addAll(sources)
            } else if (binPath == "tar") {
                val flag = when (format) {
                    ArchiveFormat.TAR_GZ -> "-czf"
                    ArchiveFormat.TAR_XZ -> "-cJf"
                    else -> "-cf"
                }
                cmd.addAll(listOf("tar", flag, outputFile.absolutePath, "-C", parentDir))
                cmd.addAll(relativeBasenames)
            } else {
                cmd.addAll(listOf("zip", "-r", "-$level", outputFile.absolutePath))
                cmd.addAll(sources)
            }
        }

        val pb = alpineManager.createAlpineProcessBuilder(cmd)
        executeProcessWithParser(pb, parser, onProgress)
    }

    private fun runAlpineExtract(
        alpineManager: AlpineManager,
        binary: String,
        archiveFile: File,
        destDir: File,
        password: String?,
        onProgress: (Float, String) -> Unit
    ) {
        if (!destDir.exists()) destDir.mkdirs()

        val xtArcScript = File(alpineManager.filesDir, "xt-arc.sh")
        val cmd = mutableListOf<String>()
        val parser: ArchiveOutputParser

        if (xtArcScript.exists()) {
            cmd.addAll(listOf("sh", xtArcScript.absolutePath, "extract", archiveFile.absolutePath, destDir.absolutePath))
            if (!password.isNullOrEmpty()) cmd.add(password)
            parser = SevenZipOutputParser()
        } else {
            val binPath = if (binary == "7z" && !alpineManager.hasAlpineBinary("7z") && alpineManager.hasAlpineBinary("7za")) "7za" else binary

            if (binPath == "7z" || binPath == "7za") {
                cmd.addAll(listOf(binPath, "x", archiveFile.absolutePath, "-o${destDir.absolutePath}", "-bsp1", "-y", "-mmt=on"))
                if (!password.isNullOrEmpty()) cmd.add("-p$password")
                parser = SevenZipOutputParser()
            } else if (binPath == "tar") {
                cmd.addAll(listOf("tar", "--no-same-owner", "--no-same-permissions", "-xf", archiveFile.absolutePath, "-C", destDir.absolutePath))
                parser = SevenZipOutputParser()
            } else {
                cmd.addAll(listOf("unzip", "-o", archiveFile.absolutePath, "-d", destDir.absolutePath))
                if (!password.isNullOrEmpty()) cmd.add("-P$password")
                parser = UnzipOutputParser()
            }
        }

        val pb = alpineManager.createAlpineProcessBuilder(cmd)
        executeProcessWithParser(pb, parser, onProgress)
    }

    private fun executeProcessWithParser(
        pb: ProcessBuilder,
        parser: ArchiveOutputParser,
        onProgress: (Float, String) -> Unit
    ) {
        onProgress(0.05f, "Starting Alpine PRoot archive engine...")
        pb.redirectErrorStream(true)
        val process = pb.start()

        val reader = InputStreamReader(process.inputStream)
        val sb = StringBuilder()
        val collectedLogs = mutableListOf<String>()
        var charCode = reader.read()
        var lastReportTime = 0L

        while (charCode != -1) {
            val ch = charCode.toChar()
            if (ch == '\r' || ch == '\n') {
                if (sb.isNotEmpty()) {
                    val line = sb.toString()
                    collectedLogs.add(line)
                    if (collectedLogs.size > 200) {
                        collectedLogs.removeAt(0)
                    }
                    val event = parser.parseLine(line)
                    if (event is ProgressEvent.Progressing) {
                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 50) {
                            onProgress(event.percentage, event.currentItem)
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
        if (sb.isNotEmpty()) {
            collectedLogs.add(sb.toString())
        }

        val exitCode = process.waitFor()
        val isTar = pb.command().any { it.contains("tar") }

        // GNU tar returns exit code 1 for non-fatal warnings (e.g., owner/chmod warnings on Android FUSE storage)
        if (exitCode != 0 && !(exitCode == 1 && isTar)) {
            val lastOutput = collectedLogs.takeLast(10).joinToString("\n")
            AppLogger.e("ARCHIVE", "❌ Alpine PRoot CLI operation failed (exit code $exitCode):\n$lastOutput")
            throw java.io.IOException("Alpine PRoot CLI operation failed (exit code $exitCode): ${collectedLogs.lastOrNull() ?: "Unknown error"}")
        } else if (exitCode == 1 && isTar) {
            AppLogger.w("ARCHIVE", "⚠️ tar CLI exited with warning code 1 (non-fatal permission/owner warning on Android storage)")
        }

        AppLogger.i("ARCHIVE", "✅ Alpine PRoot CLI operation completed successfully!")
        onProgress(1.0f, "Operation completed successfully!")
    }
}
