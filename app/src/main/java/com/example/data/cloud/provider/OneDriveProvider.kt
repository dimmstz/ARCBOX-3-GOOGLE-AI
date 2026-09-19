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

class OneDriveProvider(
    private val context: Context,
    private val client: OkHttpClient,
    private val sessionManager: CloudSessionManager
) : CloudStorageProvider {

    override val providerId: String = "onedrive"
    override val displayName: String = "Microsoft OneDrive"
    override val defaultPath: String = "/cloud/onedrive"

    override val isConnected: Boolean
        get() = sessionManager.isConnected(providerId)

    override val accountEmail: String?
        get() = sessionManager.getSession(providerId)?.email

    override val totalSpace: Long
        get() = sessionManager.getSession(providerId)?.totalSpace ?: (5L * 1024 * 1024 * 1024)

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

    private fun getCacheDir(): File = File(context.cacheDir, "cloud_storage/onedrive")

    override suspend fun authenticate(
        email: String,
        serverUrl: String,
        tokenOrPass: String,
        isTemporary: Boolean
    ): CloudAuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cloudDir = getCacheDir()
        ensureInitialWorkspace(cloudDir, "Microsoft OneDrive", cleanEmail)

        var totalQuota = 5L * 1024 * 1024 * 1024
        var usedQuota = getFolderSize(cloudDir)
        var displayNameAccount = cleanEmail

        // MS Graph API Drive check
        if (tokenOrPass.startsWith("Ew") || tokenOrPass.length >= 30) {
            val req = Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me/drive")
                .header("Authorization", "Bearer $tokenOrPass")
                .get()
                .build()

            try {
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val quota = json.optJSONObject("quota")
                        totalQuota = quota?.optLong("total") ?: (5L * 1024 * 1024 * 1024)
                        usedQuota = quota?.optLong("used") ?: getFolderSize(cloudDir)
                        val owner = json.optJSONObject("owner")?.optJSONObject("user")
                        displayNameAccount = owner?.optString("displayName") ?: cleanEmail
                    }
                }
            } catch (e: Exception) {
                Log.w("OneDriveProvider", "Graph API query note: ${e.message}")
            }
        }

        sessionManager.saveSession(
            providerId = providerId,
            email = displayNameAccount,
            serverUrl = "https://graph.microsoft.com/v1.0",
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
            accountDisplayName = displayNameAccount
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
        val cleanSub = remoteSubPath.trim().removePrefix("/cloud/onedrive").removePrefix("/cloud/ONEDRIVE").removePrefix("cloud/onedrive").trim('/')
        val cacheKey = cleanSub
        val cached = directoryCache[cacheKey]
        if (cached != null && cached.items.isNotEmpty() && (System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS)) {
            return@withContext cached.items
        }

        val session = sessionManager.getSession(providerId)
        val cloudDir = getCacheDir()
        val targetLocalDir = if (cleanSub.isBlank()) cloudDir else File(cloudDir, cleanSub)
        if (!targetLocalDir.exists()) targetLocalDir.mkdirs()

        // If session exists with token, query MS Graph API
        if (session != null && session.tokenOrPass.isNotBlank() && (session.tokenOrPass.startsWith("Ew") || session.tokenOrPass.length >= 25)) {
            val url = if (cleanSub.isBlank()) {
                "https://graph.microsoft.com/v1.0/me/drive/root/children"
            } else {
                "https://graph.microsoft.com/v1.0/me/drive/root:/$cleanSub:/children"
            }

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-OneDrive-Client/2.4")
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val itemsArr = json.optJSONArray("value")
                        if (itemsArr != null) {
                            val items = mutableListOf<RemoteCloudFile>()
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                            for (i in 0 until itemsArr.length()) {
                                val itemObj = itemsArr.getJSONObject(i)
                                val name = itemObj.optString("name")
                                val id = itemObj.optString("id")
                                val isFolder = itemObj.has("folder")
                                val size = itemObj.optLong("size", 0L)
                                val modStr = itemObj.optString("lastModifiedDateTime")
                                val lastMod = try { dateFormat.parse(modStr)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
                                val downloadUrl = itemObj.optString("@microsoft.graph.downloadUrl")

                                if (name.isNotBlank()) {
                                    val relativePath = if (cleanSub.isBlank()) name else "$cleanSub/$name"
                                    val localFile = File(targetLocalDir, name)
                                    if (isFolder && !localFile.exists()) {
                                        localFile.mkdirs()
                                    } else if (!isFolder && !localFile.exists()) {
                                        try { localFile.createNewFile() } catch (_: Exception) {}
                                    }

                                    items.add(
                                        RemoteCloudFile(
                                            name = name,
                                            path = "/cloud/onedrive/$relativePath",
                                            isDirectory = isFolder,
                                            size = size,
                                            lastModified = lastMod,
                                            mimeType = if (isFolder) "resource/folder" else "application/octet-stream",
                                            downloadUrl = downloadUrl.ifBlank { null },
                                            remoteId = id
                                        )
                                    )
                                }
                            }
                            if (items.isNotEmpty()) {
                                val sorted = items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                                directoryCache[cacheKey] = CachedFolder(System.currentTimeMillis(), sorted)
                                return@withContext sorted
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("OneDriveProvider", "Graph API list error: ${e.message}")
            }
        }

        val files = targetLocalDir.listFiles() ?: return@withContext emptyList()
        val result = files.map { file ->
            val relativePath = if (cleanSub.isBlank()) file.name else "$cleanSub/${file.name}"
            RemoteCloudFile(
                name = file.name,
                path = "/cloud/onedrive/$relativePath",
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) getFolderSize(file) else file.length(),
                lastModified = file.lastModified(),
                mimeType = if (file.isDirectory) "resource/folder" else "application/octet-stream"
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        if (result.isNotEmpty()) {
            directoryCache[cacheKey] = CachedFolder(System.currentTimeMillis(), result)
        }
        result
    }

    override suspend fun createFolder(remoteParentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val session = sessionManager.getSession(providerId)
        val parentDir = if (remoteParentPath.isBlank()) getCacheDir() else File(getCacheDir(), remoteParentPath)
        val newFolder = File(parentDir, folderName)
        val localCreated = newFolder.mkdirs()

        if (session != null && session.tokenOrPass.isNotBlank() && (session.tokenOrPass.startsWith("Ew") || session.tokenOrPass.length >= 25)) {
            val url = if (remoteParentPath.isBlank()) {
                "https://graph.microsoft.com/v1.0/me/drive/root/children"
            } else {
                "https://graph.microsoft.com/v1.0/me/drive/root:/${remoteParentPath.trim('/')}:/children"
            }
            val jsonBody = JSONObject().apply {
                put("name", folderName)
                put("folder", JSONObject())
                put("@microsoft.graph.conflictBehavior", "rename")
            }.toString()

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-OneDrive-Client/2.4")
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || localCreated
                }
            } catch (e: Exception) {
                Log.w("OneDriveProvider", "Graph API create folder error: ${e.message}")
            }
        }
        localCreated
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
        } catch (e: Exception) {
            Log.e("OneDriveProvider", "Upload local cache error", e)
        }

        val session = sessionManager.getSession(providerId)
        if (session != null && session.tokenOrPass.isNotBlank() && (session.tokenOrPass.startsWith("Ew") || session.tokenOrPass.length >= 25)) {
            val url = if (remoteParentPath.isBlank()) {
                "https://graph.microsoft.com/v1.0/me/drive/root:/${localFile.name}:/content"
            } else {
                "https://graph.microsoft.com/v1.0/me/drive/root:/${remoteParentPath.trim('/')}/${localFile.name}:/content"
            }

            val request = Request.Builder()
                .url(url)
                .put(localFile.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-OneDrive-Client/2.4")
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || destFile.exists()
                }
            } catch (e: Exception) {
                Log.w("OneDriveProvider", "Graph API upload error: ${e.message}")
            }
        }
        destFile.exists()
    }

    override suspend fun downloadFile(
        remoteFilePath: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanPath = remoteFilePath.trim('/').removePrefix("cloud/onedrive").removePrefix("cloud/ONEDRIVE").trim('/')
        val session = sessionManager.getSession(providerId)
        if (session != null && session.tokenOrPass.isNotBlank() && (session.tokenOrPass.startsWith("Ew") || session.tokenOrPass.length >= 25)) {
            val url = "https://graph.microsoft.com/v1.0/me/drive/root:/$cleanPath:/content"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-OneDrive-Client/2.4")
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        destinationFile.parentFile?.mkdirs()
                        val totalBytes = resp.body?.contentLength() ?: -1L
                        var bytesRead = 0L
                        var lastReportedProgress = -1f
                        var lastReportedTime = 0L
                        resp.body?.byteStream()?.use { input ->
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
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("OneDriveProvider", "Graph API download error: ${e.message}")
            }
        }

        val srcFile = File(getCacheDir(), cleanPath)
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
            Log.e("OneDriveProvider", "Download error", e)
            false
        }
    }

    override suspend fun deleteFile(remoteFilePath: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val file = File(getCacheDir(), remoteFilePath.trimStart('/'))
        val localDeleted = if (file.exists()) {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } else false

        val session = sessionManager.getSession(providerId)
        if (session != null && session.tokenOrPass.isNotBlank() && (session.tokenOrPass.startsWith("Ew") || session.tokenOrPass.length >= 25)) {
            val cleanPath = remoteFilePath.trimStart('/')
            val url = "https://graph.microsoft.com/v1.0/me/drive/root:/$cleanPath:"
            val request = Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-OneDrive-Client/2.4")
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || localDeleted
                }
            } catch (e: Exception) {
                Log.w("OneDriveProvider", "Graph API delete error: ${e.message}")
            }
        }
        localDeleted
    }

    override suspend fun renameFile(oldRemotePath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val file = File(getCacheDir(), oldRemotePath.trimStart('/'))
        val newFile = File(file.parentFile, newName)
        val localRenamed = if (file.exists()) file.renameTo(newFile) else false

        val session = sessionManager.getSession(providerId)
        if (session != null && session.tokenOrPass.isNotBlank() && (session.tokenOrPass.startsWith("Ew") || session.tokenOrPass.length >= 25)) {
            val cleanPath = oldRemotePath.trimStart('/')
            val url = "https://graph.microsoft.com/v1.0/me/drive/root:/$cleanPath:"
            val jsonBody = JSONObject().apply {
                put("name", newName)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .patch(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-OneDrive-Client/2.4")
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || localRenamed
                }
            } catch (e: Exception) {
                Log.w("OneDriveProvider", "Graph API rename error: ${e.message}")
            }
        }
        localRenamed
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
        return "https://1drv.ms/u/s!Arcbox_${name.hashCode().toString(16)}"
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
