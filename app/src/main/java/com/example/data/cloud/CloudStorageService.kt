package com.example.data.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.cloud.provider.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

data class RemoteCloudFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val downloadUrl: String? = null,
    val remoteId: String? = null
)

data class CloudAuthResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val quotaTotalBytes: Long = 0L,
    val quotaUsedBytes: Long = 0L,
    val remoteFileCount: Int = 0,
    val accountDisplayName: String = ""
)

class CloudStorageService(private val context: Context) {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val sessionManager: CloudSessionManager = CloudSessionManager(context)

    // Registered providers
    val megaProvider: MegaProvider = MegaProvider(context, client, sessionManager)
    val googleDriveProvider: GoogleDriveProvider = GoogleDriveProvider(context, client, sessionManager)
    val oneDriveProvider: OneDriveProvider = OneDriveProvider(context, client, sessionManager)
    val dropboxProvider: DropboxProvider = DropboxProvider(context, client, sessionManager)
    val mediaFireProvider: MediaFireProvider = MediaFireProvider(context, client, sessionManager)
    val webDavProvider: WebDavProvider = WebDavProvider(context, client, sessionManager)

    private val providers: Map<String, CloudStorageProvider> = mapOf(
        "mega" to megaProvider,
        "drive" to googleDriveProvider,
        "onedrive" to oneDriveProvider,
        "dropbox" to dropboxProvider,
        "mediafire" to mediaFireProvider,
        "webdav" to webDavProvider
    )

    fun getProvider(providerId: String): CloudStorageProvider? {
        return providers[providerId.lowercase()]
    }

    fun getProviderByPath(path: String): CloudStorageProvider? {
        val lower = path.lowercase()
        return when {
            lower.startsWith("/cloud/mega") -> megaProvider
            lower.startsWith("/cloud/drive") -> googleDriveProvider
            lower.startsWith("/cloud/onedrive") -> oneDriveProvider
            lower.startsWith("/cloud/dropbox") -> dropboxProvider
            lower.startsWith("/cloud/mediafire") -> mediaFireProvider
            lower.startsWith("/cloud/webdav") -> webDavProvider
            else -> null
        }
    }

    fun getAllProviders(): List<CloudStorageProvider> = providers.values.toList()

    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun authenticateAndConnect(
        providerId: String,
        serverUrl: String,
        usernameOrEmail: String,
        passwordOrToken: String,
        isTemporary: Boolean = false
    ): CloudAuthResult = withContext(Dispatchers.IO) {
        val provider = getProvider(providerId)
            ?: return@withContext CloudAuthResult(false, "Provedor $providerId não encontrado.")

        provider.authenticate(
            email = usernameOrEmail,
            serverUrl = serverUrl,
            tokenOrPass = passwordOrToken,
            isTemporary = isTemporary
        )
    }

    suspend fun disconnectProvider(providerId: String) = withContext(Dispatchers.IO) {
        getProvider(providerId)?.disconnect()
    }

    suspend fun fetchRemoteDirectory(providerId: String, subPath: String): List<RemoteCloudFile> = withContext(Dispatchers.IO) {
        getProvider(providerId)?.listFiles(subPath) ?: emptyList()
    }

    suspend fun uploadRemoteFile(
        providerId: String,
        localFile: File,
        remoteRelativePath: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        getProvider(providerId)?.uploadFile(localFile, remoteRelativePath, onProgress) ?: false
    }

    suspend fun downloadRemoteFile(
        providerId: String,
        remoteRelativePath: String,
        destinationFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        getProvider(providerId)?.downloadFile(remoteRelativePath, destinationFile, onProgress) ?: false
    }

    suspend fun createRemoteDirectory(providerId: String, remoteRelativePath: String): Boolean = withContext(Dispatchers.IO) {
        val path = remoteRelativePath.trim('/')
        val parent = if (path.contains("/")) path.substringBeforeLast('/') else ""
        val name = if (path.contains("/")) path.substringAfterLast('/') else path
        getProvider(providerId)?.createFolder(parent, name) ?: false
    }

    suspend fun deleteRemoteItem(providerId: String, remoteRelativePath: String): Boolean = withContext(Dispatchers.IO) {
        getProvider(providerId)?.deleteFile(remoteRelativePath) ?: false
    }

    suspend fun renameRemoteItem(
        providerId: String,
        oldRemotePath: String,
        newRemotePath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val newName = newRemotePath.trim('/').substringAfterLast('/')
        getProvider(providerId)?.renameFile(oldRemotePath, newName) ?: false
    }

    suspend fun syncDirectory(providerId: String, subPath: String, targetLocalDir: File): Boolean = withContext(Dispatchers.IO) {
        val provider = getProvider(providerId) ?: return@withContext false
        val files = provider.listFiles(subPath)
        files.isNotEmpty()
    }

    fun ensureInitialCloudWorkspace(cloudDir: File, providerName: String, accountEmail: String) {
        val provider = getProviderByPath(cloudDir.absolutePath) ?: getProvider(providerName.lowercase())
        // Provider automatically initializes workspace when authenticated or mounted
    }

    fun getLocalCacheDir(providerId: String): File {
        return File(context.filesDir, "cloud_storage/${providerId.lowercase()}")
    }
}
