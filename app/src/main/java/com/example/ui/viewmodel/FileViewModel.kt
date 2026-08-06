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

data class FileUiState(
    val currentTabId: String = "tab_main",
    val tabs: List<TabItem> = emptyList(),
    val storageVolumes: List<StorageVolume> = emptyList(),
    val selectedVolume: StorageVolume? = null,
    val currentPath: String = "",
    val currentFiles: List<FileItem> = emptyList(),
    val selectedItems: Set<FileItem> = emptySet(),
    val searchQuery: String = "",
    val isGlobalSearch: Boolean = false,
    val isFavoritesOnly: Boolean = false,
    val searchHistory: List<String> = listOf("pdf", "jpg", "mp4", "apk", "doc", "zip"),
    val filterCategory: FileType? = null,
    val appSubFilter: String = "USER", // "USER", "SYSTEM", "APK_FILES"
    val sortOption: SortOption = SortOption.NAME,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val viewMode: ViewMode = ViewMode.LIST,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val accentOption: AccentColorOption = AccentColorOption.AZUL_CLARO,
    val isLoading: Boolean = false,
    val operationProgress: Float? = null,
    val operationStatusText: String? = null,
    val activeMediaItem: FileItem? = null,
    val activeEditorFile: FileItem? = null,
    val editorContent: String = "",
    val activeApkInfo: ApkInfo? = null,
    val activeZipFile: FileItem? = null,
    val activeZipEntries: List<ZipEntryItem> = emptyList(),
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
    val isDropboxConnected: Boolean = false
)

class FileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("arcbox_prefs", android.content.Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(FileUiState())
    val uiState: StateFlow<FileUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Clean old trash items (30 days) on startup
            repository.cleanOldTrashItems(30)
            
            val deletePermanently = prefs.getBoolean("delete_permanently", false)
            val confirmDelete = prefs.getBoolean("confirm_delete", true)
            val showThumbnails = prefs.getBoolean("show_thumbnails", true)
            val isMegaConnected = prefs.getBoolean("cloud_connected_mega", false)
            val isDriveConnected = prefs.getBoolean("cloud_connected_drive", false)
            val isMediafireConnected = prefs.getBoolean("cloud_connected_mediafire", false)
            val isOnedriveConnected = prefs.getBoolean("cloud_connected_onedrive", false)
            val isDropboxConnected = prefs.getBoolean("cloud_connected_dropbox", false)
            
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

            _uiState.update {
                it.copy(
                    storageVolumes = volumes,
                    selectedVolume = primary,
                    tabs = listOf(initialTab),
                    currentTabId = initialTab.id,
                    currentPath = initialPath,
                    deletePermanently = deletePermanently,
                    confirmDelete = confirmDelete,
                    showThumbnails = showThumbnails,
                    isMegaConnected = isMegaConnected,
                    isDriveConnected = isDriveConnected,
                    isMediafireConnected = isMediafireConnected,
                    isOnedriveConnected = isOnedriveConnected,
                    isDropboxConnected = isDropboxConnected
                )
            }
            refreshFiles()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refreshFiles() {
        viewModelScope.launch {
            val state = uiState.value
            val volumes = repository.getStorageVolumes()
            val files = if (state.isFavoritesOnly) {
                repository.getFavoriteFiles(
                    sortOption = state.sortOption,
                    sortOrder = state.sortOrder,
                    searchQuery = state.searchQuery
                )
            } else {
                repository.listFiles(
                    directoryPath = state.currentPath,
                    safUriString = null,
                    sortOption = state.sortOption,
                    sortOrder = state.sortOrder,
                    searchQuery = state.searchQuery,
                    filterCategory = state.filterCategory,
                    appSubFilter = state.appSubFilter,
                    isGlobalSearch = state.isGlobalSearch
                )
            }
            _uiState.update { 
                it.copy(
                    currentFiles = files,
                    storageVolumes = volumes
                )
            }
        }
    }

    fun navigateToDirectory(path: String) {
        viewModelScope.launch {
            val currentTabs = _uiState.value.tabs.toMutableList()
            val currentTabIdx = currentTabs.indexOfFirst { it.id == _uiState.value.currentTabId }

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
                        isFavoritesOnly = false
                    )
                }
                refreshFiles()
            }
        }
    }

    fun navigateBackInTab(): Boolean {
        val currentTabs = _uiState.value.tabs.toMutableList()
        val currentTabIdx = currentTabs.indexOfFirst { it.id == _uiState.value.currentTabId }
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
                        selectedItems = emptySet()
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
        _uiState.update { it.copy(filterCategory = category, isFavoritesOnly = false) }
        refreshFiles()
    }

    fun setAppSubFilter(subFilter: String) {
        _uiState.update { it.copy(appSubFilter = subFilter) }
        refreshFiles()
    }

    fun shareFile(context: Context, item: FileItem) {
        try {
            val file = File(item.path)
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
                val file = File(item.path)
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
        _uiState.update { it.copy(isFavoritesOnly = true, filterCategory = null) }
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
        _uiState.update { it.copy(accentOption = accent) }
    }

    fun setThemeMode(mode: ThemeMode) {
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

    fun toggleMegaConnected(connected: Boolean) {
        prefs.edit().putBoolean("cloud_connected_mega", connected).apply()
        _uiState.update { it.copy(isMegaConnected = connected) }
        refreshVolumesAndCurrentFiles()
    }

    fun toggleDriveConnected(connected: Boolean) {
        prefs.edit().putBoolean("cloud_connected_drive", connected).apply()
        _uiState.update { it.copy(isDriveConnected = connected) }
        refreshVolumesAndCurrentFiles()
    }

    fun toggleMediafireConnected(connected: Boolean) {
        prefs.edit().putBoolean("cloud_connected_mediafire", connected).apply()
        _uiState.update { it.copy(isMediafireConnected = connected) }
        refreshVolumesAndCurrentFiles()
    }

    fun toggleOnedriveConnected(connected: Boolean) {
        prefs.edit().putBoolean("cloud_connected_onedrive", connected).apply()
        _uiState.update { it.copy(isOnedriveConnected = connected) }
        refreshVolumesAndCurrentFiles()
    }

    fun toggleDropboxConnected(connected: Boolean) {
        prefs.edit().putBoolean("cloud_connected_dropbox", connected).apply()
        _uiState.update { it.copy(isDropboxConnected = connected) }
        refreshVolumesAndCurrentFiles()
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
                if (mode == ClipboardMode.COPY) {
                    repository.copyFile(item.path, targetPath) { progress ->
                        _uiState.update { it.copy(operationProgress = progress) }
                    }
                } else {
                    repository.moveFile(item.path, targetPath) { progress ->
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

    fun deleteSelected(permanently: Boolean) {
        viewModelScope.launch {
            val selected = _uiState.value.selectedItems
            var count = 0
            if (permanently) {
                for (item in selected) {
                    val file = File(item.path)
                    if (file.exists()) {
                        if (file.deleteRecursively()) {
                            count++
                        }
                    }
                }
                _uiState.update { it.copy(selectedItems = emptySet()) }
                showToast("arquivo excluído com sucesso")
            } else {
                for (item in selected) {
                    if (repository.moveToTrash(item)) {
                        count++
                    }
                }
                _uiState.update { it.copy(selectedItems = emptySet()) }
                showToast("arquivo removido para lixeira")
            }
            refreshFiles()
        }
    }

    fun deleteSelectedToTrash() {
        deleteSelected(_uiState.value.deletePermanently)
    }

    fun toggleFavorite(item: FileItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item)
            refreshFiles()
        }
    }

    // -------------------------------------------------------------
    // MODALS & VIEWERS (APK, ZIP, TEXT, MEDIA, DASHBOARD, TRASH, SETTINGS)
    // -------------------------------------------------------------
    fun inspectApk(item: FileItem) {
        viewModelScope.launch {
            val info = repository.inspectApk(item.path)
            _uiState.update { it.copy(activeApkInfo = info) }
        }
    }

    fun closeApkInspector() {
        _uiState.update { it.copy(activeApkInfo = null) }
    }

    fun openZipArchive(item: FileItem) {
        viewModelScope.launch {
            val entries = repository.listZipContents(item.path)
            _uiState.update { it.copy(activeZipFile = item, activeZipEntries = entries) }
        }
    }

    fun closeZipArchive() {
        _uiState.update { it.copy(activeZipFile = null, activeZipEntries = emptyList()) }
    }

    fun extractZipArchive(item: FileItem, targetDirectory: String = _uiState.value.currentPath) {
        viewModelScope.launch {
            _uiState.update { it.copy(operationStatusText = "Descompactando ${item.name}...") }
            val success = repository.extractZip(item.path, targetDirectory) { progress, name ->
                _uiState.update { it.copy(operationProgress = progress) }
            }
            _uiState.update { it.copy(operationStatusText = null, operationProgress = null) }
            if (success) {
                showToast("Descompactado com sucesso!")
                refreshFiles()
            } else {
                showToast("Erro ao descompactar o arquivo.")
            }
        }
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
        _uiState.update { it.copy(activeMediaItem = item) }
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

    fun openStorageDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isStorageDashboardOpen = true, isLoading = true) }
            val stats = repository.analyzeStorage(_uiState.value.currentPath)
            val large = repository.findLargeFiles(_uiState.value.currentPath)
            val dups = repository.findDuplicateFiles(_uiState.value.currentPath)
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
            var count = 0
            for (item in items) {
                if (repository.restoreFromTrash(item)) {
                    count++
                }
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
            var count = 0
            for (item in items) {
                if (repository.permanentlyDeleteTrash(item)) {
                    count++
                }
            }
            showToast("$count item(ns) excluído(s) permanentemente.")
        }
    }

    fun emptyTrashBin() {
        viewModelScope.launch {
            repository.emptyTrashBin()
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

    fun showToast(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun dismissToast() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
