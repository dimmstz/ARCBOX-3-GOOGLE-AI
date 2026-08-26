package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.models.FileType
import com.example.data.models.FileItem
import com.example.data.models.ThemeMode
import androidx.compose.ui.window.DialogProperties
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
    var filesToDeleteDirectly by remember { mutableStateOf<List<com.example.data.models.FileItem>?>(null) }
    var isDirectDeletePermanent by remember { mutableStateOf(false) }
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

    LaunchedEffect(uiState.isWelcomeOnboardingOpen) {
        if (!uiState.isWelcomeOnboardingOpen) {
            drawerState.snapTo(DrawerValue.Closed)
        }
    }

    val currentTab = uiState.tabs.find { it.id == uiState.currentTabId }
    val canGoBackInFiles = (currentTab != null && currentTab.historyIndex > 0) ||
            uiState.filterCategory != null ||
            uiState.isFavoritesOnly ||
            uiState.searchQuery.isNotEmpty() ||
            uiState.selectedItems.isNotEmpty() ||
            uiState.tempZipSourcePath != null

    val hasOtherModalOrOverlay = uiState.activeApkInfo != null ||
            uiState.activeZipFile != null ||
            uiState.activeEditorFile != null ||
            uiState.activeMediaItem != null ||
            uiState.isCloudManagerOpen ||
            uiState.isStorageDashboardOpen ||
            uiState.isTrashBinOpen ||
            uiState.isSettingsOpen ||
            uiState.isWelcomeOnboardingOpen ||
            uiState.oauthConnectProvider != null ||
            showNewFolderDialog ||
            showNewFileDialog ||
            showRenameDialog ||
            showCompressDialog ||
            showDeleteConfirmationDialog

    val hasActiveModalOrOverlay = drawerState.isOpen || hasOtherModalOrOverlay

    BackHandler(enabled = hasActiveModalOrOverlay || canGoBackInFiles) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            showNewFolderDialog -> showNewFolderDialog = false
            showNewFileDialog -> showNewFileDialog = false
            showRenameDialog -> showRenameDialog = false
            showCompressDialog -> showCompressDialog = false
            showDeleteConfirmationDialog -> showDeleteConfirmationDialog = false
            uiState.oauthConnectProvider != null -> viewModel.closeOAuthFlow()
            uiState.activeApkInfo != null -> viewModel.closeApkInspector()
            uiState.activeZipFile != null -> viewModel.closeZipArchive()
            uiState.activeEditorFile != null -> viewModel.closeCodeEditor()
            uiState.activeMediaItem != null -> viewModel.closeMediaViewer()
            uiState.isCloudManagerOpen -> viewModel.closeCloudManager()
            uiState.isStorageDashboardOpen -> viewModel.closeStorageDashboard()
            uiState.isTrashBinOpen -> viewModel.closeTrashBin()
            uiState.isSettingsOpen -> viewModel.closeSettings()
            uiState.isWelcomeOnboardingOpen -> viewModel.closeWelcomeOnboarding()
            uiState.selectedItems.isNotEmpty() -> viewModel.clearSelection()
            uiState.tempZipSourcePath != null -> viewModel.exitTempZipView()
            else -> viewModel.navigateBackInTab()
        }
    }

    ArcboxTheme(
        darkTheme = isDark,
        accentOption = uiState.accentOption,
        customColorHex = uiState.customAccentColorHex
    ) {
        if (uiState.isWelcomeOnboardingOpen) {
            PermissionWelcomeScreen(
                onDismiss = {
                    scope.launch { drawerState.snapTo(DrawerValue.Closed) }
                    viewModel.closeWelcomeOnboarding()
                }
            )
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = !hasOtherModalOrOverlay,
                scrimColor = DrawerDefaults.scrimColor,
                drawerContent = {
                ArcboxNavigationDrawerContent(
                    storageVolumes = uiState.storageVolumes,
                    selectedVolume = uiState.selectedVolume,
                    currentFilterCategory = uiState.filterCategory,
                    trashCount = uiState.trashItems.size,
                    favoritesCount = uiState.favorites.size,
                    isFavoritesOnly = uiState.isFavoritesOnly,
                    isAppManagerOpen = uiState.isAppManagerOpen,
                    currentThemeMode = uiState.themeMode,
                    isMegaConnected = uiState.isMegaConnected,
                    isDriveConnected = uiState.isDriveConnected,
                    isMediafireConnected = uiState.isMediafireConnected,
                    isOnedriveConnected = uiState.isOnedriveConnected,
                    isDropboxConnected = uiState.isDropboxConnected,
                    isWebdavConnected = uiState.isWebDavConnected,
                    megaEmail = uiState.megaAccountEmail,
                    webdavEmail = uiState.webdavAccountEmail,
                    driveEmail = uiState.driveAccountEmail,
                    mediafireEmail = uiState.mediafireAccountEmail,
                    onedriveEmail = uiState.onedriveAccountEmail,
                    dropboxEmail = uiState.dropboxAccountEmail,
                    onSelectVolume = { viewModel.selectStorageVolume(it) },
                    onStartOAuthFlow = { provider -> viewModel.startOAuthFlow(provider) },
                    onSelectFavorites = { viewModel.selectFavorites() },
                    onSelectCategory = { viewModel.setFilterCategory(it) },
                    onOpenAppManager = { viewModel.openAppManager() },
                    onOpenStorageDashboard = { viewModel.openStorageDashboard() },
                    onOpenTrashBin = { viewModel.openTrashBin() },
                    onOpenSettings = { viewModel.openSettings() },
                    onOpenCloudManager = { viewModel.openCloudManager() },
                    onOpenWelcomeOnboarding = { viewModel.openWelcomeOnboarding() },
                    onToggleThemeMode = { viewModel.setThemeMode(it) },
                    onCloseDrawer = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            Scaffold(
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
                            onOpenCloudManager = { viewModel.openCloudManager() },
                            onOpenDrawer = {
                                scope.launch { drawerState.open() }
                            },
                            trashCount = uiState.trashItems.size,
                            isGlobalSearch = uiState.isGlobalSearch,
                            onToggleGlobalSearch = { viewModel.toggleGlobalSearch() },
                            searchHistory = uiState.searchHistory
                        )

                        if (uiState.tempZipSourcePath != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
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
                                            Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Pasta Temporária (Conteúdo ZIP)",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = "Clique no ícone de extração para salvar arquivos.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = { viewModel.exitTempZipView() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("Sair", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        } else if (uiState.isFavoritesOnly) {
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
                                storageVolumes = uiState.storageVolumes,
                                onNavigateToPath = { viewModel.navigateToDirectory(it) },
                                onNavigateUp = { viewModel.navigateBackInTab() },
                                selectedCount = uiState.selectedItems.size,
                                isFilterActive = uiState.filterCategory != null || uiState.isFavoritesOnly || uiState.searchQuery.isNotEmpty()
                            )
                        }

                        if (uiState.isAppManagerOpen) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TabRow(
                                    selectedTabIndex = if (uiState.appSubFilter == "SYSTEM") 1 else 0,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Tab(
                                        selected = uiState.appSubFilter != "SYSTEM",
                                        onClick = { viewModel.setAppSubFilter("USER") },
                                        text = { Text("Apps do Usuário", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                    )
                                    Tab(
                                        selected = uiState.appSubFilter == "SYSTEM",
                                        onClick = { viewModel.setAppSubFilter("SYSTEM") },
                                        text = { Text("Apps do Sistema", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.closeAppManager() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Sair dos Aplicativos",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    val isArquivosSelected = !uiState.isFavoritesOnly && !uiState.isRecentsOnly && !uiState.isTrashBinOpen
                    NavigationBarItem(
                        selected = isArquivosSelected,
                        onClick = {
                            viewModel.selectFilesTab()
                            viewModel.closeTrashBin()
                        },
                        icon = {
                            Icon(
                                imageVector = if (isArquivosSelected) Icons.Filled.Folder else Icons.Outlined.Folder,
                                contentDescription = "Arquivos"
                            )
                        },
                        label = { Text("Arquivos") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    val isRecentsSelected = uiState.isRecentsOnly && !uiState.isTrashBinOpen
                    NavigationBarItem(
                        selected = isRecentsSelected,
                        onClick = {
                            viewModel.selectRecents()
                            viewModel.closeTrashBin()
                        },
                        icon = {
                            Icon(
                                imageVector = if (isRecentsSelected) Icons.Filled.History else Icons.Outlined.History,
                                contentDescription = "Recentes"
                            )
                        },
                        label = { Text("Recentes") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    val isFavoritesSelected = uiState.isFavoritesOnly && !uiState.isTrashBinOpen
                    NavigationBarItem(
                        selected = isFavoritesSelected,
                        onClick = {
                            viewModel.selectFavorites()
                            viewModel.closeTrashBin()
                        },
                        icon = {
                            Icon(
                                imageVector = if (isFavoritesSelected) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favoritos"
                            )
                        },
                        label = { Text("Favoritos") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    val isLixeiraSelected = uiState.isTrashBinOpen
                    NavigationBarItem(
                        selected = isLixeiraSelected,
                        onClick = {
                            viewModel.openTrashBin()
                        },
                        icon = {
                            Icon(
                                imageVector = if (isLixeiraSelected) Icons.Filled.Delete else Icons.Outlined.DeleteOutline,
                                contentDescription = "Lixeira"
                            )
                        },
                        label = { Text("Lixeira") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
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
                        currentPath = uiState.currentPath,
                        onItemClick = { item ->
                            if (item.isDirectory) {
                                viewModel.navigateToDirectory(item.path)
                            } else {
                                viewModel.openGenericFile(item)
                            }
                        },
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
                        onToggleGlobalSearch = { viewModel.toggleGlobalSearch() },
                        tempZipSourcePath = uiState.tempZipSourcePath,
                        onExtractIndividual = { viewModel.extractIndividualFileFromTemp(it) },
                        onUninstallApp = { packageName -> viewModel.uninstallUserApp(context, packageName) },
                        onOpenAppSettings = { packageName -> viewModel.openAppSettings(context, packageName) }
                    )
                }

                // Operation Progress Indicator overlay
                uiState.operationStatusText?.let { statusText ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 8.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp)
                            .widthIn(max = 380.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val prog = uiState.operationProgress
                            if (prog != null) {
                                val percentage = (prog * 100).coerceIn(0f, 100f).toInt()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Carregando...",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$percentage%",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { prog },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    strokeCap = StrokeCap.Round,
                                    gapSize = 0.dp,
                                    drawStopIndicator = {}
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedButton(
                                onClick = { viewModel.cancelCurrentOperation() },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                    text = "Cancelar",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
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
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 84.dp)
                )
            }
        }
    }

        // Modals & Overlays
        uiState.activeApkInfo?.let { apkInfo ->
            ArcboxApkInspectorModal(
                apkInfo = apkInfo,
                onInstall = { path -> viewModel.installApk(context, path) },
                onUninstall = { pkg -> viewModel.uninstallUserApp(context, pkg) },
                onRemoveSystemApp = { pkg -> viewModel.removeSystemApp(context, pkg) },
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
                onOpenPackage = { info -> viewModel.openApkPackage(info) },
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
                onOpen = { viewModel.openAndExtractZipArchive(zipFile) },
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
                onClose = { viewModel.closeMediaViewer() },
                onDelete = { item ->
                    if (uiState.confirmDelete) {
                        filesToDeleteDirectly = listOf(item)
                        isDirectDeletePermanent = false
                    } else {
                        viewModel.deleteFilesDirectly(listOf(item), permanently = false)
                        viewModel.closeMediaViewer()
                    }
                },
                onEditWithThirdParty = { item ->
                    viewModel.editFileWithThirdParty(context, item)
                }
            )
        }

        if (uiState.isStorageDashboardOpen) {
            ArcboxStorageDashboardModal(
                stats = uiState.storageCategoryStats,
                largeFiles = uiState.largeFiles,
                duplicateGroups = uiState.duplicateGroups,
                storageVolumes = uiState.storageVolumes,
                selectedVolume = uiState.selectedVolume,
                isAnalyzing = uiState.isLoading,
                onFetchCategoryDetails = { viewModel.getCategoryDetails(it) },
                onNavigateToFolder = { viewModel.navigateToDirectoryAndCloseDashboard(it) },
                onSelectVolume = { viewModel.selectDashboardVolume(it) },
                onOpenCloudManager = { viewModel.openCloudManager() },
                onDeleteFiles = { files ->
                    if (uiState.confirmDelete) {
                        filesToDeleteDirectly = files
                        isDirectDeletePermanent = uiState.deletePermanently
                    } else {
                        viewModel.deleteFilesDirectly(files, uiState.deletePermanently)
                    }
                },
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
                customAccentColorHex = uiState.customAccentColorHex,
                deletePermanently = uiState.deletePermanently,
                onToggleDeletePermanently = { viewModel.setDeletePermanently(it) },
                confirmDelete = uiState.confirmDelete,
                onToggleConfirmDelete = { viewModel.setConfirmDelete(it) },
                showThumbnails = uiState.showThumbnails,
                onToggleShowThumbnails = { viewModel.toggleShowThumbnails(it) },
                onSelectThemeMode = { viewModel.setThemeMode(it) },
                onSelectAccent = { viewModel.setAccentOption(it) },
                onSelectCustomColor = { viewModel.setCustomAccentColor(it) },
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
                megaEmail = uiState.megaAccountEmail,
                driveEmail = uiState.driveAccountEmail,
                mediafireEmail = uiState.mediafireAccountEmail,
                onedriveEmail = uiState.onedriveAccountEmail,
                dropboxEmail = uiState.dropboxAccountEmail,
                isRootAvailable = uiState.isRootAvailable,
                isRootGranted = uiState.isRootGranted,
                rootStatusDetails = uiState.rootStatusDetails,
                onRequestRootAccess = { viewModel.checkRootStatus(requestPrompt = true) },
                onRemountSystemRw = { viewModel.remountSystemRw() },
                onOpenCloudManager = { viewModel.openCloudManager() },
                onOpenWelcomeOnboarding = { viewModel.openWelcomeOnboarding() },
                onClose = { viewModel.closeSettings() }
            )
        }

        val oauthProvider = uiState.oauthConnectProvider
        if (oauthProvider != null) {
            OAuthCloudConnectModal(
                provider = oauthProvider,
                onAuthorize = { email, serverUrl, passwordOrToken ->
                    viewModel.completeOAuthConnect(oauthProvider, email, serverUrl, passwordOrToken)
                },
                onDismiss = { viewModel.closeOAuthFlow() }
            )
        }

        if (uiState.isCloudManagerOpen) {
            CloudIntegrationManagerDialog(
                connectedMega = uiState.isMegaConnected,
                connectedWebdav = uiState.isWebDavConnected,
                connectedDrive = uiState.isDriveConnected,
                connectedMediafire = uiState.isMediafireConnected,
                connectedOnedrive = uiState.isOnedriveConnected,
                connectedDropbox = uiState.isDropboxConnected,
                megaEmail = uiState.megaAccountEmail,
                webdavEmail = uiState.webdavAccountEmail,
                driveEmail = uiState.driveAccountEmail,
                mediafireEmail = uiState.mediafireAccountEmail,
                onedriveEmail = uiState.onedriveAccountEmail,
                dropboxEmail = uiState.dropboxAccountEmail,
                safCloudDrives = uiState.safCloudDrives,
                onRegisterSafDrive = { uri -> viewModel.registerSafCloudDrive(uri) },
                onRemoveSafDrive = { id -> viewModel.removeSafCloudDrive(id) },
                onStartOAuthFlow = { provider -> viewModel.startOAuthFlow(provider) },
                onQuickConnectProvider = { provider -> viewModel.quickConnectCloudProvider(provider) },
                onConnectAll = { viewModel.connectAllCloudProviders() },
                onDisconnectProvider = { provider -> viewModel.disconnectCloudProvider(provider) },
                onOpenCloudPath = { path ->
                    viewModel.closeCloudManager()
                    viewModel.closeStorageDashboard()
                    viewModel.closeSettings()
                    viewModel.navigateToDirectory(path)
                },
                onClose = { viewModel.closeCloudManager() }
            )
        }

        // Dialogs
        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                title = { Text("Criar Nova Pasta") },
                text = {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Nome da Pasta") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
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
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                title = { Text("Criar Novo Arquivo") },
                text = {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Nome do Arquivo (ex: nota.txt)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (inputName.isNotBlank()) {
                            viewModel.createFolder(inputName.trim()) // or createFile
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
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                title = { Text("Renomear Item") },
                text = {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Novo Nome") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
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
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                title = { Text("Compactar em ZIP") },
                text = {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Nome do arquivo ZIP") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
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
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                title = { 
                    Text(
                        text = if (uiState.deletePermanently) "Excluir permanentemente" else "Mover para Lixeira"
                    ) 
                },
                text = { 
                    val count = uiState.selectedItems.size
                    val message = if (count == 1) {
                        val firstItemName = uiState.selectedItems.firstOrNull()?.name ?: "o arquivo"
                        if (uiState.deletePermanently) "Deseja realmente excluir permanentemente o arquivo \"$firstItemName\"? Esta ação não poderá ser desfeita."
                        else "Deseja realmente mover o arquivo \"$firstItemName\" para a Lixeira?"
                    } else {
                        if (uiState.deletePermanently) "Deseja realmente excluir permanentemente os $count itens selecionados? Esta ação não poderá ser desfeita."
                        else "Deseja realmente mover os $count itens selecionados para a Lixeira?"
                    }
                    Text(message)
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteSelected(uiState.deletePermanently)
                            showDeleteConfirmationDialog = false
                        },
                        colors = if (uiState.deletePermanently) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (uiState.deletePermanently) "Excluir" else "Mover")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        filesToDeleteDirectly?.let { files ->
            val permanent = isDirectDeletePermanent
            AlertDialog(
                onDismissRequest = { filesToDeleteDirectly = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                title = {
                    Text(
                        text = if (permanent) "Excluir permanentemente" else "Mover para Lixeira"
                    )
                },
                text = {
                    val message = if (files.size == 1) {
                        if (permanent) "Deseja realmente excluir permanentemente o arquivo \"${files.first().name}\"? Esta ação não poderá ser desfeita."
                        else "Deseja realmente mover o arquivo \"${files.first().name}\" para a Lixeira?"
                    } else {
                        if (permanent) "Deseja realmente excluir permanentemente os ${files.size} itens selecionados? Esta ação não poderá ser desfeita."
                        else "Deseja realmente mover os ${files.size} itens selecionados para a Lixeira?"
                    }
                    Text(text = message)
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteFilesDirectly(files, permanent)
                            filesToDeleteDirectly = null
                            // If we deleted from media viewer, we should close it if the active media item is deleted
                            uiState.activeMediaItem?.let { active ->
                                if (files.any { it.path == active.path }) {
                                    viewModel.closeMediaViewer()
                                }
                            }
                        },
                        colors = if (permanent) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (permanent) "Excluir" else "Mover")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { filesToDeleteDirectly = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
        }
    }
}
