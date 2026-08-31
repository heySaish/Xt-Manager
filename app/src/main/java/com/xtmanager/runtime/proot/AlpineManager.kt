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
            val hasScripts = File(context.filesDir, "init-sandbox.sh").exists() && File(context.filesDir, "init-alpine.sh").exists()
            return prootManager.isProotInstalled && hasRootfs && hasScripts
        }

    suspend fun install(
        onProgress: (status: String, progress: Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            val arch = getArchForAbi(abi)
            val filesDir = context.filesDir

            // 1. Create directory structure
            onProgress("Preparing directories...", 0.05f)
            if (!prootManager.alpineRootfs.exists()) prootManager.alpineRootfs.mkdirs()

            // 2. Copy scripts from assets to filesDir
            onProgress("Copying environment scripts...", 0.10f)
            copyAssetToFile("init-alpine.sh", File(filesDir, "init-alpine.sh"))
            copyAssetToFile("init-sandbox.sh", File(filesDir, "init-sandbox.sh"))
            copyAssetToFile("rm-wrapper.sh", File(filesDir, "rm-wrapper.sh"))

            // 3. Create axs symlink to libaxs.so
            onProgress("Setting up compatibility layer...", 0.15f)
            val axsLink = File(filesDir, "axs")
            val nativeAxs = File(context.applicationInfo.nativeLibraryDir, "libaxs.so")
            if (axsLink.exists()) axsLink.delete()
            try {
                android.system.Os.symlink(nativeAxs.absolutePath, axsLink.absolutePath)
            } catch (e: Exception) {
                // Fallback to direct copy if symlink fails
                nativeAxs.copyTo(axsLink, overwrite = true)
            }
            axsLink.setExecutable(true)

            // 4. Locate and Copy/Download Alpine rootfs
            val tempRootfsFile = File(filesDir, "alpine.tar.gz")
            val rootfsAssetPath = "alpine-minirootfs-$arch.tar.gz"

            if (assetExists(rootfsAssetPath)) {
                onProgress("Copying Alpine Rootfs from assets...", 0.25f)
                copyAssetToFile(rootfsAssetPath, tempRootfsFile)
            } else if (assetExists("alpine-minirootfs-aarch64.tar.gz") && arch == "aarch64") {
                onProgress("Copying Alpine Rootfs from assets...", 0.25f)
                copyAssetToFile("alpine-minirootfs-aarch64.tar.gz", tempRootfsFile)
            } else {
                onProgress("Downloading Alpine Rootfs...", 0.20f)
                val rootfsUrl = getAlpineRootfsUrlForArch(arch)
                downloadFile(rootfsUrl, tempRootfsFile) { progress ->
                    onProgress("Downloading Alpine Rootfs...", 0.20f + (progress * 0.35f))
                }
            }

            // 5. Extract Alpine Rootfs using toybox tar
            onProgress("Extracting Alpine Rootfs...", 0.60f)
            val success = extractTarGz(tempRootfsFile, prootManager.alpineRootfs)
            if (tempRootfsFile.exists()) tempRootfsFile.delete()
            
            if (!success) {
                throw IOException("Extraction failed. Make sure your device has enough free space.")
            }

            // 6. Setup resolv.conf inside Alpine
            onProgress("Configuring network settings...", 0.80f)
            setupDns(prootManager.alpineRootfs)

            // 7. Inject Acode-style rm wrapper
            onProgress("Injecting secure rm wrapper...", 0.85f)
            val alpineBinRm = File(prootManager.alpineRootfs, "bin/rm")
            if (alpineBinRm.exists()) alpineBinRm.delete()
            copyAssetToFile("rm-wrapper.sh", alpineBinRm)
            alpineBinRm.setExecutable(true)

            // 8. Run sandbox configure script
            onProgress("Completing sandbox configuration...", 0.90f)
            val configureSuccess = runConfigureScript()
            if (!configureSuccess) {
                throw IOException("Sandbox initial configuration failed.")
            }

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
        return "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/$arch/alpine-minirootfs-3.21.0-$arch.tar.gz"
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
                val buffer = ByteArray(16384)
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
        connection.connectTimeout = 20000
        connection.readTimeout = 20000
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("Server returned HTTP ${connection.responseCode}: ${connection.responseMessage}")
        }

        val fileLength = connection.contentLength
        BufferedInputStream(connection.inputStream).use { input ->
            FileOutputStream(destination).use { output ->
                val data = ByteArray(8192)
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
        return try {
            val process = ProcessBuilder(
                "tar",
                "--no-same-owner",
                "-xf",
                tarGzFile.absolutePath,
                "-C",
                targetDir.absolutePath
            ).start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
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

    private fun runConfigureScript(): Boolean {
        return try {
            val pb = ProcessBuilder("sh", "-c", "source ${context.filesDir.absolutePath}/init-sandbox.sh --installing")
                .directory(context.filesDir)
            
            pb.environment()["PREFIX"] = context.filesDir.absolutePath
            pb.environment()["NATIVE_DIR"] = context.applicationInfo.nativeLibraryDir
            pb.environment()["FDROID"] = "false"
            
            val p = pb.start()
            val exitCode = p.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
