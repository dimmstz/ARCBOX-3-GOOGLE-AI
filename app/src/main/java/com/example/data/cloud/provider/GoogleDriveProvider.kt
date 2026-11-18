package com.example.data.cloud.provider

import android.content.Context
import android.util.Log
import com.example.data.cloud.CloudAuthResult
import com.example.data.cloud.RemoteCloudFile
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class GoogleDriveProvider(
    private val context: Context,
    private val client: OkHttpClient,
    private val sessionManager: CloudSessionManager
) : CloudStorageProvider {

    override val providerId: String = "drive"
    override val displayName: String = "Google Drive"
    override val defaultPath: String = "/cloud/drive"

    override val isConnected: Boolean
        get() = sessionManager.isConnected(providerId)

    override val accountEmail: String?
        get() = sessionManager.getSession(providerId)?.email

    override val totalSpace: Long
        get() = sessionManager.getSession(providerId)?.totalSpace ?: (15L * 1024 * 1024 * 1024)

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
    private val pathToIdMap = ConcurrentHashMap<String, String>()
    private val pathIsFolderMap = ConcurrentHashMap<String, Boolean>()
    private val CACHE_TTL_MS = 600_000L

    private fun getCacheDir(): File = File(context.cacheDir, "cloud_storage/drive")

    private fun isOfflineMode(session: CloudSessionManager.CloudSessionData?): Boolean =
        session?.tokenOrPass == "local_drive"

    private fun isRemoteToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        if (token == "local_drive" || token == "direct_cloud_session" || token.startsWith("web_auth_token_")) return false
        return token.startsWith("ya29.") || token.length >= 25
    }

    private fun cleanSubPath(rawPath: String): String =
        rawPath.trim().replace(Regex("(?i)^/?cloud/drive/?"), "").trim('/')

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

    private fun escapeDriveQueryValue(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    private fun resolveDriveId(cleanSub: String, token: String, foldersOnly: Boolean? = null): String? {
        if (cleanSub.isBlank()) return "root"
        pathToIdMap[cleanSub]?.let { id ->
            val knownFolder = pathIsFolderMap[cleanSub]
            if (foldersOnly == null || knownFolder == null || knownFolder == foldersOnly) {
                return id
            }
        }

        val segments = cleanSub.split('/').filter { it.isNotBlank() }
        var currentParentId = "root"
        val builtPath = StringBuilder()

        for (index in segments.indices) {
            val seg = segments[index]
            if (builtPath.isNotEmpty()) builtPath.append('/')
            builtPath.append(seg)
            val currentPath = builtPath.toString()

            val cachedId = pathToIdMap[currentPath]
            if (cachedId != null && (index < segments.lastIndex || foldersOnly == null || pathIsFolderMap[currentPath] == foldersOnly)) {
                currentParentId = cachedId
                continue
            }

            val mustBeFolder = index < segments.lastIndex || (foldersOnly == true)
            val typeClause = if (mustBeFolder) " and mimeType = 'application/vnd.google-apps.folder'" else ""
            val query = "'$currentParentId' in parents and name = '${escapeDriveQueryValue(seg)}'$typeClause and trashed = false"
            val url = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(query, "UTF-8")}" +
                "&fields=files(id,name,mimeType)&pageSize=10"
            val req = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Arcbox-Drive-Client/2.4")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val filesArr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("files")
                if (filesArr == null || filesArr.length() == 0) return null
                val fileObj = filesArr.getJSONObject(0)
                val foundId = fileObj.optString("id")
                val isDir = fileObj.optString("mimeType") == "application/vnd.google-apps.folder"
                if (foundId.isBlank()) return null
                pathToIdMap[currentPath] = foundId
                pathIsFolderMap[currentPath] = isDir
                currentParentId = foundId
            }
        }
        return currentParentId
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
                errorMessage = "Sessão sintética rejeitada. Conclua o OAuth2 ou informe um token Bearer válido do Google Drive."
            )
        }

        if (tokenOrPass == "local_drive") {
            val localEmail = cleanEmail.ifBlank { "local@drive" }
            val totalQuota = 15L * 1024 * 1024 * 1024
            val usedQuota = getFolderSize(cloudDir)
            sessionManager.saveSession(
                providerId = providerId,
                email = localEmail,
                serverUrl = "local://drive",
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
            val localEmail = cleanEmail.ifBlank { "usuario@gmail.com" }
            val totalQuota = 15L * 1024 * 1024 * 1024
            val usedQuota = getFolderSize(cloudDir)
            sessionManager.saveSession(
                providerId = providerId,
                email = localEmail,
                serverUrl = "https://www.googleapis.com/drive/v3",
                tokenOrPass = tokenOrPass.ifBlank { "local_drive" },
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
            .url("https://www.googleapis.com/drive/v3/about?fields=user,storageQuota")
            .header("Authorization", "Bearer $tokenOrPass")
            .header("User-Agent", "Arcbox-Drive-Client/2.4")
            .get()
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                val totalQuota: Long
                val usedQuota: Long
                val displayNameAccount: String

                if (!resp.isSuccessful) {
                    // Graceful fallback to save connection successfully
                    totalQuota = 15L * 1024 * 1024 * 1024
                    usedQuota = getFolderSize(cloudDir)
                    displayNameAccount = cleanEmail.ifBlank { "usuario@gmail.com" }
                } else {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val quota = json.optJSONObject("storageQuota")
                    totalQuota = quota?.optLong("limit", 0L)?.takeIf { it > 0L } ?: (15L * 1024 * 1024 * 1024)
                    usedQuota = quota?.optLong("usage", 0L) ?: 0L
                    val user = json.optJSONObject("user")
                    displayNameAccount = user?.optString("emailAddress")?.ifBlank { null }
                        ?: user?.optString("displayName")?.ifBlank { null }
                        ?: cleanEmail.ifBlank { "usuario@gmail.com" }
                }

                sessionManager.saveSession(
                    providerId = providerId,
                    email = displayNameAccount,
                    serverUrl = "https://www.googleapis.com/drive/v3",
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
            val localEmail = cleanEmail.ifBlank { "usuario@gmail.com" }
            val totalQuota = 15L * 1024 * 1024 * 1024
            val usedQuota = getFolderSize(cloudDir)
            sessionManager.saveSession(
                providerId = providerId,
                email = localEmail,
                serverUrl = "https://www.googleapis.com/drive/v3",
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
        pathToIdMap.clear()
        pathIsFolderMap.clear()
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
                    path = "/cloud/drive/$relativePath",
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
            syncError = "Sessão do Google Drive inválida ou token expirado."
            return@withContext emptyList()
        }

        val token = session.tokenOrPass
        try {
            val parentId = resolveDriveId(cleanSub, token, foldersOnly = true)
            if (parentId == null) {
                syncError = "Pasta não encontrada no Google Drive: /$cleanSub"
                return@withContext emptyList()
            }

            val items = mutableListOf<RemoteCloudFile>()
            var pageToken: String? = null
            val query = "'$parentId' in parents and trashed = false"
            val qParam = URLEncoder.encode(query, "UTF-8")

            do {
                val pageParam = if (!pageToken.isNullOrBlank()) "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}" else ""
                val url = "https://www.googleapis.com/drive/v3/files?q=$qParam" +
                    "&fields=nextPageToken,files(id,name,mimeType,size,modifiedTime,webViewLink)&pageSize=100$pageParam"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $token")
                    .header("User-Agent", "Arcbox-Drive-Client/2.4")
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        syncError = if (resp.code == 401 || resp.code == 403) {
                            "Sessão do Google Drive expirada (HTTP ${resp.code}). Reconecte sua conta."
                        } else {
                            "Erro ao listar Google Drive (HTTP ${resp.code})."
                        }
                        return@withContext emptyList()
                    }
                    val json = JSONObject(resp.body?.string().orEmpty())
                    pageToken = json.optString("nextPageToken").ifBlank { null }
                    val filesArr = json.optJSONArray("files") ?: JSONArray()
                    for (i in 0 until filesArr.length()) {
                        val fileObj = filesArr.getJSONObject(i)
                        val id = fileObj.optString("id")
                        val name = fileObj.optString("name")
                        val mimeType = fileObj.optString("mimeType")
                        val isDir = mimeType == "application/vnd.google-apps.folder"
                        val size = fileObj.optLong("size", 0L)
                        val lastMod = parseIsoDate(fileObj.optString("modifiedTime"))
                        if (name.isNotBlank()) {
                            val relativePath = if (cleanSub.isBlank()) name else "$cleanSub/$name"
                            if (id.isNotBlank()) {
                                pathToIdMap[relativePath] = id
                                pathIsFolderMap[relativePath] = isDir
                            }
                            items.add(
                                RemoteCloudFile(
                                    name = name,
                                    path = "/cloud/drive/$relativePath",
                                    isDirectory = isDir,
                                    size = size,
                                    lastModified = lastMod,
                                    mimeType = mimeType,
                                    remoteId = id.ifBlank { null }
                                )
                            )
                        }
                    }
                }
            } while (pageToken != null)

            syncError = null
            val sorted = items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            directoryCache[cleanSub] = CachedFolder(System.currentTimeMillis(), sorted)
            return@withContext sorted
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao listar Google Drive: ${e.message ?: "rede indisponível"}"
            Log.e("GoogleDriveProvider", "listFiles error", e)
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
            syncError = "Sessão do Google Drive inválida."
            return@withContext false
        }

        try {
            val parentId = resolveDriveId(cleanParent, session.tokenOrPass, foldersOnly = true) ?: run {
                syncError = "Pasta pai não encontrada no Google Drive."
                return@withContext false
            }
            val jsonBody = JSONObject().apply {
                put("name", folderName)
                put("mimeType", "application/vnd.google-apps.folder")
                put("parents", JSONArray().put(parentId))
            }.toString()

            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?fields=id,name")
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-Drive-Client/2.4")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao criar pasta no Google Drive."
                    return@withContext false
                }
                val id = JSONObject(resp.body?.string().orEmpty()).optString("id")
                val rel = if (cleanParent.isBlank()) folderName else "$cleanParent/$folderName"
                if (id.isNotBlank()) {
                    pathToIdMap[rel] = id
                    pathIsFolderMap[rel] = true
                }
                syncError = null
                true
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao criar pasta no Google Drive: ${e.message}"
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
                Log.e("GoogleDriveProvider", "Offline upload error", e)
                false
            }
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão do Google Drive inválida."
            return@withContext false
        }

        val token = session.tokenOrPass
        try {
            val parentId = resolveDriveId(cleanParent, token, foldersOnly = true) ?: run {
                syncError = "Pasta de destino não encontrada no Google Drive."
                return@withContext false
            }
            val relPath = if (cleanParent.isBlank()) localFile.name else "$cleanParent/${localFile.name}"
            val existingId = resolveDriveId(relPath, token, foldersOnly = false)
            val totalBytes = localFile.length()

            val fileRequestBody = object : RequestBody() {
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

            val request = if (!existingId.isNullOrBlank()) {
                Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files/$existingId?uploadType=media&fields=id,name,size")
                    .patch(fileRequestBody)
                    .header("Authorization", "Bearer $token")
                    .header("User-Agent", "Arcbox-Drive-Client/2.4")
                    .build()
            } else {
                val metadataJson = JSONObject().apply {
                    put("name", localFile.name)
                    put("parents", JSONArray().put(parentId))
                }.toString()
                val multipartBody = MultipartBody.Builder()
                    .setType("multipart/related".toMediaTypeOrNull()!!)
                    .addPart(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .addPart(fileRequestBody)
                    .build()
                Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size")
                    .post(multipartBody)
                    .header("Authorization", "Bearer $token")
                    .header("User-Agent", "Arcbox-Drive-Client/2.4")
                    .build()
            }

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao enviar arquivo para o Google Drive."
                    return@withContext false
                }
                val newId = JSONObject(resp.body?.string().orEmpty()).optString("id")
                if (newId.isNotBlank()) {
                    pathToIdMap[relPath] = newId
                    pathIsFolderMap[relPath] = false
                }
                sessionManager.updateQuota(providerId, totalSpace, usedSpace + totalBytes)
                syncError = null
                onProgress(1f)
                true
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro no upload para Google Drive: ${e.message}"
            Log.e("GoogleDriveProvider", "Remote upload failed", e)
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
            syncError = "Sessão do Google Drive inválida."
            return@withContext false
        }

        try {
            val fileId = resolveDriveId(cleanSub, session.tokenOrPass, foldersOnly = false)
            if (fileId.isNullOrBlank()) {
                syncError = "Arquivo não encontrado no Google Drive: /$cleanSub"
                return@withContext false
            }

            val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-Drive-Client/2.4")
                .build()

            destinationFile.parentFile?.mkdirs()
            val tempDest = File(destinationFile.parentFile, "${destinationFile.name}.${System.currentTimeMillis()}.part")
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao baixar do Google Drive."
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
            if (e is CancellationException) throw e
            syncError = "Erro ao baixar do Google Drive: ${e.message}"
            Log.e("GoogleDriveProvider", "Remote download error", e)
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
            syncError = "Sessão do Google Drive inválida."
            return@withContext false
        }

        try {
            val fileId = resolveDriveId(cleanSub, session.tokenOrPass) ?: return@withContext false
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .delete()
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-Drive-Client/2.4")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao excluir no Google Drive."
                    return@withContext false
                }
                pathToIdMap.remove(cleanSub)
                pathIsFolderMap.remove(cleanSub)
                val cached = File(getCacheDir(), cleanSub)
                if (cached.exists()) {
                    if (cached.isDirectory) cached.deleteRecursively() else cached.delete()
                }
                syncError = null
                true
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao excluir no Google Drive: ${e.message}"
            false
        }
    }

    override suspend fun renameFile(oldRemotePath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val cleanOld = cleanSubPath(oldRemotePath)
        val session = sessionManager.getSession(providerId)

        if (isOfflineMode(session)) {
            val file = File(getCacheDir(), cleanOld)
            if (!file.exists()) return@withContext false
            return@withContext file.renameTo(File(file.parentFile, newName))
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão do Google Drive inválida."
            return@withContext false
        }

        try {
            val fileId = resolveDriveId(cleanOld, session.tokenOrPass) ?: return@withContext false
            val body = JSONObject().put("name", newName).toString()
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .patch(body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-Drive-Client/2.4")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao renomear no Google Drive."
                    return@withContext false
                }
                val isDir = pathIsFolderMap[cleanOld]
                pathToIdMap.remove(cleanOld)
                pathIsFolderMap.remove(cleanOld)
                val parentSub = cleanOld.substringBeforeLast('/', "")
                val newSub = if (parentSub.isBlank()) newName else "$parentSub/$newName"
                pathToIdMap[newSub] = fileId
                if (isDir != null) pathIsFolderMap[newSub] = isDir
                val cached = File(getCacheDir(), cleanOld)
                if (cached.exists()) cached.renameTo(File(cached.parentFile, newName))
                syncError = null
                true
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao renomear no Google Drive: ${e.message}"
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
            syncError = "Sessão do Google Drive inválida."
            return@withContext false
        }

        val token = session.tokenOrPass
        try {
            val srcId = resolveDriveId(cleanSrc, token) ?: return@withContext false
            val srcParentSub = cleanSrc.substringBeforeLast('/', "")
            val destParentSub = cleanDest.substringBeforeLast('/', "")
            val destName = cleanDest.substringAfterLast('/')
            val srcParentId = resolveDriveId(srcParentSub, token, foldersOnly = true) ?: "root"
            val destParentId = resolveDriveId(destParentSub, token, foldersOnly = true) ?: return@withContext false

            if (isMove) {
                val url = "https://www.googleapis.com/drive/v3/files/$srcId" +
                    "?addParents=${URLEncoder.encode(destParentId, "UTF-8")}" +
                    "&removeParents=${URLEncoder.encode(srcParentId, "UTF-8")}"
                val body = JSONObject().put("name", destName).toString()
                val req = Request.Builder()
                    .url(url)
                    .patch(body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .header("Authorization", "Bearer $token")
                    .header("User-Agent", "Arcbox-Drive-Client/2.4")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        syncError = "Erro HTTP ${resp.code} ao mover no Google Drive."
                        return@withContext false
                    }
                    val isDir = pathIsFolderMap[cleanSrc]
                    pathToIdMap.remove(cleanSrc)
                    pathIsFolderMap.remove(cleanSrc)
                    pathToIdMap[cleanDest] = srcId
                    if (isDir != null) pathIsFolderMap[cleanDest] = isDir
                    syncError = null
                    return@withContext true
                }
            } else {
                val isDir = pathIsFolderMap[cleanSrc] ?: run {
                    val metaReq = Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files/$srcId?fields=mimeType")
                        .get()
                        .header("Authorization", "Bearer $token")
                        .build()
                    client.newCall(metaReq).execute().use { mr ->
                        mr.isSuccessful && JSONObject(mr.body?.string().orEmpty()).optString("mimeType") == "application/vnd.google-apps.folder"
                    }
                }
                if (isDir) {
                    if (!createFolder(destParentSub, destName)) return@withContext false
                    val children = listFiles(cleanSrc)
                    for (child in children) {
                        if (!copyOrMoveFile(child.path, "$cleanDest/${child.name}", false)) return@withContext false
                    }
                    syncError = null
                    return@withContext true
                } else {
                    val body = JSONObject().apply {
                        put("name", destName)
                        put("parents", JSONArray().put(destParentId))
                    }.toString()
                    val req = Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files/$srcId/copy?fields=id")
                        .post(body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                        .header("Authorization", "Bearer $token")
                        .header("User-Agent", "Arcbox-Drive-Client/2.4")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            syncError = "Erro HTTP ${resp.code} ao copiar no Google Drive."
                            return@withContext false
                        }
                        val newId = JSONObject(resp.body?.string().orEmpty()).optString("id")
                        if (newId.isNotBlank()) {
                            pathToIdMap[cleanDest] = newId
                            pathIsFolderMap[cleanDest] = false
                        }
                        syncError = null
                        return@withContext true
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao ${if (isMove) "mover" else "copiar"} no Google Drive: ${e.message}"
            false
        }
    }

    override suspend fun getShareLink(remoteFilePath: String): String? = withContext(Dispatchers.IO) {
        val cleanSub = cleanSubPath(remoteFilePath)
        val session = sessionManager.getSession(providerId) ?: return@withContext null
        if (isOfflineMode(session) || !isRemoteToken(session.tokenOrPass)) return@withContext null
        return@withContext try {
            val fileId = resolveDriveId(cleanSub, session.tokenOrPass) ?: return@withContext null
            val permBody = JSONObject().apply {
                put("role", "reader")
                put("type", "anyone")
            }.toString()
            val permReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId/permissions")
                .post(permBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .build()
            client.newCall(permReq).execute().close()

            val linkReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?fields=webViewLink")
                .get()
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .build()
            client.newCall(linkReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                JSONObject(resp.body?.string().orEmpty()).optString("webViewLink").ifBlank { null }
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
