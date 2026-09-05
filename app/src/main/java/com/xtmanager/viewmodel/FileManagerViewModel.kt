package com.xtmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xtmanager.core.filesystem.FileSystem
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.PaneState
import com.xtmanager.core.model.PaneType
import com.xtmanager.core.operations.OperationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

import com.xtmanager.core.filesystem.FileSystemCache
import kotlinx.coroutines.delay

class FileManagerViewModel(
    private val fileSystem: FileSystem,
    private val operationManager: OperationManager
) : ViewModel() {

    private val _leftPaneState = MutableStateFlow(PaneState(path = "/storage/emulated/0"))
    val leftPaneState: StateFlow<PaneState> = _leftPaneState.asStateFlow()

    private val _rightPaneState = MutableStateFlow(PaneState(path = "/storage/emulated/0"))
    val rightPaneState: StateFlow<PaneState> = _rightPaneState.asStateFlow()

    private val _activePane = MutableStateFlow(PaneType.LEFT)
    val activePane: StateFlow<PaneType> = _activePane.asStateFlow()

    val operations = operationManager.operations

    private val _showHiddenFiles = MutableStateFlow(false)
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    private val _densityScale = MutableStateFlow(1.0f)
    val densityScale: StateFlow<Float> = _densityScale.asStateFlow()

    init {
        // Initial load is deferred to MainActivity's onResume when permissions are active
    }

    fun setActivePane(paneType: PaneType) {
        _activePane.value = paneType
    }

    fun setDensityScale(scale: Float) {
        _densityScale.value = scale.coerceIn(0.7f, 1.4f)
    }

    fun toggleShowHiddenFiles() {
        _showHiddenFiles.value = !_showHiddenFiles.value
        refreshPane(PaneType.LEFT)
        refreshPane(PaneType.RIGHT)
    }

    private suspend fun loadAndEmitChunked(
        paneType: PaneType,
        path: String,
        stateBuilder: (List<FileEntry>) -> PaneState
    ) {
        val allFiles = fileSystem.list(path)
        val filteredFiles = if (_showHiddenFiles.value) {
            allFiles
        } else {
            allFiles.filter { !it.name.startsWith(".") }
        }

        if (filteredFiles.size <= 500) {
            updatePane(paneType, stateBuilder(filteredFiles))
        } else {
            // First chunk of 500 items for instant UI frame rendering (<16ms)
            val firstChunk = filteredFiles.take(500)
            updatePane(paneType, stateBuilder(firstChunk))
            
            delay(10)
            updatePane(paneType, stateBuilder(filteredFiles))
        }
    }

    fun refreshPane(paneType: PaneType) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        FileSystemCache.invalidate(state.path)
        viewModelScope.launch {
            try {
                loadAndEmitChunked(paneType, state.path) { files ->
                    state.copy(files = files, selected = emptySet())
                }
            } catch (e: Exception) {
                updatePane(paneType, state.copy(files = emptyList(), selected = emptySet()))
            }
        }
    }

    fun navigateTo(paneType: PaneType, newPath: String) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        val normalizedPath = File(newPath).absoluteFile.normalize().path
        
        val newHistory = state.history.subList(0, state.historyIndex + 1) + normalizedPath
        val newIndex = newHistory.size - 1

        viewModelScope.launch {
            try {
                loadAndEmitChunked(paneType, normalizedPath) { files ->
                    PaneState(
                        path = normalizedPath,
                        files = files,
                        selected = emptySet(),
                        history = newHistory,
                        historyIndex = newIndex
                    )
                }
            } catch (e: Exception) {
                // Handle navigation error
            }
        }
    }

    fun goBack(paneType: PaneType) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        if (state.canGoBack) {
            val newIndex = state.historyIndex - 1
            val newPath = state.history[newIndex]
            viewModelScope.launch {
                try {
                    loadAndEmitChunked(paneType, newPath) { files ->
                        state.copy(
                            path = newPath,
                            files = files,
                            selected = emptySet(),
                            historyIndex = newIndex
                        )
                    }
                } catch (e: Exception) {}
            }
        }
    }

    fun goForward(paneType: PaneType) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        if (state.canGoForward) {
            val newIndex = state.historyIndex + 1
            val newPath = state.history[newIndex]
            viewModelScope.launch {
                try {
                    loadAndEmitChunked(paneType, newPath) { files ->
                        state.copy(
                            path = newPath,
                            files = files,
                            selected = emptySet(),
                            historyIndex = newIndex
                        )
                    }
                } catch (e: Exception) {}
            }
        }
    }

    fun toggleFileSelection(paneType: PaneType, filePath: String) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        val isCurrentlySelected = state.selected.contains(filePath)
        val newSelection = if (isCurrentlySelected) {
            state.selected - filePath
        } else {
            state.selected + filePath
        }
        val fileIndex = state.files.indexOfFirst { it.path == filePath }
        val newAnchor = if (newSelection.isEmpty()) {
            null
        } else {
            state.anchorIndex ?: (if (fileIndex >= 0) fileIndex else null)
        }
        updatePane(paneType, state.copy(selected = newSelection, anchorIndex = newAnchor))
    }

    fun handleSwipe(paneType: PaneType, index: Int) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        if (index !in state.files.indices) return

        val targetPath = state.files[index].path

        if (!state.isSelectionMode || state.anchorIndex == null) {
            // First Swipe: Activate Selection Mode, select swiped item, set Anchor Index
            updatePane(
                paneType,
                state.copy(
                    selected = setOf(targetPath),
                    anchorIndex = index
                )
            )
        } else {
            // Second / Range Swipe: Select range between Anchor Index and current swiped index
            val anchor = state.anchorIndex
            val start = minOf(anchor, index)
            val end = maxOf(anchor, index)
            val rangePaths = state.files.subList(start, end + 1).map { it.path }.toSet()
            
            updatePane(
                paneType,
                state.copy(
                    selected = state.selected + rangePaths,
                    anchorIndex = index
                )
            )
        }
    }

    fun selectAll(paneType: PaneType) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        val allPaths = state.files.map { it.path }.toSet()
        updatePane(paneType, state.copy(selected = allPaths, anchorIndex = 0))
    }

    fun clearSelection(paneType: PaneType) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        updatePane(paneType, state.copy(selected = emptySet(), anchorIndex = null))
    }

    fun createDirectory(paneType: PaneType, name: String) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        val newDir = File(state.path, name).absolutePath
        viewModelScope.launch {
            try {
                fileSystem.mkdir(newDir)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                refreshPane(PaneType.LEFT)
                refreshPane(PaneType.RIGHT)
            }
        }
    }

    fun createFile(paneType: PaneType, name: String) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        val newFile = File(state.path, name).absolutePath
        viewModelScope.launch {
            try {
                fileSystem.createFile(newFile)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                refreshPane(PaneType.LEFT)
                refreshPane(PaneType.RIGHT)
            }
        }
    }

    fun renameFile(paneType: PaneType, oldPath: String, newName: String) {
        val parent = File(oldPath).parent ?: return
        val newPath = File(parent, newName).absolutePath
        viewModelScope.launch {
            try {
                fileSystem.rename(oldPath, newPath)
                refreshPane(PaneType.LEFT)
                refreshPane(PaneType.RIGHT)
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    fun deletePaths(paths: List<String>) {
        if (paths.isEmpty()) return
        operationManager.enqueueDelete(paths)
        viewModelScope.launch {
            refreshPane(PaneType.LEFT)
            refreshPane(PaneType.RIGHT)
        }
    }

    fun deleteSelected(paneType: PaneType) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        if (state.selected.isEmpty()) return
        
        operationManager.enqueueDelete(state.selected.toList())
        updatePane(paneType, state.copy(selected = emptySet()))
        
        // Refresh after some delay, or observe operation completion
        viewModelScope.launch {
            refreshPane(PaneType.LEFT)
            refreshPane(PaneType.RIGHT)
        }
    }

    fun copyPaths(paths: List<String>, destDir: String) {
        if (paths.isEmpty()) return
        operationManager.enqueueCopy(paths, destDir)
        viewModelScope.launch {
            refreshPane(PaneType.LEFT)
            refreshPane(PaneType.RIGHT)
        }
    }

    fun movePaths(paths: List<String>, destDir: String) {
        if (paths.isEmpty()) return
        operationManager.enqueueMove(paths, destDir)
        viewModelScope.launch {
            refreshPane(PaneType.LEFT)
            refreshPane(PaneType.RIGHT)
        }
    }

    fun copySelected(fromPane: PaneType) {
        val srcState = if (fromPane == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        val destState = if (fromPane == PaneType.LEFT) _rightPaneState.value else _leftPaneState.value
        
        if (srcState.selected.isEmpty()) return
        
        operationManager.enqueueCopy(srcState.selected.toList(), destState.path)
        updatePane(fromPane, srcState.copy(selected = emptySet()))
        
        viewModelScope.launch {
            refreshPane(PaneType.LEFT)
            refreshPane(PaneType.RIGHT)
        }
    }

    fun moveSelected(fromPane: PaneType) {
        val srcState = if (fromPane == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        val destState = if (fromPane == PaneType.LEFT) _rightPaneState.value else _leftPaneState.value
        
        if (srcState.selected.isEmpty()) return
        
        operationManager.enqueueMove(srcState.selected.toList(), destState.path)
        updatePane(fromPane, srcState.copy(selected = emptySet()))
        
        viewModelScope.launch {
            refreshPane(PaneType.LEFT)
            refreshPane(PaneType.RIGHT)
        }
    }

    fun enqueueExtract(archivePath: String, destDir: String, overwritePolicy: Int = 0) {
        operationManager.enqueueExtract(archivePath, destDir, overwritePolicy)
        viewModelScope.launch {
            refreshPane(PaneType.LEFT)
            refreshPane(PaneType.RIGHT)
        }
    }

    fun enqueueCompress(sources: List<String>, destArchive: String, format: String = "zip", level: Int = 5) {
        if (sources.isEmpty()) return
        operationManager.enqueueCompress(sources, destArchive, format, level)
        viewModelScope.launch {
            refreshPane(PaneType.LEFT)
            refreshPane(PaneType.RIGHT)
        }
    }

    fun cancelOperation(id: String) {
        operationManager.cancelOperation(id)
    }

    fun clearCompletedOperations() {
        operationManager.clearCompleted()
    }

    private fun updatePane(paneType: PaneType, state: PaneState) {
        if (paneType == PaneType.LEFT) {
            _leftPaneState.value = state
        } else {
            _rightPaneState.value = state
        }
    }
}
