package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
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
    private var mockFilesCreated = false

    val allTrashItems: Flow<List<TrashEntity>> = trashDao.getAllTrashItems()
    val allFavorites: Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()

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

        // Downloads Shortcut
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir.exists()) {
            list.add(
                StorageVolume(
                    id = "downloads",
                    name = "Downloads",
                    path = downloadsDir.absolutePath,
                    totalBytes = totalInternal,
                    freeBytes = freeInternal,
                    typeKey = "DOWNLOADS"
                )
            )
        }

        // Documents Shortcut
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (documentsDir.exists()) {
            list.add(
                StorageVolume(
                    id = "documents",
                    name = "Documentos",
                    path = documentsDir.absolutePath,
                    totalBytes = totalInternal,
                    freeBytes = freeInternal,
                    typeKey = "DOCUMENTS"
                )
            )
        }

        // DCIM / Pictures Shortcut
        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        if (dcimDir.exists()) {
            list.add(
                StorageVolume(
                    id = "pictures",
                    name = "Imagens & Câmera",
                    path = dcimDir.absolutePath,
                    totalBytes = totalInternal,
                    freeBytes = freeInternal,
                    typeKey = "PICTURES"
                )
            )
        }

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
                        list.add(
                            StorageVolume(
                                id = "sdcard_$i",
                                name = "Cartão SD / Removível ($i)",
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
                                val volumeName = if (isOtg) "Armazenamento OTG ($name)" else "Cartão SD ($name)"
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
            list.add(
                StorageVolume(
                    id = "cloud_mega",
                    name = "Nuvem Mega",
                    path = "/cloud/mega",
                    totalBytes = 50 * 1024 * 1024 * 1024L,
                    freeBytes = 37 * 1024 * 1024 * 1024L,
                    typeKey = "CLOUD"
                )
            )
        }
        if (prefs.getBoolean("cloud_connected_drive", false)) {
            list.add(
                StorageVolume(
                    id = "cloud_drive",
                    name = "Google Drive",
                    path = "/cloud/drive",
                    totalBytes = 15 * 1024 * 1024 * 1024L,
                    freeBytes = 8 * 1024 * 1024 * 1024L,
                    typeKey = "CLOUD"
                )
            )
        }
        if (prefs.getBoolean("cloud_connected_mediafire", false)) {
            list.add(
                StorageVolume(
                    id = "cloud_mediafire",
                    name = "Mediafire",
                    path = "/cloud/mediafire",
                    totalBytes = 10 * 1024 * 1024 * 1024L,
                    freeBytes = 9 * 1024 * 1024 * 1024L,
                    typeKey = "CLOUD"
                )
            )
        }
        if (prefs.getBoolean("cloud_connected_onedrive", false)) {
            list.add(
                StorageVolume(
                    id = "cloud_onedrive",
                    name = "OneDrive",
                    path = "/cloud/onedrive",
                    totalBytes = 5 * 1024 * 1024 * 1024L,
                    freeBytes = 2 * 1024 * 1024 * 1024L,
                    typeKey = "CLOUD"
                )
            )
        }
        if (prefs.getBoolean("cloud_connected_dropbox", false)) {
            list.add(
                StorageVolume(
                    id = "cloud_dropbox",
                    name = "Dropbox",
                    path = "/cloud/dropbox",
                    totalBytes = 2 * 1024 * 1024 * 1024L,
                    freeBytes = 1200 * 1024 * 1024L,
                    typeKey = "CLOUD"
                )
            )
        }

        list
    }

    fun getCloudFiles(path: String): List<FileItem> {
        val items = mutableListOf<FileItem>()
        val cleanPath = path.removeSuffix("/")
        
        when (cleanPath) {
            "/cloud/mega" -> {
                items.add(createCloudFolder("Fotos de Viagem", "$cleanPath/Fotos de Viagem", 3))
                items.add(createCloudFolder("Backups", "$cleanPath/Backups", 1))
                items.add(createCloudFile("Anotações Importantes.txt", "$cleanPath/Anotações Importantes.txt", 1204, FileType.DOCUMENT, "txt"))
                items.add(createCloudFile("Musica Instrumental.mp3", "$cleanPath/Musica Instrumental.mp3", 5402019, FileType.AUDIO, "mp3"))
            }
            "/cloud/mega/Fotos de Viagem" -> {
                items.add(createCloudFile("Praia.jpg", "$cleanPath/Praia.jpg", 2401928, FileType.IMAGE, "jpg"))
                items.add(createCloudFile("Pôr do Sol.jpg", "$cleanPath/Pôr do Sol.jpg", 1802910, FileType.IMAGE, "jpg"))
                items.add(createCloudFile("Ondas do Mar.mp4", "$cleanPath/Ondas do Mar.mp4", 15402019, FileType.VIDEO, "mp4"))
            }
            "/cloud/mega/Backups" -> {
                items.add(createCloudFile("Backup_Sistema_2026.zip", "$cleanPath/Backup_Sistema_2026.zip", 104857600, FileType.ARCHIVE, "zip"))
            }
            
            "/cloud/drive" -> {
                items.add(createCloudFolder("Trabalho", "$cleanPath/Trabalho", 2))
                items.add(createCloudFolder("Projetos", "$cleanPath/Projetos", 1))
                items.add(createCloudFile("Planilha Orçamento.xlsx", "$cleanPath/Planilha Orçamento.xlsx", 45120, FileType.DOCUMENT, "xlsx"))
                items.add(createCloudFile("Apresentação de Slides.pptx", "$cleanPath/Apresentação de Slides.pptx", 3204910, FileType.DOCUMENT, "pptx"))
            }
            "/cloud/drive/Trabalho" -> {
                items.add(createCloudFile("Contrato de Prestação.pdf", "$cleanPath/Contrato de Prestação.pdf", 450123, FileType.DOCUMENT, "pdf"))
                items.add(createCloudFile("Relatório Semestral.docx", "$cleanPath/Relatório Semestral.docx", 85040, FileType.DOCUMENT, "docx"))
            }
            "/cloud/drive/Projetos" -> {
                items.add(createCloudFile("App_Prototype.apk", "$cleanPath/App_Prototype.apk", 24501234, FileType.APK, "apk"))
            }

            "/cloud/mediafire" -> {
                items.add(createCloudFolder("Filmes & Series", "$cleanPath/Filmes", 2))
                items.add(createCloudFile("Instalador Arcbox.exe", "$cleanPath/Instalador Arcbox.exe", 15402019, FileType.OTHER, "exe"))
            }
            "/cloud/mediafire/Filmes" -> {
                items.add(createCloudFile("Filme_Classico.mkv", "$cleanPath/Filme_Classico.mkv", 1450123400, FileType.VIDEO, "mkv"))
                items.add(createCloudFile("Video_Tutorial.mp4", "$cleanPath/Video_Tutorial.mp4", 85040000, FileType.VIDEO, "mp4"))
            }

            "/cloud/onedrive" -> {
                items.add(createCloudFolder("Documentos Faculdade", "$cleanPath/Documentos Faculdade", 2))
                items.add(createCloudFile("Perfil de Usuario.png", "$cleanPath/Perfil de Usuario.png", 540219, FileType.IMAGE, "png"))
            }
            "/cloud/onedrive/Documentos Faculdade" -> {
                items.add(createCloudFile("TCC_Final_v2_revisado.docx", "$cleanPath/TCC_Final_v2_revisado.docx", 212000, FileType.DOCUMENT, "docx"))
                items.add(createCloudFile("Artigo Cientifico.pdf", "$cleanPath/Artigo Cientifico.pdf", 1250120, FileType.DOCUMENT, "pdf"))
            }

            "/cloud/dropbox" -> {
                items.add(createCloudFolder("Portfólio", "$cleanPath/Portfólio", 2))
                items.add(createCloudFile("Logo_Marca.ai", "$cleanPath/Logo_Marca.ai", 8402190, FileType.OTHER, "ai"))
            }
            "/cloud/dropbox/Portfólio" -> {
                items.add(createCloudFile("Portfolio_Design.pdf", "$cleanPath/Portfolio_Design.pdf", 12450120, FileType.DOCUMENT, "pdf"))
                items.add(createCloudFile("Website_Screenshot.png", "$cleanPath/Website_Screenshot.png", 1450120, FileType.IMAGE, "png"))
            }
        }
        return items
    }

    private fun createCloudFolder(name: String, path: String, childCount: Int): FileItem {
        return FileItem(
            id = path,
            name = name,
            path = path,
            isDirectory = true,
            fileType = FileType.FOLDER,
            childCount = childCount,
            lastModified = System.currentTimeMillis()
        )
    }

    private fun createCloudFile(name: String, path: String, size: Long, type: FileType, ext: String): FileItem {
        return FileItem(
            id = path,
            name = name,
            path = path,
            size = size,
            isDirectory = false,
            fileType = type,
            extension = ext,
            lastModified = System.currentTimeMillis() - 3600000L
        )
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
        isGlobalSearch: Boolean = false
    ): List<FileItem> = withContext(Dispatchers.IO) {
        if (filterCategory == FileType.APK) {
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

        if (directoryPath.startsWith("/cloud/")) {
            val cloudFiles = getCloudFiles(directoryPath)
            val filtered = if (filterCategory != null) {
                cloudFiles.filter { it.fileType == filterCategory }
            } else {
                cloudFiles
            }
            val searched = if (searchQuery.isNotEmpty()) {
                filtered.filter { it.name.contains(searchQuery, ignoreCase = true) }
            } else {
                filtered
            }
            val sorted = when (sortOption) {
                SortOption.NAME -> if (sortOrder == SortOrder.ASCENDING) searched.sortedBy { it.name } else searched.sortedByDescending { it.name }
                SortOption.DATE -> if (sortOrder == SortOrder.ASCENDING) searched.sortedBy { it.lastModified } else searched.sortedByDescending { it.lastModified }
                SortOption.SIZE -> if (sortOrder == SortOrder.ASCENDING) searched.sortedBy { it.size } else searched.sortedByDescending { it.size }
                SortOption.TYPE -> if (sortOrder == SortOrder.ASCENDING) searched.sortedBy { it.fileType.name } else searched.sortedByDescending { it.fileType.name }
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
            if (safUriString != null && safUriString.startsWith("content://")) {
                // Read via SAF DocumentFile
                val treeUri = Uri.parse(safUriString)
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDoc != null && rootDoc.isDirectory) {
                    val files = rootDoc.listFiles()
                    for (doc in files) {
                        val isDir = doc.isDirectory
                        val name = doc.name ?: "Sem nome"
                        val ext = name.substringAfterLast('.', "").lowercase()
                        val type = if (isDir) FileType.FOLDER else getFileTypeFromExtension(ext, doc.type)
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
                            childCount = if (isDir) (doc.listFiles().size) else 0,
                            mimeType = doc.type ?: "*/*"
                        )
                        items.add(item)
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
                    val searchDir = File(rootDirPath)
                    val searchResults = searchRecursive(searchDir, searchQuery, favoritePaths)
                    items.addAll(searchResults)
                } else {
                    val targetDir = File(directoryPath)
                    if (targetDir.exists() && targetDir.isDirectory) {
                        val files = targetDir.listFiles() ?: emptyArray()
                        for (file in files) {
                            val isDir = file.isDirectory
                            val name = file.name
                            val ext = file.extension.lowercase()
                            val mime = getMimeType(file)
                            val type = if (isDir) FileType.FOLDER else getFileTypeFromExtension(ext, mime)
                            val size = if (isDir) 0L else file.length()
                            val count = if (isDir) (file.list()?.size ?: 0) else 0

                            val item = FileItem(
                                id = file.absolutePath,
                                name = name,
                                path = file.absolutePath,
                                size = size,
                                lastModified = file.lastModified(),
                                isDirectory = isDir,
                                fileType = type,
                                extension = ext,
                                isFavorite = favoritePaths.contains(file.absolutePath),
                                childCount = count,
                                mimeType = mime
                            )
                            items.add(item)
                        }
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

    // -------------------------------------------------------------
    // FILE OPERATIONS (Create, Rename, Copy, Move, Delete to Trash)
    // -------------------------------------------------------------
    suspend fun createFolder(parentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        if (parentPath.startsWith("/cloud/")) return@withContext false
        val newDir = File(parentPath, folderName)
        if (!newDir.exists()) newDir.mkdirs() else false
    }

    suspend fun createFile(parentPath: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        if (parentPath.startsWith("/cloud/")) return@withContext false
        val newFile = File(parentPath, fileName)
        if (!newFile.exists()) newFile.createNewFile() else false
    }

    suspend fun renameFile(oldPath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        if (oldPath.startsWith("/cloud/")) return@withContext false
        val target = File(oldPath)
        val parent = target.parentFile ?: return@withContext false
        val dest = File(parent, newName)
        target.renameTo(dest)
    }

    suspend fun copyFile(sourcePath: String, targetDirectory: String, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (sourcePath.startsWith("/cloud/") || targetDirectory.startsWith("/cloud/")) return@withContext false
        val src = File(sourcePath)
        val destDir = File(targetDirectory)
        if (!src.exists() || !destDir.isDirectory) return@withContext false

        val dest = File(destDir, src.name)
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
        true
    }

    suspend fun moveFile(sourcePath: String, targetDirectory: String, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (sourcePath.startsWith("/cloud/") || targetDirectory.startsWith("/cloud/")) return@withContext false
        val src = File(sourcePath)
        val destDir = File(targetDirectory)
        if (!src.exists()) return@withContext false
        val dest = File(destDir, src.name)
        val renamed = src.renameTo(dest)
        if (!renamed) {
            val copied = copyFile(sourcePath, targetDirectory, onProgress)
            if (copied) {
                src.deleteRecursively()
            }
            copied
        } else {
            true
        }
    }

    suspend fun moveToTrash(item: FileItem): Boolean = withContext(Dispatchers.IO) {
        if (item.path.startsWith("/cloud/")) return@withContext false
        val sourceFile = File(item.path)
        if (!sourceFile.exists()) return@withContext false

        // Move to internal trash directory
        val trashFolder = File(context.filesDir, "arcbox_trash")
        if (!trashFolder.exists()) trashFolder.mkdirs()

        val trashFile = File(trashFolder, "${System.currentTimeMillis()}_${sourceFile.name}")
        val moved = sourceFile.renameTo(trashFile)
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
        val origFile = File(trashEntity.originalPath)

        origFile.parentFile?.mkdirs()
        val restored = trashFile.renameTo(origFile)
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
        if (!apkFile.exists()) return@withContext null

        try {
            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_CONFIGURATIONS
            val packageInfo: PackageInfo? = pm.getPackageArchiveInfo(apkFilePath, flags)

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

                val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
                val abis = mutableListOf<String>()

                // Extract ABIs from zip lib entries
                try {
                    ZipInputStream(FileInputStream(apkFile)).use { zip ->
                        var ze = zip.nextEntry
                        while (ze != null) {
                            val name = ze.name
                            if (name.startsWith("lib/") && name.split("/").size > 2) {
                                val abi = name.split("/")[1]
                                if (!abis.contains(abi)) abis.add(abi)
                            }
                            zip.closeEntry()
                            ze = zip.nextEntry
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
                    permissions = permissions,
                    abis = abis,
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
        try {
            File(filePath).readText(Charsets.UTF_8)
        } catch (e: Exception) {
            "Erro ao ler arquivo: ${e.localizedMessage}"
        }
    }

    suspend fun saveTextFile(filePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(filePath).writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    // -------------------------------------------------------------
    // STORAGE ANALYSIS DASHBOARD
    // -------------------------------------------------------------
    suspend fun analyzeStorage(rootPath: String): List<StorageCategoryStats> = withContext(Dispatchers.IO) {
        val root = File(rootPath)
        val statsMap = mutableMapOf<FileType, Pair<Long, Int>>()
        FileType.values().forEach { statsMap[it] = Pair(0L, 0) }

        if (root.exists()) {
            val fileList = mutableListOf<File>()
            collectAllFiles(root, fileList, maxDepth = 4, currentDepth = 0)

            for (f in fileList) {
                val ext = f.extension.lowercase()
                val mime = getMimeType(f)
                val type = getFileTypeFromExtension(ext, mime)
                val len = f.length()

                val current = statsMap[type] ?: Pair(0L, 0)
                statsMap[type] = Pair(current.first + len, current.second + 1)
            }
        }

        statsMap.map { (type, pair) ->
            StorageCategoryStats(
                fileType = type,
                name = getCategoryName(type),
                bytes = pair.first,
                fileCount = pair.second
            )
        }.sortedByDescending { it.bytes }
    }

    suspend fun getCategoryDetails(rootPath: String, fileType: FileType): CategoryDetailInfo = withContext(Dispatchers.IO) {
        val root = File(rootPath)
        val fileList = mutableListOf<File>()
        if (root.exists()) {
            collectAllFiles(root, fileList, maxDepth = 4, currentDepth = 0)
        }

        val folderMap = mutableMapOf<String, MutableList<FileItem>>()
        var catTotalSize = 0L
        var catTotalFiles = 0

        for (f in fileList) {
            val ext = f.extension.lowercase()
            val mime = getMimeType(f)
            val type = getFileTypeFromExtension(ext, mime)

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
        val root = File(rootPath)
        val result = mutableListOf<FileItem>()

        if (root.exists()) {
            val fileList = mutableListOf<File>()
            collectAllFiles(root, fileList, maxDepth = 4, currentDepth = 0)

            for (f in fileList) {
                if (f.length() >= minSizeBytes) {
                    val ext = f.extension.lowercase()
                    val mime = getMimeType(f)
                    val type = getFileTypeFromExtension(ext, mime)

                    result.add(
                        FileItem(
                            id = f.absolutePath,
                            name = f.name,
                            path = f.absolutePath,
                            size = f.length(),
                            lastModified = f.lastModified(),
                            isDirectory = false,
                            fileType = type,
                            extension = ext,
                            mimeType = mime
                        )
                    )
                }
            }
        }
        result.sortedByDescending { it.size }
    }

    suspend fun findDuplicateFiles(rootPath: String): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val root = File(rootPath)
        val sizeGroupMap = mutableMapOf<Long, MutableList<File>>()

        if (root.exists()) {
            val fileList = mutableListOf<File>()
            collectAllFiles(root, fileList, maxDepth = 4, currentDepth = 0)

            for (f in fileList) {
                val len = f.length()
                if (len > 100 * 1024L) { // Ignore tiny files < 100KB
                    sizeGroupMap.getOrPut(len) { mutableListOf() }.add(f)
                }
            }
        }

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
        duplicates.sortedByDescending { it.size * it.files.size }
    }

    private fun collectAllFiles(dir: File, result: MutableList<File>, maxDepth: Int, currentDepth: Int) {
        if (currentDepth > maxDepth) return
        val children = dir.listFiles() ?: return
        for (c in children) {
            if (c.isDirectory) {
                // Skip system hidden/android folders to stay fast
                if (!c.name.startsWith(".") && c.name != "Android") {
                    collectAllFiles(c, result, maxDepth, currentDepth + 1)
                }
            } else {
                result.add(c)
            }
        }
    }

    // Helper functions
    private fun getFileTypeFromExtension(extension: String, mimeType: String?): FileType {
        return when (extension) {
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "heic" -> FileType.IMAGE
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv" -> FileType.VIDEO
            "mp3", "flac", "wav", "aac", "ogg", "m4a", "wma" -> FileType.AUDIO
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt" -> FileType.DOCUMENT
            "apk", "xapk", "apks" -> FileType.APK
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> FileType.ARCHIVE
            "kt", "java", "py", "js", "ts", "json", "xml", "html", "css", "md", "c", "cpp", "sh" -> FileType.CODE
            else -> FileType.OTHER
        }
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }

    private fun getCategoryName(type: FileType): String = when (type) {
        FileType.FOLDER -> "Pastas"
        FileType.IMAGE -> "Imagens"
        FileType.VIDEO -> "Vídeos"
        FileType.AUDIO -> "Áudios"
        FileType.DOCUMENT -> "Documentos"
        FileType.APK -> "Aplicativos"
        FileType.ARCHIVE -> "Compactados"
        FileType.CODE -> "Código & Texto"
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
            bmp.recycle()
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
                        bmp.recycle()
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
                context.packageManager.getPackageArchiveInfo(mockApk.absolutePath, 0) != null
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
                        } else {
                            mockApk.writeBytes(ByteArray(4800000) { 0 })
                        }
                    } else {
                        mockApk.writeBytes(ByteArray(4800000) { 0 })
                    }
                } catch (_: Exception) {
                    mockApk.writeBytes(ByteArray(4800000) { 0 })
                }
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
