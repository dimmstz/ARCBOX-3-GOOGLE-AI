package com.example.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object ApkSecurityValidator {
    private const val TAG = "ApkSecurityValidator"

    /**
     * Calcula o hash SHA-256 de um arquivo local.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Valida a integridade do arquivo através de comparação com o hash esperado (se fornecido).
     */
    fun verifySha256(file: File, expectedHash: String?): Boolean {
        if (expectedHash.isNullOrBlank()) {
            Log.d(TAG, "Nenhum hash SHA-256 fornecido na release; prosseguindo com verificação de integridade do pacote.")
            return true
        }
        val calculatedHash = computeSha256(file)
        val matches = calculatedHash.equals(expectedHash.trim(), ignoreCase = true)
        if (!matches) {
            Log.e(TAG, "FALHA DE INTEGRIDADE: SHA-256 esperado ($expectedHash) diverge do calculado ($calculatedHash)")
        }
        return matches
    }

    /**
     * Valida se o APK baixado:
     * 1. É um APK Android válido que o PackageManager consegue parsear.
     * 2. Tem o mesmo applicationId / packageName que o ArcBox.
     * 3. Não é uma versão inferior (impede downgrade acidental).
     */
    fun validateApkPackage(
        context: Context,
        apkFile: File,
        expectedPackageName: String = UpdateConfig.EXPECTED_APPLICATION_ID,
        currentVersionCode: Int = UpdateConfig.CURRENT_VERSION_CODE
    ): ValidationResult {
        if (!apkFile.exists() || apkFile.length() <= 0) {
            return ValidationResult.Failed("Arquivo APK não existe ou está vazio.")
        }

        val pm = context.packageManager
        val packageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_PERMISSIONS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao analisar APK com PackageManager", e)
            null
        }

        if (packageInfo == null) {
            return ValidationResult.Failed("O arquivo APK baixado está corrompido ou incompleto.")
        }

        val apkPackage = packageInfo.packageName
        if (apkPackage != expectedPackageName) {
            return ValidationResult.Failed("O aplicativo baixado ($apkPackage) não pertence ao ArcBox ($expectedPackageName).")
        }

        val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }

        if (apkVersionCode < currentVersionCode) {
            return ValidationResult.Failed("A versão baixada ($apkVersionCode) é inferior à versão instalada ($currentVersionCode). Downgrades são impedidos por segurança.")
        }

        return ValidationResult.Success(
            packageName = apkPackage,
            versionCode = apkVersionCode,
            versionName = packageInfo.versionName ?: "Desconhecida"
        )
    }

    sealed class ValidationResult {
        data class Success(val packageName: String, val versionCode: Int, val versionName: String) : ValidationResult()
        data class Failed(val reason: String) : ValidationResult()
    }
}
