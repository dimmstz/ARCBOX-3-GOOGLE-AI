package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.db.TrashEntity
import com.example.util.formatFileSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArcboxTrashBinModal(
    trashItems: List<TrashEntity>,
    onRestoreItem: (TrashEntity) -> Unit,
    onRestoreSelected: (Set<TrashEntity>) -> Unit,
    onDeletePermanently: (TrashEntity) -> Unit,
    onDeleteSelected: (Set<TrashEntity>) -> Unit,
    onEmptyTrash: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var selectedItems by remember { mutableStateOf(setOf<TrashEntity>()) }
    val isMultiSelecting = selectedItems.isNotEmpty()
    var isGridView by remember { mutableStateOf(false) }

    var itemToDelete by remember { mutableStateOf<TrashEntity?>(null) }
    var showConfirmDeleteSelected by remember { mutableStateOf(false) }
    var showConfirmEmptyTrash by remember { mutableStateOf(false) }

    BackHandler(enabled = isMultiSelecting) {
        selectedItems = emptySet()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar
                TopAppBar(
                    title = {
                        Column {
                            if (isMultiSelecting) {
                                Text(
                                    text = "${selectedItems.size} selecionados",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "Lixeira Arcbox",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${trashItems.size} itens • Limpeza automática em 30 dias",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (isMultiSelecting) {
                            IconButton(onClick = { selectedItems = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar seleção")
                            }
                        } else {
                            IconButton(onClick = onClose) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                            }
                        }
                    },
                    actions = {
                        // Toggle between List and Grid/Miniaturas
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "Visualizar em lista" else "Visualizar em miniaturas",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isMultiSelecting) {
                            IconButton(onClick = {
                                onRestoreSelected(selectedItems)
                                selectedItems = emptySet()
                            }) {
                                Icon(
                                    Icons.Default.Restore,
                                    contentDescription = "Restaurar selecionados",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = {
                                showConfirmDeleteSelected = true
                            }) {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = "Excluir permanentemente selecionados",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            IconButton(onClick = {
                                if (selectedItems.size == trashItems.size) {
                                    selectedItems = emptySet()
                                } else {
                                    selectedItems = trashItems.toSet()
                                }
                            }) {
                                Icon(
                                    Icons.Default.SelectAll,
                                    contentDescription = "Selecionar todos"
                                )
                            }
                        } else {
                            if (trashItems.isNotEmpty()) {
                                TextButton(onClick = { showConfirmEmptyTrash = true }) {
                                    Text(
                                        text = "Esvaziar",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (trashItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            ModernEmptyTrashHero(
                                size = 110.dp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "A Lixeira está vazia",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Os arquivos excluídos permanecerão aqui por 30 dias antes de serem permanentemente removidos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                } else if (isGridView) {
                    // Miniaturas / Grid View
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(trashItems, key = { it.id }) { item ->
                            val isSelected = selectedItems.contains(item)
                            val isMedia = isMediaTrashItem(item)
                            val isVideo = isVideoTrashItem(item)
                            val trashFile = remember(item.trashTempPath) { File(item.trashTempPath) }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                border = if (isSelected) {
                                    androidx.compose.foundation.BorderStroke(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    androidx.compose.foundation.BorderStroke(
                                        0.5.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(138.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (isMultiSelecting) {
                                                selectedItems = if (isSelected) selectedItems - item else selectedItems + item
                                            } else {
                                                selectedItems = setOf(item)
                                            }
                                        },
                                        onLongClick = {
                                            selectedItems = if (isSelected) selectedItems - item else selectedItems + item
                                        }
                                    )
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (isMedia && trashFile.exists()) {
                                        val imageRequest = remember(trashFile.path, item.deletedTimestamp) {
                                            ImageRequest.Builder(context)
                                                .data(trashFile)
                                                .size(200, 200)
                                                .precision(coil.size.Precision.INEXACT)
                                                .crossfade(false)
                                                .build()
                                        }
                                        AsyncImage(
                                            model = imageRequest,
                                            contentDescription = item.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        // Bottom gradient scrim
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, Color(0xCC000000))
                                                    )
                                                )
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = item.displayName,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = formatFileSize(item.size),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        if (isVideo) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0x77000000)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = "Vídeo",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        // Folder or non-media file
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (item.isDirectory) {
                                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                                        } else {
                                                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (item.isDirectory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
                                                    contentDescription = null,
                                                    tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = item.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                lineHeight = 14.sp
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = formatFileSize(item.size),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    // Selection badge indicator
                                    if (isMultiSelecting || isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary else Color(0x88000000)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = if (isSelected) "Selecionado" else "Não selecionado",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Lista / List View
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(trashItems, key = { it.id }) { item ->
                            val isSelected = selectedItems.contains(item)
                            val isMedia = isMediaTrashItem(item)
                            val trashFile = remember(item.trashTempPath) { File(item.trashTempPath) }

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                },
                                border = if (isSelected) {
                                    androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (isMultiSelecting) {
                                                selectedItems = if (isSelected) {
                                                    selectedItems - item
                                                } else {
                                                    selectedItems + item
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            selectedItems = if (isSelected) {
                                                selectedItems - item
                                            } else {
                                                selectedItems + item
                                            }
                                        }
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    // Circular Selection Checkbox / Icon
                                    if (isMultiSelecting) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = if (isSelected) "Selecionado" else "Não selecionado",
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .padding(end = 12.dp)
                                                .size(22.dp)
                                        )
                                    } else {
                                        if (isMedia && trashFile.exists()) {
                                            val imageRequest = remember(trashFile.path, item.deletedTimestamp) {
                                                ImageRequest.Builder(context)
                                                    .data(trashFile)
                                                    .size(120, 120)
                                                    .precision(coil.size.Precision.INEXACT)
                                                    .crossfade(false)
                                                    .build()
                                            }
                                            AsyncImage(
                                                model = imageRequest,
                                                contentDescription = item.displayName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (item.isDirectory) {
                                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                                        } else {
                                                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (item.isDirectory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
                                                    contentDescription = null,
                                                    tint = if (item.isDirectory) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.tertiary
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Excluído: ${formatTimestamp(item.deletedTimestamp)} • ${formatFileSize(item.size)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (!isMultiSelecting) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { onRestoreItem(item) }) {
                                                Icon(
                                                    Icons.Default.Restore,
                                                    contentDescription = "Restaurar",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(onClick = { itemToDelete = item }) {
                                                Icon(
                                                    Icons.Default.DeleteForever,
                                                    contentDescription = "Excluir Definitivamente",
                                                    tint = MaterialTheme.colorScheme.error
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
        }
    }

    // Confirmation dialogs for deletion
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            title = { Text("Excluir permanentemente?") },
            text = { Text("O item \"${item.displayName}\" será excluído permanentemente da Lixeira. Esta ação não poderá ser desfeita.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePermanently(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showConfirmDeleteSelected && selectedItems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteSelected = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            title = { Text("Excluir permanentemente?") },
            text = { Text("Deseja excluir permanentemente os ${selectedItems.size} itens selecionados? Esta ação não poderá ser desfeita.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSelected(selectedItems)
                        selectedItems = emptySet()
                        showConfirmDeleteSelected = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteSelected = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showConfirmEmptyTrash) {
        AlertDialog(
            onDismissRequest = { showConfirmEmptyTrash = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            title = { Text("Esvaziar Lixeira?") },
            text = { Text("Todos os ${trashItems.size} itens da Lixeira serão excluídos permanentemente. Esta ação não poderá ser desfeita.") },
            confirmButton = {
                Button(
                    onClick = {
                        onEmptyTrash()
                        showConfirmEmptyTrash = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Esvaziar Lixeira", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmEmptyTrash = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

private val trashDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

private fun formatTimestamp(time: Long): String {
    return synchronized(trashDateFormat) {
        trashDateFormat.format(Date(time))
    }
}

private fun isMediaTrashItem(item: TrashEntity): Boolean {
    if (item.isDirectory) return false
    val ext = item.displayName.substringAfterLast('.', "").lowercase()
    return ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "mp4", "mkv", "mov", "webm", "3gp")
}

private fun isVideoTrashItem(item: TrashEntity): Boolean {
    if (item.isDirectory) return false
    val ext = item.displayName.substringAfterLast('.', "").lowercase()
    return ext in setOf("mp4", "mkv", "mov", "webm", "3gp", "avi", "flv")
}
