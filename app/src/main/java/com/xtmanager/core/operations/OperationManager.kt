package com.xtmanager.core.operations

import com.xtmanager.core.filesystem.FileSystem
import com.xtmanager.core.logger.AppLogger
import com.xtmanager.core.model.Operation
import com.xtmanager.core.model.OperationStatus
import com.xtmanager.core.model.OperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class OperationManager(
    private val fileSystem: FileSystem
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _operations = MutableStateFlow<List<Operation>>(emptyList())
    val operations: StateFlow<List<Operation>> = _operations.asStateFlow()

    fun enqueueCopy(sources: List<String>, destinationDir: String) {
        sources.forEach { source ->
            val id = UUID.randomUUID().toString()
            val destName = File(source).name
            val destPath = File(destinationDir, destName).absolutePath
            val operation = Operation(
                id = id,
                type = OperationType.COPY,
                source = source,
                destination = destPath,
                status = OperationStatus.PENDING
            )
            addOperation(operation)
            AppLogger.i("OPERATIONS", "📋 Enqueued COPY: ${File(source).name} ➡️ $destinationDir")

            scope.launch {
                updateStatus(id, OperationStatus.RUNNING)
                try {
                    fileSystem.copy(source, destPath) { processed, total, currentFile ->
                        updateProgress(id, processed, total, currentFile)
                    }
                    updateStatus(id, OperationStatus.COMPLETED)
                    AppLogger.i("OPERATIONS", "✅ COPY COMPLETED: ${File(source).name}")
                } catch (e: Exception) {
                    updateError(id, e.localizedMessage ?: "Unknown error")
                    AppLogger.e("OPERATIONS", "❌ COPY FAILED: ${File(source).name} - ${e.message}")
                }
            }
        }
    }

    fun enqueueMove(sources: List<String>, destinationDir: String) {
        sources.forEach { source ->
            val id = UUID.randomUUID().toString()
            val destName = File(source).name
            val destPath = File(destinationDir, destName).absolutePath
            val operation = Operation(
                id = id,
                type = OperationType.MOVE,
                source = source,
                destination = destPath,
                status = OperationStatus.PENDING
            )
            addOperation(operation)
            AppLogger.i("OPERATIONS", "🚚 Enqueued MOVE: ${File(source).name} ➡️ $destinationDir")

            scope.launch {
                updateStatus(id, OperationStatus.RUNNING)
                try {
                    fileSystem.move(source, destPath) { processed, total, currentFile ->
                        updateProgress(id, processed, total, currentFile)
                    }
                    updateStatus(id, OperationStatus.COMPLETED)
                    AppLogger.i("OPERATIONS", "✅ MOVE COMPLETED: ${File(source).name}")
                } catch (e: Exception) {
                    updateError(id, e.localizedMessage ?: "Unknown error")
                    AppLogger.e("OPERATIONS", "❌ MOVE FAILED: ${File(source).name} - ${e.message}")
                }
            }
        }
    }

    fun enqueueDelete(paths: List<String>) {
        paths.forEach { path ->
            val id = UUID.randomUUID().toString()
            val operation = Operation(
                id = id,
                type = OperationType.DELETE,
                source = path,
                destination = "",
                status = OperationStatus.PENDING
            )
            addOperation(operation)
            AppLogger.i("OPERATIONS", "🗑️ Enqueued DELETE: ${File(path).name}")

            scope.launch {
                updateStatus(id, OperationStatus.RUNNING)
                try {
                    fileSystem.delete(path) { processed, total, currentFile ->
                        updateProgress(id, processed, total, currentFile)
                    }
                    updateStatus(id, OperationStatus.COMPLETED)
                    AppLogger.i("OPERATIONS", "✅ DELETE COMPLETED: ${File(path).name}")
                } catch (e: Exception) {
                    updateError(id, e.localizedMessage ?: "Unknown error")
                    AppLogger.e("OPERATIONS", "❌ DELETE FAILED: ${File(path).name} - ${e.message}")
                }
            }
        }
    }

    private fun addOperation(operation: Operation) {
        _operations.value = _operations.value + operation
    }

    private fun updateStatus(id: String, status: OperationStatus) {
        _operations.value = _operations.value.map {
            if (it.id == id) it.copy(status = status) else it
        }
    }

    private fun updateProgress(id: String, processed: Long, total: Long, currentFile: String) {
        _operations.value = _operations.value.map {
            if (it.id == id) {
                val progress = if (total > 0) processed.toFloat() / total else 0f
                it.copy(
                    processedBytes = processed,
                    totalBytes = total,
                    progress = progress.coerceIn(0f, 1f),
                    currentFileName = currentFile
                )
            } else {
                it
            }
        }
    }

    private fun updateError(id: String, error: String) {
        _operations.value = _operations.value.map {
            if (it.id == id) it.copy(status = OperationStatus.FAILED, error = error) else it
        }
    }

    fun clearCompleted() {
        _operations.value = _operations.value.filter {
            it.status != OperationStatus.COMPLETED && it.status != OperationStatus.FAILED
        }
    }
}
