package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.FileType
import com.example.data.models.SortOption
import com.example.data.models.SortOrder
import com.example.data.models.StorageVolume
import com.example.data.models.ViewMode
import com.example.ui.theme.ArcboxBlue
import com.example.ui.theme.ArcboxIndigo
import com.example.util.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcboxTopBar(
    storageVolumes: List<StorageVolume>,
    selectedVolume: StorageVolume?,
    onVolumeSelected: (StorageVolume) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filterCategory: FileType?,
    onFilterCategorySelected: (FileType?) -> Unit,
    sortOption: SortOption,
    sortOrder: SortOrder,
    onSortOptionSelected: (SortOption) -> Unit,
    viewMode: ViewMode,
    onToggleViewMode: () -> Unit,
    onOpenStorageDashboard: () -> Unit,
    onOpenTrashBin: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    trashCount: Int = 0,
    isGlobalSearch: Boolean = false,
    onToggleGlobalSearch: () -> Unit = {},
    searchHistory: List<String> = emptyList()
) {
    var isSearchActive by remember(searchQuery) { mutableStateOf(searchQuery.isNotEmpty()) }
    var showVolumeDropdown by remember { mutableStateOf(false) }
    var showQuickMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            onSearchQueryChange("")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fechar busca")
                        }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("Buscar arquivos ou extensões") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                                    }
                                }
                            }
                        )
                    } else {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Abrir menu")
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showVolumeDropdown = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Brush.linearGradient(listOf(ArcboxBlue, ArcboxIndigo))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.FolderSpecial,
                                    contentDescription = "Arcbox",
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Arcbox",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = selectedVolume?.name ?: "Armazenamento",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Trocar armazenamento",
                                        modifier = Modifier.size(17.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                        Box {
                            IconButton(onClick = { showQuickMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Mais ações")
                            }
                            DropdownMenu(
                                expanded = showQuickMenu,
                                onDismissRequest = { showQuickMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (viewMode == ViewMode.GRID) "Mudar para lista" else "Mudar para grade") },
                                    leadingIcon = { Icon(if (viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView, contentDescription = null) },
                                    onClick = {
                                        onToggleViewMode()
                                        showQuickMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Análise de armazenamento") },
                                    leadingIcon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                                    onClick = {
                                        onOpenStorageDashboard()
                                        showQuickMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (trashCount > 0) "Lixeira · $trashCount" else "Lixeira") },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                    onClick = {
                                        onOpenTrashBin()
                                        showQuickMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Configurações") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = {
                                        onOpenSettings()
                                        showQuickMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (!isSearchActive && selectedVolume != null) {
                    Row(
                        modifier = Modifier.padding(start = 62.dp, end = 16.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = { selectedVolume.usedRatio },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape),
                            color = if (selectedVolume.usedRatio > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${(selectedVolume.usedRatio * 100).toInt()}% usado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (isSearchActive || searchQuery.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = isGlobalSearch,
                        onClick = onToggleGlobalSearch,
                        label = { Text(if (isGlobalSearch) "Todo armazenamento" else "Pasta atual") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                val quickExts = listOf("pdf", "jpg", "mp4", "mp3", "apk", "zip", "doc", "txt")
                items(quickExts) { ext ->
                    val isSelected = searchQuery.equals(ext, ignoreCase = true) || searchQuery.equals(".$ext", ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { if (isSelected) onSearchQueryChange("") else onSearchQueryChange(ext) },
                        label = { Text(".$ext") }
                    )
                }
                if (searchHistory.isNotEmpty()) {
                    items(searchHistory) { historyItem ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ) {
                            TextButton(onClick = { onSearchQueryChange(historyItem) }) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(historyItem, maxLines = 1)
                            }
                        }
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filterCategory == null,
                        onClick = { onFilterCategorySelected(null) },
                        label = { Text("Todos") },
                        leadingIcon = if (filterCategory == null) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                items(FileType.values().filterNot { it == FileType.FOLDER }) { cat ->
                    FilterChip(
                        selected = filterCategory == cat,
                        onClick = { onFilterCategorySelected(if (filterCategory == cat) null else cat) },
                        label = { Text(categoryLabel(cat)) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(cat.getCategoryColor())
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = cat.getCategoryColor().copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            selectedLeadingIconColor = cat.getCategoryColor()
                        )
                    )
                }
            }
        }
    }

    DropdownMenu(
        expanded = showVolumeDropdown,
        onDismissRequest = { showVolumeDropdown = false }
    ) {
        storageVolumes.forEach { volume ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(volume.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${formatFileSize(volume.usedBytes)} usados · ${formatFileSize(volume.freeBytes)} livres",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                onClick = {
                    onVolumeSelected(volume)
                    showVolumeDropdown = false
                },
                leadingIcon = {
                    Icon(volumeIcon(volume.typeKey), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            )
        }
    }
}

private fun categoryLabel(type: FileType): String = when (type) {
    FileType.IMAGE -> "Imagens"
    FileType.VIDEO -> "Vídeos"
    FileType.AUDIO -> "Áudios"
    FileType.DOCUMENT -> "Documentos"
    FileType.APK -> "Aplicativos"
    FileType.ARCHIVE -> "Arquivos ZIP"
    FileType.CODE -> "Código"
    else -> "Outros"
}

private fun volumeIcon(typeKey: String) = when (typeKey) {
    "SDCARD" -> Icons.Default.SdCard
    "DOWNLOADS" -> Icons.Default.Download
    "DOCUMENTS" -> Icons.Default.Description
    "PICTURES" -> Icons.Default.Image
    "CLOUD" -> Icons.Default.Cloud
    else -> Icons.Default.Storage
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
