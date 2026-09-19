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

        fun cleanVersionName(tag: String): String {
            return tag.removePrefix("v").removePrefix("V").trim()
        }

        fun parseSemverParts(version: String): List<Int> {
            val cleaned = cleanVersionName(version)
            return cleaned.split(".").mapNotNull {
                val numStr = it.takeWhile { char -> char.isDigit() }
                numStr.toIntOrNull()
            }
        }

        /**
         * Extrai código de build/versão explícito se estiver presente na tag ou título.
         * Exemplos:
         * "v1.0.7+8" -> 8
         * "v1.0.7 (build 8)" -> 8
         * "v1.0.7-b8" -> 8
         * "v1.0.7" -> null (não há código de build explícito)
         */
        fun extractExplicitVersionCode(tagName: String, releaseTitle: String): Int? {
            val combined = "$tagName $releaseTitle"
            val plusMatch = Regex("""\+(\d+)""").find(tagName)
            if (plusMatch != null) {
                return plusMatch.groupValues[1].toIntOrNull()
            }
            val codeMatch = Regex("""(?:build|code|b|vc)[\s:=_-]*(\d+)""", RegexOption.IGNORE_CASE).find(combined)
            if (codeMatch != null) {
                return codeMatch.groupValues[1].toIntOrNull()
            }
            return null
        }

        /**
         * Compara se a versão remota é estritamente mais recente que a instalada.
         *
         * Regras rigorosas:
         * 1. Se a versão remota e instalada tiverem o mesmo nome (ex: "1.0.6" == "1.0.6"):
         *    NUNCA é considerada mais recente, a não ser que haja um versionCode explícito
         *    na release remota E esse versionCode seja estritamente maior que o instalado.
         * 2. Comparação SemVer parte a parte (ex: 1.0.7 vs 1.0.6 -> 7 > 6 -> TRUE).
         *    (ex: 1.0.5 vs 1.0.6 -> 5 < 6 -> FALSE).
         * 3. Se todas as partes numéricas forem iguais (ex: "1.0.6" vs "1.0.6.0"):
         *    Apenas retorna true se houver código de build explícito superior.
         */
        fun isRemoteVersionNewer(
            installedVersionCode: Int,
            installedVersionName: String,
            remoteExplicitCode: Int?,
            remoteVersionName: String
        ): Boolean {
            val cleanInstalled = cleanVersionName(installedVersionName)
            val cleanRemote = cleanVersionName(remoteVersionName)

            // Se os nomes de versão forem exatamente iguais (ex: "1.0.6" == "1.0.6")
            if (cleanInstalled.equals(cleanRemote, ignoreCase = true)) {
                return if (remoteExplicitCode != null && remoteExplicitCode > 0 && installedVersionCode > 0) {
                    remoteExplicitCode > installedVersionCode
                } else {
                    false
                }
            }

            // Comparação SemVer (Major.Minor.Patch...)
            val installedParts = parseSemverParts(cleanInstalled)
            val remoteParts = parseSemverParts(cleanRemote)

            val maxLen = maxOf(installedParts.size, remoteParts.size)
            for (i in 0 until maxLen) {
                val inst = installedParts.getOrElse(i) { 0 }
                val rem = remoteParts.getOrElse(i) { 0 }
                if (rem > inst) return true
                if (rem < inst) return false
            }

            // Se todas as partes numéricas forem iguais (ex: "1.0.6" e "1.0.6.0")
            if (remoteExplicitCode != null && remoteExplicitCode > 0 && installedVersionCode > 0) {
                return remoteExplicitCode > installedVersionCode
            }

            return false
        }
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
        // Se estiver vazio ou for o token antigo/revogado, atualiza imediatamente para o novo padrão oficial
        return if (!saved.isNullOrBlank() && !saved.contains("O4kiFPemK1Cx") && !saved.contains("2bIeZv7")) {
            saved
        } else {
            val defaultToken = UpdateConfig.DEFAULT_GITHUB_PAT_TOKEN
            if (defaultToken.isNotBlank()) {
                prefs.edit().putString(UpdateConfig.PREF_GITHUB_PAT_TOKEN, defaultToken).apply()
            }
            defaultToken
        }
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
     * Consulta a API de atualizações mais recentes e compara com a versão atual do ArcBox.
     */
    suspend fun checkLatestRelease(): Result<UpdateReleaseInfo?> = withContext(Dispatchers.IO) {
        try {
            val owner = getRepoOwner()
            val repo = getRepoName()
            val apiUrl = UpdateConfig.getLatestReleaseApiUrl(owner, repo)

            Log.d(TAG, "Consultando atualizações em: $apiUrl")

            var token = getGithubToken()
            fun buildRequest(t: String): Request {
                val b = Request.Builder()
                    .url(apiUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Arcbox-Android/${UpdateConfig.CURRENT_VERSION_NAME}")
                if (t.isNotBlank()) {
                    b.header("Authorization", "Bearer $t")
                }
                return b.build()
            }

            var response = httpClient.newCall(buildRequest(token)).execute()
            recordLastCheckedTime()

            // Se falhar com 401 ou 403 e o token usado for diferente do novo token padrão, reverte para o token padrão e tenta novamente
            if (!response.isSuccessful && (response.code == 401 || response.code == 403) && token != UpdateConfig.DEFAULT_GITHUB_PAT_TOKEN) {
                response.close()
                token = UpdateConfig.DEFAULT_GITHUB_PAT_TOKEN
                prefs.edit().putString(UpdateConfig.PREF_GITHUB_PAT_TOKEN, token).apply()
                response = httpClient.newCall(buildRequest(token)).execute()
            }

            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                val userFriendlyMessage = when (code) {
                    404 -> "Nenhuma nova versão encontrada no momento."
                    401, 403 -> "Serviço de atualizações temporariamente indisponível. Tente novamente mais tarde."
                    else -> "Não foi possível verificar atualizações no momento (código $code)."
                }
                return@withContext Result.failure(Exception(userFriendlyMessage))
            }

            val responseBody = response.body?.string() ?: ""
            response.close()

            if (responseBody.isBlank()) {
                return@withContext Result.failure(Exception("Não foi possível carregar as informações da nova versão."))
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

            // First scan for .apk asset, preferring release builds over debug builds
            for (i in 0 until assetsArray.length()) {
                val asset = assetsArray.getJSONObject(i)
                val assetName = asset.optString("name", "")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    // Se já encontramos um release, não substituímos por um debug
                    if (apkFileName.contains("release", ignoreCase = true) && assetName.contains("debug", ignoreCase = true)) {
                        continue
                    }
                    apkDownloadUrl = asset.optString("browser_download_url", "")
                    apiAssetUrl = asset.optString("url", "")
                    apkFileName = assetName
                    apkSizeBytes = asset.optLong("size", 0L)
                    // Se acharmos o release, quebramos o loop, caso contrário continuamos procurando
                    if (assetName.contains("release", ignoreCase = true)) {
                        break
                    }
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
                return@withContext Result.failure(Exception("Nenhum pacote de instalação (.apk) disponível para a versão $tagName."))
            }

            // Dynamic installed version from context / PackageManager
            val installedVersionName = UpdateConfig.getInstalledVersionName(context)
            val installedVersionCode = UpdateConfig.getInstalledVersionCode(context)

            // Version comparison
            val remoteVersionName = cleanVersionName(tagName)
            val explicitRemoteCode = extractExplicitVersionCode(tagName, releaseTitle)
            val isMandatory = body.contains("[MANDATORY]", ignoreCase = true) || body.contains("OBRIGATÓRIA", ignoreCase = true)

            val isNewer = isRemoteVersionNewer(
                installedVersionCode = installedVersionCode,
                installedVersionName = installedVersionName,
                remoteExplicitCode = explicitRemoteCode,
                remoteVersionName = remoteVersionName
            )

            Log.d(TAG, "Checagem de versão: instalada=$installedVersionName (code=$installedVersionCode) vs remota=$remoteVersionName (code=$explicitRemoteCode) -> isNewer=$isNewer")

            if (!isNewer) {
                // Já está na versão mais recente ou superior
                return@withContext Result.success(null)
            }

            val releaseInfo = UpdateReleaseInfo(
                tagName = tagName,
                releaseName = releaseTitle,
                targetVersionCode = explicitRemoteCode ?: installedVersionCode,
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
            Log.e(TAG, "Erro ao verificar atualizações", e)
            val msg = e.message ?: ""
            val cleanMsg = if (msg.contains("Unable to resolve host", ignoreCase = true) || msg.contains("timeout", ignoreCase = true)) {
                "Sem conexão com a internet. Verifique sua conexão e tente novamente."
            } else if (msg.isNotBlank() && !msg.contains("GitHub", ignoreCase = true) && !msg.contains("Token", ignoreCase = true)) {
                msg
            } else {
                "Não foi possível verificar atualizações no momento. Tente novamente mais tarde."
            }
            Result.failure(Exception(cleanMsg))
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

    /**
     * Verifica se uma release específica é estritamente mais recente que a versão instalada.
     */
    fun isReleaseNewerThanInstalled(releaseInfo: UpdateReleaseInfo): Boolean {
        val installedVersionName = UpdateConfig.getInstalledVersionName(context)
        val installedVersionCode = UpdateConfig.getInstalledVersionCode(context)
        val explicitCode = extractExplicitVersionCode(releaseInfo.tagName, releaseInfo.releaseName)
        return isRemoteVersionNewer(
            installedVersionCode = installedVersionCode,
            installedVersionName = installedVersionName,
            remoteExplicitCode = explicitCode,
            remoteVersionName = releaseInfo.targetVersionName
        )
    }

    private fun extractSha256FromString(text: String): String? {
        val shaPattern = Regex("""\b([a-fA-F0-9]{64})\b""")
        val match = shaPattern.find(text)
        return match?.groupValues?.get(1)?.lowercase()
    }
}
