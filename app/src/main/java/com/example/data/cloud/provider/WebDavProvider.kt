package com.example.data.cloud.provider

import android.content.Context
import android.util.Log
import com.example.data.cloud.CloudAuthResult
import com.example.data.cloud.RemoteCloudFile
import kotlinx.coroutines.*
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

class WebDavProvider(
    private val context: Context,
    private val client: OkHttpClient,
    private val sessionManager: CloudSessionManager
) : CloudStorageProvider {

    override val providerId: String = "webdav"
    override val displayName: String = "WebDAV / Servidor"
    override val defaultPath: String = "/cloud/webdav"

    override val isConnected: Boolean
        get() = sessionManager.isConnected(providerId)

    override val accountEmail: String?
        get() = sessionManager.getSession(providerId)?.email

    override val totalSpace: Long
        get() = sessionManager.getSession(providerId)?.totalSpace ?: (100L * 1024 * 1024 * 1024)

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

    private val directoryCache = java.util.concurrent.ConcurrentHashMap<String, CachedFolder>()
    private val CACHE_TTL_MS = 600_000L

    private fun getCacheDir(): File = File(context.cacheDir, "cloud_storage/webdav")

    private fun isOfflineMode(session: CloudSessionManager.CloudSessionData?): Boolean =
        session?.tokenOrPass == "local_webdav"

    private fun cleanSubPath(rawPath: String): String =
        rawPath.trim().replace(Regex("(?i)^/?cloud/webdav/?"), "").trim('/')

    private fun buildWebDavUrl(baseUrl: String, subPath: String, userEmail: String = ""): String {
        var cleanBase = if (baseUrl.isBlank()) {
            "https://cloud.nextcloud.com/remote.php/dav/files/${userEmail.ifBlank { "usuario" }}"
        } else if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            "https://$baseUrl"
        } else {
            baseUrl
        }
        if (cleanBase.contains("nextcloud", ignoreCase = true) && !cleanBase.contains("/remote.php/dav/files/")) {
            val user = userEmail.ifBlank { "usuario" }
            cleanBase = "${cleanBase.trimEnd('/')}/remote.php/dav/files/$user"
        }
        val baseWithoutTrailing = cleanBase.trimEnd('/')
        val cleanSub = cleanSubPath(subPath)
            .split("/").filter { it.isNotBlank() }
            .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return if (cleanSub.isEmpty()) "$baseWithoutTrailing/" else "$baseWithoutTrailing/$cleanSub"
    }

    override suspend fun authenticate(
        email: String,
        serverUrl: String,
        tokenOrPass: String,
        isTemporary: Boolean
    ): CloudAuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val targetUrl = buildWebDavUrl(serverUrl, "", cleanEmail)
        val credentials = Credentials.basic(cleanEmail, tokenOrPass)
        val propfindXml = """<?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
                <d:prop>
                    <d:displayname />
                    <d:getcontentlength />
                    <d:getlastmodified />
                    <d:resourcetype />
                    <d:quota-available-bytes />
                    <d:quota-used-bytes />
                </d:prop>
            </d:propfind>""".trimIndent()

        val request = Request.Builder()
            .url(targetUrl)
            .method("PROPFIND", propfindXml.toRequestBody("application/xml".toMediaTypeOrNull()))
            .header("Depth", "1")
            .header("Authorization", credentials)
            .header("User-Agent", "Arcbox-WebDAV-Client/2.4")
            .build()

        val cloudDir = getCacheDir()
        ensureInitialWorkspace(cloudDir, "WebDAV", cleanEmail)

        if (tokenOrPass == "local_webdav") {
            val totalQuota = 100L * 1024 * 1024 * 1024
            val usedQuota = getFolderSize(cloudDir)
            sessionManager.saveSession(
                providerId = providerId,
                email = cleanEmail.ifBlank { "local@webdav.storage" },
                serverUrl = serverUrl,
                tokenOrPass = "local_webdav",
                isTemporary = isTemporary,
                totalSpace = totalQuota,
                usedSpace = usedQuota
            )
            syncError = null
            return@withContext CloudAuthResult(
                success = true,
                quotaTotalBytes = totalQuota,
                quotaUsedBytes = usedQuota,
                remoteFileCount = countFiles(cloudDir),
                accountDisplayName = cleanEmail.ifBlank { "local@webdav.storage" }
            )
        }

        if (tokenOrPass == "direct_cloud_session" || tokenOrPass.startsWith("web_auth_token_") || tokenOrPass.startsWith("web_session_")) {
            return@withContext CloudAuthResult(
                success = false,
                errorMessage = "Não foi possível capturar suas credenciais WebDAV na tela web. Use E-mail/Usuário e Senha (ou App Password)."
            )
        }

        try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                if (code == 401 || code == 403) {
                    return@withContext CloudAuthResult(
                        success = false,
                        errorMessage = "Credenciais WebDAV recusadas (HTTP $code - Não autorizado)."
                    )
                }
                if (!response.isSuccessful && code != 207) {
                    return@withContext CloudAuthResult(
                        success = false,
                        errorMessage = "Servidor WebDAV respondeu HTTP $code. Verifique a URL do servidor."
                    )
                }

                val respXml = response.body?.string() ?: ""
                val (parsedUsed, parsedAvailable) = parseQuotaFromPropfind(respXml)
                val totalQuota = if (parsedAvailable > 0L || parsedUsed > 0L) (parsedUsed + parsedAvailable) else (100L * 1024 * 1024 * 1024)
                val usedQuota = if (parsedUsed > 0L) parsedUsed else 0L

                syncError = null
                sessionManager.saveSession(
                    providerId = providerId,
                    email = cleanEmail,
                    serverUrl = serverUrl,
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
        } catch (e: Exception) {
            return@withContext CloudAuthResult(
                success = false,
                errorMessage = "Não foi possível conectar ao servidor WebDAV: ${e.localizedMessage ?: e.message}"
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
        val cacheKey = cleanSub
        val cached = directoryCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS)) {
            return@withContext cached.items
        }

        val session = sessionManager.getSession(providerId)
        val cloudDir = getCacheDir()
        val targetLocalDir = if (cleanSub.isBlank()) cloudDir else File(cloudDir, cleanSub)
        if (!targetLocalDir.exists()) targetLocalDir.mkdirs()

        if (isOfflineMode(session)) {
            syncError = null
            val files = targetLocalDir.listFiles() ?: return@withContext emptyList()
            val result = files.map { file ->
                val relativePath = if (cleanSub.isBlank()) file.name else "$cleanSub/${file.name}"
                RemoteCloudFile(
                    name = file.name,
                    path = "/cloud/webdav/$relativePath",
                    isDirectory = file.isDirectory,
                    size = if (file.isDirectory) getFolderSize(file) else file.length(),
                    lastModified = file.lastModified(),
                    mimeType = if (file.isDirectory) "resource/folder" else "application/octet-stream"
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            directoryCache[cacheKey] = CachedFolder(System.currentTimeMillis(), result)
            return@withContext result
        }

        if (session == null || session.serverUrl.isBlank() || session.tokenOrPass.isBlank()) {
            syncError = "Sessão WebDAV ausente. Conecte sua conta novamente."
            return@withContext emptyList()
        }

        val targetUrl = buildWebDavUrl(session.serverUrl, cleanSub, session.email)
        val credentials = Credentials.basic(session.email, session.tokenOrPass)
        val propfindXml = """<?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
                <d:prop>
                    <d:displayname />
                    <d:getcontentlength />
                    <d:getlastmodified />
                    <d:resourcetype />
                </d:prop>
            </d:propfind>""".trimIndent()

        val request = Request.Builder()
            .url(targetUrl)
            .method("PROPFIND", propfindXml.toRequestBody("application/xml".toMediaTypeOrNull()))
            .header("Depth", "1")
            .header("Authorization", credentials)
            .header("User-Agent", "Arcbox-WebDAV-Client/2.4")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 207) {
                    val responseBody = response.body?.string() ?: ""
                    val parsedItems = parseWebDavPropfindResponse(responseBody, targetUrl, cleanSub)
                    syncError = null
                    directoryCache[cacheKey] = CachedFolder(System.currentTimeMillis(), parsedItems)
                    return@withContext parsedItems
                } else {
                    syncError = if (response.code == 401 || response.code == 403) {
                        "Credenciais WebDAV expiradas ou inválidas (HTTP ${response.code})."
                    } else {
                        "Falha ao listar servidor WebDAV (HTTP ${response.code})."
                    }
                    return@withContext emptyList()
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro de conexão WebDAV: ${e.localizedMessage ?: e.message}"
            Log.w("WebDavProvider", "Network PROPFIND failed: ${e.message}")
            return@withContext emptyList()
        }
    }

    private fun parseWebDavPropfindResponse(xml: String, requestUrl: String, cleanSub: String): List<RemoteCloudFile> {
        val result = mutableListOf<RemoteCloudFile>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentHref = ""
            var currentDisplayName = ""
            var contentLength = 0L
            var lastModified = 0L
            var isCollection = false
            var inResponse = false

            val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "response" -> {
                                inResponse = true
                                currentHref = ""
                                currentDisplayName = ""
                                contentLength = 0L
                                lastModified = System.currentTimeMillis()
                                isCollection = false
                            }
                            "href" -> {
                                if (inResponse) currentHref = parser.nextText().trim()
                            }
                            "displayname" -> {
                                if (inResponse) currentDisplayName = parser.nextText().trim()
                            }
                            "getcontentlength" -> {
                                if (inResponse) {
                                    contentLength = parser.nextText().trim().toLongOrNull() ?: 0L
                                }
                            }
                            "getlastmodified" -> {
                                if (inResponse) {
                                    val dateStr = parser.nextText().trim()
                                    try {
                                        lastModified = dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                                    } catch (_: Exception) {}
                                }
                            }
                            "collection" -> {
                                if (inResponse) isCollection = true
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag == "response") {
                            inResponse = false
                            val hrefPath = try {
                                val rawPath = if (currentHref.startsWith("http://") || currentHref.startsWith("https://")) {
                                    java.net.URI(currentHref).path ?: currentHref
                                } else {
                                    currentHref
                                }
                                java.net.URLDecoder.decode(rawPath, "UTF-8").trimEnd('/')
                            } catch (_: Exception) { currentHref.trimEnd('/') }

                            val reqPath = try {
                                val rawPath = java.net.URI(requestUrl).path ?: requestUrl
                                java.net.URLDecoder.decode(rawPath, "UTF-8").trimEnd('/')
                            } catch (_: Exception) { requestUrl.trimEnd('/') }

                            // Skip self response accurately by normalized path comparison
                            if (hrefPath.isNotBlank() && !hrefPath.equals(reqPath, ignoreCase = true)) {
                                val name = if (currentDisplayName.isNotBlank()) {
                                    currentDisplayName
                                } else {
                                    hrefPath.substringAfterLast('/')
                                }
                                if (name.isNotBlank()) {
                                    val relativePath = if (cleanSub.isBlank()) name else "$cleanSub/$name"
                                    result.add(
                                        RemoteCloudFile(
                                            name = name,
                                            path = "/cloud/webdav/$relativePath",
                                            isDirectory = isCollection,
                                            size = contentLength,
                                            lastModified = lastModified,
                                            mimeType = if (isCollection) "resource/folder" else "application/octet-stream",
                                            remoteId = currentHref
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w("WebDavProvider", "XML parse error: ${e.message}")
        }
        return result.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    private fun parseQuotaFromPropfind(xml: String): Pair<Long, Long> {
        var used = 0L
        var available = 0L
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name?.lowercase()) {
                        "quota-used-bytes" -> used = parser.nextText().trim().toLongOrNull() ?: used
                        "quota-available-bytes" -> available = parser.nextText().trim().toLongOrNull() ?: available
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {}
        return Pair(used, available)
    }

    override suspend fun createFolder(remoteParentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val session = sessionManager.getSession(providerId)
        val cleanParent = cleanSubPath(remoteParentPath)
        val parentDir = if (cleanParent.isBlank()) getCacheDir() else File(getCacheDir(), cleanParent)
        val newFolder = File(parentDir, folderName)

        if (isOfflineMode(session)) {
            return@withContext newFolder.mkdirs()
        }
        if (session == null || session.serverUrl.isBlank() || session.tokenOrPass.isBlank()) {
            syncError = "Sessão WebDAV ausente."
            return@withContext false
        }

        val sub = if (cleanParent.isBlank()) folderName else "$cleanParent/$folderName"
        val targetUrl = buildWebDavUrl(session.serverUrl, sub, session.email)
        val credentials = Credentials.basic(session.email, session.tokenOrPass)
        val request = Request.Builder()
            .url(targetUrl)
            .method("MKCOL", "".toRequestBody("text/plain".toMediaTypeOrNull()))
            .header("Authorization", credentials)
            .header("User-Agent", "Arcbox-WebDAV-Client/2.4")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val ok = resp.isSuccessful || resp.code == 201 || resp.code == 405
                if (ok) {
                    newFolder.mkdirs()
                    syncError = null
                } else {
                    syncError = "Erro ao criar pasta WebDAV (HTTP ${resp.code})."
                }
                return@withContext ok
            }
        } catch (e: Exception) {
            syncError = "Erro ao criar pasta WebDAV: ${e.message}"
            Log.w("WebDavProvider", "Remote MKCOL failed: ${e.message}")
            return@withContext false
        }
    }

    override suspend fun uploadFile(
        localFile: File,
        remoteParentPath: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!localFile.exists()) return@withContext false
        directoryCache.clear()
        val session = sessionManager.getSession(providerId)
        val cleanParent = cleanSubPath(remoteParentPath)
        val destDir = if (cleanParent.isBlank()) getCacheDir() else File(getCacheDir(), cleanParent)
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = File(destDir, localFile.name)

        if (isOfflineMode(session)) {
            return@withContext try {
                localFile.copyTo(destFile, overwrite = true)
                onProgress(1f)
                sessionManager.updateQuota(providerId, totalSpace, usedSpace + destFile.length())
                true
            } catch (_: Exception) { false }
        }

        if (session == null || session.serverUrl.isBlank() || session.tokenOrPass.isBlank()) {
            syncError = "Sessão WebDAV ausente."
            return@withContext false
        }

        val sub = if (cleanParent.isBlank()) localFile.name else "$cleanParent/${localFile.name}"
        val targetUrl = buildWebDavUrl(session.serverUrl, sub, session.email)
        val credentials = Credentials.basic(session.email, session.tokenOrPass)
        val request = Request.Builder()
            .url(targetUrl)
            .put(localFile.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
            .header("Authorization", credentials)
            .header("User-Agent", "Arcbox-WebDAV-Client/2.4")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val ok = resp.isSuccessful || resp.code == 201 || resp.code == 204
                if (ok) {
                    try { localFile.copyTo(destFile, overwrite = true) } catch (_: Exception) {}
                    onProgress(1f)
                    sessionManager.updateQuota(providerId, totalSpace, usedSpace + localFile.length())
                    syncError = null
                } else {
                    syncError = "Erro no upload WebDAV (HTTP ${resp.code})."
                }
                return@withContext ok
            }
        } catch (e: Exception) {
            syncError = "Erro no upload WebDAV: ${e.message}"
            Log.w("WebDavProvider", "Remote PUT failed: ${e.message}")
            return@withContext false
        }
    }

    override suspend fun downloadFile(
        remoteFilePath: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val session = sessionManager.getSession(providerId)
        val cleanSub = cleanSubPath(remoteFilePath)

        if (isOfflineMode(session)) {
            val srcFile = File(getCacheDir(), cleanSub)
            if (!srcFile.exists()) return@withContext false
            return@withContext try {
                destinationFile.parentFile?.mkdirs()
                srcFile.copyTo(destinationFile, overwrite = true)
                onProgress(1f)
                true
            } catch (_: Exception) { false }
        }

        if (session == null || session.serverUrl.isBlank() || session.tokenOrPass.isBlank()) {
            syncError = "Sessão WebDAV ausente."
            return@withContext false
        }

        val targetUrl = buildWebDavUrl(session.serverUrl, cleanSub, session.email)
        val credentials = Credentials.basic(session.email, session.tokenOrPass)
        val request = Request.Builder()
            .url(targetUrl)
            .get()
            .header("Authorization", credentials)
            .header("User-Agent", "Arcbox-WebDAV-Client/2.4")
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
                    syncError = null
                    onProgress(1f)
                    return@withContext true
                } else {
                    syncError = "Erro ao baixar do WebDAV (HTTP ${resp.code})."
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncError = "Erro ao baixar do WebDAV: ${e.message}"
            Log.w("WebDavProvider", "Remote GET failed: ${e.message}")
            return@withContext false
        }
    }

    override suspend fun deleteFile(remoteFilePath: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val session = sessionManager.getSession(providerId)
        val cleanSub = cleanSubPath(remoteFilePath)
        val file = File(getCacheDir(), cleanSub)

        if (isOfflineMode(session)) {
            return@withContext if (file.exists()) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            } else false
        }

        if (session == null || session.serverUrl.isBlank() || session.tokenOrPass.isBlank()) {
            syncError = "Sessão WebDAV ausente."
            return@withContext false
        }

        val targetUrl = buildWebDavUrl(session.serverUrl, cleanSub, session.email)
        val credentials = Credentials.basic(session.email, session.tokenOrPass)
        val request = Request.Builder()
            .url(targetUrl)
            .delete()
            .header("Authorization", credentials)
            .header("User-Agent", "Arcbox-WebDAV-Client/2.4")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val ok = resp.isSuccessful || resp.code == 204
                if (ok) {
                    if (file.exists()) {
                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                    }
                    syncError = null
                } else {
                    syncError = "Erro ao excluir no WebDAV (HTTP ${resp.code})."
                }
                return@withContext ok
            }
        } catch (e: Exception) {
            syncError = "Erro ao excluir no WebDAV: ${e.message}"
            Log.w("WebDavProvider", "Remote DELETE failed: ${e.message}")
            return@withContext false
        }
    }

    override suspend fun renameFile(oldRemotePath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val session = sessionManager.getSession(providerId)
        val cleanOld = cleanSubPath(oldRemotePath)
        val file = File(getCacheDir(), cleanOld)

        if (isOfflineMode(session)) {
            return@withContext if (file.exists()) file.renameTo(File(file.parentFile, newName)) else false
        }

        if (session == null || session.serverUrl.isBlank() || session.tokenOrPass.isBlank()) {
            syncError = "Sessão WebDAV ausente."
            return@withContext false
        }

        val targetUrl = buildWebDavUrl(session.serverUrl, cleanOld, session.email)
        val parentPath = if (cleanOld.contains('/')) cleanOld.substringBeforeLast('/') else ""
        val newSub = if (parentPath.isBlank()) newName else "$parentPath/$newName"
        val destUrl = buildWebDavUrl(session.serverUrl, newSub, session.email)
        val credentials = Credentials.basic(session.email, session.tokenOrPass)
        val request = Request.Builder()
            .url(targetUrl)
            .method("MOVE", "".toRequestBody("text/plain".toMediaTypeOrNull()))
            .header("Destination", destUrl)
            .header("Overwrite", "T")
            .header("Authorization", credentials)
            .header("User-Agent", "Arcbox-WebDAV-Client/2.4")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val ok = resp.isSuccessful || resp.code == 201 || resp.code == 204
                if (ok) {
                    if (file.exists()) file.renameTo(File(file.parentFile, newName))
                    syncError = null
                } else {
                    syncError = "Erro ao renomear no WebDAV (HTTP ${resp.code})."
                }
                return@withContext ok
            }
        } catch (e: Exception) {
            syncError = "Erro ao renomear no WebDAV: ${e.message}"
            Log.w("WebDavProvider", "Remote MOVE failed: ${e.message}")
            return@withContext false
        }
    }

    override suspend fun copyOrMoveFile(
        sourceRemotePath: String,
        destRemotePath: String,
        isMove: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        directoryCache.clear()
        val session = sessionManager.getSession(providerId)
        val cleanSrc = cleanSubPath(sourceRemotePath)
        val cleanDest = cleanSubPath(destRemotePath)

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
            } catch (_: Exception) { false }
        }

        if (session == null || session.serverUrl.isBlank() || session.tokenOrPass.isBlank()) {
            syncError = "Sessão WebDAV ausente."
            return@withContext false
        }

        val srcUrl = buildWebDavUrl(session.serverUrl, cleanSrc, session.email)
        val destUrl = buildWebDavUrl(session.serverUrl, cleanDest, session.email)
        val credentials = Credentials.basic(session.email, session.tokenOrPass)
        val method = if (isMove) "MOVE" else "COPY"
        val request = Request.Builder()
            .url(srcUrl)
            .method(method, "".toRequestBody("text/plain".toMediaTypeOrNull()))
            .header("Destination", destUrl)
            .header("Overwrite", "T")
            .header("Authorization", credentials)
            .header("User-Agent", "Arcbox-WebDAV-Client/2.4")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val ok = resp.isSuccessful || resp.code == 201 || resp.code == 204
                if (ok) {
                    val srcLocal = File(getCacheDir(), cleanSrc)
                    val destLocal = File(getCacheDir(), cleanDest)
                    if (srcLocal.exists()) {
                        destLocal.parentFile?.mkdirs()
                        if (isMove) srcLocal.renameTo(destLocal) else {
                            try {
                                if (srcLocal.isDirectory) srcLocal.copyRecursively(destLocal, overwrite = true)
                                else srcLocal.copyTo(destLocal, overwrite = true)
                            } catch (_: Exception) {}
                        }
                    }
                    syncError = null
                } else {
                    syncError = "Erro ao ${if (isMove) "mover" else "copiar"} no WebDAV (HTTP ${resp.code})."
                }
                return@withContext ok
            }
        } catch (e: Exception) {
            syncError = "Erro no $method WebDAV: ${e.message}"
            return@withContext false
        }
    }

    override suspend fun getShareLink(remoteFilePath: String): String? {
        return null
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
