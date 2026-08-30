package com.xtmanager.core.model

data class PaneState(
    val path: String = "/storage/emulated/0",
    val files: List<FileEntry> = emptyList(),
    val selected: Set<String> = emptySet(),
    val history: List<String> = listOf("/storage/emulated/0"),
    val historyIndex: Int = 0
) {
    val canGoBack: Boolean get() = historyIndex > 0
    val canGoForward: Boolean get() = historyIndex < history.size - 1
}
