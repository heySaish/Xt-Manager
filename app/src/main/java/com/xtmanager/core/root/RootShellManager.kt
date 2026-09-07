package com.xtmanager.core.root

import android.util.Log
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
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
            coroutineScope {
                val cmdArray = arrayOf("su", "-M", "-c", command)
                var process = try {
                    Runtime.getRuntime().exec(cmdArray)
                } catch (_: Exception) {
                    Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                }

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errReader = BufferedReader(InputStreamReader(process.errorStream))

                val stdoutBuilder = StringBuilder()
                val stderrBuilder = StringBuilder()

                val jobStdout = launch(Dispatchers.IO) {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stdoutBuilder.append(line).append("\n")
                    }
                }

                val jobStderr = launch(Dispatchers.IO) {
                    var line: String?
                    while (errReader.readLine().also { line = it } != null) {
                        stderrBuilder.append(line).append("\n")
                    }
                }

                jobStdout.join()
                jobStderr.join()
                val exitCode = process.waitFor()

                if (exitCode != 0 && cmdArray[1] == "-M") {
                    val fallbackProc = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                    val fbReader = BufferedReader(InputStreamReader(fallbackProc.inputStream))
                    val fbErrReader = BufferedReader(InputStreamReader(fallbackProc.errorStream))
                    val fbStdout = StringBuilder()
                    val fbStderr = StringBuilder()

                    val j1 = launch(Dispatchers.IO) {
                        var l: String?
                        while (fbReader.readLine().also { l = it } != null) fbStdout.append(l).append("\n")
                    }
                    val j2 = launch(Dispatchers.IO) {
                        var l: String?
                        while (fbErrReader.readLine().also { l = it } != null) fbStderr.append(l).append("\n")
                    }
                    j1.join()
                    j2.join()
                    val fbExitCode = fallbackProc.waitFor()
                    return@coroutineScope CommandResult(fbExitCode, fbStdout.toString().trim(), fbStderr.toString().trim())
                }

                CommandResult(exitCode, stdoutBuilder.toString().trim(), stderrBuilder.toString().trim())
            }
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

            val permissions: String
            val name: String
            val size: Long

            if (trimmed.contains(" -> ")) {
                val arrowSplit = trimmed.split(" -> ", limit = 2)
                val leftParts = arrowSplit[0].trim().split(Regex("\\s+"))
                if (leftParts.size < 8) continue
                permissions = leftParts[0]
                size = leftParts[4].toLongOrNull() ?: 0L
                name = leftParts.subList(7, leftParts.size).joinToString(" ")
            } else {
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size < 8) continue
                permissions = parts[0]
                size = parts[4].toLongOrNull() ?: 0L
                name = parts.subList(7, parts.size).joinToString(" ")
            }

            val cleanName = name.trim()
            if (cleanName.isEmpty() || cleanName == "." || cleanName == "..") continue

            val isDir = permissions.startsWith("d") || permissions.startsWith("l")
            val fullPath = if (path.endsWith("/")) "$path$cleanName" else "$path/$cleanName"

            val archiveExtensions = listOf(
                ".zip", ".apk", ".7z", ".tar", ".gz", ".bz2", ".xz", ".zst", ".lz4",
                ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".tar.xz", ".txz", ".tar.zst", ".tzst", ".tar.lz4"
            )
            val isArchive = !isDir && archiveExtensions.any { cleanName.lowercase().endsWith(it) }

            val type = when {
                isDir -> FileType.DIRECTORY
                isArchive -> FileType.ARCHIVE
                else -> FileType.FILE
            }

            entries.add(
                FileEntry(
                    name = cleanName,
                    path = fullPath,
                    isDirectory = isDir,
                    size = if (isDir) 0L else size,
                    lastModified = System.currentTimeMillis(),
                    type = type
                )
            )
        }

        entries.distinctBy { it.path }.sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
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
