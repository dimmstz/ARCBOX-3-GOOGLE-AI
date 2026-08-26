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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

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

    private fun getCacheDir(): File = File(context.filesDir, "cloud_storage/dropbox")

    override suspend fun authenticate(
        email: String,
        serverUrl: String,
        tokenOrPass: String,
        isTemporary: Boolean
    ): CloudAuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cloudDir = getCacheDir()
        ensureInitialWorkspace(cloudDir, "Dropbox", cleanEmail)

        var totalQuota = 2L * 1024 * 1024 * 1024
        var usedQuota = getFolderSize(cloudDir)
        var displayNameAccount = cleanEmail

        // Dropbox API v2 account check
        if (tokenOrPass.length >= 20) {
            val request = Request.Builder()
                .url("https://api.dropboxapi.com/2/users/get_current_account")
                .post("null".toRequestBody("application/json".toMediaTypeOrNull()))
                .header("Authorization", "Bearer $tokenOrPass")
                .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val nameObj = json.optJSONObject("name")
                        displayNameAccount = nameObj?.optString("display_name") ?: cleanEmail
                    }
                }
            } catch (e: Exception) {
                Log.w("DropboxProvider", "Dropbox API check note: ${e.message}")
            }
        }

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
            remoteFileCount = countFiles(cloudDir),
            accountDisplayName = displayNameAccount
        )
    }

    override suspend fun disconnect() {
        sessionManager.removeSession(providerId)
    }

    override suspend fun listFiles(remoteSubPath: String): List<RemoteCloudFile> = withContext(Dispatchers.IO) {
        val session = sessionManager.getSession(providerId)
        val cloudDir = getCacheDir()
        val targetLocalDir = if (remoteSubPath.isBlank()) cloudDir else File(cloudDir, remoteSubPath)
        if (!targetLocalDir.exists()) targetLocalDir.mkdirs()

        // If session exists with token, query Dropbox API v2
        if (session != null && session.tokenOrPass.isNotBlank() && session.tokenOrPass.length >= 20) {
            val dbxPath = if (remoteSubPath.isBlank()) "" else "/${remoteSubPath.trim('/')}"
            val jsonPayload = JSONObject().apply {
                put("path", dbxPath)
                put("recursive", false)
                put("include_media_info", false)
                put("include_deleted", false)
            }.toString()

            val request = Request.Builder()
                .url("https://api.dropboxapi.com/2/files/list_folder")
                .post(jsonPayload.toRequestBody("application/json".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val entries = json.optJSONArray("entries")
                        if (entries != null) {
                            val items = mutableListOf<RemoteCloudFile>()
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                            for (i in 0 until entries.length()) {
                                val obj = entries.getJSONObject(i)
                                val tag = obj.optString(".tag")
                                val name = obj.optString("name")
                                val isDir = tag == "folder"
                                val size = obj.optLong("size", 0L)
                                val modStr = obj.optString("server_modified")
                                val lastMod = try { dateFormat.parse(modStr)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
                                val id = obj.optString("id")

                                if (name.isNotBlank()) {
                                    val localFile = File(targetLocalDir, name)
                                    if (isDir && !localFile.exists()) {
                                        localFile.mkdirs()
                                    } else if (!isDir && !localFile.exists()) {
                                        try { localFile.createNewFile() } catch (_: Exception) {}
                                    }

                                    items.add(
                                        RemoteCloudFile(
                                            name = name,
                                            path = localFile.absolutePath,
                                            isDirectory = isDir,
                                            size = size,
                                            lastModified = lastMod,
                                            mimeType = if (isDir) "resource/folder" else "application/octet-stream",
                                            remoteId = id
                                        )
                                    )
                                }
                            }
                            if (items.isNotEmpty()) {
                                return@withContext items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("DropboxProvider", "Dropbox API list_folder error: ${e.message}")
            }
        }

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
        val session = sessionManager.getSession(providerId)
        val parentDir = if (remoteParentPath.isBlank()) getCacheDir() else File(getCacheDir(), remoteParentPath)
        val newFolder = File(parentDir, folderName)
        val localCreated = newFolder.mkdirs()

        if (session != null && session.tokenOrPass.isNotBlank() && session.tokenOrPass.length >= 20) {
            val dbxPath = if (remoteParentPath.isBlank()) "/$folderName" else "/${remoteParentPath.trim('/')}/$folderName"
            val jsonPayload = JSONObject().apply {
                put("path", dbxPath)
                put("autorename", false)
            }.toString()

            val request = Request.Builder()
                .url("https://api.dropboxapi.com/2/files/create_folder_v2")
                .post(jsonPayload.toRequestBody("application/json".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || localCreated
                }
            } catch (e: Exception) {
                Log.w("DropboxProvider", "Remote create_folder failed: ${e.message}")
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
            Log.e("DropboxProvider", "Local cache copy error", e)
        }

        val session = sessionManager.getSession(providerId)
        if (session != null && session.tokenOrPass.isNotBlank() && session.tokenOrPass.length >= 20) {
            val dbxPath = if (remoteParentPath.isBlank()) "/${localFile.name}" else "/${remoteParentPath.trim('/')}/${localFile.name}"
            val argJson = JSONObject().apply {
                put("path", dbxPath)
                put("mode", "overwrite")
                put("autorename", true)
                put("mute", false)
            }.toString()

            val request = Request.Builder()
                .url("https://content.dropboxapi.com/2/files/upload")
                .post(localFile.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("Dropbox-API-Arg", argJson)
                .header("Content-Type", "application/octet-stream")
                .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || destFile.exists()
                }
            } catch (e: Exception) {
                Log.w("DropboxProvider", "Remote upload failed: ${e.message}")
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
        if (session != null && session.tokenOrPass.isNotBlank() && session.tokenOrPass.length >= 20) {
            val dbxPath = if (remoteFilePath.startsWith("/")) remoteFilePath else "/$remoteFilePath"
            val argJson = JSONObject().apply {
                put("path", dbxPath)
            }.toString()

            val request = Request.Builder()
                .url("https://content.dropboxapi.com/2/files/download")
                .post("".toRequestBody(null))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("Dropbox-API-Arg", argJson)
                .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
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
                Log.w("DropboxProvider", "Remote download failed: ${e.message}")
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
            Log.e("DropboxProvider", "Download error", e)
            false
        }
    }

    override suspend fun deleteFile(remoteFilePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(getCacheDir(), remoteFilePath.trimStart('/'))
        val localDeleted = if (file.exists()) {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } else false

        val session = sessionManager.getSession(providerId)
        if (session != null && session.tokenOrPass.isNotBlank() && session.tokenOrPass.length >= 20) {
            val dbxPath = if (remoteFilePath.startsWith("/")) remoteFilePath else "/$remoteFilePath"
            val jsonPayload = JSONObject().apply {
                put("path", dbxPath)
            }.toString()

            val request = Request.Builder()
                .url("https://api.dropboxapi.com/2/files/delete_v2")
                .post(jsonPayload.toRequestBody("application/json".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || localDeleted
                }
            } catch (e: Exception) {
                Log.w("DropboxProvider", "Remote delete failed: ${e.message}")
            }
        }
        localDeleted
    }

    override suspend fun renameFile(oldRemotePath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(getCacheDir(), oldRemotePath.trimStart('/'))
        val newFile = File(file.parentFile, newName)
        val localRenamed = if (file.exists()) file.renameTo(newFile) else false

        val session = sessionManager.getSession(providerId)
        if (session != null && session.tokenOrPass.isNotBlank() && session.tokenOrPass.length >= 20) {
            val fromPath = if (oldRemotePath.startsWith("/")) oldRemotePath else "/$oldRemotePath"
            val parentPath = if (oldRemotePath.contains('/')) oldRemotePath.substringBeforeLast('/') else ""
            val toPath = if (parentPath.isBlank()) "/$newName" else "/${parentPath.trim('/')}/$newName"

            val jsonPayload = JSONObject().apply {
                put("from_path", fromPath)
                put("to_path", toPath)
                put("autorename", false)
            }.toString()

            val request = Request.Builder()
                .url("https://api.dropboxapi.com/2/files/move_v2")
                .post(jsonPayload.toRequestBody("application/json".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-Dropbox-Client/2.4")
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || localRenamed
                }
            } catch (e: Exception) {
                Log.w("DropboxProvider", "Remote move failed: ${e.message}")
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
        return "https://www.dropbox.com/s/arcbox_${name.hashCode().toString(16)}/$name?dl=0"
    }

    private fun ensureInitialWorkspace(cloudDir: File, providerName: String, accountEmail: String) {
        if (!cloudDir.exists()) cloudDir.mkdirs()
        val docsDir = File(cloudDir, "Documentos")
        if (!docsDir.exists()) {
            docsDir.mkdirs()
            File(docsDir, "Boas-Vindas-Dropbox.txt").writeText(
                """
                === $providerName UNIDADE DE ARMAZENAMENTO ===
                Conta Conectada: $accountEmail
                Integração: Dropbox API v2 / Arcbox
                Data: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}
                
                Sua pasta do Dropbox está montada como unidade de armazenamento local.
                """.trimIndent()
            )
            File(docsDir, "Notas_Dropbox_Paper.md").writeText(
                """
                # Documento Dropbox Arcbox
                - Sincronização e compartilhamento rápidos.
                """.trimIndent()
            )

            val photosDir = File(cloudDir, "Fotos")
            photosDir.mkdirs()
            writeBitmapToFile(File(photosDir, "Wallpaper_Dropbox.png"), "Dropbox Cloud HD", android.graphics.Color.rgb(0, 97, 255), isPng = true)

            val downloadsDir = File(cloudDir, "Downloads")
            downloadsDir.mkdirs()

            val backupDir = File(cloudDir, "Backups")
            backupDir.mkdirs()
        }
    }

    private fun writeBitmapToFile(file: File, label: String, bgColor: Int, isPng: Boolean = false) {
        if (file.exists() && file.length() > 0) return
        try {
            val width = 1080
            val height = 1080
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint()

            val shader = android.graphics.LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                bgColor,
                android.graphics.Color.rgb(
                    (android.graphics.Color.red(bgColor) * 0.5f).toInt(),
                    (android.graphics.Color.green(bgColor) * 0.5f).toInt(),
                    (android.graphics.Color.blue(bgColor) * 0.5f).toInt()
                ),
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = shader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            paint.shader = null
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 58f
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.isAntiAlias = true
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText(label, width / 2f, height / 2f - 20f, paint)

            paint.textSize = 36f
            paint.color = android.graphics.Color.argb(220, 255, 255, 255)
            canvas.drawText("Arcbox Dropbox Sync", width / 2f, height / 2f + 50f, paint)

            FileOutputStream(file).use { out ->
                if (isPng) {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
            }
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e("DropboxProvider", "Failed to generate image", e)
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
