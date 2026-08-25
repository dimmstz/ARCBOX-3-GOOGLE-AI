package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
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
    onToggleGlobalSearch: () -> Unit = {}
) {
    var showFabMenu by remember { mutableStateOf(false) }
    val isMultiSelecting = selectedItems.isNotEmpty()

    // Smooth rotation angle for + into X (135 degrees)
    val fabRotation by animateFloatAsState(
        targetValue = if (isMultiSelecting || showFabMenu) 135f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "FabRotation"
    )

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
                label = "ViewModeAnimation"
            ) { mode ->
                if (mode == ViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(14.dp, 12.dp, 14.dp, 104.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(files, key = { it.id }) { item ->
                            FileGridCard(
                                item = item,
                                showThumbnails = showThumbnails,
                                isSelected = selectedItems.any { it.id == item.id || it.path == item.path },
                                onClick = {
                                    if (isMultiSelecting) {
                                        onItemLongClick(item)
                                    } else {
                                        handleItemClick(item, onItemClick, onInspectApk, onOpenZip, onOpenCodeEditor, onOpenMedia)
                                    }
                                },
                                onLongClick = { onItemLongClick(item) },
                                onToggleFavorite = { onToggleFavorite(item) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(14.dp, 12.dp, 14.dp, 104.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(files, key = { it.id }) { item ->
                            FileListItem(
                                item = item,
                                showThumbnails = showThumbnails,
                                isSelected = selectedItems.any { it.id == item.id || it.path == item.path },
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
                                onShareItem = { onShareItem(item) }
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

            // Row holding both the Cancel, Paste buttons (if copy/cut active) and the main FAB / morphing selection bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // Cancel/Close Paste Button (Appears to the LEFT of the Paste FAB button)
                AnimatedVisibility(
                    visible = clipboardItems.isNotEmpty() && !isMultiSelecting,
                    enter = fadeIn() + slideInHorizontally { -it / 2 } + scaleIn(),
                    exit = fadeOut() + slideOutHorizontally { -it / 2 } + scaleOut()
                ) {
                    IconButton(
                        onClick = onClearClipboard,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancelar Colar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (clipboardItems.isNotEmpty() && !isMultiSelecting) {
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Paste FAB button (Appears directly to the left of the '+' FAB button)
                AnimatedVisibility(
                    visible = clipboardItems.isNotEmpty() && !isMultiSelecting,
                    enter = fadeIn() + slideInHorizontally { -it / 2 } + scaleIn(),
                    exit = fadeOut() + slideOutHorizontally { -it / 2 } + scaleOut()
                ) {
                    Box {
                        Surface(
                            onClick = onPasteClipboard,
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
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPasteGo,
                                    contentDescription = "Colar arquivos",
                                    tint = MaterialTheme.colorScheme.primary, // Cor do tema utilizado (cor do +)
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Badge with item count
                        Badge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ) {
                            Text(
                                text = "${clipboardItems.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (clipboardItems.isNotEmpty() && !isMultiSelecting) {
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Morphing Bottom Action Container
                Surface(
                    shape = CircleShape,
                    color = if (isMultiSelecting) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
                    shadowElevation = 8.dp,
                    border = if (isMultiSelecting) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) else null
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
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) + fadeIn(
                                animationSpec = spring(stiffness = Spring.StiffnessLow)
                            ),
                            exit = shrinkHorizontally(
                                shrinkTowards = Alignment.End,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) + fadeOut(
                                animationSpec = spring(stiffness = Spring.StiffnessLow)
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(start = 6.dp, end = 2.dp)
                            ) {
                                IconButton(onClick = onCopySelected, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = onCutSelected, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.ContentCut, contentDescription = "Recortar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = onMoveSelected, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.DriveFileMove, contentDescription = "Mover", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { if (selectedItems.isNotEmpty()) onRenameItem(selectedItems.first()) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Renomear", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = onCompressSelectedToZip, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.FolderZip, contentDescription = "ZIP", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = onShareSelected, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Share, contentDescription = "Compartilhar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = onDeleteSelectedToTrash, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = onSelectAll, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.SelectAll, contentDescription = "Tudo", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(2.dp))
                            }
                        }

                        // Main Circular (+) Button
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isMultiSelecting) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary
                                )
                                .clickable {
                                    if (isMultiSelecting) {
                                        onClearSelection()
                                    } else {
                                        showFabMenu = !showFabMenu
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = if (isMultiSelecting) "Fechar Seleção" else "Criar",
                                tint = if (isMultiSelecting) MaterialTheme.colorScheme.primary else Color.White,
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
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val categoryColor = item.fileType.getCategoryColor()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 3.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.95f)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
                shape = RoundedCornerShape(20.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            // Checkbox top end if selected
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopEnd)
                )
            } else if (item.appCategory != "USER" && item.appCategory != "SYSTEM") {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(22.dp)
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                // Category Icon / Thumbnail Badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(categoryColor.copy(alpha = 0.24f), categoryColor.copy(alpha = 0.07f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    FileThumbnailImage(
                        item = item,
                        showThumbnails = showThumbnails,
                        modifier = Modifier.fillMaxSize(),
                        iconSize = 26.dp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (item.isDirectory) "${item.childCount} itens" else formatFileSize(item.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    item: FileItem,
    showThumbnails: Boolean = true,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onShareItem: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val categoryColor = item.fileType.getCategoryColor()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 3.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(categoryColor.copy(alpha = 0.22f), categoryColor.copy(alpha = 0.06f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                FileThumbnailImage(
                    item = item,
                    showThumbnails = showThumbnails,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 24.dp
                )
            }

            Spacer(modifier = Modifier.width(13.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    maxLines = 1,
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
                        text = if (item.isDirectory) "${item.childCount} itens" else formatFileSize(item.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(" • ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (item.versionName != null) "v${item.versionName}" else formatDate(item.lastModified),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            if (item.appCategory != "USER" && item.appCategory != "SYSTEM") {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (item.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorito",
                        tint = if (item.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MultiSelectionBottomBar(
    selectedCount: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onCompress: () -> Unit,
    onSelectAll: () -> Unit,
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
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onMove) {
                    Icon(Icons.Default.DriveFileMove, contentDescription = "Mover", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onCompress) {
                    Icon(Icons.Default.FolderZip, contentDescription = "Compactar ZIP", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir para Lixeira", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Selecionar Tudo")
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
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
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
    FileType.OTHER -> Icons.Default.InsertDriveFile
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "-"
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun FileThumbnailImage(
    item: FileItem,
    showThumbnails: Boolean = true,
    modifier: Modifier = Modifier,
    iconSize: Dp = 26.dp
) {
    val categoryColor = item.fileType.getCategoryColor()
    val context = LocalContext.current

    if (!showThumbnails) {
        Icon(
            getFileIcon(item),
            contentDescription = null,
            tint = categoryColor,
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
                val file = remember(item.path) { java.io.File(item.path) }
                val imageRequest = remember(item.path) {
                    ImageRequest.Builder(context)
                        .data(if (file.exists() && file.length() > 0) file else item.path)
                        .crossfade(true)
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
                    tint = categoryColor,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        FileType.VIDEO -> {
            var loadFailed by remember(item.path) { mutableStateOf(false) }

            if (!loadFailed) {
                val file = remember(item.path) { java.io.File(item.path) }
                val imageRequest = remember(item.path) {
                    ImageRequest.Builder(context)
                        .data(if (file.exists() && file.length() > 0) file else item.path)
                        .decoderFactory(VideoFrameDecoder.Factory())
                        .crossfade(true)
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
                        tint = categoryColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
        else -> {
            Icon(
                getFileIcon(item),
                contentDescription = null,
                tint = categoryColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
