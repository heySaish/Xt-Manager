package com.xtmanager.runtime.tools

interface ToolManager {
    suspend fun exists(tool: String): Boolean
    suspend fun install(tool: String)
    suspend fun execute(tool: String, args: List<String>): ToolResult
}
