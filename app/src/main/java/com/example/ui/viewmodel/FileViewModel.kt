package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.FavoriteEntity
import com.example.data.db.TrashEntity
import com.example.data.models.*
import com.example.data.repository.FileRepository
import com.example.ui.theme.AccentColorOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

val DEFAULT_INITIAL_PATH = android.os.Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"

val DEFAULT_INTERNAL_STORAGE_VOLUME = StorageVolume(
    id = "internal",
    name = "Armazenamento Interno",
    path = DEFAULT_INITIAL_PATH,
    totalBytes = 0L,
    freeBytes = 0L,
    typeKey = "INTERNAL"
)

val DEFAULT_INITIAL_TAB = TabItem(
    id = "tab_main",
    title = "Armazenamento Interno",
    currentPath = DEFAULT_INITIAL_PATH,
    history = listOf(DEFAULT_INITIAL_PATH),
    historyIndex = 0
)

data class FileUiState(
    val currentTabId: String = "tab_main",
    val tabs: List<TabItem> = listOf(DEFAULT_INITIAL_TAB),
    val storageVolumes: List<StorageVolume> = listOf(DEFAULT_INTERNAL_STORAGE_VOLUME),
    val selectedVolume: StorageVolume? = DEFAULT_INTERNAL_STORAGE_VOLUME,
    val currentPath: String = DEFAULT_INITIAL_PATH,
    val currentFiles: List<FileItem> = emptyList(),
    val selectedItems: Set<FileItem> = emptySet(),
    val searchQuery: String = "",
    val isGlobalSearch: Boolean = false,
    val isFavoritesOnly: Boolean = false,
    val isRecentsOnly: Boolean = false,
    val searchHistory: List<String> = listOf("pdf", "jpg", "mp4", "apk", "doc", "zip"),
    val filterCategory: FileType? = null,
    val appSubFilter: String = "USER", // "USER", "SYSTEM", "APK_FILES"
    val isAppManagerOpen: Boolean = false,
    val sortOption: SortOption = SortOption.NAME,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val viewMode: ViewMode = ViewMode.LIST,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val accentOption: AccentColorOption = AccentColorOption.AZUL_CLARO,
    val customAccentColorHex: Long = 0xFF4F46E5L,
    val isLoading: Boolean = true,
    val operationProgress: Float? = null,
    val operationStatusText: String? = null,
    val activeMediaItem: FileItem? = null,
    val activeEditorFile: FileItem? = null,
    val editorContent: String = "",
    val activeApkInfo: ApkInfo? = null,
    val activeZipFile: FileItem? = null,
    val activeZipEntries: List<ZipEntryItem> = emptyList(),
    val tempZipSourcePath: String? = null,
    val isStorageDashboardOpen: Boolean = false,
    val isTrashBinOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isNewFolderDialogOpen: Boolean = false,
    val isRenameDialogOpen: Boolean = false,
    val itemToRename: FileItem? = null,
    val storageCategoryStats: List<StorageCategoryStats> = emptyList(),
    val largeFiles: List<FileItem> = emptyList(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val trashItems: List<TrashEntity> = emptyList(),
    val snackbarMessage: String? = null,
    val clipboardItems: Set<FileItem> = emptySet(),
    val clipboardMode: ClipboardMode? = null,
    val deletePermanently: Boolean = false,
    val confirmDelete: Boolean = true,
    val showThumbnails: Boolean = true,
    val isMegaConnected: Boolean = false,
    val isDriveConnected: Boolean = false,
    val isMediafireConnected: Boolean = false,
    val isOnedriveConnected: Boolean = false,
    val isDropboxConnected: Boolean = false,
    val isWebDavConnected: Boolean = false,
    val safCloudDrives: List<com.example.data.cloud.SafCloudDrive> = emptyList(),
    val megaAccountEmail: String = "conta.mega@arcbox.com",
    val driveAccountEmail: String = "usuario.drive@gmail.com",
    val mediafireAccountEmail: String = "usuario.mfire@mediafire.com",
    val onedriveAccountEmail: String = "usuario.office@outlook.com",
    val dropboxAccountEmail: String = "usuario.dbx@dropbox.com",
    val webdavAccountEmail: String = "usuario@meuservidor.com",
    val webdavServerUrl: String = "https://cloud.nextcloud.com",
    val isCloudManagerOpen: Boolean = false,
    val oauthConnectProvider: com.example.ui.components.CloudProvider? = null,
    val isWelcomeOnboardingOpen: Boolean = false,
    val isRootAvailable: Boolean = false,
    val isRootGranted: Boolean = false,
    val rootStatusDetails: String = ""
)

class FileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("arcbox_prefs", android.content.Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(buildInitialUiState(application, prefs))
    val uiState: StateFlow<FileUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    companion object {
        private fun buildInitialUiState(app: Application, prefs: android.content.SharedPreferences): FileUiState {
            val hasStoragePermission = com.example.util.PermissionHelper.hasAllFilesAccess(app)
            val onboardingShown = prefs.getBoolean("welcome_onboarding_shown", false)
            val shouldShowOnboarding = !hasStoragePermission || !onboardingShown

            val deletePermanently = prefs.getBoolean("delete_permanently", false)
            val confirmDelete = prefs.getBoolean("confirm_delete", true)
            val showThumbnails = prefs.getBoolean("show_thumbnails", true)

            val savedThemeModeName = prefs.getString("theme_mode", ThemeMode.LIGHT.name) ?: ThemeMode.LIGHT.name
            val loadedThemeMode = try { ThemeMode.valueOf(savedThemeModeName) } catch (_: Exception) { ThemeMode.LIGHT }

            val savedAccentName = prefs.getString("accent_color_option", AccentColorOption.AZUL_CLARO.name) ?: AccentColorOption.AZUL_CLARO.name
            val loadedAccent = try { AccentColorOption.valueOf(savedAccentName) } catch (_: Exception) { AccentColorOption.AZUL_CLARO }
            val loadedCustomColorHex = prefs.getLong("custom_accent_color_hex", 0xFF4F46E5L)

            val isMegaConnected = prefs.getBoolean("cloud_connected_mega", false)
            val isDriveConnected = prefs.getBoolean("cloud_connected_drive", false)
            val isMediafireConnected = prefs.getBoolean("cloud_connected_mediafire", false)
            val isOnedriveConnected = prefs.getBoolean("cloud_connected_onedrive", false)
            val isDropboxConnected = prefs.getBoolean("cloud_connected_dropbox", false)
            val isWebDavConnected = prefs.getBoolean("cloud_connected_webdav", false)

            val megaEmail = prefs.getString("cloud_email_mega", "conta.mega@arcbox.com") ?: "conta.mega@arcbox.com"
            val driveEmail = prefs.getString("cloud_email_drive", "usuario.drive@gmail.com") ?: "usuario.drive@gmail.com"
            val mediafireEmail = prefs.getString("cloud_email_mediafire", "usuario.mfire@mediafire.com") ?: "usuario.mfire@mediafire.com"
            val onedriveEmail = prefs.getString("cloud_email_onedrive", "usuario.office@outlook.com") ?: "usuario.office@outlook.com"
            val dropboxEmail = prefs.getString("cloud_email_dropbox", "usuario.dbx@dropbox.com") ?: "usuario.dbx@dropbox.com"
            val webdavEmail = prefs.getString("cloud_email_webdav", "usuario@meuservidor.com") ?: "usuario@meuservidor.com"
            val webdavUrl = prefs.getString("cloud_url_webdav", "https://cloud.nextcloud.com") ?: "https://cloud.nextcloud.com"

            return FileUiState(
                isWelcomeOnboardingOpen = shouldShowOnboarding,
                themeMode = loadedThemeMode,
                accentOption = loadedAccent,
                customAccentColorHex = loadedCustomColorHex,
                deletePermanently = deletePermanently,
                confirmDelete = confirmDelete,
                showThumbnails = showThumbnails,
                isMegaConnected = isMegaConnected,
                isDriveConnected = isDriveConnected,
                isMediafireConnected = isMediafireConnected,
                isOnedriveConnected = isOnedriveConnected,
                isDropboxConnected = isDropboxConnected,
                isWebDavConnected = isWebDavConnected,
                megaAccountEmail = megaEmail,
                driveAccountEmail = driveEmail,
                mediafireAccountEmail = mediafireEmail,
                onedriveAccountEmail = onedriveEmail,
                dropboxAccountEmail = dropboxEmail,
                webdavAccountEmail = webdavEmail,
                webdavServerUrl = webdavUrl,
                isLoading = true
            )
        }
    }

    init {
        loadInitialData()
        observeDatabaseFlows()
    }

    private fun observeDatabaseFlows() {
        viewModelScope.launch {
            repository.allFavorites.collect { favs ->
                _uiState.update { it.copy(favorites = favs) }
            }
        }
        viewModelScope.launch {
            repository.allTrashItems.collect { trash ->
                _uiState.update { it.copy(trashItems = trash) }
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            // Clean old trash items (30 days) asynchronously in background without delaying startup
            launch(Dispatchers.IO) {
                try {
                    repository.cleanOldTrashItems(30)
                } catch (_: Exception) {}
            }
            
            val volumes = repository.getStorageVolumes()
            val primary = volumes.firstOrNull()
            val initialPath = primary?.path ?: "/storage/emulated/0"

            val initialTab = TabItem(
                id = "tab_main",
                title = primary?.name ?: "Armazenamento",
                currentPath = initialPath,
                history = listOf(initialPath),
                historyIndex = 0
            )

            val rootStatus = com.example.util.RootHelper.getRootStatus(forceRefresh = false)
            val safDrives = repository.safCloudManager.getRegisteredDrives()

            _uiState.update {
                it.copy(
                    storageVolumes = volumes,
                    selectedVolume = primary,
                    safCloudDrives = safDrives,
                    tabs = listOf(initialTab),
                    currentTabId = initialTab.id,
                    currentPath = initialPath,
                    isRootAvailable = rootStatus.isAvailable,
                    isRootGranted = rootStatus.isGranted,
                    rootStatusDetails = rootStatus.details,
                    isLoading = true
                )
            }
            fetchFilesInternal()
        }
    }

    fun registerSafCloudDrive(uri: Uri, customLabel: String? = null) {
        viewModelScope.launch {
            val drive = repository.safCloudManager.registerSafDrive(uri, customLabel)
            if (drive != null) {
                val updatedDrives = repository.safCloudManager.getRegisteredDrives()
                val volumes = repository.getStorageVolumes()
                _uiState.update {
                    it.copy(
                        safCloudDrives = updatedDrives,
                        storageVolumes = volumes,
                        snackbarMessage = "Nuvem Real vinculada com sucesso: ${drive.name}"
                    )
                }
                refreshFiles()
            } else {
                showToast("Não foi possível vincular o armazenamento em nuvem SAF.")
            }
        }
    }

    fun removeSafCloudDrive(id: String) {
        viewModelScope.launch {
            repository.safCloudManager.removeSafDrive(id)
            val updatedDrives = repository.safCloudManager.getRegisteredDrives()
            val volumes = repository.getStorageVolumes()
            _uiState.update {
                it.copy(
                    safCloudDrives = updatedDrives,
                    storageVolumes = volumes,
                    snackbarMessage = "Armazenamento em nuvem desvinculado."
                )
            }
            refreshFiles()
        }
    }

    private suspend fun fetchFilesInternal() {
        val state = uiState.value
        val volumes = repository.getStorageVolumes()
        val files = if (state.isFavoritesOnly) {
            repository.getFavoriteFiles(
                sortOption = state.sortOption,
                sortOrder = state.sortOrder,
                searchQuery = state.searchQuery
            )
        } else if (state.isRecentsOnly) {
            repository.listFiles(
                directoryPath = state.currentPath,
                safUriString = null,
                sortOption = SortOption.DATE,
                sortOrder = SortOrder.DESCENDING,
                searchQuery = state.searchQuery,
                filterCategory = state.filterCategory,
                appSubFilter = state.appSubFilter,
                isGlobalSearch = false,
                isAppManagerMode = state.isAppManagerOpen
            ).filter { !it.isDirectory }
        } else {
            repository.listFiles(
                directoryPath = state.currentPath,
                safUriString = null,
                sortOption = state.sortOption,
                sortOrder = state.sortOrder,
                searchQuery = state.searchQuery,
                filterCategory = state.filterCategory,
                appSubFilter = state.appSubFilter,
                isGlobalSearch = state.isGlobalSearch,
                isAppManagerMode = state.isAppManagerOpen
            )
        }
        _uiState.update { 
            it.copy(
                currentFiles = files,
                storageVolumes = volumes,
                isLoading = false
            )
        }
    }

    fun refreshFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            fetchFilesInternal()
        }
    }

    fun navigateToDirectory(path: String) {
        viewModelScope.launch {
            val currentTabs = _uiState.value.tabs.toMutableList()
            val currentTabIdx = currentTabs.indexOfFirst { it.id == _uiState.value.currentTabId }
            val context = getApplication<Application>().applicationContext
            val tempViewsDir = File(context.cacheDir, "temp_zip_views").absolutePath

            if (currentTabIdx != -1) {
                val tab = currentTabs[currentTabIdx]
                val newHistory = tab.history.take(tab.historyIndex + 1) + path
                val updatedTab = tab.copy(
                    currentPath = path,
                    title = File(path).name.ifEmpty { "Raíz" },
                    history = newHistory,
                    historyIndex = newHistory.size - 1
                )
                currentTabs[currentTabIdx] = updatedTab

                _uiState.update {
                    it.copy(
                        tabs = currentTabs,
                        currentPath = path,
                        selectedItems = emptySet(),
                        isFavoritesOnly = false,
                        isRecentsOnly = false,
                        filterCategory = null,
                        searchQuery = "",
                        tempZipSourcePath = if (path.startsWith(tempViewsDir)) it.tempZipSourcePath else null
                    )
                }
                refreshFiles()
            }
        }
    }

    fun navigateBackInTab(): Boolean {
        if (_uiState.value.filterCategory != null || _uiState.value.isFavoritesOnly || _uiState.value.isRecentsOnly || _uiState.value.searchQuery.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    filterCategory = null,
                    isFavoritesOnly = false,
                    isRecentsOnly = false,
                    searchQuery = "",
                    selectedItems = emptySet()
                )
            }
            refreshFiles()
            return true
        }

        val currentTabs = _uiState.value.tabs.toMutableList()
        val currentTabIdx = currentTabs.indexOfFirst { it.id == _uiState.value.currentTabId }
        val context = getApplication<Application>().applicationContext
        val tempViewsDir = File(context.cacheDir, "temp_zip_views").absolutePath

        if (currentTabIdx != -1) {
            val tab = currentTabs[currentTabIdx]
            if (tab.historyIndex > 0) {
                val newIndex = tab.historyIndex - 1
                val newPath = tab.history[newIndex]
                val updatedTab = tab.copy(
                    currentPath = newPath,
                    title = File(newPath).name.ifEmpty { "Raíz" },
                    historyIndex = newIndex
                )
                currentTabs[currentTabIdx] = updatedTab
                _uiState.update {
                    it.copy(
                        tabs = currentTabs,
                        currentPath = newPath,
                        selectedItems = emptySet(),
                        tempZipSourcePath = if (newPath.startsWith(tempViewsDir)) it.tempZipSourcePath else null
                    )
                }
                refreshFiles()
                return true
            }
        }
        return false
    }

    fun addNewTab(path: String = uiState.value.currentPath) {
        val newTabId = "tab_${UUID.randomUUID().toString().take(8)}"
        val newTab = TabItem(
            id = newTabId,
            title = File(path).name.ifEmpty { "Aba" },
            currentPath = path,
            history = listOf(path),
            historyIndex = 0
        )
        _uiState.update {
            it.copy(
                tabs = it.tabs + newTab,
                currentTabId = newTabId,
                currentPath = path,
                selectedItems = emptySet()
            )
        }
        refreshFiles()
    }

    fun closeTab(tabId: String) {
        if (_uiState.value.tabs.size <= 1) return
        val remaining = _uiState.value.tabs.filterNot { it.id == tabId }
        val newActiveTab = remaining.last()
        _uiState.update {
            it.copy(
                tabs = remaining,
                currentTabId = newActiveTab.id,
                currentPath = newActiveTab.currentPath,
                selectedItems = emptySet()
            )
        }
        refreshFiles()
    }

    fun switchTab(tabId: String) {
        val targetTab = _uiState.value.tabs.find { it.id == tabId } ?: return
        _uiState.update {
            it.copy(
                currentTabId = tabId,
                currentPath = targetTab.currentPath,
                selectedItems = emptySet()
            )
        }
        refreshFiles()
    }

    fun selectStorageVolume(volume: StorageVolume) {
        _uiState.update { it.copy(selectedVolume = volume) }
        navigateToDirectory(volume.path)
    }

    fun toggleSelectItem(item: FileItem) {
        _uiState.update { state ->
            val current = state.selectedItems.toMutableSet()
            val existing = current.find { it.id == item.id || it.path == item.path }
            if (existing != null) {
                current.remove(existing)
            } else {
                current.add(item)
            }
            state.copy(selectedItems = current)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            val allFiles = state.currentFiles.toSet()
            val currentSelectedPaths = state.selectedItems.map { it.path }.toSet()
            val allFilePaths = allFiles.map { it.path }.toSet()
            
            if (currentSelectedPaths.size == allFilePaths.size && currentSelectedPaths.containsAll(allFilePaths)) {
                state.copy(selectedItems = emptySet())
            } else {
                state.copy(selectedItems = allFiles)
            }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItems = emptySet()) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val updatedHistory = if (query.isNotBlank() && query.length >= 2 && !state.searchHistory.contains(query.trim())) {
                (listOf(query.trim()) + state.searchHistory).take(10)
            } else state.searchHistory
            state.copy(searchQuery = query, searchHistory = updatedHistory)
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            refreshFiles()
        }
    }

    fun toggleGlobalSearch() {
        _uiState.update { it.copy(isGlobalSearch = !it.isGlobalSearch) }
        refreshFiles()
    }

    fun setFilterCategory(category: FileType?) {
        _uiState.update { it.copy(filterCategory = category, isAppManagerOpen = false, isFavoritesOnly = false, isRecentsOnly = false) }
        refreshFiles()
    }

    fun openAppManager() {
        _uiState.update { it.copy(isAppManagerOpen = true, filterCategory = null, isFavoritesOnly = false, isRecentsOnly = false, appSubFilter = "USER") }
        refreshFiles()
    }

    fun closeAppManager() {
        _uiState.update { it.copy(isAppManagerOpen = false) }
        refreshFiles()
    }

    fun setAppSubFilter(subFilter: String) {
        _uiState.update { it.copy(appSubFilter = subFilter) }
        refreshFiles()
    }

    fun shareFile(context: Context, item: FileItem) {
        try {
            val file = repository.resolveFile(item.path)
            if (!file.exists()) {
                Toast.makeText(context, "Arquivo não encontrado para compartilhar", Toast.LENGTH_SHORT).show()
                return
            }
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (item.fileType == FileType.APK) "application/vnd.android.package-archive" else item.mimeType.ifEmpty { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Compartilhar ${item.name}")
                putExtra(Intent.EXTRA_TEXT, "Enviando aplicativo/arquivo: ${item.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Compartilhar ${item.name} via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao compartilhar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun editFileWithThirdParty(context: Context, item: FileItem) {
        try {
            val file = repository.resolveFile(item.path)
            if (!file.exists()) {
                Toast.makeText(context, "Arquivo não encontrado para editar", Toast.LENGTH_SHORT).show()
                return
            }
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = item.mimeType.ifEmpty {
                context.contentResolver.getType(uri) ?: when (item.fileType) {
                    FileType.IMAGE -> "image/*"
                    FileType.VIDEO -> "video/*"
                    FileType.AUDIO -> "audio/*"
                    FileType.DOCUMENT -> "application/*"
                    FileType.CODE -> "text/*"
                    else -> "*/*"
                }
            }

            val editIntent = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooserIntent = Intent.createChooser(editIntent, "Editar ${item.name} com").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val file = repository.resolveFile(item.path)
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val mimeType = item.mimeType.ifEmpty { "*/*" }
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooserIntent = Intent.createChooser(viewIntent, "Editar / Abrir ${item.name} com").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooserIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Nenhum aplicativo encontrado para editar este arquivo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun installApk(context: Context, apkPath: String) {
        try {
            val file = repository.resolveFile(apkPath)
            if (!file.exists()) {
                Toast.makeText(context, "Arquivo APK não encontrado", Toast.LENGTH_SHORT).show()
                return
            }
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao iniciar instalação: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareSelectedFiles(context: Context) {
        val selected = _uiState.value.selectedItems.toList()
        if (selected.isEmpty()) return
        if (selected.size == 1) {
            shareFile(context, selected.first())
            return
        }
        try {
            val uris = ArrayList<Uri>()
            for (item in selected) {
                val file = repository.resolveFile(item.path)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    uris.add(uri)
                }
            }
            if (uris.isEmpty()) {
                Toast.makeText(context, "Nenhum arquivo encontrado para compartilhar", Toast.LENGTH_SHORT).show()
                return
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Compartilhar ${uris.size} arquivos via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao compartilhar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchApp(context: Context, packageName: String) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                Toast.makeText(context, "Aplicativo do sistema sem tela inicial pública", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao abrir: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppSettings(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao abrir configurações", Toast.LENGTH_SHORT).show()
        }
    }

    fun selectFavorites() {
        _uiState.update { it.copy(isFavoritesOnly = true, isRecentsOnly = false, filterCategory = null) }
        refreshFiles()
    }

    fun selectRecents() {
        _uiState.update { it.copy(isRecentsOnly = true, isFavoritesOnly = false, filterCategory = null) }
        refreshFiles()
    }

    fun selectFilesTab() {
        _uiState.update { it.copy(isFavoritesOnly = false, isRecentsOnly = false, filterCategory = null) }
        refreshFiles()
    }

    fun setSortOption(sortOption: SortOption) {
        _uiState.update { state ->
            val newOrder = if (state.sortOption == sortOption) {
                if (state.sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
            } else {
                SortOrder.ASCENDING
            }
            state.copy(sortOption = sortOption, sortOrder = newOrder)
        }
        refreshFiles()
    }

    fun toggleViewMode() {
        _uiState.update {
            it.copy(viewMode = if (it.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
        }
    }

    fun setAccentOption(accent: AccentColorOption) {
        prefs.edit().putString("accent_color_option", accent.name).apply()
        _uiState.update { it.copy(accentOption = accent) }
    }

    fun setCustomAccentColor(hexValue: Long) {
        prefs.edit()
            .putLong("custom_accent_color_hex", hexValue)
            .putString("accent_color_option", AccentColorOption.PERSONALIZADO.name)
            .apply()
        _uiState.update {
            it.copy(
                accentOption = AccentColorOption.PERSONALIZADO,
                customAccentColorHex = hexValue
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setDeletePermanently(enabled: Boolean) {
        prefs.edit().putBoolean("delete_permanently", enabled).apply()
        _uiState.update { it.copy(deletePermanently = enabled) }
    }

    fun setConfirmDelete(enabled: Boolean) {
        prefs.edit().putBoolean("confirm_delete", enabled).apply()
        _uiState.update { it.copy(confirmDelete = enabled) }
    }

    fun openCloudManager() {
        _uiState.update { it.copy(isCloudManagerOpen = true) }
    }

    fun selectVolume(volume: StorageVolume) {
        val currentTabs = _uiState.value.tabs.toMutableList()
        val currentTabIdx = currentTabs.indexOfFirst { it.id == _uiState.value.currentTabId }
        val targetPath = volume.path
        if (currentTabIdx != -1) {
            val tab = currentTabs[currentTabIdx]
            val newHistory = tab.history.take(tab.historyIndex + 1) + targetPath
            val updatedTab = tab.copy(
                currentPath = targetPath,
                title = volume.name,
                history = newHistory,
                historyIndex = newHistory.size - 1
            )
            currentTabs[currentTabIdx] = updatedTab
        }
        _uiState.update {
            it.copy(
                tabs = currentTabs,
                selectedVolume = volume,
                currentPath = targetPath,
                filterCategory = null,
                isFavoritesOnly = false,
                isRecentsOnly = false,
                searchQuery = "",
                selectedItems = emptySet(),
                isAppManagerOpen = false,
                isTrashBinOpen = false
            )
        }
        refreshFiles()
    }

    fun closeCloudManager() {
        _uiState.update { it.copy(isCloudManagerOpen = false) }
    }

    fun startOAuthFlow(provider: com.example.ui.components.CloudProvider) {
        _uiState.update { it.copy(oauthConnectProvider = provider) }
    }

    fun closeOAuthFlow() {
        _uiState.update { it.copy(oauthConnectProvider = null) }
    }

    fun completeOAuthConnect(
        provider: com.example.ui.components.CloudProvider,
        accountEmail: String,
        serverUrl: String = provider.defaultServerUrl,
        passwordOrToken: String = ""
    ) {
        val prefConnKey = "cloud_connected_${provider.id}"
        val prefEmailKey = "cloud_email_${provider.id}"
        val prefUrlKey = "cloud_url_${provider.id}"
        val prefPassKey = "cloud_pass_${provider.id}"
        prefs.edit()
            .putBoolean(prefConnKey, true)
            .putString(prefEmailKey, accountEmail)
            .putString(prefUrlKey, serverUrl)
            .putString(prefPassKey, passwordOrToken)
            .apply()

        val targetPath = provider.path
        val currentTabs = _uiState.value.tabs.toMutableList()
        val currentTabIdx = currentTabs.indexOfFirst { it.id == _uiState.value.currentTabId }
        if (currentTabIdx != -1) {
            val tab = currentTabs[currentTabIdx]
            val newHistory = tab.history.take(tab.historyIndex + 1) + targetPath
            val updatedTab = tab.copy(
                currentPath = targetPath,
                title = provider.displayName,
                history = newHistory,
                historyIndex = newHistory.size - 1
            )
            currentTabs[currentTabIdx] = updatedTab
        }

        _uiState.update {
            val baseState = when (provider) {
                com.example.ui.components.CloudProvider.MEGA -> it.copy(isMegaConnected = true, megaAccountEmail = accountEmail)
                com.example.ui.components.CloudProvider.WEBDAV -> it.copy(isWebDavConnected = true, webdavAccountEmail = accountEmail, webdavServerUrl = serverUrl)
                com.example.ui.components.CloudProvider.GOOGLE_DRIVE -> it.copy(isDriveConnected = true, driveAccountEmail = accountEmail)
                com.example.ui.components.CloudProvider.MEDIAFIRE -> it.copy(isMediafireConnected = true, mediafireAccountEmail = accountEmail)
                com.example.ui.components.CloudProvider.ONEDRIVE -> it.copy(isOnedriveConnected = true, onedriveAccountEmail = accountEmail)
                com.example.ui.components.CloudProvider.DROPBOX -> it.copy(isDropboxConnected = true, dropboxAccountEmail = accountEmail)
            }
            baseState.copy(
                tabs = currentTabs,
                currentPath = targetPath,
                filterCategory = null,
                isFavoritesOnly = false,
                isRecentsOnly = false,
                searchQuery = "",
                selectedItems = emptySet(),
                oauthConnectProvider = null,
                isCloudManagerOpen = false,
                isTrashBinOpen = false,
                isAppManagerOpen = false,
                snackbarMessage = "Conta ${provider.displayName} ($accountEmail) conectada com sucesso!"
            )
        }
        refreshVolumesAndCurrentFiles()
    }

    fun quickConnectCloudProvider(provider: com.example.ui.components.CloudProvider) {
        completeOAuthConnect(
            provider = provider,
            accountEmail = provider.defaultEmail,
            serverUrl = provider.defaultServerUrl,
            passwordOrToken = "direct_cloud_session"
        )
    }

    fun connectAllCloudProviders() {
        val providers = listOf(
            com.example.ui.components.CloudProvider.MEGA,
            com.example.ui.components.CloudProvider.GOOGLE_DRIVE,
            com.example.ui.components.CloudProvider.ONEDRIVE,
            com.example.ui.components.CloudProvider.DROPBOX,
            com.example.ui.components.CloudProvider.MEDIAFIRE,
            com.example.ui.components.CloudProvider.WEBDAV
        )
        providers.forEach { provider ->
            prefs.edit()
                .putBoolean("cloud_connected_${provider.id}", true)
                .putString("cloud_email_${provider.id}", provider.defaultEmail)
                .putString("cloud_url_${provider.id}", provider.defaultServerUrl)
                .putString("cloud_pass_${provider.id}", "direct_cloud_session")
                .apply()
        }
        _uiState.update {
            it.copy(
                isMegaConnected = true,
                megaAccountEmail = com.example.ui.components.CloudProvider.MEGA.defaultEmail,
                isDriveConnected = true,
                driveAccountEmail = com.example.ui.components.CloudProvider.GOOGLE_DRIVE.defaultEmail,
                isOnedriveConnected = true,
                onedriveAccountEmail = com.example.ui.components.CloudProvider.ONEDRIVE.defaultEmail,
                isDropboxConnected = true,
                dropboxAccountEmail = com.example.ui.components.CloudProvider.DROPBOX.defaultEmail,
                isMediafireConnected = true,
                mediafireAccountEmail = com.example.ui.components.CloudProvider.MEDIAFIRE.defaultEmail,
                isWebDavConnected = true,
                webdavAccountEmail = com.example.ui.components.CloudProvider.WEBDAV.defaultEmail,
                snackbarMessage = "Todos os provedores de nuvem foram conectados com sucesso!"
            )
        }
        refreshVolumesAndCurrentFiles()
    }

    fun disconnectCloudProvider(provider: com.example.ui.components.CloudProvider) {
        val prefConnKey = "cloud_connected_${provider.id}"
        prefs.edit().putBoolean(prefConnKey, false).apply()

        _uiState.update {
            when (provider) {
                com.example.ui.components.CloudProvider.MEGA -> it.copy(isMegaConnected = false)
                com.example.ui.components.CloudProvider.WEBDAV -> it.copy(isWebDavConnected = false)
                com.example.ui.components.CloudProvider.GOOGLE_DRIVE -> it.copy(isDriveConnected = false)
                com.example.ui.components.CloudProvider.MEDIAFIRE -> it.copy(isMediafireConnected = false)
                com.example.ui.components.CloudProvider.ONEDRIVE -> it.copy(isOnedriveConnected = false)
                com.example.ui.components.CloudProvider.DROPBOX -> it.copy(isDropboxConnected = false)
            }.copy(
                snackbarMessage = "Conta ${provider.displayName} desvinculada."
            )
        }
        refreshVolumesAndCurrentFiles()
    }

    fun toggleWebdavConnected(connected: Boolean) {
        if (connected) {
            startOAuthFlow(com.example.ui.components.CloudProvider.WEBDAV)
        } else {
            disconnectCloudProvider(com.example.ui.components.CloudProvider.WEBDAV)
        }
    }

    fun toggleMegaConnected(connected: Boolean) {
        if (connected) {
            startOAuthFlow(com.example.ui.components.CloudProvider.MEGA)
        } else {
            disconnectCloudProvider(com.example.ui.components.CloudProvider.MEGA)
        }
    }

    fun toggleDriveConnected(connected: Boolean) {
        if (connected) {
            startOAuthFlow(com.example.ui.components.CloudProvider.GOOGLE_DRIVE)
        } else {
            disconnectCloudProvider(com.example.ui.components.CloudProvider.GOOGLE_DRIVE)
        }
    }

    fun toggleMediafireConnected(connected: Boolean) {
        if (connected) {
            startOAuthFlow(com.example.ui.components.CloudProvider.MEDIAFIRE)
        } else {
            disconnectCloudProvider(com.example.ui.components.CloudProvider.MEDIAFIRE)
        }
    }

    fun toggleOnedriveConnected(connected: Boolean) {
        if (connected) {
            startOAuthFlow(com.example.ui.components.CloudProvider.ONEDRIVE)
        } else {
            disconnectCloudProvider(com.example.ui.components.CloudProvider.ONEDRIVE)
        }
    }

    fun toggleDropboxConnected(connected: Boolean) {
        if (connected) {
            startOAuthFlow(com.example.ui.components.CloudProvider.DROPBOX)
        } else {
            disconnectCloudProvider(com.example.ui.components.CloudProvider.DROPBOX)
        }
    }

    private fun refreshVolumesAndCurrentFiles() {
        viewModelScope.launch {
            val volumes = repository.getStorageVolumes()
            val currentPath = _uiState.value.currentPath
            val isCurrentPathCloud = currentPath.startsWith("/cloud/")
            
            var newPath = currentPath
            var newVolume = _uiState.value.selectedVolume
            
            if (isCurrentPathCloud) {
                val stillExists = volumes.any { currentPath.startsWith(it.path) }
                if (!stillExists) {
                    val primary = volumes.firstOrNull { it.typeKey == "INTERNAL" } ?: volumes.firstOrNull()
                    newPath = primary?.path ?: "/storage/emulated/0"
                    newVolume = primary
                }
            }
            
            _uiState.update { 
                it.copy(
                    storageVolumes = volumes,
                    currentPath = newPath,
                    selectedVolume = newVolume
                ) 
            }
            refreshFiles()
        }
    }

    // -------------------------------------------------------------
    // FILE OPERATIONS
    // -------------------------------------------------------------
    fun createFolder(name: String) {
        viewModelScope.launch {
            val success = repository.createFolder(_uiState.value.currentPath, name)
            if (success) {
                showToast("Pasta \"$name\" criada com sucesso!")
                refreshFiles()
            } else {
                showToast("Não foi possível criar a pasta.")
            }
        }
    }

    fun renameItem(item: FileItem, newName: String) {
        viewModelScope.launch {
            val success = repository.renameFile(item.path, newName)
            if (success) {
                showToast("Item renomeado para \"$newName\".")
                refreshFiles()
            } else {
                showToast("Falha ao renomear o item.")
            }
        }
    }

    fun copyToClipboard() {
        val selected = _uiState.value.selectedItems
        if (selected.isEmpty()) return
        _uiState.update {
            it.copy(
                clipboardItems = selected,
                clipboardMode = ClipboardMode.COPY,
                selectedItems = emptySet()
            )
        }
        showToast("${selected.size} item(ns) copiado(s). Navegue até o destino e clique no botão Colar.")
    }

    fun cutToClipboard() {
        val selected = _uiState.value.selectedItems
        if (selected.isEmpty()) return
        _uiState.update {
            it.copy(
                clipboardItems = selected,
                clipboardMode = ClipboardMode.CUT,
                selectedItems = emptySet()
            )
        }
        showToast("${selected.size} item(ns) recortado(s). Navegue até o destino e clique no botão Colar.")
    }

    fun pasteClipboard() {
        val items = _uiState.value.clipboardItems
        val mode = _uiState.value.clipboardMode ?: return
        val targetPath = _uiState.value.currentPath

        if (items.isEmpty() || targetPath.isEmpty()) return

        viewModelScope.launch {
            val statusMsg = if (mode == ClipboardMode.COPY) "Copiando arquivos..." else "Movendo arquivos..."
            _uiState.update { it.copy(operationStatusText = statusMsg) }

            var count = 0
            for (item in items) {
                val customName = if (item.fileType == FileType.APK) {
                    if (item.name.endsWith(".apk", ignoreCase = true)) item.name else "${item.name}.apk"
                } else null

                if (mode == ClipboardMode.COPY) {
                    repository.copyFile(item.path, targetPath, customFileName = customName) { progress ->
                        _uiState.update { it.copy(operationProgress = progress) }
                    }
                } else {
                    repository.moveFile(item.path, targetPath, customFileName = customName) { progress ->
                        _uiState.update { it.copy(operationProgress = progress) }
                    }
                }
                count++
            }

            _uiState.update {
                it.copy(
                    operationStatusText = null,
                    operationProgress = null,
                    clipboardItems = emptySet(),
                    clipboardMode = null
                )
            }

            val actionMsg = if (mode == ClipboardMode.COPY) "copiado(s)" else "movido(s)"
            showToast("$count item(ns) $actionMsg com sucesso!")
            refreshFiles()
        }
    }

    fun clearClipboard() {
        _uiState.update {
            it.copy(
                clipboardItems = emptySet(),
                clipboardMode = null
            )
        }
        showToast("Área de transferência limpa.")
    }

    fun copySelected(targetPath: String) {
        viewModelScope.launch {
            val selected = _uiState.value.selectedItems
            _uiState.update { it.copy(operationStatusText = "Copiando arquivos...") }

            var count = 0
            for (item in selected) {
                repository.copyFile(item.path, targetPath) { progress ->
                    _uiState.update { it.copy(operationProgress = progress) }
                }
                count++
            }
            _uiState.update { it.copy(operationStatusText = null, operationProgress = null, selectedItems = emptySet()) }
            showToast("$count item(ns) copiado(s) com sucesso!")
            refreshFiles()
        }
    }

    fun moveSelected(targetPath: String) {
        viewModelScope.launch {
            val selected = _uiState.value.selectedItems
            _uiState.update { it.copy(operationStatusText = "Movendo arquivos...") }

            var count = 0
            for (item in selected) {
                repository.moveFile(item.path, targetPath) { progress ->
                    _uiState.update { it.copy(operationProgress = progress) }
                }
                count++
            }
            _uiState.update { it.copy(operationStatusText = null, operationProgress = null, selectedItems = emptySet()) }
            showToast("$count item(ns) movido(s) com sucesso!")
            refreshFiles()
        }
    }

    fun deleteFilesDirectly(files: List<FileItem>, permanently: Boolean) {
        viewModelScope.launch {
            if (files.isEmpty()) return@launch
            val total = files.size
            var count = 0
            val statusMsg = if (permanently) "Excluindo arquivos..." else "Movendo arquivos para a lixeira..."
            _uiState.update { it.copy(operationStatusText = statusMsg, operationProgress = 0f) }
            if (permanently) {
                for (item in files) {
                    if (repository.deletePermanently(item)) count++
                    _uiState.update { it.copy(operationProgress = count.toFloat() / total) }
                }
                showToast("$count arquivo(s) excluído(s) com sucesso")
            } else {
                for (item in files) {
                    if (repository.moveToTrash(item)) count++
                    _uiState.update { it.copy(operationProgress = count.toFloat() / total) }
                }
                showToast("$count arquivo(s) removido(s) para lixeira")
            }
            _uiState.update { it.copy(operationStatusText = null, operationProgress = null) }
            refreshFiles()
            // Refresh storage stats too
            if (_uiState.value.isStorageDashboardOpen) refreshStorageDashboard()
        }
    }

    fun deleteSelected(permanently: Boolean) {
        viewModelScope.launch {
            val selected = _uiState.value.selectedItems
            if (selected.isEmpty()) return@launch
            
            val total = selected.size
            var count = 0
            val statusMsg = if (permanently) "Excluindo arquivos..." else "Movendo arquivos para a lixeira..."
            
            _uiState.update { 
                it.copy(
                    operationStatusText = statusMsg,
                    operationProgress = 0f
                ) 
            }
            
            if (permanently) {
                for (item in selected) {
                    if (repository.deletePermanently(item)) {
                        count++
                    }
                    val progress = count.toFloat() / total
                    _uiState.update { it.copy(operationProgress = progress) }
                }
                _uiState.update { 
                    it.copy(
                        selectedItems = emptySet(),
                        operationStatusText = null,
                        operationProgress = null
                    ) 
                }
                showToast("arquivo excluído com sucesso")
            } else {
                for (item in selected) {
                    if (repository.moveToTrash(item)) {
                        count++
                    }
                    val progress = count.toFloat() / total
                    _uiState.update { it.copy(operationProgress = progress) }
                }
                _uiState.update { 
                    it.copy(
                        selectedItems = emptySet(),
                        operationStatusText = null,
                        operationProgress = null
                    ) 
                }
                showToast("arquivo removido para lixeira")
            }
            refreshFiles()
        }
    }

    fun deleteSelectedToTrash() {
        deleteSelected(_uiState.value.deletePermanently)
    }

    fun toggleFavorite(item: FileItem) {
        val newIsFavorite = !item.isFavorite
        _uiState.update { state ->
            val updatedFiles = state.currentFiles.map {
                if (it.path == item.path || (item.id.isNotEmpty() && it.id == item.id)) {
                    it.copy(isFavorite = newIsFavorite)
                } else {
                    it
                }
            }.let { list ->
                if (state.isFavoritesOnly && !newIsFavorite) {
                    list.filter { it.path != item.path && it.id != item.id }
                } else {
                    list
                }
            }
            state.copy(currentFiles = updatedFiles)
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(item)
        }
    }

    // -------------------------------------------------------------
    // MODALS & VIEWERS (APK, ZIP, TEXT, MEDIA, DASHBOARD, TRASH, SETTINGS)
    // -------------------------------------------------------------
    private var currentOperationJob: kotlinx.coroutines.Job? = null

    fun cancelCurrentOperation() {
        currentOperationJob?.cancel()
        currentOperationJob = null
        _uiState.update { 
            it.copy(
                operationStatusText = null,
                operationProgress = null
            )
        }
        showToast("Operação cancelada")
    }

    private suspend fun checkAndSimulateLargeFileLoading(item: FileItem) {
        // Fast immediate loading without artificial delays
    }

    fun openGenericFile(item: FileItem) {
        if (item.isDirectory) {
            navigateToDirectory(item.path)
            return
        }
        currentOperationJob?.cancel()
        currentOperationJob = viewModelScope.launch {
            checkAndSimulateLargeFileLoading(item)
            when (item.fileType) {
                FileType.APK -> {
                    val info = repository.inspectApk(item.path)
                    _uiState.update { it.copy(activeApkInfo = info) }
                }
                FileType.ARCHIVE -> {
                    val entries = repository.listZipContents(item.path)
                    _uiState.update { it.copy(activeZipFile = item, activeZipEntries = entries) }
                }
                FileType.CODE, FileType.DOCUMENT -> {
                    val content = repository.readTextFile(item.path)
                    _uiState.update { it.copy(activeEditorFile = item, editorContent = content) }
                }
                FileType.IMAGE, FileType.VIDEO, FileType.AUDIO -> {
                    _uiState.update { it.copy(activeMediaItem = item) }
                }
                else -> {
                    showToast("Abrindo ${item.name}...")
                }
            }
        }
    }

    fun inspectApk(item: FileItem) {
        viewModelScope.launch {
            checkAndSimulateLargeFileLoading(item)
            var info = repository.inspectApk(item.path)
            if (info != null) {
                val category = item.appCategory ?: if (!item.packageName.isNullOrEmpty()) "USER" else null
                info = info.copy(
                    appCategory = category,
                    isInstalledApp = !category.isNullOrEmpty() || !item.packageName.isNullOrEmpty()
                )
            }
            _uiState.update { it.copy(activeApkInfo = info) }
        }
    }

    fun uninstallUserApp(context: Context, packageName: String) {
        closeApkInspector()
        try {
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:$packageName")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Não foi possível abrir o desinstalador do pacote", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeSystemApp(context: Context, packageName: String) {
        closeApkInspector()
        val criticalPackages = setOf(
            "android", "com.android.systemui", "com.android.settings", "com.google.android.gms",
            "com.android.phone", "com.android.providers.media", "com.android.launcher",
            "com.google.android.packageinstaller", "com.android.packageinstaller",
            "com.android.providers.downloads", "com.android.providers.telephony"
        )
        if (criticalPackages.contains(packageName) || packageName.startsWith("com.android.internal")) {
            Toast.makeText(context, "Operação impedida: aplicativo crítico do sistema Android", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:$packageName")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "Abra as configurações para desinstalar atualizações ou desativar o app", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Toast.makeText(context, "Não foi possível desinstalar ou desativar este aplicativo de sistema", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun closeApkInspector() {
        _uiState.update { it.copy(activeApkInfo = null) }
    }

    fun openApkPackage(apkInfo: ApkInfo) {
        viewModelScope.launch {
            closeApkInspector()
            val file = File(apkInfo.apkFilePath)
            if (file.exists() && file.canRead()) {
                val fileItem = FileItem(
                    id = file.absolutePath,
                    name = file.name.ifEmpty { "${apkInfo.appName}.apk" },
                    path = file.absolutePath,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    isDirectory = false,
                    fileType = FileType.APK,
                    extension = "apk",
                    mimeType = "application/vnd.android.package-archive",
                    packageName = apkInfo.packageName
                )
                openAndExtractZipArchive(fileItem)
            } else {
                showToast("Não foi possível acessar os arquivos do pacote APK.")
            }
        }
    }

    fun openZipArchive(item: FileItem) {
        viewModelScope.launch {
            checkAndSimulateLargeFileLoading(item)
            val entries = repository.listZipContents(item.path)
            _uiState.update { it.copy(activeZipFile = item, activeZipEntries = entries) }
        }
    }

    fun closeZipArchive() {
        _uiState.update { it.copy(activeZipFile = null, activeZipEntries = emptyList()) }
    }

    fun extractZipArchive(item: FileItem, targetDirectory: String = _uiState.value.currentPath) {
        viewModelScope.launch {
            val zipFile = File(item.path)
            val parentFolder = zipFile.parentFile ?: File(targetDirectory)
            val folderName = zipFile.nameWithoutExtension.ifEmpty { "Extraido" }
            val destDir = File(parentFolder, folderName)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            _uiState.update { it.copy(operationStatusText = "Extraindo ${item.name}...") }
            val success = repository.extractZip(item.path, destDir.absolutePath) { progress, name ->
                _uiState.update { it.copy(operationProgress = progress) }
            }
            _uiState.update { 
                it.copy(
                    operationStatusText = null, 
                    operationProgress = null,
                    activeZipFile = null,
                    activeZipEntries = emptyList()
                ) 
            }
            if (success) {
                showToast("Extraído com sucesso na pasta '${folderName}'!")
                refreshFiles()
            } else {
                showToast("Erro ao extrair o arquivo.")
            }
        }
    }

    fun openAndExtractZipArchive(item: FileItem, targetDirectory: String = _uiState.value.currentPath) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val zipFile = File(item.path)
            
            // Clean up old temp zip views to save space
            try {
                val baseTempDir = File(context.cacheDir, "temp_zip_views")
                if (baseTempDir.exists()) {
                    baseTempDir.deleteRecursively()
                }
            } catch (_: Exception) {}

            val tempDir = File(context.cacheDir, "temp_zip_views/${zipFile.nameWithoutExtension}")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }

            _uiState.update { it.copy(operationStatusText = "Abrindo ${item.name}...") }
            val success = repository.extractZip(item.path, tempDir.absolutePath) { progress, name ->
                _uiState.update { it.copy(operationProgress = progress) }
            }
            _uiState.update { 
                it.copy(
                    operationStatusText = null, 
                    operationProgress = null,
                    activeZipFile = null,
                    activeZipEntries = emptyList(),
                    tempZipSourcePath = item.path
                ) 
            }
            if (success) {
                navigateToDirectory(tempDir.absolutePath)
            } else {
                showToast("Erro ao abrir o arquivo ZIP.")
            }
        }
    }

    fun extractIndividualFileFromTemp(item: FileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val originalZipPath = _uiState.value.tempZipSourcePath ?: return@launch
            val originalZipFile = File(originalZipPath)
            val originalParentDir = originalZipFile.parentFile ?: return@launch

            val relativePath = getRelativePathInTempZip(item.path)
            val destFile = File(originalParentDir, relativePath)

            try {
                _uiState.update { 
                    it.copy(
                        operationStatusText = "Extraindo ${item.name}...",
                        operationProgress = 0f
                    ) 
                }
                val srcFile = File(item.path)
                if (srcFile.exists()) {
                    if (srcFile.isDirectory) {
                        _uiState.update { it.copy(operationProgress = 0.3f) }
                        srcFile.copyRecursively(destFile, overwrite = true)
                        _uiState.update { it.copy(operationProgress = 1.0f) }
                    } else {
                        destFile.parentFile?.mkdirs()
                        _uiState.update { it.copy(operationProgress = 0.5f) }
                        srcFile.copyTo(destFile, overwrite = true)
                        _uiState.update { it.copy(operationProgress = 1.0f) }
                    }
                    kotlinx.coroutines.delay(150)
                    showToast("Extraído com sucesso para ${originalParentDir.name}!")
                } else {
                    showToast("Erro: arquivo fonte não encontrado.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showToast("Erro ao extrair o arquivo: ${e.message}")
            } finally {
                _uiState.update { 
                    it.copy(
                        operationStatusText = null,
                        operationProgress = null
                    ) 
                }
                refreshFiles()
            }
        }
    }

    fun exitTempZipView() {
        viewModelScope.launch {
            val originalZipPath = _uiState.value.tempZipSourcePath
            _uiState.update { it.copy(tempZipSourcePath = null) }
            
            // Clean up temp dir
            val context = getApplication<Application>().applicationContext
            try {
                val tempViewsDir = File(context.cacheDir, "temp_zip_views")
                if (tempViewsDir.exists()) {
                    tempViewsDir.deleteRecursively()
                }
            } catch (_: Exception) {}

            if (originalZipPath != null) {
                val origFile = File(originalZipPath)
                if (origFile.parentFile != null) {
                    navigateToDirectory(origFile.parentFile!!.absolutePath)
                } else {
                    refreshFiles()
                }
            } else {
                refreshFiles()
            }
        }
    }

    private fun getRelativePathInTempZip(filePath: String): String {
        val keyword = "/temp_zip_views/"
        val index = filePath.indexOf(keyword)
        if (index == -1) return File(filePath).name
        val sub = filePath.substring(index + keyword.length)
        val relative = sub.substringAfter('/', "")
        return relative.ifEmpty { File(filePath).name }
    }

    fun compressSelectedToZip(zipName: String) {
        viewModelScope.launch {
            val selected = _uiState.value.selectedItems.map { it.path }
            val targetZip = File(_uiState.value.currentPath, if (zipName.endsWith(".zip")) zipName else "$zipName.zip").absolutePath
            _uiState.update { it.copy(operationStatusText = "Criando arquivo ZIP...") }

            val success = repository.createZip(selected, targetZip) { progress, file ->
                _uiState.update { it.copy(operationProgress = progress) }
            }
            _uiState.update { it.copy(operationStatusText = null, operationProgress = null, selectedItems = emptySet()) }
            if (success) {
                showToast("Arquivo ZIP criado com sucesso!")
                refreshFiles()
            } else {
                showToast("Erro ao criar o arquivo ZIP.")
            }
        }
    }

    fun openCodeEditor(item: FileItem) {
        viewModelScope.launch {
            checkAndSimulateLargeFileLoading(item)
            val content = repository.readTextFile(item.path)
            _uiState.update { it.copy(activeEditorFile = item, editorContent = content) }
        }
    }

    fun saveCodeEditor(content: String) {
        viewModelScope.launch {
            val item = _uiState.value.activeEditorFile ?: return@launch
            val success = repository.saveTextFile(item.path, content)
            if (success) {
                showToast("Arquivo salvo com sucesso!")
                _uiState.update { it.copy(editorContent = content) }
                refreshFiles()
            } else {
                showToast("Falha ao salvar o arquivo.")
            }
        }
    }

    fun closeCodeEditor() {
        _uiState.update { it.copy(activeEditorFile = null, editorContent = "") }
    }

    fun openMediaViewer(item: FileItem) {
        viewModelScope.launch {
            checkAndSimulateLargeFileLoading(item)
            _uiState.update { it.copy(activeMediaItem = item) }
        }
    }

    fun nextMediaItem() {
        val currentItem = _uiState.value.activeMediaItem ?: return
        val currentFiles = _uiState.value.currentFiles
        val mediaFiles = currentFiles.filter { it.fileType == FileType.IMAGE || it.fileType == FileType.VIDEO || it.fileType == FileType.AUDIO }
        val currentIndex = mediaFiles.indexOfFirst { it.path == currentItem.path }
        if (currentIndex != -1 && currentIndex < mediaFiles.size - 1) {
            _uiState.update { it.copy(activeMediaItem = mediaFiles[currentIndex + 1]) }
        } else if (mediaFiles.isNotEmpty()) {
            // Loop back to start
            _uiState.update { it.copy(activeMediaItem = mediaFiles[0]) }
        }
    }

    fun previousMediaItem() {
        val currentItem = _uiState.value.activeMediaItem ?: return
        val currentFiles = _uiState.value.currentFiles
        val mediaFiles = currentFiles.filter { it.fileType == FileType.IMAGE || it.fileType == FileType.VIDEO || it.fileType == FileType.AUDIO }
        val currentIndex = mediaFiles.indexOfFirst { it.path == currentItem.path }
        if (currentIndex > 0) {
            _uiState.update { it.copy(activeMediaItem = mediaFiles[currentIndex - 1]) }
        } else if (mediaFiles.isNotEmpty()) {
            // Loop to end
            _uiState.update { it.copy(activeMediaItem = mediaFiles.last()) }
        }
    }

    fun closeMediaViewer() {
        _uiState.update { it.copy(activeMediaItem = null) }
    }

    fun refreshStorageDashboard() {
        viewModelScope.launch(Dispatchers.IO) {
            val targetPath = _uiState.value.selectedVolume?.path ?: _uiState.value.currentPath
            val stats = repository.analyzeStorage(targetPath, forceRefresh = true)
            val large = repository.findLargeFiles(targetPath)
            val dups = repository.findDuplicateFiles(targetPath)
            _uiState.update {
                it.copy(
                    storageCategoryStats = stats,
                    largeFiles = large,
                    duplicateGroups = dups
                )
            }
        }
    }

    fun selectDashboardVolume(volume: StorageVolume) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(selectedVolume = volume, isLoading = true) }
            val stats = repository.analyzeStorage(volume.path)
            val large = repository.findLargeFiles(volume.path)
            val dups = repository.findDuplicateFiles(volume.path)
            _uiState.update {
                it.copy(
                    storageCategoryStats = stats,
                    largeFiles = large,
                    duplicateGroups = dups,
                    isLoading = false
                )
            }
        }
    }

    fun openStorageDashboard() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isStorageDashboardOpen = true, isLoading = true) }
            val targetPath = _uiState.value.selectedVolume?.path ?: _uiState.value.currentPath
            val stats = repository.analyzeStorage(targetPath)
            val large = repository.findLargeFiles(targetPath)
            val dups = repository.findDuplicateFiles(targetPath)
            _uiState.update {
                it.copy(
                    storageCategoryStats = stats,
                    largeFiles = large,
                    duplicateGroups = dups,
                    isLoading = false
                )
            }
        }
    }

    fun closeStorageDashboard() {
        _uiState.update { it.copy(isStorageDashboardOpen = false) }
    }

    suspend fun getCategoryDetails(fileType: FileType): CategoryDetailInfo {
        val rootPath = _uiState.value.selectedVolume?.path ?: _uiState.value.currentPath
        return repository.getCategoryDetails(rootPath, fileType)
    }

    fun navigateToDirectoryAndCloseDashboard(path: String) {
        closeStorageDashboard()
        navigateToDirectory(path)
    }

    fun openTrashBin() {
        _uiState.update { it.copy(isTrashBinOpen = true) }
    }

    fun closeTrashBin() {
        _uiState.update { it.copy(isTrashBinOpen = false) }
    }

    fun restoreTrashItem(trashEntity: TrashEntity) {
        viewModelScope.launch {
            val success = repository.restoreFromTrash(trashEntity)
            if (success) {
                showToast("Item restaurado!")
                refreshFiles()
            } else {
                showToast("Falha ao restaurar o item.")
            }
        }
    }

    fun restoreSelectedTrashItems(items: Set<TrashEntity>) {
        viewModelScope.launch {
            if (items.isEmpty()) return@launch
            val total = items.size
            var count = 0
            _uiState.update { 
                it.copy(
                    operationStatusText = "Restaurando itens da lixeira...",
                    operationProgress = 0f
                ) 
            }
            for (item in items) {
                if (repository.restoreFromTrash(item)) {
                    count++
                }
                val progress = count.toFloat() / total
                _uiState.update { it.copy(operationProgress = progress) }
            }
            _uiState.update { 
                it.copy(
                    operationStatusText = null,
                    operationProgress = null
                ) 
            }
            showToast("$count item(ns) restaurado(s) com sucesso!")
            refreshFiles()
        }
    }

    fun deleteTrashPermanently(trashEntity: TrashEntity) {
        viewModelScope.launch {
            repository.permanentlyDeleteTrash(trashEntity)
            showToast("Item excluído permanentemente.")
        }
    }

    fun deleteSelectedTrashPermanently(items: Set<TrashEntity>) {
        viewModelScope.launch {
            if (items.isEmpty()) return@launch
            val total = items.size
            var count = 0
            _uiState.update { 
                it.copy(
                    operationStatusText = "Excluindo itens permanentemente...",
                    operationProgress = 0f
                ) 
            }
            for (item in items) {
                if (repository.permanentlyDeleteTrash(item)) {
                    count++
                }
                val progress = count.toFloat() / total
                _uiState.update { it.copy(operationProgress = progress) }
            }
            _uiState.update { 
                it.copy(
                    operationStatusText = null,
                    operationProgress = null
                ) 
            }
            showToast("$count item(ns) excluído(s) permanentemente.")
        }
    }

    fun emptyTrashBin() {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    operationStatusText = "Esvaziando lixeira...",
                    operationProgress = 0f
                ) 
            }
            
            // Query current items to get count or let it simulate quickly
            _uiState.update { it.copy(operationProgress = 0.5f) }
            repository.emptyTrashBin()
            _uiState.update { it.copy(operationProgress = 1.0f) }
            
            _uiState.update { 
                it.copy(
                    operationStatusText = null,
                    operationProgress = null
                ) 
            }
            showToast("Lixeira esvaziada.")
        }
    }

    fun toggleShowThumbnails(enabled: Boolean) {
        prefs.edit().putBoolean("show_thumbnails", enabled).apply()
        _uiState.update { it.copy(showThumbnails = enabled) }
    }

    fun openSettings() {
        _uiState.update { it.copy(isSettingsOpen = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    fun openWelcomeOnboarding() {
        _uiState.update { it.copy(isWelcomeOnboardingOpen = true) }
    }

    fun closeWelcomeOnboarding() {
        prefs.edit().putBoolean("welcome_onboarding_shown", true).apply()
        _uiState.update { it.copy(isWelcomeOnboardingOpen = false) }
        refreshFiles()
    }

    fun checkRootStatus(requestPrompt: Boolean = false) {
        viewModelScope.launch {
            if (requestPrompt) {
                com.example.util.RootHelper.requestRootPermission()
            }
            val rootStatus = com.example.util.RootHelper.getRootStatus(forceRefresh = true)
            val volumes = repository.getStorageVolumes()
            _uiState.update {
                it.copy(
                    isRootAvailable = rootStatus.isAvailable,
                    isRootGranted = rootStatus.isGranted,
                    rootStatusDetails = rootStatus.details,
                    storageVolumes = volumes,
                    snackbarMessage = if (requestPrompt) {
                        if (rootStatus.isGranted) "Acesso Root (Superusuário) ativo e concedido!" else if (rootStatus.isAvailable) "Superusuário detectado mas permissão pendente." else "Nenhum binário root (su) encontrado."
                    } else it.snackbarMessage
                )
            }
        }
    }

    fun remountSystemRw() {
        viewModelScope.launch {
            val success = com.example.util.RootHelper.remountSystemRw()
            _uiState.update {
                it.copy(
                    snackbarMessage = if (success) "Partições / e /system remontadas com permissão de Leitura e Escrita (R/W)!" else "Falha ao remontar partições como R/W. Verifique as permissões de Superusuário."
                )
            }
        }
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun dismissToast() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
