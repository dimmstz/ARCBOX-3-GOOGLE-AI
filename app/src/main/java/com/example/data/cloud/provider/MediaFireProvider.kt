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

    private fun cleanPath(rawPath: String): String {
        return rawPath.trim()
            .removePrefix("/")
            .removePrefix("cloud/mediafire")
            .removePrefix("cloud/MEDIAFIRE")
            .removePrefix("/cloud/mediafire")
            .removePrefix("/cloud/MEDIAFIRE")
            .trim('/')
    }

    override suspend fun authenticate(
        email: String,
        serverUrl: String,
        tokenOrPass: String,
        isTemporary: Boolean
    ): CloudAuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().ifBlank { "usuario@mediafire.com" }
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

    override fun invalidateCache() {
        directoryCache.clear()
    }

    override suspend fun listFiles(remoteSubPath: String): List<RemoteCloudFile> = withContext(Dispatchers.IO) {
        val cleanSub = cleanPath(remoteSubPath)
        val cached = directoryCache[cleanSub]
        if (cached != null && cached.items.isNotEmpty() && (System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS)) {
            return@withContext cached.items
        }

        val targetLocalDir = if (cleanSub.isBlank()) getCacheDir() else File(getCacheDir(), cleanSub)
        if (!targetLocalDir.exists()) {
            targetLocalDir.mkdirs()
        }

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
        val cleanParent = cleanPath(remoteParentPath)
        val parentDir = if (cleanParent.isBlank()) getCacheDir() else File(getCacheDir(), cleanParent)
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
        val cleanParent = cleanPath(remoteParentPath)
        val destDir = if (cleanParent.isBlank()) getCacheDir() else File(getCacheDir(), cleanParent)
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
        val cleanSub = cleanPath(remoteFilePath)
        val srcFile = File(getCacheDir(), cleanSub)

        // If destination points to the exact same file in the cloud cache, it is already ready to read!
        try {
            if (destinationFile.canonicalPath == srcFile.canonicalPath) {
                if (srcFile.exists()) {
                    onProgress(1f)
                    return@withContext true
                } else {
                    return@withContext false
                }
            }
        } catch (_: Exception) {}

        if (!srcFile.exists() || srcFile.isDirectory) return@withContext false

        destinationFile.parentFile?.mkdirs()
        val totalBytes = srcFile.length()
        var bytesRead = 0L

        val tempDest = File(destinationFile.parentFile ?: destinationFile.absoluteFile.parentFile, "${destinationFile.name}.${System.currentTimeMillis()}.part")
        try {
            var lastReportedProgress = -1f
            var lastReportedTime = 0L
            srcFile.inputStream().use { input ->
                FileOutputStream(tempDest).use { output ->
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
            if (tempDest.exists()) {
                if (destinationFile.exists()) destinationFile.delete()
                val success = tempDest.renameTo(destinationFile)
                if (!success) {
                    tempDest.copyTo(destinationFile, overwrite = true)
                    tempDest.delete()
                }
            }
            onProgress(1f)
            true
        } catch (e: Exception) {
            try {
                if (tempDest.exists()) tempDest.delete()
            } catch (_: Exception) {}
            if (e is CancellationException) throw e
            Log.e("MediaFireProvider", "Download error", e)
            false
        }
    }

    override suspend fun deleteFile(remoteFilePath: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val cleanSub = cleanPath(remoteFilePath)
        val file = File(getCacheDir(), cleanSub)
        if (file.exists()) {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } else {
            false
        }
    }

    override suspend fun renameFile(oldRemotePath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val cleanSub = cleanPath(oldRemotePath)
        val file = File(getCacheDir(), cleanSub)
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
        val cleanSrc = cleanPath(sourceRemotePath)
        val cleanDest = cleanPath(destRemotePath)
        val src = File(getCacheDir(), cleanSrc)
        val dest = File(getCacheDir(), cleanDest)
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
        val readme = File(cloudDir, "Bem-vindo ao MediaFire.txt")
        if (readme.exists()) {
            try { readme.delete() } catch (_: Exception) {}
        }
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
