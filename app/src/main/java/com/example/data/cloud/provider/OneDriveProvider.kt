package com.example.data.cloud.provider

import android.content.Context
import android.util.Log
import com.example.data.cloud.CloudAuthResult
import com.example.data.cloud.RemoteCloudFile
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

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

    @Volatile
    private var syncError: String? = null

    override val lastSyncError: String?
        get() = syncError

    private data class CachedFolder(
        val timestamp: Long,
        val items: List<RemoteCloudFile>
    )

    private val directoryCache = ConcurrentHashMap<String, CachedFolder>()
    private val CACHE_TTL_MS = 600_000L

    private fun getCacheDir(): File = File(context.cacheDir, "cloud_storage/onedrive")

    private fun isOfflineMode(session: CloudSessionManager.CloudSessionData?): Boolean =
        session?.tokenOrPass == "local_onedrive"

    private fun isRemoteToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        if (token == "local_onedrive" || token == "direct_cloud_session" || token.startsWith("web_auth_token_")) return false
        return token.startsWith("Ew") || token.length >= 25
    }

    private fun cleanSubPath(rawPath: String): String =
        rawPath.trim().replace(Regex("(?i)^/?cloud/onedrive/?"), "").trim('/')

    private fun encodeGraphPath(cleanSub: String): String =
        cleanSub.split('/').filter { it.isNotBlank() }
            .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun parseIsoDate(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX"
        )
        for (pattern in patterns) {
            try {
                val df = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                df.parse(raw)?.let { return it.time }
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }

    override suspend fun authenticate(
        email: String,
        serverUrl: String,
        tokenOrPass: String,
        isTemporary: Boolean
    ): CloudAuthResult = withContext(Dispatchers.IO) {
        syncError = null
        val cleanEmail = email.trim()
        val cloudDir = getCacheDir()
        if (!cloudDir.exists()) cloudDir.mkdirs()

        if (tokenOrPass == "direct_cloud_session" || tokenOrPass.startsWith("web_auth_token_")) {
            return@withContext CloudAuthResult(
                success = false,
                errorMessage = "Sessão sintética rejeitada. Conclua o OAuth2 ou informe um token Bearer válido do OneDrive."
            )
        }

        if (tokenOrPass == "local_onedrive") {
            val localEmail = cleanEmail.ifBlank { "local@onedrive" }
            val totalQuota = 5L * 1024 * 1024 * 1024
            val usedQuota = getFolderSize(cloudDir)
            sessionManager.saveSession(
                providerId = providerId,
                email = localEmail,
                serverUrl = "local://onedrive",
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
                accountDisplayName = localEmail
            )
        }

        if (!isRemoteToken(tokenOrPass)) {
            val localEmail = cleanEmail.ifBlank { "usuario@outlook.com" }
            val totalQuota = 5L * 1024 * 1024 * 1024
            val usedQuota = getFolderSize(cloudDir)
            sessionManager.saveSession(
                providerId = providerId,
                email = localEmail,
                serverUrl = "https://graph.microsoft.com/v1.0",
                tokenOrPass = tokenOrPass.ifBlank { "local_onedrive" },
                isTemporary = isTemporary,
                totalSpace = totalQuota,
                usedSpace = usedQuota
            )
            return@withContext CloudAuthResult(
                success = true,
                quotaTotalBytes = totalQuota,
                quotaUsedBytes = usedQuota,
                remoteFileCount = countFiles(cloudDir),
                accountDisplayName = localEmail
            )
        }

        val req = Request.Builder()
            .url("https://graph.microsoft.com/v1.0/me/drive")
            .header("Authorization", "Bearer $tokenOrPass")
            .header("User-Agent", "Arcbox-OneDrive-Client/2.4")
            .get()
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                val totalQuota: Long
                val usedQuota: Long
                val displayNameAccount: String

                if (!resp.isSuccessful) {
                    totalQuota = 5L * 1024 * 1024 * 1024
                    usedQuota = getFolderSize(cloudDir)
                    displayNameAccount = cleanEmail.ifBlank { "usuario@outlook.com" }
                } else {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val quota = json.optJSONObject("quota")
                    totalQuota = quota?.optLong("total", 0L)?.takeIf { it > 0L } ?: (5L * 1024 * 1024 * 1024)
                    usedQuota = quota?.optLong("used", 0L) ?: 0L
                    val owner = json.optJSONObject("owner")?.optJSONObject("user")
                    displayNameAccount = owner?.optString("email")?.ifBlank { null }
                        ?: owner?.optString("displayName")?.ifBlank { null }
                        ?: cleanEmail.ifBlank { "usuario@outlook.com" }
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
                    remoteFileCount = 0,
                    accountDisplayName = displayNameAccount
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val localEmail = cleanEmail.ifBlank { "usuario@outlook.com" }
            val totalQuota = 5L * 1024 * 1024 * 1024
            val usedQuota = getFolderSize(cloudDir)
            sessionManager.saveSession(
                providerId = providerId,
                email = localEmail,
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
                accountDisplayName = localEmail
            )
        }
    }

    override suspend fun disconnect() {
        directoryCache.clear()
        syncError = null
        sessionManager.removeSession(providerId)
    }

    override fun invalidateCache() {
        directoryCache.clear()
    }

    override suspend fun listFiles(remoteSubPath: String): List<RemoteCloudFile> = withContext(Dispatchers.IO) {
        val cleanSub = cleanSubPath(remoteSubPath)
        val cached = directoryCache[cleanSub]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS)) {
            syncError = null
            return@withContext cached.items
        }

        val session = sessionManager.getSession(providerId)
        if (isOfflineMode(session)) {
            syncError = null
            val targetLocalDir = if (cleanSub.isBlank()) getCacheDir() else File(getCacheDir(), cleanSub)
            if (!targetLocalDir.exists()) targetLocalDir.mkdirs()
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
            directoryCache[cleanSub] = CachedFolder(System.currentTimeMillis(), result)
            return@withContext result
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão do OneDrive inválida ou token expirado."
            return@withContext emptyList()
        }

        val encoded = encodeGraphPath(cleanSub)
        var nextUrl: String? = if (encoded.isBlank()) {
            "https://graph.microsoft.com/v1.0/me/drive/root/children?\$top=200"
        } else {
            "https://graph.microsoft.com/v1.0/me/drive/root:/$encoded:/children?\$top=200"
        }

        try {
            val items = mutableListOf<RemoteCloudFile>()
            while (nextUrl != null) {
                val request = Request.Builder()
                    .url(nextUrl)
                    .get()
                    .header("Authorization", "Bearer ${session.tokenOrPass}")
                    .header("User-Agent", "Arcbox-OneDrive-Client/2.4")
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        syncError = if (resp.code == 401 || resp.code == 403) {
                            "Sessão do OneDrive expirada (HTTP ${resp.code}). Reconecte sua conta."
                        } else {
                            "Erro ao listar OneDrive (HTTP ${resp.code})."
                        }
                        return@withContext emptyList()
                    }
                    val json = JSONObject(resp.body?.string().orEmpty())
                    nextUrl = json.optString("@odata.nextLink").ifBlank { null }
                    val itemsArr = json.optJSONArray("value")
                    if (itemsArr != null) {
                        for (i in 0 until itemsArr.length()) {
                            val itemObj = itemsArr.getJSONObject(i)
                            val name = itemObj.optString("name")
                            val id = itemObj.optString("id")
                            val isFolder = itemObj.has("folder")
                            val size = itemObj.optLong("size", 0L)
                            val lastMod = parseIsoDate(itemObj.optString("lastModifiedDateTime"))
                            val downloadUrl = itemObj.optString("@microsoft.graph.downloadUrl")
                            val mime = if (isFolder) "resource/folder"
                            else itemObj.optJSONObject("file")?.optString("mimeType")?.ifBlank { null }
                                ?: "application/octet-stream"

                            if (name.isNotBlank()) {
                                val relativePath = if (cleanSub.isBlank()) name else "$cleanSub/$name"
                                items.add(
                                    RemoteCloudFile(
                                        name = name,
                                        path = "/cloud/onedrive/$relativePath",
                                        isDirectory = isFolder,
                                        size = size,
                                        lastModified = lastMod,
                                        mimeType = mime,
                                        downloadUrl = downloadUrl.ifBlank { null },
                                        remoteId = id.ifBlank { null }
                                    )
                                )
                            }
                        }
                    }
                }
            }
            syncError = null
            val sorted = items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            directoryCache[cleanSub] = CachedFolder(System.currentTimeMillis(), sorted)
            return@withContext sorted
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao listar OneDrive: ${e.message ?: "rede indisponível"}"
            Log.e("OneDriveProvider", "listFiles error", e)
            return@withContext emptyList()
        }
    }

    override suspend fun createFolder(remoteParentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val cleanParent = cleanSubPath(remoteParentPath)
        val session = sessionManager.getSession(providerId)

        if (isOfflineMode(session)) {
            val parentDir = if (cleanParent.isBlank()) getCacheDir() else File(getCacheDir(), cleanParent)
            val newFolder = File(parentDir, folderName)
            return@withContext newFolder.exists() || newFolder.mkdirs()
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão do OneDrive inválida."
            return@withContext false
        }

        val encodedParent = encodeGraphPath(cleanParent)
        val url = if (encodedParent.isBlank()) {
            "https://graph.microsoft.com/v1.0/me/drive/root/children"
        } else {
            "https://graph.microsoft.com/v1.0/me/drive/root:/$encodedParent:/children"
        }
        val jsonBody = JSONObject().apply {
            put("name", folderName)
            put("folder", JSONObject())
            put("@microsoft.graph.conflictBehavior", "rename")
        }.toString()

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .header("Authorization", "Bearer ${session.tokenOrPass}")
            .header("User-Agent", "Arcbox-OneDrive-Client/2.4")
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao criar pasta no OneDrive."
                    return@withContext false
                }
                syncError = null
                true
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao criar pasta no OneDrive: ${e.message}"
            false
        }
    }

    override suspend fun uploadFile(
        localFile: File,
        remoteParentPath: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!localFile.exists() || !localFile.isFile) return@withContext false
        directoryCache.clear()
        val cleanParent = cleanSubPath(remoteParentPath)
        val session = sessionManager.getSession(providerId)

        if (isOfflineMode(session)) {
            val destDir = if (cleanParent.isBlank()) getCacheDir() else File(getCacheDir(), cleanParent)
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, localFile.name)
            val totalBytes = localFile.length()
            var bytesWritten = 0L
            return@withContext try {
                localFile.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesWritten += read
                            if (totalBytes > 0) onProgress(bytesWritten.toFloat() / totalBytes)
                        }
                    }
                }
                sessionManager.updateQuota(providerId, totalSpace, usedSpace + destFile.length())
                onProgress(1f)
                true
            } catch (e: Exception) {
                Log.e("OneDriveProvider", "Offline upload error", e)
                false
            }
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão do OneDrive inválida."
            return@withContext false
        }

        val remoteSub = if (cleanParent.isBlank()) localFile.name else "$cleanParent/${localFile.name}"
        val encodedPath = encodeGraphPath(remoteSub)
        val totalBytes = localFile.length()

        try {
            if (totalBytes <= 4L * 1024 * 1024) {
                val url = "https://graph.microsoft.com/v1.0/me/drive/root:/$encodedPath:/content"
                val progressBody = object : RequestBody() {
                    override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                    override fun contentLength() = totalBytes
                    override fun writeTo(sink: BufferedSink) {
                        val buffer = ByteArray(64 * 1024)
                        var uploaded = 0L
                        localFile.inputStream().use { input ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                sink.write(buffer, 0, read)
                                uploaded += read
                                if (totalBytes > 0) {
                                    onProgress((uploaded.toFloat() / totalBytes).coerceIn(0f, 0.99f))
                                }
                            }
                        }
                    }
                }
                val request = Request.Builder()
                    .url(url)
                    .put(progressBody)
                    .header("Authorization", "Bearer ${session.tokenOrPass}")
                    .header("User-Agent", "Arcbox-OneDrive-Client/2.4")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        syncError = "Erro HTTP ${resp.code} ao enviar arquivo para o OneDrive."
                        return@withContext false
                    }
                }
            } else {
                // Upload session for files > 4 MB
                val sessionUrl = "https://graph.microsoft.com/v1.0/me/drive/root:/$encodedPath:/createUploadSession"
                val sessionBody = JSONObject().put(
                    "item",
                    JSONObject().put("@microsoft.graph.conflictBehavior", "replace")
                ).toString()
                val createReq = Request.Builder()
                    .url(sessionUrl)
                    .post(sessionBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .header("Authorization", "Bearer ${session.tokenOrPass}")
                    .build()
                val uploadUrl: String = client.newCall(createReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        syncError = "Erro HTTP ${resp.code} ao criar sessão de upload do OneDrive."
                        return@withContext false
                    }
                    JSONObject(resp.body?.string().orEmpty()).optString("uploadUrl")
                }
                if (uploadUrl.isBlank()) {
                    syncError = "URL de sessão de upload do OneDrive ausente."
                    return@withContext false
                }

                val chunkSize = 5 * 1024 * 1024 // multiple of 320 KiB (16 * 320 KiB)
                RandomAccessFile(localFile, "r").use { raf ->
                    var offset = 0L
                    while (offset < totalBytes) {
                        val toRead = minOf(chunkSize.toLong(), totalBytes - offset).toInt()
                        val bytes = ByteArray(toRead)
                        raf.seek(offset)
                        raf.readFully(bytes)
                        val end = offset + toRead - 1
                        val chunkReq = Request.Builder()
                            .url(uploadUrl)
                            .put(bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                            .header("Content-Range", "bytes $offset-$end/$totalBytes")
                            .build()
                        client.newCall(chunkReq).execute().use { chunkResp ->
                            if (!chunkResp.isSuccessful && chunkResp.code != 202) {
                                syncError = "Erro HTTP ${chunkResp.code} ao enviar bloco para o OneDrive."
                                return@withContext false
                            }
                        }
                        offset += toRead
                        onProgress((offset.toFloat() / totalBytes).coerceIn(0f, 0.99f))
                    }
                }
            }
            sessionManager.updateQuota(providerId, totalSpace, usedSpace + totalBytes)
            syncError = null
            onProgress(1f)
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro no upload para OneDrive: ${e.message}"
            Log.e("OneDriveProvider", "Graph API upload error", e)
            false
        }
    }

    override suspend fun downloadFile(
        remoteFilePath: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanPath = cleanSubPath(remoteFilePath)
        val session = sessionManager.getSession(providerId)

        if (isOfflineMode(session)) {
            val srcFile = File(getCacheDir(), cleanPath)
            try {
                if (destinationFile.canonicalPath == srcFile.canonicalPath) {
                    return@withContext if (srcFile.exists()) {
                        onProgress(1f)
                        true
                    } else false
                }
            } catch (_: Exception) {}
            if (!srcFile.exists() || srcFile.isDirectory) return@withContext false
            destinationFile.parentFile?.mkdirs()
            val totalBytes = srcFile.length()
            var bytesRead = 0L
            val tempDest = File(destinationFile.parentFile, "${destinationFile.name}.${System.currentTimeMillis()}.part")
            return@withContext try {
                srcFile.inputStream().use { input ->
                    FileOutputStream(tempDest).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (totalBytes > 0) onProgress((bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f))
                        }
                    }
                }
                if (destinationFile.exists()) destinationFile.delete()
                if (!tempDest.renameTo(destinationFile)) {
                    tempDest.copyTo(destinationFile, overwrite = true)
                    tempDest.delete()
                }
                onProgress(1f)
                true
            } catch (e: Exception) {
                tempDest.delete()
                if (e is CancellationException) throw e
                false
            }
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão do OneDrive inválida."
            return@withContext false
        }

        val url = "https://graph.microsoft.com/v1.0/me/drive/root:/${encodeGraphPath(cleanPath)}:/content"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer ${session.tokenOrPass}")
            .header("User-Agent", "Arcbox-OneDrive-Client/2.4")
            .build()

        destinationFile.parentFile?.mkdirs()
        val tempDest = File(destinationFile.parentFile, "${destinationFile.name}.${System.currentTimeMillis()}.part")
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao baixar do OneDrive."
                    return@withContext false
                }
                val totalBytes = resp.body?.contentLength() ?: -1L
                var bytesRead = 0L
                var lastReportedProgress = -1f
                var lastReportedTime = 0L
                resp.body?.byteStream()?.use { input ->
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
            }
            if (destinationFile.exists()) destinationFile.delete()
            if (!tempDest.renameTo(destinationFile)) {
                tempDest.copyTo(destinationFile, overwrite = true)
                tempDest.delete()
            }
            syncError = null
            onProgress(1f)
            true
        } catch (e: Exception) {
            tempDest.delete()
            if (e is CancellationException) throw e
            syncError = "Erro ao baixar do OneDrive: ${e.message}"
            Log.e("OneDriveProvider", "Graph API download error", e)
            false
        }
    }

    override suspend fun deleteFile(remoteFilePath: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val cleanPath = cleanSubPath(remoteFilePath)
        val session = sessionManager.getSession(providerId)

        if (isOfflineMode(session)) {
            val file = File(getCacheDir(), cleanPath)
            return@withContext if (file.exists()) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            } else false
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão do OneDrive inválida."
            return@withContext false
        }

        val url = "https://graph.microsoft.com/v1.0/me/drive/root:/${encodeGraphPath(cleanPath)}:"
        val request = Request.Builder()
            .url(url)
            .delete()
            .header("Authorization", "Bearer ${session.tokenOrPass}")
            .header("User-Agent", "Arcbox-OneDrive-Client/2.4")
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao excluir no OneDrive."
                    return@withContext false
                }
                val cached = File(getCacheDir(), cleanPath)
                if (cached.exists()) {
                    if (cached.isDirectory) cached.deleteRecursively() else cached.delete()
                }
                syncError = null
                true
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao excluir no OneDrive: ${e.message}"
            false
        }
    }

    override suspend fun renameFile(oldRemotePath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val cleanPath = cleanSubPath(oldRemotePath)
        val session = sessionManager.getSession(providerId)

        if (isOfflineMode(session)) {
            val file = File(getCacheDir(), cleanPath)
            if (!file.exists()) return@withContext false
            return@withContext file.renameTo(File(file.parentFile, newName))
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão do OneDrive inválida."
            return@withContext false
        }

        val url = "https://graph.microsoft.com/v1.0/me/drive/root:/${encodeGraphPath(cleanPath)}:"
        val jsonBody = JSONObject().put("name", newName).toString()
        val request = Request.Builder()
            .url(url)
            .patch(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .header("Authorization", "Bearer ${session.tokenOrPass}")
            .header("User-Agent", "Arcbox-OneDrive-Client/2.4")
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao renomear no OneDrive."
                    return@withContext false
                }
                val cached = File(getCacheDir(), cleanPath)
                if (cached.exists()) cached.renameTo(File(cached.parentFile, newName))
                syncError = null
                true
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao renomear no OneDrive: ${e.message}"
            false
        }
    }

    override suspend fun copyOrMoveFile(
        sourceRemotePath: String,
        destRemotePath: String,
        isMove: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val cleanSrc = cleanSubPath(sourceRemotePath)
        val cleanDest = cleanSubPath(destRemotePath)
        val session = sessionManager.getSession(providerId)

        if (isOfflineMode(session)) {
            val src = File(getCacheDir(), cleanSrc)
            val dest = File(getCacheDir(), cleanDest)
            if (!src.exists()) return@withContext false
            return@withContext try {
                dest.parentFile?.mkdirs()
                if (src.isDirectory) {
                    src.copyRecursively(dest, overwrite = true)
                    if (isMove) src.deleteRecursively()
                } else {
                    src.copyTo(dest, overwrite = true)
                    if (isMove) src.delete()
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão do OneDrive inválida."
            return@withContext false
        }

        try {
            val destParentSub = cleanDest.substringBeforeLast('/', "")
            val destName = cleanDest.substringAfterLast('/')
            val parentUrl = if (destParentSub.isBlank()) {
                "https://graph.microsoft.com/v1.0/me/drive/root?\$select=id"
            } else {
                "https://graph.microsoft.com/v1.0/me/drive/root:/${encodeGraphPath(destParentSub)}?\$select=id"
            }
            val destParentId: String = client.newCall(
                Request.Builder().url(parentUrl).get()
                    .header("Authorization", "Bearer ${session.tokenOrPass}")
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Pasta de destino não encontrada no OneDrive (HTTP ${resp.code})."
                    return@withContext false
                }
                JSONObject(resp.body?.string().orEmpty()).optString("id")
            }
            if (destParentId.isBlank()) return@withContext false

            val payload = JSONObject().apply {
                put("parentReference", JSONObject().put("id", destParentId))
                put("name", destName)
            }.toString()

            val encodedSrc = encodeGraphPath(cleanSrc)
            if (isMove) {
                val req = Request.Builder()
                    .url("https://graph.microsoft.com/v1.0/me/drive/root:/$encodedSrc:")
                    .patch(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .header("Authorization", "Bearer ${session.tokenOrPass}")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        syncError = "Erro HTTP ${resp.code} ao mover no OneDrive."
                        return@withContext false
                    }
                    syncError = null
                    return@withContext true
                }
            } else {
                val req = Request.Builder()
                    .url("https://graph.microsoft.com/v1.0/me/drive/root:/$encodedSrc:/copy")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .header("Authorization", "Bearer ${session.tokenOrPass}")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 202) {
                        syncError = null
                        return@withContext true
                    }
                    syncError = "Erro HTTP ${resp.code} ao copiar no OneDrive."
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao ${if (isMove) "mover" else "copiar"} no OneDrive: ${e.message}"
            false
        }
    }

    override suspend fun getShareLink(remoteFilePath: String): String? = withContext(Dispatchers.IO) {
        val cleanSub = cleanSubPath(remoteFilePath)
        val session = sessionManager.getSession(providerId) ?: return@withContext null
        if (isOfflineMode(session) || !isRemoteToken(session.tokenOrPass)) return@withContext null
        return@withContext try {
            val body = JSONObject().apply {
                put("type", "view")
                put("scope", "anonymous")
            }.toString()
            val req = Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me/drive/root:/${encodeGraphPath(cleanSub)}:/createLink")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                JSONObject(resp.body?.string().orEmpty())
                    .optJSONObject("link")
                    ?.optString("webUrl")
                    ?.ifBlank { null }
            }
        } catch (_: Exception) {
            null
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
