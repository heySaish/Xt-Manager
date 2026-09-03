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

    private fun extractTarGz(tarGzFile: File, destDir: File) {
        val process = ProcessBuilder("tar", "-xzf", tarGzFile.absolutePath, "-C", destDir.absolutePath)
            .redirectErrorStream(true)
            .start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val output = process.inputStream.bufferedReader().readText()
            Log.e(TAG, "tar extraction failed with exit code $exitCode: $output")
            throw RuntimeException("Tar extraction failed: $output")
        }
    }

    fun startAlpineProcess(): Process {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val filesPath = filesDir.absolutePath
        val prootBin = File(nativeDir, "libproot-xed.so").takeIf { it.exists() }?.absolutePath
            ?: File(nativeDir, "libaxs.so").absolutePath

        val initSandboxScript = File(filesDir, "init-sandbox.sh").absolutePath

        val builder = ProcessBuilder("sh", "-c", "source $initSandboxScript")
        
        val env = builder.environment()
        env["PREFIX"] = filesPath
        env["NATIVE_DIR"] = nativeDir
        env["HOME"] = "/public"
        env["TERM"] = "xterm-256color"
        env["PROOT"] = prootBin

        builder.directory(filesDir)
        builder.redirectErrorStream(true)
        return builder.start()
    }
}
