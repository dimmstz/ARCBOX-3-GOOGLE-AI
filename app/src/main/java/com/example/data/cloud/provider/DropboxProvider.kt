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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DropboxProvider(
    private val context: Context,
    private val client: OkHttpClient,
    private val sessionManager: CloudSessionManager
) : CloudStorageProvider {

    override val providerId: String = "dropbox"
    override val displayName: String = "Dropbox"
    override val defaultPath: String = "/cloud/dropbox"

    override val isConnected: Boolean
        get() = sessionManager.isConnected(providerId)

    override val accountEmail: String?
        get() = sessionManager.getSession(providerId)?.email

    override val totalSpace: Long
        get() = sessionManager.getSession(providerId)?.totalSpace ?: (2L * 1024 * 1024 * 1024)

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

    private fun getCacheDir(): File = File(context.cacheDir, "cloud_storage/dropbox")

    private fun isOfflineMode(session: CloudSessionManager.CloudSessionData?): Boolean =
        session?.tokenOrPass == "local_dropbox"

    private fun isRemoteToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        if (token == "local_dropbox" || token == "direct_cloud_session" || token.startsWith("web_auth_token_")) return false
        return token.length >= 20
    }

    private fun cleanSubPath(rawPath: String): String =
        rawPath.trim().replace(Regex("(?i)^/?cloud/dropbox/?"), "").trim('/')

    /**
     * Dropbox HTTP header Dropbox-API-Arg requires ASCII-safe JSON (unicode escaped).
     */
    private fun escapeDropboxHeaderJson(json: String): String {
        val sb = StringBuilder(json.length)
        for (ch in json) {
            if (ch.code > 127) {
                sb.append(String.format(Locale.US, "\\u%04x", ch.code))
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun parseIsoDate(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
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
                errorMessage = "Sessão sintética rejeitada. Conclua o OAuth2 ou informe um token Bearer válido do Dropbox."
            )
        }

        if (tokenOrPass == "local_dropbox") {
            val localEmail = cleanEmail.ifBlank { "local@dropbox" }
            val totalQuota = 2L * 1024 * 1024 * 1024
            val usedQuota = getFolderSize(cloudDir)
            sessionManager.saveSession(
                providerId = providerId,
                email = localEmail,
                serverUrl = "local://dropbox",
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
            val localEmail = cleanEmail.ifBlank { "usuario@dropbox.com" }
            val totalQuota = 2L * 1024 * 1024 * 1024
            val usedQuota = getFolderSize(cloudDir)
            sessionManager.saveSession(
                providerId = providerId,
                email = localEmail,
                serverUrl = "https://api.dropboxapi.com/2",
                tokenOrPass = tokenOrPass.ifBlank { "local_dropbox" },
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

        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/users/get_current_account")
            .post("null".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .header("Authorization", "Bearer $tokenOrPass")
            .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
            .build()

        try {
            var displayNameAccount = cleanEmail.ifBlank { "usuario@dropbox.com" }
            var totalQuota = 2L * 1024 * 1024 * 1024
            var usedQuota = 0L

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string().orEmpty())
                    val nameObj = json.optJSONObject("name")
                    displayNameAccount = json.optString("email").ifBlank { null }
                        ?: nameObj?.optString("display_name")?.ifBlank { null }
                        ?: displayNameAccount
                }
            }

            val quotaReq = Request.Builder()
                .url("https://api.dropboxapi.com/2/users/get_space_usage")
                .post("null".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .header("Authorization", "Bearer $tokenOrPass")
                .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
                .build()
            try {
                client.newCall(quotaReq).execute().use { qResp ->
                    if (qResp.isSuccessful) {
                        val qJson = JSONObject(qResp.body?.string().orEmpty())
                        usedQuota = qJson.optLong("used", 0L)
                        val allocated = qJson.optJSONObject("allocation")?.optLong("allocated", 0L) ?: 0L
                        if (allocated > 0L) totalQuota = allocated
                    }
                }
            } catch (_: Exception) {}

            sessionManager.saveSession(
                providerId = providerId,
                email = displayNameAccount,
                serverUrl = "https://api.dropboxapi.com/2",
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val localEmail = cleanEmail.ifBlank { "usuario@dropbox.com" }
            val totalQuota = 2L * 1024 * 1024 * 1024
            val usedQuota = getFolderSize(cloudDir)
            sessionManager.saveSession(
                providerId = providerId,
                email = localEmail,
                serverUrl = "https://api.dropboxapi.com/2",
                tokenOrPass = tokenOrPass.ifBlank { "local_dropbox" },
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
                    path = "/cloud/dropbox/$relativePath",
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
            syncError = "Sessão do Dropbox inválida ou token expirado."
            return@withContext emptyList()
        }

        try {
            val dbxPath = if (cleanSub.isBlank()) "" else "/$cleanSub"
            val initialPayload = JSONObject().apply {
                put("path", dbxPath)
                put("recursive", false)
                put("include_media_info", false)
                put("include_deleted", false)
            }.toString()

            val items = mutableListOf<RemoteCloudFile>()
            var endpoint = "https://api.dropboxapi.com/2/files/list_folder"
            var payload = initialPayload

            while (true) {
                val request = Request.Builder()
                    .url(endpoint)
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .header("Authorization", "Bearer ${session.tokenOrPass}")
                    .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
                    .build()

                var hasMore = false
                var cursor = ""
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        syncError = if (resp.code == 401 || resp.code == 403) {
                            "Sessão do Dropbox expirada (HTTP ${resp.code}). Reconecte sua conta."
                        } else {
                            "Erro ao listar Dropbox (HTTP ${resp.code})."
                        }
                        return@withContext emptyList()
                    }
                    val json = JSONObject(resp.body?.string().orEmpty())
                    hasMore = json.optBoolean("has_more", false)
                    cursor = json.optString("cursor")
                    val entries = json.optJSONArray("entries") ?: JSONArray()
                    for (i in 0 until entries.length()) {
                        val obj = entries.getJSONObject(i)
                        val tag = obj.optString(".tag")
                        if (tag == "deleted") continue
                        val name = obj.optString("name")
                        val isDir = tag == "folder"
                        val size = obj.optLong("size", 0L)
                        val lastMod = parseIsoDate(obj.optString("server_modified"))
                        val id = obj.optString("id")
                        if (name.isNotBlank()) {
                            val relativePath = if (cleanSub.isBlank()) name else "$cleanSub/$name"
                            items.add(
                                RemoteCloudFile(
                                    name = name,
                                    path = "/cloud/dropbox/$relativePath",
                                    isDirectory = isDir,
                                    size = size,
                                    lastModified = lastMod,
                                    mimeType = if (isDir) "resource/folder" else "application/octet-stream",
                                    remoteId = id.ifBlank { null }
                                )
                            )
                        }
                    }
                }
                if (!hasMore || cursor.isBlank()) break
                endpoint = "https://api.dropboxapi.com/2/files/list_folder/continue"
                payload = JSONObject().put("cursor", cursor).toString()
            }

            syncError = null
            val sorted = items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            directoryCache[cleanSub] = CachedFolder(System.currentTimeMillis(), sorted)
            return@withContext sorted
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao listar Dropbox: ${e.message ?: "rede indisponível"}"
            Log.e("DropboxProvider", "list_folder error", e)
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
            syncError = "Sessão do Dropbox inválida."
            return@withContext false
        }

        val dbxPath = if (cleanParent.isBlank()) "/$folderName" else "/$cleanParent/$folderName"
        val jsonPayload = JSONObject().apply {
            put("path", dbxPath)
            put("autorename", false)
        }.toString()

        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/create_folder_v2")
            .post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .header("Authorization", "Bearer ${session.tokenOrPass}")
            .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao criar pasta no Dropbox."
                    return@withContext false
                }
                syncError = null
                true
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao criar pasta no Dropbox: ${e.message}"
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
                Log.e("DropboxProvider", "Offline upload error", e)
                false
            }
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão do Dropbox inválida."
            return@withContext false
        }

        val dbxPath = if (cleanParent.isBlank()) "/${localFile.name}" else "/$cleanParent/${localFile.name}"
        val totalBytes = localFile.length()

        try {
            if (totalBytes <= 140L * 1024 * 1024) {
                val argJson = escapeDropboxHeaderJson(
                    JSONObject().apply {
                        put("path", dbxPath)
                        put("mode", "overwrite")
                        put("autorename", false)
                        put("mute", false)
                    }.toString()
                )
                val progressBody = object : RequestBody() {
                    override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                    override fun contentLength() = totalBytes
                    override fun writeTo(sink: BufferedSink) {
                        val buf = ByteArray(64 * 1024)
                        var uploaded = 0L
                        localFile.inputStream().use { input ->
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                sink.write(buf, 0, read)
                                uploaded += read
                                if (totalBytes > 0) {
                                    onProgress((uploaded.toFloat() / totalBytes).coerceIn(0f, 0.99f))
                                }
                            }
                        }
                    }
                }
                val request = Request.Builder()
                    .url("https://content.dropboxapi.com/2/files/upload")
                    .post(progressBody)
                    .header("Authorization", "Bearer ${session.tokenOrPass}")
                    .header("Dropbox-API-Arg", argJson)
                    .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        syncError = "Erro HTTP ${resp.code} no upload para Dropbox."
                        return@withContext false
                    }
                }
            } else {
                // Chunked upload session (>140MB)
                val startArg = escapeDropboxHeaderJson(JSONObject().put("close", false).toString())
                val startReq = Request.Builder()
                    .url("https://content.dropboxapi.com/2/files/upload_session/start")
                    .post(ByteArray(0).toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                    .header("Authorization", "Bearer ${session.tokenOrPass}")
                    .header("Dropbox-API-Arg", startArg)
                    .build()
                val sessionId = client.newCall(startReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        syncError = "Erro HTTP ${resp.code} ao iniciar sessão de upload no Dropbox."
                        return@withContext false
                    }
                    JSONObject(resp.body?.string().orEmpty()).optString("session_id")
                }
                if (sessionId.isBlank()) return@withContext false

                val chunkSize = 8 * 1024 * 1024
                RandomAccessFile(localFile, "r").use { raf ->
                    var offset = 0L
                    while (offset < totalBytes) {
                        val toRead = minOf(chunkSize.toLong(), totalBytes - offset).toInt()
                        val bytes = ByteArray(toRead)
                        raf.seek(offset)
                        raf.readFully(bytes)
                        val isLast = offset + toRead >= totalBytes
                        if (!isLast) {
                            val appendArg = escapeDropboxHeaderJson(
                                JSONObject().apply {
                                    put("cursor", JSONObject().apply {
                                        put("session_id", sessionId)
                                        put("offset", offset)
                                    })
                                    put("close", false)
                                }.toString()
                            )
                            val appendReq = Request.Builder()
                                .url("https://content.dropboxapi.com/2/files/upload_session/append_v2")
                                .post(bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                                .header("Authorization", "Bearer ${session.tokenOrPass}")
                                .header("Dropbox-API-Arg", appendArg)
                                .build()
                            client.newCall(appendReq).execute().use { r ->
                                if (!r.isSuccessful) {
                                    syncError = "Erro HTTP ${r.code} no envio de bloco Dropbox."
                                    return@withContext false
                                }
                            }
                        } else {
                            val finishArg = escapeDropboxHeaderJson(
                                JSONObject().apply {
                                    put("cursor", JSONObject().apply {
                                        put("session_id", sessionId)
                                        put("offset", offset)
                                    })
                                    put("commit", JSONObject().apply {
                                        put("path", dbxPath)
                                        put("mode", "overwrite")
                                        put("autorename", false)
                                        put("mute", false)
                                    })
                                }.toString()
                            )
                            val finishReq = Request.Builder()
                                .url("https://content.dropboxapi.com/2/files/upload_session/finish")
                                .post(bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                                .header("Authorization", "Bearer ${session.tokenOrPass}")
                                .header("Dropbox-API-Arg", finishArg)
                                .build()
                            client.newCall(finishReq).execute().use { r ->
                                if (!r.isSuccessful) {
                                    syncError = "Erro HTTP ${r.code} ao finalizar upload no Dropbox."
                                    return@withContext false
                                }
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
            syncError = "Erro no upload para Dropbox: ${e.message}"
            Log.e("DropboxProvider", "Remote upload failed", e)
            false
        }
    }

    override suspend fun downloadFile(
        remoteFilePath: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanSub = cleanSubPath(remoteFilePath)
        val session = sessionManager.getSession(providerId)

        if (isOfflineMode(session)) {
            val srcFile = File(getCacheDir(), cleanSub)
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
            syncError = "Sessão do Dropbox inválida."
            return@withContext false
        }

        val argJson = escapeDropboxHeaderJson(JSONObject().put("path", "/$cleanSub").toString())
        val request = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/download")
            .post(ByteArray(0).toRequestBody(null))
            .header("Authorization", "Bearer ${session.tokenOrPass}")
            .header("Dropbox-API-Arg", argJson)
            .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
            .build()

        destinationFile.parentFile?.mkdirs()
        val tempDest = File(destinationFile.parentFile, "${destinationFile.name}.${System.currentTimeMillis()}.part")
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao baixar do Dropbox."
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
            syncError = "Erro ao baixar do Dropbox: ${e.message}"
            Log.e("DropboxProvider", "Remote download failed", e)
            false
        }
    }

    override suspend fun deleteFile(remoteFilePath: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val cleanSub = cleanSubPath(remoteFilePath)
        val session = sessionManager.getSession(providerId)

        if (isOfflineMode(session)) {
            val file = File(getCacheDir(), cleanSub)
            return@withContext if (file.exists()) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            } else false
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão do Dropbox inválida."
            return@withContext false
        }

        val jsonPayload = JSONObject().put("path", "/$cleanSub").toString()
        val request = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/delete_v2")
            .post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .header("Authorization", "Bearer ${session.tokenOrPass}")
            .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao excluir no Dropbox."
                    return@withContext false
                }
                val cached = File(getCacheDir(), cleanSub)
                if (cached.exists()) {
                    if (cached.isDirectory) cached.deleteRecursively() else cached.delete()
                }
                syncError = null
                true
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao excluir no Dropbox: ${e.message}"
            false
        }
    }

    override suspend fun renameFile(oldRemotePath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val cleanOld = cleanSubPath(oldRemotePath)
        val parentSub = cleanOld.substringBeforeLast('/', "")
        val newSub = if (parentSub.isBlank()) newName else "$parentSub/$newName"
        copyOrMoveFile(cleanOld, newSub, isMove = true)
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
            syncError = "Sessão do Dropbox inválida."
            return@withContext false
        }

        val jsonPayload = JSONObject().apply {
            put("from_path", "/$cleanSrc")
            put("to_path", "/$cleanDest")
            put("autorename", false)
        }.toString()
        val endpoint = if (isMove) {
            "https://api.dropboxapi.com/2/files/move_v2"
        } else {
            "https://api.dropboxapi.com/2/files/copy_v2"
        }

        val request = Request.Builder()
            .url(endpoint)
            .post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .header("Authorization", "Bearer ${session.tokenOrPass}")
            .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao ${if (isMove) "mover" else "copiar"} no Dropbox."
                    return@withContext false
                }
                val cachedSrc = File(getCacheDir(), cleanSrc)
                if (isMove && cachedSrc.exists()) {
                    val cachedDest = File(getCacheDir(), cleanDest)
                    cachedDest.parentFile?.mkdirs()
                    cachedSrc.renameTo(cachedDest)
                }
                syncError = null
                true
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao ${if (isMove) "mover" else "copiar"} no Dropbox: ${e.message}"
            false
        }
    }

    override suspend fun getShareLink(remoteFilePath: String): String? = withContext(Dispatchers.IO) {
        val cleanSub = cleanSubPath(remoteFilePath)
        val session = sessionManager.getSession(providerId) ?: return@withContext null
        if (isOfflineMode(session) || !isRemoteToken(session.tokenOrPass)) return@withContext null
        val dbxPath = "/$cleanSub"
        return@withContext try {
            val createReq = Request.Builder()
                .url("https://api.dropboxapi.com/2/sharing/create_shared_link_with_settings")
                .post(JSONObject().put("path", dbxPath).toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .build()
            client.newCall(createReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    return@withContext JSONObject(resp.body?.string().orEmpty()).optString("url").ifBlank { null }
                }
            }
            val listReq = Request.Builder()
                .url("https://api.dropboxapi.com/2/sharing/list_shared_links")
                .post(
                    JSONObject().apply {
                        put("path", dbxPath)
                        put("direct_only", true)
                    }.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                )
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .build()
            client.newCall(listReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val links = JSONObject(resp.body?.string().orEmpty()).optJSONArray("links")
                if (links != null && links.length() > 0) {
                    links.getJSONObject(0).optString("url").ifBlank { null }
                } else null
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
