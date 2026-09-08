package com.xtmanager.runtime

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPInputStream

class AlpineManager(private val context: Context) {

    companion object {
        private const val TAG = "AlpineManager"
    }

    val filesDir: File get() = context.filesDir
    val alpineDir: File get() = File(filesDir, "alpine")
    val isInstalled: Boolean get() = (File(alpineDir, "bin/busybox").exists() || File(alpineDir, "bin/sh").exists()) && File(alpineDir, "etc").exists()

    fun getArchName(): String = "arm64"

    fun setupAlpineEnvironment(onProgress: (String) -> Unit = {}): Boolean {
        try {
            if (!filesDir.exists()) filesDir.mkdirs()
            if (!alpineDir.exists()) alpineDir.mkdirs()

            val arch = getArchName()
            Log.d(TAG, "Setting up Alpine Linux for architecture: $arch")

            // 1. Copy setup scripts from assets
            onProgress("Copying environment scripts...")
            copyAssetFile("alpine/init-sandbox.sh", File(filesDir, "init-sandbox.sh"))
            copyAssetFile("alpine/init-alpine.sh", File(filesDir, "init-alpine.sh"))
            copyAssetFile("alpine/rm-wrapper.sh", File(filesDir, "rm-wrapper.sh"))
            copyAssetFile("alpine/setup-alpine.sh", File(filesDir, "setup-alpine.sh"))
            try { copyAssetFile("alpine/xt-arc.sh", File(filesDir, "xt-arc.sh")) } catch (_: Exception) {}

            makeExecutable(File(filesDir, "init-sandbox.sh"))
            makeExecutable(File(filesDir, "init-alpine.sh"))
            makeExecutable(File(filesDir, "setup-alpine.sh"))
            makeExecutable(File(filesDir, "xt-arc.sh"))

            // 2. Fail-safe: Copy native binaries directly to filesDir
            onProgress("Setting up PRoot native binaries...")
            copyNativeBinaries(arch)

            // 3. Extract Alpine Rootfs if not installed
            if (!isInstalled) {
                onProgress("Extracting Alpine Linux RootFS ($arch)...")
                
                // Clear any incomplete previous extraction
                alpineDir.deleteRecursively()
                alpineDir.mkdirs()

                val rootfsAssetPath = "alpine/$arch/alpine.rootfs"
                val tempTarGz = File(filesDir, "temp_rootfs.tar.gz")
                copyAssetFile(rootfsAssetPath, tempTarGz)

                Log.d(TAG, "Extracting $tempTarGz to $alpineDir")
                extractTarGz(tempTarGz, alpineDir)
                tempTarGz.delete()
                onProgress("Alpine Linux extracted successfully!")
            }

            // 4. Setup rm wrapper inside alpine bin
            val alpineBinDir = File(alpineDir, "bin")
            if (!alpineBinDir.exists()) alpineBinDir.mkdirs()

            val alpineBinRm = File(alpineBinDir, "rm")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Files.deleteIfExists(alpineBinRm.toPath())
                } else if (alpineBinRm.exists()) {
                    alpineBinRm.delete()
                }
                copyAssetFile("alpine/rm-wrapper.sh", alpineBinRm)
                makeExecutable(alpineBinRm)
            } catch (e: Exception) {
                Log.w(TAG, "Optional rm-wrapper setup skipped: ${e.message}")
            }

            fixAlpinePermissionsAndLinks()
            ensureMountPointsExist()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup Alpine environment", e)
            onProgress("Error setting up Alpine: ${e.message}")
            return false
        }
    }

    fun fixAlpinePermissionsAndLinks() {
        try {
            val targetDirs = listOf(
                File(alpineDir, "bin"),
                File(alpineDir, "sbin"),
                File(alpineDir, "lib"),
                File(alpineDir, "lib64"),
                File(alpineDir, "usr/bin"),
                File(alpineDir, "usr/sbin"),
                File(alpineDir, "usr/lib"),
                File(alpineDir, "libexec")
            )

            for (dir in targetDirs) {
                if (dir.exists()) {
                    dir.walkTopDown().forEach { file ->
                        try {
                            file.setReadable(true, false)
                            file.setExecutable(true, false)
                        } catch (_: Exception) {}
                    }
                }
            }

            // Ensure loader symlink /lib/ld-musl-aarch64.so.1 exists and points to musl libc
            val libDir = File(alpineDir, "lib")
            if (libDir.exists()) {
                val muslLib = libDir.listFiles()?.find { it.name.startsWith("libc.musl-") || it.name.startsWith("libc.so") }
                val ldMusl = File(libDir, "ld-musl-aarch64.so.1")
                if (muslLib != null && (!ldMusl.exists() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Files.isSymbolicLink(ldMusl.toPath())))) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Files.deleteIfExists(ldMusl.toPath())
                            Files.createSymbolicLink(ldMusl.toPath(), Paths.get(muslLib.name))
                        } else {
                            muslLib.copyTo(ldMusl, overwrite = true)
                        }
                        makeExecutable(ldMusl)
                    } catch (e: Exception) {
                        Log.w(TAG, "Fix ld-musl symlink failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fixAlpinePermissionsAndLinks error: ${e.message}")
        }
    }


    private fun copyNativeBinaries(arch: String) {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val nativeLibsDir = "alpine/$arch/libs"
        val nativeLibs = arrayOf("libproot-xed.so", "libproot.so", "libtalloc.so")

        for (libName in nativeLibs) {
            val nativeFile = File(nativeDir, libName)
            val outFile = File(filesDir, libName)
            if (!nativeFile.exists() || !outFile.exists()) {
                try {
                    copyAssetFile("$nativeLibsDir/$libName", outFile)
                    makeExecutable(outFile)
                    Log.d(TAG, "Copied native library $libName to ${outFile.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "Optional asset copy skipped for $libName: ${e.message}")
                }
            } else {
                makeExecutable(outFile)
            }
        }

        // Create libtalloc.so.2 link for Android dynamic linker
        try {
            val talloc2 = File(filesDir, "libtalloc.so.2")
            val talloc1 = File(filesDir, "libtalloc.so")
            if (talloc1.exists()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Files.deleteIfExists(talloc2.toPath())
                    Files.createSymbolicLink(talloc2.toPath(), Paths.get("libtalloc.so"))
                } else {
                    talloc1.copyTo(talloc2, overwrite = true)
                }
                makeExecutable(talloc2)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create libtalloc.so.2 link: ${e.message}")
        }
    }

    private fun copyAssetFile(assetPath: String, outFile: File) {
        if (outFile.exists()) outFile.delete()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun makeExecutable(file: File) {
        file.setExecutable(true, false)
        file.setReadable(true, false)
        file.setWritable(true, true)
    }



    /**
     * Pure Kotlin Tar.Gz extractor with GNU LongName/LongLink & Symlink support.
     */
    private fun extractTarGz(tarGzFile: File, destDir: File) {
        GZIPInputStream(tarGzFile.inputStream().buffered()).use { gzis ->
            val buffer = ByteArray(512)
            var nextLongName: String? = null
            var nextLongLink: String? = null

            while (true) {
                val read = readFully(gzis, buffer, 0, 512)
                if (read < 512) break

                // Two consecutive 512-byte zero blocks signal EOF in Tar
                if (buffer.all { it == 0.toByte() }) break

                // Type flag at offset 156
                val typeFlag = buffer[156].toInt().toChar()

                // File size in octal (offset 124, length 12)
                val sizeStr = String(buffer, 124, 12, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                val fileSize = sizeStr.toLongOrNull(8) ?: 0L

                // Handle GNU Long Name / Long Link extensions
                if (typeFlag == 'L') {
                    val nameBuf = ByteArray(fileSize.toInt())
                    readFully(gzis, nameBuf, 0, nameBuf.size)
                    nextLongName = String(nameBuf, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                    skipPadding(gzis, fileSize)
                    continue
                } else if (typeFlag == 'K') {
                    val linkBuf = ByteArray(fileSize.toInt())
                    readFully(gzis, linkBuf, 0, linkBuf.size)
                    nextLongLink = String(linkBuf, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                    skipPadding(gzis, fileSize)
                    continue
                } else if (typeFlag == 'x' || typeFlag == 'g') { // PAX Extended headers
                    skipPayload(gzis, fileSize)
                    skipPadding(gzis, fileSize)
                    continue
                }

                // Header filename (offset 0, length 100)
                val headerNameBytes = buffer.copyOfRange(0, 100)
                var name = nextLongName ?: String(headerNameBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                nextLongName = null

                if (name.isEmpty()) continue

                // Prefix (offset 345, length 155) for long paths if not overridden by GNU LongName
                if (buffer[345] != 0.toByte() && !name.contains("/")) {
                    val prefixBytes = buffer.copyOfRange(345, 500)
                    val prefix = String(prefixBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                    if (prefix.isNotEmpty()) {
                        name = "$prefix/$name"
                    }
                }

                // File mode (offset 100, length 8)
                val modeStr = String(buffer, 100, 8, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                val mode = modeStr.toIntOrNull(8) ?: 0

                val targetFile = File(destDir, name)

                when (typeFlag) {
                    '5' -> { // Directory
                        targetFile.mkdirs()
                    }
                    '2' -> { // Symbolic link
                        val headerLinkBytes = buffer.copyOfRange(157, 257)
                        val linkTarget = nextLongLink ?: String(headerLinkBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                        nextLongLink = null

                        targetFile.parentFile?.mkdirs()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                Files.deleteIfExists(targetFile.toPath())
                                Files.createSymbolicLink(targetFile.toPath(), Paths.get(linkTarget))
                            } catch (e: Exception) {
                                Log.w(TAG, "Symlink creation skipped for $name -> $linkTarget: ${e.message}")
                            }
                        }
                    }
                    else -> { // Regular file ('0' or '\0')
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { fos ->
                            var remaining = fileSize
                            val copyBuf = ByteArray(8192)
                            while (remaining > 0) {
                                val toRead = Math.min(remaining, copyBuf.size.toLong()).toInt()
                                val bytesRead = gzis.read(copyBuf, 0, toRead)
                                if (bytesRead <= 0) break
                                fos.write(copyBuf, 0, bytesRead)
                                remaining -= bytesRead
                            }
                        }
                        skipPadding(gzis, fileSize)

                        // Set permissions for executables
                        if ((mode and 73) != 0 || name.contains("bin/") || name.contains("sbin/") || name.contains("lib/")) {
                            makeExecutable(targetFile)
                        } else {
                            targetFile.setReadable(true, false)
                        }
                    }
                }
            }
        }
    }

    private fun skipPadding(gzis: InputStream, fileSize: Long) {
        val remainder = (fileSize % 512).toInt()
        if (remainder > 0) {
            val padBuf = ByteArray(512 - remainder)
            readFully(gzis, padBuf, 0, padBuf.size)
        }
    }

    private fun skipPayload(gzis: InputStream, fileSize: Long) {
        var remaining = fileSize
        val copyBuf = ByteArray(8192)
        while (remaining > 0) {
            val toRead = Math.min(remaining, copyBuf.size.toLong()).toInt()
            val bytesRead = gzis.read(copyBuf, 0, toRead)
            if (bytesRead <= 0) break
            remaining -= bytesRead
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        var totalRead = 0
        while (totalRead < length) {
            val read = input.read(buffer, offset + totalRead, length - totalRead)
            if (read <= 0) break
            totalRead += read
        }
        return totalRead
    }

    fun createAlpineTerminalSession(
        client: com.termux.terminal.TerminalSessionClient,
        initialPath: String? = null
    ): com.termux.terminal.TerminalSession {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val filesPath = filesDir.absolutePath
        
        val prootBinInNative = File(nativeDir, "libproot-xed.so").takeIf { it.exists() }?.absolutePath
            ?: File(nativeDir, "libproot.so").takeIf { it.exists() }?.absolutePath

        var prootBinInFiles = File(filesDir, "libproot-xed.so").takeIf { it.exists() }?.absolutePath
            ?: File(filesDir, "libproot.so").takeIf { it.exists() }?.absolutePath

        if (prootBinInNative == null && prootBinInFiles == null) {
            copyNativeBinaries("arm64")
            prootBinInFiles = File(filesDir, "libproot-xed.so").takeIf { it.exists() }?.absolutePath
                ?: File(filesDir, "libproot.so").takeIf { it.exists() }?.absolutePath
        }

        val prootBin = prootBinInNative ?: prootBinInFiles ?: File(filesDir, "libproot-xed.so").absolutePath
        val initSandboxScript = File(filesDir, "init-sandbox.sh").absolutePath

        val publicDir = File(filesDir, "public")
        if (!publicDir.exists()) publicDir.mkdirs()

        val workingDir = if (!initialPath.isNullOrBlank() && File(initialPath).exists()) {
            File(initialPath)
        } else {
            publicDir
        }

        val envList = arrayOf(
            "PREFIX=$filesPath",
            "NATIVE_DIR=$nativeDir",
            "HOME=/public",
            "TERM=xterm-256color",
            "PROOT=$prootBin",
            "LD_LIBRARY_PATH=$filesPath:$nativeDir",
            "INITIAL_CWD=${normalizePathForAlpine(workingDir.absolutePath)}"
        )

        val shellPath = "/system/bin/sh"
        val args = arrayOf("/system/bin/sh", initSandboxScript)

        return com.termux.terminal.TerminalSession(
            shellPath,
            workingDir.absolutePath,
            args,
            envList,
            10000,
            client
        )
    }

    fun hasAlpineBinary(binaryName: String): Boolean {
        if (!isInstalled) return false
        val alpineUsrBin = File(alpineDir, "usr/bin/$binaryName")
        val alpineBin = File(alpineDir, "bin/$binaryName")
        return alpineUsrBin.exists() || alpineBin.exists()
    }

    fun ensureMountPointsExist(sysMounts: List<String> = emptyList()) {
        val baseMounts = listOf("sdcard", "storage", "mnt", "data", "files", "tmp", "dev", "proc", "sys")
        for (m in baseMounts) {
            val d = File(alpineDir, m)
            if (!d.exists()) {
                try { d.mkdirs() } catch (_: Exception) {}
            }
        }
        for (m in sysMounts) {
            if (m.startsWith("/")) {
                val rel = m.removePrefix("/")
                val d = File(alpineDir, rel)
                if (!d.exists()) {
                    try { d.mkdirs() } catch (_: Exception) {}
                }
            }
        }
    }

    fun createAlpineProcessBuilder(commandInAlpine: List<String>): ProcessBuilder {
        fixAlpinePermissionsAndLinks()
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val filesPath = filesDir.absolutePath

        val prootBinInNative = File(nativeDir, "libproot-xed.so").takeIf { it.exists() }?.absolutePath
            ?: File(nativeDir, "libproot.so").takeIf { it.exists() }?.absolutePath

        var prootBinInFiles = File(filesDir, "libproot-xed.so").takeIf { it.exists() }?.absolutePath
            ?: File(filesDir, "libproot.so").takeIf { it.exists() }?.absolutePath

        if (prootBinInNative == null && prootBinInFiles == null) {
            copyNativeBinaries(getArchName())
            prootBinInFiles = File(filesDir, "libproot-xed.so").takeIf { it.exists() }?.absolutePath
                ?: File(filesDir, "libproot.so").takeIf { it.exists() }?.absolutePath
        }

        val prootBin = prootBinInNative ?: prootBinInFiles ?: File(filesDir, "libproot-xed.so").absolutePath
        val prootLoader = File(nativeDir, "libproot.so").takeIf { it.exists() }?.absolutePath
            ?: File(filesDir, "libproot.so").absolutePath
        val prootLoader32 = File(nativeDir, "libproot32.so").takeIf { it.exists() }?.absolutePath
            ?: File(filesDir, "libproot32.so").absolutePath

        val linker = if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"

        val prootExec = if (prootBin.startsWith("/data/app/")) {
            listOf(prootBin)
        } else {
            listOf(linker, prootBin)
        }

        val rawSysMounts = mutableListOf<String>()
        val sysMountArgs = mutableListOf<String>()
        val systemPaths = arrayOf(
            "/apex", "/odm", "/product", "/system", "/system_ext", "/vendor",
            "/proc", "/sys", "/dev", "/linkerconfig/ld.config.txt"
        )
        for (mnt in systemPaths) {
            if (File(mnt).exists()) {
                rawSysMounts.add(mnt)
                sysMountArgs.add("-b")
                sysMountArgs.add(mnt)
            }
        }

        // CRITICAL FIX: Ensure guest mount point directories exist inside alpineDir before starting PRoot!
        ensureMountPointsExist(rawSysMounts)

        val cmd = mutableListOf<String>()
        cmd.addAll(prootExec)
        cmd.addAll(listOf(
            "--kill-on-exit",
            "-b", "/sdcard",
            "-b", "/storage",
            "-b", "/mnt",
            "-b", "/data",
            "-b", filesPath,
            "-b", "$filesPath:/files",
            "-b", nativeDir
        ))
        cmd.addAll(sysMountArgs)
        cmd.addAll(listOf(
            "-r", "$filesPath/alpine",
            "-0",
            "--link2symlink",
            "--sysvipc",
            "-L"
        ))

        cmd.addAll(commandInAlpine)

        val pb = ProcessBuilder(cmd)
        val env = pb.environment()
        env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        env["PREFIX"] = filesPath
        env["NATIVE_DIR"] = nativeDir
        env["HOME"] = "/root"
        env["TERM"] = "xterm-256color"
        env["PROOT_TMP_DIR"] = "$filesPath/tmp"
        env["PROOT_LOADER"] = prootLoader
        env["PROOT_LOADER32"] = prootLoader32
        env["PROOT"] = prootBin
        env["LD_LIBRARY_PATH"] = "$filesPath:$nativeDir"

        return pb
    }

    fun normalizePathForAlpine(path: String): String {
        return if (path.startsWith("/storage/emulated/0")) {
            path.replace("/storage/emulated/0", "/sdcard")
        } else {
            path
        }
    }

    suspend fun compressArchiveWithAlpine(
        format: String,
        outputArchive: String,
        sources: List<String>,
        onProgress: (processedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val totalBytes = sources.sumOf { path ->
            val f = File(path)
            if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            else f.length()
        }.coerceAtLeast(1L)

        val scriptPath = File(filesDir, "xt-arc.sh")
        try { copyAssetFile("alpine/xt-arc.sh", scriptPath); makeExecutable(scriptPath) } catch (_: Exception) {}

        val canonicalOutput = normalizePathForAlpine(try { File(outputArchive).canonicalPath } catch (_: Exception) { outputArchive })
        val canonicalSources = sources.map { s ->
            normalizePathForAlpine(try { File(s).canonicalPath } catch (_: Exception) { s })
        }

        val cmdInAlpine = mutableListOf("/bin/sh", "/files/xt-arc.sh", "compress", format, canonicalOutput)
        cmdInAlpine.addAll(canonicalSources)

        val cmdString = cmdInAlpine.joinToString(" ")
        com.xtmanager.core.logger.AppLogger.i("ALPINE_CLI", "🚀 Executing Alpine CLI Compress: $cmdString")
        Log.d(TAG, "🚀 Executing Alpine CLI Compress: $cmdString")

        try {
            val pb = createAlpineProcessBuilder(cmdInAlpine)
            val proc = pb.start()

            val reader = proc.inputStream.bufferedReader()
            val errReader = proc.errorStream.bufferedReader()

            val jobStdout = launch(Dispatchers.IO) {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: ""
                    com.xtmanager.core.logger.AppLogger.d("ALPINE_CLI", "[STDOUT] $l")
                    Log.d("ALPINE_CLI", "[STDOUT] $l")
                }
            }

            val jobStderr = launch(Dispatchers.IO) {
                var line: String?
                while (errReader.readLine().also { line = it } != null) {
                    val l = line ?: ""
                    val trimmed = l.trim()
                    val pct = trimmed.toLongOrNull()
                    if (pct != null && pct in 0..100) {
                        val processed = (totalBytes * pct) / 100
                        onProgress(processed, totalBytes)
                    } else {
                        com.xtmanager.core.logger.AppLogger.i("ALPINE_CLI", "[STDERR] $l")
                        Log.d("ALPINE_CLI", "[STDERR] $l")
                    }
                }
            }

            jobStdout.join()
            jobStderr.join()
            val exitCode = proc.waitFor()
            if (exitCode == 0) {
                onProgress(totalBytes, totalBytes)
            }
            com.xtmanager.core.logger.AppLogger.i("ALPINE_CLI", "🏁 Alpine CLI Compress ($format) finished with exit code: $exitCode")
            Log.d(TAG, "🏁 Alpine CLI Compress ($format) finished with exit code: $exitCode")
            exitCode
        } catch (e: Exception) {
            Log.e(TAG, "❌ Alpine CLI Compress error: ${e.message}", e)
            com.xtmanager.core.logger.AppLogger.e("ALPINE_CLI", "❌ Compress Error: ${e.message}")
            -1
        }
    }

    suspend fun extractArchiveWithAlpine(
        archivePath: String,
        outputDir: String,
        onProgress: (processedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val canonicalArchive = normalizePathForAlpine(try { File(archivePath).canonicalPath } catch (_: Exception) { archivePath })
        val canonicalOutput = normalizePathForAlpine(try { File(outputDir).canonicalPath } catch (_: Exception) { outputDir })

        val totalBytes = File(canonicalArchive).length().coerceAtLeast(1L)
        val scriptPath = File(filesDir, "xt-arc.sh")
        try { copyAssetFile("alpine/xt-arc.sh", scriptPath); makeExecutable(scriptPath) } catch (_: Exception) {}

        val cmdInAlpine = listOf("/bin/sh", "/files/xt-arc.sh", "extract", canonicalArchive, canonicalOutput)
        val cmdString = cmdInAlpine.joinToString(" ")
        com.xtmanager.core.logger.AppLogger.i("ALPINE_CLI", "🚀 Executing Alpine CLI Extract: $cmdString")
        Log.d(TAG, "🚀 Executing Alpine CLI Extract: $cmdString")

        try {
            val pb = createAlpineProcessBuilder(cmdInAlpine)
            val proc = pb.start()

            val reader = proc.inputStream.bufferedReader()
            val errReader = proc.errorStream.bufferedReader()

            val jobStdout = launch(Dispatchers.IO) {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: ""
                    com.xtmanager.core.logger.AppLogger.d("ALPINE_CLI", "[STDOUT] $l")
                    Log.d("ALPINE_CLI", "[STDOUT] $l")
                }
            }

            val jobStderr = launch(Dispatchers.IO) {
                var line: String?
                while (errReader.readLine().also { line = it } != null) {
                    val l = line ?: ""
                    val trimmed = l.trim()
                    val pct = trimmed.toLongOrNull()
                    if (pct != null && pct in 0..100) {
                        val processed = (totalBytes * pct) / 100
                        onProgress(processed, totalBytes)
                    } else {
                        com.xtmanager.core.logger.AppLogger.i("ALPINE_CLI", "[STDERR] $l")
                        Log.d("ALPINE_CLI", "[STDERR] $l")
                    }
                }
            }

            jobStdout.join()
            jobStderr.join()
            val exitCode = proc.waitFor()
            if (exitCode == 0) {
                onProgress(totalBytes, totalBytes)
            }
            com.xtmanager.core.logger.AppLogger.i("ALPINE_CLI", "🏁 Alpine CLI Extract finished with exit code: $exitCode")
            Log.d(TAG, "🏁 Alpine CLI Extract finished with exit code: $exitCode")
            exitCode
        } catch (e: Exception) {
            Log.e(TAG, "❌ Alpine CLI Extract error: ${e.message}", e)
            com.xtmanager.core.logger.AppLogger.e("ALPINE_CLI", "❌ Extract Error: ${e.message}")
            -1
        }
    }
}
