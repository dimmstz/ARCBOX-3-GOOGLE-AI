package com.example.data.cloud

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class TransferType {
    DOWNLOAD,
    UPLOAD
}

enum class TransferStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TransferItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TransferType,
    val providerId: String,
    val providerName: String,
    val remotePath: String,
    val localFilePath: String,
    val totalBytes: Long = 0L,
    val transferredBytes: Long = 0L,
    val progress: Float = 0f,
    val speedBytesPerSec: Long = 0L,
    val status: TransferStatus = TransferStatus.QUEUED,
    val errorMessage: String? = null,
    val startedAt: Long = System.currentTimeMillis()
)

class CloudTransferManager(
    private val context: Context,
    private val cloudStorageService: CloudStorageService
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val activeJobs = mutableMapOf<String, Job>()

    private val _transfers = MutableStateFlow<List<TransferItem>>(emptyList())
    val transfers: StateFlow<List<TransferItem>> = _transfers.asStateFlow()

    fun enqueueDownload(
        providerId: String,
        remotePath: String,
        destinationFile: File,
        fileSize: Long = 0L
    ): String {
        val provider = cloudStorageService.getProvider(providerId)
        val providerName = provider?.displayName ?: providerId.uppercase()
        val item = TransferItem(
            name = destinationFile.name,
            type = TransferType.DOWNLOAD,
            providerId = providerId,
            providerName = providerName,
            remotePath = remotePath,
            localFilePath = destinationFile.absolutePath,
            totalBytes = fileSize,
            status = TransferStatus.QUEUED
        )

        _transfers.update { listOf(item) + it }
        startTransfer(item.id)
        return item.id
    }

    fun enqueueUpload(
        providerId: String,
        localFile: File,
        remoteParentPath: String
    ): String {
        val provider = cloudStorageService.getProvider(providerId)
        val providerName = provider?.displayName ?: providerId.uppercase()
        val item = TransferItem(
            name = localFile.name,
            type = TransferType.UPLOAD,
            providerId = providerId,
            providerName = providerName,
            remotePath = remoteParentPath,
            localFilePath = localFile.absolutePath,
            totalBytes = localFile.length(),
            status = TransferStatus.QUEUED
        )

        _transfers.update { listOf(item) + it }
        startTransfer(item.id)
        return item.id
    }

    private fun startTransfer(transferId: String) {
        val job = scope.launch {
            val item = _transfers.value.find { it.id == transferId } ?: return@launch
            updateTransferStatus(transferId, TransferStatus.RUNNING)

            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L

            try {
                if (item.type == TransferType.DOWNLOAD) {
                    val dest = File(item.localFilePath)
                    dest.parentFile?.mkdirs()
                    val success = cloudStorageService.downloadRemoteFile(
                        providerId = item.providerId,
                        remoteRelativePath = item.remotePath,
                        destinationFile = dest,
                        onProgress = { ratio ->
                            val currentBytes = (ratio * item.totalBytes).toLong()
                            val now = System.currentTimeMillis()
                            val dt = (now - lastTime).coerceAtLeast(1L)
                            val speed = if (dt > 300) {
                                val spd = ((currentBytes - lastBytes) * 1000L) / dt
                                lastTime = now
                                lastBytes = currentBytes
                                spd.coerceAtLeast(0L)
                            } else {
                                item.speedBytesPerSec
                            }

                            updateTransferProgress(transferId, ratio, currentBytes, speed)
                        }
                    )

                    if (success) {
                        updateTransferStatus(transferId, TransferStatus.COMPLETED)
                    } else {
                        updateTransferStatus(transferId, TransferStatus.FAILED, "Falha na transferência do arquivo.")
                    }
                } else {
                    val local = File(item.localFilePath)
                    val success = cloudStorageService.uploadRemoteFile(
                        providerId = item.providerId,
                        localFile = local,
                        remoteRelativePath = item.remotePath,
                        onProgress = { ratio ->
                            val currentBytes = (ratio * item.totalBytes).toLong()
                            val now = System.currentTimeMillis()
                            val dt = (now - lastTime).coerceAtLeast(1L)
                            val speed = if (dt > 300) {
                                val spd = ((currentBytes - lastBytes) * 1000L) / dt
                                lastTime = now
                                lastBytes = currentBytes
                                spd.coerceAtLeast(0L)
                            } else {
                                item.speedBytesPerSec
                            }

                            updateTransferProgress(transferId, ratio, currentBytes, speed)
                        }
                    )

                    if (success) {
                        updateTransferStatus(transferId, TransferStatus.COMPLETED)
                    } else {
                        updateTransferStatus(transferId, TransferStatus.FAILED, "Falha no envio para o servidor remoto.")
                    }
                }
            } catch (e: Exception) {
                updateTransferStatus(transferId, TransferStatus.FAILED, e.message ?: "Erro desconhecido")
            } finally {
                activeJobs.remove(transferId)
            }
        }
        activeJobs[transferId] = job
    }

    fun cancelTransfer(transferId: String) {
        activeJobs[transferId]?.cancel()
        activeJobs.remove(transferId)
        updateTransferStatus(transferId, TransferStatus.CANCELLED)
    }

    fun retryTransfer(transferId: String) {
        val item = _transfers.value.find { it.id == transferId } ?: return
        if (item.status == TransferStatus.FAILED || item.status == TransferStatus.CANCELLED) {
            updateTransferStatus(transferId, TransferStatus.QUEUED, null)
            startTransfer(transferId)
        }
    }

    fun clearCompleted() {
        _transfers.update { list ->
            list.filter { it.status == TransferStatus.RUNNING || it.status == TransferStatus.QUEUED }
        }
    }

    private fun updateTransferStatus(id: String, status: TransferStatus, error: String? = null) {
        _transfers.update { list ->
            list.map {
                if (it.id == id) it.copy(status = status, errorMessage = error) else it
            }
        }
    }

    private fun updateTransferProgress(id: String, progress: Float, bytes: Long, speed: Long) {
        _transfers.update { list ->
            list.map {
                if (it.id == id) it.copy(progress = progress, transferredBytes = bytes, speedBytesPerSec = speed) else it
            }
        }
    }
}
