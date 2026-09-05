package com.xtmanager.core.operations

import com.xtmanager.core.filesystem.FileSystem
import com.xtmanager.core.filesystem.LocalFileSystem
import com.xtmanager.core.logger.AppLogger
import com.xtmanager.core.model.Operation
import com.xtmanager.core.model.OperationStatus
import com.xtmanager.core.model.OperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class OperationManager(
    private val fileSystem: FileSystem
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _operations = MutableStateFlow<List<Operation>>(emptyList())
    val operations: StateFlow<List<Operation>> = _operations.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCancelTokens = ConcurrentHashMap<String, Long>()

    fun cancelOperation(id: String) {
        val tokenId = activeCancelTokens.remove(id)
        if (tokenId != null && tokenId != 0L) {
            LocalFileSystem.nativeTriggerCancel(tokenId)
            LocalFileSystem.nativeFreeCancelToken(tokenId)
        }

        activeJobs.remove(id)?.cancel()
        updateStatus(id, OperationStatus.CANCELLED)
        AppLogger.i("OPERATIONS", "🛑 Operation CANCELLED: $id")
    }

    fun enqueueExtract(
        archivePath: String,
        destinationDir: String,
        overwritePolicy: Int = 0
    ) {
        val id = UUID.randomUUID().toString()
        val tokenId = try {
            LocalFileSystem.nativeCreateCancelToken()
        } catch (_: Throwable) {
            0L
        }

        val operation = Operation(
            id = id,
            type = OperationType.EXTRACT,
            source = archivePath,
            destination = destinationDir,
            status = OperationStatus.PENDING,
            cancelToken = tokenId
        )
        addOperation(operation)
        if (tokenId != 0L) {
            activeCancelTokens[id] = tokenId
        }

        val job = scope.launch {
            updateStatus(id, OperationStatus.RUNNING)
            try {
                val resultCode = LocalFileSystem.nativeExtractArchive(
                    archivePath,
                    destinationDir,
                    overwritePolicy,
                    tokenId,
                    null
                )

                if (resultCode == 0) {
                    updateStatus(id, OperationStatus.COMPLETED)
                    AppLogger.i("OPERATIONS", "✅ EXTRACT COMPLETED: ${File(archivePath).name}")
                } else if (resultCode == -2) {
                    updateStatus(id, OperationStatus.CANCELLED)
                    AppLogger.i("OPERATIONS", "🛑 EXTRACT CANCELLED: ${File(archivePath).name}")
                } else {
                    updateError(id, "Extraction failed or security violation")
                    AppLogger.e("OPERATIONS", "❌ EXTRACT FAILED: ${File(archivePath).name}")
                }
            } catch (e: Exception) {
                updateError(id, e.localizedMessage ?: "Unknown error")
                AppLogger.e("OPERATIONS", "❌ EXTRACT ERROR: ${File(archivePath).name} - ${e.message}")
            } finally {
                if (tokenId != 0L) {
                    LocalFileSystem.nativeFreeCancelToken(tokenId)
                    activeCancelTokens.remove(id)
                }
                activeJobs.remove(id)
            }
        }
        activeJobs[id] = job
    }

    fun enqueueCompress(
        sources: List<String>,
        destinationArchive: String,
        format: String = "zip",
        compressionLevel: Int = 5
    ) {
        val id = UUID.randomUUID().toString()
        val tokenId = try {
            LocalFileSystem.nativeCreateCancelToken()
        } catch (_: Throwable) {
            0L
        }

        val operation = Operation(
            id = id,
            type = OperationType.COMPRESS,
            source = if (sources.size == 1) sources[0] else "${sources.size} items",
            destination = destinationArchive,
            status = OperationStatus.PENDING,
            cancelToken = tokenId
        )
        addOperation(operation)
        if (tokenId != 0L) {
            activeCancelTokens[id] = tokenId
        }

        val job = scope.launch {
            updateStatus(id, OperationStatus.RUNNING)
            try {
                val resultCode = LocalFileSystem.nativeCompressArchive(
                    sources.toTypedArray(),
                    destinationArchive,
                    format,
                    compressionLevel,
                    tokenId,
                    null
                )

                if (resultCode == 0) {
                    updateStatus(id, OperationStatus.COMPLETED)
                    AppLogger.i("OPERATIONS", "✅ COMPRESS COMPLETED: ${File(destinationArchive).name}")
                } else if (resultCode == -2) {
                    updateStatus(id, OperationStatus.CANCELLED)
                    AppLogger.i("OPERATIONS", "🛑 COMPRESS CANCELLED: ${File(destinationArchive).name}")
                } else {
                    updateError(id, "Compression failed")
                    AppLogger.e("OPERATIONS", "❌ COMPRESS FAILED: ${File(destinationArchive).name}")
                }
            } catch (e: Exception) {
                updateError(id, e.localizedMessage ?: "Unknown error")
                AppLogger.e("OPERATIONS", "❌ COMPRESS ERROR: ${File(destinationArchive).name} - ${e.message}")
            } finally {
                if (tokenId != 0L) {
                    LocalFileSystem.nativeFreeCancelToken(tokenId)
                    activeCancelTokens.remove(id)
                }
                activeJobs.remove(id)
            }
        }
        activeJobs[id] = job
    }

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

            val job = scope.launch {
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
                } finally {
                    activeJobs.remove(id)
                }
            }
            activeJobs[id] = job
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

            val job = scope.launch {
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
                } finally {
                    activeJobs.remove(id)
                }
            }
            activeJobs[id] = job
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

            val job = scope.launch {
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
                } finally {
                    activeJobs.remove(id)
                }
            }
            activeJobs[id] = job
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
            it.status != OperationStatus.COMPLETED && it.status != OperationStatus.FAILED && it.status != OperationStatus.CANCELLED
        }
    }
}
