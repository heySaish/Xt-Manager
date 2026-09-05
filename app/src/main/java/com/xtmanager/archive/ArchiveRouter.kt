package com.xtmanager.archive

import com.xtmanager.runtime.AlpineManager

class ArchiveRouter(private val alpineManager: AlpineManager) {

    fun getRequiredBinaryForFormat(format: ArchiveFormat, isExtract: Boolean = false): String {
        return when (format) {
            ArchiveFormat.ZIP -> if (isExtract) {
                if (alpineManager.hasAlpineBinary("7z")) "7z" else if (alpineManager.hasAlpineBinary("7za")) "7za" else "unzip"
            } else {
                if (alpineManager.hasAlpineBinary("7z")) "7z" else if (alpineManager.hasAlpineBinary("7za")) "7za" else "zip"
            }
            ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_XZ -> "tar"
            ArchiveFormat.SEVEN_Z -> if (alpineManager.hasAlpineBinary("7z")) "7z" else "7za"
        }
    }

    fun getRequiredBinaryForFile(archivePath: String): String {
        val lower = archivePath.lowercase()
        return when {
            lower.endsWith(".7z") || lower.endsWith(".rar") -> if (alpineManager.hasAlpineBinary("7z")) "7z" else "7za"
            lower.endsWith(".tar") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz") || lower.endsWith(".tar.xz") -> "tar"
            lower.endsWith(".zip") || lower.endsWith(".apk") || lower.endsWith(".jar") -> {
                if (alpineManager.hasAlpineBinary("7z")) "7z" else if (alpineManager.hasAlpineBinary("7za")) "7za" else "unzip"
            }
            else -> "7z"
        }
    }

    fun resolveEngine(format: ArchiveFormat, isExtract: Boolean = false): ArchiveEngine {
        val binary = getRequiredBinaryForFormat(format, isExtract)
        return if (alpineManager.hasAlpineBinary(binary) || alpineManager.hasAlpineBinary("7z") || alpineManager.hasAlpineBinary("7za")) {
            ArchiveEngine.ALPINE_PROOT
        } else {
            ArchiveEngine.JAVA_FALLBACK
        }
    }

    fun resolveEngineForFile(archivePath: String): ArchiveEngine {
        val binary = getRequiredBinaryForFile(archivePath)
        return if (alpineManager.hasAlpineBinary(binary) || alpineManager.hasAlpineBinary("7z") || alpineManager.hasAlpineBinary("7za")) {
            ArchiveEngine.ALPINE_PROOT
        } else {
            ArchiveEngine.JAVA_FALLBACK
        }
    }
}
