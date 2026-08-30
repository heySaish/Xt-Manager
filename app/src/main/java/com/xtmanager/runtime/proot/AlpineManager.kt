package com.xtmanager.runtime.proot

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class AlpineManager(
    private val context: Context,
    private val prootManager: ProotManager
) {
    val isInstalled: Boolean
        get() {
            val rootfs = prootManager.alpineRootfs
            val hasRootfs = rootfs.exists() && File(rootfs, "bin/sh").exists()
            return prootManager.isProotInstalled && hasRootfs
        }

    suspend fun install(
        onProgress: (status: String, progress: Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val arch = getArchForAbi(abi)
            
            // 1. Create directory structure
            onProgress("Preparing directories...", 0.10f)
            if (!prootManager.alpineRootfs.exists()) prootManager.alpineRootfs.mkdirs()

            // 2. Install Alpine rootfs (Check assets first, fallback to network download)
            val tempRootfsFile = File(context.cacheDir, "alpine_temp.tar.gz")
            val rootfsAssetPath = "alpine-minirootfs-$arch.tar.gz"
            
            if (assetExists(rootfsAssetPath)) {
                onProgress("Copying Alpine Rootfs from assets...", 0.30f)
                copyAssetToFile(rootfsAssetPath, tempRootfsFile)
            } else {
                onProgress("Downloading Alpine Rootfs...", 0.20f)
                val rootfsUrl = getAlpineRootfsUrlForArch(arch)
                downloadFile(rootfsUrl, tempRootfsFile) { progress ->
                    onProgress("Downloading Alpine Rootfs...", 0.20f + (progress * 0.40f))
                }
            }

            // 3. Extract Alpine Rootfs
            onProgress("Extracting Alpine Rootfs...", 0.70f)
            val success = extractTarGz(tempRootfsFile, prootManager.alpineRootfs)
            tempRootfsFile.delete()
            
            if (!success) {
                throw IOException("Extraction failed")
            }

            // 4. Setup DNS resolver inside Alpine
            onProgress("Configuring network settings...", 0.90f)
            setupDns(prootManager.alpineRootfs)

            onProgress("Installation complete!", 1.0f)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            onProgress("Installation failed: ${e.localizedMessage}", -1.0f)
            false
        }
    }

    private fun getArchForAbi(abi: String): String {
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "armhf"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> "aarch64"
        }
    }

    private fun getAlpineRootfsUrlForArch(arch: String): String {
        return "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/$arch/alpine-minirootfs-3.20.2-$arch.tar.gz"
    }

    private fun assetExists(path: String): Boolean {
        return try {
            context.assets.open(path).close()
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun copyAssetToFile(assetPath: String, destination: File) {
        context.assets.open(assetPath).use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var length: Int
                while (input.read(buffer).also { length = it } > 0) {
                    output.write(buffer, 0, length)
                }
            }
        }
    }

    private fun downloadFile(urlStr: String, destination: File, onProgressUpdate: (Float) -> Unit) {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("Server returned HTTP ${connection.responseCode}: ${connection.responseMessage}")
        }

        val fileLength = connection.contentLength
        BufferedInputStream(connection.inputStream).use { input ->
            FileOutputStream(destination).use { output ->
                val data = ByteArray(4096)
                var total = 0L
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        onProgressUpdate(total.toFloat() / fileLength)
                    }
                    output.write(data, 0, count)
                }
            }
        }
    }

    private fun extractTarGz(tarGzFile: File, targetDir: File): Boolean {
        try {
            val process = ProcessBuilder(
                "tar",
                "-xzf",
                tarGzFile.absolutePath,
                "-C",
                targetDir.absolutePath
            ).start()
            val exitCode = process.waitFor()
            return exitCode == 0
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun setupDns(rootfsDir: File) {
        val etcDir = File(rootfsDir, "etc")
        if (!etcDir.exists()) etcDir.mkdirs()
        val resolvConf = File(etcDir, "resolv.conf")
        FileOutputStream(resolvConf).use {
            it.write("nameserver 8.8.8.8\nnameserver 8.8.4.4\n".toByteArray())
        }
    }
}
