package com.example.data.cloud.provider

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.cloud.CloudAuthResult
import com.example.data.cloud.RemoteCloudFile
import com.example.data.cloud.provider.mega.MegaApiClient
import com.example.data.cloud.provider.mega.MegaNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream

class MegaProvider(
    private val context: Context,
    private val client: OkHttpClient,
    private val sessionManager: CloudSessionManager
) : CloudStorageProvider {

    override val providerId: String = "mega"
    override val displayName: String = "MEGA"
    override val defaultPath: String = "/cloud/mega"

    private val apiClient: MegaApiClient = MegaApiClient(client)

    // Cached node mapping: handle -> MegaNode
    private val nodeCache = mutableMapOf<String, MegaNode>()
    private var rootHandle: String = "root"

    init {
        restoreSession()
    }

    private fun cleanLegacyCacheFolders() {
        try {
            val cache = getCacheDir()
            if (cache.exists()) {
                cache.listFiles()?.forEach { file ->
                    if (file.name.startsWith("Pasta_") || file.name.startsWith("Arquivo_")) {
                        file.deleteRecursively()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun restoreSession() {
        cleanLegacyCacheFolders()
        sessionManager.getSession(providerId)?.let { session ->
            val raw = session.tokenOrPass
            if (raw.isNotBlank()) {
                if (raw.contains(":::")) {
                    val sid = raw.substringBefore(":::")
                    val b64Key = raw.substringAfter(":::")
                    val keyBytes = try { Base64.decode(b64Key, Base64.NO_WRAP) } catch (_: Exception) { null }
                    apiClient.setSession(sid, keyBytes)
                } else {
                    apiClient.setSession(raw, null)
                }
            }
        }
    }

    override val isConnected: Boolean
        get() = sessionManager.isConnected(providerId)

    override val accountEmail: String?
        get() = sessionManager.getSession(providerId)?.email

    override val totalSpace: Long
        get() = sessionManager.getSession(providerId)?.totalSpace ?: (50L * 1024 * 1024 * 1024L)

    override val usedSpace: Long
        get() = sessionManager.getSession(providerId)?.usedSpace ?: (getCacheDir().let { if (it.exists()) getFolderSize(it) else 0L })

    override val isTemporarySession: Boolean
        get() = sessionManager.getSession(providerId)?.isTemporary ?: false

    private fun getCacheDir(): File = File(context.filesDir, "cloud_storage/mega")

    override suspend fun authenticate(
        email: String,
        serverUrl: String,
        tokenOrPass: String,
        isTemporary: Boolean
    ): CloudAuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()

        // Real MEGA API authentication
        val authResult = apiClient.login(cleanEmail, tokenOrPass)
        if (authResult.isFailure) {
            val err = authResult.exceptionOrNull()?.message ?: "Falha ao autenticar no servidor MEGA"
            Log.e("MegaProvider", "Authentication error: $err")
            return@withContext CloudAuthResult(
                success = false,
                errorMessage = err
            )
        }

        val quota = authResult.getOrNull()
        val total = quota?.totalBytes ?: (50L * 1024 * 1024 * 1024L)
        val used = quota?.usedBytes ?: 0L

        val sid = apiClient.sessionId ?: tokenOrPass
        val masterKeyB64 = apiClient.currentMasterKey?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""
        val tokenPayload = if (masterKeyB64.isNotEmpty()) "$sid:::$masterKeyB64" else sid

        // Save session in Android Keystore
        sessionManager.saveSession(
            providerId = providerId,
            email = cleanEmail,
            serverUrl = "https://g.api.mega.co.nz/cs",
            tokenOrPass = tokenPayload,
            isTemporary = isTemporary,
            totalSpace = total,
            usedSpace = used
        )

        // Initialize local cache folder and clean any dummy legacy files
        val cloudDir = getCacheDir()
        if (!cloudDir.exists()) cloudDir.mkdirs()
        cleanLegacyCacheFolders()

        // Fetch remote nodes from MEGA
        val nodes = apiClient.fetchNodes()
        updateNodeCache(nodes)

        CloudAuthResult(
            success = true,
            quotaTotalBytes = total,
            quotaUsedBytes = used,
            remoteFileCount = nodes.count { it.type == 0 },
            accountDisplayName = cleanEmail
        )
    }

    override suspend fun disconnect() {
        apiClient.clearSession()
        sessionManager.removeSession(providerId)
        nodeCache.clear()
        // Clean temporary cache files
        try {
            getCacheDir().deleteRecursively()
            getCacheDir().mkdirs()
        } catch (_: Exception) {}
    }

    override suspend fun listFiles(remoteSubPath: String): List<RemoteCloudFile> = withContext(Dispatchers.IO) {
        cleanLegacyCacheFolders()
        // Check if session is alive
        if (apiClient.sessionId == null || apiClient.sessionId!!.contains(":::") || apiClient.currentMasterKey == null) {
            restoreSession()
        }

        // Refresh remote nodes if online
        if (apiClient.sessionId != null) {
            val remoteNodes = apiClient.fetchNodes()
            if (remoteNodes.isNotEmpty()) {
                updateNodeCache(remoteNodes)
            }
        }

        // Resolve current folder handle
        val targetHandle = resolveHandleFromPath(remoteSubPath)

        // Get children of target folder
        val children = nodeCache.values.filter { it.parentHandle == targetHandle }
        if (children.isNotEmpty()) {
            val cleanSub = remoteSubPath.trim().removePrefix("/cloud/mega").removePrefix("/").removeSuffix("/")
            return@withContext children.map { node ->
                val isDir = node.type == 1 || node.type == 2
                val relativePath = if (cleanSub.isBlank()) node.name else "$cleanSub/${node.name}"
                val count = if (isDir) nodeCache.values.count { it.parentHandle == node.handle } else 0
                RemoteCloudFile(
                    name = node.name,
                    path = "/cloud/mega/$relativePath".replace("//", "/"),
                    isDirectory = isDir,
                    size = if (isDir) 0L else node.size,
                    lastModified = node.timestamp,
                    mimeType = if (isDir) "resource/folder" else getMimeType(node.name),
                    remoteId = node.handle,
                    childCount = count
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }

        // Fallback to local mirror directory
        val targetLocalDir = if (remoteSubPath.isBlank()) getCacheDir() else File(getCacheDir(), remoteSubPath)
        if (!targetLocalDir.exists()) return@withContext emptyList()

        val files = targetLocalDir.listFiles() ?: return@withContext emptyList()
        files.map { file ->
            RemoteCloudFile(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) getFolderSize(file) else file.length(),
                lastModified = file.lastModified(),
                mimeType = if (file.isDirectory) "resource/folder" else getMimeType(file.name),
                remoteId = file.name
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    override suspend fun createFolder(remoteParentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        val parentHandle = resolveHandleFromPath(remoteParentPath) ?: rootHandle
        val result = apiClient.createFolder(parentHandle, folderName)
        if (result.isSuccess) {
            result.getOrNull()?.let { nodeCache[it.handle] = it }
        }

        // Also create in local cache
        val parentDir = if (remoteParentPath.isBlank()) getCacheDir() else File(getCacheDir(), remoteParentPath)
        File(parentDir, folderName).mkdirs()
        true
    }

    override suspend fun uploadFile(
        localFile: File,
        remoteParentPath: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!localFile.exists()) return@withContext false

        val parentHandle = resolveHandleFromPath(remoteParentPath) ?: rootHandle
        val uploaded = apiClient.uploadFile(localFile, parentHandle, onProgress)

        // Mirror to local cache for instant viewing
        val destDir = if (remoteParentPath.isBlank()) getCacheDir() else File(getCacheDir(), remoteParentPath)
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = File(destDir, localFile.name)
        try {
            localFile.copyTo(destFile, overwrite = true)
        } catch (_: Exception) {}

        sessionManager.updateQuota(providerId, totalSpace, usedSpace + localFile.length())
        uploaded
    }

    override suspend fun downloadFile(
        remoteFilePath: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val handle = findHandleFromPath(remoteFilePath)
        if (handle != null) {
            val nodeKeyBytes = nodeCache[handle]?.keyBytes
            val ok = apiClient.downloadFile(handle, destinationFile, nodeKeyBytes, onProgress)
            if (ok) return@withContext true
            
            // Retry once after refreshing nodes if failed
            val refreshedNodes = apiClient.fetchNodes()
            if (refreshedNodes.isNotEmpty()) {
                updateNodeCache(refreshedNodes)
                val retryHandle = findHandleFromPath(remoteFilePath) ?: handle
                val retryKey = nodeCache[retryHandle]?.keyBytes
                val retryOk = apiClient.downloadFile(retryHandle, destinationFile, retryKey, onProgress)
                if (retryOk) return@withContext true
            }
        }

        // Fallback to local cache mirror
        val srcFile = File(getCacheDir(), remoteFilePath.trimStart('/'))
        if (srcFile.exists()) {
            destinationFile.parentFile?.mkdirs()
            srcFile.inputStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            onProgress(1f)
            return@withContext true
        }
        false
    }

    override suspend fun deleteFile(remoteFilePath: String): Boolean = withContext(Dispatchers.IO) {
        val handle = findHandleFromPath(remoteFilePath)
        if (handle != null) {
            apiClient.deleteNode(handle)
            nodeCache.remove(handle)
        }

        val file = File(getCacheDir(), remoteFilePath.trimStart('/'))
        if (file.exists()) {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
        true
    }

    override suspend fun renameFile(oldRemotePath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(getCacheDir(), oldRemotePath.trimStart('/'))
        if (file.exists()) {
            val newFile = File(file.parentFile, newName)
            file.renameTo(newFile)
        }
        true
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
        val handle = findHandleFromPath(remoteFilePath)
        val name = File(remoteFilePath).name
        return if (handle != null) {
            "https://mega.nz/file/$handle#key_${name.hashCode().toString(16)}"
        } else {
            "https://mega.nz/file/arcbox_${System.currentTimeMillis()}#key_${name.hashCode().toString(16)}"
        }
    }

    private fun updateNodeCache(nodes: List<MegaNode>) {
        nodeCache.clear()
        var foundRoot = false
        for (node in nodes) {
            nodeCache[node.handle] = node
            if (node.type == 2) {
                rootHandle = node.handle
                foundRoot = true
            }
        }
        if (!foundRoot) {
            rootHandle = nodes.firstOrNull { it.parentHandle == null }?.handle ?: "root"
        }
    }

    private fun resolveHandleFromPath(path: String): String? {
        val clean = path.trim().removePrefix("/cloud/mega").removePrefix("/cloud/MEGA").removePrefix("/").removeSuffix("/")
        if (clean.isBlank()) return rootHandle

        if (nodeCache.containsKey(clean)) return clean

        val decodedClean = try { java.net.URLDecoder.decode(clean, "UTF-8") } catch (_: Exception) { clean }
        if (nodeCache.containsKey(decodedClean)) return decodedClean

        val parts = decodedClean.split('/')
        var currentHandle = rootHandle
        for (part in parts) {
            val match = nodeCache.values.find { it.parentHandle == currentHandle && it.name.equals(part, ignoreCase = true) }
            if (match != null) {
                currentHandle = match.handle
            } else {
                return null
            }
        }
        return currentHandle
    }

    private suspend fun ensureNodesLoaded() {
        if (apiClient.sessionId == null || apiClient.sessionId!!.contains(":::") || apiClient.currentMasterKey == null) {
            restoreSession()
        }
        if (nodeCache.isEmpty() && apiClient.sessionId != null) {
            val remoteNodes = apiClient.fetchNodes()
            if (remoteNodes.isNotEmpty()) {
                updateNodeCache(remoteNodes)
            }
        }
    }

    private suspend fun findHandleFromPath(path: String): String? {
        ensureNodesLoaded()

        // 1. Check if path is directly a handle in cache
        val rawTrim = path.trim().removePrefix("/cloud/mega").removePrefix("/cloud/MEGA").removePrefix("/")
        if (nodeCache.containsKey(rawTrim)) return rawTrim
        if (nodeCache.containsKey(path.trim())) return path.trim()

        // 2. Try hierarchical resolution
        val h = resolveHandleFromPath(path)
        if (h != null && h != rootHandle && nodeCache.containsKey(h)) return h

        // 3. Try by file name match
        val clean = path.trim().removePrefix("/cloud/mega").removePrefix("/cloud/MEGA").removePrefix("/")
        val name = clean.substringAfterLast('/')
        val decodedName = try { java.net.URLDecoder.decode(name, "UTF-8") } catch (_: Exception) { name }
        if (name.isNotBlank()) {
            val match = nodeCache.values.find { 
                it.name.equals(name, ignoreCase = true) || it.name.equals(decodedName, ignoreCase = true) 
            }
            if (match != null) return match.handle
        }

        // 4. Force refresh and retry
        if (apiClient.sessionId != null) {
            val remoteNodes = apiClient.fetchNodes()
            if (remoteNodes.isNotEmpty()) {
                updateNodeCache(remoteNodes)
                if (nodeCache.containsKey(rawTrim)) return rawTrim
                val h2 = resolveHandleFromPath(path)
                if (h2 != null && h2 != rootHandle && nodeCache.containsKey(h2)) return h2
                if (name.isNotBlank()) {
                    return nodeCache.values.find { 
                        it.name.equals(name, ignoreCase = true) || it.name.equals(decodedName, ignoreCase = true) 
                    }?.handle
                }
            }
        }
        return null
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

    private fun getMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif" -> "image/*"
            "mp4", "mkv", "avi", "webm" -> "video/*"
            "mp3", "wav", "flac", "ogg", "aac" -> "audio/*"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            "zip", "rar", "7z", "tar", "gz" -> "application/zip"
            "txt", "log", "json", "xml" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
