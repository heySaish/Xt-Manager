package com.xtmanager.archive

import com.xtmanager.runtime.tools.ToolManager
import java.io.IOException

class AlpineArchiveManager(private val toolManager: ToolManager) : ArchiveManager {

    override suspend fun compress(
        sources: List<String>,
        output: String,
        format: ArchiveFormat,
        onProgress: (progress: Float, currentFile: String) -> Unit
    ) {
        onProgress(0.1f, "Preparing compression...")
        
        when (format) {
            ArchiveFormat.ZIP -> {
                ensureToolInstalled("zip")
                onProgress(0.3f, "Compressing to ZIP...")
                val args = mutableListOf("-r", output)
                args.addAll(sources)
                val result = toolManager.execute("zip", args)
                if (!result.isSuccess) {
                    throw IOException("ZIP compression failed: ${result.stderr}")
                }
            }
            ArchiveFormat.TAR -> {
                ensureToolInstalled("tar")
                onProgress(0.3f, "Compressing to TAR...")
                val args = mutableListOf("-cf", output)
                args.addAll(sources)
                val result = toolManager.execute("tar", args)
                if (!result.isSuccess) {
                    throw IOException("TAR compression failed: ${result.stderr}")
                }
            }
            ArchiveFormat.TAR_GZ -> {
                ensureToolInstalled("tar")
                ensureToolInstalled("gzip")
                onProgress(0.3f, "Compressing to TAR.GZ...")
                val args = mutableListOf("-czf", output)
                args.addAll(sources)
                val result = toolManager.execute("tar", args)
                if (!result.isSuccess) {
                    throw IOException("TAR.GZ compression failed: ${result.stderr}")
                }
            }
            ArchiveFormat.TAR_XZ -> {
                ensureToolInstalled("tar")
                ensureToolInstalled("xz")
                onProgress(0.3f, "Compressing to TAR.XZ...")
                val args = mutableListOf("-cJf", output)
                args.addAll(sources)
                val result = toolManager.execute("tar", args)
                if (!result.isSuccess) {
                    throw IOException("TAR.XZ compression failed: ${result.stderr}")
                }
            }
            ArchiveFormat.SEVEN_Z -> {
                ensureToolInstalled("7z")
                onProgress(0.3f, "Compressing to 7z...")
                val args = mutableListOf("a", output)
                args.addAll(sources)
                val result = toolManager.execute("7z", args)
                if (!result.isSuccess) {
                    throw IOException("7z compression failed: ${result.stderr}")
                }
            }
        }
        
        onProgress(1.0f, "Compression completed successfully!")
    }

    override suspend fun extract(
        archive: String,
        destination: String,
        onProgress: (progress: Float, currentFile: String) -> Unit
    ) {
        onProgress(0.1f, "Preparing extraction...")

        // Create destination directory if not exists
        val destFile = java.io.File(destination)
        if (!destFile.exists()) {
            destFile.mkdirs()
        }

        val ext = archive.lowercase()
        when {
            ext.endsWith(".zip") -> {
                ensureToolInstalled("unzip")
                onProgress(0.3f, "Extracting ZIP...")
                val result = toolManager.execute("unzip", listOf(archive, "-d", destination))
                if (!result.isSuccess) {
                    throw IOException("ZIP extraction failed: ${result.stderr}")
                }
            }
            ext.endsWith(".tar") -> {
                ensureToolInstalled("tar")
                onProgress(0.3f, "Extracting TAR...")
                val result = toolManager.execute("tar", listOf("-xf", archive, "-C", destination))
                if (!result.isSuccess) {
                    throw IOException("TAR extraction failed: ${result.stderr}")
                }
            }
            ext.endsWith(".tar.gz") || ext.endsWith(".tgz") -> {
                ensureToolInstalled("tar")
                ensureToolInstalled("gzip")
                onProgress(0.3f, "Extracting TAR.GZ...")
                val result = toolManager.execute("tar", listOf("-xzf", archive, "-C", destination))
                if (!result.isSuccess) {
                    throw IOException("TAR.GZ extraction failed: ${result.stderr}")
                }
            }
            ext.endsWith(".tar.xz") -> {
                ensureToolInstalled("tar")
                ensureToolInstalled("xz")
                onProgress(0.3f, "Extracting TAR.XZ...")
                val result = toolManager.execute("tar", listOf("-xJf", archive, "-C", destination))
                if (!result.isSuccess) {
                    throw IOException("TAR.XZ extraction failed: ${result.stderr}")
                }
            }
            ext.endsWith(".7z") -> {
                ensureToolInstalled("7z")
                onProgress(0.3f, "Extracting 7z...")
                // For 7z, output directory parameter is -o<dir> (no space)
                val result = toolManager.execute("7z", listOf("x", archive, "-o$destination"))
                if (!result.isSuccess) {
                    throw IOException("7z extraction failed: ${result.stderr}")
                }
            }
            else -> {
                // Default to 7z for unsupported formats since it is a universal extractor
                ensureToolInstalled("7z")
                onProgress(0.3f, "Extracting archive...")
                val result = toolManager.execute("7z", listOf("x", archive, "-o$destination"))
                if (!result.isSuccess) {
                    throw IOException("Extraction failed: ${result.stderr}")
                }
            }
        }
        
        onProgress(1.0f, "Extraction completed successfully!")
    }

    private suspend fun ensureToolInstalled(tool: String) {
        val packageName = when (tool) {
            "7z" -> "p7zip"
            else -> tool
        }
        if (!toolManager.exists(tool)) {
            toolManager.install(packageName)
        }
    }
}
