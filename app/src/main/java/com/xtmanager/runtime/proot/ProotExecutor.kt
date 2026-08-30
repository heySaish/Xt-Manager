package com.xtmanager.runtime.proot

import com.xtmanager.runtime.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class ProotExecutor(private val prootManager: ProotManager) {

    suspend fun execute(command: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            val cmd = prootManager.getProotCommand(command)
            val processBuilder = ProcessBuilder(cmd)
            
            // Set environment variables if needed
            val env = processBuilder.environment()
            env["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            env["HOME"] = "/root"
            env["TMPDIR"] = "/tmp"

            val process = processBuilder.start()

            // Read output and error streams concurrently
            val stdoutThread = StreamGobbler(process.inputStream)
            val stderrThread = StreamGobbler(process.errorStream)
            
            stdoutThread.start()
            stderrThread.start()

            val exitCode = process.waitFor()
            stdoutThread.join()
            stderrThread.join()

            ToolResult(
                exitCode = exitCode,
                stdout = stdoutThread.result,
                stderr = stderrThread.result
            )
        } catch (e: Exception) {
            ToolResult(
                exitCode = -1,
                stdout = "",
                stderr = e.localizedMessage ?: "Process execution failed"
            )
        }
    }

    private class StreamGobbler(private val inputStream: InputStream) : Thread() {
        var result: String = ""
            private set

        override fun run() {
            try {
                result = inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                result = ""
            }
        }
    }
}
