package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.FileType
import com.example.data.models.SortOption
import com.example.data.models.SortOrder
import com.example.data.models.StorageVolume
import com.example.data.models.ViewMode

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
    var showSortDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Main Top Bar Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                    placeholder = { Text("Buscar arquivos...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu Lateral")
                    }

                    // Logo & Volume Selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showVolumeDropdown = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FolderSpecial,
                                contentDescription = "Arcbox",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
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
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
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
                                            "${formatFileSize(volume.usedBytes)} / ${formatFileSize(volume.totalBytes)}",
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
                                    Icon(
                                        when (volume.typeKey) {
                                            "SDCARD" -> Icons.Default.SdCard
                                            "DOWNLOADS" -> Icons.Default.Download
                                            "DOCUMENTS" -> Icons.Default.Description
                                            "PICTURES" -> Icons.Default.Image
                                            "CLOUD" -> Icons.Default.Cloud
                                            else -> Icons.Default.Storage
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                    }
                }

                // Action Icons (Search, View Mode, Sort, Dashboard, Trash, Settings)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isSearchActive = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }

                    IconButton(onClick = onToggleViewMode) {
                        Icon(
                            if (viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Modo de Visualização"
                        )
                    }

                    Box {
                        IconButton(onClick = { showSortDropdown = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar")
                        }
                        DropdownMenu(
                            expanded = showSortDropdown,
                            onDismissRequest = { showSortDropdown = false }
                        ) {
                            SortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (option) {
                                                SortOption.NAME -> "Nome"
                                                SortOption.DATE -> "Data"
                                                SortOption.SIZE -> "Tamanho"
                                                SortOption.TYPE -> "Tipo"
                                            }
                                        )
                                    },
                                    onClick = {
                                        onSortOptionSelected(option)
                                        showSortDropdown = false
                                    },
                                    trailingIcon = {
                                        if (sortOption == option) {
                                            Icon(
                                                if (sortOrder == SortOrder.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Filter Chips Row
        if (isSearchActive || searchQuery.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = isGlobalSearch,
                        onClick = onToggleGlobalSearch,
                        label = { Text(if (isGlobalSearch) "🌐 Todo Armazenamento" else "📂 Pasta Atual") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                val quickExts = listOf(".pdf", ".jpg", ".mp4", ".mp3", ".apk", ".zip", ".doc", ".txt")
                items(quickExts) { ext ->
                    val isSelected = searchQuery.equals(ext, ignoreCase = true) || searchQuery.equals(ext.removePrefix("."), ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) onSearchQueryChange("") else onSearchQueryChange(ext.removePrefix("."))
                        },
                        label = { Text(ext) }
                    )
                }

                if (searchHistory.isNotEmpty()) {
                    items(searchHistory) { historyItem ->
                        val isHistSelected = searchQuery.equals(historyItem, ignoreCase = true)
                        AssistChip(
                            onClick = { onSearchQueryChange(historyItem) },
                            label = { Text(historyItem) },
                            leadingIcon = {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
            }
        } else {
            // Category Filter Chips Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filterCategory == null,
                        onClick = { onFilterCategorySelected(null) },
                        label = { Text("Todos") },
                        leadingIcon = {
                            if (filterCategory == null) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
                items(FileType.values().filterNot { it == FileType.FOLDER }) { cat ->
                    FilterChip(
                        selected = filterCategory == cat,
                        onClick = { onFilterCategorySelected(if (filterCategory == cat) null else cat) },
                        label = {
                            Text(
                                when (cat) {
                                    FileType.IMAGE -> "Imagens"
                                    FileType.VIDEO -> "Vídeos"
                                    FileType.AUDIO -> "Áudios"
                                    FileType.DOCUMENT -> "Docs"
                                    FileType.APK -> "Aplicativos"
                                    FileType.ARCHIVE -> "ZIPs"
                                    FileType.CODE -> "Código"
                                    else -> "Outros"
                                }
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(cat.getCategoryColor())
                            )
                        }
                    )
                }
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
