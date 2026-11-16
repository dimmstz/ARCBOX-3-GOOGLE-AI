package com.example.data.cloud.provider.mega

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class MegaNode(
    val handle: String,
    val parentHandle: String?,
    val type: Int, // 0 = file, 1 = folder, 2 = root, 3 = inbox, 4 = trash
    val name: String,
    val size: Long,
    val timestamp: Long,
    val keyBytes: ByteArray? = null
)

data class MegaAccountQuota(
    val totalBytes: Long,
    val usedBytes: Long,
    val email: String
)

/**
 * Official MEGA API client protocol implementation.
 * 
 * Communicates with https://g.api.mega.co.nz/cs using JSON RPC payloads,
 * official X-Hashcash Proof-of-Work solver (HTTP 402 challenge resolution),
 * AES-128 cryptographic key derivation, URL-safe Base64 encoding/decoding,
 * and chunked upload/download streams.
 */
class MegaApiClient(
    private val client: OkHttpClient
) {
    private val sequenceId = AtomicLong(System.currentTimeMillis() % 10000000L)
    private val megaApiEndpoint = "https://g.api.mega.co.nz/cs"

    // Session ID and derived Master Key
    var sessionId: String? = null
        private set
    private var masterKey: ByteArray? = null
    @Volatile
    var lastErrorMessage: String? = null
        private set
    private val nodeCacheMap = java.util.concurrent.ConcurrentHashMap<String, MegaNode>()
    private val decryptedAttrCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, ByteArray?>>()

    val currentMasterKey: ByteArray?
        get() = masterKey

    fun setSession(sid: String, key: ByteArray? = null) {
        if (sid.contains(":::")) {
            this.sessionId = sid.substringBefore(":::")
            val b64Key = sid.substringAfter(":::")
            this.masterKey = key ?: (try { Base64.decode(b64Key, Base64.NO_WRAP) } catch (_: Exception) { null }) ?: ByteArray(16) { 0 }
        } else {
            this.sessionId = sid
            this.masterKey = key ?: ByteArray(16) { 0 }
        }
    }

    fun clearSession() {
        this.sessionId = null
        this.masterKey = null
        this.nodeCacheMap.clear()
        this.decryptedAttrCache.clear()
    }

    /**
     * Executes an API request to MEGA endpoint, handling HTTP 402 X-Hashcash challenges automatically.
     */
    private suspend fun executeMegaRequest(payload: JSONArray, sid: String? = null): String = withContext(Dispatchers.IO) {
        val currentSid = (sid ?: sessionId)?.substringBefore(":::")
        val url = if (!currentSid.isNullOrBlank()) {
            "$megaApiEndpoint?id=${sequenceId.incrementAndGet()}&sid=$currentSid"
        } else {
            "$megaApiEndpoint?id=${sequenceId.incrementAndGet()}"
        }

        val payloadStr = payload.toString()
        val mediaType = "application/json".toMediaTypeOrNull()
        val reqBuilder = Request.Builder()
            .url(url)
            .post(payloadStr.toRequestBody(mediaType))
            .header("User-Agent", "Arcbox-FileManager-Android/2.4")

        var response = client.newCall(reqBuilder.build()).execute()
        var hashcashAttempts = 0

        // Handle MEGA HTTP 402 Hashcash challenge safely without reusing a closed Response
        while (response.code == 402 && hashcashAttempts < 2) {
            hashcashAttempts++
            val challenge = response.header("X-Hashcash")
            response.close()

            if (challenge.isNullOrBlank()) {
                lastErrorMessage = "Servidor MEGA retornou HTTP 402 sem desafio X-Hashcash."
                throw IllegalStateException(lastErrorMessage)
            }

            Log.d("MegaApiClient", "Received X-Hashcash challenge (attempt $hashcashAttempts): $challenge")
            val solvedProof = solveHashcash(challenge)
            if (solvedProof.isNullOrBlank()) {
                lastErrorMessage = "Não foi possível concluir a validação Hashcash do MEGA dentro do tempo limite."
                throw IllegalStateException(lastErrorMessage)
            }

            Log.d("MegaApiClient", "Solved X-Hashcash header: $solvedProof")
            val retryReq = Request.Builder()
                .url(url)
                .post(payloadStr.toRequestBody(mediaType))
                .header("User-Agent", "Arcbox-FileManager-Android/2.4")
                .header("X-Hashcash", solvedProof)
                .build()
            response = client.newCall(retryReq).execute()
        }

        if (response.code == 402) {
            response.close()
            lastErrorMessage = "Validação anti-spam Hashcash recusada pelo servidor MEGA (HTTP 402)."
            throw IllegalStateException(lastErrorMessage)
        }

        return@withContext response.use { resp ->
            resp.body?.string()?.trim() ?: ""
        }
    }

    /**
     * Solves the official MEGA X-Hashcash challenge using multi-threaded Proof of Work.
     * Challenge format: 1:<easiness>:<timestamp>:<b64token>
     * Buffer size: 4 bytes nonce + 262144 * 48 bytes token (12.58 MB)
     */
    private suspend fun solveHashcash(challenge: String): String? = withContext(Dispatchers.Default) {
        kotlinx.coroutines.withTimeoutOrNull(30_000L) {
            try {
                val parts = challenge.split(":")
                if (parts.size < 4 || parts[0] != "1") {
                    Log.w("MegaApiClient", "Unsupported hashcash challenge format: $challenge")
                    return@withTimeoutOrNull null
                }

                val easiness = parts[1].toIntOrNull() ?: 192
                val b64token = parts[3]
                val tokenBin = base64UrlDecode(b64token)
                if (tokenBin.size != 48) {
                    Log.w("MegaApiClient", "Invalid token length: ${tokenBin.size}")
                    return@withTimeoutOrNull null
                }

                // Target difficulty threshold calculation according to MEGA SDK
                val threshold = (((easiness and 63) shl 1) + 1).toLong() shl ((easiness shr 6) * 7 + 3)
                val thresholdUnsigned = threshold and 0xFFFFFFFFL

                // 262144 repeats of 48 bytes = 8 tiles of 32768 repeats (1,572,864 bytes shared read-only across workers)
                val kTokenBytes = 48
                val kTileRepeats = 32768
                val kNumTiles = 8
                val kTileBytes = kTileRepeats * kTokenBytes
                val sharedTile = ByteArray(kTileBytes)
                System.arraycopy(tokenBin, 0, sharedTile, 0, kTokenBytes)
                var filled = kTokenBytes
                while (filled < kTileBytes) {
                    val copyLen = Math.min(filled, kTileBytes - filled)
                    System.arraycopy(sharedTile, 0, sharedTile, filled, copyLen)
                    filled += copyLen
                }

                val numWorkers = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
                val stop = AtomicBoolean(false)
                val winningNonce = AtomicReference<Int?>(null)

                coroutineScope {
                    val jobs = (0 until numWorkers).map { workerIndex ->
                        launch(Dispatchers.Default) {
                            val nonceBytes = ByteArray(4)
                            val md = MessageDigest.getInstance("SHA-256")
                            val stride = numWorkers
                            var n = workerIndex

                            while (isActive && !stop.get() && n < 10_000_000) {
                                nonceBytes[0] = (n ushr 24).toByte()
                                nonceBytes[1] = (n ushr 16).toByte()
                                nonceBytes[2] = (n ushr 8).toByte()
                                nonceBytes[3] = n.toByte()

                                md.reset()
                                md.update(nonceBytes)
                                for (t in 0 until kNumTiles) {
                                    md.update(sharedTile)
                                }
                                val digest = md.digest()

                                val firstWord = ((digest[0].toLong() and 0xFF) shl 24) or
                                        ((digest[1].toLong() and 0xFF) shl 16) or
                                        ((digest[2].toLong() and 0xFF) shl 8) or
                                        (digest[3].toLong() and 0xFF)

                                if (firstWord <= thresholdUnsigned) {
                                    if (stop.compareAndSet(false, true)) {
                                        winningNonce.set(n)
                                    }
                                    break
                                }

                                n += stride
                            }
                        }
                    }
                    jobs.joinAll()
                }

                val found = winningNonce.get() ?: return@withTimeoutOrNull null
                val nonceBytes = byteArrayOf(
                    (found ushr 24).toByte(),
                    (found ushr 16).toByte(),
                    (found ushr 8).toByte(),
                    found.toByte()
                )
                val nonceB64 = megaBtoa(nonceBytes)
                "1:$b64token:$nonceB64"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MegaApiClient", "Error solving Hashcash", e)
                null
            }
        }
    }

    /**
     * Modified Base64 encoding according to MEGA specification (no padding, -_ symbols, 4 bytes -> 6 chars).
     */
    private fun megaBtoa(data: ByteArray): String {
        val to64 = { c: Int ->
            val v = c and 63
            when {
                v < 26 -> ('A'.code + v).toChar()
                v < 52 -> ('a'.code + (v - 26)).toChar()
                v < 62 -> ('0'.code + (v - 52)).toChar()
                v == 62 -> '-'
                else -> '_'
            }
        }
        val sb = StringBuilder()
        var i = 0
        var blen = data.size
        while (blen > 0) {
            val b0 = data[i].toInt() and 0xFF
            sb.append(to64(b0 ushr 2))
            val b1 = if (blen > 1) data[i + 1].toInt() and 0xFF else 0
            sb.append(to64(((b0 shl 4) and 63) or (b1 ushr 4)))
            if (blen < 2) break
            val b2 = if (blen > 2) data[i + 2].toInt() and 0xFF else 0
            sb.append(to64(((b1 shl 2) and 63) or (b2 ushr 6)))
            if (blen < 3) break
            sb.append(to64(b2 and 63))
            i += 3
            blen -= 3
        }
        return sb.toString()
    }

    /**
     * Authenticate user against official MEGA API.
     * Supports:
     * 1) Direct session token (sid)
     * 2) Standard credentials (email + password) with v1/v2 challenge response and AES/PBKDF2 key derivation
     * 3) Safe local offline mode ("local_mega" or "direct_cloud_session")
     */
    suspend fun login(email: String, passwordOrToken: String): Result<MegaAccountQuota> = withContext(Dispatchers.IO) {
        try {
            val token = passwordOrToken.trim()
            val cleanEmail = email.trim().ifBlank {
                if (token.length > 20) "usuario.mega@arcbox.cloud" else ""
            }

            // Explicit local offline workspace only
            if (token == "local_mega") {
                sessionId = "local_mega"
                masterKey = ByteArray(16) { 0 }
                lastErrorMessage = null
                return@withContext Result.success(
                    MegaAccountQuota(
                        totalBytes = 50L * 1024 * 1024 * 1024L,
                        usedBytes = 0L,
                        email = cleanEmail.ifBlank { "local@mega.storage" }
                    )
                )
            }

            // Reject synthetic placeholder tokens from failed WebView/quick-connect captures
            if (token == "direct_cloud_session" || token.startsWith("web_auth_token_") || token.startsWith("web_session_")) {
                return@withContext Result.failure(
                    IllegalStateException("Não foi possível capturar a senha na tela web. Use a aba 'E-mail e Senha' ou 'Chave / Token'.")
                )
            }

            // Direct MEGA Session ID (via token input)
            if (token.length > 20 && !token.contains(" ") && !token.contains("@")) {
                sessionId = token
                masterKey = ByteArray(16) { 0 }
                val effectiveEmail = cleanEmail.ifBlank { "usuario.mega@arcbox.cloud" }
                val quota = try {
                    getQuota(effectiveEmail)
                } catch (_: Exception) {
                    MegaAccountQuota(50L * 1024 * 1024 * 1024L, 0L, effectiveEmail)
                }
                if (lastErrorMessage != null) {
                    return@withContext Result.failure(IllegalStateException(lastErrorMessage))
                }
                return@withContext Result.success(
                    if (quota.totalBytes > 0) quota else MegaAccountQuota(50L * 1024 * 1024 * 1024L, 0L, effectiveEmail)
                )
            }

            if (cleanEmail.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Por favor, insira o seu e-mail do MEGA."))
            }

            if (token.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Por favor, insira a sua senha ou chave de sessão."))
            }

            // Step 1: Request user challenge from MEGA endpoint (us0)
            val reqPayload = JSONArray().apply {
                put(JSONObject().apply {
                    put("a", "us0")
                    put("user", cleanEmail.lowercase())
                })
            }

            val responseStr = executeMegaRequest(reqPayload)

            if (!responseStr.startsWith("[")) {
                return@withContext Result.failure(IllegalStateException("Resposta inválida dos servidores do MEGA: $responseStr"))
            }

            val jsonArray = JSONArray(responseStr)
            if (jsonArray.length() == 0) {
                return@withContext Result.failure(IllegalStateException("Resposta vazia da API do MEGA"))
            }

            val firstItem = jsonArray.get(0)
            if (firstItem is Int && firstItem < 0) {
                val errMsg = when (firstItem) {
                    -9 -> "Conta não cadastrada no MEGA. Verifique o e-mail digitado."
                    -3 -> "Servidor do MEGA temporariamente ocupado (-3). Tente novamente."
                    -15 -> "Sessão ou credenciais expiradas (-15)"
                    -16 -> "Acesso bloqueado temporariamente pelo MEGA por tentativas excessivas (-16)."
                    else -> "Erro na verificação da conta MEGA (Código $firstItem)"
                }
                return@withContext Result.failure(IllegalStateException(errMsg))
            }

            val jsonObj = firstItem as JSONObject
            val version = jsonObj.optInt("v", 1)
            val tsid = jsonObj.optString("tsid", "")
            val csid = jsonObj.optString("csid", "")
            val kStr = jsonObj.optString("k", "")

            var pwKey: ByteArray
            var userHash: String

            if (version >= 2 && jsonObj.has("s")) {
                val saltStr = jsonObj.getString("s")
                val salt = base64UrlDecode(saltStr)
                val derivedKey = try {
                    val spec = PBEKeySpec(token.toCharArray(), salt, 100_000, 256)
                    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
                    factory.generateSecret(spec).encoded
                } catch (e: Exception) {
                    Log.w("MegaApiClient", "PBKDF2 SHA512 fallback to SHA1: ${e.message}")
                    val spec = PBEKeySpec(token.toCharArray(), salt, 100_000, 256)
                    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                    factory.generateSecret(spec).encoded
                }

                val dek = derivedKey.copyOfRange(0, 16)
                val dak = derivedKey.copyOfRange(16, 32)

                pwKey = dek
                userHash = base64UrlEncode(dak)
            } else {
                pwKey = prepareKey(token.toByteArray(Charsets.UTF_8))
                userHash = stringHash(cleanEmail.lowercase(), pwKey)
            }

            // Step 2: Submit login challenge response with derived user hash (uh)
            val loginPayload = JSONArray().apply {
                put(JSONObject().apply {
                    put("a", "us")
                    put("user", cleanEmail.lowercase())
                    put("uh", userHash)
                })
            }

            val loginRespStr = executeMegaRequest(loginPayload)

            if (!loginRespStr.startsWith("[")) {
                return@withContext Result.failure(IllegalStateException("Erro na autenticação: $loginRespStr"))
            }

            val loginArray = JSONArray(loginRespStr)
            if (loginArray.length() == 0) {
                return@withContext Result.failure(IllegalStateException("Resposta de login vazia do MEGA"))
            }

            val loginFirst = loginArray.get(0)
            if (loginFirst is Int && loginFirst < 0) {
                val errorMsg = when (loginFirst) {
                    -3 -> "Senha incorreta no MEGA (-3)."
                    -9 -> "Conta de usuário não encontrada no MEGA (-9)."
                    -15 -> "Credenciais inválidas ou sessão expirada (-15)."
                    -16 -> "Acesso temporariamente bloqueado pelo MEGA (-16)."
                    -26 -> "Esta conta MEGA possui 2FA ativo. Conecte colando o Token de Sessão (sid) na aba Token."
                    else -> "Falha na autenticação MEGA (Código $loginFirst)."
                }
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            if (loginFirst is JSONObject) {
                val authObj = loginFirst
                val encK = authObj.optString("k", kStr)
                masterKey = if (encK.isNotBlank()) {
                    try {
                        decryptMasterKey(encK, pwKey)
                    } catch (e: Exception) {
                        Log.w("MegaApiClient", "Could not decrypt master key: ${e.message}")
                        pwKey
                    }
                } else {
                    pwKey
                }

                val privkStr = authObj.optString("privk", "")
                val csidStr = authObj.optString("csid", csid)
                val tsidStr = authObj.optString("tsid", tsid)

                val directSid = if (authObj.has("sid")) {
                    authObj.getString("sid")
                } else if (csidStr.isNotBlank() && privkStr.isNotBlank()) {
                    val decrypted = decryptCsid(csidStr, privkStr, masterKey ?: pwKey)
                    if (!decrypted.isNullOrBlank()) {
                        decrypted
                    } else if (tsidStr.isNotBlank()) {
                        decryptTsid(tsidStr, masterKey ?: pwKey)
                    } else {
                        csidStr
                    }
                } else if (tsidStr.isNotBlank()) {
                    try {
                        decryptTsid(tsidStr, masterKey ?: pwKey)
                    } catch (e: Exception) {
                        tsidStr
                    }
                } else if (csidStr.isNotBlank()) {
                    csidStr
                } else {
                    ""
                }
                sessionId = directSid
            } else {
                sessionId = if (tsid.isNotEmpty()) tsid else csid
                masterKey = pwKey
            }

            if (sessionId.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("Não foi possível obter o identificador de sessão do MEGA."))
            }

            val quota = getQuota(cleanEmail)
            Result.success(quota)
        } catch (e: Exception) {
            Log.e("MegaApiClient", "Login failed", e)
            Result.failure(e)
        }
    }

    private fun decryptMasterKey(encryptedKeyBase64: String, passwordKey: ByteArray): ByteArray {
        val encrypted = base64UrlDecode(encryptedKeyBase64)
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(passwordKey, "AES"))
        val decrypted = cipher.doFinal(encrypted)
        return decrypted.copyOfRange(0, 16)
    }

    private fun decryptTsid(tsidB64: String, key: ByteArray): String {
        val enc = base64UrlDecode(tsidB64)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        val decrypted = cipher.doFinal(enc)
        val len = decrypted.indexOf(0.toByte()).takeIf { it in 1..43 } ?: Math.min(decrypted.size, 43)
        return base64UrlEncode(decrypted.copyOfRange(0, len))
    }

    private fun decryptCsid(csidB64: String, privkB64: String, masterKey: ByteArray): String? {
        return try {
            val encPrivk = base64UrlDecode(privkB64)
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(masterKey, "AES"))
            val privkPlain = cipher.doFinal(encPrivk)

            var offset = 0
            fun readMpi(): BigInteger? {
                if (offset + 2 > privkPlain.size) return null
                val bitLen = ((privkPlain[offset].toInt() and 0xFF) shl 8) or (privkPlain[offset + 1].toInt() and 0xFF)
                val byteLen = (bitLen + 7) / 8
                if (offset + 2 + byteLen > privkPlain.size) return null
                val bytes = privkPlain.copyOfRange(offset + 2, offset + 2 + byteLen)
                offset += 2 + byteLen
                return BigInteger(1, bytes)
            }

            val p = readMpi() ?: return null
            val q = readMpi() ?: return null
            val d = readMpi() ?: return null
            val u = readMpi() ?: return null

            val n = p.multiply(q)

            val encCsid = base64UrlDecode(csidB64)
            val cBitLen = ((encCsid[0].toInt() and 0xFF) shl 8) or (encCsid[1].toInt() and 0xFF)
            val cByteLen = (cBitLen + 7) / 8
            val cBytes = encCsid.copyOfRange(2, 2 + cByteLen)
            val c = BigInteger(1, cBytes)

            val m = c.modPow(d, n)
            val mBytes = m.toByteArray()
            val noSign = if (mBytes.isNotEmpty() && mBytes[0] == 0.toByte()) mBytes.copyOfRange(1, mBytes.size) else mBytes
            val sidBytes = if (noSign.size >= 43) noSign.copyOfRange(0, 43) else noSign
            val decryptedSid = base64UrlEncode(sidBytes)
            Log.d("MegaApiClient", "Decrypted RSA CSID successfully")
            decryptedSid
        } catch (e: Exception) {
            Log.e("MegaApiClient", "Failed to decrypt CSID", e)
            null
        }
    }

    /**
     * Query account quota from MEGA API.
     */
    suspend fun getQuota(email: String): MegaAccountQuota = withContext(Dispatchers.IO) {
        val defaultTotal = 50L * 1024 * 1024 * 1024L
        val sid = sessionId ?: return@withContext MegaAccountQuota(defaultTotal, 0L, email)

        try {
            val reqPayload = JSONArray().apply {
                put(JSONObject().apply {
                    put("a", "uq")
                    put("strg", 1)
                })
            }

            val respStr = executeMegaRequest(reqPayload, sid)
            if (respStr.startsWith("[")) {
                val array = JSONArray(respStr)
                if (array.length() > 0 && array.get(0) is JSONObject) {
                    val obj = array.getJSONObject(0)
                    val usedBytes = obj.optLong("mstrg", 0L)
                    val totalBytes = obj.optLong("cstrg", defaultTotal).takeIf { it > 0 } ?: defaultTotal
                    return@withContext MegaAccountQuota(totalBytes, usedBytes, email)
                }
            }
        } catch (e: Exception) {
            Log.w("MegaApiClient", "Quota fetch error: ${e.message}")
        }
        MegaAccountQuota(defaultTotal, 0L, email)
    }

    /**
     * Fetch user file and directory node tree from MEGA API.
     */
    suspend fun fetchNodes(): List<MegaNode> = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext emptyList()

        try {
            val reqPayload = JSONArray().apply {
                put(JSONObject().apply {
                    put("a", "f")
                    put("c", 1)
                    put("r", 1)
                })
            }

            val respStr = executeMegaRequest(reqPayload, sid)
            if (!respStr.startsWith("[")) {
                lastErrorMessage = parseMegaErrorCode(respStr) ?: "Resposta inválida do MEGA ao listar arquivos."
                return@withContext emptyList()
            }

            val array = JSONArray(respStr)
            if (array.length() == 0) return@withContext emptyList()
            val first = array.get(0)
            if (first is Int && first < 0) {
                lastErrorMessage = megaErrorCodeToMessage(first)
                return@withContext emptyList()
            }
            if (first !is JSONObject) return@withContext emptyList()

            lastErrorMessage = null
            val rootObj = first
            val fArray = rootObj.optJSONArray("f") ?: return@withContext emptyList()

            val nodes = mutableListOf<MegaNode>()
            for (i in 0 until fArray.length()) {
                val nodeObj = fArray.getJSONObject(i)
                val handle = nodeObj.optString("h")
                val parentHandle = nodeObj.optString("p").takeIf { it.isNotBlank() }
                val type = nodeObj.optInt("t", 0)
                val size = nodeObj.optLong("s", 0L)
                val ts = nodeObj.optLong("ts", System.currentTimeMillis() / 1000) * 1000L
                val attrStr = nodeObj.optString("a")
                val kStr = nodeObj.optString("k")

                val (parsedName, decKeyBytes) = decryptNodeNameAndKey(attrStr, kStr, type, handle)
                val node = MegaNode(
                    handle = handle,
                    parentHandle = parentHandle,
                    type = type,
                    name = parsedName,
                    size = size,
                    timestamp = ts,
                    keyBytes = decKeyBytes
                )
                nodes.add(node)
                nodeCacheMap[handle] = node
            }
            nodes
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            lastErrorMessage = "Erro de conexão com o MEGA: ${e.localizedMessage ?: e.message}"
            Log.e("MegaApiClient", "Fetch nodes failed", e)
            emptyList()
        }
    }

    /**
     * Creates a new folder on MEGA servers.
     */
    suspend fun createFolder(parentHandle: String, folderName: String): Result<MegaNode> = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext Result.failure(IllegalStateException("Não conectado ao MEGA"))
        if (sid == "local_mega") {
            return@withContext Result.success(
                MegaNode(generateRandomHandle(), parentHandle, 1, folderName, 0L, System.currentTimeMillis(), null)
            )
        }
        val mKey = masterKey ?: return@withContext Result.failure(IllegalStateException("Chave mestra MEGA ausente. Reconecte sua conta."))

        try {
            val randomHandle = generateRandomHandle()
            val folderKey = ByteArray(16).apply { SecureRandom().nextBytes(this) }
            val encAttr = encodeNodeAttributes(folderName, folderKey)
            val encFolderKey = encryptAesEcb(folderKey, mKey)

            val reqPayload = JSONArray().apply {
                put(JSONObject().apply {
                    put("a", "p")
                    put("t", parentHandle)
                    put("n", JSONArray().apply {
                        put(JSONObject().apply {
                            put("h", randomHandle)
                            put("t", 1) // folder
                            put("a", encAttr)
                            put("k", base64UrlEncode(encFolderKey))
                        })
                    })
                })
            }

            val respStr = executeMegaRequest(reqPayload, sid)
            val err = parseMegaErrorCode(respStr)
            if (err != null) {
                lastErrorMessage = err
                return@withContext Result.failure(IllegalStateException(err))
            }
            if (!respStr.startsWith("[")) {
                return@withContext Result.failure(IllegalStateException("Resposta inválida ao criar pasta no MEGA"))
            }

            val arr = JSONArray(respStr)
            val respObj = arr.optJSONObject(0)
            val createdObj = respObj?.optJSONArray("f")?.optJSONObject(0)
            val realHandle = createdObj?.optString("h")?.takeIf { it.isNotBlank() } ?: randomHandle
            val ts = (createdObj?.optLong("ts", System.currentTimeMillis() / 1000L) ?: (System.currentTimeMillis() / 1000L)) * 1000L

            lastErrorMessage = null
            val node = MegaNode(
                handle = realHandle,
                parentHandle = parentHandle,
                type = 1,
                name = folderName,
                size = 0L,
                timestamp = ts,
                keyBytes = folderKey
            )
            nodeCacheMap[realHandle] = node
            Result.success(node)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            lastErrorMessage = "Erro ao criar pasta no MEGA: ${e.message}"
            Result.failure(e)
        }
    }

    /**
     * Download a file from MEGA.
     */
    suspend fun downloadFile(
        nodeHandle: String,
        destinationFile: File,
        passedKeyBytes: ByteArray? = null,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val rawSid = sessionId ?: return@withContext false
        val sid = rawSid.substringBefore(":::")

        var tempFile: File? = null

        try {
            var keyBytes = passedKeyBytes ?: nodeCacheMap[nodeHandle]?.keyBytes
            if (keyBytes == null || keyBytes.isEmpty()) {
                fetchNodes()
                keyBytes = passedKeyBytes ?: nodeCacheMap[nodeHandle]?.keyBytes
            }
            var finalKeyBytes = keyBytes ?: masterKey

            val reqPayload = JSONArray().apply {
                put(JSONObject().apply {
                    put("a", "g")
                    put("g", 1)
                    put("n", nodeHandle)
                })
            }

            var respStr = executeMegaRequest(reqPayload, sid)
            if (respStr.startsWith("[-") || respStr.startsWith("-")) {
                Log.w("MegaApiClient", "Download request returned error code: $respStr, refreshing nodes...")
                fetchNodes()
                val retrySid = (sessionId ?: sid).substringBefore(":::")
                respStr = executeMegaRequest(reqPayload, retrySid)
            }
            if (!respStr.startsWith("[")) {
                Log.e("MegaApiClient", "Invalid MEGA API download response: $respStr")
                return@withContext false
            }

            val array = JSONArray(respStr)
            if (array.length() == 0) return@withContext false
            val first = array.get(0)
            if (first !is JSONObject) {
                Log.w("MegaApiClient", "Download URL request returned non-object: $respStr")
                return@withContext false
            }

            val downloadUrl = first.optString("g")
            if (downloadUrl.isBlank()) {
                Log.e("MegaApiClient", "Download URL 'g' is empty in response: $first")
                return@withContext false
            }
            val expectedSize = first.optLong("s", -1L)

            // If returned payload contains file key and we don't have keyBytes, decrypt it
            if ((finalKeyBytes == null || finalKeyBytes.size < 16) && first.has("k")) {
                val respKStr = first.optString("k")
                if (respKStr.isNotBlank()) {
                    val (_, decK) = decryptNodeNameAndKey("", respKStr, 0, nodeHandle)
                    if (decK != null) {
                        finalKeyBytes = decK
                    }
                }
            }

            val downloadReq = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/119.0 Firefox/119.0")
                .get()
                .build()

            val parentDir = destinationFile.parentFile ?: destinationFile.absoluteFile.parentFile
            parentDir?.mkdirs()
            
            // Delete existing partial file if any
            if (destinationFile.exists()) destinationFile.delete()
            destinationFile.createNewFile()
            tempFile = destinationFile

            val downloadClient = client.newBuilder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.MINUTES)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            // Handle 0-byte file edge case immediately
            if (expectedSize == 0L) {
                if (destinationFile.exists()) destinationFile.delete()
                destinationFile.createNewFile()
                onProgress(1f)
                Log.d("MegaApiClient", "Download succeeded (0-byte file): ${destinationFile.absolutePath}")
                return@withContext true
            }

            var totalBytesRead = 0L
            downloadClient.newCall(downloadReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e("MegaApiClient", "Download HTTP error code ${resp.code}: ${resp.message}")
                    return@withContext false
                }
                val body = resp.body ?: run {
                    Log.e("MegaApiClient", "Download response body is null")
                    return@withContext false
                }
                val totalBytes = if (expectedSize > 0) expectedSize else body.contentLength().coerceAtLeast(1L)
                val rawInput = body.byteStream()

                val isEncrypted = finalKeyBytes != null && finalKeyBytes.isNotEmpty()
                rawInput.use { input ->
                    FileOutputStream(tempFile).use { fos ->
                        java.io.BufferedOutputStream(fos, 64 * 1024).use { output ->
                            if (isEncrypted) {
                                val aesKey = ByteArray(16) { i ->
                                    if (finalKeyBytes!!.size >= 32) (finalKeyBytes[i].toInt() xor finalKeyBytes[i + 16].toInt()).toByte()
                                    else finalKeyBytes[i]
                                }
                                val iv = ByteArray(16)
                                if (finalKeyBytes!!.size >= 24) {
                                    System.arraycopy(finalKeyBytes, 16, iv, 0, 8)
                                }
                                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))

                                val inBuffer = ByteArray(64 * 1024)
                                val outBuffer = ByteArray(64 * 1024 + 16)
                                var read: Int
                                var transferred = 0L

                                var lastReportedProgress = -1f
                                var lastReportedTime = 0L

                                java.io.BufferedInputStream(input, 64 * 1024).use { bis ->
                                    while (bis.read(inBuffer).also { read = it } != -1) {
                                        if (read > 0) {
                                            totalBytesRead += read
                                            val decLen = cipher.update(inBuffer, 0, read, outBuffer, 0)
                                            if (decLen > 0) {
                                                output.write(outBuffer, 0, decLen)
                                            }
                                            transferred += read
                                            if (totalBytes > 0) {
                                                val p = (transferred.toFloat() / totalBytes).coerceIn(0f, 1f)
                                                val now = System.currentTimeMillis()
                                                if (p >= 1f || p - lastReportedProgress >= 0.02f || now - lastReportedTime >= 150L) {
                                                    lastReportedProgress = p
                                                    lastReportedTime = now
                                                    onProgress(p)
                                                }
                                            }
                                        }
                                    }
                                }
                                val finalLen = cipher.doFinal(outBuffer, 0)
                                if (finalLen > 0) {
                                    output.write(outBuffer, 0, finalLen)
                                }
                            } else {
                                val inBuffer = ByteArray(64 * 1024)
                                var read: Int
                                var transferred = 0L
                                var lastReportedProgress = -1f
                                var lastReportedTime = 0L

                                java.io.BufferedInputStream(input, 64 * 1024).use { bis ->
                                    while (bis.read(inBuffer).also { read = it } != -1) {
                                        if (read > 0) {
                                            totalBytesRead += read
                                            output.write(inBuffer, 0, read)
                                            transferred += read
                                            if (totalBytes > 0) {
                                                val p = (transferred.toFloat() / totalBytes).coerceIn(0f, 1f)
                                                val now = System.currentTimeMillis()
                                                if (p >= 1f || p - lastReportedProgress >= 0.02f || now - lastReportedTime >= 150L) {
                                                    lastReportedProgress = p
                                                    lastReportedTime = now
                                                    onProgress(p)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                }
            }

            val tf = tempFile
            if (tf != null && tf.exists() && (tf.length() > 0 || (expectedSize == 0L || totalBytesRead == 0L))) {
                onProgress(1f)
                Log.d("MegaApiClient", "Download succeeded: ${destinationFile.absolutePath} (${destinationFile.length()} bytes)")
                true
            } else {
                tf?.delete()
                Log.e("MegaApiClient", "Download failed: temp file is empty or non-existent (read $totalBytesRead bytes, expected $expectedSize)")
                false
            }
        } catch (e: Exception) {
            try {
                if (tempFile?.exists() == true) tempFile?.delete()
            } catch (_: Exception) {}
            if (e is CancellationException) throw e
            Log.e("MegaApiClient", "Download failed with exception", e)
            false
        }
    }

    /**
     * Upload a file to MEGA with official AES-128-CTR encryption and chunked CBC-MAC integrity verification.
     */
    suspend fun uploadFile(
        localFile: File,
        parentHandle: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext false
        if (sid == "local_mega") {
            onProgress(1f)
            return@withContext true
        }
        val mKey = masterKey ?: run {
            lastErrorMessage = "Chave mestra MEGA ausente. Reconecte sua conta."
            return@withContext false
        }

        try {
            val fileSize = localFile.length()
            val reqPayload = JSONArray().apply {
                put(JSONObject().apply {
                    put("a", "u")
                    put("s", fileSize)
                })
            }

            val respStr = executeMegaRequest(reqPayload, sid)
            val initErr = parseMegaErrorCode(respStr)
            if (initErr != null || !respStr.startsWith("[")) {
                lastErrorMessage = initErr ?: "Falha ao iniciar upload no MEGA"
                return@withContext false
            }

            val array = JSONArray(respStr)
            if (array.length() == 0 || array.get(0) !is JSONObject) return@withContext false

            val uploadUrl = array.getJSONObject(0).optString("p")
            if (uploadUrl.isBlank()) return@withContext false

            val key16 = ByteArray(16).apply { SecureRandom().nextBytes(this) }
            val iv8 = ByteArray(8).apply { SecureRandom().nextBytes(this) }
            val fileMac = ByteArray(16)
            val ecbCipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key16, "AES"))
            }

            val uploadClient = client.newBuilder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(10, java.util.concurrent.TimeUnit.MINUTES)
                .readTimeout(10, java.util.concurrent.TimeUnit.MINUTES)
                .build()

            var fileHandle = ""
            if (fileSize == 0L) {
                val uploadReq = Request.Builder()
                    .url("$uploadUrl/0")
                    .post(ByteArray(0).toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                    .build()
                uploadClient.newCall(uploadReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        lastErrorMessage = "Erro HTTP ${resp.code} ao enviar arquivo vazio ao MEGA"
                        return@withContext false
                    }
                    fileHandle = resp.body?.string()?.trim() ?: ""
                }
            } else {
                // MEGA chunk sizes: 128KB, 256KB, 384KB, 512KB, 640KB, 768KB, 896KB, then 1MB
                val chunkSizes = intArrayOf(131072, 262144, 393280, 524288, 655360, 786432, 917504)
                var chunkIndex = 0
                var offset = 0L

                localFile.inputStream().buffered(64 * 1024).use { input ->
                    while (offset < fileSize) {
                        val targetSize = if (chunkIndex < chunkSizes.size) chunkSizes[chunkIndex] else 1048576
                        val currentSize = minOf(targetSize.toLong(), fileSize - offset).toInt()
                        val plainChunk = ByteArray(currentSize)
                        var totalRead = 0
                        while (totalRead < currentSize) {
                            val r = input.read(plainChunk, totalRead, currentSize - totalRead)
                            if (r == -1) break
                            totalRead += r
                        }
                        if (totalRead <= 0) break

                        // Compute chunk CBC-MAC (starting IV = iv8 || iv8)
                        val chunkMac = ByteArray(16)
                        System.arraycopy(iv8, 0, chunkMac, 0, 8)
                        System.arraycopy(iv8, 0, chunkMac, 8, 8)
                        var pos = 0
                        val block = ByteArray(16)
                        while (pos < totalRead) {
                            val blockLen = minOf(16, totalRead - pos)
                            for (b in 0 until 16) {
                                val pv = if (b < blockLen) plainChunk[pos + b] else 0
                                block[b] = (chunkMac[b].toInt() xor pv.toInt()).toByte()
                            }
                            val encBlock = ecbCipher.doFinal(block)
                            System.arraycopy(encBlock, 0, chunkMac, 0, 16)
                            pos += 16
                        }
                        for (b in 0 until 16) {
                            fileMac[b] = (fileMac[b].toInt() xor chunkMac[b].toInt()).toByte()
                        }
                        val nextFileMac = ecbCipher.doFinal(fileMac)
                        System.arraycopy(nextFileMac, 0, fileMac, 0, 16)

                        // Encrypt chunk using AES-128-CTR at block offset (offset / 16)
                        val ctrIv = ByteArray(16)
                        System.arraycopy(iv8, 0, ctrIv, 0, 8)
                        val blockCounter = offset / 16L
                        for (b in 0 until 8) {
                            ctrIv[15 - b] = ((blockCounter ushr (b * 8)) and 0xFFL).toByte()
                        }
                        val ctrCipher = Cipher.getInstance("AES/CTR/NoPadding")
                        ctrCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key16, "AES"), IvParameterSpec(ctrIv))
                        val encChunk = ctrCipher.doFinal(plainChunk, 0, totalRead)

                        val uploadReq = Request.Builder()
                            .url("$uploadUrl/$offset")
                            .post(encChunk.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                            .build()
                        val chunkResp = uploadClient.newCall(uploadReq).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                lastErrorMessage = "Erro HTTP ${resp.code} durante upload ao MEGA"
                                return@withContext false
                            }
                            resp.body?.string()?.trim() ?: ""
                        }
                        if (chunkResp.startsWith("-")) {
                            lastErrorMessage = parseMegaErrorCode(chunkResp) ?: "Erro no servidor de upload MEGA ($chunkResp)"
                            return@withContext false
                        }
                        if (chunkResp.isNotEmpty()) {
                            fileHandle = chunkResp
                        }

                        offset += totalRead
                        chunkIndex++
                        onProgress((offset.toFloat() / fileSize.toFloat()).coerceIn(0f, 0.98f))
                    }
                }
            }

            if (fileHandle.isBlank() || fileHandle.startsWith("-")) {
                lastErrorMessage = "Upload para o MEGA não retornou identificador de arquivo válido."
                return@withContext false
            }

            // Build MetaMAC (8 bytes) & full 32-byte obfuscated file key
            val metaMac = ByteArray(8)
            for (i in 0 until 4) {
                metaMac[i] = (fileMac[i].toInt() xor fileMac[i + 4].toInt()).toByte()
                metaMac[i + 4] = (fileMac[i + 8].toInt() xor fileMac[i + 12].toInt()).toByte()
            }
            val fullFileKey = ByteArray(32)
            for (i in 0 until 8) {
                fullFileKey[i] = (key16[i].toInt() xor iv8[i].toInt()).toByte()
                fullFileKey[i + 8] = (key16[i + 8].toInt() xor metaMac[i].toInt()).toByte()
            }
            System.arraycopy(iv8, 0, fullFileKey, 16, 8)
            System.arraycopy(metaMac, 0, fullFileKey, 24, 8)

            val encAttr = encodeNodeAttributes(localFile.name, key16)
            val encKey = encryptAesEcb(fullFileKey, mKey)

            val commitPayload = JSONArray().apply {
                put(JSONObject().apply {
                    put("a", "p")
                    put("t", parentHandle)
                    put("n", JSONArray().apply {
                        put(JSONObject().apply {
                            put("h", fileHandle)
                            put("t", 0) // file
                            put("a", encAttr)
                            put("k", base64UrlEncode(encKey))
                        })
                    })
                })
            }

            val commitResp = executeMegaRequest(commitPayload, sid)
            val commitErr = parseMegaErrorCode(commitResp)
            if (commitErr != null) {
                lastErrorMessage = commitErr
                return@withContext false
            }

            try {
                if (commitResp.startsWith("[")) {
                    val createdObj = JSONArray(commitResp).optJSONObject(0)?.optJSONArray("f")?.optJSONObject(0)
                    val realHandle = createdObj?.optString("h")?.takeIf { it.isNotBlank() } ?: fileHandle
                    val ts = (createdObj?.optLong("ts", System.currentTimeMillis() / 1000L) ?: (System.currentTimeMillis() / 1000L)) * 1000L
                    nodeCacheMap[realHandle] = MegaNode(
                        handle = realHandle,
                        parentHandle = parentHandle,
                        type = 0,
                        name = localFile.name,
                        size = fileSize,
                        timestamp = ts,
                        keyBytes = fullFileKey
                    )
                }
            } catch (_: Exception) {}

            lastErrorMessage = null
            onProgress(1f)
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            lastErrorMessage = "Erro no upload para o MEGA: ${e.message}"
            Log.e("MegaApiClient", "Upload failed", e)
            false
        }
    }

    /**
     * Delete node from MEGA servers.
     */
    suspend fun deleteNode(nodeHandle: String): Boolean = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext false
        if (sid == "local_mega") return@withContext true
        try {
            val reqPayload = JSONArray().apply {
                put(JSONObject().apply {
                    put("a", "d")
                    put("n", nodeHandle)
                })
            }
            val respStr = executeMegaRequest(reqPayload, sid)
            val err = parseMegaErrorCode(respStr)
            if (err != null) {
                lastErrorMessage = err
                return@withContext false
            }
            if (respStr.isBlank()) {
                lastErrorMessage = "Resposta vazia do MEGA ao excluir item."
                return@withContext false
            }
            nodeCacheMap.remove(nodeHandle)
            lastErrorMessage = null
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            lastErrorMessage = "Erro ao excluir no MEGA: ${e.message}"
            Log.e("MegaApiClient", "Delete failed", e)
            false
        }
    }

    /**
     * Rename node on MEGA servers ("a": "a").
     */
    suspend fun renameNode(nodeHandle: String, newName: String, existingKeyBytes: ByteArray?): Boolean = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext false
        if (sid == "local_mega") return@withContext true
        try {
            val rawKey = existingKeyBytes ?: nodeCacheMap[nodeHandle]?.keyBytes ?: masterKey
                ?: return@withContext false
            val aesKey = if (rawKey.size >= 32) {
                ByteArray(16) { i -> (rawKey[i].toInt() xor rawKey[i + 16].toInt()).toByte() }
            } else if (rawKey.size >= 16) {
                rawKey.copyOfRange(0, 16)
            } else {
                return@withContext false
            }
            val encAttr = encodeNodeAttributes(newName, aesKey)
            val reqPayload = JSONArray().apply {
                put(JSONObject().apply {
                    put("a", "a")
                    put("n", nodeHandle)
                    put("attr", encAttr)
                })
            }
            val respStr = executeMegaRequest(reqPayload, sid)
            val err = parseMegaErrorCode(respStr)
            if (err != null) {
                lastErrorMessage = err
                return@withContext false
            }
            if (respStr.isBlank()) {
                lastErrorMessage = "Resposta vazia do MEGA ao renomear item."
                return@withContext false
            }
            nodeCacheMap[nodeHandle]?.let { old ->
                nodeCacheMap[nodeHandle] = old.copy(name = newName)
            }
            lastErrorMessage = null
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            lastErrorMessage = "Erro ao renomear no MEGA: ${e.message}"
            Log.e("MegaApiClient", "Rename failed", e)
            false
        }
    }

    /**
     * Move node to another parent folder on MEGA servers ("a": "m").
     */
    suspend fun moveNode(nodeHandle: String, targetParentHandle: String): Boolean = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext false
        if (sid == "local_mega") return@withContext true
        try {
            val reqPayload = JSONArray().apply {
                put(JSONObject().apply {
                    put("a", "m")
                    put("n", nodeHandle)
                    put("t", targetParentHandle)
                })
            }
            val respStr = executeMegaRequest(reqPayload, sid)
            val err = parseMegaErrorCode(respStr)
            if (err != null) {
                lastErrorMessage = err
                return@withContext false
            }
            if (respStr.isBlank()) {
                lastErrorMessage = "Resposta vazia do MEGA ao mover item."
                return@withContext false
            }
            nodeCacheMap[nodeHandle]?.let { old ->
                nodeCacheMap[nodeHandle] = old.copy(parentHandle = targetParentHandle)
            }
            lastErrorMessage = null
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            lastErrorMessage = "Erro ao mover no MEGA: ${e.message}"
            Log.e("MegaApiClient", "Move failed", e)
            false
        }
    }

    /**
     * Generates an official MEGA public export link ("a": "l").
     */
    suspend fun createPublicLink(nodeHandle: String, isDirectory: Boolean, nodeKeyBytes: ByteArray?): String? = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext null
        if (sid == "local_mega" || nodeKeyBytes == null || nodeKeyBytes.isEmpty()) return@withContext null
        try {
            val reqPayload = JSONArray().apply {
                put(JSONObject().apply {
                    put("a", "l")
                    put("n", nodeHandle)
                })
            }
            val respStr = executeMegaRequest(reqPayload, sid)
            if (!respStr.startsWith("[")) return@withContext null
            val arr = JSONArray(respStr)
            if (arr.length() == 0) return@withContext null
            val ph = arr.optString(0).trim()
            if (ph.isBlank() || ph.startsWith("-")) return@withContext null
            val keyB64 = base64UrlEncode(nodeKeyBytes)
            val kind = if (isDirectory) "folder" else "file"
            "https://mega.nz/$kind/$ph#$keyB64"
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------
    // Cryptography and Encoding Helpers
    // -------------------------------------------------------------

    private fun parseMegaErrorCode(respStr: String): String? {
        val trimmed = respStr.trim()
        if (trimmed.isBlank()) return "Resposta vazia do servidor MEGA"
        val directCode = trimmed.toIntOrNull()
        if (directCode != null && directCode < 0) return megaErrorCodeToMessage(directCode)
        if (trimmed.startsWith("[")) {
            try {
                val arr = JSONArray(trimmed)
                if (arr.length() > 0 && arr.get(0) is Int) {
                    val code = arr.getInt(0)
                    if (code < 0) return megaErrorCodeToMessage(code)
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun megaErrorCodeToMessage(code: Int): String = when (code) {
        -3 -> "Servidor do MEGA temporariamente ocupado (-3)."
        -9 -> "Item ou conta não encontrada no MEGA (-9)."
        -11 -> "Acesso negado pelo servidor MEGA (-11)."
        -15 -> "Sessão MEGA expirada (-15). Reconecte sua conta."
        -16 -> "Limite de requisições excedido no MEGA (-16)."
        -17 -> "Cota de armazenamento ou transferência excedida no MEGA (-17)."
        else -> "Erro da API MEGA (Código $code)."
    }

    private fun encryptAesEcb(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    private fun prepareKey(password: ByteArray): ByteArray {
        val key = IntArray(4)
        for (i in password.indices) {
            key[(i / 4) % 4] = key[(i / 4) % 4] xor (password[i].toInt() and 0xFF shl (24 - (i % 4) * 8))
        }
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        key.forEach { buffer.putInt(it) }
        return buffer.array()
    }

    private fun stringHash(string: String, key: ByteArray): String {
        val data = string.toByteArray(Charsets.UTF_8)
        val h = IntArray(4)
        for (i in data.indices) {
            h[(i / 4) % 4] = h[(i / 4) % 4] xor (data[i].toInt() and 0xFF shl (24 - (i % 4) * 8))
        }
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        h.forEach { buffer.putInt(it) }
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val enc = cipher.doFinal(buffer.array())
        return base64UrlEncode(enc.copyOfRange(0, 8))
    }

    private fun encodeNodeAttributes(name: String, key: ByteArray): String {
        val jsonObj = JSONObject().put("n", name).toString()
        val json = "MEGA$jsonObj"
        val plain = json.toByteArray(Charsets.UTF_8)
        val paddedLen = ((plain.size + 15) / 16) * 16
        val padded = plain.copyOf(paddedLen)

        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        val enc = cipher.doFinal(padded)
        return base64UrlEncode(enc)
    }

    private fun decryptNodeNameAndKey(attrStr: String, kStr: String, type: Int, handle: String): Pair<String, ByteArray?> {
        if (type == 2) return Pair("Disco do MEGA", null)
        if (type == 3) return Pair("Caixa de Entrada", null)
        if (type == 4) return Pair("Lixeira", null)

        val cacheKey = "$handle|$attrStr|$kStr"
        decryptedAttrCache[cacheKey]?.let { return it }

        val currentMasterKey = masterKey
        var extractedKeyBytes: ByteArray? = null
        var extractedName: String? = null

        if (currentMasterKey != null && kStr.isNotBlank()) {
            // kStr format can be "user_handle:enc_key", "enc_key", or multiple "h1:k1/h2:k2"
            val candidates = kStr.split("/").map { segment ->
                if (segment.contains(":")) segment.substringAfter(":") else segment
            }.filter { it.isNotBlank() }

            for (encKeyStr in candidates) {
                try {
                    val encKeyBytes = base64UrlDecode(encKeyStr)
                    if (encKeyBytes.isNotEmpty()) {
                        val paddedLen = ((encKeyBytes.size + 15) / 16) * 16
                        val paddedKeyBytes = if (encKeyBytes.size != paddedLen) encKeyBytes.copyOf(paddedLen) else encKeyBytes

                        val cipherKey = Cipher.getInstance("AES/ECB/NoPadding")
                        cipherKey.init(Cipher.DECRYPT_MODE, SecretKeySpec(currentMasterKey, "AES"))
                        val dec = cipherKey.doFinal(paddedKeyBytes)
                        if (dec != null && dec.isNotEmpty()) {
                            extractedKeyBytes = dec
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.d("MegaApiClient", "Key candidate decryption fallback for $handle: ${e.message}")
                }
            }
        }

        if (attrStr.isBlank()) {
            val defaultName = if (type == 1) "Pasta_$handle" else "Arquivo_$handle"
            val result = Pair(defaultName, extractedKeyBytes)
            decryptedAttrCache[cacheKey] = result
            return result
        }

        val keysToTry = mutableListOf<ByteArray>()
        extractedKeyBytes?.let { keysToTry.add(it) }
        currentMasterKey?.let { keysToTry.add(it) }

        for (candidateKey in keysToTry) {
            try {
                val nodeAesKey = if (candidateKey.size >= 32) {
                    ByteArray(16) { i -> (candidateKey[i].toInt() xor candidateKey[i + 16].toInt()).toByte() }
                } else if (candidateKey.size >= 16) {
                    candidateKey.copyOfRange(0, 16)
                } else {
                    candidateKey
                }

                val encAttrBytes = base64UrlDecode(attrStr)
                val cipherAttr = Cipher.getInstance("AES/CBC/NoPadding")
                cipherAttr.init(Cipher.DECRYPT_MODE, SecretKeySpec(nodeAesKey, "AES"), IvParameterSpec(ByteArray(16)))
                val decAttrBytes = cipherAttr.doFinal(encAttrBytes)
                val decStr = String(decAttrBytes, Charsets.UTF_8).trimEnd { it == '\u0000' || it == ' ' }

                val start = decStr.indexOf('{')
                val end = decStr.lastIndexOf('}')
                if (start >= 0 && end > start) {
                    val jsonSub = decStr.substring(start, end + 1)
                    try {
                        val jsonObj = JSONObject(jsonSub)
                        val name = jsonObj.optString("n")
                        if (name.isNotBlank()) {
                            extractedName = name
                            break
                        }
                    } catch (_: Exception) {
                        if (jsonSub.contains("\"n\":\"")) {
                            val name = jsonSub.substringAfter("\"n\":\"").substringBefore("\"")
                            if (name.isNotBlank()) {
                                extractedName = name
                                break
                            }
                        }
                    }
                } else if (decStr.startsWith("MEGA")) {
                    val json = decStr.removePrefix("MEGA").trim()
                    try {
                        val name = JSONObject(json).optString("n")
                        if (name.isNotBlank()) {
                            extractedName = name
                            break
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.d("MegaApiClient", "Attr decryption attempt failed for $handle: ${e.message}")
            }
        }

        val finalName = extractedName ?: parseNodeName(attrStr, type, handle)
        val result = Pair(finalName, extractedKeyBytes)
        decryptedAttrCache[cacheKey] = result
        return result
    }

    private fun parseNodeName(attrStr: String, type: Int, handle: String): String {
        if (attrStr.isBlank()) {
            return when (type) {
                1 -> "Nova Pasta"
                2 -> "Disco Raiz"
                3 -> "Caixa de Entrada"
                4 -> "Lixeira"
                else -> "Arquivo_$handle"
            }
        }
        return try {
            val decoded = base64UrlDecode(attrStr)
            val str = String(decoded, Charsets.UTF_8)
            if (str.contains("{\"n\":\"")) {
                str.substringAfter("{\"n\":\"").substringBefore("\"")
            } else {
                when (type) {
                    1 -> "Pasta_$handle"
                    else -> "Arquivo_$handle"
                }
            }
        } catch (_: Exception) {
            when (type) {
                1 -> "Pasta_$handle"
                else -> "Arquivo_$handle"
            }
        }
    }

    private fun generateRandomHandle(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        val random = SecureRandom()
        return (1..11).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun base64UrlDecode(str: String): ByteArray {
        val clean = str.trim()
        if (clean.isEmpty()) return ByteArray(0)
        return try {
            Base64.decode(clean, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (_: Exception) {
            try {
                val replaced = clean.replace('-', '+').replace('_', '/')
                val padded = when (replaced.length % 4) {
                    2 -> "$replaced=="
                    3 -> "$replaced="
                    else -> replaced
                }
                Base64.decode(padded, Base64.DEFAULT or Base64.NO_WRAP)
            } catch (_: Exception) {
                ByteArray(0)
            }
        }
    }
}

