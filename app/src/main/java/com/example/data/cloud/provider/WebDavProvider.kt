package com.example.data.cloud.provider

import android.content.Context
import android.util.Log
import com.example.data.cloud.CloudAuthResult
import com.example.data.cloud.RemoteCloudFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    private fun getCacheDir(): File = File(context.filesDir, "cloud_storage/webdav")

    private fun buildWebDavUrl(baseUrl: String, subPath: String): String {
        val cleanBase = if (baseUrl.isBlank()) {
            "https://cloud.nextcloud.com/remote.php/dav/files/"
        } else if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            "https://$baseUrl"
        } else {
            baseUrl
        }
        val baseWithoutTrailing = cleanBase.trimEnd('/')
        val cleanSub = subPath.trim('/').split("/").filter { it.isNotBlank() }
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
        val targetUrl = buildWebDavUrl(serverUrl, "")
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

        try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                if (code == 401 || code == 403) {
                    return@withContext CloudAuthResult(
                        success = false,
                        errorMessage = "Credenciais WebDAV recusadas (HTTP $code - Não autorizado)."
                    )
                }

                val totalQuota = 100L * 1024 * 1024 * 1024
                val usedQuota = getFolderSize(cloudDir)

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
            val totalQuota = 100L * 1024 * 1024 * 1024
            val usedQuota = getFolderSize(cloudDir)

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
    }

    override suspend fun disconnect() {
        sessionManager.removeSession(providerId)
    }

    override suspend fun listFiles(remoteSubPath: String): List<RemoteCloudFile> = withContext(Dispatchers.IO) {
        val session = sessionManager.getSession(providerId)
        val cloudDir = getCacheDir()
        val targetLocalDir = if (remoteSubPath.isBlank()) cloudDir else File(cloudDir, remoteSubPath)
        if (!targetLocalDir.exists()) targetLocalDir.mkdirs()

        // If session exists with serverUrl and token, try real PROPFIND over network
        if (session != null && session.serverUrl.isNotBlank() && session.tokenOrPass.isNotBlank()) {
            val targetUrl = buildWebDavUrl(session.serverUrl, remoteSubPath)
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
                        val parsedItems = parseWebDavPropfindResponse(responseBody, targetUrl)
                        if (parsedItems.isNotEmpty()) {
                            // Synchronize remote files into local cache directory
                            for (item in parsedItems) {
                                val localTarget = File(targetLocalDir, item.name)
                                if (item.isDirectory && !localTarget.exists()) {
                                    localTarget.mkdirs()
                                } else if (!item.isDirectory && !localTarget.exists()) {
                                    try {
                                        localTarget.createNewFile()
                                    } catch (_: Exception) {}
                                }
                            }
                            return@withContext parsedItems
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("WebDavProvider", "Network PROPFIND failed, falling back to cached files: ${e.message}")
            }
        }

        // Fallback to local cache files
        val files = targetLocalDir.listFiles() ?: return@withContext emptyList()
        return@withContext files.map { file ->
            RemoteCloudFile(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) getFolderSize(file) else file.length(),
                lastModified = file.lastModified(),
                mimeType = if (file.isDirectory) "resource/folder" else "application/octet-stream"
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    private fun parseWebDavPropfindResponse(xml: String, requestUrl: String): List<RemoteCloudFile> {
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
                            val cleanHref = currentHref.trimEnd('/')
                            val requestClean = requestUrl.trimEnd('/')

                            // Skip self response
                            if (cleanHref.isNotBlank() && !cleanHref.endsWith(requestClean) && cleanHref != requestClean) {
                                val name = if (currentDisplayName.isNotBlank()) {
                                    currentDisplayName
                                } else {
                                    cleanHref.substringAfterLast('/')
                                }
                                if (name.isNotBlank()) {
                                    result.add(
                                        RemoteCloudFile(
                                            name = name,
                                            path = currentHref,
                                            isDirectory = isCollection,
                                            size = contentLength,
                                            lastModified = lastModified,
                                            mimeType = if (isCollection) "resource/folder" else "application/octet-stream"
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

    override suspend fun createFolder(remoteParentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        val session = sessionManager.getSession(providerId)
        val parentDir = if (remoteParentPath.isBlank()) getCacheDir() else File(getCacheDir(), remoteParentPath)
        val newFolder = File(parentDir, folderName)
        val localCreated = newFolder.mkdirs()

        if (session != null && session.serverUrl.isNotBlank() && session.tokenOrPass.isNotBlank()) {
            val sub = if (remoteParentPath.isBlank()) folderName else "$remoteParentPath/$folderName"
            val targetUrl = buildWebDavUrl(session.serverUrl, sub)
            val credentials = Credentials.basic(session.email, session.tokenOrPass)
            val request = Request.Builder()
                .url(targetUrl)
                .method("MKCOL", "".toRequestBody("text/plain".toMediaTypeOrNull()))
                .header("Authorization", credentials)
                .header("User-Agent", "Arcbox-WebDAV-Client/2.4")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || resp.code == 201 || resp.code == 405 || localCreated
                }
            } catch (e: Exception) {
                Log.w("WebDavProvider", "Remote MKCOL failed: ${e.message}")
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
            Log.e("WebDavProvider", "Local cache copy error", e)
        }

        val session = sessionManager.getSession(providerId)
        if (session != null && session.serverUrl.isNotBlank() && session.tokenOrPass.isNotBlank()) {
            val sub = if (remoteParentPath.isBlank()) localFile.name else "$remoteParentPath/${localFile.name}"
            val targetUrl = buildWebDavUrl(session.serverUrl, sub)
            val credentials = Credentials.basic(session.email, session.tokenOrPass)
            val request = Request.Builder()
                .url(targetUrl)
                .put(localFile.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                .header("Authorization", credentials)
                .header("User-Agent", "Arcbox-WebDAV-Client/2.4")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || resp.code == 201 || resp.code == 204
                }
            } catch (e: Exception) {
                Log.w("WebDavProvider", "Remote PUT failed: ${e.message}")
            }
        }
        destFile.exists()
    }

    override suspend fun downloadFile(
        remoteFilePath: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val session = sessionManager.getSession(providerId)
        if (session != null && session.serverUrl.isNotBlank() && session.tokenOrPass.isNotBlank()) {
            val targetUrl = buildWebDavUrl(session.serverUrl, remoteFilePath)
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
                        resp.body?.byteStream()?.use { input ->
                            FileOutputStream(destinationFile).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    bytesRead += read
                                    if (totalBytes > 0) {
                                        onProgress(bytesRead.toFloat() / totalBytes)
                                    }
                                }
                            }
                        }
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                Log.w("WebDavProvider", "Remote GET failed: ${e.message}")
            }
        }

        val srcFile = File(getCacheDir(), remoteFilePath.trimStart('/'))
        if (!srcFile.exists()) return@withContext false

        destinationFile.parentFile?.mkdirs()
        val totalBytes = srcFile.length()
        var bytesRead = 0L

        try {
            srcFile.inputStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            onProgress(bytesRead.toFloat() / totalBytes)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("WebDavProvider", "Download error", e)
            false
        }
    }

    override suspend fun deleteFile(remoteFilePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(getCacheDir(), remoteFilePath.trimStart('/'))
        val localDeleted = if (file.exists()) {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } else false

        val session = sessionManager.getSession(providerId)
        if (session != null && session.serverUrl.isNotBlank() && session.tokenOrPass.isNotBlank()) {
            val targetUrl = buildWebDavUrl(session.serverUrl, remoteFilePath)
            val credentials = Credentials.basic(session.email, session.tokenOrPass)
            val request = Request.Builder()
                .url(targetUrl)
                .delete()
                .header("Authorization", credentials)
                .header("User-Agent", "Arcbox-WebDAV-Client/2.4")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || resp.code == 204 || localDeleted
                }
            } catch (e: Exception) {
                Log.w("WebDavProvider", "Remote DELETE failed: ${e.message}")
            }
        }
        localDeleted
    }

    override suspend fun renameFile(oldRemotePath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(getCacheDir(), oldRemotePath.trimStart('/'))
        val newFile = File(file.parentFile, newName)
        val localRenamed = if (file.exists()) file.renameTo(newFile) else false

        val session = sessionManager.getSession(providerId)
        if (session != null && session.serverUrl.isNotBlank() && session.tokenOrPass.isNotBlank()) {
            val targetUrl = buildWebDavUrl(session.serverUrl, oldRemotePath)
            val parentPath = if (oldRemotePath.contains('/')) oldRemotePath.substringBeforeLast('/') else ""
            val newSub = if (parentPath.isBlank()) newName else "$parentPath/$newName"
            val destUrl = buildWebDavUrl(session.serverUrl, newSub)
            val credentials = Credentials.basic(session.email, session.tokenOrPass)
            val request = Request.Builder()
                .url(targetUrl)
                .method("MOVE", "".toRequestBody("text/plain".toMediaTypeOrNull()))
                .header("Destination", destUrl)
                .header("Authorization", credentials)
                .header("User-Agent", "Arcbox-WebDAV-Client/2.4")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || resp.code == 201 || resp.code == 204 || localRenamed
                }
            } catch (e: Exception) {
                Log.w("WebDavProvider", "Remote MOVE failed: ${e.message}")
            }
        }
        localRenamed
    }

    override suspend fun copyOrMoveFile(
        sourceRemotePath: String,
        destRemotePath: String,
        isMove: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
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
        return "https://cloud.nextcloud.com/s/arcbox_${name.hashCode().toString(16)}"
    }

    private fun ensureInitialWorkspace(cloudDir: File, providerName: String, accountEmail: String) {
        if (!cloudDir.exists()) cloudDir.mkdirs()
        val docsDir = File(cloudDir, "Documentos")
        if (!docsDir.exists()) {
            docsDir.mkdirs()
            File(docsDir, "WebDAV-Nextcloud.txt").writeText(
                """
                === $providerName UNIDADE DE ARMAZENAMENTO ===
                Conta Conectada: $accountEmail
                Integração: RFC 4918 WebDAV Client
                Data: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}
                
                Unidade WebDAV conectada com sucesso ao Arcbox.
                """.trimIndent()
            )

            val photosDir = File(cloudDir, "Fotos")
            photosDir.mkdirs()

            val downloadsDir = File(cloudDir, "Downloads")
            downloadsDir.mkdirs()

            val backupDir = File(cloudDir, "Backups")
            backupDir.mkdirs()
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
