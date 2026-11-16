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
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

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

    @Volatile
    private var syncError: String? = null

    override val lastSyncError: String?
        get() = syncError

    private data class CachedFolder(
        val timestamp: Long,
        val items: List<RemoteCloudFile>
    )

    private val directoryCache = ConcurrentHashMap<String, CachedFolder>()
    private val pathToKeyMap = ConcurrentHashMap<String, String>()
    private val pathIsFolderMap = ConcurrentHashMap<String, Boolean>()
    private val CACHE_TTL_MS = 600_000L

    private fun getCacheDir(): File = File(context.cacheDir, "cloud_storage/mediafire")

    private fun isOfflineMode(session: CloudSessionManager.CloudSessionData?): Boolean =
        session?.tokenOrPass == "local_mediafire"

    private fun isRemoteToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        if (token == "local_mediafire" || token == "direct_cloud_session" || token.startsWith("web_auth_token_")) return false
        return token.length >= 24
    }

    private fun cleanPath(rawPath: String): String {
        return rawPath.trim()
            .replace(Regex("(?i)^/?cloud/mediafire/?"), "")
            .trim('/')
    }

    private fun parseMediaFireDate(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
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

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
                errorMessage = "Sessão sintética rejeitada. Informe um session_token válido do MediaFire ou use o modo local."
            )
        }

        if (tokenOrPass == "local_mediafire") {
            val localEmail = cleanEmail.ifBlank { "local@mediafire" }
            val totalQuota = 10L * 1024 * 1024 * 1024
            val usedQuota = getFolderSize(cloudDir)
            sessionManager.saveSession(
                providerId = providerId,
                email = localEmail,
                serverUrl = "local://mediafire",
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
            return@withContext CloudAuthResult(
                success = false,
                errorMessage = "Para conectar ao MediaFire remoto, informe um session_token da API v1.5 (>= 24 caracteres) ou use o modo offline (local_mediafire)."
            )
        }

        val encToken = URLEncoder.encode(tokenOrPass, "UTF-8")
        val req = Request.Builder()
            .url("https://www.mediafire.com/api/1.5/user/get_info.php?session_token=$encToken&response_format=json")
            .get()
            .header("User-Agent", "Arcbox-MediaFire-Client/2.4")
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val msg = "Falha HTTP ${resp.code} ao autenticar no MediaFire."
                    syncError = msg
                    return@withContext CloudAuthResult(success = false, errorMessage = msg)
                }
                val body = resp.body?.string().orEmpty()
                val root = JSONObject(body)
                val mfResp = root.optJSONObject("response")
                val result = mfResp?.optString("result")
                if (result != "Success") {
                    val errMsg = mfResp?.optString("message")?.ifBlank { "Token MediaFire inválido ou expirado." }
                        ?: "Resposta inválida do MediaFire."
                    syncError = errMsg
                    return@withContext CloudAuthResult(success = false, errorMessage = errMsg)
                }
                val userInfo = mfResp.optJSONObject("user_info")
                val displayEmail = userInfo?.optString("email")?.ifBlank { null }
                    ?: userInfo?.optString("display_name")?.ifBlank { null }
                    ?: cleanEmail.ifBlank { "usuario@mediafire.com" }
                val usedBytes = userInfo?.optString("used_storage_size")?.toLongOrNull() ?: 0L
                val totalBytes = userInfo?.optString("storage_limit")?.toLongOrNull()
                    ?.takeIf { it > 0L } ?: (10L * 1024 * 1024 * 1024)

                sessionManager.saveSession(
                    providerId = providerId,
                    email = displayEmail,
                    serverUrl = "https://www.mediafire.com/api/1.5",
                    tokenOrPass = tokenOrPass,
                    isTemporary = isTemporary,
                    totalSpace = totalBytes,
                    usedSpace = usedBytes
                )

                return@withContext CloudAuthResult(
                    success = true,
                    quotaTotalBytes = totalBytes,
                    quotaUsedBytes = usedBytes,
                    remoteFileCount = 0,
                    accountDisplayName = displayEmail
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val msg = "Erro de conexão com o MediaFire: ${e.message ?: "rede indisponível"}"
            syncError = msg
            Log.e("MediaFireProvider", msg, e)
            return@withContext CloudAuthResult(success = false, errorMessage = msg)
        }
    }

    override suspend fun disconnect() {
        directoryCache.clear()
        pathToKeyMap.clear()
        pathIsFolderMap.clear()
        syncError = null
        sessionManager.removeSession(providerId)
    }

    override fun invalidateCache() {
        directoryCache.clear()
    }

    private fun resolveFolderKey(cleanSub: String, token: String): String? {
        if (cleanSub.isBlank()) return "myfiles"
        pathToKeyMap[cleanSub]?.let { return it }

        var currentKey = "myfiles"
        val builtPath = StringBuilder()
        for (segment in cleanSub.split('/').filter { it.isNotBlank() }) {
            if (builtPath.isNotEmpty()) builtPath.append('/')
            builtPath.append(segment)
            val currentPath = builtPath.toString()
            val cachedKey = pathToKeyMap[currentPath]
            if (cachedKey != null) {
                currentKey = cachedKey
                continue
            }
            var foundKey: String? = null
            var chunk = 1
            var hasMore = true
            while (hasMore) {
                val url = "https://www.mediafire.com/api/1.5/folder/get_content.php" +
                    "?session_token=${URLEncoder.encode(token, "UTF-8")}" +
                    "&folder_key=${URLEncoder.encode(currentKey, "UTF-8")}" +
                    "&content_type=folders&chunk=$chunk&response_format=json"
                val req = Request.Builder().url(url).get().build()
                hasMore = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val mfResp = JSONObject(resp.body?.string().orEmpty()).optJSONObject("response") ?: return null
                    if (mfResp.optString("result") != "Success") return null
                    val fc = mfResp.optJSONObject("folder_content") ?: return null
                    val folders = fc.optJSONArray("folders")
                    if (folders != null) {
                        for (i in 0 until folders.length()) {
                            val obj = folders.getJSONObject(i)
                            val name = obj.optString("name")
                            val key = obj.optString("folderkey")
                            if (name.isNotBlank() && key.isNotBlank()) {
                                val segPath = if (currentPath == segment) name else "${currentPath.substringBeforeLast('/')}/$name"
                                pathToKeyMap[segPath] = key
                                pathIsFolderMap[segPath] = true
                                if (name.equals(segment, ignoreCase = false)) {
                                    foundKey = key
                                }
                            }
                        }
                    }
                    foundKey == null && fc.optString("more_chunks") == "yes"
                }
                if (hasMore) chunk++
            }
            currentKey = foundKey ?: return null
        }
        return currentKey
    }

    private fun resolveItemInfo(cleanSub: String, token: String): Pair<String, Boolean>? {
        val cachedKey = pathToKeyMap[cleanSub]
        val cachedIsFolder = pathIsFolderMap[cleanSub]
        if (cachedKey != null && cachedIsFolder != null) return cachedKey to cachedIsFolder
        val parentSub = cleanSub.substringBeforeLast('/', "")
        val itemName = cleanSub.substringAfterLast('/')
        val parentKey = resolveFolderKey(parentSub, token) ?: return null
        // Fetch folders and files in parentKey
        for (contentType in listOf("folders", "files")) {
            var chunk = 1
            var hasMore = true
            while (hasMore) {
                val url = "https://www.mediafire.com/api/1.5/folder/get_content.php" +
                    "?session_token=${URLEncoder.encode(token, "UTF-8")}" +
                    "&folder_key=${URLEncoder.encode(parentKey, "UTF-8")}" +
                    "&content_type=$contentType&chunk=$chunk&response_format=json"
                val req = Request.Builder().url(url).get().build()
                hasMore = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        false
                    } else {
                        val mfResp = JSONObject(resp.body?.string().orEmpty()).optJSONObject("response")
                        if (mfResp == null || mfResp.optString("result") != "Success") {
                            false
                        } else {
                            val fc = mfResp.optJSONObject("folder_content")
                            if (fc == null) {
                                false
                            } else {
                                val arr = fc.optJSONArray(contentType)
                                if (arr != null) {
                                    for (i in 0 until arr.length()) {
                                        val obj = arr.getJSONObject(i)
                                        val isDir = contentType == "folders"
                                        val name = if (isDir) obj.optString("name") else obj.optString("filename")
                                        val key = if (isDir) obj.optString("folderkey") else obj.optString("quickkey")
                                        if (name.isNotBlank() && key.isNotBlank()) {
                                            val rel = if (parentSub.isBlank()) name else "$parentSub/$name"
                                            pathToKeyMap[rel] = key
                                            pathIsFolderMap[rel] = isDir
                                            if (name == itemName) return key to isDir
                                        }
                                    }
                                }
                                fc.optString("more_chunks") == "yes"
                            }
                        }
                    }
                }
                if (hasMore) chunk++
            }
        }
        return null
    }

    override suspend fun listFiles(remoteSubPath: String): List<RemoteCloudFile> = withContext(Dispatchers.IO) {
        val cleanSub = cleanPath(remoteSubPath)
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
                    path = "/cloud/mediafire/$relativePath",
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
            syncError = "Sessão MediaFire inválida ou expirada."
            return@withContext emptyList()
        }

        val token = session.tokenOrPass
        try {
            val folderKey = resolveFolderKey(cleanSub, token)
            if (folderKey == null) {
                syncError = "Pasta não encontrada no MediaFire: /$cleanSub"
                return@withContext emptyList()
            }

            val items = mutableListOf<RemoteCloudFile>()
            for (contentType in listOf("folders", "files")) {
                var chunk = 1
                while (true) {
                    val url = "https://www.mediafire.com/api/1.5/folder/get_content.php" +
                        "?session_token=${URLEncoder.encode(token, "UTF-8")}" +
                        "&folder_key=${URLEncoder.encode(folderKey, "UTF-8")}" +
                        "&content_type=$contentType&chunk=$chunk&response_format=json"
                    val req = Request.Builder()
                        .url(url)
                        .get()
                        .header("User-Agent", "Arcbox-MediaFire-Client/2.4")
                        .build()

                    var hasMore = false
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            syncError = "Erro MediaFire HTTP ${resp.code}"
                            return@withContext emptyList()
                        }
                        val root = JSONObject(resp.body?.string().orEmpty())
                        val mfResp = root.optJSONObject("response")
                        if (mfResp?.optString("result") != "Success") {
                            syncError = mfResp?.optString("message")?.ifBlank { "Erro ao listar MediaFire" }
                                ?: "Erro ao listar MediaFire"
                            return@withContext emptyList()
                        }
                        val fc = mfResp.optJSONObject("folder_content")
                        val arr = fc?.optJSONArray(contentType)
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                val isDir = contentType == "folders"
                                val name = if (isDir) obj.optString("name") else obj.optString("filename")
                                val key = if (isDir) obj.optString("folderkey") else obj.optString("quickkey")
                                val size = if (isDir) obj.optString("byte_count").toLongOrNull() ?: 0L
                                else obj.optString("size").toLongOrNull() ?: 0L
                                val dateStr = obj.optString("created_utc").ifBlank { obj.optString("created") }
                                val mime = if (isDir) "resource/folder"
                                else obj.optString("mimetype").ifBlank { "application/octet-stream" }
                                if (name.isNotBlank()) {
                                    val rel = if (cleanSub.isBlank()) name else "$cleanSub/$name"
                                    if (key.isNotBlank()) {
                                        pathToKeyMap[rel] = key
                                        pathIsFolderMap[rel] = isDir
                                    }
                                    items.add(
                                        RemoteCloudFile(
                                            name = name,
                                            path = "/cloud/mediafire/$rel",
                                            isDirectory = isDir,
                                            size = size,
                                            lastModified = parseMediaFireDate(dateStr),
                                            mimeType = mime,
                                            remoteId = key.ifBlank { null }
                                        )
                                    )
                                }
                            }
                        }
                        hasMore = fc?.optString("more_chunks") == "yes"
                    }
                    if (!hasMore) break
                    chunk++
                }
            }

            syncError = null
            val sorted = items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            directoryCache[cleanSub] = CachedFolder(System.currentTimeMillis(), sorted)
            return@withContext sorted
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao listar MediaFire: ${e.message ?: "rede indisponível"}"
            Log.e("MediaFireProvider", "listFiles error", e)
            return@withContext emptyList()
        }
    }

    override suspend fun createFolder(remoteParentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val cleanParent = cleanPath(remoteParentPath)
        val session = sessionManager.getSession(providerId)

        if (isOfflineMode(session)) {
            val parentDir = if (cleanParent.isBlank()) getCacheDir() else File(getCacheDir(), cleanParent)
            val newFolder = File(parentDir, folderName)
            return@withContext newFolder.exists() || newFolder.mkdirs()
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão MediaFire inválida."
            return@withContext false
        }

        try {
            val parentKey = resolveFolderKey(cleanParent, session.tokenOrPass) ?: run {
                syncError = "Pasta de destino não encontrada no MediaFire."
                return@withContext false
            }
            val url = "https://www.mediafire.com/api/1.5/folder/create.php" +
                "?session_token=${URLEncoder.encode(session.tokenOrPass, "UTF-8")}" +
                "&foldername=${URLEncoder.encode(folderName, "UTF-8")}" +
                "&parent_key=${URLEncoder.encode(parentKey, "UTF-8")}" +
                "&response_format=json"
            val req = Request.Builder().url(url).post("".toRequestBody(null)).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao criar pasta no MediaFire."
                    return@withContext false
                }
                val mfResp = JSONObject(resp.body?.string().orEmpty()).optJSONObject("response")
                if (mfResp?.optString("result") == "Success") {
                    val key = mfResp.optString("folder_key")
                    if (key.isNotBlank()) {
                        val rel = if (cleanParent.isBlank()) folderName else "$cleanParent/$folderName"
                        pathToKeyMap[rel] = key
                        pathIsFolderMap[rel] = true
                    }
                    syncError = null
                    return@withContext true
                }
                syncError = mfResp?.optString("message")?.ifBlank { "Falha ao criar pasta no MediaFire." }
                return@withContext false
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao criar pasta no MediaFire: ${e.message}"
            return@withContext false
        }
    }

    override suspend fun uploadFile(
        localFile: File,
        remoteParentPath: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!localFile.exists() || !localFile.isFile) return@withContext false
        directoryCache.clear()
        val cleanParent = cleanPath(remoteParentPath)
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
                Log.e("MediaFireProvider", "Offline upload error", e)
                false
            }
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão MediaFire inválida."
            return@withContext false
        }

        try {
            val token = session.tokenOrPass
            val folderKey = resolveFolderKey(cleanParent, token) ?: run {
                syncError = "Pasta de destino não encontrada no MediaFire."
                return@withContext false
            }
            val totalBytes = localFile.length()
            val fileHash = sha256Hex(localFile)
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
                                onProgress((uploaded.toFloat() / totalBytes).coerceIn(0f, 0.95f))
                            }
                        }
                    }
                }
            }

            val uploadUrl = "https://www.mediafire.com/api/1.5/upload/simple.php" +
                "?session_token=${URLEncoder.encode(token, "UTF-8")}" +
                "&folder_key=${URLEncoder.encode(folderKey, "UTF-8")}" +
                "&action_on_duplicate=replace&response_format=json"
            val req = Request.Builder()
                .url(uploadUrl)
                .post(progressBody)
                .header("X-Filename", URLEncoder.encode(localFile.name, "UTF-8").replace("+", "%20"))
                .header("X-Filesize", totalBytes.toString())
                .header("X-Filehash", fileHash)
                .header("User-Agent", "Arcbox-MediaFire-Client/2.4")
                .build()

            var uploadKey: String? = null
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} no upload para MediaFire."
                    return@withContext false
                }
                val mfResp = JSONObject(resp.body?.string().orEmpty()).optJSONObject("response")
                if (mfResp?.optString("result") != "Success") {
                    syncError = mfResp?.optString("message")?.ifBlank { "Erro no upload MediaFire." }
                    return@withContext false
                }
                uploadKey = mfResp.optJSONObject("doupload")?.optString("key")
            }

            if (!uploadKey.isNullOrBlank()) {
                repeat(30) {
                    delay(500)
                    val pollUrl = "https://www.mediafire.com/api/1.5/upload/poll_upload.php" +
                        "?session_token=${URLEncoder.encode(token, "UTF-8")}" +
                        "&key=${URLEncoder.encode(uploadKey, "UTF-8")}&response_format=json"
                    val pollReq = Request.Builder().url(pollUrl).get().build()
                    client.newCall(pollReq).execute().use { pollResp ->
                        if (pollResp.isSuccessful) {
                            val mfPoll = JSONObject(pollResp.body?.string().orEmpty()).optJSONObject("response")
                            val doUp = mfPoll?.optJSONObject("doupload")
                            val status = doUp?.optInt("status", 0) ?: 0
                            val quickKey = doUp?.optString("quickkey").orEmpty()
                            if (status == 99 || quickKey.isNotBlank()) {
                                val rel = if (cleanParent.isBlank()) localFile.name else "$cleanParent/${localFile.name}"
                                if (quickKey.isNotBlank()) {
                                    pathToKeyMap[rel] = quickKey
                                    pathIsFolderMap[rel] = false
                                }
                                sessionManager.updateQuota(providerId, totalSpace, usedSpace + totalBytes)
                                syncError = null
                                onProgress(1f)
                                return@withContext true
                            }
                            if (status < 0) {
                                syncError = doUp?.optString("description")?.ifBlank { "Falha ao processar upload no MediaFire." }
                                return@withContext false
                            }
                        }
                    }
                }
            }
            syncError = null
            onProgress(1f)
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro no upload para MediaFire: ${e.message}"
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
            syncError = "Sessão MediaFire inválida."
            return@withContext false
        }

        try {
            val info = resolveItemInfo(cleanSub, session.tokenOrPass)
            if (info == null || info.second) {
                syncError = "Arquivo não encontrado no MediaFire: /$cleanSub"
                return@withContext false
            }
            val quickKey = info.first
            val linksUrl = "https://www.mediafire.com/api/1.5/file/get_links.php" +
                "?session_token=${URLEncoder.encode(session.tokenOrPass, "UTF-8")}" +
                "&quick_key=${URLEncoder.encode(quickKey, "UTF-8")}" +
                "&link_type=direct_download&response_format=json"
            val linksReq = Request.Builder().url(linksUrl).get().build()
            var directUrl: String? = null
            client.newCall(linksReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao obter link do MediaFire."
                    return@withContext false
                }
                val mfResp = JSONObject(resp.body?.string().orEmpty()).optJSONObject("response")
                val links = mfResp?.optJSONArray("links")
                if (links != null && links.length() > 0) {
                    val linkObj = links.getJSONObject(0)
                    directUrl = linkObj.optString("direct_download").ifBlank { linkObj.optString("normal_download") }
                }
            }
            if (directUrl.isNullOrBlank()) {
                syncError = "Link direto indisponível para este arquivo do MediaFire."
                return@withContext false
            }

            val dlReq = Request.Builder().url(directUrl!!).get().build()
            destinationFile.parentFile?.mkdirs()
            val tempDest = File(destinationFile.parentFile, "${destinationFile.name}.${System.currentTimeMillis()}.part")
            client.newCall(dlReq).execute().use { dlResp ->
                if (!dlResp.isSuccessful) {
                    syncError = "Erro HTTP ${dlResp.code} ao baixar do MediaFire."
                    return@withContext false
                }
                val totalBytes = dlResp.body?.contentLength() ?: -1L
                var bytesRead = 0L
                var lastProgress = -1f
                var lastTime = 0L
                dlResp.body?.byteStream()?.use { input ->
                    FileOutputStream(tempDest).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (totalBytes > 0) {
                                val p = (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                                val now = System.currentTimeMillis()
                                if (p >= 1f || p - lastProgress >= 0.01f || now - lastTime >= 100L) {
                                    lastProgress = p
                                    lastTime = now
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
            syncError = "Erro ao baixar do MediaFire: ${e.message}"
            Log.e("MediaFireProvider", "Download error", e)
            false
        }
    }

    override suspend fun deleteFile(remoteFilePath: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val cleanSub = cleanPath(remoteFilePath)
        val session = sessionManager.getSession(providerId)

        if (isOfflineMode(session)) {
            val file = File(getCacheDir(), cleanSub)
            return@withContext if (file.exists()) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            } else false
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão MediaFire inválida."
            return@withContext false
        }

        try {
            val info = resolveItemInfo(cleanSub, session.tokenOrPass) ?: return@withContext false
            val (key, isDir) = info
            val endpoint = if (isDir) "folder/delete.php" else "file/delete.php"
            val paramName = if (isDir) "folder_key" else "quick_key"
            val url = "https://www.mediafire.com/api/1.5/$endpoint" +
                "?session_token=${URLEncoder.encode(session.tokenOrPass, "UTF-8")}" +
                "&$paramName=${URLEncoder.encode(key, "UTF-8")}&response_format=json"
            val req = Request.Builder().url(url).post("".toRequestBody(null)).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao excluir no MediaFire."
                    return@withContext false
                }
                val mfResp = JSONObject(resp.body?.string().orEmpty()).optJSONObject("response")
                if (mfResp?.optString("result") == "Success") {
                    pathToKeyMap.remove(cleanSub)
                    pathIsFolderMap.remove(cleanSub)
                    val cached = File(getCacheDir(), cleanSub)
                    if (cached.exists()) {
                        if (cached.isDirectory) cached.deleteRecursively() else cached.delete()
                    }
                    syncError = null
                    return@withContext true
                }
                syncError = mfResp?.optString("message")?.ifBlank { "Erro ao excluir no MediaFire." }
                false
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao excluir no MediaFire: ${e.message}"
            false
        }
    }

    override suspend fun renameFile(oldRemotePath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val cleanSub = cleanPath(oldRemotePath)
        val session = sessionManager.getSession(providerId)

        if (isOfflineMode(session)) {
            val file = File(getCacheDir(), cleanSub)
            if (!file.exists()) return@withContext false
            return@withContext file.renameTo(File(file.parentFile, newName))
        }

        if (session == null || !isRemoteToken(session.tokenOrPass)) {
            syncError = "Sessão MediaFire inválida."
            return@withContext false
        }

        try {
            val info = resolveItemInfo(cleanSub, session.tokenOrPass) ?: return@withContext false
            val (key, isDir) = info
            val endpoint = if (isDir) "folder/update.php" else "file/update.php"
            val keyParam = if (isDir) "folder_key" else "quick_key"
            val nameParam = if (isDir) "foldername" else "filename"
            val url = "https://www.mediafire.com/api/1.5/$endpoint" +
                "?session_token=${URLEncoder.encode(session.tokenOrPass, "UTF-8")}" +
                "&$keyParam=${URLEncoder.encode(key, "UTF-8")}" +
                "&$nameParam=${URLEncoder.encode(newName, "UTF-8")}&response_format=json"
            val req = Request.Builder().url(url).post("".toRequestBody(null)).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    syncError = "Erro HTTP ${resp.code} ao renomear no MediaFire."
                    return@withContext false
                }
                val mfResp = JSONObject(resp.body?.string().orEmpty()).optJSONObject("response")
                if (mfResp?.optString("result") == "Success") {
                    pathToKeyMap.remove(cleanSub)
                    pathIsFolderMap.remove(cleanSub)
                    val parentSub = cleanSub.substringBeforeLast('/', "")
                    val newSub = if (parentSub.isBlank()) newName else "$parentSub/$newName"
                    pathToKeyMap[newSub] = key
                    pathIsFolderMap[newSub] = isDir
                    syncError = null
                    return@withContext true
                }
                syncError = mfResp?.optString("message")?.ifBlank { "Erro ao renomear no MediaFire." }
                false
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao renomear no MediaFire: ${e.message}"
            false
        }
    }

    override suspend fun copyOrMoveFile(
        sourceRemotePath: String,
        destRemotePath: String,
        isMove: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val cleanSrc = cleanPath(sourceRemotePath)
        val cleanDest = cleanPath(destRemotePath)
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
            syncError = "Sessão MediaFire inválida."
            return@withContext false
        }

        try {
            val info = resolveItemInfo(cleanSrc, session.tokenOrPass) ?: return@withContext false
            val (srcKey, isDir) = info
            val destParentSub = cleanDest.substringBeforeLast('/', "")
            val destName = cleanDest.substringAfterLast('/')
            val srcName = cleanSrc.substringAfterLast('/')
            val destParentKey = resolveFolderKey(destParentSub, session.tokenOrPass) ?: return@withContext false

            if (isDir) {
                if (isMove) {
                    val url = "https://www.mediafire.com/api/1.5/folder/move.php" +
                        "?session_token=${URLEncoder.encode(session.tokenOrPass, "UTF-8")}" +
                        "&folder_key_src=${URLEncoder.encode(srcKey, "UTF-8")}" +
                        "&folder_key_dst=${URLEncoder.encode(destParentKey, "UTF-8")}&response_format=json"
                    client.newCall(Request.Builder().url(url).post("".toRequestBody(null)).build()).execute().use { resp ->
                        val mfResp = JSONObject(resp.body?.string().orEmpty()).optJSONObject("response")
                        val ok = resp.isSuccessful && mfResp?.optString("result") == "Success"
                        if (ok && destName != srcName) {
                            val movedPath = if (destParentSub.isBlank()) srcName else "$destParentSub/$srcName"
                            pathToKeyMap[movedPath] = srcKey
                            pathIsFolderMap[movedPath] = true
                            return@withContext renameFile(movedPath, destName)
                        }
                        return@withContext ok
                    }
                } else {
                    if (!createFolder(destParentSub, destName)) return@withContext false
                    val children = listFiles(cleanSrc)
                    for (child in children) {
                        val ok = copyOrMoveFile(child.path, "$cleanDest/${child.name}", false)
                        if (!ok) return@withContext false
                    }
                    return@withContext true
                }
            } else {
                val endpoint = if (isMove) "file/move.php" else "file/copy.php"
                val url = "https://www.mediafire.com/api/1.5/$endpoint" +
                    "?session_token=${URLEncoder.encode(session.tokenOrPass, "UTF-8")}" +
                    "&quick_key=${URLEncoder.encode(srcKey, "UTF-8")}" +
                    "&folder_key=${URLEncoder.encode(destParentKey, "UTF-8")}&response_format=json"
                client.newCall(Request.Builder().url(url).post("".toRequestBody(null)).build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        syncError = "Erro HTTP ${resp.code} ao ${if (isMove) "mover" else "copiar"} no MediaFire."
                        return@withContext false
                    }
                    val mfResp = JSONObject(resp.body?.string().orEmpty()).optJSONObject("response")
                    if (mfResp?.optString("result") != "Success") {
                        syncError = mfResp?.optString("message")?.ifBlank { "Erro na operação no MediaFire." }
                        return@withContext false
                    }
                    if (destName != srcName) {
                        val intermediatePath = if (destParentSub.isBlank()) srcName else "$destParentSub/$srcName"
                        val newQuickKey = mfResp.optString("new_quickkey").ifBlank { srcKey }
                        pathToKeyMap[intermediatePath] = newQuickKey
                        pathIsFolderMap[intermediatePath] = false
                        return@withContext renameFile(intermediatePath, destName)
                    }
                    syncError = null
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao ${if (isMove) "mover" else "copiar"} no MediaFire: ${e.message}"
            false
        }
    }

    override suspend fun getShareLink(remoteFilePath: String): String? = withContext(Dispatchers.IO) {
        val cleanSub = cleanPath(remoteFilePath)
        val session = sessionManager.getSession(providerId) ?: return@withContext null
        if (isOfflineMode(session) || !isRemoteToken(session.tokenOrPass)) return@withContext null
        return@withContext try {
            val info = resolveItemInfo(cleanSub, session.tokenOrPass) ?: return@withContext null
            if (info.second) return@withContext null
            val url = "https://www.mediafire.com/api/1.5/file/get_links.php" +
                "?session_token=${URLEncoder.encode(session.tokenOrPass, "UTF-8")}" +
                "&quick_key=${URLEncoder.encode(info.first, "UTF-8")}" +
                "&link_type=normal_download&response_format=json"
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val links = JSONObject(resp.body?.string().orEmpty())
                    .optJSONObject("response")
                    ?.optJSONArray("links")
                if (links != null && links.length() > 0) {
                    links.getJSONObject(0).optString("normal_download").ifBlank { null }
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
