package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.models.FileType
import com.example.data.models.FileItem
import com.example.data.models.ThemeMode
import com.example.ui.components.*
import com.example.ui.theme.ArcboxTheme
import com.example.ui.viewmodel.FileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcboxApp(
    viewModel: FileViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var activeNotificationText by remember { mutableStateOf<String?>(null) }

    var inputName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<com.example.data.models.FileItem?>(null) }

    val isDark = when (uiState.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            activeNotificationText = msg
            viewModel.dismissToast()
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ArcboxTheme(
        darkTheme = isDark,
        accentOption = uiState.accentOption
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ArcboxNavigationDrawerContent(
                    storageVolumes = uiState.storageVolumes,
                    selectedVolume = uiState.selectedVolume,
                    currentFilterCategory = uiState.filterCategory,
                    trashCount = uiState.trashItems.size,
                    favoritesCount = uiState.favorites.size,
                    isFavoritesOnly = uiState.isFavoritesOnly,
                    currentThemeMode = uiState.themeMode,
                    onSelectVolume = { viewModel.selectStorageVolume(it) },
                    onSelectFavorites = { viewModel.selectFavorites() },
                    onSelectCategory = { viewModel.setFilterCategory(it) },
                    onOpenStorageDashboard = { viewModel.openStorageDashboard() },
                    onOpenTrashBin = { viewModel.openTrashBin() },
                    onOpenSettings = { viewModel.openSettings() },
                    onToggleThemeMode = { viewModel.setThemeMode(it) },
                    onCloseDrawer = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { },
                topBar = {
                    Column {
                        ArcboxTopBar(
                            storageVolumes = uiState.storageVolumes,
                            selectedVolume = uiState.selectedVolume,
                            onVolumeSelected = { viewModel.selectStorageVolume(it) },
                            searchQuery = uiState.searchQuery,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            filterCategory = uiState.filterCategory,
                            onFilterCategorySelected = { viewModel.setFilterCategory(it) },
                            sortOption = uiState.sortOption,
                            sortOrder = uiState.sortOrder,
                            onSortOptionSelected = { viewModel.setSortOption(it) },
                            viewMode = uiState.viewMode,
                            onToggleViewMode = { viewModel.toggleViewMode() },
                            onOpenStorageDashboard = { viewModel.openStorageDashboard() },
                            onOpenTrashBin = { viewModel.openTrashBin() },
                            onOpenSettings = { viewModel.openSettings() },
                            onOpenDrawer = {
                                scope.launch { drawerState.open() }
                            },
                            trashCount = uiState.trashItems.size,
                            isGlobalSearch = uiState.isGlobalSearch,
                            onToggleGlobalSearch = { viewModel.toggleGlobalSearch() },
                            searchHistory = uiState.searchHistory
                        )

                        if (uiState.isFavoritesOnly) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Favoritos (${uiState.currentFiles.size} itens)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            viewModel.setFilterCategory(null)
                                            uiState.selectedVolume?.let { vol -> viewModel.navigateToDirectory(vol.path) }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("Sair dos Favoritos", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        } else if (uiState.searchQuery.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${uiState.currentFiles.size} resultados para \"${uiState.searchQuery}\"",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.setSearchQuery("") },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("Limpar", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        } else {
                            ArcboxBreadcrumbHeader(
                                currentPath = uiState.currentPath,
                                onNavigateToPath = { viewModel.navigateToDirectory(it) },
                                onNavigateUp = { viewModel.navigateBackInTab() },
                                selectedCount = uiState.selectedItems.size
                            )
                        }

                        if (uiState.filterCategory == FileType.APK) {
                            ScrollableTabRow(
                                selectedTabIndex = when (uiState.appSubFilter) {
                                    "SYSTEM" -> 1
                                    "APK_FILES" -> 2
                                    else -> 0
                                },
                                edgePadding = 16.dp,
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Tab(
                                    selected = uiState.appSubFilter == "USER" || uiState.appSubFilter == "ALL",
                                    onClick = { viewModel.setAppSubFilter("USER") },
                                    text = { Text("Apps do Usuário", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                )
                                Tab(
                                    selected = uiState.appSubFilter == "SYSTEM",
                                    onClick = { viewModel.setAppSubFilter("SYSTEM") },
                                    text = { Text("Apps do Sistema", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                )
                                Tab(
                                    selected = uiState.appSubFilter == "APK_FILES",
                                    onClick = { viewModel.setAppSubFilter("APK_FILES") },
                                    text = { Text("APK", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                )
                            }
                        }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    ArcboxFileGridList(
                        files = uiState.currentFiles,
                        viewMode = uiState.viewMode,
                        showThumbnails = uiState.showThumbnails,
                        selectedItems = uiState.selectedItems,
                        onItemClick = { viewModel.navigateToDirectory(it.path) },
                        onItemLongClick = { viewModel.toggleSelectItem(it) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onCreateFolder = {
                            inputName = ""
                            showNewFolderDialog = true
                        },
                        onCreateFile = {
                            inputName = ""
                            showNewFileDialog = true
                        },
                        onCopySelected = { viewModel.copyToClipboard() },
                        onCutSelected = { viewModel.cutToClipboard() },
                        onMoveSelected = { viewModel.moveSelected(uiState.currentPath) },
                        onDeleteSelectedToTrash = {
                            if (uiState.confirmDelete) {
                                showDeleteConfirmationDialog = true
                            } else {
                                viewModel.deleteSelected(uiState.deletePermanently)
                            }
                        },
                        onCompressSelectedToZip = {
                            inputName = "arquivo.zip"
                            showCompressDialog = true
                        },
                        onSelectAll = { viewModel.selectAll() },
                        onClearSelection = { viewModel.clearSelection() },
                        clipboardItems = uiState.clipboardItems,
                        clipboardMode = uiState.clipboardMode,
                        onPasteClipboard = { viewModel.pasteClipboard() },
                        onClearClipboard = { viewModel.clearClipboard() },
                        onRenameItem = { item ->
                            renameTarget = item
                            inputName = item.name
                            showRenameDialog = true
                        },
                        onInspectApk = { viewModel.inspectApk(it) },
                        onOpenZip = { viewModel.openZipArchive(it) },
                        onOpenCodeEditor = { viewModel.openCodeEditor(it) },
                        onOpenMedia = { viewModel.openMediaViewer(it) },
                        onShareItem = { viewModel.shareFile(context, it) },
                        onShareSelected = { viewModel.shareSelectedFiles(context) },
                        searchQuery = uiState.searchQuery,
                        isGlobalSearch = uiState.isGlobalSearch,
                        onClearSearch = { viewModel.setSearchQuery("") },
                        onToggleGlobalSearch = { viewModel.toggleGlobalSearch() }
                    )
                }

                // Operation Progress Indicator overlay
                uiState.operationStatusText?.let { statusText ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 10.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(statusText, style = MaterialTheme.typography.titleMedium)
                            uiState.operationProgress?.let { prog ->
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { prog },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                ArcboxNotification(
                    message = activeNotificationText,
                    onDismissed = {
                        activeNotificationText = null
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                )
            }
        }
    }

        // Modals & Overlays
        uiState.activeApkInfo?.let { apkInfo ->
            ArcboxApkInspectorModal(
                apkInfo = apkInfo,
                onShare = { info ->
                    val item = FileItem(
                        id = info.packageName,
                        name = info.appName,
                        path = info.apkFilePath,
                        size = if (java.io.File(info.apkFilePath).exists()) java.io.File(info.apkFilePath).length() else 0L,
                        lastModified = System.currentTimeMillis(),
                        isDirectory = false,
                        fileType = FileType.APK,
                        extension = "apk",
                        mimeType = "application/vnd.android.package-archive",
                        packageName = info.packageName
                    )
                    viewModel.shareFile(context, item)
                },
                onLaunchApp = { pkg -> viewModel.launchApp(context, pkg) },
                onOpenSettings = { pkg -> viewModel.openAppSettings(context, pkg) },
                onClose = { viewModel.closeApkInspector() }
            )
        }

        uiState.activeZipFile?.let { zipFile ->
            ArcboxArchiveViewerModal(
                zipItem = zipFile,
                zipEntries = uiState.activeZipEntries,
                onExtractAll = { viewModel.extractZipArchive(zipFile) },
                onClose = { viewModel.closeZipArchive() }
            )
        }

        uiState.activeEditorFile?.let { editorFile ->
            ArcboxCodeEditorModal(
                fileItem = editorFile,
                initialContent = uiState.editorContent,
                onSaveContent = { viewModel.saveCodeEditor(it) },
                onClose = { viewModel.closeCodeEditor() }
            )
        }

        uiState.activeMediaItem?.let { mediaItem ->
            ArcboxMediaViewerModal(
                item = mediaItem,
                onNext = { viewModel.nextMediaItem() },
                onPrevious = { viewModel.previousMediaItem() },
                onClose = { viewModel.closeMediaViewer() }
            )
        }

        if (uiState.isStorageDashboardOpen) {
            ArcboxStorageDashboardModal(
                stats = uiState.storageCategoryStats,
                largeFiles = uiState.largeFiles,
                duplicateGroups = uiState.duplicateGroups,
                onFetchCategoryDetails = { viewModel.getCategoryDetails(it) },
                onNavigateToFolder = { viewModel.navigateToDirectoryAndCloseDashboard(it) },
                onClose = { viewModel.closeStorageDashboard() }
            )
        }

        if (uiState.isTrashBinOpen) {
            ArcboxTrashBinModal(
                trashItems = uiState.trashItems,
                onRestoreItem = { viewModel.restoreTrashItem(it) },
                onRestoreSelected = { viewModel.restoreSelectedTrashItems(it) },
                onDeletePermanently = { viewModel.deleteTrashPermanently(it) },
                onDeleteSelected = { viewModel.deleteSelectedTrashPermanently(it) },
                onEmptyTrash = { viewModel.emptyTrashBin() },
                onClose = { viewModel.closeTrashBin() }
            )
        }

        if (uiState.isSettingsOpen) {
            ArcboxSettingsModal(
                currentThemeMode = uiState.themeMode,
                currentAccent = uiState.accentOption,
                deletePermanently = uiState.deletePermanently,
                onToggleDeletePermanently = { viewModel.setDeletePermanently(it) },
                confirmDelete = uiState.confirmDelete,
                onToggleConfirmDelete = { viewModel.setConfirmDelete(it) },
                showThumbnails = uiState.showThumbnails,
                onToggleShowThumbnails = { viewModel.toggleShowThumbnails(it) },
                onSelectThemeMode = { viewModel.setThemeMode(it) },
                onSelectAccent = { viewModel.setAccentOption(it) },
                isMegaConnected = uiState.isMegaConnected,
                onToggleMegaConnected = { viewModel.toggleMegaConnected(it) },
                isDriveConnected = uiState.isDriveConnected,
                onToggleDriveConnected = { viewModel.toggleDriveConnected(it) },
                isMediafireConnected = uiState.isMediafireConnected,
                onToggleMediafireConnected = { viewModel.toggleMediafireConnected(it) },
                isOnedriveConnected = uiState.isOnedriveConnected,
                onToggleOnedriveConnected = { viewModel.toggleOnedriveConnected(it) },
                isDropboxConnected = uiState.isDropboxConnected,
                onToggleDropboxConnected = { viewModel.toggleDropboxConnected(it) },
                onClose = { viewModel.closeSettings() }
            )
        }

        // Dialogs
        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text("Criar Nova Pasta") },
                text = {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Nome da Pasta") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (inputName.isNotBlank()) {
                            viewModel.createFolder(inputName.trim())
                        }
                        showNewFolderDialog = false
                    }) {
                        Text("Criar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancelar") }
                }
            )
        }

        if (showNewFileDialog) {
            AlertDialog(
                onDismissRequest = { showNewFileDialog = false },
                title = { Text("Criar novo arquivo") },
                text = {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Nome do Arquivo (ex: nota.txt)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (inputName.isNotBlank()) {
                            viewModel.createFile(inputName.trim())
                        }
                        showNewFileDialog = false
                    }) {
                        Text("Criar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFileDialog = false }) { Text("Cancelar") }
                }
            )
        }

        if (showRenameDialog && renameTarget != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Renomear Item") },
                text = {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Novo Nome") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        renameTarget?.let { target ->
                            if (inputName.isNotBlank()) {
                                viewModel.renameItem(target, inputName.trim())
                            }
                        }
                        showRenameDialog = false
                    }) {
                        Text("Salvar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) { Text("Cancelar") }
                }
            )
        }

        if (showCompressDialog) {
            AlertDialog(
                onDismissRequest = { showCompressDialog = false },
                title = { Text("Compactar em ZIP") },
                text = {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Nome do arquivo ZIP") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (inputName.isNotBlank()) {
                            viewModel.compressSelectedToZip(inputName.trim())
                        }
                        showCompressDialog = false
                    }) {
                        Text("Compactar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCompressDialog = false }) { Text("Cancelar") }
                }
            )
        }

        if (showDeleteConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmationDialog = false },
                title = { 
                    Text(
                        text = if (uiState.deletePermanently) "Excluir permanentemente" else "Mover para Lixeira"
                    ) 
                },
                text = { 
                    Text(
                        text = if (uiState.deletePermanently) "arquivo excluído com sucesso" else "arquivo removido para lixeira"
                    ) 
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteSelected(uiState.deletePermanently)
                            showDeleteConfirmationDialog = false
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
