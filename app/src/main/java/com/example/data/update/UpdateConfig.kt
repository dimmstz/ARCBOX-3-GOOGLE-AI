package com.example.data.update

import com.example.BuildConfig

/**
 * Configuração centralizada do sistema de atualizações do ArcBox.
 */
object UpdateConfig {
    /**
     * Repositório padrão do GitHub onde as Releases oficiais do ArcBox são publicadas.
     * Formato: "owner/repo"
     * Usuário pode publicar em: https://github.com/douglas-br/arcbox/releases
     */
    const val DEFAULT_GITHUB_OWNER = "douglas-br"
    const val DEFAULT_GITHUB_REPO = "arcbox"

    /**
     * URL base da API do GitHub Releases para buscar a versão mais recente.
     */
    fun getLatestReleaseApiUrl(owner: String = DEFAULT_GITHUB_OWNER, repo: String = DEFAULT_GITHUB_REPO): String {
        return "https://api.github.com/repos/$owner/$repo/releases/latest"
    }

    /**
     * Application ID oficial esperado no AndroidManifest do APK baixado.
     */
    val EXPECTED_APPLICATION_ID: String = BuildConfig.APPLICATION_ID

    /**
     * Versão instalada atualmente no dispositivo.
     */
    val CURRENT_VERSION_CODE: Int = BuildConfig.VERSION_CODE
    val CURRENT_VERSION_NAME: String = BuildConfig.VERSION_NAME

    /**
     * Nome do diretório interno de cache onde os APKs baixados são salvos com segurança.
     */
    const val UPDATE_DIR_NAME = "arcbox_updates"

    /**
     * Chaves de preferências de atualização.
     */
    const val PREF_AUTO_CHECK_UPDATES = "pref_auto_check_updates"
    const val PREF_UPDATE_WIFI_ONLY = "pref_update_wifi_only"
    const val PREF_LAST_UPDATE_CHECK = "pref_last_update_check"
    const val PREF_LAST_CHECKED_VERSION = "pref_last_checked_version"
    const val PREF_CUSTOM_REPO_OWNER = "pref_custom_repo_owner"
    const val PREF_CUSTOM_REPO_NAME = "pref_custom_repo_name"

    /**
     * Intervalo mínimo entre verificações automáticas ao abrir o app (4 horas).
     */
    const val MIN_AUTO_CHECK_INTERVAL_MS = 4 * 60 * 60 * 1000L
}
