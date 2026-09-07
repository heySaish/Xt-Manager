package com.xtmanager.core.root

import android.util.Log
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

class RootShellManager {

    companion object {
        private const val TAG = "RootShellManager"
    }

    suspend fun checkRootAvailability(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = executeCommand("id")
            val isRoot = result.exitCode == 0 && result.stdout.contains("uid=0")
            Log.d(TAG, "Root check result: $isRoot (stdout: ${result.stdout})")
            isRoot
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed", e)
            false
        }
    }

    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))

            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            val stdoutBuilder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                stdoutBuilder.append(line).append("\n")
            }

            val stderrBuilder = StringBuilder()
            while (errReader.readLine().also { line = it } != null) {
                stderrBuilder.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            CommandResult(exitCode, stdoutBuilder.toString().trim(), stderrBuilder.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error executing root command: $command", e)
            CommandResult(-1, "", e.message ?: "Execution failed")
        }
    }

    suspend fun listDirectory(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        val result = executeCommand("ls -la \"$path\"")
        if (result.exitCode != 0 || result.stdout.isEmpty()) {
            return@withContext emptyList()
        }

        val entries = mutableListOf<FileEntry>()
        val lines = result.stdout.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("total")) continue

            val parts = trimmed.split(Regex("\\s+"))
            if (parts.size < 8) continue

            val permissions = parts[0]
            val isDir = permissions.startsWith("d") || permissions.startsWith("l")
            val name = parts.subList(8, parts.size).joinToString(" ")

            if (name == "." || name == "..") continue

            val size = parts[4].toLongOrNull() ?: 0L
            val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"

            val archiveExtensions = listOf(
                ".zip", ".apk", ".7z", ".tar", ".gz", ".bz2", ".xz", ".zst", ".lz4",
                ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".tar.xz", ".txz", ".tar.zst", ".tzst", ".tar.lz4"
            )
            val isArchive = !isDir && archiveExtensions.any { name.lowercase().endsWith(it) }

            val type = when {
                isDir -> FileType.DIRECTORY
                isArchive -> FileType.ARCHIVE
                else -> FileType.FILE
            }

            entries.add(
                FileEntry(
                    name = name,
                    path = fullPath,
                    isDirectory = isDir,
                    size = if (isDir) 0L else size,
                    lastModified = System.currentTimeMillis(),
                    type = type
                )
            )
        }

        entries.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun mkdir(path: String): Boolean = withContext(Dispatchers.IO) {
        val res = executeCommand("mkdir -p \"$path\"")
        res.exitCode == 0
    }

    suspend fun createFile(path: String): Boolean = withContext(Dispatchers.IO) {
        val res = executeCommand("touch \"$path\"")
        res.exitCode == 0
    }

    suspend fun delete(path: String): Boolean = withContext(Dispatchers.IO) {
        val res = executeCommand("rm -rf \"$path\"")
        res.exitCode == 0
    }

    suspend fun rename(oldPath: String, newPath: String): Boolean = withContext(Dispatchers.IO) {
        val res = executeCommand("mv \"$oldPath\" \"$newPath\"")
        res.exitCode == 0
    }
}
