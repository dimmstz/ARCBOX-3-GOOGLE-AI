package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.data.db.AppDatabase
import com.example.data.db.FavoriteEntity
import com.example.data.db.TrashEntity
import com.example.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val trashDao = db.trashDao()
    private val favoriteDao = db.favoriteDao()
    private val prefs = context.getSharedPreferences("arcbox_prefs", Context.MODE_PRIVATE)
    private val cloudStorageService = com.example.data.cloud.CloudStorageService(context)
    val safCloudManager = com.example.data.cloud.SafCloudManager(context)
    private var mockFilesCreated = false

    val allTrashItems: Flow<List<TrashEntity>> = trashDao.getAllTrashItems()
    val allFavorites: Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()

    fun resolveFile(path: String): File {
        return if (path.startsWith("/cloud/")) {
            val relative = path.removePrefix("/cloud/").removePrefix("/")
            val cloudDir = File(context.filesDir, "cloud_storage")
            File(cloudDir, relative)
        } else {
            File(path)
        }
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val children = file.listFiles() ?: return 0L
        for (child in children) {
            size += if (child.isDirectory) getFolderSize(child) else child.length()
        }
        return size
    }

    // -------------------------------------------------------------
    // STORAGE VOLUMES & ROOTS
    // -------------------------------------------------------------
    suspend fun getStorageVolumes(): List<StorageVolume> = withContext(Dispatchers.IO) {
        ensureMockFilesExist()
        val list = mutableListOf<StorageVolume>()

        // Primary Internal Storage
        val primaryInternal = Environment.getExternalStorageDirectory()
        val primaryStat = try { StatFs(primaryInternal.path) } catch (e: Exception) { null }
        val totalInternal = primaryStat?.totalBytes ?: 0L
        val freeInternal = primaryStat?.availableBytes ?: 0L

        list.add(
            StorageVolume(
                id = "internal",
                name = "Armazenamento Interno",
                path = primaryInternal.absolutePath,
                totalBytes = totalInternal,
                freeBytes = freeInternal,
                typeKey = "INTERNAL"
            )
        )

        // Detect secondary SD Cards or USB OTG mounted drives
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            for (i in 1 until externalDirs.size) {
                val dir = externalDirs[i]
                if (dir != null) {
                    val rootPath = dir.absolutePath.substringBefore("/Android/")
                    val stat = try { StatFs(rootPath) } catch (e: Exception) { null }
                    val total = stat?.totalBytes ?: 0L
                    val free = stat?.availableBytes ?: 0L
                    if (list.none { it.path == rootPath }) {
                        val volumeLabel = if (externalDirs.size > 2) "Cartão SD $i" else "Cartão SD"
                        list.add(
                            StorageVolume(
                                id = "sdcard_$i",
                                name = volumeLabel,
                                path = rootPath,
                                totalBytes = total,
                                freeBytes = free,
                                typeKey = "SDCARD"
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // Scan /storage/ for mounted SD Cards and USB OTG drives
        try {
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                val files = storageDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        val name = file.name
                        if (file.isDirectory && name != "emulated" && name != "self" && name != "knox" && !name.startsWith(".")) {
                            val path = file.absolutePath
                            if (list.none { it.path == path }) {
                                val stat = try { StatFs(path) } catch (e: Exception) { null }
                                val total = stat?.totalBytes ?: 0L
                                val free = stat?.availableBytes ?: 0L
                                val isOtg = name.lowercase().contains("otg") || name.lowercase().contains("usb")
                                val volumeName = if (isOtg) "Armazenamento OTG" else "Cartão SD"
                                list.add(
                                    StorageVolume(
                                        id = "ext_${name.lowercase()}",
                                        name = volumeName,
                                        path = path,
                                        totalBytes = total,
                                        freeBytes = free,
                                        typeKey = if (isOtg) "OTG" else "SDCARD"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Scan typical /mnt/ mount paths for USB/OTG
        try {
            val mntPaths = listOf("/mnt/media_rw", "/mnt/usb", "/mnt/otg")
            for (mntPath in mntPaths) {
                val mntDir = File(mntPath)
                if (mntDir.exists() && mntDir.isDirectory) {
                    val files = mntDir.listFiles()
                    if (files != null) {
                        for (file in files) {
                            if (file.isDirectory && !file.name.startsWith(".")) {
                                val path = file.absolutePath
                                if (list.none { it.path == path }) {
                                    val stat = try { StatFs(path) } catch (e: Exception) { null }
                                    val total = stat?.totalBytes ?: 0L
                                    val free = stat?.availableBytes ?: 0L
                                    list.add(
                                        StorageVolume(
                                            id = "mnt_${file.name.lowercase()}",
                                            name = "USB/OTG (${file.name})",
                                            path = path,
                                            totalBytes = total,
                                            freeBytes = free,
                                            typeKey = "OTG"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Append connected Cloud volumes
        val prefs = context.getSharedPreferences("arcbox_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("cloud_connected_mega", false)) {
            val megaDir = resolveFile("/cloud/mega")
            megaDir.mkdirs()
            val total = 50 * 1024 * 1024 * 1024L
            val used = getFolderSize(megaDir)
            list.add(
                StorageVolume(
                    id = "cloud_mega",
                    name = "MEGA",
                    path = "/cloud/mega",
                    totalBytes = total,
                    freeBytes = (total - used).coerceAtLeast(0L),
                    typeKey = "CLOUD"
                )
            )
        }
        if (prefs.getBoolean("cloud_connected_drive", false)) {
            val driveDir = resolveFile("/cloud/drive")
            driveDir.mkdirs()
            val total = 15 * 1024 * 1024 * 1024L
            val used = getFolderSize(driveDir)
            list.add(
                StorageVolume(
                    id = "cloud_drive",
                    name = "Google Drive",
                    path = "/cloud/drive",
                    totalBytes = total,
                    freeBytes = (total - used).coerceAtLeast(0L),
                    typeKey = "CLOUD"
                )
            )
        }
        if (prefs.getBoolean("cloud_connected_onedrive", false)) {
            val odDir = resolveFile("/cloud/onedrive")
            odDir.mkdirs()
            val total = 5 * 1024 * 1024 * 1024L
            val used = getFolderSize(odDir)
            list.add(
                StorageVolume(
                    id = "cloud_onedrive",
                    name = "Microsoft OneDrive",
                    path = "/cloud/onedrive",
                    totalBytes = total,
                    freeBytes = (total - used).coerceAtLeast(0L),
                    typeKey = "CLOUD"
                )
            )
        }
        if (prefs.getBoolean("cloud_connected_dropbox", false)) {
            val dbxDir = resolveFile("/cloud/dropbox")
            dbxDir.mkdirs()
            val total = 2 * 1024 * 1024 * 1024L
            val used = getFolderSize(dbxDir)
            list.add(
                StorageVolume(
                    id = "cloud_dropbox",
                    name = "Dropbox",
                    path = "/cloud/dropbox",
                    totalBytes = total,
                    freeBytes = (total - used).coerceAtLeast(0L),
                    typeKey = "CLOUD"
                )
            )
        }
        if (prefs.getBoolean("cloud_connected_mediafire", false)) {
            val mfDir = resolveFile("/cloud/mediafire")
            mfDir.mkdirs()
            val total = 10 * 1024 * 1024 * 1024L
            val used = getFolderSize(mfDir)
            list.add(
                StorageVolume(
                    id = "cloud_mediafire",
                    name = "MediaFire",
                    path = "/cloud/mediafire",
                    totalBytes = total,
                    freeBytes = (total - used).coerceAtLeast(0L),
                    typeKey = "CLOUD"
                )
            )
        }
        if (prefs.getBoolean("cloud_connected_webdav", false)) {
            val webdavDir = resolveFile("/cloud/webdav")
            webdavDir.mkdirs()
            val total = 100 * 1024 * 1024 * 1024L
            val used = getFolderSize(webdavDir)
            list.add(
                StorageVolume(
                    id = "cloud_webdav",
                    name = "WebDAV / Servidor",
                    path = "/cloud/webdav",
                    totalBytes = total,
                    freeBytes = (total - used).coerceAtLeast(0L),
                    typeKey = "CLOUD"
                )
            )
        }

        // Native Android Cloud Drives (Storage Access Framework / DocumentsProvider)
        val safCloudDrives = safCloudManager.getRegisteredDrives()
        for (safDrive in safCloudDrives) {
            list.add(
                StorageVolume(
                    id = safDrive.id,
                    name = safDrive.name,
                    path = safDrive.uriString,
                    isSaf = true,
                    safUriString = safDrive.uriString,
                    totalBytes = safDrive.totalBytes,
                    freeBytes = safDrive.freeBytes,
                    typeKey = "CLOUD"
                )
            )
        }

        // Superuser / Root storage volume (Only available when Root is present on the device)
        if (com.example.util.RootHelper.isRootAvailable()) {
            val rootStat = try { StatFs("/") } catch (e: Exception) { null }
            val totalRoot = rootStat?.totalBytes ?: 0L
            val freeRoot = rootStat?.availableBytes ?: 0L
            list.add(
                StorageVolume(
                    id = "root_fs",
                    name = "Raiz (Superusuário)",
                    path = "/",
                    totalBytes = totalRoot,
                    freeBytes = freeRoot,
                    typeKey = "ROOT"
                )
            )
        }

        list
    }

    // -------------------------------------------------------------
    // FILE NAVIGATION & LISTING
    // -------------------------------------------------------------
    suspend fun listFiles(
        directoryPath: String,
        safUriString: String? = null,
        sortOption: SortOption = SortOption.NAME,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        searchQuery: String = "",
        filterCategory: FileType? = null,
        appSubFilter: String = "ALL",
        isGlobalSearch: Boolean = false,
        isAppManagerMode: Boolean = false
    ): List<FileItem> = withContext(Dispatchers.IO) {
        if (isAppManagerMode) {
            val installedAndStorageApps = getInstalledApps(appSubFilter)
            val filtered = if (searchQuery.isNotEmpty()) {
                installedAndStorageApps.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    (it.packageName?.contains(searchQuery, ignoreCase = true) == true)
                }
            } else {
                installedAndStorageApps
            }
            val sorted = when (sortOption) {
                SortOption.NAME -> if (sortOrder == SortOrder.ASCENDING) filtered.sortedBy { it.name.lowercase() } else filtered.sortedByDescending { it.name.lowercase() }
                SortOption.DATE -> if (sortOrder == SortOrder.ASCENDING) filtered.sortedBy { it.lastModified } else filtered.sortedByDescending { it.lastModified }
                SortOption.SIZE -> if (sortOrder == SortOrder.ASCENDING) filtered.sortedBy { it.size } else filtered.sortedByDescending { it.size }
                SortOption.TYPE -> if (sortOrder == SortOrder.ASCENDING) filtered.sortedBy { it.appCategory ?: "" } else filtered.sortedByDescending { it.appCategory ?: "" }
            }
            return@withContext sorted
        }

        val favoritePaths = try {
            favoriteDao.getAllFavoritePaths().toSet()
        } catch (_: Exception) {
            emptySet()
        }
        val items = mutableListOf<FileItem>()

        if (filterCategory != null) {
            if (safUriString != null && safUriString.startsWith("content://")) {
                // Read recursively via SAF DocumentFile
                val treeUri = Uri.parse(safUriString)
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDoc != null && rootDoc.isDirectory) {
                    suspend fun traverseDoc(doc: DocumentFile) {
                        val name = doc.name ?: "Sem nome"
                        if (name == "Android" || name.startsWith(".")) {
                            return
                        }
                        if (doc.isDirectory) {
                            val children = doc.listFiles()
                            for (child in children) {
                                traverseDoc(child)
                            }
                        } else {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            val type = getFileTypeFromExtension(ext, doc.type)
                            if (type == filterCategory) {
                                val item = FileItem(
                                    id = doc.uri.toString(),
                                    name = name,
                                    path = doc.uri.toString(),
                                    safUriString = doc.uri.toString(),
                                    size = doc.length(),
                                    lastModified = doc.lastModified(),
                                    isDirectory = false,
                                    fileType = type,
                                    extension = ext,
                                    isFavorite = favoritePaths.contains(doc.uri.toString()),
                                    childCount = 0,
                                    mimeType = doc.type ?: "*/*"
                                )
                                items.add(item)
                            }
                        }
                    }
                    traverseDoc(rootDoc)
                }
            } else {
                // Read recursively via standard Java File
                val activeVolumePath = getStorageVolumes()
                    .sortedByDescending { it.path.length }
                    .find { directoryPath.startsWith(it.path) }?.path ?: directoryPath
                val startDir = File(activeVolumePath)
                if (startDir.exists() && startDir.isDirectory) {
                    suspend fun traverse(dir: File) {
                        if (dir.name == "Android" || dir.name.startsWith(".")) {
                            return
                        }
                        val files = dir.listFiles() ?: return
                        for (file in files) {
                            if (file.isDirectory) {
                                traverse(file)
                            } else {
                                val name = file.name
                                if (name.startsWith(".")) {
                                    continue
                                }
                                val ext = file.extension.lowercase()
                                val mime = getMimeType(file)
                                val type = getFileTypeFromExtension(ext, mime)
                                if (type == filterCategory) {
                                    val item = FileItem(
                                        id = file.absolutePath,
                                        name = name,
                                        path = file.absolutePath,
                                        size = file.length(),
                                        lastModified = file.lastModified(),
                                        isDirectory = false,
                                        fileType = type,
                                        extension = ext,
                                        isFavorite = favoritePaths.contains(file.absolutePath),
                                        childCount = 0,
                                        mimeType = mime
                                    )
                                    items.add(item)
                                }
                            }
                        }
                    }
                    traverse(startDir)
                }
            }
        } else {
            val isSafTarget = directoryPath.startsWith("content://") || (safUriString != null && safUriString.startsWith("content://"))
            if (isSafTarget) {
                val safTargetUriStr = if (directoryPath.startsWith("content://")) directoryPath else safUriString!!
                val targetUri = Uri.parse(safTargetUriStr)
                val rootDoc = try {
                    if (safTargetUriStr.contains("/tree/")) {
                        DocumentFile.fromTreeUri(context, targetUri) ?: DocumentFile.fromSingleUri(context, targetUri)
                    } else {
                        DocumentFile.fromSingleUri(context, targetUri) ?: DocumentFile.fromTreeUri(context, targetUri)
                    }
                } catch (_: Exception) {
                    null
                }

                if (rootDoc != null && rootDoc.isDirectory) {
                    if (searchQuery.isNotBlank()) {
                        suspend fun searchSafRecursive(dir: DocumentFile, depth: Int = 0) {
                            if (depth > 5) return
                            val files = try { dir.listFiles() } catch (_: Exception) { emptyArray() }
                            for (doc in files) {
                                val docName = doc.name ?: continue
                                if (docName.startsWith(".")) continue
                                if (docName.contains(searchQuery, ignoreCase = true)) {
                                    val isDir = doc.isDirectory
                                    val ext = docName.substringAfterLast('.', "").lowercase()
                                    val mime = doc.type ?: getMimeTypeFromExtension(ext)
                                    val type = if (isDir) FileType.FOLDER else getFileTypeFromExtension(ext, mime)
                                    val size = if (isDir) 0L else doc.length()
                                    items.add(
                                        FileItem(
                                            id = doc.uri.toString(),
                                            name = docName,
                                            path = doc.uri.toString(),
                                            safUriString = doc.uri.toString(),
                                            size = size,
                                            lastModified = doc.lastModified(),
                                            isDirectory = isDir,
                                            fileType = type,
                                            extension = ext,
                                            isFavorite = favoritePaths.contains(doc.uri.toString()),
                                            childCount = if (isDir) (try { doc.listFiles().size } catch (_: Exception) { 0 }) else 0,
                                            mimeType = mime
                                        )
                                    )
                                }
                                if (doc.isDirectory) {
                                    searchSafRecursive(doc, depth + 1)
                                }
                            }
                        }
                        searchSafRecursive(rootDoc)
                    } else {
                        val files = try { rootDoc.listFiles() } catch (_: Exception) { emptyArray() }
                        for (doc in files) {
                            val isDir = doc.isDirectory
                            val name = doc.name ?: "Sem nome"
                            val ext = name.substringAfterLast('.', "").lowercase()
                            val mime = doc.type ?: getMimeTypeFromExtension(ext)
                            val type = if (isDir) FileType.FOLDER else getFileTypeFromExtension(ext, mime)
                            val size = if (isDir) 0L else doc.length()

                            val item = FileItem(
                                id = doc.uri.toString(),
                                name = name,
                                path = doc.uri.toString(),
                                safUriString = doc.uri.toString(),
                                size = size,
                                lastModified = doc.lastModified(),
                                isDirectory = isDir,
                                fileType = type,
                                extension = ext,
                                isFavorite = favoritePaths.contains(doc.uri.toString()),
                                childCount = if (isDir) (try { doc.listFiles().size } catch (_: Exception) { 0 }) else 0,
                                mimeType = mime
                            )
                            items.add(item)
                        }
                    }
                }
            } else {
                // Read via standard java File
                if (searchQuery.isNotBlank()) {
                    val rootDirPath = if (isGlobalSearch) {
                        getStorageVolumes().find { directoryPath.startsWith(it.path) }?.path ?: directoryPath
                    } else {
                        directoryPath
                    }
                    val searchDir = resolveFile(rootDirPath)
                    val searchResults = searchRecursiveCloudOrLocal(searchDir, rootDirPath, searchQuery, favoritePaths)
                    items.addAll(searchResults)
                } else {
                    val targetDir = resolveFile(directoryPath)
                    if (directoryPath.startsWith("/cloud/")) {
                        if (!targetDir.exists()) {
                            targetDir.mkdirs()
                        }
                        val providerSegment = directoryPath.removePrefix("/cloud/").substringBefore("/")
                        val subPath = directoryPath.removePrefix("/cloud/$providerSegment").removePrefix("/")

                        // Trigger live sync with remote server if online and connected
                        if (prefs.getBoolean("cloud_connected_$providerSegment", false) && cloudStorageService.isOnline()) {
                            try {
                                cloudStorageService.syncDirectory(providerSegment, subPath, targetDir)
                            } catch (e: Exception) {
                                android.util.Log.w("FileRepository", "Cloud live sync exception: ${e.message}")
                            }
                        }

                        val existing = targetDir.listFiles()
                        if (existing == null || existing.isEmpty()) {
                            val providerName = when (providerSegment.lowercase()) {
                                "mega" -> "Nuvem Mega"
                                "drive" -> "Google Drive"
                                "webdav" -> "WebDAV"
                                "onedrive" -> "OneDrive"
                                "dropbox" -> "Dropbox"
                                "mediafire" -> "MediaFire"
                                else -> providerSegment.replaceFirstChar { it.uppercase() }
                            }
                            cloudStorageService.ensureInitialCloudWorkspace(
                                targetDir,
                                providerName,
                                prefs.getString("cloud_email_$providerSegment", "usuario@$providerSegment.com") ?: "usuario@cloud.com"
                            )
                        }
                    }
                    val files = if (targetDir.exists() && targetDir.isDirectory) {
                        targetDir.listFiles()
                    } else null

                    if (files != null) {
                        for (file in files) {
                            val isDir = file.isDirectory
                            val name = file.name
                            val ext = file.extension.lowercase()
                            val mime = getMimeType(file)
                            val type = if (isDir) FileType.FOLDER else getFileTypeFromExtension(ext, mime)
                            val size = if (isDir) 0L else file.length()
                            val count = if (isDir) (file.list()?.size ?: 0) else 0

                            val itemPath = if (directoryPath.startsWith("/cloud/")) {
                                directoryPath.removeSuffix("/") + "/" + name
                            } else {
                                file.absolutePath
                            }

                            val item = FileItem(
                                id = itemPath,
                                name = name,
                                path = itemPath,
                                size = size,
                                lastModified = file.lastModified(),
                                isDirectory = isDir,
                                fileType = type,
                                extension = ext,
                                isFavorite = favoritePaths.contains(itemPath),
                                childCount = count,
                                mimeType = mime
                            )
                            items.add(item)
                        }
                    } else if (com.example.util.RootHelper.isRootAvailable() && !directoryPath.startsWith("/cloud/")) {
                        // Fallback to superuser root listing for protected system directories
                        val rootItems = com.example.util.RootHelper.listDirectory(directoryPath, favoritePaths)
                        items.addAll(rootItems)
                    }
                }
            }
        }

        // Apply search filter if present (for SAF or category filtered results)
        var filtered = if (searchQuery.isNotBlank() && items.isNotEmpty() && items.all { !it.path.contains("/") || it.name.contains(searchQuery, ignoreCase = true) }) {
            items.filter { it.name.contains(searchQuery, ignoreCase = true) || it.extension.equals(searchQuery.removePrefix("."), ignoreCase = true) }
        } else {
            items
        }

        // Apply category filter if present
        if (filterCategory != null) {
            filtered = filtered.filter { it.isDirectory || it.fileType == filterCategory }
        }

        // Sort items (Folders always on top)
        val comparator = when (sortOption) {
            SortOption.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortOption.DATE -> compareBy { it.lastModified }
            SortOption.SIZE -> compareBy { it.size }
            SortOption.TYPE -> compareBy { it.extension.lowercase() }
        }

        val sorted = if (sortOrder == SortOrder.ASCENDING) {
            filtered.sortedWith(comparator)
        } else {
            filtered.sortedWith(comparator.reversed())
        }

        // Put directories on top
        val (dirs, nonDirs) = sorted.partition { it.isDirectory }
        dirs + nonDirs
    }

    private fun searchRecursiveCloudOrLocal(
        dir: File,
        virtualPath: String,
        query: String,
        favoritePaths: Set<String>
    ): List<FileItem> {
        val result = mutableListOf<FileItem>()
        if (!dir.exists() || !dir.isDirectory) return result
        val files = dir.listFiles() ?: return result
        for (file in files) {
            val name = file.name
            if (name.startsWith(".")) continue
            val itemVirtualPath = if (virtualPath.startsWith("/cloud/")) {
                virtualPath.removeSuffix("/") + "/" + name
            } else {
                file.absolutePath
            }
            val matches = name.contains(query, ignoreCase = true)
            val isDir = file.isDirectory
            val ext = file.extension.lowercase()
            val mime = getMimeType(file)
            val type = if (isDir) FileType.FOLDER else getFileTypeFromExtension(ext, mime)

            if (matches) {
                result.add(
                    FileItem(
                        id = itemVirtualPath,
                        name = name,
                        path = itemVirtualPath,
                        size = if (isDir) 0L else file.length(),
                        lastModified = file.lastModified(),
                        isDirectory = isDir,
                        fileType = type,
                        extension = ext,
                        isFavorite = favoritePaths.contains(itemVirtualPath),
                        childCount = if (isDir) (file.list()?.size ?: 0) else 0,
                        mimeType = mime
                    )
                )
            }
            if (isDir) {
                result.addAll(searchRecursiveCloudOrLocal(file, itemVirtualPath, query, favoritePaths))
            }
        }
        return result
    }

    // -------------------------------------------------------------
    // FILE OPERATIONS (Create, Rename, Copy, Move, Delete to Trash)
    // -------------------------------------------------------------
    suspend fun createFolder(parentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        if (parentPath.startsWith("content://")) {
            val targetUri = Uri.parse(parentPath)
            val parentDoc = try {
                if (parentPath.contains("/tree/")) {
                    DocumentFile.fromTreeUri(context, targetUri) ?: DocumentFile.fromSingleUri(context, targetUri)
                } else {
                    DocumentFile.fromSingleUri(context, targetUri) ?: DocumentFile.fromTreeUri(context, targetUri)
                }
            } catch (_: Exception) { null }
            return@withContext parentDoc?.createDirectory(folderName) != null
        }

        val parentDir = resolveFile(parentPath)
        if (!parentDir.exists()) parentDir.mkdirs()
        val newDir = File(parentDir, folderName)
        if (!newDir.exists()) {
            val created = try { newDir.mkdirs() } catch (_: Exception) { false }
            if (parentPath.startsWith("/cloud/")) {
                val providerSegment = parentPath.removePrefix("/cloud/").substringBefore("/")
                val subPath = (parentPath.removePrefix("/cloud/$providerSegment").removePrefix("/") + "/" + folderName).trim('/')
                cloudStorageService.createRemoteDirectory(providerSegment, subPath)
            }
            if (!created && com.example.util.RootHelper.isRootAvailable() && !parentPath.startsWith("/cloud/")) {
                com.example.util.RootHelper.createFolder(parentPath, folderName)
            } else {
                created
            }
        } else false
    }

    suspend fun createFile(parentPath: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        if (parentPath.startsWith("content://")) {
            val targetUri = Uri.parse(parentPath)
            val parentDoc = try {
                if (parentPath.contains("/tree/")) {
                    DocumentFile.fromTreeUri(context, targetUri) ?: DocumentFile.fromSingleUri(context, targetUri)
                } else {
                    DocumentFile.fromSingleUri(context, targetUri) ?: DocumentFile.fromTreeUri(context, targetUri)
                }
            } catch (_: Exception) { null }
            val ext = fileName.substringAfterLast('.', "")
            val mime = getMimeTypeFromExtension(ext)
            return@withContext parentDoc?.createFile(mime, fileName) != null
        }

        val parentDir = resolveFile(parentPath)
        if (!parentDir.exists()) parentDir.mkdirs()
        val newFile = File(parentDir, fileName)
        if (!newFile.exists()) {
            val created = try { newFile.createNewFile() } catch (_: Exception) { false }
            if (parentPath.startsWith("/cloud/")) {
                val providerSegment = parentPath.removePrefix("/cloud/").substringBefore("/")
                val subPath = (parentPath.removePrefix("/cloud/$providerSegment").removePrefix("/") + "/" + fileName).trim('/')
                cloudStorageService.uploadRemoteFile(providerSegment, newFile, subPath)
            }
            if (!created && com.example.util.RootHelper.isRootAvailable() && !parentPath.startsWith("/cloud/")) {
                com.example.util.RootHelper.createFile(parentPath, fileName)
            } else {
                created
            }
        } else false
    }

    suspend fun renameFile(oldPath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        if (oldPath.startsWith("content://")) {
            val targetUri = Uri.parse(oldPath)
            val doc = try {
                DocumentFile.fromSingleUri(context, targetUri) ?: DocumentFile.fromTreeUri(context, targetUri)
            } catch (_: Exception) { null }
            return@withContext doc?.renameTo(newName) == true
        }

        val target = resolveFile(oldPath)
        val parent = target.parentFile ?: return@withContext false
        val dest = File(parent, newName)
        val renamed = try { target.renameTo(dest) } catch (_: Exception) { false }
        if (oldPath.startsWith("/cloud/")) {
            val providerSegment = oldPath.removePrefix("/cloud/").substringBefore("/")
            val oldSubPath = oldPath.removePrefix("/cloud/$providerSegment").removePrefix("/")
            val parentSub = if (oldSubPath.contains("/")) oldSubPath.substringBeforeLast("/") else ""
            val newSubPath = (if (parentSub.isNotBlank()) "$parentSub/$newName" else newName).trim('/')
            cloudStorageService.renameRemoteItem(providerSegment, oldSubPath, newSubPath)
        }
        if (!renamed && com.example.util.RootHelper.isRootAvailable() && !oldPath.startsWith("/cloud/")) {
            com.example.util.RootHelper.rename(oldPath, newName)
        } else {
            renamed
        }
    }

    suspend fun copyFile(sourcePath: String, targetDirectory: String, customFileName: String? = null, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        // Handling SAF (content://) streams
        if (sourcePath.startsWith("content://") || targetDirectory.startsWith("content://")) {
            return@withContext try {
                val sourceUri = if (sourcePath.startsWith("content://")) Uri.parse(sourcePath) else null
                val targetUri = if (targetDirectory.startsWith("content://")) Uri.parse(targetDirectory) else null

                val resolvedFileName = customFileName?.ifBlank { null }
                    ?: if (sourceUri != null) (DocumentFile.fromSingleUri(context, sourceUri)?.name ?: "arquivo")
                    else File(sourcePath).name

                val ext = resolvedFileName.substringAfterLast('.', "")
                val mime = getMimeTypeFromExtension(ext)

                val inputStream = if (sourceUri != null) {
                    context.contentResolver.openInputStream(sourceUri)
                } else {
                    File(sourcePath).inputStream()
                } ?: return@withContext false

                val outputStream = if (targetUri != null) {
                    val destDirDoc = if (targetDirectory.contains("/tree/")) {
                        DocumentFile.fromTreeUri(context, targetUri) ?: DocumentFile.fromSingleUri(context, targetUri)
                    } else {
                        DocumentFile.fromSingleUri(context, targetUri) ?: DocumentFile.fromTreeUri(context, targetUri)
                    }
                    val newDoc = destDirDoc?.createFile(mime, resolvedFileName) ?: return@withContext false
                    context.contentResolver.openOutputStream(newDoc.uri)
                } else {
                    val destFile = File(resolveFile(targetDirectory), resolvedFileName)
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream()
                } ?: return@withContext false

                inputStream.use { input ->
                    outputStream.use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                    }
                }
                true
            } catch (e: Exception) {
                Log.e("FileRepository", "Copy SAF error", e)
                false
            }
        }

        val src = resolveFile(sourcePath)
        val destDir = resolveFile(targetDirectory)
        if (!destDir.exists()) destDir.mkdirs()
        if (!src.exists()) {
            if (com.example.util.RootHelper.isRootAvailable() && !sourcePath.startsWith("/cloud/") && !targetDirectory.startsWith("/cloud/")) {
                return@withContext com.example.util.RootHelper.copy(sourcePath, targetDirectory, customFileName)
            }
            return@withContext false
        }

        val fileName = customFileName?.ifBlank { null } ?: src.name
        val dest = File(destDir, fileName)
        try {
            if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = true)
            } else {
                val totalBytes = src.length()
                var copiedBytes = 0L
                src.inputStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            copiedBytes += read
                            if (totalBytes > 0) {
                                onProgress(copiedBytes.toFloat() / totalBytes)
                            }
                        }
                    }
                }
            }
            if (targetDirectory.startsWith("/cloud/")) {
                val providerSegment = targetDirectory.removePrefix("/cloud/").substringBefore("/")
                val subPath = (targetDirectory.removePrefix("/cloud/$providerSegment").removePrefix("/") + "/" + fileName).trim('/')
                cloudStorageService.uploadRemoteFile(providerSegment, dest, subPath)
            }
            true
        } catch (_: Exception) {
            if (com.example.util.RootHelper.isRootAvailable() && !sourcePath.startsWith("/cloud/") && !targetDirectory.startsWith("/cloud/")) {
                com.example.util.RootHelper.copy(sourcePath, targetDirectory, customFileName)
            } else {
                false
            }
        }
    }

    suspend fun moveFile(sourcePath: String, targetDirectory: String, customFileName: String? = null, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (sourcePath.startsWith("content://") || targetDirectory.startsWith("content://")) {
            val copied = copyFile(sourcePath, targetDirectory, customFileName, onProgress)
            if (copied && sourcePath.startsWith("content://")) {
                try {
                    val srcUri = Uri.parse(sourcePath)
                    DocumentFile.fromSingleUri(context, srcUri)?.delete()
                } catch (_: Exception) {}
            }
            return@withContext copied
        }

        val src = resolveFile(sourcePath)
        val destDir = resolveFile(targetDirectory)
        if (!destDir.exists()) destDir.mkdirs()
        val fileName = customFileName?.ifBlank { null } ?: src.name
        val dest = File(destDir, fileName)
        val renamed = try {
            if (customFileName == null) src.renameTo(dest) else false
        } catch (_: Exception) { false }

        if (sourcePath.startsWith("/cloud/") && targetDirectory.startsWith("/cloud/")) {
            val srcProvider = sourcePath.removePrefix("/cloud/").substringBefore("/")
            val destProvider = targetDirectory.removePrefix("/cloud/").substringBefore("/")
            if (srcProvider == destProvider) {
                val oldSub = sourcePath.removePrefix("/cloud/$srcProvider").removePrefix("/")
                val newSub = (targetDirectory.removePrefix("/cloud/$destProvider").removePrefix("/") + "/" + fileName).trim('/')
                cloudStorageService.renameRemoteItem(srcProvider, oldSub, newSub)
            }
        }

        if (!renamed) {
            val copied = copyFile(sourcePath, targetDirectory, customFileName, onProgress)
            if (copied) {
                try { src.deleteRecursively() } catch (_: Exception) {
                    if (com.example.util.RootHelper.isRootAvailable() && !sourcePath.startsWith("/cloud/")) com.example.util.RootHelper.delete(sourcePath)
                }
                if (sourcePath.startsWith("/cloud/")) {
                    val providerSegment = sourcePath.removePrefix("/cloud/").substringBefore("/")
                    val subPath = sourcePath.removePrefix("/cloud/$providerSegment").removePrefix("/")
                    cloudStorageService.deleteRemoteItem(providerSegment, subPath)
                }
                true
            } else if (com.example.util.RootHelper.isRootAvailable() && !sourcePath.startsWith("/cloud/") && !targetDirectory.startsWith("/cloud/")) {
                com.example.util.RootHelper.move(sourcePath, targetDirectory, customFileName)
            } else {
                false
            }
        } else {
            if (targetDirectory.startsWith("/cloud/")) {
                val providerSegment = targetDirectory.removePrefix("/cloud/").substringBefore("/")
                val subPath = (targetDirectory.removePrefix("/cloud/$providerSegment").removePrefix("/") + "/" + fileName).trim('/')
                cloudStorageService.uploadRemoteFile(providerSegment, dest, subPath)
            }
            true
        }
    }

    suspend fun deletePermanently(item: FileItem): Boolean = withContext(Dispatchers.IO) {
        favoriteDao.deleteFavoriteByPath(item.path)
        if (item.path.startsWith("content://") || item.safUriString != null) {
            val uriStr = item.safUriString ?: item.path
            return@withContext try {
                val uri = Uri.parse(uriStr)
                val doc = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
                doc?.delete() == true
            } catch (_: Exception) { false }
        }

        val file = resolveFile(item.path)
        var deleted = try {
            if (file.exists()) file.deleteRecursively() else true
        } catch (_: Exception) { false }

        if (item.path.startsWith("/cloud/")) {
            val providerSegment = item.path.removePrefix("/cloud/").substringBefore("/")
            val subPath = item.path.removePrefix("/cloud/$providerSegment").removePrefix("/")
            cloudStorageService.deleteRemoteItem(providerSegment, subPath)
        }

        if (!deleted && com.example.util.RootHelper.isRootAvailable() && !item.path.startsWith("/cloud/")) {
            deleted = com.example.util.RootHelper.delete(item.path)
        }
        if (deleted) invalidateStorageCache()
        deleted
    }

    suspend fun moveToTrash(item: FileItem): Boolean = withContext(Dispatchers.IO) {
        favoriteDao.deleteFavoriteByPath(item.path)
        invalidateStorageCache()
        val sourceFile = resolveFile(item.path)
        if (!sourceFile.exists()) return@withContext false

        if (item.path.startsWith("/cloud/")) {
            val providerSegment = item.path.removePrefix("/cloud/").substringBefore("/")
            val subPath = item.path.removePrefix("/cloud/$providerSegment").removePrefix("/")
            cloudStorageService.deleteRemoteItem(providerSegment, subPath)
        }

        // Move to internal trash directory
        val trashFolder = File(context.filesDir, "arcbox_trash")
        if (!trashFolder.exists()) trashFolder.mkdirs()

        val trashFile = File(trashFolder, "${System.currentTimeMillis()}_${sourceFile.name}")
        var moved = sourceFile.renameTo(trashFile)
        if (!moved) {
            try {
                if (sourceFile.isDirectory) {
                    if (sourceFile.copyRecursively(trashFile, overwrite = true)) {
                        sourceFile.deleteRecursively()
                        moved = true
                    }
                } else {
                    sourceFile.copyTo(trashFile, overwrite = true)
                    sourceFile.delete()
                    moved = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (moved) {
            trashDao.insertTrashItem(
                TrashEntity(
                    originalPath = item.path,
                    displayName = item.name,
                    size = item.size,
                    deletedTimestamp = System.currentTimeMillis(),
                    trashTempPath = trashFile.absolutePath,
                    isDirectory = item.isDirectory
                )
            )
            true
        } else {
            false
        }
    }

    suspend fun restoreFromTrash(trashEntity: TrashEntity): Boolean = withContext(Dispatchers.IO) {
        val trashFile = File(trashEntity.trashTempPath)
        val origFile = resolveFile(trashEntity.originalPath)

        origFile.parentFile?.mkdirs()
        var restored = trashFile.renameTo(origFile)
        if (!restored && trashFile.exists()) {
            try {
                if (trashFile.isDirectory) {
                    if (trashFile.copyRecursively(origFile, overwrite = true)) {
                        trashFile.deleteRecursively()
                        restored = true
                    }
                } else {
                    trashFile.copyTo(origFile, overwrite = true)
                    trashFile.delete()
                    restored = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (restored || !trashFile.exists()) {
            trashDao.deleteTrashById(trashEntity.id)
            true
        } else {
            false
        }
    }

    suspend fun permanentlyDeleteTrash(trashEntity: TrashEntity): Boolean = withContext(Dispatchers.IO) {
        val trashFile = File(trashEntity.trashTempPath)
        if (trashFile.exists()) {
            trashFile.deleteRecursively()
        }
        trashDao.deleteTrashById(trashEntity.id)
        true
    }

    suspend fun emptyTrashBin(): Boolean = withContext(Dispatchers.IO) {
        val trashFolder = File(context.filesDir, "arcbox_trash")
        if (trashFolder.exists()) {
            trashFolder.deleteRecursively()
            trashFolder.mkdirs()
        }
        trashDao.emptyTrash()
        true
    }

    suspend fun cleanOldTrashItems(days: Int = 30): Boolean = withContext(Dispatchers.IO) {
        try {
            val threshold = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
            val oldItems = trashDao.getOldTrashItems(threshold)
            for (item in oldItems) {
                val file = File(item.trashTempPath)
                if (file.exists()) {
                    file.deleteRecursively()
                }
            }
            trashDao.deleteOldTrashItems(threshold)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // -------------------------------------------------------------
    // FAVORITES TOGGLE
    // -------------------------------------------------------------
    suspend fun toggleFavorite(item: FileItem) = withContext(Dispatchers.IO) {
        if (favoriteDao.isFavorite(item.path)) {
            favoriteDao.deleteFavoriteByPath(item.path)
        } else {
            favoriteDao.insertFavorite(
                FavoriteEntity(
                    path = item.path,
                    displayName = item.name,
                    isDirectory = item.isDirectory
                )
            )
        }
    }

    suspend fun getFavoriteFiles(
        sortOption: SortOption = SortOption.NAME,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        searchQuery: String = ""
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val favEntities = try {
            favoriteDao.getAllFavoritesList()
        } catch (_: Exception) {
            emptyList()
        }
        val items = mutableListOf<FileItem>()
        for (fav in favEntities) {
            val file = File(fav.path)
            val isDir = fav.isDirectory || (file.exists() && file.isDirectory)
            val name = fav.displayName.ifEmpty { file.name }
            val ext = file.extension.lowercase()
            val mime = getMimeType(file)
            val type = if (isDir) FileType.FOLDER else getFileTypeFromExtension(ext, mime)

            val item = FileItem(
                id = fav.path,
                name = name,
                path = fav.path,
                size = if (file.exists() && !isDir) file.length() else 0L,
                lastModified = if (file.exists()) file.lastModified() else fav.addedTimestamp,
                isDirectory = isDir,
                fileType = type,
                extension = ext,
                isFavorite = true,
                childCount = if (isDir && file.exists()) (file.list()?.size ?: 0) else 0,
                mimeType = mime
            )
            items.add(item)
        }

        val filtered = if (searchQuery.isNotBlank()) {
            items.filter { it.name.contains(searchQuery, ignoreCase = true) || it.extension.equals(searchQuery.removePrefix("."), ignoreCase = true) }
        } else {
            items
        }

        when (sortOption) {
            SortOption.NAME -> if (sortOrder == SortOrder.ASCENDING) filtered.sortedBy { it.name } else filtered.sortedByDescending { it.name }
            SortOption.DATE -> if (sortOrder == SortOrder.ASCENDING) filtered.sortedBy { it.lastModified } else filtered.sortedByDescending { it.lastModified }
            SortOption.SIZE -> if (sortOrder == SortOrder.ASCENDING) filtered.sortedBy { it.size } else filtered.sortedByDescending { it.size }
            SortOption.TYPE -> if (sortOrder == SortOrder.ASCENDING) filtered.sortedBy { it.fileType.name } else filtered.sortedByDescending { it.fileType.name }
        }
    }

    // -------------------------------------------------------------
    // ZIP & ARCHIVE OPERATIONS
    // -------------------------------------------------------------
    suspend fun listZipContents(zipFilePath: String): List<ZipEntryItem> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<ZipEntryItem>()
        val zipFile = File(zipFilePath)
        if (!zipFile.exists()) return@withContext entries

        try {
            ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    val isDir = entry.isDirectory || entryName.endsWith("/")
                    entries.add(
                        ZipEntryItem(
                            name = entryName.trimEnd('/').substringAfterLast('/'),
                            fullPath = entryName,
                            size = entry.size.coerceAtLeast(0L),
                            compressedSize = entry.compressedSize.coerceAtLeast(0L),
                            isDirectory = isDir,
                            time = entry.time
                        )
                    )
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        entries
    }

    suspend fun createZip(
        sourcePaths: List<String>,
        outputZipPath: String,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val destFile = File(outputZipPath)
        destFile.parentFile?.mkdirs()

        val filesToZip = mutableListOf<Pair<File, String>>()
        for (p in sourcePaths) {
            val f = File(p)
            if (f.exists()) {
                collectFilesForZip(f, f.name, filesToZip)
            }
        }

        val totalFiles = filesToZip.size.coerceAtLeast(1)
        var processed = 0

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destFile))).use { zipOut ->
                for ((file, entryName) in filesToZip) {
                    processed++
                    onProgress(processed.toFloat() / totalFiles, file.name)
                    if (file.isDirectory) {
                        val dirEntry = ZipEntry(if (entryName.endsWith("/")) entryName else "$entryName/")
                        zipOut.putNextEntry(dirEntry)
                        zipOut.closeEntry()
                    } else {
                        val fileEntry = ZipEntry(entryName)
                        zipOut.putNextEntry(fileEntry)
                        FileInputStream(file).use { fis ->
                            fis.copyTo(zipOut, bufferSize = 16 * 1024)
                        }
                        zipOut.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun collectFilesForZip(file: File, relativePath: String, list: MutableList<Pair<File, String>>) {
        if (file.isDirectory) {
            list.add(Pair(file, relativePath))
            val children = file.listFiles() ?: return
            for (child in children) {
                collectFilesForZip(child, "$relativePath/${child.name}", list)
            }
        } else {
            list.add(Pair(file, relativePath))
        }
    }

    suspend fun extractZip(
        zipFilePath: String,
        targetDirectory: String,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val zipFile = File(zipFilePath)
        val targetDir = File(targetDirectory)
        if (!zipFile.exists()) return@withContext false
        targetDir.mkdirs()

        val entries = listZipContents(zipFilePath)
        val total = entries.size.coerceAtLeast(1)
        var count = 0

        try {
            ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    count++
                    onProgress(count.toFloat() / total, entry.name)

                    val outFile = File(targetDir, entry.name)
                    // Security check against Zip Slip
                    if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        throw SecurityException("Zip Slip vulnerability detected: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zipIn.copyTo(fos, bufferSize = 16 * 1024)
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // -------------------------------------------------------------
    // APK INSPECTOR & ANALYZER
    // -------------------------------------------------------------
    suspend fun inspectApk(apkFilePath: String): ApkInfo? = withContext(Dispatchers.IO) {
        val apkFile = File(apkFilePath)
        val isCloudOrVirtual = apkFilePath.startsWith("/cloud/") || !apkFile.exists()

        if (isCloudOrVirtual) {
            val name = apkFile.nameWithoutExtension.ifEmpty { "App Prototype" }
            val cleanPkgName = "com.cloud.app.${name.lowercase().replace(Regex("[^a-z0-9]"), "_")}"
            return@withContext ApkInfo(
                packageName = cleanPkgName,
                versionName = "1.0.0",
                versionCode = 1L,
                minSdk = 26,
                targetSdk = 34,
                appName = name.replace("_", " "),
                permissions = listOf(
                    "android.permission.INTERNET",
                    "android.permission.ACCESS_NETWORK_STATE",
                    "android.permission.READ_EXTERNAL_STORAGE",
                    "android.permission.WRITE_EXTERNAL_STORAGE",
                    "android.permission.POST_NOTIFICATIONS"
                ),
                abis = listOf("arm64-v8a", "x86_64"),
                apkFilePath = apkFilePath
            )
        }

        try {
            val pm = context.packageManager
            // Use flag 0 for instant metadata reading without slow manifest/permission parsing
            val packageInfo: PackageInfo? = try {
                pm.getPackageArchiveInfo(apkFilePath, 0)
            } catch (_: Exception) {
                null
            }

            if (packageInfo != null) {
                val appInfo = packageInfo.applicationInfo ?: return@withContext null
                appInfo.sourceDir = apkFilePath
                appInfo.publicSourceDir = apkFilePath

                val appName = try { pm.getApplicationLabel(appInfo).toString() } catch (e: Exception) { apkFile.nameWithoutExtension }
                val pkgName = packageInfo.packageName
                val verName = packageInfo.versionName ?: "1.0"
                val verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
                val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 21
                val targetSdk = appInfo.targetSdkVersion

                val abis = mutableListOf<String>()

                // Fast random-access ABI lookup using ZipFile central directory
                try {
                    java.util.zip.ZipFile(apkFile).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val name = entry.name
                            if (name.startsWith("lib/") && name.split("/").size > 2) {
                                val abi = name.split("/")[1]
                                if (!abis.contains(abi)) abis.add(abi)
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (abis.isEmpty()) abis.add("universal")

                return@withContext ApkInfo(
                    packageName = pkgName,
                    versionName = verName,
                    versionCode = verCode,
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    appName = appName,
                    permissions = emptyList(),
                    abis = abis,
                    apkFilePath = apkFilePath
                )
            } else {
                // Fallback ApkInfo for mock/external files
                return@withContext ApkInfo(
                    packageName = context.packageName,
                    versionName = "1.0",
                    versionCode = 1L,
                    minSdk = 24,
                    targetSdk = 33,
                    appName = apkFile.nameWithoutExtension,
                    permissions = emptyList(),
                    abis = listOf("arm64-v8a", "x86_64"),
                    apkFilePath = apkFilePath
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun getInstalledApps(subFilter: String = "ALL"): List<FileItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<FileItem>()
        val pm = context.packageManager

        val favoritePaths = try {
            favoriteDao.getAllFavoritePaths().toSet()
        } catch (_: Exception) {
            emptySet()
        }

        if (subFilter == "ALL" || subFilter == "USER" || subFilter == "SYSTEM") {
            try {
                val flags = PackageManager.GET_META_DATA
                val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledPackages(flags)
                }

                for (pkg in packages) {
                    val appInfo = pkg.applicationInfo ?: continue
                    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    if (subFilter == "USER" && isSystem) continue
                    if (subFilter == "SYSTEM" && !isSystem) continue

                    val appName = try { appInfo.loadLabel(pm).toString() } catch (_: Exception) { pkg.packageName }
                    val sourcePath = appInfo.sourceDir ?: continue
                    val apkFile = File(sourcePath)
                    val size = if (apkFile.exists()) apkFile.length() else 0L
                    val lastModified = pkg.lastUpdateTime.takeIf { it > 0 }
                        ?: pkg.firstInstallTime.takeIf { it > 0 }
                        ?: if (apkFile.exists()) apkFile.lastModified() else 0L

                    items.add(
                        FileItem(
                            id = pkg.packageName,
                            name = appName,
                            path = sourcePath,
                            size = size,
                            lastModified = lastModified,
                            isDirectory = false,
                            fileType = FileType.APK,
                            extension = "apk",
                            isFavorite = favoritePaths.contains(sourcePath) || favoritePaths.contains(pkg.packageName),
                            childCount = 0,
                            mimeType = "application/vnd.android.package-archive",
                            packageName = pkg.packageName,
                            appCategory = if (isSystem) "SYSTEM" else "USER",
                            versionName = pkg.versionName ?: "1.0"
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // If ALL or APK_FILES, also add standalone APK files found in storage
        if (subFilter == "ALL" || subFilter == "APK_FILES") {
            val rootDir = Environment.getExternalStorageDirectory()
            if (rootDir.exists() && rootDir.isDirectory) {
                fun traverse(dir: File, depth: Int = 0) {
                    if (depth > 5) return
                    if (dir.name == "Android" || dir.name.startsWith(".")) return
                    val files = dir.listFiles() ?: return
                    for (file in files) {
                        if (file.isDirectory) {
                            traverse(file, depth + 1)
                        } else if (file.extension.equals("apk", ignoreCase = true)) {
                            val pkgInfo = try { pm.getPackageArchiveInfo(file.absolutePath, 0) } catch (_: Exception) { null }
                            val appName = pkgInfo?.applicationInfo?.let {
                                it.sourceDir = file.absolutePath
                                it.publicSourceDir = file.absolutePath
                                try { pm.getApplicationLabel(it).toString() } catch (_: Exception) { null }
                            } ?: file.nameWithoutExtension

                            items.add(
                                FileItem(
                                    id = file.absolutePath,
                                    name = appName,
                                    path = file.absolutePath,
                                    size = file.length(),
                                    lastModified = file.lastModified(),
                                    isDirectory = false,
                                    fileType = FileType.APK,
                                    extension = "apk",
                                    isFavorite = favoritePaths.contains(file.absolutePath),
                                    childCount = 0,
                                    mimeType = "application/vnd.android.package-archive",
                                    packageName = pkgInfo?.packageName,
                                    appCategory = "APK_FILES",
                                    versionName = pkgInfo?.versionName
                                )
                            )
                        }
                    }
                }
                traverse(rootDir)
            }
        }

        items
    }

    // -------------------------------------------------------------
    // CODE & TEXT FILE EDITOR
    // -------------------------------------------------------------
    suspend fun readTextFile(filePath: String): String = withContext(Dispatchers.IO) {
        if (filePath.startsWith("content://")) {
            return@withContext try {
                val uri = Uri.parse(filePath)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                } ?: "Arquivo não encontrado."
            } catch (e: Exception) {
                "Erro ao ler arquivo da nuvem: ${e.localizedMessage}"
            }
        }
        try {
            val file = File(filePath)
            if (file.exists() && file.canRead()) {
                file.readText(Charsets.UTF_8)
            } else if (com.example.util.RootHelper.isRootAvailable()) {
                com.example.util.RootHelper.readText(filePath)
            } else {
                file.readText(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            if (com.example.util.RootHelper.isRootAvailable()) {
                com.example.util.RootHelper.readText(filePath)
            } else {
                "Erro ao ler arquivo: ${e.localizedMessage}"
            }
        }
    }

    suspend fun saveTextFile(filePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
        if (filePath.startsWith("content://")) {
            return@withContext try {
                val uri = Uri.parse(filePath)
                context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                }
                true
            } catch (e: Exception) {
                Log.e("FileRepository", "Failed to save SAF file", e)
                false
            }
        }
        try {
            val file = File(filePath)
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            if (com.example.util.RootHelper.isRootAvailable()) {
                com.example.util.RootHelper.writeText(filePath, content)
            } else {
                false
            }
        }
    }

    // -------------------------------------------------------------
    // STORAGE ANALYSIS DASHBOARD (Optimized Single-Pass Caching)
    // -------------------------------------------------------------
    data class StorageScanSnapshot(
        val timestamp: Long,
        val rootPath: String,
        val fileList: List<File>,
        val emptyFoldersList: List<File>,
        val stats: List<StorageCategoryStats>,
        val largeFiles: List<FileItem>,
        val duplicateGroups: List<DuplicateGroup>
    )

    private var cachedStorageSnapshot: StorageScanSnapshot? = null

    fun invalidateStorageCache() {
        cachedStorageSnapshot = null
    }

    private suspend fun getOrComputeStorageSnapshot(rootPath: String, forceRefresh: Boolean = false): StorageScanSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedStorageSnapshot
        if (!forceRefresh && cached != null && cached.rootPath == rootPath && (now - cached.timestamp < 30_000L)) {
            return@withContext cached
        }

        val root = File(rootPath)
        val fileList = mutableListOf<File>()
        val emptyFoldersList = mutableListOf<File>()
        if (root.exists()) {
            collectAllFilesAndEmptyFolders(root, fileList, emptyFoldersList, maxDepth = 5, currentDepth = 0)
        }

        val statsMap = mutableMapOf<FileType, Pair<Long, Int>>()
        FileType.values().forEach { statsMap[it] = Pair(0L, 0) }

        val largeFileList = mutableListOf<FileItem>()
        val sizeGroupMap = mutableMapOf<Long, MutableList<File>>()

        for (f in fileList) {
            val len = f.length()
            val ext = f.extension.lowercase()
            val mime = getMimeType(f)
            val type = getFileTypeFromExtension(ext, mime, f.name, f.absolutePath)

            val current = statsMap[type] ?: Pair(0L, 0)
            statsMap[type] = Pair(current.first + len, current.second + 1)

            if (len >= 50 * 1024 * 1024L) {
                largeFileList.add(
                    FileItem(
                        id = f.absolutePath,
                        name = f.name,
                        path = f.absolutePath,
                        size = len,
                        lastModified = f.lastModified(),
                        isDirectory = false,
                        fileType = type,
                        extension = ext,
                        mimeType = mime
                    )
                )
            }

            if (len > 100 * 1024L) {
                sizeGroupMap.getOrPut(len) { mutableListOf() }.add(f)
            }
        }

        statsMap[FileType.FOLDER] = Pair(0L, emptyFoldersList.size)

        val stats = statsMap.map { (type, pair) ->
            StorageCategoryStats(
                fileType = type,
                name = getCategoryName(type),
                bytes = pair.first,
                fileCount = pair.second
            )
        }.sortedWith(Comparator { a, b ->
            if (a.fileType == FileType.FOLDER && b.fileType == FileType.FOLDER) 0
            else if (a.fileType == FileType.FOLDER) 1
            else if (b.fileType == FileType.FOLDER) -1
            else b.bytes.compareTo(a.bytes)
        })

        val duplicates = mutableListOf<DuplicateGroup>()
        for ((size, files) in sizeGroupMap) {
            if (files.size > 1) {
                val items = files.map { f ->
                    val ext = f.extension.lowercase()
                    val mime = getMimeType(f)
                    val type = getFileTypeFromExtension(ext, mime)
                    FileItem(
                        id = f.absolutePath,
                        name = f.name,
                        path = f.absolutePath,
                        size = size,
                        lastModified = f.lastModified(),
                        isDirectory = false,
                        fileType = type,
                        extension = ext,
                        mimeType = mime
                    )
                }
                duplicates.add(
                    DuplicateGroup(
                        key = "dup_${size}_${files.first().name}",
                        size = size,
                        files = items
                    )
                )
            }
        }

        val snapshot = StorageScanSnapshot(
            timestamp = now,
            rootPath = rootPath,
            fileList = fileList,
            emptyFoldersList = emptyFoldersList,
            stats = stats,
            largeFiles = largeFileList.sortedByDescending { it.size },
            duplicateGroups = duplicates.sortedByDescending { it.size * it.files.size }
        )
        cachedStorageSnapshot = snapshot
        snapshot
    }

    suspend fun analyzeStorage(rootPath: String, forceRefresh: Boolean = false): List<StorageCategoryStats> = withContext(Dispatchers.IO) {
        getOrComputeStorageSnapshot(rootPath, forceRefresh).stats
    }

    suspend fun getCategoryDetails(rootPath: String, fileType: FileType): CategoryDetailInfo = withContext(Dispatchers.IO) {
        val snapshot = getOrComputeStorageSnapshot(rootPath)
        val fileList = snapshot.fileList
        val emptyFoldersList = snapshot.emptyFoldersList

        if (fileType == FileType.FOLDER) {
            val folderMap = mutableMapOf<String, MutableList<FileItem>>()
            for (emptyDir in emptyFoldersList) {
                val parentPath = emptyDir.parentFile?.absolutePath ?: rootPath
                val item = FileItem(
                    id = emptyDir.absolutePath,
                    name = emptyDir.name,
                    path = emptyDir.absolutePath,
                    size = 0L,
                    lastModified = emptyDir.lastModified(),
                    isDirectory = true,
                    fileType = FileType.FOLDER,
                    extension = ""
                )
                folderMap.getOrPut(parentPath) { mutableListOf() }.add(item)
            }

            val foldersList = folderMap.map { (folderPath, items) ->
                val folderFile = File(folderPath)
                val folderName = if (folderPath == rootPath) "Pasta Raiz" else folderFile.name
                CategoryFolderInfo(
                    folderName = folderName,
                    folderPath = folderPath,
                    fileCount = items.size,
                    totalSize = 0L,
                    files = items
                )
            }.sortedByDescending { it.fileCount }

            return@withContext CategoryDetailInfo(
                fileType = FileType.FOLDER,
                categoryName = getCategoryName(FileType.FOLDER),
                totalSize = 0L,
                totalFiles = emptyFoldersList.size,
                folders = foldersList
            )
        }

        val folderMap = mutableMapOf<String, MutableList<FileItem>>()
        var catTotalSize = 0L
        var catTotalFiles = 0

        for (f in fileList) {
            val ext = f.extension.lowercase()
            val mime = getMimeType(f)
            val type = getFileTypeFromExtension(ext, mime, f.name, f.absolutePath)

            if (type == fileType) {
                val len = f.length()
                catTotalSize += len
                catTotalFiles++

                val parentFile = f.parentFile
                val parentPath = parentFile?.absolutePath ?: rootPath

                val item = FileItem(
                    id = f.absolutePath,
                    name = f.name,
                    path = f.absolutePath,
                    size = len,
                    lastModified = f.lastModified(),
                    isDirectory = false,
                    fileType = type,
                    extension = ext,
                    mimeType = mime
                )

                folderMap.getOrPut(parentPath) { mutableListOf() }.add(item)
            }
        }

        val foldersList = folderMap.map { (folderPath, files) ->
            val folderFile = File(folderPath)
            val folderName = if (folderPath == rootPath) "Pasta Raiz" else folderFile.name
            CategoryFolderInfo(
                folderName = folderName,
                folderPath = folderPath,
                fileCount = files.size,
                totalSize = files.sumOf { it.size },
                files = files.sortedByDescending { it.size }
            )
        }.sortedByDescending { it.totalSize }

        CategoryDetailInfo(
            fileType = fileType,
            categoryName = getCategoryName(fileType),
            totalSize = catTotalSize,
            totalFiles = catTotalFiles,
            folders = foldersList
        )
    }

    suspend fun findLargeFiles(rootPath: String, minSizeBytes: Long = 50 * 1024 * 1024L): List<FileItem> = withContext(Dispatchers.IO) {
        val snapshot = getOrComputeStorageSnapshot(rootPath)
        snapshot.largeFiles.filter { it.size >= minSizeBytes }
    }

    suspend fun findDuplicateFiles(rootPath: String): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val snapshot = getOrComputeStorageSnapshot(rootPath)
        snapshot.duplicateGroups
    }

    private fun collectAllFiles(dir: File, result: MutableList<File>, maxDepth: Int, currentDepth: Int) {
        collectAllFilesAndEmptyFolders(dir, result, mutableListOf(), maxDepth, currentDepth)
    }

    private fun collectAllFilesAndEmptyFolders(
        dir: File,
        filesResult: MutableList<File>,
        emptyFoldersResult: MutableList<File>,
        maxDepth: Int,
        currentDepth: Int
    ): Boolean {
        if (currentDepth > maxDepth) return true
        val children = dir.listFiles() ?: return false
        if (children.isEmpty()) {
            emptyFoldersResult.add(dir)
            return false
        }
        var hasFiles = false
        for (c in children) {
            if (c.isDirectory) {
                if (!c.name.startsWith(".") && c.name != "Android") {
                    val childHasFiles = collectAllFilesAndEmptyFolders(c, filesResult, emptyFoldersResult, maxDepth, currentDepth + 1)
                    if (childHasFiles) hasFiles = true
                }
            } else {
                filesResult.add(c)
                hasFiles = true
            }
        }
        if (!hasFiles) {
            emptyFoldersResult.add(dir)
        }
        return hasFiles
    }

    // Helper functions
    private fun getFileTypeFromExtension(
        extension: String,
        mimeType: String?,
        fileName: String = "",
        filePath: String = ""
    ): FileType {
        val ext = extension.lowercase()
        val nameLower = fileName.lowercase()
        val pathLower = filePath.lowercase()

        // Temporários & Residuais
        if (ext in listOf("tmp", "temp", "log", "cache", "bak", "old", "chk", "part", "crdownload", "dmp", "swp", "cnt", "thumbs", "residual") ||
            nameLower.startsWith("~") || nameLower == "thumbs.db" || nameLower == ".ds_store" ||
            nameLower.contains(".tmp.") || nameLower.endsWith(".tmp") || nameLower.endsWith(".bak") || nameLower.endsWith(".log") ||
            pathLower.contains("/cache/") || pathLower.contains("/.cache/") || pathLower.contains("/temp/")
        ) {
            return FileType.TEMP_RESIDUAL
        }

        return when (ext) {
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "heic", "heif", "tiff", "ico", "raw", "cr2", "nef", "arw", "dng", "psd" -> FileType.IMAGE
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "m4v", "wmv", "ts", "mpg", "mpeg", "m2ts", "vob", "ogv", "divx", "asf", "rm", "rmvb", "f4v", "3g2", "m2v" -> FileType.VIDEO
            "mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "opus", "mid", "midi", "amr", "alac", "aiff", "pcm", "m4p" -> FileType.AUDIO
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp", "csv", "epub" -> FileType.DOCUMENT
            "apk", "xapk", "apks", "apkm", "idsig" -> FileType.APK
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso" -> FileType.ARCHIVE
            "kt", "java", "py", "js", "ts", "json", "xml", "html", "css", "md", "c", "cpp", "sh", "yml", "yaml", "properties", "sql", "ktm", "gradle" -> FileType.CODE
            else -> FileType.OTHER
        }
    }

    private fun getMimeType(file: File): String {
        return getMimeTypeFromExtension(file.extension)
    }

    private fun getMimeTypeFromExtension(extension: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"
    }

    private fun getCategoryName(type: FileType): String = when (type) {
        FileType.FOLDER -> "Pastas Vazias"
        FileType.IMAGE -> "Imagens"
        FileType.VIDEO -> "Vídeos"
        FileType.AUDIO -> "Áudios"
        FileType.DOCUMENT -> "Documentos"
        FileType.APK -> "APK"
        FileType.ARCHIVE -> "Compactados"
        FileType.CODE -> "Código & Texto"
        FileType.TEMP_RESIDUAL -> "Temporários & Residuais"
        FileType.OTHER -> "Outros"
    }

    private fun writeBitmapToFile(file: File, label: String, bgColor: Int, isPng: Boolean = false) {
        try {
            val bmp = android.graphics.Bitmap.createBitmap(400, 400, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(bgColor)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 32f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText(label, 200f, 210f, paint)
            val format = if (isPng) android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG
            FileOutputStream(file).use { out ->
                bmp.compress(format, 90, out)
            }
        } catch (_: Exception) {
            file.writeBytes(ByteArray(10000) { 0 })
        }
    }

    private fun ensureMockFilesExist() {
        if (mockFilesCreated) return
        try {
            mockFilesCreated = true
            val internalDir = Environment.getExternalStorageDirectory()
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

            listOf(internalDir, downloadsDir, documentsDir, dcimDir, musicDir, moviesDir, picturesDir).forEach { it.mkdirs() }

            // Mock Empty Folders
            listOf(
                File(downloadsDir, "Pastas_Vazias_Download_Demo"),
                File(documentsDir, "Projeto_Antigo_Vazio"),
                File(picturesDir, "Album_Sem_Fotos")
            ).forEach { it.mkdirs() }

            // Mock Temp & Residual Files
            val mockTemp1 = File(downloadsDir, "temp_download_cache.tmp")
            if (!mockTemp1.exists()) mockTemp1.writeBytes(ByteArray(850000) { 0 })

            val mockTemp2 = File(documentsDir, "app_debug_residual.log")
            if (!mockTemp2.exists()) mockTemp2.writeBytes(ByteArray(1250000) { 0 })

            val mockTemp3 = File(internalDir, "thumbs_cache_backup.bak")
            if (!mockTemp3.exists()) mockTemp3.writeBytes(ByteArray(420000) { 0 })

            // Document Mocks
            val mockTextFile = File(documentsDir, "Notas_de_Reuniao.txt")
            if (!mockTextFile.exists()) {
                mockTextFile.writeText("Anotações importantes para o projeto Arcbox File Manager:\n1. Interface limpa e rápida\n2. Suporte a temas\n3. Animações de seleção de arquivos")
            }

            val mockMdFile = File(documentsDir, "README_Projeto.md")
            if (!mockMdFile.exists()) {
                mockMdFile.writeText("# Arcbox Storage\nGerenciador de arquivos completo para Android em Jetpack Compose.")
            }

            val mockJson = File(documentsDir, "configuracoes.json")
            if (!mockJson.exists()) {
                mockJson.writeText("{\n  \"theme\": \"dark\",\n  \"gridColumns\": 3,\n  \"autoBackup\": true\n}")
            }

            // Archive Mocks
            val mockZip1 = File(downloadsDir, "backup_documentos.zip")
            if (!mockZip1.exists()) {
                mockZip1.writeBytes(ByteArray(254800) { 0 })
            }

            val mockZip2 = File(documentsDir, "projeto_source.zip")
            if (!mockZip2.exists()) {
                mockZip2.writeBytes(ByteArray(1250000) { 0 })
            }

            // 10 Test Images in Pictures
            val imageNames = listOf(
                "Foto_Praia_Sunset.jpg",
                "Paisagem_Montanha.jpg",
                "Avatar_Perfil.png",
                "Captura_Tela_Design.png",
                "Fotografia_Urbana.jpg",
                "Natureza_Floresta.jpg",
                "Wallpaper_Minimalista.png",
                "Documento_Digitalizado.jpg",
                "Ilustracao_Vector.png",
                "Foto_Evento_2026.jpg"
            )
            val colors = listOf(
                android.graphics.Color.BLUE,
                android.graphics.Color.RED,
                android.graphics.Color.GREEN,
                android.graphics.Color.MAGENTA,
                android.graphics.Color.CYAN,
                android.graphics.Color.YELLOW,
                android.graphics.Color.LTGRAY,
                android.graphics.Color.DKGRAY,
                android.graphics.Color.rgb(255, 128, 0),
                android.graphics.Color.rgb(128, 0, 255)
            )

            imageNames.forEachIndexed { index, name ->
                val imgFile = File(picturesDir, name)
                if (!imgFile.exists()) {
                    try {
                        val bmp = android.graphics.Bitmap.createBitmap(400, 400, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(colors[index % colors.size])
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 36f
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        canvas.drawText("Imagem #${index + 1}", 200f, 210f, paint)

                        val format = if (name.endsWith(".png")) android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG
                        FileOutputStream(imgFile).use { out ->
                            bmp.compress(format, 90, out)
                        }
                    } catch (_: Exception) {
                        imgFile.writeBytes(ByteArray(300000) { 0 })
                    }
                }
            }

            // Additional image in DCIM & Downloads
            val mockImage1 = File(dcimDir, "Foto_Praia_2026.jpg")
            if (!mockImage1.exists() || mockImage1.length() < 100) {
                writeBitmapToFile(mockImage1, "Foto Praia 2026", android.graphics.Color.rgb(0, 150, 200))
            }
            val mockImage2 = File(downloadsDir, "wallpaper_abstract.png")
            if (!mockImage2.exists() || mockImage2.length() < 100) {
                writeBitmapToFile(mockImage2, "Wallpaper Abstract", android.graphics.Color.rgb(180, 50, 220), isPng = true)
            }

            // Audio Mock
            val mockAudio = File(musicDir, "musica_lofi_demo.mp3")
            if (!mockAudio.exists()) {
                mockAudio.writeBytes(ByteArray(3400000) { 0 })
            }

            // 10 Test Videos in Movies
            val videoNames = listOf(
                "video_apresentacao.mp4",
                "Tutorial_Android_Compose.mp4",
                "Vlog_Viagem_Ferias.mp4",
                "Gravacao_Tela_Demo.mp4",
                "Clipe_Musical_HD.mp4",
                "Animacao_3D_Teaser.mp4",
                "TimeLapse_Por_do_Sol.mp4",
                "Drone_Vista_Aerea.mp4",
                "Gameplay_Highlights.mp4",
                "Conferencia_Tech_2026.mp4"
            )

            videoNames.forEachIndexed { index, name ->
                val vidFile = File(moviesDir, name)
                if (!vidFile.exists() || vidFile.length() < 100) {
                    val color = colors[(index + 3) % colors.size]
                    writeBitmapToFile(vidFile, "Vídeo #${index + 1}", color)
                }
            }

            // APK Mock - Copy actual installed APK so PackageParser doesn't report Invalid file
            val mockApk = File(downloadsDir, "Arcbox_v1.0.apk")
            val isMockApkValid = try {
                if (mockApk.exists() && mockApk.length() > 100000) {
                    context.packageManager.getPackageArchiveInfo(mockApk.absolutePath, 0) != null
                } else false
            } catch (_: Exception) {
                false
            }
            if (!mockApk.exists() || !isMockApkValid) {
                try {
                    val appSourceDir = context.applicationInfo?.sourceDir
                    if (!appSourceDir.isNullOrEmpty()) {
                        val realApkFile = File(appSourceDir)
                        if (realApkFile.exists()) {
                            realApkFile.copyTo(mockApk, overwrite = true)
                        }
                    }
                } catch (_: Exception) {}
            }

            // Internal Storage Root Mocks
            val rootDoc = File(internalDir, "relatorio_mensal.pdf")
            if (!rootDoc.exists()) {
                rootDoc.writeBytes(ByteArray(450000) { 0 })
            }
            val rootTxt = File(internalDir, "anotacoes.txt")
            if (!rootTxt.exists()) {
                rootTxt.writeText("Lista de tarefas:\n- Grid de 3 colunas\n- Botão circular de adição\n- Animação suave para seleção de arquivos")
            }
            val rootZip = File(internalDir, "pacote_fotos.zip")
            if (!rootZip.exists()) {
                rootZip.writeBytes(ByteArray(890000) { 0 })
            }
            val rootImg = File(internalDir, "imagem_exemplo.png")
            if (!rootImg.exists()) {
                rootImg.writeBytes(ByteArray(620000) { 0 })
            }

            // Mock Cache & Temporary files
            val tempDir = File(internalDir, ".cache")
            if (!tempDir.exists()) tempDir.mkdirs()
            val mockTmp1 = File(tempDir, "system_cache_dump.tmp")
            if (!mockTmp1.exists()) mockTmp1.writeBytes(ByteArray(1250000) { 0 })
            val mockLog = File(tempDir, "app_execution.log")
            if (!mockLog.exists()) mockLog.writeText("LOG STREAM 2026...\n" + "x".repeat(450000))
            val mockBak = File(downloadsDir, "old_backup_temp.bak")
            if (!mockBak.exists()) mockBak.writeBytes(ByteArray(850000) { 0 })

            // Mock Empty Folders
            val emptyDir1 = File(internalDir, "Pasta Vazia Temporaria")
            if (!emptyDir1.exists()) emptyDir1.mkdirs()
            val emptyDir2 = File(downloadsDir, "Temp Downloads Vazia")
            if (!emptyDir2.exists()) emptyDir2.mkdirs()
        } catch (_: Exception) {}
    }

    private fun searchRecursive(
        startDir: File,
        query: String,
        favoritePaths: Set<String>,
        maxResults: Int = 300
    ): List<FileItem> {
        val results = mutableListOf<FileItem>()
        val cleanQuery = query.trim()
        val cleanExt = cleanQuery.removePrefix(".").lowercase()

        fun traverse(dir: File, depth: Int) {
            if (depth > 7 || results.size >= maxResults) return
            val name = dir.name
            if (name == "Android" || name.startsWith(".") || name.equals("cache", ignoreCase = true)) return

            val files = dir.listFiles() ?: return
            for (file in files) {
                if (results.size >= maxResults) break
                val fname = file.name
                if (fname.startsWith(".")) continue

                val isDir = file.isDirectory
                val ext = file.extension.lowercase()
                val mime = getMimeType(file)
                val type = if (isDir) FileType.FOLDER else getFileTypeFromExtension(ext, mime)

                val matchesName = fname.contains(cleanQuery, ignoreCase = true)
                val matchesExt = cleanExt.isNotEmpty() && ext.equals(cleanExt, ignoreCase = true)

                if (matchesName || matchesExt) {
                    val item = FileItem(
                        id = file.absolutePath,
                        name = fname,
                        path = file.absolutePath,
                        size = if (isDir) 0L else file.length(),
                        lastModified = file.lastModified(),
                        isDirectory = isDir,
                        fileType = type,
                        extension = ext,
                        isFavorite = favoritePaths.contains(file.absolutePath),
                        childCount = if (isDir) (file.list()?.size ?: 0) else 0,
                        mimeType = mime
                    )
                    results.add(item)
                }

                if (isDir) {
                    traverse(file, depth + 1)
                }
            }
        }

        if (startDir.exists() && startDir.isDirectory) {
            traverse(startDir, 0)
        }
        return results
    }
}
