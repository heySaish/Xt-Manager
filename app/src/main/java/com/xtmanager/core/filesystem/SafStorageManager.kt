package com.xtmanager.core.filesystem

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.xtmanager.core.logger.AppLogger
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object SafStorageManager {

    private const val TAG = "SafStorageManager"

    fun isRemovableStoragePath(path: String): Boolean {
        val p = path.trim()
        if (!p.startsWith("/storage/")) return false
        if (p.startsWith("/storage/emulated") || p == "/storage" || p == "/storage/self") return false
        return true
    }

    fun getPersistedTreeUris(context: Context): List<Uri> {
        return try {
            context.contentResolver.persistedUriPermissions
                .filter { it.isWritePermission }
                .map { it.uri }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun findDocumentFile(
        context: Context,
        path: String,
        createIfMissing: Boolean = false,
        isDirectory: Boolean = false
    ): DocumentFile? {
        val file = File(path)
        val treeUris = getPersistedTreeUris(context)
        if (treeUris.isEmpty()) return null

        for (treeUri in treeUris) {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: continue
            val relativePath = getRelativePathForDoc(targetFile = file) ?: continue
            var currentDoc: DocumentFile? = rootDoc

            if (relativePath.isEmpty()) {
                return rootDoc
            }

            val segments = relativePath.split("/").filter { it.isNotEmpty() }
            for (i in segments.indices) {
                val segment = segments[i]
                val isLast = (i == segments.lastIndex)
                var child = currentDoc?.findFile(segment)

                if (child == null && createIfMissing && currentDoc != null && currentDoc.canWrite()) {
                    child = if (isLast && !isDirectory) {
                        currentDoc.createFile("application/octet-stream", segment)
                    } else {
                        currentDoc.createDirectory(segment)
                    }
                }
                currentDoc = child
                if (currentDoc == null) break
            }

            if (currentDoc != null && currentDoc.exists()) {
                return currentDoc
            }
        }
        return null
    }

    private fun getRelativePathForDoc(targetFile: File): String? {
        val targetAbs = targetFile.absolutePath
        val storageIndex = targetAbs.indexOf("/storage/")
        if (storageIndex == -1) return null

        val sub = targetAbs.substring(storageIndex + "/storage/".length)
        val parts = sub.split("/").filter { it.isNotEmpty() }
        if (parts.size <= 1) return ""
        return parts.drop(1).joinToString("/")
    }

    fun mkdir(context: Context, path: String): Boolean {
        return try {
            val doc = findDocumentFile(context, path, createIfMissing = true, isDirectory = true)
            doc != null && doc.isDirectory
        } catch (e: Exception) {
            AppLogger.e(TAG, "SAF mkdir failed for $path: ${e.message}")
            false
        }
    }

    fun createFile(context: Context, path: String): Boolean {
        return try {
            val doc = findDocumentFile(context, path, createIfMissing = true, isDirectory = false)
            doc != null && doc.isFile
        } catch (e: Exception) {
            AppLogger.e(TAG, "SAF createFile failed for $path: ${e.message}")
            false
        }
    }

    fun delete(context: Context, path: String): Boolean {
        return try {
            val doc = findDocumentFile(context, path, createIfMissing = false)
            doc?.delete() == true
        } catch (e: Exception) {
            AppLogger.e(TAG, "SAF delete failed for $path: ${e.message}")
            false
        }
    }

    fun rename(context: Context, sourcePath: String, newName: String): Boolean {
        return try {
            val doc = findDocumentFile(context, sourcePath, createIfMissing = false)
            doc?.renameTo(newName) == true
        } catch (e: Exception) {
            AppLogger.e(TAG, "SAF rename failed for $sourcePath: ${e.message}")
            false
        }
    }

    fun openOutputStream(context: Context, path: String): OutputStream? {
        return try {
            val doc = findDocumentFile(context, path, createIfMissing = true, isDirectory = false) ?: return null
            context.contentResolver.openOutputStream(doc.uri)
        } catch (e: Exception) {
            AppLogger.e(TAG, "SAF openOutputStream failed for $path: ${e.message}")
            null
        }
    }

    fun openInputStream(context: Context, path: String): InputStream? {
        return try {
            val doc = findDocumentFile(context, path, createIfMissing = false) ?: return null
            context.contentResolver.openInputStream(doc.uri)
        } catch (e: Exception) {
            AppLogger.e(TAG, "SAF openInputStream failed for $path: ${e.message}")
            null
        }
    }
}
