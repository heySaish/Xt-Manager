package com.xtmanager.runtime.tools

import com.xtmanager.runtime.proot.ProotExecutor
import java.io.IOException

class AlpineToolManager(private val executor: ProotExecutor) : ToolManager {
    
    private var isIndexUpdated = false

    override suspend fun exists(tool: String): Boolean {
        val result = executor.execute("which $tool")
        return result.isSuccess && result.stdout.trim().isNotEmpty()
    }

    override suspend fun install(tool: String) {
        if (!isIndexUpdated) {
            val updateResult = executor.execute("apk update")
            if (updateResult.isSuccess) {
                isIndexUpdated = true
            }
        }
        
        val result = executor.execute("apk add $tool")
        if (!result.isSuccess) {
            throw IOException("Failed to install tool: $tool. Stderr: ${result.stderr}")
        }
    }

    override suspend fun execute(tool: String, args: List<String>): ToolResult {
        val escapedArgs = args.joinToString(" ") { arg ->
            "'" + arg.replace("'", "'\\''") + "'"
        }
        val command = "$tool $escapedArgs"
        return executor.execute(command)
    }
}
