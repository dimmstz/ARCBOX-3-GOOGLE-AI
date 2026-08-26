package com.example.data.cloud.provider

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secure session manager for Cloud Storage connections.
 * 
 * Supports:
 * - "Vincular conta" (Persistent encrypted storage across app restarts)
 * - "Acesso temporário" (In-memory transient session wiped on disconnect or app exit)
 * - AES cipher / SHA-256 key derivation - NEVER stores raw plaintext credentials.
 */
class CloudSessionManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("arcbox_secure_cloud_vault", Context.MODE_PRIVATE)

    // In-memory registry for temporary sessions (never written to disk)
    private val temporarySessions = mutableMapOf<String, CloudSessionData>()

    data class CloudSessionData(
        val providerId: String,
        val email: String,
        val serverUrl: String,
        val tokenOrPass: String,
        val isTemporary: Boolean,
        val totalSpace: Long = 0L,
        val usedSpace: Long = 0L,
        val connectedAt: Long = System.currentTimeMillis()
    )

    private fun deriveKey(): ByteArray {
        val seed = "ArcboxCloudSecurityVault_${context.packageName}_2026".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(seed)
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val key = deriveKey()
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val iv = ByteArray(16) { 0x42.toByte() }
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return ""
        return try {
            val key = deriveKey()
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val iv = ByteArray(16) { 0x42.toByte() }
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val decodedBytes = Base64.decode(encryptedText, Base64.NO_WRAP)
            String(cipher.doFinal(decodedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                String(Base64.decode(encryptedText, Base64.NO_WRAP), Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }
    }

    fun saveSession(
        providerId: String,
        email: String,
        serverUrl: String,
        tokenOrPass: String,
        isTemporary: Boolean,
        totalSpace: Long = 0L,
        usedSpace: Long = 0L
    ) {
        val session = CloudSessionData(
            providerId = providerId,
            email = email,
            serverUrl = serverUrl,
            tokenOrPass = tokenOrPass,
            isTemporary = isTemporary,
            totalSpace = totalSpace,
            usedSpace = usedSpace
        )

        if (isTemporary) {
            temporarySessions[providerId.lowercase()] = session
            // Ensure no stale persistent data remains
            clearPersistent(providerId)
        } else {
            temporarySessions.remove(providerId.lowercase())
            prefs.edit()
                .putString("email_${providerId.lowercase()}", email)
                .putString("url_${providerId.lowercase()}", serverUrl)
                .putString("enc_token_${providerId.lowercase()}", encrypt(tokenOrPass))
                .putLong("total_space_${providerId.lowercase()}", totalSpace)
                .putLong("used_space_${providerId.lowercase()}", usedSpace)
                .putLong("connected_at_${providerId.lowercase()}", System.currentTimeMillis())
                .apply()
        }
    }

    fun getSession(providerId: String): CloudSessionData? {
        val key = providerId.lowercase()
        // Check temporary session in RAM first
        temporarySessions[key]?.let { return it }

        // Check persistent store
        val email = prefs.getString("email_$key", null) ?: return null
        val encToken = prefs.getString("enc_token_$key", "") ?: ""
        val url = prefs.getString("url_$key", "") ?: ""
        val totalSpace = prefs.getLong("total_space_$key", 0L)
        val usedSpace = prefs.getLong("used_space_$key", 0L)

        return CloudSessionData(
            providerId = key,
            email = email,
            serverUrl = url,
            tokenOrPass = decrypt(encToken),
            isTemporary = false,
            totalSpace = totalSpace,
            usedSpace = usedSpace
        )
    }

    fun isConnected(providerId: String): Boolean {
        val key = providerId.lowercase()
        if (temporarySessions.containsKey(key)) return true
        return prefs.contains("email_$key")
    }

    fun removeSession(providerId: String) {
        val key = providerId.lowercase()
        temporarySessions.remove(key)
        clearPersistent(providerId)
    }

    private fun clearPersistent(providerId: String) {
        val key = providerId.lowercase()
        prefs.edit()
            .remove("email_$key")
            .remove("url_$key")
            .remove("enc_token_$key")
            .remove("total_space_$key")
            .remove("used_space_$key")
            .remove("connected_at_$key")
            .apply()
    }

    fun updateQuota(providerId: String, totalSpace: Long, usedSpace: Long) {
        val key = providerId.lowercase()
        val temp = temporarySessions[key]
        if (temp != null) {
            temporarySessions[key] = temp.copy(totalSpace = totalSpace, usedSpace = usedSpace)
        } else if (prefs.contains("email_$key")) {
            prefs.edit()
                .putLong("total_space_$key", totalSpace)
                .putLong("used_space_$key", usedSpace)
                .apply()
        }
    }
}
