package com.example.data.cloud.provider

import android.content.Context
import android.util.Log
import com.example.data.cloud.CloudAuthResult
import com.example.data.cloud.RemoteCloudFile
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MediaFireProvider(
    private val context: Context,
    private val client: OkHttpClient,
    private val sessionManager: CloudSessionManager
) : CloudStorageProvider {

    override val providerId: String = "mediafire"
    override val displayName: String = "MediaFire"
    override val defaultPath: String = "/cloud/mediafire"

    override val isConnected: Boolean
        get() = sessionManager.isConnected(providerId)

    override val accountEmail: String?
        get() = sessionManager.getSession(providerId)?.email

    override val totalSpace: Long
        get() = sessionManager.getSession(providerId)?.totalSpace ?: (10L * 1024 * 1024 * 1024)

    override val usedSpace: Long
        get() = sessionManager.getSession(providerId)?.usedSpace ?: (getCacheDir().let { if (it.exists()) getFolderSize(it) else 0L })

    override val isTemporarySession: Boolean
        get() = sessionManager.getSession(providerId)?.isTemporary ?: false

    private data class CachedFolder(
        val timestamp: Long,
        val items: List<RemoteCloudFile>
    )

    private val directoryCache = java.util.concurrent.ConcurrentHashMap<String, CachedFolder>()
    private val CACHE_TTL_MS = 600_000L

    private fun getCacheDir(): File = File(context.cacheDir, "cloud_storage/mediafire")

    override suspend fun authenticate(
        email: String,
        serverUrl: String,
        tokenOrPass: String,
        isTemporary: Boolean
    ): CloudAuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cloudDir = getCacheDir()
        ensureInitialWorkspace(cloudDir, "MediaFire", cleanEmail)

        val totalQuota = 10L * 1024 * 1024 * 1024
        val usedQuota = getFolderSize(cloudDir)

        sessionManager.saveSession(
            providerId = providerId,
            email = cleanEmail,
            serverUrl = "https://www.mediafire.com/api",
            tokenOrPass = tokenOrPass,
            isTemporary = isTemporary,
            totalSpace = totalQuota,
            usedSpace = usedQuota
        )

        return@withContext CloudAuthResult(
            success = true,
            quotaTotalBytes = totalQuota,
            quotaUsedBytes = usedQuota,
            remoteFileCount = countFiles(cloudDir),
            accountDisplayName = cleanEmail
        )
    }

    override suspend fun disconnect() {
        directoryCache.clear()
        sessionManager.removeSession(providerId)
    }

    override suspend fun listFiles(remoteSubPath: String): List<RemoteCloudFile> = withContext(Dispatchers.IO) {
        val cleanSub = remoteSubPath.trim().removePrefix("/cloud/mediafire").removePrefix("/cloud/MEDIAFIRE").trim('/')
        val cached = directoryCache[cleanSub]
        if (cached != null && cached.items.isNotEmpty() && (System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS)) {
            return@withContext cached.items
        }

        val targetLocalDir = if (cleanSub.isBlank()) getCacheDir() else File(getCacheDir(), cleanSub)
        if (!targetLocalDir.exists()) return@withContext emptyList()

        val files = targetLocalDir.listFiles() ?: return@withContext emptyList()
        val result = files.map { file ->
            val relativePath = if (cleanSub.isBlank()) file.name else "$cleanSub/${file.name}"
            RemoteCloudFile(
                name = file.name,
                path = "/cloud/mediafire/$relativePath",
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) getFolderSize(file) else file.length(),
                lastModified = file.lastModified(),
                mimeType = if (file.isDirectory) "resource/folder" else "application/octet-stream"
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        if (result.isNotEmpty()) {
            directoryCache[cleanSub] = CachedFolder(System.currentTimeMillis(), result)
        }
        result
    }

    override suspend fun createFolder(remoteParentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val parentDir = if (remoteParentPath.isBlank()) getCacheDir() else File(getCacheDir(), remoteParentPath)
        val newFolder = File(parentDir, folderName)
        newFolder.mkdirs()
    }

    override suspend fun uploadFile(
        localFile: File,
        remoteParentPath: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!localFile.exists()) return@withContext false
        directoryCache.clear()
        val destDir = if (remoteParentPath.isBlank()) getCacheDir() else File(getCacheDir(), remoteParentPath)
        if (!destDir.exists()) destDir.mkdirs()

        val destFile = File(destDir, localFile.name)
        val totalBytes = localFile.length()
        var bytesWritten = 0L

        try {
            localFile.inputStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesWritten += read
                        if (totalBytes > 0) {
                            onProgress(bytesWritten.toFloat() / totalBytes)
                        }
                    }
                }
            }
            sessionManager.updateQuota(providerId, totalSpace, usedSpace + destFile.length())
            true
        } catch (e: Exception) {
            Log.e("MediaFireProvider", "Upload error", e)
            false
        }
    }

    override suspend fun downloadFile(
        remoteFilePath: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanSub = remoteFilePath.trim('/').removePrefix("cloud/mediafire").removePrefix("cloud/MEDIAFIRE").trim('/')
        val srcFile = File(getCacheDir(), cleanSub)
        if (!srcFile.exists()) return@withContext false

        destinationFile.parentFile?.mkdirs()
        val totalBytes = srcFile.length()
        var bytesRead = 0L

        try {
            var lastReportedProgress = -1f
            var lastReportedTime = 0L
            srcFile.inputStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            val p = (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                            val now = System.currentTimeMillis()
                            if (p >= 1f || p - lastReportedProgress >= 0.01f || now - lastReportedTime >= 100L) {
                                lastReportedProgress = p
                                lastReportedTime = now
                                onProgress(p)
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MediaFireProvider", "Download error", e)
            false
        }
    }

    override suspend fun deleteFile(remoteFilePath: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val file = File(getCacheDir(), remoteFilePath.trimStart('/'))
        if (file.exists()) {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } else {
            false
        }
    }

    override suspend fun renameFile(oldRemotePath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val file = File(getCacheDir(), oldRemotePath.trimStart('/'))
        if (!file.exists()) return@withContext false
        val newFile = File(file.parentFile, newName)
        file.renameTo(newFile)
    }

    override suspend fun copyOrMoveFile(
        sourceRemotePath: String,
        destRemotePath: String,
        isMove: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val src = File(getCacheDir(), sourceRemotePath.trimStart('/'))
        val dest = File(getCacheDir(), destRemotePath.trimStart('/'))
        if (!src.exists()) return@withContext false

        try {
            if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = true)
                if (isMove) src.deleteRecursively()
            } else {
                src.copyTo(dest, overwrite = true)
                if (isMove) src.delete()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getShareLink(remoteFilePath: String): String? {
        val name = File(remoteFilePath).name
        return "https://www.mediafire.com/file/arcbox_${name.hashCode().toString(16)}/$name"
    }

    private fun ensureInitialWorkspace(cloudDir: File, providerName: String, accountEmail: String) {
        if (!cloudDir.exists()) cloudDir.mkdirs()
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var length = 0L
        file.listFiles()?.forEach { child ->
            length += if (child.isDirectory) getFolderSize(child) else child.length()
        }
        return length
    }

    private fun countFiles(file: File): Int {
        if (!file.exists()) return 0
        if (file.isFile) return 1
        var count = 0
        file.listFiles()?.forEach { count += countFiles(it) }
        return count
    }
}
