package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.models.FileType
import com.example.data.models.SortOption
import com.example.data.models.SortOrder
import com.example.data.models.StorageVolume
import com.example.data.models.ViewMode

import com.example.util.formatFileSize
import com.example.ui.theme.*

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
    onOpenCloudManager: () -> Unit = {},
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
            .statusBarsPadding()
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu Lateral", modifier = Modifier.size(22.dp))
                    }

                    // Logo & Volume Selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showVolumeDropdown = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Arcbox",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val accentLabelColor = MaterialTheme.colorScheme.primary
                                Text(
                                    text = selectedVolume?.name ?: "Armazenamento Interno",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accentLabelColor,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = accentLabelColor
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = showVolumeDropdown,
                        onDismissRequest = { showVolumeDropdown = false }
                    ) {
                        val iconTint = MaterialTheme.colorScheme.primary
                        storageVolumes.forEach { volume ->
                            val isCloud = volume.typeKey == "CLOUD"
                            val cloudColor = when {
                                volume.name.contains("Mega", ignoreCase = true) -> Color(0xFFD9272E)
                                volume.name.contains("Drive", ignoreCase = true) -> Color(0xFF4285F4)
                                volume.name.contains("OneDrive", ignoreCase = true) -> Color(0xFF0078D4)
                                volume.name.contains("Mediafire", ignoreCase = true) -> Color(0xFF1262D3)
                                volume.name.contains("Dropbox", ignoreCase = true) -> Color(0xFF0061FF)
                                else -> iconTint
                            }

                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(volume.name, fontWeight = FontWeight.SemiBold)
                                            if (isCloud) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = cloudColor.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        "NUVEM",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = cloudColor,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (volume.totalBytes > 0) {
                                            Text(
                                                "${formatFileSize(volume.usedBytes)} / ${formatFileSize(volume.totalBytes)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onVolumeSelected(volume)
                                    showVolumeDropdown = false
                                },
                                leadingIcon = {
                                    if (isCloud) {
                                        CloudBrandIconByName(
                                            nameOrId = volume.name,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            when (volume.typeKey) {
                                                "SDCARD" -> Icons.Default.SdCard
                                                "OTG", "USB" -> Icons.Default.Usb
                                                "ROOT" -> Icons.Default.Security
                                                else -> Icons.Default.Storage
                                            },
                                            contentDescription = null,
                                            tint = iconTint
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // Action Icons (Search, View Mode, Sort)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { isSearchActive = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar", modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = onToggleViewMode,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (viewMode == ViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Modo de Visualização",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showSortDropdown = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar", modifier = Modifier.size(20.dp))
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    val isAllSelected = filterCategory == null
                    FilterChip(
                        selected = isAllSelected,
                        onClick = { onFilterCategorySelected(null) },
                        label = {
                            Text(
                                text = "Todos",
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isAllSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.2.dp
                        ),
                        leadingIcon = {
                            if (isAllSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
                items(FileType.values().filterNot { it == FileType.FOLDER || it == FileType.TEMP_RESIDUAL }) { cat ->
                    val isSelected = filterCategory == cat
                    val catColor = cat.getCategoryColor()
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterCategorySelected(if (filterCategory == cat) null else cat) },
                        label = {
                            Text(
                                text = when (cat) {
                                    FileType.IMAGE -> "Imagens"
                                    FileType.VIDEO -> "Vídeos"
                                    FileType.AUDIO -> "Áudios"
                                    FileType.DOCUMENT -> "Docs"
                                    FileType.APK -> "APK"
                                    FileType.ARCHIVE -> "ZIPs"
                                    FileType.CODE -> "Código"
                                    else -> "Outros"
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = catColor.copy(alpha = 0.16f),
                            selectedLabelColor = catColor,
                            selectedLeadingIconColor = catColor
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            selectedBorderColor = catColor.copy(alpha = 0.85f),
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.2.dp
                        ),
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(getVibrantHorizontalGradient(catColor))
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StorageVolumeChip(
    volume: StorageVolume,
    isSelected: Boolean,
    onClick: () -> Unit,
    onManageStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    val iconTint = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val progressValue = if (volume.totalBytes > 0) {
        (volume.usedBytes.toFloat() / volume.totalBytes).coerceIn(0f, 1f)
    } else 0f

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (volume.typeKey) {
                        "SDCARD" -> Icons.Default.SdCard
                        "OTG", "USB" -> Icons.Default.Usb
                        "CLOUD" -> Icons.Default.Cloud
                        "ROOT" -> Icons.Default.Security
                        else -> Icons.Default.Storage
                    },
                    contentDescription = volume.name,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = volume.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                if (volume.totalBytes > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${formatFileSize(volume.usedBytes)} / ${formatFileSize(volume.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { progressValue },
                        modifier = Modifier
                            .width(80.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(java.util.Locale.US, kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(java.util.Locale.US, mb)
    val gb = mb / 1024.0
    if (gb < 1024) return "%.1f GB".format(java.util.Locale.US, gb)
    val tb = gb / 1024.0
    return "%.1f TB".format(java.util.Locale.US, tb)
}
