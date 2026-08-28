package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import com.example.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.data.models.ClipboardMode
import com.example.data.models.FileItem
import com.example.data.models.FileType
import com.example.data.models.ViewMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArcboxFileGridList(
    files: List<FileItem>,
    viewMode: ViewMode,
    showThumbnails: Boolean = true,
    showExtensions: Boolean = true,
    selectedItems: Set<FileItem>,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onToggleFavorite: (FileItem) -> Unit,
    onCreateFolder: () -> Unit,
    onCreateFile: () -> Unit,
    onCopySelected: () -> Unit,
    onCutSelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onDeleteSelectedToTrash: () -> Unit,
    onCompressSelectedToZip: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    clipboardItems: Set<FileItem> = emptySet(),
    clipboardMode: ClipboardMode? = null,
    onPasteClipboard: () -> Unit = {},
    onClearClipboard: () -> Unit = {},
    onRenameItem: (FileItem) -> Unit,
    onInspectApk: (FileItem) -> Unit,
    onOpenZip: (FileItem) -> Unit,
    onOpenCodeEditor: (FileItem) -> Unit,
    onOpenMedia: (FileItem) -> Unit,
    onShareItem: (FileItem) -> Unit = {},
    onShareSelected: () -> Unit = {},
    searchQuery: String = "",
    isGlobalSearch: Boolean = false,
    onClearSearch: () -> Unit = {},
    onToggleGlobalSearch: () -> Unit = {},
    currentPath: String = "",
    tempZipSourcePath: String? = null,
    onExtractIndividual: ((FileItem) -> Unit)? = null,
    onUninstallApp: ((String) -> Unit)? = null,
    onOpenAppSettings: ((String) -> Unit)? = null
) {
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    LaunchedEffect(currentPath) {
        gridState.scrollToItem(0)
        listState.scrollToItem(0)
    }

    var showFabMenu by remember { mutableStateOf(false) }
    val isMultiSelecting = selectedItems.isNotEmpty()

    // Smooth rotation angle for + into X (135 degrees)
    val fabRotation by animateFloatAsState(
        targetValue = if (isMultiSelecting || showFabMenu) 135f else 0f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "FabRotation"
    )

    val selectedItemIds = remember(selectedItems) { selectedItems.mapTo(HashSet()) { it.id } }

    Box(modifier = Modifier.fillMaxSize()) {
        if (files.isEmpty()) {
            if (searchQuery.isNotBlank()) {
                EmptySearchState(
                    searchQuery = searchQuery,
                    isGlobalSearch = isGlobalSearch,
                    onClearSearch = onClearSearch,
                    onToggleGlobalSearch = onToggleGlobalSearch
                )
            } else {
                EmptyFolderState(onCreateFolder = onCreateFolder)
            }
        } else {
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(animationSpec = tween(140, easing = FastOutLinearInEasing))
                },
                label = "ViewModeAnimation"
            ) { mode ->
                if (mode == ViewMode.GRID) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(10.dp, 10.dp, 10.dp, 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = files,
                            key = { it.id },
                            contentType = { it.fileType }
                        ) { item ->
                            FileGridCard(
                                item = item,
                                showThumbnails = showThumbnails,
                                showExtensions = showExtensions,
                                isSelected = selectedItemIds.contains(item.id),
                                isSelectionMode = isMultiSelecting,
                                onClick = {
                                    if (isMultiSelecting) {
                                        onItemLongClick(item)
                                    } else {
                                        handleItemClick(item, onItemClick, onInspectApk, onOpenZip, onOpenCodeEditor, onOpenMedia)
                                    }
                                },
                                onLongClick = { onItemLongClick(item) },
                                onToggleFavorite = { onToggleFavorite(item) },
                                tempZipSourcePath = tempZipSourcePath,
                                onExtractIndividual = onExtractIndividual,
                                onUninstallApp = onUninstallApp,
                                onOpenAppSettings = onOpenAppSettings
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = files,
                            key = { it.id },
                            contentType = { it.fileType }
                        ) { item ->
                            FileListItem(
                                item = item,
                                showThumbnails = showThumbnails,
                                showExtensions = showExtensions,
                                isSelected = selectedItemIds.contains(item.id),
                                isSelectionMode = isMultiSelecting,
                                onClick = {
                                    if (isMultiSelecting) {
                                        onItemLongClick(item)
                                    } else {
                                        handleItemClick(item, onItemClick, onInspectApk, onOpenZip, onOpenCodeEditor, onOpenMedia)
                                    }
                                },
                                onLongClick = { onItemLongClick(item) },
                                onToggleFavorite = { onToggleFavorite(item) },
                                onRename = { onRenameItem(item) },
                                onShareItem = { onShareItem(item) },
                                tempZipSourcePath = tempZipSourcePath,
                                onExtractIndividual = onExtractIndividual,
                                onUninstallApp = onUninstallApp,
                                onOpenAppSettings = onOpenAppSettings
                            )
                        }
                    }
                }
            }
        }

        // Unified Circular FAB & Morphing Selection Options Bar
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            // Fab menu items when creating new file/folder
            AnimatedVisibility(
                visible = showFabMenu && !isMultiSelecting,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            showFabMenu = false
                            onCreateFile()
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.NoteAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Novo Arquivo",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    SmallFloatingActionButton(
                        onClick = {
                            showFabMenu = false
                            onCreateFolder()
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Nova Pasta",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val primaryColor = MaterialTheme.colorScheme.primary
            val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

            val showClipboardActions = clipboardItems.isNotEmpty() && !isMultiSelecting

            // Animações sincronizadas para o botão de Colar (emerge de trás do círculo +)
            val pasteOffsetX by animateDpAsState(
                targetValue = if (showClipboardActions) (-68).dp else (-6).dp,
                animationSpec = if (showClipboardActions) {
                    spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                },
                label = "pasteOffsetX"
            )

            val pasteScale by animateFloatAsState(
                targetValue = if (showClipboardActions) 1f else 0.35f,
                animationSpec = if (showClipboardActions) {
                    spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                } else {
                    tween(durationMillis = 150, easing = FastOutLinearInEasing)
                },
                label = "pasteScale"
            )

            val pasteAlpha by animateFloatAsState(
                targetValue = if (showClipboardActions) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (showClipboardActions) 180 else 120,
                    delayMillis = if (showClipboardActions) 120 else 0
                ),
                label = "pasteAlpha"
            )

            // Animações sincronizadas para o botão Cancelar X (emerge de trás do botão Colar)
            val cancelOffsetX by animateDpAsState(
                targetValue = if (showClipboardActions) (-122).dp else (-68).dp,
                animationSpec = if (showClipboardActions) {
                    spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                },
                label = "cancelOffsetX"
            )

            val cancelScale by animateFloatAsState(
                targetValue = if (showClipboardActions) 1f else 0.25f,
                animationSpec = if (showClipboardActions) {
                    spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                } else {
                    tween(durationMillis = 120, easing = FastOutLinearInEasing)
                },
                label = "cancelScale"
            )

            val cancelAlpha by animateFloatAsState(
                targetValue = if (showClipboardActions) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (showClipboardActions) 200 else 140,
                    delayMillis = if (showClipboardActions) 320 else 0
                ),
                label = "cancelAlpha"
            )

            // Fundo sólido um tom mais claro que o círculo primário
            val toolbarSolidBgColor = if (isDarkTheme) {
                primaryColor.copy(alpha = 0.70f).compositeOver(MaterialTheme.colorScheme.surface)
            } else {
                primaryColor.copy(alpha = 0.80f).compositeOver(Color.White)
            }

            val fabBorder = if (isMultiSelecting) {
                androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.25f)
                )
            } else null

            // Container em camadas: Cancelar emerge de trás de Colar, Colar emerge de trás do Círculo +
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.wrapContentSize()
            ) {
                // 1. Botão Cancelar (X) - zIndex 0, posicionado e animado de trás do botão Colar
                if (cancelAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .zIndex(0f)
                            .offset { IntOffset(x = cancelOffsetX.roundToPx(), y = 0) }
                            .graphicsLayer {
                                scaleX = cancelScale
                                scaleY = cancelScale
                                alpha = cancelAlpha
                            }
                    ) {
                        Surface(
                            onClick = onClearClipboard,
                            enabled = showClipboardActions,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                            ),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancelar Colar",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // 2. Botão Colar - zIndex 1, posicionado e animado de trás do círculo (+)
                if (pasteAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .zIndex(1f)
                            .offset { IntOffset(x = pasteOffsetX.roundToPx(), y = 0) }
                            .graphicsLayer {
                                scaleX = pasteScale
                                scaleY = pasteScale
                                alpha = pasteAlpha
                            }
                    ) {
                        Box {
                            Surface(
                                onClick = onPasteClipboard,
                                enabled = showClipboardActions,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 6.dp,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(getVibrantBadgeGradient(primaryColor)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPasteGo,
                                        contentDescription = "Colar arquivos",
                                        tint = primaryColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Badge com contador de itens copiados/recortados
                            Badge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp),
                                containerColor = primaryColor,
                                contentColor = onPrimaryColor
                            ) {
                                Text(
                                    text = "${clipboardItems.size}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 3. Barra de Seleção com Morphing e Círculo (+) Principal - zIndex 2
                Box(
                    modifier = Modifier.zIndex(2f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isMultiSelecting) toolbarSolidBgColor else Color.Transparent,
                        shadowElevation = if (isMultiSelecting) 6.dp else 0.dp,
                        border = fabBorder
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            // Actions sliding out from behind the circular button to the left
                            AnimatedVisibility(
                                visible = isMultiSelecting,
                                modifier = Modifier.weight(1f, fill = false),
                                enter = expandHorizontally(
                                    expandFrom = Alignment.End,
                                    animationSpec = spring(
                                        dampingRatio = 0.85f,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) + fadeIn(
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                ),
                                exit = shrinkHorizontally(
                                    shrinkTowards = Alignment.End,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) + fadeOut(
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                )
                            ) {
                                val actionTint = Color.White
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(start = 12.dp, end = 4.dp)
                                ) {
                                    // 1. Renomear
                                    IconButton(
                                        onClick = { if (selectedItems.isNotEmpty()) onRenameItem(selectedItems.first()) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Renomear", tint = actionTint, modifier = Modifier.size(20.dp))
                                    }
                                    // 2. Copiar
                                    IconButton(onClick = onCopySelected, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = actionTint, modifier = Modifier.size(20.dp))
                                    }
                                    // 3. Recortar
                                    IconButton(onClick = onCutSelected, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.ContentCut, contentDescription = "Recortar", tint = actionTint, modifier = Modifier.size(20.dp))
                                    }
                                    // 4. Compactar
                                    IconButton(onClick = onCompressSelectedToZip, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.FolderZip, contentDescription = "Compactar", tint = actionTint, modifier = Modifier.size(20.dp))
                                    }
                                    // 5. Compartilhar
                                    IconButton(onClick = onShareSelected, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = actionTint, modifier = Modifier.size(20.dp))
                                    }
                                    // 6. Multi seleção (Selecionar Tudo)
                                    IconButton(onClick = onSelectAll, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.SelectAll, contentDescription = "Multi seleção", tint = actionTint, modifier = Modifier.size(20.dp))
                                    }
                                    // 7. Excluir
                                    IconButton(onClick = onDeleteSelectedToTrash, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                            }

                            // Main Circular (+) Button com gradiente vibrante e sem manchas de sombra transparente
                            Surface(
                                shape = CircleShape,
                                color = primaryColor,
                                shadowElevation = if (isMultiSelecting) 0.dp else 6.dp,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clickable {
                                        if (isMultiSelecting) {
                                            onClearSelection()
                                        } else {
                                            showFabMenu = !showFabMenu
                                        }
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(getVibrantLinearGradient(primaryColor, 0.28f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = if (isMultiSelecting) "Fechar Seleção" else "Criar",
                                        tint = onPrimaryColor,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .graphicsLayer(rotationZ = fabRotation)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun handleItemClick(
    item: FileItem,
    onItemClick: (FileItem) -> Unit,
    onInspectApk: (FileItem) -> Unit,
    onOpenZip: (FileItem) -> Unit,
    onOpenCodeEditor: (FileItem) -> Unit,
    onOpenMedia: (FileItem) -> Unit
) {
    when {
        item.isDirectory -> onItemClick(item)
        item.fileType == FileType.APK -> onInspectApk(item)
        item.fileType == FileType.ARCHIVE -> onOpenZip(item)
        item.fileType == FileType.CODE || item.fileType == FileType.DOCUMENT -> onOpenCodeEditor(item)
        item.fileType == FileType.IMAGE || item.fileType == FileType.VIDEO || item.fileType == FileType.AUDIO -> onOpenMedia(item)
        else -> onItemClick(item)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCard(
    item: FileItem,
    showThumbnails: Boolean = true,
    showExtensions: Boolean = true,
    isSelected: Boolean,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    tempZipSourcePath: String? = null,
    onExtractIndividual: ((FileItem) -> Unit)? = null,
    onUninstallApp: ((String) -> Unit)? = null,
    onOpenAppSettings: ((String) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val categoryColor = item.fileType.getCategoryColor()
    val isMedia = item.fileType == FileType.IMAGE || item.fileType == FileType.VIDEO
    val displayName = remember(item.name, item.isDirectory, showExtensions) {
        if (showExtensions || item.isDirectory || !item.name.contains('.')) item.name else item.name.substringBeforeLast('.')
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (isSelected) 2.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isMedia && showThumbnails) {
                // Media full-bleed presentation
                FileThumbnailImage(
                    item = item,
                    showThumbnails = showThumbnails,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 26.dp
                )

                // Bottom text overlay with gradient scrim for high legibility & fast rendering
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    offset = Offset(0f, 1f),
                                    blurRadius = 3f
                                )
                            ),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            color = Color.White
                        )
                        Text(
                            text = formatFileSize(item.size),
                            style = MaterialTheme.typography.bodySmall.copy(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black,
                                    offset = Offset(0f, 1f),
                                    blurRadius = 3f
                                )
                            ),
                            fontWeight = FontWeight.Normal,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            textAlign = TextAlign.Center,
                            color = Color.White.copy(alpha = 0.95f)
                        )
                    }
                }
            } else {
                // Standard folder/file presentation
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    val isFolder = item.fileType == FileType.FOLDER
                    val badgeBrush = remember(categoryColor, isFolder) {
                        if (isFolder) null else getVibrantBadgeGradient(categoryColor)
                    }
                    val folderBadgeBorder = remember(categoryColor, isFolder) {
                        androidx.compose.foundation.BorderStroke(1.dp, categoryColor.copy(alpha = if (isFolder) 0.35f else 0.25f))
                    }
                    val formattedSubtext = remember(item.size, item.childCount, item.isDirectory) {
                        if (item.isDirectory) (if (item.childCount == 1) "1 item" else "${item.childCount} itens") else formatFileSize(item.size)
                    }

                    // Category Icon / Thumbnail Badge com leve gradiente
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                        border = folderBadgeBorder,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .size(44.dp)
                            .then(
                                if (badgeBrush != null) Modifier.background(badgeBrush, RoundedCornerShape(14.dp)) else Modifier
                            )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            FileThumbnailImage(
                                item = item,
                                showThumbnails = showThumbnails,
                                modifier = Modifier.fillMaxSize(),
                                iconSize = 24.dp,
                                overrideIconTint = null
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp)
                    )

                    Text(
                        text = formattedSubtext,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Controls overlay: Checkbox with slide animation in selection mode
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shadowElevation = 2.dp
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            if (tempZipSourcePath != null && onExtractIndividual != null) {
                IconButton(
                    onClick = { onExtractIndividual(item) },
                    modifier = Modifier
                        .padding(4.dp)
                        .size(28.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Default.Unarchive,
                        contentDescription = "Extrair individualmente",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else if (item.appCategory == "USER") {
                IconButton(
                    onClick = { item.packageName?.let { onUninstallApp?.invoke(it) } },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Excluir Aplicativo",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else if (item.appCategory != "USER" && item.appCategory != "SYSTEM") {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Icon(
                        if (item.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorito",
                        tint = if (item.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    item: FileItem,
    showThumbnails: Boolean = true,
    showExtensions: Boolean = true,
    isSelected: Boolean,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onShareItem: () -> Unit = {},
    tempZipSourcePath: String? = null,
    onExtractIndividual: ((FileItem) -> Unit)? = null,
    onUninstallApp: ((String) -> Unit)? = null,
    onOpenAppSettings: ((String) -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val categoryColor = item.fileType.getCategoryColor()
    val displayName = remember(item.name, item.isDirectory, showExtensions) {
        if (showExtensions || item.isDirectory || !item.name.contains('.')) item.name else item.name.substringBeforeLast('.')
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RectangleShape,
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Caixa de seleção com animação de slide na frente do arquivo
                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + expandHorizontally() + fadeIn(),
                    exit = slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + shrinkHorizontally() + fadeOut()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onClick() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                checkmarkColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                val isFolder = item.fileType == FileType.FOLDER
                val badgeBrush = remember(categoryColor, isFolder) {
                    if (isFolder) null else getVibrantBadgeGradient(categoryColor)
                }
                val folderBadgeBorder = remember(categoryColor, isFolder) {
                    androidx.compose.foundation.BorderStroke(1.dp, categoryColor.copy(alpha = if (isFolder) 0.35f else 0.25f))
                }
                val formattedSize = remember(item.size, item.childCount, item.isDirectory) {
                    if (item.isDirectory) (if (item.childCount == 1) "1 item" else "${item.childCount} itens") else formatFileSize(item.size)
                }
                val formattedDate = remember(item.lastModified, item.versionName) {
                    if (item.versionName != null) "v${item.versionName}" else formatDate(item.lastModified)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    border = folderBadgeBorder,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .then(
                            if (badgeBrush != null) Modifier.background(badgeBrush, RoundedCornerShape(12.dp)) else Modifier
                        )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        FileThumbnailImage(
                            item = item,
                            showThumbnails = showThumbnails,
                            modifier = Modifier.fillMaxSize(),
                            iconSize = 24.dp,
                            overrideIconTint = null
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        lineHeight = 20.sp,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.fileType == FileType.APK && !item.appCategory.isNullOrEmpty()) {
                            val (badgeText, badgeBg) = when (item.appCategory) {
                                "USER" -> "Usuário" to MaterialTheme.colorScheme.primary
                                "SYSTEM" -> "Sistema" to MaterialTheme.colorScheme.secondary
                                else -> "Instalador" to MaterialTheme.colorScheme.tertiary
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = badgeBg.copy(alpha = 0.15f),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeBg,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = formattedSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(" • ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tempZipSourcePath != null && onExtractIndividual != null) {
                        IconButton(onClick = { onExtractIndividual(item) }) {
                            Icon(
                                Icons.Default.Unarchive,
                                contentDescription = "Extrair individualmente",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else if (item.appCategory == "USER") {
                        IconButton(onClick = { item.packageName?.let { onUninstallApp?.invoke(it) } }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Excluir Aplicativo",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else if (item.appCategory != "SYSTEM") {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                if (item.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorito",
                                tint = if (item.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(start = if (isSelectionMode) 56.dp else 74.dp, end = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    }
}

@Composable
fun MultiSelectionBottomBar(
    selectedCount: Int,
    onRename: () -> Unit = {},
    onCopy: () -> Unit,
    onCut: () -> Unit = {},
    onCompress: () -> Unit,
    onShare: () -> Unit = {},
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Cancelar")
                }
                Text(
                    text = "$selectedCount selec.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "Renomear", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onCut) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Recortar", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onCompress) {
                    Icon(Icons.Default.FolderZip, contentDescription = "Compactar", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Multi seleção", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun EmptySearchState(
    searchQuery: String,
    isGlobalSearch: Boolean,
    onClearSearch: () -> Unit,
    onToggleGlobalSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Nenhum arquivo encontrado",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Não encontramos nenhum arquivo para \"$searchQuery\" em ${if (isGlobalSearch) "todo o armazenamento" else "esta pasta"}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClearSearch) {
                Text("Limpar Pesquisa")
            }
            if (!isGlobalSearch) {
                Button(onClick = onToggleGlobalSearch) {
                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buscar em tudo")
                }
            }
        }
    }
}

@Composable
fun EmptyFolderState(onCreateFolder: () -> Unit) {
    val badgeBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    val iconTint = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = iconTint
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Esta pasta está vazia",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Adicione arquivos ou crie uma nova pasta para começar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onCreateFolder,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Criar Pasta")
        }
    }
}

private fun getFileIcon(item: FileItem) = when (item.fileType) {
    FileType.FOLDER -> Icons.Default.Folder
    FileType.IMAGE -> Icons.Default.Image
    FileType.VIDEO -> Icons.Default.Movie
    FileType.AUDIO -> Icons.Default.MusicNote
    FileType.DOCUMENT -> Icons.Default.Description
    FileType.APK -> Icons.Default.Android
    FileType.ARCHIVE -> Icons.Default.FolderZip
    FileType.CODE -> Icons.Default.Code
    FileType.TEMP_RESIDUAL -> Icons.Default.DeleteSweep
    FileType.OTHER -> Icons.Default.InsertDriveFile
}

private val dateFormatThreadLocal = ThreadLocal.withInitial {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "-"
    return dateFormatThreadLocal.get()?.format(Date(timestamp)) ?: "-"
}

@Composable
fun FileThumbnailImage(
    item: FileItem,
    showThumbnails: Boolean = true,
    modifier: Modifier = Modifier,
    iconSize: Dp = 26.dp,
    overrideIconTint: Color? = null
) {
    val categoryColor = item.fileType.getCategoryColor()
    val finalIconTint = overrideIconTint ?: categoryColor
    val context = LocalContext.current

    if (!showThumbnails) {
        Icon(
            getFileIcon(item),
            contentDescription = null,
            tint = finalIconTint,
            modifier = Modifier.size(iconSize)
        )
        return
    }

    when (item.fileType) {
        FileType.APK -> {
            AppIconImage(
                packageName = item.packageName,
                apkPath = item.path,
                modifier = modifier
            )
        }
        FileType.IMAGE -> {
            var loadFailed by remember(item.path) { mutableStateOf(false) }

            if (!loadFailed) {
                val imageRequest = remember(item.path, item.lastModified) {
                    ImageRequest.Builder(context)
                        .data(java.io.File(item.path))
                        .size(180, 180)
                        .precision(coil.size.Precision.INEXACT)
                        .memoryCacheKey("${item.path}_${item.lastModified}")
                        .diskCacheKey("${item.path}_${item.lastModified}")
                        .crossfade(false)
                        .build()
                }

                Box(modifier = modifier, contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        onError = { loadFailed = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            } else {
                Icon(
                    getFileIcon(item),
                    contentDescription = null,
                    tint = finalIconTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        FileType.VIDEO -> {
            var loadFailed by remember(item.path) { mutableStateOf(false) }

            if (!loadFailed) {
                val imageRequest = remember(item.path, item.lastModified) {
                    ImageRequest.Builder(context)
                        .data(java.io.File(item.path))
                        .size(180, 180)
                        .precision(coil.size.Precision.INEXACT)
                        .videoFrameMillis(1000)
                        .memoryCacheKey("video_${item.path}_${item.lastModified}")
                        .diskCacheKey("video_${item.path}_${item.lastModified}")
                        .crossfade(false)
                        .build()
                }

                Box(
                    modifier = modifier.clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        onError = { loadFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = modifier,
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        getFileIcon(item),
                        contentDescription = null,
                        tint = finalIconTint,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
        else -> {
            Icon(
                getFileIcon(item),
                contentDescription = null,
                tint = finalIconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun OutlinedGridText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight = FontWeight.SemiBold,
    textColor: Color = Color.White,
    outlineColor: Color = Color.Black.copy(alpha = 0.95f),
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign = TextAlign.Center,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val outlineOffsets = remember {
            listOf(
                Offset(-1.2f, -1.2f),
                Offset(1.2f, -1.2f),
                Offset(-1.2f, 1.2f),
                Offset(1.2f, 1.2f),
                Offset(0f, 1.5f)
            )
        }
        for (offset in outlineOffsets) {
            Text(
                text = text,
                style = style.copy(
                    shadow = Shadow(
                        color = outlineColor,
                        offset = offset,
                        blurRadius = 3f
                    )
                ),
                fontWeight = fontWeight,
                fontSize = fontSize,
                maxLines = maxLines,
                overflow = overflow,
                textAlign = textAlign,
                color = outlineColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = text,
            style = style.copy(
                shadow = Shadow(
                    color = Color.Black,
                    offset = Offset(0f, 1f),
                    blurRadius = 3f
                )
            ),
            fontWeight = fontWeight,
            fontSize = fontSize,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            color = textColor,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

