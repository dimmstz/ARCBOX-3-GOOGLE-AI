package com.example.ui.components

import androidx.activity.compose.BackHandler

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.CategoryDetailInfo
import com.example.data.models.DuplicateGroup
import com.example.data.models.FileItem
import com.example.data.models.FileType
import com.example.ui.theme.*
import com.example.data.models.StorageCategoryStats
import com.example.data.models.StorageVolume
import com.example.data.models.formatFileSize
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcboxStorageDashboardModal(
    stats: List<StorageCategoryStats>,
    largeFiles: List<FileItem>,
    duplicateGroups: List<DuplicateGroup>,
    storageVolumes: List<StorageVolume> = emptyList(),
    selectedVolume: StorageVolume? = null,
    isAnalyzing: Boolean = false,
    onFetchCategoryDetails: suspend (FileType) -> CategoryDetailInfo,
    onNavigateToFolder: (String) -> Unit,
    onSelectVolume: (StorageVolume) -> Unit = {},
    onOpenCloudManager: () -> Unit = {},
    onDeleteFiles: (List<FileItem>) -> Unit = {},
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedDetailCategory by remember { mutableStateOf<FileType?>(null) }
    var categoryDetailInfo by remember { mutableStateOf<CategoryDetailInfo?>(null) }
    var isFetchingDetail by remember { mutableStateOf(false) }
    var showVolumeDropdown by remember { mutableStateOf(false) }

    val activeVolume = remember(selectedVolume, storageVolumes) {
        selectedVolume ?: storageVolumes.firstOrNull()
    }

    LaunchedEffect(selectedDetailCategory) {
        val cat = selectedDetailCategory
        if (cat != null) {
            isFetchingDetail = true
            categoryDetailInfo = onFetchCategoryDetails(cat)
            isFetchingDetail = false
        } else {
            categoryDetailInfo = null
        }
    }

    BackHandler(enabled = selectedDetailCategory != null) {
        selectedDetailCategory = null
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedDetailCategory != null) "Detalhes da Categoria" else "Análise & Limpeza",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectedDetailCategory != null) {
                                selectedDetailCategory = null
                            } else {
                                onClose()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    },
                    actions = {},
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                if (isAnalyzing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (selectedDetailCategory == null) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PieChart, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Visão Geral", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painter = painterResource(R.drawable.ic_broom), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Limpeza", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }

                    // Dedicated, full-width Storage Volume Selector Bar
                    if (storageVolumes.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Box {
                                    Surface(
                                        onClick = { showVolumeDropdown = true },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp)
                                        ) {
                                            if (activeVolume?.typeKey == "CLOUD") {
                                                CloudBrandIconByName(
                                                    nameOrId = activeVolume.name,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = when (activeVolume?.typeKey) {
                                                        "SDCARD" -> Icons.Default.SdCard
                                                        "OTG", "USB" -> Icons.Default.Usb
                                                        "ROOT" -> Icons.Default.Security
                                                        else -> Icons.Default.Storage
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = activeVolume?.name ?: "Unidade",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = "Selecionar Unidade de Armazenamento",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showVolumeDropdown,
                                        onDismissRequest = { showVolumeDropdown = false },
                                        modifier = Modifier.widthIn(min = 300.dp, max = 360.dp)
                                    ) {
                                        Text(
                                            text = "Selecionar Unidade de Armazenamento",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

                                        storageVolumes.forEach { volume ->
                                            val isSelected = activeVolume?.id == volume.id || (activeVolume == null && volume == storageVolumes.firstOrNull())
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Text(
                                                                text = volume.name,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f, fill = false)
                                                            )
                                                            if (isSelected) {
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Surface(
                                                                    shape = CircleShape,
                                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                                ) {
                                                                    Text(
                                                                        text = "Ativa",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontSize = 9.sp,
                                                                        color = MaterialTheme.colorScheme.primary,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                        fontWeight = FontWeight.Bold,
                                                                        maxLines = 1,
                                                                        softWrap = false
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Text(
                                                            text = "${formatFileSize(volume.freeBytes)} livres de ${formatFileSize(volume.totalBytes)}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            softWrap = false
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    showVolumeDropdown = false
                                                    onSelectVolume(volume)
                                                },
                                                leadingIcon = {
                                                    if (volume.typeKey == "CLOUD") {
                                                        CloudBrandIconByName(
                                                            nameOrId = volume.name,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = when (volume.typeKey) {
                                                                "SDCARD" -> Icons.Default.SdCard
                                                                "OTG", "USB" -> Icons.Default.Usb
                                                                "ROOT" -> Icons.Default.Security
                                                                else -> Icons.Default.Storage
                                                            },
                                                            contentDescription = null,
                                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                },
                                                trailingIcon = if (isSelected) {
                                                    {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (selectedTab) {
                            0 -> StorageCategoriesView(
                                stats = stats,
                                storageVolumes = storageVolumes,
                                selectedVolume = activeVolume,
                                onCategoryClick = { selectedDetailCategory = it },
                                onSelectVolume = { vol -> onSelectVolume(vol) },
                                onOpenCloudManager = onOpenCloudManager,
                                onOpenCleaner = { selectedTab = 1 }
                            )
                            1 -> CleaningHubView(
                                largeFiles = largeFiles,
                                duplicateGroups = duplicateGroups,
                                selectedVolume = activeVolume,
                                onFetchCategoryDetails = onFetchCategoryDetails,
                                onNavigateToFolder = onNavigateToFolder,
                                onDeleteFiles = onDeleteFiles
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (isFetchingDetail) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Analisando pastas...", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else categoryDetailInfo?.let { detail ->
                            CategoryFoldersDetailView(
                                detail = detail,
                                onBack = { selectedDetailCategory = null },
                                onNavigateToFolder = onNavigateToFolder,
                                onDeleteFiles = onDeleteFiles
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StorageCategoriesView(
    stats: List<StorageCategoryStats>,
    storageVolumes: List<StorageVolume> = emptyList(),
    selectedVolume: StorageVolume? = null,
    onCategoryClick: (FileType) -> Unit,
    onSelectVolume: (StorageVolume) -> Unit = {},
    onOpenCloudManager: () -> Unit = {},
    onOpenCleaner: () -> Unit = {}
) {
    val totalBytes = stats.sumOf { it.bytes }.coerceAtLeast(1L)
    val activeVol = selectedVolume ?: storageVolumes.firstOrNull()
    
    // Animation for the donut chart and progress bars (single shared factor to avoid per-item recomposition)
    var animationPlayed by remember { mutableStateOf(false) }
    val donutAnimatedRatio by animateFloatAsState(targetValue = if (animationPlayed) 1f else 0f, animationSpec = tween(1500, easing = FastOutSlowInEasing), label = "donut_anim")
    val progressAnimatedFactor by animateFloatAsState(targetValue = if (animationPlayed) 1f else 0f, animationSpec = tween(1000, easing = FastOutSlowInEasing), label = "progress_factor")
    LaunchedEffect(Unit) {
        animationPlayed = true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Distribuição do Armazenamento",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val categoryColors = stats.map { it.fileType.getCategoryColor() }
                    
                    Box(
                        modifier = Modifier.size(190.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            var startAngle = -90f
                            val strokeWidth = 36.dp.toPx()
                            val arcStroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            val bgStroke = Stroke(width = strokeWidth)

                            stats.forEachIndexed { index, stat ->
                                val sweep = (stat.bytes.toFloat() / totalBytes) * 360f
                                if (sweep > 0f) {
                                    drawArc(
                                        color = categoryColors.getOrElse(index) { Color.Gray },
                                        startAngle = startAngle,
                                        sweepAngle = sweep * donutAnimatedRatio,
                                        useCenter = false,
                                        style = arcStroke
                                    )
                                    startAngle += sweep
                                }
                            }
                            
                            drawCircle(
                                color = Color.Gray.copy(alpha = 0.1f),
                                radius = size.minDimension / 2f,
                                style = bgStroke
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatFileSize(totalBytes),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Analisados",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onOpenCleaner,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_broom), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ir para Limpeza", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "Categorias de Arquivos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(stats, key = { it.fileType.name }) { stat ->
            val ratio = stat.bytes.toFloat() / totalBytes
            val catColor = stat.fileType.getCategoryColor()

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoryClick(stat.fileType) }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(getVibrantHorizontalGradient(catColor))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stat.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (stat.fileCount == 1) "1 item" else "${stat.fileCount} itens",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatFileSize(stat.bytes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format("%.1f%%", ratio * 100),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { ratio * progressAnimatedFactor },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = catColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleaningHubView(
    largeFiles: List<FileItem>,
    duplicateGroups: List<DuplicateGroup>,
    selectedVolume: StorageVolume? = null,
    onFetchCategoryDetails: suspend (FileType) -> CategoryDetailInfo,
    onNavigateToFolder: (String) -> Unit,
    onDeleteFiles: (List<FileItem>) -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    leadingIcon = { Icon(painter = painterResource(R.drawable.ic_broom), contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Rápida", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Grandes (${largeFiles.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    modifier = Modifier.weight(1.15f)
                )
                FilterChip(
                    selected = subTab == 2,
                    onClick = { subTab = 2 },
                    leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Duplicados (${duplicateGroups.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    modifier = Modifier.weight(1.25f)
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (subTab) {
                0 -> SmartCleanerTab(duplicateGroups, onFetchCategoryDetails, onDeleteFiles, selectedVolume)
                1 -> LargeFilesView(largeFiles, onNavigateToFolder, onDeleteFiles)
                2 -> DuplicateFilesView(duplicateGroups, onNavigateToFolder, onDeleteFiles)
            }
        }
    }
}

@Composable
fun LargeFilesView(
    largeFiles: List<FileItem>,
    onNavigateToFolder: (String) -> Unit,
    onDeleteFiles: (List<FileItem>) -> Unit
) {
    var selectedFiles by remember { mutableStateOf(setOf<FileItem>()) }

    if (largeFiles.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painter = painterResource(R.drawable.ic_broom), contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Ótimo! Nenhum arquivo excessivamente grande encontrado.")
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "${selectedFiles.size} selecionados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = { 
                                selectedFiles = if (selectedFiles.size == largeFiles.size) emptySet() else largeFiles.toSet() 
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (selectedFiles.size == largeFiles.size) "Desmarcar Todos" else "Selecionar Todos",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (selectedFiles.isNotEmpty()) {
                        Button(
                            onClick = {
                                onDeleteFiles(selectedFiles.toList())
                                selectedFiles = emptySet()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Limpar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(largeFiles, key = { it.path }) { file ->
                    val isSelected = selectedFiles.contains(file)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedFiles = if (isSelected) selectedFiles - file else selectedFiles + file
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { 
                                    selectedFiles = if (it) selectedFiles + file else selectedFiles - file 
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (file.size > 500 * 1024 * 1024) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (file.size > 500 * 1024 * 1024) "500M+" else "50M+",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (file.size > 500 * 1024 * 1024) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = file.path,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatFileSize(file.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                IconButton(
                                    onClick = {
                                        val parent = file.path.substringBeforeLast('/')
                                        if (parent.isNotEmpty()) onNavigateToFolder(parent)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = "Abrir pasta",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
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

@Composable
fun DuplicateFilesView(
    duplicateGroups: List<DuplicateGroup>,
    onNavigateToFolder: (String) -> Unit,
    onDeleteFiles: (List<FileItem>) -> Unit
) {
    var selectedFiles by remember { mutableStateOf(setOf<FileItem>()) }

    // Auto-select duplicates (everything except the first item in each group)
    LaunchedEffect(duplicateGroups) {
        val toSelect = mutableSetOf<FileItem>()
        duplicateGroups.forEach { group ->
            if (group.files.size > 1) {
                // Keep the first one, select the rest
                toSelect.addAll(group.files.drop(1))
            }
        }
        selectedFiles = toSelect
    }

    if (duplicateGroups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painter = painterResource(R.drawable.ic_broom), contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Seu armazenamento está limpo! Sem duplicatas.")
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "${selectedFiles.size} selecionados",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = { 
                                val allFiles = duplicateGroups.flatMap { it.files }
                                selectedFiles = if (selectedFiles.size == allFiles.size) emptySet() else allFiles.toSet() 
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (selectedFiles.size == duplicateGroups.flatMap { it.files }.size) "Desmarcar Todos" else "Selecionar Todos",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (selectedFiles.isNotEmpty()) {
                        Button(
                            onClick = {
                                onDeleteFiles(selectedFiles.toList())
                                selectedFiles = emptySet()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Limpar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(duplicateGroups, key = { it.key }) { group ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Grupo Duplicado (${group.files.size} cópias)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${formatFileSize(group.size)} cada",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            group.files.forEachIndexed { index, f ->
                                val isSelected = selectedFiles.contains(f)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else Color.Transparent)
                                        .clickable {
                                            selectedFiles = if (isSelected) selectedFiles - f else selectedFiles + f
                                        }
                                        .padding(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            selectedFiles = if (it) selectedFiles + f else selectedFiles - f
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (index == 0) {
                                            Text("Original Sugerido", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(
                                            text = f.path,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val parent = f.path.substringBeforeLast('/')
                                            if (parent.isNotEmpty()) onNavigateToFolder(parent)
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.FolderOpen,
                                            contentDescription = "Ir para pasta",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
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

@Composable
fun StorageVolumesView(
    storageVolumes: List<StorageVolume>,
    onSelectVolume: (StorageVolume) -> Unit,
    onOpenCloudManager: () -> Unit
) {
    var animationPlayed by remember { mutableStateOf(false) }
    val progressAnimatedFactor by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "volume_progress_factor"
    )
    LaunchedEffect(Unit) {
        animationPlayed = true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Unidades & Discos",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${storageVolumes.size} locais encontrados",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onOpenCloudManager,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gerenciar Armazenamentos em Nuvem", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(storageVolumes, key = { "vol_${it.id}" }) { volume ->
            val usedBytes = (volume.totalBytes - volume.freeBytes).coerceAtLeast(0L)
            val usedRatio = if (volume.totalBytes > 0) usedBytes.toFloat() / volume.totalBytes.toFloat() else 0f
            val isCloud = volume.typeKey == "CLOUD"

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isCloud) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectVolume(volume) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCloud) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer,
                                border = if (isCloud) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) else null,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isCloud) {
                                        CloudBrandIconByName(
                                            nameOrId = volume.name,
                                            modifier = Modifier.size(26.dp)
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
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = volume.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isCloud) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = when (volume.typeKey) {
                                            "INTERNAL" -> "Armazenamento Interno"
                                            "SDCARD" -> "Cartão SD Removível"
                                            "OTG", "USB" -> "USB / OTG Externo"
                                            "CLOUD" -> "Nuvem Sincronizada"
                                            "ROOT" -> "Raiz do Sistema (Root)"
                                            else -> volume.typeKey
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCloud) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { onSelectVolume(volume) }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Explorar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LinearProgressIndicator(
                        progress = { usedRatio * progressAnimatedFactor },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = if (usedRatio > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${formatFileSize(usedBytes)} usado de ${formatFileSize(volume.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${formatFileSize(volume.freeBytes)} livres",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (usedRatio > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryFoldersDetailView(
    detail: CategoryDetailInfo,
    onBack: () -> Unit,
    onNavigateToFolder: (String) -> Unit,
    onDeleteFiles: (List<FileItem>) -> Unit = {}
) {
    var expandedFolderPaths by remember { mutableStateOf(setOf<String>()) }
    val isFolderCategory = detail.fileType == FileType.FOLDER
    val isTempCategory = detail.fileType == FileType.TEMP_RESIDUAL

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = detail.fileType.getCategoryColor().copy(alpha = 0.05f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val detailCatColor = detail.fileType.getCategoryColor()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, detailCatColor.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .size(48.dp)
                            .background(getVibrantBadgeGradient(detailCatColor), RoundedCornerShape(14.dp))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isFolderCategory) Icons.Default.Folder else if (isTempCategory) Icons.Default.DeleteSweep else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = detailCatColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = detail.categoryName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isFolderCategory) {
                                "${detail.totalFiles} pasta(s) vazia(s) localizadas"
                            } else {
                                "${detail.folders.size} local(is) • ${detail.totalFiles} arquivo(s) • ${formatFileSize(detail.totalSize)}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val allCategoryItems = remember(detail) { detail.folders.flatMap { it.files } }
                if (allCategoryItems.isNotEmpty() && (isFolderCategory || isTempCategory)) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onDeleteFiles(allCategoryItems)
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            if (isFolderCategory) Icons.Default.Delete else Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isFolderCategory) "Excluir Todas as Pastas Vazias (${allCategoryItems.size})" else "Limpar Todos os Temporários & Residuais (${formatFileSize(detail.totalSize)})",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (detail.folders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isFolderCategory) Icons.Default.FolderOpen else Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isFolderCategory) "Nenhuma pasta vazia encontrada no armazenamento!" else "Nenhum arquivo encontrado nesta categoria.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(detail.folders, key = { it.folderPath }) { folder ->
                    val isExpanded = expandedFolderPaths.contains(folder.folderPath)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = detail.fileType.getCategoryColor().copy(alpha = 0.06f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = detail.fileType.getCategoryColor(),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.folderName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = folder.folderPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isFolderCategory) "${folder.fileCount} pasta(s) vazia(s)" else "${folder.fileCount} arquivo(s) • ${formatFileSize(folder.totalSize)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = detail.fileType.getCategoryColor(),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                FilledTonalButton(
                                    onClick = { onNavigateToFolder(folder.folderPath) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Abrir", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (folder.files.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider()

                                TextButton(
                                    onClick = {
                                        expandedFolderPaths = if (isExpanded) {
                                            expandedFolderPaths - folder.folderPath
                                        } else {
                                            expandedFolderPaths + folder.folderPath
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (isExpanded) "Ocultar itens ▲" else "Ver itens (${folder.files.size}) ▼",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (isExpanded) {
                                    folder.files.forEach { file ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp, horizontal = 8.dp)
                                        ) {
                                            Icon(
                                                if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = detail.fileType.getCategoryColor().copy(alpha = 0.8f)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = file.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (file.isDirectory) {
                                                    Text(
                                                        text = "Pasta Vazia",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            if (!file.isDirectory) {
                                                Text(
                                                    text = formatFileSize(file.size),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteFiles(listOf(file)) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Excluir item",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
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
}

@Composable
fun SmartCleanerTab(
    duplicateGroups: List<DuplicateGroup>,
    onFetchCategoryDetails: suspend (FileType) -> CategoryDetailInfo,
    onDeleteFiles: (List<FileItem>) -> Unit,
    selectedVolume: StorageVolume? = null
) {
    var isScanning by remember { mutableStateOf(true) }
    var tempDetailInfo by remember { mutableStateOf<CategoryDetailInfo?>(null) }
    var folderDetailInfo by remember { mutableStateOf<CategoryDetailInfo?>(null) }
    var apkDetailInfo by remember { mutableStateOf<CategoryDetailInfo?>(null) }

    var selectedTempIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFolderIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedDupIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedApkIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var expandedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hasInitializedSelection by remember { mutableStateOf(false) }

    var isCleaningProgress by remember { mutableStateOf(false) }
    var showCleanSuccessDialog by remember { mutableStateOf(false) }
    var lastCleanedBytes by remember { mutableLongStateOf(0L) }
    var lastCleanedItemsCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedVolume) {
        isScanning = true
        hasInitializedSelection = false
    }

    LaunchedEffect(isScanning) {
        if (isScanning) {
            tempDetailInfo = onFetchCategoryDetails(FileType.TEMP_RESIDUAL)
            folderDetailInfo = onFetchCategoryDetails(FileType.FOLDER)
            apkDetailInfo = onFetchCategoryDetails(FileType.APK)
            isScanning = false
        }
    }

    val tempFiles = remember(tempDetailInfo) { tempDetailInfo?.folders?.flatMap { it.files } ?: emptyList() }
    val emptyFolders = remember(folderDetailInfo) { folderDetailInfo?.folders?.flatMap { it.files } ?: emptyList() }
    val apkFiles = remember(apkDetailInfo) { apkDetailInfo?.folders?.flatMap { it.files } ?: emptyList() }
    val duplicateExtraFiles = remember(duplicateGroups) {
        duplicateGroups.flatMap { group -> group.files.drop(1) }
    }

    LaunchedEffect(tempFiles, emptyFolders, duplicateExtraFiles, apkFiles, isScanning) {
        if (!isScanning && !hasInitializedSelection) {
            selectedTempIds = tempFiles.map { it.id }.toSet()
            selectedFolderIds = emptyFolders.map { it.id }.toSet()
            selectedDupIds = duplicateExtraFiles.map { it.id }.toSet()
            selectedApkIds = emptySet() // APKs are not auto-selected by default to prevent accidental deletion
            hasInitializedSelection = true
        }
    }

    val activeSelectedTempFiles = remember(tempFiles, selectedTempIds) {
        tempFiles.filter { it.id in selectedTempIds }
    }
    val activeSelectedFolderFiles = remember(emptyFolders, selectedFolderIds) {
        emptyFolders.filter { it.id in selectedFolderIds }
    }
    val activeSelectedDupFiles = remember(duplicateExtraFiles, selectedDupIds) {
        duplicateExtraFiles.filter { it.id in selectedDupIds }
    }
    val activeSelectedApkFiles = remember(apkFiles, selectedApkIds) {
        apkFiles.filter { it.id in selectedApkIds }
    }

    val selectedFilesToClean = remember(
        activeSelectedTempFiles, activeSelectedFolderFiles,
        activeSelectedDupFiles, activeSelectedApkFiles
    ) {
        activeSelectedTempFiles + activeSelectedFolderFiles + activeSelectedDupFiles + activeSelectedApkFiles
    }

    val totalSelectedSize = remember(
        activeSelectedTempFiles, activeSelectedDupFiles, activeSelectedApkFiles
    ) {
        activeSelectedTempFiles.sumOf { it.size } +
        activeSelectedDupFiles.sumOf { it.size } +
        activeSelectedApkFiles.sumOf { it.size }
    }

    if (isScanning) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Analisando armazenamento para limpeza...", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("Buscando temporários, arquivos duplicados e pastas vazias", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_broom),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Limpeza Automática",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (selectedFilesToClean.isNotEmpty())
                                        "${formatFileSize(totalSelectedSize)} pronto(s) para liberação (${selectedFilesToClean.size} itens)"
                                    else
                                        "Selecione itens ou categorias abaixo para limpar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (selectedFilesToClean.isNotEmpty()) {
                                    isCleaningProgress = true
                                }
                            },
                            enabled = selectedFilesToClean.isNotEmpty() && !isCleaningProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Limpar Agora (${formatFileSize(totalSelectedSize)})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Categorias de Arquivos Desnecessários",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            item {
                CleanerCategoryCard(
                    categoryId = "TEMP",
                    title = "Arquivos Temporários & Cache",
                    description = "Logs do sistema, cache de apps, arquivos .tmp, .bak e resíduos",
                    icon = Icons.Default.DeleteSweep,
                    iconColor = Color(0xFFFF7043),
                    items = tempFiles,
                    selectedIds = selectedTempIds,
                    isExpanded = "TEMP" in expandedCategories,
                    onToggleExpand = {
                        expandedCategories = if ("TEMP" in expandedCategories) {
                            expandedCategories - "TEMP"
                        } else {
                            expandedCategories + "TEMP"
                        }
                    },
                    onToggleItem = { id ->
                        selectedTempIds = if (id in selectedTempIds) {
                            selectedTempIds - id
                        } else {
                            selectedTempIds + id
                        }
                    },
                    onSelectAll = {
                        selectedTempIds = tempFiles.map { it.id }.toSet()
                    },
                    onDeselectAll = {
                        selectedTempIds = emptySet()
                    }
                )
            }

            item {
                CleanerCategoryCard(
                    categoryId = "FOLDERS",
                    title = "Pastas Vazias",
                    description = "Diretórios em branco que não contêm nenhum arquivo",
                    icon = Icons.Default.FolderOpen,
                    iconColor = Color(0xFFFFB74D),
                    items = emptyFolders,
                    selectedIds = selectedFolderIds,
                    isExpanded = "FOLDERS" in expandedCategories,
                    onToggleExpand = {
                        expandedCategories = if ("FOLDERS" in expandedCategories) {
                            expandedCategories - "FOLDERS"
                        } else {
                            expandedCategories + "FOLDERS"
                        }
                    },
                    onToggleItem = { id ->
                        selectedFolderIds = if (id in selectedFolderIds) {
                            selectedFolderIds - id
                        } else {
                            selectedFolderIds + id
                        }
                    },
                    onSelectAll = {
                        selectedFolderIds = emptyFolders.map { it.id }.toSet()
                    },
                    onDeselectAll = {
                        selectedFolderIds = emptySet()
                    }
                )
            }

            item {
                CleanerCategoryCard(
                    categoryId = "DUPLICATES",
                    title = "Cópias de Arquivos Duplicados",
                    description = "Remove somente as cópias repetidas, mantendo o arquivo original intacto",
                    icon = Icons.Default.Layers,
                    iconColor = Color(0xFF42A5F5),
                    items = duplicateExtraFiles,
                    selectedIds = selectedDupIds,
                    isExpanded = "DUPLICATES" in expandedCategories,
                    onToggleExpand = {
                        expandedCategories = if ("DUPLICATES" in expandedCategories) {
                            expandedCategories - "DUPLICATES"
                        } else {
                            expandedCategories + "DUPLICATES"
                        }
                    },
                    onToggleItem = { id ->
                        selectedDupIds = if (id in selectedDupIds) {
                            selectedDupIds - id
                        } else {
                            selectedDupIds + id
                        }
                    },
                    onSelectAll = {
                        selectedDupIds = duplicateExtraFiles.map { it.id }.toSet()
                    },
                    onDeselectAll = {
                        selectedDupIds = emptySet()
                    }
                )
            }

            item {
                CleanerCategoryCard(
                    categoryId = "APKS",
                    title = "Pacotes de Instalação (APKs)",
                    description = "Instaladores baixados salvos na memória interna ou downloads",
                    icon = Icons.Default.Android,
                    iconColor = Color(0xFF66BB6A),
                    items = apkFiles,
                    selectedIds = selectedApkIds,
                    isExpanded = "APKS" in expandedCategories,
                    onToggleExpand = {
                        expandedCategories = if ("APKS" in expandedCategories) {
                            expandedCategories - "APKS"
                        } else {
                            expandedCategories + "APKS"
                        }
                    },
                    onToggleItem = { id ->
                        selectedApkIds = if (id in selectedApkIds) {
                            selectedApkIds - id
                        } else {
                            selectedApkIds + id
                        }
                    },
                    onSelectAll = {
                        selectedApkIds = apkFiles.map { it.id }.toSet()
                    },
                    onDeselectAll = {
                        selectedApkIds = emptySet()
                    }
                )
            }
        }
    }

    if (isCleaningProgress) {
        LaunchedEffect(Unit) {
            delay(1200)
            val count = selectedFilesToClean.size
            val bytes = totalSelectedSize
            onDeleteFiles(selectedFilesToClean)
            lastCleanedBytes = bytes
            lastCleanedItemsCount = count
            isCleaningProgress = false
            showCleanSuccessDialog = true
            isScanning = true
        }

        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Executando Limpeza...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Removendo itens selecionados e liberando memória...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showCleanSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showCleanSuccessDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text("Limpeza Concluída!", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Foram removidos $lastCleanedItemsCount item(ns) e ${formatFileSize(lastCleanedBytes)} de armazenamento foram liberados com sucesso!"
                )
            },
            confirmButton = {
                Button(onClick = { showCleanSuccessDialog = false }) {
                    Text("Entendido")
                }
            }
        )
    }
}

@Composable
private fun CleanerCategoryCard(
    categoryId: String,
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    items: List<FileItem>,
    selectedIds: Set<String>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleItem: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    val totalCount = items.size
    val selectedCount = remember(items, selectedIds) { items.count { it.id in selectedIds } }
    val totalBytes = remember(items) { items.sumOf { it.size } }
    val selectedBytes = remember(items, selectedIds) {
        items.filter { it.id in selectedIds }.sumOf { it.size }
    }

    val toggleableState = remember(totalCount, selectedCount) {
        when {
            totalCount == 0 -> ToggleableState.Off
            selectedCount == totalCount -> ToggleableState.On
            selectedCount == 0 -> ToggleableState.Off
            else -> ToggleableState.Indeterminate
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            width = if (isExpanded || selectedCount > 0) 1.5.dp else 1.dp,
            color = if (selectedCount > 0) iconColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Main Category Header with top alignment and balanced spacing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier.padding(top = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TriStateCheckbox(
                        state = toggleableState,
                        onClick = {
                            if (toggleableState == ToggleableState.On) {
                                onDeselectAll()
                                if (!isExpanded) onToggleExpand()
                            } else {
                                onSelectAll()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = iconColor.copy(alpha = 0.15f),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Status and size row formatted cleanly
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val statusText = when {
                            totalCount == 0 -> "Nenhum item"
                            selectedCount == totalCount -> "$totalCount item(ns) selecionado(s)"
                            selectedCount == 0 -> "$totalCount item(ns) disponíveis"
                            else -> "$selectedCount de $totalCount selecionados"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedCount > 0) iconColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (totalBytes > 0L) {
                            val sizeDisplay = if (selectedCount == totalCount) {
                                formatFileSize(totalBytes)
                            } else if (selectedCount > 0) {
                                "${formatFileSize(selectedBytes)} / ${formatFileSize(totalBytes)}"
                            } else {
                                formatFileSize(totalBytes)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selectedCount > 0) iconColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Text(
                                    text = sizeDisplay,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedCount > 0) iconColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Ocultar subitens" else "Ver subitens para seleção individual",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded subcategories & individual item selection list
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Sub-item header actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Seleção Individual ($selectedCount/$totalCount):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = onSelectAll,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Todos", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = onDeselectAll,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.Deselect, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nenhum", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Nenhum arquivo encontrado nesta categoria.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items.forEach { fileItem ->
                                val isItemSelected = fileItem.id in selectedIds
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isItemSelected) {
                                        iconColor.copy(alpha = 0.08f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    },
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isItemSelected) iconColor.copy(alpha = 0.35f) else Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleItem(fileItem.id) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isItemSelected,
                                            onCheckedChange = { onToggleItem(fileItem.id) },
                                            modifier = Modifier.size(24.dp)
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = (if (isItemSelected) iconColor else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.12f),
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = when {
                                                        fileItem.isDirectory -> Icons.Default.Folder
                                                        fileItem.fileType == FileType.APK -> Icons.Default.Android
                                                        categoryId == "TEMP" -> Icons.Default.DeleteSweep
                                                        categoryId == "DUPLICATES" -> Icons.Default.Layers
                                                        else -> Icons.Default.InsertDriveFile
                                                    },
                                                    contentDescription = null,
                                                    tint = if (isItemSelected) iconColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = fileItem.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isItemSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val subInfo = fileItem.path.ifEmpty {
                                                fileItem.packageName ?: "Armazenamento local"
                                            }
                                            Text(
                                                text = subInfo,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (fileItem.size > 0L) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isItemSelected) iconColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                            ) {
                                                Text(
                                                    text = formatFileSize(fileItem.size),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.5.sp,
                                                    color = if (isItemSelected) iconColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
