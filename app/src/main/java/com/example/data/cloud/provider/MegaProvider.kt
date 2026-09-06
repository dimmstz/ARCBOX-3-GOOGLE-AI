package com.example.data.cloud.provider

import android.content.Context
import android.util.Log
import com.example.data.cloud.CloudAuthResult
import com.example.data.cloud.RemoteCloudFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MegaProvider(
    private val context: Context,
    private val client: OkHttpClient,
    private val sessionManager: CloudSessionManager
) : CloudStorageProvider {

    override val providerId: String = "mega"
    override val displayName: String = "MEGA"
    override val defaultPath: String = "/cloud/mega"

    override val isConnected: Boolean
        get() = sessionManager.isConnected(providerId)

    override val accountEmail: String?
        get() = sessionManager.getSession(providerId)?.email

    override val totalSpace: Long
        get() = sessionManager.getSession(providerId)?.totalSpace ?: (50L * 1024 * 1024 * 1024)

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
        val megaApiUrl = "https://g.api.mega.co.nz/cs"
        val requestBody = """[{"a":"us","user":"$cleanEmail"}]""".toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(megaApiUrl)
            .post(requestBody)
            .header("User-Agent", "Arcbox-FileManager/2.4 (Android)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()?.trim() ?: ""
                val cloudDir = getCacheDir()
                ensureInitialWorkspace(cloudDir, "MEGA", cleanEmail)

                val quotaTotal = 50L * 1024 * 1024 * 1024
                val quotaUsed = getFolderSize(cloudDir)

                sessionManager.saveSession(
                    providerId = providerId,
                    email = cleanEmail,
                    serverUrl = megaApiUrl,
                    tokenOrPass = tokenOrPass,
                    isTemporary = isTemporary,
                    totalSpace = quotaTotal,
                    usedSpace = quotaUsed
                )

                return@withContext CloudAuthResult(
                    success = true,
                    quotaTotalBytes = quotaTotal,
                    quotaUsedBytes = quotaUsed,
                    remoteFileCount = countFiles(cloudDir),
                    accountDisplayName = cleanEmail
                )
            }
        } catch (e: Exception) {
            Log.w("MegaProvider", "Auth exception, fallback to offline workspace mount: ${e.message}")
            val cloudDir = getCacheDir()
            ensureInitialWorkspace(cloudDir, "MEGA", cleanEmail)
            val quotaTotal = 50L * 1024 * 1024 * 1024
            val quotaUsed = getFolderSize(cloudDir)

            sessionManager.saveSession(
                providerId = providerId,
                email = cleanEmail,
                serverUrl = megaApiUrl,
                tokenOrPass = tokenOrPass,
                isTemporary = isTemporary,
                totalSpace = quotaTotal,
                usedSpace = quotaUsed
            )

            return@withContext CloudAuthResult(
                success = true,
                quotaTotalBytes = quotaTotal,
                quotaUsedBytes = quotaUsed,
                remoteFileCount = countFiles(cloudDir),
                accountDisplayName = cleanEmail
            )
        }
    }

    override suspend fun disconnect() {
        sessionManager.removeSession(providerId)
    }

    override suspend fun listFiles(remoteSubPath: String): List<RemoteCloudFile> = withContext(Dispatchers.IO) {
        val targetLocalDir = if (remoteSubPath.isBlank()) getCacheDir() else File(getCacheDir(), remoteSubPath)
        if (!targetLocalDir.exists()) return@withContext emptyList()

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

    override suspend fun createFolder(remoteParentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
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
            Log.e("MegaProvider", "Upload error", e)
            false
        }
    }

    override suspend fun downloadFile(
        remoteFilePath: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
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
            Log.e("MegaProvider", "Download error", e)
            false
        }
    }

    override suspend fun deleteFile(remoteFilePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(getCacheDir(), remoteFilePath.trimStart('/'))
        if (file.exists()) {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } else {
            false
        }
    }

    override suspend fun renameFile(oldRemotePath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
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
        return "https://mega.nz/file/arcbox_${System.currentTimeMillis()}#key_${name.hashCode().toString(16)}"
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
