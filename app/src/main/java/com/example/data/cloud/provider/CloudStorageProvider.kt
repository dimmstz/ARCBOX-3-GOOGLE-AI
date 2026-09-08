package com.example.data.cloud.provider

import com.example.data.cloud.CloudAuthResult
import com.example.data.cloud.RemoteCloudFile
import java.io.File

/**
 * Common abstraction contract for all cloud storage providers (MEGA, Google Drive,
 * Microsoft OneDrive, Dropbox, MediaFire, WebDAV).
 * 
 * The ArcBox file explorer repository and UI communicate strictly through this interface.
 */
interface CloudStorageProvider {
    val providerId: String
    val displayName: String
    val defaultPath: String
    val isConnected: Boolean
    val accountEmail: String?
    val totalSpace: Long
    val usedSpace: Long
    val isTemporarySession: Boolean

    /**
     * Authenticate user with official protocol (OAuth2, REST API session, TLS direct).
     * @param email User identifier / email
     * @param serverUrl Optional custom server URL (e.g. for WebDAV/Nextcloud)
     * @param tokenOrPass OAuth token or API session token (never stored in plain text)
     * @param isTemporary Whether this is a temporary session or permanent linked account
     */
    suspend fun authenticate(
        email: String,
        serverUrl: String,
        tokenOrPass: String,
        isTemporary: Boolean
    ): CloudAuthResult

    /**
     * Disconnects the session, wiping temporary credentials or persistent tokens.
     */
    suspend fun disconnect()

    /**
     * List remote items in the specified relative subpath (e.g. "Documentos" or "" for root).
     */
    suspend fun listFiles(remoteSubPath: String): List<RemoteCloudFile>

    /**
     * Creates a new folder remotely.
     */
    suspend fun createFolder(remoteParentPath: String, folderName: String): Boolean

    /**
     * Uploads a local file to the remote parent path with streaming progress callback.
     */
    suspend fun uploadFile(
        localFile: File,
        remoteParentPath: String,
        onProgress: (Float) -> Unit = {}
    ): Boolean

    /**
     * Downloads a remote file to a local destination with streaming progress callback.
     */
    suspend fun downloadFile(
        remoteFilePath: String,
        destinationFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean

    /**
     * Deletes a remote file or folder.
     */
    suspend fun deleteFile(remoteFilePath: String): Boolean

    /**
     * Renames a remote file or folder.
     */
    suspend fun renameFile(oldRemotePath: String, newName: String): Boolean

    /**
     * Moves or copies a remote file to another folder.
     */
    suspend fun copyOrMoveFile(
        sourceRemotePath: String,
        destRemotePath: String,
        isMove: Boolean
    ): Boolean

    /**
     * Generates a shareable link if supported by the provider.
     */
    suspend fun getShareLink(remoteFilePath: String): String?
}
