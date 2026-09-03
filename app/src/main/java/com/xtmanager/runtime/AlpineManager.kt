package com.xtmanager.runtime

import android.content.Context
import android.os.Build
import android.util.Log
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
    val isInstalled: Boolean get() = File(alpineDir, "bin/sh").exists() || File(alpineDir, "bin/busybox").exists()

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

            makeExecutable(File(filesDir, "init-sandbox.sh"))
            makeExecutable(File(filesDir, "init-alpine.sh"))

            // 2. Extract Alpine Rootfs if not installed
            if (!isInstalled) {
                onProgress("Extracting Alpine Linux RootFS ($arch)...")
                val rootfsAssetPath = "alpine/$arch/alpine.rootfs"
                val tempTarGz = File(filesDir, "temp_rootfs.tar.gz")
                copyAssetFile(rootfsAssetPath, tempTarGz)

                Log.d(TAG, "Extracting $tempTarGz to $alpineDir")
                extractTarGz(tempTarGz, alpineDir)
                tempTarGz.delete()
                onProgress("Alpine Linux extracted successfully!")
            }

            // 3. Setup rm wrapper inside alpine bin
            val alpineBinRm = File(alpineDir, "bin/rm")
            if (alpineBinRm.exists()) {
                alpineBinRm.delete()
            }
            copyAssetFile("alpine/rm-wrapper.sh", alpineBinRm)
            makeExecutable(alpineBinRm)

            // 4. Create axs symlink if needed
            refreshAxsSymlink()

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup Alpine environment", e)
            onProgress("Error setting up Alpine: ${e.message}")
            return false
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

    private fun refreshAxsSymlink() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val axsPath = Paths.get(filesDir.absolutePath, "axs")
                val nativeAxsPath = Paths.get(context.applicationInfo.nativeLibraryDir, "libaxs.so")
                if (Files.exists(nativeAxsPath)) {
                    Files.deleteIfExists(axsPath)
                    Files.createSymbolicLink(axsPath, nativeAxsPath)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create axs symlink", e)
            }
        }
    }

    /**
     * Pure Kotlin Tar.Gz extractor to avoid dependence on system 'tar' command.
     * Works across all Android versions reliably.
     */
    private fun extractTarGz(tarGzFile: File, destDir: File) {
        GZIPInputStream(tarGzFile.inputStream().buffered()).use { gzis ->
            val buffer = ByteArray(512)
            while (true) {
                val read = readFully(gzis, buffer, 0, 512)
                if (read < 512) break

                // Two consecutive 512-byte zero blocks signal EOF in Tar
                if (buffer.all { it == 0.toByte() }) break

                // Filename (offset 0, length 100)
                val nameBytes = buffer.copyOfRange(0, 100)
                var name = String(nameBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                if (name.isEmpty()) continue

                // Prefix (offset 345, length 155) for long paths
                val prefixBytes = buffer.copyOfRange(345, 500)
                val prefix = String(prefixBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                if (prefix.isNotEmpty()) {
                    name = "$prefix/$name"
                }

                // File size in octal (offset 124, length 12)
                val sizeStr = String(buffer, 124, 12, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
                val fileSize = sizeStr.toLongOrNull(8) ?: 0L

                // Type flag at offset 156 ('0'/'\0'=file, '5'=dir, '2'=symlink)
                val typeFlag = buffer[156].toInt().toChar()

                val targetFile = File(destDir, name)

                when (typeFlag) {
                    '5' -> { // Directory
                        targetFile.mkdirs()
                    }
                    '2' -> { // Symbolic link
                        val linkTargetBytes = buffer.copyOfRange(157, 257)
                        val linkTarget = String(linkTargetBytes, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
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
                    else -> { // Regular file
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
                        // Skip padding to align to 512-byte boundary
                        val remainder = (fileSize % 512).toInt()
                        if (remainder > 0) {
                            val padBuf = ByteArray(512 - remainder)
                            readFully(gzis, padBuf, 0, padBuf.size)
                        }
                    }
                }
            }
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

    fun startAlpineProcess(): Process {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val filesPath = filesDir.absolutePath
        val prootBin = File(nativeDir, "libproot-xed.so").takeIf { it.exists() }?.absolutePath
            ?: File(nativeDir, "libaxs.so").absolutePath

        val initSandboxScript = File(filesDir, "init-sandbox.sh").absolutePath

        // POSIX compliant execution (. instead of source)
        val builder = ProcessBuilder("sh", "-c", ". \"$initSandboxScript\"")
        
        val env = builder.environment()
        env["PREFIX"] = filesPath
        env["NATIVE_DIR"] = nativeDir
        env["FDROID"] = "false"
        env["HOME"] = "/public"
        env["TERM"] = "xterm-256color"
        env["PROOT"] = prootBin

        builder.directory(filesDir)
        builder.redirectErrorStream(true)
        return builder.start()
    }
}
