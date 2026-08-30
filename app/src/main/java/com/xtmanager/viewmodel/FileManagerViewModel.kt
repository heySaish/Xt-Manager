package com.xtmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xtmanager.archive.ArchiveFormat
import com.xtmanager.core.filesystem.FileSystem
import com.xtmanager.core.model.FileEntry
import com.xtmanager.core.model.PaneState
import com.xtmanager.core.model.PaneType
import com.xtmanager.core.operations.OperationManager
import com.xtmanager.runtime.proot.AlpineManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class FileManagerViewModel(
    private val fileSystem: FileSystem,
    private val operationManager: OperationManager,
    private val alpineManager: AlpineManager
) : ViewModel() {

    private val _leftPaneState = MutableStateFlow(PaneState(path = "/storage/emulated/0"))
    val leftPaneState: StateFlow<PaneState> = _leftPaneState.asStateFlow()

    private val _rightPaneState = MutableStateFlow(PaneState(path = "/storage/emulated/0"))
    val rightPaneState: StateFlow<PaneState> = _rightPaneState.asStateFlow()

    private val _activePane = MutableStateFlow(PaneType.LEFT)
    val activePane: StateFlow<PaneType> = _activePane.asStateFlow()

    val operations = operationManager.operations

    private val _alpineInstallStatus = MutableStateFlow("Idle")
    val alpineInstallStatus: StateFlow<String> = _alpineInstallStatus.asStateFlow()

    private val _alpineInstallProgress = MutableStateFlow(if (alpineManager.isInstalled) 1.0f else 0.0f)
    val alpineInstallProgress: StateFlow<Float> = _alpineInstallProgress.asStateFlow()

    private val _showHiddenFiles = MutableStateFlow(false)
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    init {
        // Initial load
        refreshPane(PaneType.LEFT)
        refreshPane(PaneType.RIGHT)
    }

    fun setActivePane(paneType: PaneType) {
        _activePane.value = paneType
    }

    fun toggleShowHiddenFiles() {
        _showHiddenFiles.value = !_showHiddenFiles.value
        refreshPane(PaneType.LEFT)
        refreshPane(PaneType.RIGHT)
    }

    fun refreshPane(paneType: PaneType) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        viewModelScope.launch {
            try {
                val allFiles = fileSystem.list(state.path)
                val filteredFiles = if (_showHiddenFiles.value) {
                    allFiles
                } else {
                    allFiles.filter { !it.name.startsWith(".") }
                }
                
                updatePane(paneType, state.copy(files = filteredFiles, selected = emptySet()))
            } catch (e: Exception) {
                // Keep empty files if list fails
                updatePane(paneType, state.copy(files = emptyList(), selected = emptySet()))
            }
        }
    }

    fun navigateTo(paneType: PaneType, newPath: String) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        val normalizedPath = File(newPath).canonicalPath
        
        val newHistory = state.history.subList(0, state.historyIndex + 1) + normalizedPath
        val newIndex = newHistory.size - 1

        viewModelScope.launch {
            try {
                val allFiles = fileSystem.list(normalizedPath)
                val filteredFiles = if (_showHiddenFiles.value) {
                    allFiles
                } else {
                    allFiles.filter { !it.name.startsWith(".") }
                }
                
                updatePane(
                    paneType,
                    PaneState(
                        path = normalizedPath,
                        files = filteredFiles,
                        selected = emptySet(),
                        history = newHistory,
                        historyIndex = newIndex
                    )
                )
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
                val allFiles = fileSystem.list(newPath)
                val filteredFiles = if (_showHiddenFiles.value) {
                    allFiles
                } else {
                    allFiles.filter { !it.name.startsWith(".") }
                }
                updatePane(
                    paneType,
                    state.copy(
                        path = newPath,
                        files = filteredFiles,
                        selected = emptySet(),
                        historyIndex = newIndex
                    )
                )
            }
        }
    }

    fun goForward(paneType: PaneType) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        if (state.canGoForward) {
            val newIndex = state.historyIndex + 1
            val newPath = state.history[newIndex]
            viewModelScope.launch {
                val allFiles = fileSystem.list(newPath)
                val filteredFiles = if (_showHiddenFiles.value) {
                    allFiles
                } else {
                    allFiles.filter { !it.name.startsWith(".") }
                }
                updatePane(
                    paneType,
                    state.copy(
                        path = newPath,
                        files = filteredFiles,
                        selected = emptySet(),
                        historyIndex = newIndex
                    )
                )
            }
        }
    }

    fun toggleFileSelection(paneType: PaneType, filePath: String) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        val newSelection = if (state.selected.contains(filePath)) {
            state.selected - filePath
        } else {
            state.selected + filePath
        }
        updatePane(paneType, state.copy(selected = newSelection))
    }

    fun selectAll(paneType: PaneType) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        val allPaths = state.files.map { it.path }.toSet()
        updatePane(paneType, state.copy(selected = allPaths))
    }

    fun clearSelection(paneType: PaneType) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        updatePane(paneType, state.copy(selected = emptySet()))
    }

    fun createDirectory(paneType: PaneType, name: String) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        val newDir = File(state.path, name).absolutePath
        viewModelScope.launch {
            try {
                fileSystem.mkdir(newDir)
                refreshPane(PaneType.LEFT)
                refreshPane(PaneType.RIGHT)
            } catch (e: Exception) {
                // handle error
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

    fun compressSelected(paneType: PaneType, archiveName: String, format: ArchiveFormat) {
        val state = if (paneType == PaneType.LEFT) _leftPaneState.value else _rightPaneState.value
        if (state.selected.isEmpty()) return

        val outputArchive = File(state.path, archiveName).absolutePath
        operationManager.enqueueCompress(state.selected.toList(), outputArchive, format)
        updatePane(paneType, state.copy(selected = emptySet()))

        viewModelScope.launch {
            refreshPane(PaneType.LEFT)
            refreshPane(PaneType.RIGHT)
        }
    }

    fun extractSelected(paneType: PaneType, archivePath: String, destDir: String) {
        operationManager.enqueueExtract(archivePath, destDir)
        viewModelScope.launch {
            refreshPane(PaneType.LEFT)
            refreshPane(PaneType.RIGHT)
        }
    }

    fun installAlpine() {
        viewModelScope.launch {
            _alpineInstallStatus.value = "Starting Installation..."
            _alpineInstallProgress.value = 0.01f
            val success = alpineManager.install { status, progress ->
                _alpineInstallStatus.value = status
                _alpineInstallProgress.value = progress
            }
            if (success) {
                _alpineInstallStatus.value = "Installed"
                _alpineInstallProgress.value = 1.0f
            } else {
                _alpineInstallStatus.value = "Installation Failed"
                _alpineInstallProgress.value = -1.0f
            }
        }
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
