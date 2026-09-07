package com.example.data.update

/**
 * Representa as informações de uma nova versão detectada no GitHub Releases.
 */
data class UpdateReleaseInfo(
    val tagName: String,               // ex: "v2.0.0" ou "2.0.0"
    val releaseName: String,           // ex: "ArcBox v2.0.0"
    val targetVersionCode: Int,        // parsed ou informado
    val targetVersionName: String,      // ex: "2.0.0"
    val changelog: String,             // Body do release ou texto formatado
    val apkDownloadUrl: String,        // URL direta para baixar o arquivo .apk
    val apkFileName: String,           // ex: "Arcbox-v2.0.0.apk"
    val apkSizeBytes: Long,            // Tamanho em bytes do APK
    val expectedSha256: String? = null,// Hash SHA-256 publicado (em .sha256 ou no changelog)
    val publishedAt: String = "",      // Data ISO ou formatada
    val isMandatory: Boolean = false   // Se a atualização foi sinalizada como obrigatória
)

/**
 * Estado detalhado do fluxo de atualização.
 */
sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class NoUpdateAvailable(val currentVersionName: String, val lastCheckedTime: Long) : UpdateStatus()
    data class UpdateAvailable(val releaseInfo: UpdateReleaseInfo) : UpdateStatus()
    data class Downloading(
        val releaseInfo: UpdateReleaseInfo,
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateStatus()
    data class DownloadCompleted(
        val releaseInfo: UpdateReleaseInfo,
        val localApkFile: java.io.File
    ) : UpdateStatus()
    data class Error(val message: String, val canRetry: Boolean = true) : UpdateStatus()
}
