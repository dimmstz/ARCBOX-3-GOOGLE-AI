package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.CategoryDetailInfo
import com.example.data.models.DuplicateGroup
import com.example.data.models.FileItem
import com.example.data.models.FileType
import com.example.data.models.StorageCategoryStats
import com.example.ui.theme.ArcboxBlue
import com.example.ui.theme.ArcboxIndigo
import com.example.util.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcboxStorageDashboardModal(
    stats: List<StorageCategoryStats>,
    largeFiles: List<FileItem>,
    duplicateGroups: List<DuplicateGroup>,
    onFetchCategoryDetails: suspend (FileType) -> CategoryDetailInfo,
    onNavigateToFolder: (String) -> Unit,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedDetailCategory by remember { mutableStateOf<FileType?>(null) }
    var categoryDetailInfo by remember { mutableStateOf<CategoryDetailInfo?>(null) }
    var isFetchingDetail by remember { mutableStateOf(false) }

    LaunchedEffect(selectedDetailCategory) {
        val category = selectedDetailCategory
        if (category != null) {
            isFetchingDetail = true
            categoryDetailInfo = onFetchCategoryDetails(category)
            isFetchingDetail = false
        } else {
            categoryDetailInfo = null
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (selectedDetailCategory != null) "Detalhes da categoria" else "Inteligência de armazenamento",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedDetailCategory == null) {
                                Text(
                                    "Uma leitura visual do seu espaço",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectedDetailCategory != null) selectedDetailCategory = null else onClose()
                        }) {
                            Icon(
                                if (selectedDetailCategory != null) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
                                contentDescription = if (selectedDetailCategory != null) "Voltar" else "Fechar"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                if (selectedDetailCategory == null) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DashboardTab("Visão geral", selectedTab == 0) { selectedTab = 0 }
                        DashboardTab("Arquivos grandes", selectedTab == 1, largeFiles.size) { selectedTab = 1 }
                        DashboardTab("Duplicados", selectedTab == 2, duplicateGroups.size) { selectedTab = 2 }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            0 -> StorageCategoriesView(
                                stats = stats,
                                largeFilesCount = largeFiles.size,
                                duplicateGroupsCount = duplicateGroups.size,
                                onCategoryClick = { selectedDetailCategory = it }
                            )
                            1 -> LargeFilesView(largeFiles = largeFiles, onNavigateToFolder = onNavigateToFolder)
                            else -> DuplicateFilesView(duplicateGroups = duplicateGroups, onNavigateToFolder = onNavigateToFolder)
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (isFetchingDetail) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    androidx.compose.material3.CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Mapeando pastas da categoria...", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else {
                            categoryDetailInfo?.let { detail ->
                                CategoryFoldersDetailView(
                                    detail = detail,
                                    onBack = { selectedDetailCategory = null },
                                    onNavigateToFolder = onNavigateToFolder
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTab(label: String, selected: Boolean, count: Int? = null, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Text(
                if (count == null) label else "$label · $count",
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    )
}

@Composable
fun StorageCategoriesView(
    stats: List<StorageCategoryStats>,
    largeFilesCount: Int = 0,
    duplicateGroupsCount: Int = 0,
    onCategoryClick: (FileType) -> Unit
) {
    val totalBytes = stats.sumOf { it.bytes }.coerceAtLeast(1L)
    val totalFiles = stats.sumOf { it.fileCount }
    val largestCategory = stats.maxByOrNull { it.bytes }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mapa do espaço", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                "Onde o armazenamento está concentrado",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Icon(
                                Icons.Default.PieChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(9.dp).size(19.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        StorageDonutChart(stats = stats, totalBytes = totalBytes)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DashboardStat("Itens", "$totalFiles", MaterialTheme.colorScheme.primary)
                        DashboardStat("Categorias", "${stats.size}", MaterialTheme.colorScheme.secondary)
                        DashboardStat("Maior", largestCategory?.name ?: "—", largestCategory?.fileType?.getCategoryColor() ?: MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                InsightCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Warning,
                    label = "Arquivos grandes",
                    value = "$largeFilesCount",
                    tint = MaterialTheme.colorScheme.error
                )
                InsightCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ContentCopy,
                    label = "Grupos duplicados",
                    value = "$duplicateGroupsCount",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Distribuição por categoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Toque em uma categoria para explorar as pastas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
        }
        items(stats) { stat ->
            CategoryStorageRow(stat = stat, totalBytes = totalBytes, onClick = { onCategoryClick(stat.fileType) })
        }
    }
}

@Composable
private fun StorageDonutChart(stats: List<StorageCategoryStats>, totalBytes: Long) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    val categoryColors = stats.map { it.fileType.getCategoryColor() }
    Box(modifier = Modifier.size(188.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 27.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            var startAngle = -90f
            stats.forEachIndexed { index, stat ->
                val sweep = stat.bytes.toFloat() / totalBytes.toFloat() * 360f
                if (sweep > 0.8f) {
                    drawArc(
                        color = categoryColors[index],
                        startAngle = startAngle + 1.8f,
                        sweepAngle = (sweep - 3.6f).coerceAtLeast(0f),
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    startAngle += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatFileSize(totalBytes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("analisados", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DashboardStat(label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun InsightCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(modifier = Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(11.dp), color = tint.copy(alpha = 0.14f)) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(8.dp).size(17.dp))
            }
            Spacer(modifier = Modifier.width(9.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CategoryStorageRow(stat: StorageCategoryStats, totalBytes: Long, onClick: () -> Unit) {
    val ratio = stat.bytes.toFloat() / totalBytes
    val color = stat.fileType.getCategoryColor()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stat.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("${stat.fileCount} itens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatFileSize(stat.bytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
                    Text("${String.format("%.1f%%", ratio * 100)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = "Explorar categoria", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 5.dp).size(18.dp))
            }
            Spacer(modifier = Modifier.height(9.dp))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun CategoryFoldersDetailView(
    detail: CategoryDetailInfo,
    onBack: () -> Unit,
    onNavigateToFolder: (String) -> Unit
) {
    var expandedFolderPaths by remember { mutableStateOf(setOf<String>()) }
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = detail.fileType.getCategoryColor().copy(alpha = 0.15f)) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = detail.fileType.getCategoryColor(), modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pastas de ${detail.categoryName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${detail.folders.size} pastas · ${detail.totalFiles} arquivos · ${formatFileSize(detail.totalSize)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (detail.folders.isEmpty()) {
            EmptyInsightState("Nenhuma pasta com arquivos nesta categoria.")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.fillMaxSize()) {
                items(detail.folders) { folder ->
                    val isExpanded = expandedFolderPaths.contains(folder.folderPath)
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Surface(shape = RoundedCornerShape(13.dp), color = detail.fileType.getCategoryColor().copy(alpha = 0.13f)) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = detail.fileType.getCategoryColor(), modifier = Modifier.padding(10.dp).size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(11.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(folder.folderName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(folder.folderPath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    Text("${folder.fileCount} arquivos · ${formatFileSize(folder.totalSize)}", style = MaterialTheme.typography.labelSmall, color = detail.fileType.getCategoryColor(), fontWeight = FontWeight.SemiBold)
                                }
                                IconButton(onClick = { onNavigateToFolder(folder.folderPath) }) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Abrir pasta", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            TextButton(onClick = {
                                expandedFolderPaths = if (isExpanded) expandedFolderPaths - folder.folderPath else expandedFolderPaths + folder.folderPath
                            }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isExpanded) "Ocultar arquivos" else "Ver arquivos (${folder.files.size})")
                            }
                            if (isExpanded) {
                                folder.files.forEach { file ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = detail.fileType.getCategoryColor())
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(file.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
                                        Text(formatFileSize(file.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
fun LargeFilesView(largeFiles: List<FileItem>, onNavigateToFolder: (String) -> Unit) {
    val totalLargeBytes = largeFiles.sumOf { it.size }
    if (largeFiles.isEmpty()) {
        EmptyInsightState("Nenhum arquivo acima de 50 MB foi encontrado.")
    } else {
        LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Oportunidade de limpeza", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${largeFiles.size} arquivos · ${formatFileSize(totalLargeBytes)} concentrados acima de 50 MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(largeFiles) { file ->
                LargeFileRow(file = file, onNavigateToFolder = onNavigateToFolder)
            }
        }
    }
}

@Composable
private fun LargeFileRow(file: FileItem, onNavigateToFolder: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
            Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)) {
                Text("50+", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 9.dp, vertical = 12.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(file.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatFileSize(file.size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                IconButton(onClick = {
                    val parent = file.path.substringBeforeLast('/')
                    if (parent.isNotEmpty()) onNavigateToFolder(parent)
                }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Abrir pasta", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun DuplicateFilesView(duplicateGroups: List<DuplicateGroup>, onNavigateToFolder: (String) -> Unit) {
    val reclaimableBytes = duplicateGroups.sumOf { group -> group.size * (group.files.size - 1).coerceAtLeast(0) }
    if (duplicateGroups.isEmpty()) {
        EmptyInsightState("Nenhum arquivo duplicado foi detectado.")
    } else {
        LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(25.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Espaço potencialmente recuperável", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(formatFileSize(reclaimableBytes) + " em cópias redundantes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(duplicateGroups) { group ->
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(8.dp).size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Grupo com ${group.files.size} cópias", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(formatFileSize(group.size) + " por cópia", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatFileSize(group.size * (group.files.size - 1).coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        group.files.forEach { file ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(file.path, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    val parent = file.path.substringBeforeLast('/')
                                    if (parent.isNotEmpty()) onNavigateToFolder(parent)
                                }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = "Ir para pasta", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyInsightState(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp).size(30.dp))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
