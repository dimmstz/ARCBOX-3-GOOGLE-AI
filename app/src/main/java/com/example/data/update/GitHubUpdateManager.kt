package com.example.data.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class GitHubUpdateManager(private val context: Context) {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val prefs = context.getSharedPreferences("arcbox_update_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "GitHubUpdateManager"
    }

    fun isAutoCheckEnabled(): Boolean {
        return prefs.getBoolean(UpdateConfig.PREF_AUTO_CHECK_UPDATES, true)
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(UpdateConfig.PREF_AUTO_CHECK_UPDATES, enabled).apply()
    }

    fun isWifiOnlyEnabled(): Boolean {
        return prefs.getBoolean(UpdateConfig.PREF_UPDATE_WIFI_ONLY, false)
    }

    fun setWifiOnlyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(UpdateConfig.PREF_UPDATE_WIFI_ONLY, enabled).apply()
    }

    fun getLastCheckedTime(): Long {
        return prefs.getLong(UpdateConfig.PREF_LAST_UPDATE_CHECK, 0L)
    }

    fun recordLastCheckedTime() {
        prefs.edit().putLong(UpdateConfig.PREF_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply()
    }

    fun getRepoOwner(): String {
        return prefs.getString(UpdateConfig.PREF_CUSTOM_REPO_OWNER, UpdateConfig.DEFAULT_GITHUB_OWNER)
            ?: UpdateConfig.DEFAULT_GITHUB_OWNER
    }

    fun getRepoName(): String {
        return prefs.getString(UpdateConfig.PREF_CUSTOM_REPO_NAME, UpdateConfig.DEFAULT_GITHUB_REPO)
            ?: UpdateConfig.DEFAULT_GITHUB_REPO
    }

    fun setCustomRepo(owner: String, repo: String) {
        prefs.edit()
            .putString(UpdateConfig.PREF_CUSTOM_REPO_OWNER, owner.trim())
            .putString(UpdateConfig.PREF_CUSTOM_REPO_NAME, repo.trim())
            .apply()
    }

    fun getGithubToken(): String {
        val saved = prefs.getString(UpdateConfig.PREF_GITHUB_PAT_TOKEN, "")
        return if (!saved.isNullOrBlank()) saved else UpdateConfig.DEFAULT_GITHUB_PAT_TOKEN
    }

    fun setGithubToken(token: String) {
        prefs.edit()
            .putString(UpdateConfig.PREF_GITHUB_PAT_TOKEN, token.trim())
            .apply()
    }

    /**
     * Verifica o estado de conectividade atual (se está conectado, e se é Wi-Fi quando exigido).
     */
    fun isNetworkAllowed(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!hasInternet) return false

        if (isWifiOnlyEnabled()) {
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
        return true
    }

    /**
     * Consulta a API de releases mais recentes do GitHub e compara com a versão atual do ArcBox.
     */
    suspend fun checkLatestRelease(): Result<UpdateReleaseInfo?> = withContext(Dispatchers.IO) {
        try {
            val owner = getRepoOwner()
            val repo = getRepoName()
            val apiUrl = UpdateConfig.getLatestReleaseApiUrl(owner, repo)

            Log.d(TAG, "Consultando atualizações em: $apiUrl")

            val token = getGithubToken()
            val requestBuilder = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Arcbox-Android/${UpdateConfig.CURRENT_VERSION_NAME}")

            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val request = requestBuilder.build()

            val response = httpClient.newCall(request).execute()
            recordLastCheckedTime()

            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                if (code == 404) {
                    val msg = if (token.isBlank()) {
                        "Nenhuma release encontrada em $owner/$repo. Se o repositório for privado, configure um Personal Access Token."
                    } else {
                        "Nenhuma release encontrada em $owner/$repo com o token informado."
                    }
                    return@withContext Result.failure(Exception(msg))
                } else if (code == 401 || code == 403) {
                    val msg = if (code == 401) {
                        "Token do GitHub não autorizado ou expirado. Verifique o token nas configurações."
                    } else {
                        "Limite de requisições do GitHub atingido ou permissão negada."
                    }
                    return@withContext Result.failure(Exception(msg))
                }
                return@withContext Result.failure(Exception("Servidor GitHub retornou erro HTTP $code."))
            }

            val responseBody = response.body?.string() ?: ""
            response.close()

            if (responseBody.isBlank()) {
                return@withContext Result.failure(Exception("Resposta vazia da API do GitHub."))
            }

            val releaseJson = JSONObject(responseBody)
            val tagName = releaseJson.optString("tag_name", "").trim()
            val releaseTitle = releaseJson.optString("name", "").ifBlank { tagName }
            val body = releaseJson.optString("body", "Melhorias de desempenho e correções.")
            val publishedAt = releaseJson.optString("published_at", "")

            // Parse assets for APK
            val assetsArray = releaseJson.optJSONArray("assets") ?: JSONArray()
            var apkDownloadUrl: String? = null
            var apiAssetUrl: String? = null
            var apkFileName = "Arcbox.apk"
            var apkSizeBytes: Long = 0L
            var expectedSha256: String? = null

            // First scan for .apk asset
            for (i in 0 until assetsArray.length()) {
                val asset = assetsArray.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    apkDownloadUrl = asset.optString("browser_download_url", "")
                    apiAssetUrl = asset.optString("url", "")
                    apkFileName = assetName
                    apkSizeBytes = asset.optLong("size", 0L)
                    break
                }
            }

            // Also check for a .sha256 companion file or search inside the release body
            for (i in 0 until assetsArray.length()) {
                val asset = assetsArray.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".sha256", ignoreCase = true) || assetName.endsWith(".txt", ignoreCase = true)) {
                    val hashFileUrl = asset.optString("browser_download_url", "")
                    if (hashFileUrl.isNotBlank()) {
                        try {
                            val hashReq = Request.Builder().url(hashFileUrl).build()
                            val hashResp = httpClient.newCall(hashReq).execute()
                            if (hashResp.isSuccessful) {
                                val hashContent = hashResp.body?.string() ?: ""
                                val parsedHash = extractSha256FromString(hashContent)
                                if (parsedHash != null) {
                                    expectedSha256 = parsedHash
                                }
                            }
                            hashResp.close()
                        } catch (e: Exception) {
                            Log.w(TAG, "Não foi possível baixar checksum acompanhante", e)
                        }
                    }
                }
            }

            if (expectedSha256 == null) {
                expectedSha256 = extractSha256FromString(body)
            }

            if (apkDownloadUrl.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Release $tagName encontrada, mas nenhum arquivo .apk está anexado a ela."))
            }

            // Version comparison
            val remoteVersionName = cleanVersionName(tagName)
            val remoteVersionCode = extractVersionCodeFromTagOrName(tagName, releaseTitle, remoteVersionName)
            val isMandatory = body.contains("[MANDATORY]", ignoreCase = true) || body.contains("OBRIGATÓRIA", ignoreCase = true)

            val isNewer = isRemoteVersionNewer(
                installedVersionCode = UpdateConfig.CURRENT_VERSION_CODE,
                installedVersionName = UpdateConfig.CURRENT_VERSION_NAME,
                remoteVersionCode = remoteVersionCode,
                remoteVersionName = remoteVersionName
            )

            if (!isNewer) {
                // Já está na versão mais recente ou superior
                return@withContext Result.success(null)
            }

            val releaseInfo = UpdateReleaseInfo(
                tagName = tagName,
                releaseName = releaseTitle,
                targetVersionCode = remoteVersionCode,
                targetVersionName = remoteVersionName,
                changelog = body,
                apkDownloadUrl = apkDownloadUrl,
                apkFileName = apkFileName,
                apkSizeBytes = apkSizeBytes,
                assetApiUrl = apiAssetUrl,
                expectedSha256 = expectedSha256,
                publishedAt = publishedAt,
                isMandatory = isMandatory
            )

            Result.success(releaseInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar atualizações do GitHub", e)
            Result.failure(e)
        }
    }

    /**
     * Faz o download do APK com medição precisa de progresso e suporte a cancelamento.
     */
    suspend fun downloadApk(
        releaseInfo: UpdateReleaseInfo,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            // Garantir diretório de atualizações limpo
            val updateDir = File(context.cacheDir, UpdateConfig.UPDATE_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            // Limpar arquivos temporários antigos
            cleanOldUpdates(updateDir)

            val safeName = releaseInfo.apkFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            tempFile = File(updateDir, "Arcbox_update_${releaseInfo.targetVersionName}_$safeName")

            val token = getGithubToken()
            val isPrivateAsset = token.isNotBlank() && !releaseInfo.assetApiUrl.isNullOrBlank()
            val downloadUrl = if (isPrivateAsset) releaseInfo.assetApiUrl!! else releaseInfo.apkDownloadUrl

            Log.d(TAG, "Iniciando download seguro HTTPS de: $downloadUrl")

            val requestBuilder = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Arcbox-Android/${UpdateConfig.CURRENT_VERSION_NAME}")

            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
                if (isPrivateAsset) {
                    requestBuilder.header("Accept", "application/octet-stream")
                }
            }

            val request = requestBuilder.build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                return@withContext Result.failure(Exception("Falha ao baixar APK. O servidor retornou HTTP $code"))
            }

            val body = response.body ?: run {
                response.close()
                return@withContext Result.failure(Exception("Corpo da resposta vazio ao baixar APK."))
            }

            val totalLength = if (body.contentLength() > 0) body.contentLength() else releaseInfo.apkSizeBytes
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                inputStream = body.byteStream()
                outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var downloadedBytes = 0L
                var lastReportedPercent = -1

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val percent = if (totalLength > 0) {
                        ((downloadedBytes * 100) / totalLength).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }

                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        onProgress(percent, downloadedBytes, totalLength)
                    }
                }
                outputStream.flush()
            } finally {
                try { outputStream?.close() } catch (_: Exception) {}
                try { inputStream?.close() } catch (_: Exception) {}
                response.close()
            }

            // Validação de Integridade SHA-256
            if (!releaseInfo.expectedSha256.isNullOrBlank()) {
                val shaMatches = ApkSecurityValidator.verifySha256(tempFile, releaseInfo.expectedSha256)
                if (!shaMatches) {
                    tempFile.delete()
                    return@withContext Result.failure(Exception("Falha de segurança: o hash SHA-256 do arquivo baixado não confere com a assinatura publicada da versão."))
                }
            }

            // Validação de Pacote Android (mesmo ID de app, não downgrade)
            val validation = ApkSecurityValidator.validateApkPackage(
                context = context,
                apkFile = tempFile,
                expectedPackageName = UpdateConfig.EXPECTED_APPLICATION_ID,
                currentVersionCode = UpdateConfig.CURRENT_VERSION_CODE
            )

            when (validation) {
                is ApkSecurityValidator.ValidationResult.Success -> {
                    Log.i(TAG, "APK validado com sucesso: ${validation.packageName} v${validation.versionName} (${validation.versionCode})")
                    Result.success(tempFile)
                }
                is ApkSecurityValidator.ValidationResult.Failed -> {
                    tempFile.delete()
                    Result.failure(Exception(validation.reason))
                }
            }

        } catch (e: CancellationException) {
            Log.d(TAG, "Download cancelado pelo usuário.")
            try { tempFile?.delete() } catch (_: Exception) {}
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante download do APK", e)
            try { tempFile?.delete() } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    /**
     * Remove arquivos temporários de versões antigas baixadas para não acumular espaço.
     */
    fun cleanOldUpdates(updateDir: File? = null) {
        try {
            val dir = updateDir ?: File(context.cacheDir, UpdateConfig.UPDATE_DIR_NAME)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao limpar arquivos temporários de atualização", e)
        }
    }

    private fun cleanVersionName(tag: String): String {
        return tag.removePrefix("v").removePrefix("V").trim()
    }

    /**
     * Tenta extrair versionCode do nome da tag ou texto.
     * Ex: "v2.0.0+15" -> 15, ou tenta deduzir através da semântica x.y.z.
     */
    private fun extractVersionCodeFromTagOrName(tagName: String, releaseTitle: String, versionName: String): Int {
        val combined = "$tagName $releaseTitle"
        val codeMatch = Regex("""(?:build|code|b|vc)[\s:=_-]*(\d+)""", RegexOption.IGNORE_CASE).find(combined)
        if (codeMatch != null) {
            return codeMatch.groupValues[1].toIntOrNull() ?: semverToVersionCode(versionName)
        }

        val plusMatch = Regex("""\+(\d+)""").find(tagName)
        if (plusMatch != null) {
            return plusMatch.groupValues[1].toIntOrNull() ?: semverToVersionCode(versionName)
        }

        return semverToVersionCode(versionName)
    }

    /**
     * Converte version "1.2.3" em código inteiro numérico ponderado (ex: 10203).
     */
    private fun semverToVersionCode(version: String): Int {
        val parts = version.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }
        if (parts.isEmpty()) return 1
        var code = 0
        val weights = listOf(10000, 100, 1)
        for (i in parts.indices) {
            if (i < weights.size) {
                code += parts[i] * weights[i]
            }
        }
        return code
    }

    /**
     * Compara versão remota e instalada.
     * Retorna true se a remota for estritamente mais recente.
     */
    private fun isRemoteVersionNewer(
        installedVersionCode: Int,
        installedVersionName: String,
        remoteVersionCode: Int,
        remoteVersionName: String
    ): Boolean {
        // Se ambos têm version codes válidos e distintos
        if (remoteVersionCode > 0 && installedVersionCode > 0 && remoteVersionCode != installedVersionCode) {
            return remoteVersionCode > installedVersionCode
        }

        // Comparação por SemVer (Major.Minor.Patch)
        val installedParts = parseSemverParts(installedVersionName)
        val remoteParts = parseSemverParts(remoteVersionName)

        for (i in 0 until maxOf(installedParts.size, remoteParts.size)) {
            val inst = installedParts.getOrElse(i) { 0 }
            val rem = remoteParts.getOrElse(i) { 0 }
            if (rem > inst) return true
            if (rem < inst) return false
        }

        return false
    }

    private fun parseSemverParts(version: String): List<Int> {
        val cleaned = version.removePrefix("v").removePrefix("V")
        return cleaned.split(".").mapNotNull {
            val numStr = it.takeWhile { char -> char.isDigit() }
            numStr.toIntOrNull()
        }
    }

    private fun extractSha256FromString(text: String): String? {
        val shaPattern = Regex("""\b([a-fA-F0-9]{64})\b""")
        val match = shaPattern.find(text)
        return match?.groupValues?.get(1)?.lowercase()
    }
}
