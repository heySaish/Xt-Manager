package com.xtmanager.core.operations

import com.xtmanager.archive.ArchiveFormat
import com.xtmanager.archive.ArchiveManager
import com.xtmanager.core.filesystem.FileSystem
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
    private val fileSystem: FileSystem,
    private val archiveManager: ArchiveManager
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

            scope.launch {
                updateStatus(id, OperationStatus.RUNNING)
                try {
                    fileSystem.copy(source, destPath) { processed, total, currentFile ->
                        updateProgress(id, processed, total, currentFile)
                    }
                    updateStatus(id, OperationStatus.COMPLETED)
                } catch (e: Exception) {
                    updateError(id, e.localizedMessage ?: "Unknown error")
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

            scope.launch {
                updateStatus(id, OperationStatus.RUNNING)
                try {
                    fileSystem.move(source, destPath) { processed, total, currentFile ->
                        updateProgress(id, processed, total, currentFile)
                    }
                    updateStatus(id, OperationStatus.COMPLETED)
                } catch (e: Exception) {
                    updateError(id, e.localizedMessage ?: "Unknown error")
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

            scope.launch {
                updateStatus(id, OperationStatus.RUNNING)
                try {
                    fileSystem.delete(path) { processed, total, currentFile ->
                        updateProgress(id, processed, total, currentFile)
                    }
                    updateStatus(id, OperationStatus.COMPLETED)
                } catch (e: Exception) {
                    updateError(id, e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    fun enqueueCompress(sources: List<String>, outputArchive: String, format: ArchiveFormat) {
        val id = UUID.randomUUID().toString()
        val operation = Operation(
            id = id,
            type = OperationType.COMPRESS,
            source = sources.joinToString(", "),
            destination = outputArchive,
            status = OperationStatus.PENDING
        )
        addOperation(operation)

        scope.launch {
            updateStatus(id, OperationStatus.RUNNING)
            try {
                archiveManager.compress(sources, outputArchive, format) { progress, currentFile ->
                    val processed = (progress * 100).toLong()
                    updateProgress(id, processed, 100, currentFile)
                }
                updateStatus(id, OperationStatus.COMPLETED)
            } catch (e: Exception) {
                updateError(id, e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun enqueueExtract(archivePath: String, destinationDir: String) {
        val id = UUID.randomUUID().toString()
        val operation = Operation(
            id = id,
            type = OperationType.EXTRACT,
            source = archivePath,
            destination = destinationDir,
            status = OperationStatus.PENDING
        )
        addOperation(operation)

        scope.launch {
            updateStatus(id, OperationStatus.RUNNING)
            try {
                archiveManager.extract(archivePath, destinationDir) { progress, currentFile ->
                    val processed = (progress * 100).toLong()
                    updateProgress(id, processed, 100, currentFile)
                }
                updateStatus(id, OperationStatus.COMPLETED)
            } catch (e: Exception) {
                updateError(id, e.localizedMessage ?: "Unknown error")
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
