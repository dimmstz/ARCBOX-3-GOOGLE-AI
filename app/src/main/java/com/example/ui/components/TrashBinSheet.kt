package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.db.TrashEntity
import com.example.util.formatFileSize
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
    var selectedItems by remember { mutableStateOf(setOf<TrashEntity>()) }
    val isMultiSelecting = selectedItems.isNotEmpty()

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
                                        text = "Esvaziar Lixeira",
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
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(trashItems, key = { it.id }) { item ->
                            val isSelected = selectedItems.contains(item)
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
                                                imageVector = if (item.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
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

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
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
