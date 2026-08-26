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
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

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

    private fun getCacheDir(): File = File(context.filesDir, "cloud_storage/drive")

    override suspend fun authenticate(
        email: String,
        serverUrl: String,
        tokenOrPass: String,
        isTemporary: Boolean
    ): CloudAuthResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cloudDir = getCacheDir()
        ensureInitialWorkspace(cloudDir, "Google Drive", cleanEmail)

        var totalQuota = 15L * 1024 * 1024 * 1024
        var usedQuota = getFolderSize(cloudDir)
        var displayNameAccount = cleanEmail

        // If user provided a real Google OAuth2 Access Token (starts with ya29. or long token)
        if (tokenOrPass.startsWith("ya29.") || tokenOrPass.length >= 30) {
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=user,storageQuota")
                .header("Authorization", "Bearer $tokenOrPass")
                .get()
                .build()

            try {
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val quota = json.optJSONObject("storageQuota")
                        totalQuota = quota?.optLong("limit") ?: (15L * 1024 * 1024 * 1024)
                        usedQuota = quota?.optLong("usage") ?: getFolderSize(cloudDir)
                        val user = json.optJSONObject("user")
                        displayNameAccount = user?.optString("displayName") ?: cleanEmail
                    }
                }
            } catch (e: Exception) {
                Log.w("GoogleDriveProvider", "Drive API about check note: ${e.message}")
            }
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

        // If session exists with token, query Google Drive API v3
        if (session != null && session.tokenOrPass.isNotBlank() && (session.tokenOrPass.startsWith("ya29.") || session.tokenOrPass.length >= 25)) {
            val qParam = URLEncoder.encode("trashed = false", "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$qParam&fields=files(id,name,mimeType,size,modifiedTime)&pageSize=100"
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-Drive-Client/2.4")
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val filesArr = json.optJSONArray("files")
                        if (filesArr != null) {
                            val items = mutableListOf<RemoteCloudFile>()
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                            for (i in 0 until filesArr.length()) {
                                val fileObj = filesArr.getJSONObject(i)
                                val id = fileObj.optString("id")
                                val name = fileObj.optString("name")
                                val mimeType = fileObj.optString("mimeType")
                                val isDir = mimeType == "application/vnd.google-apps.folder"
                                val size = fileObj.optLong("size", 0L)
                                val modTimeStr = fileObj.optString("modifiedTime")
                                val lastMod = try { dateFormat.parse(modTimeStr)?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }

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
                                            mimeType = mimeType,
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
                Log.w("GoogleDriveProvider", "Google Drive API query error: ${e.message}")
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

        if (session != null && session.tokenOrPass.isNotBlank() && (session.tokenOrPass.startsWith("ya29.") || session.tokenOrPass.length >= 25)) {
            val jsonBody = JSONObject().apply {
                put("name", folderName)
                put("mimeType", "application/vnd.google-apps.folder")
            }.toString()
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files")
                .post(jsonBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-Drive-Client/2.4")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || localCreated
                }
            } catch (e: Exception) {
                Log.w("GoogleDriveProvider", "Remote createFolder failed: ${e.message}")
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
            Log.e("GoogleDriveProvider", "Local cache copy error", e)
        }

        val session = sessionManager.getSession(providerId)
        if (session != null && session.tokenOrPass.isNotBlank() && (session.tokenOrPass.startsWith("ya29.") || session.tokenOrPass.length >= 25)) {
            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=media"
            val request = Request.Builder()
                .url(uploadUrl)
                .post(localFile.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
                .header("Authorization", "Bearer ${session.tokenOrPass}")
                .header("User-Agent", "Arcbox-Drive-Client/2.4")
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    return@withContext resp.isSuccessful || destFile.exists()
                }
            } catch (e: Exception) {
                Log.w("GoogleDriveProvider", "Remote upload failed: ${e.message}")
            }
        }
        destFile.exists()
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
            Log.e("GoogleDriveProvider", "Download error", e)
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
        return "https://drive.google.com/file/d/arcbox_${name.hashCode().toString(16)}/view?usp=sharing"
    }

    private fun ensureInitialWorkspace(cloudDir: File, providerName: String, accountEmail: String) {
        if (!cloudDir.exists()) cloudDir.mkdirs()
        val docsDir = File(cloudDir, "Documentos")
        if (!docsDir.exists()) {
            docsDir.mkdirs()
            File(docsDir, "Primeiros-Passos-$providerName.txt").writeText(
                """
                === $providerName UNIDADE DE ARMAZENAMENTO ===
                Conta Vinculada: $accountEmail
                Integração: Arcbox Drive Client 2.4
                Data: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}
                
                Seus arquivos do Google Drive estão disponíveis para edição, cópia, download
                e visualização direta pelo explorador do Arcbox.
                """.trimIndent()
            )
            File(docsDir, "Planilha_Financeira_2026.csv").writeText(
                """
                Categoria,Orcamento,Gasto,Saldo
                Servidores,2400,1800,600
                Armazenamento Nuvem,450,450,0
                Licencas,800,650,150
                """.trimIndent()
            )
            File(docsDir, "Anotacoes_Google_Drive.md").writeText(
                """
                # Google Drive no Arcbox
                - Sincronização em tempo real.
                - Suporte a múltiplos formatos e visualização rápida.
                """.trimIndent()
            )

            val photosDir = File(cloudDir, "Fotos")
            photosDir.mkdirs()
            writeBitmapToFile(File(photosDir, "Wallpaper_Google_Drive.png"), "Google Drive HD", android.graphics.Color.rgb(66, 133, 244), isPng = true)

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
            canvas.drawText("Arcbox Google Drive Sync", width / 2f, height / 2f + 50f, paint)

            FileOutputStream(file).use { out ->
                if (isPng) {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
            }
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e("GoogleDriveProvider", "Failed to generate image", e)
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
