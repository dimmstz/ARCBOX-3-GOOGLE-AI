package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.models.FileType
import com.example.data.models.StorageVolume
import com.example.data.models.ThemeMode
import com.example.util.formatFileSize
import com.example.ui.theme.*

@Composable
fun ArcboxNavigationDrawerContent(
    storageVolumes: List<StorageVolume>,
    selectedVolume: StorageVolume?,
    currentFilterCategory: FileType?,
    trashCount: Int,
    favoritesCount: Int = 0,
    isFavoritesOnly: Boolean = false,
    isRecentsOnly: Boolean = false,
    isAppManagerOpen: Boolean = false,
    currentThemeMode: ThemeMode,
    isMegaConnected: Boolean = false,
    isDriveConnected: Boolean = false,
    isMediafireConnected: Boolean = false,
    isOnedriveConnected: Boolean = false,
    isDropboxConnected: Boolean = false,
    isWebdavConnected: Boolean = false,
    megaEmail: String = "",
    driveEmail: String = "",
    mediafireEmail: String = "",
    onedriveEmail: String = "",
    dropboxEmail: String = "",
    webdavEmail: String = "",
    onSelectVolume: (StorageVolume) -> Unit,
    onStartOAuthFlow: (CloudProvider) -> Unit = {},
    onSelectFavorites: () -> Unit = {},
    onSelectRecents: () -> Unit = {},
    onSelectCategory: (FileType?) -> Unit,
    onOpenAppManager: () -> Unit = {},
    onOpenStorageDashboard: () -> Unit,
    onOpenTrashBin: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCloudManager: () -> Unit = {},
    onOpenWelcomeOnboarding: () -> Unit = {},
    onToggleThemeMode: (ThemeMode) -> Unit,
    onCloseDrawer: () -> Unit
) {
    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(310.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Header Section
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ArcboxLogoIcon(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Arcbox",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Gerenciador de Arquivos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Storage Capacity Summary Cards (Internal Storage + SD Card / OTG if available)
                    val localUnits = storageVolumes.filter { it.typeKey != "CLOUD" }.ifEmpty {
                        listOfNotNull(selectedVolume)
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        localUnits.forEach { volume ->
                            val isSelected = selectedVolume?.id == volume.id && !isFavoritesOnly && !isRecentsOnly && currentFilterCategory == null

                            Surface(
                                onClick = {
                                    onSelectVolume(volume)
                                    onCloseDrawer()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Icon(
                                                when (volume.typeKey) {
                                                    "SDCARD" -> Icons.Default.SdCard
                                                    "OTG", "USB" -> Icons.Default.Usb
                                                    "ROOT" -> Icons.Default.Security
                                                    else -> Icons.Default.Storage
                                                },
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = volume.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = "Abrir diretório",
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { volume.usedRatio },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(CircleShape),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        strokeCap = StrokeCap.Round,
                                        gapSize = 0.dp,
                                        drawStopIndicator = {}
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (volume.totalBytes > 0) "${formatFileSize(volume.usedBytes)} de ${formatFileSize(volume.totalBytes)} usados" else "Armazenamento",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (volume.totalBytes > 0) {
                                            Text(
                                                text = "${(volume.usedRatio * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NUVEM & ARMAZENAMENTO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            val allCloudProviders = listOf(
                "MEGA" to isMegaConnected,
                "Google Drive" to isDriveConnected,
                "OneDrive" to isOnedriveConnected,
                "Dropbox" to isDropboxConnected,
                "MediaFire" to isMediafireConnected,
                "WebDAV" to isWebdavConnected
            )
            val availableClouds = allCloudProviders.filter { !it.second }.map { it.first }
            val hasAvailableClouds = availableClouds.isNotEmpty()

            // Render connected cloud drives only
            if (isMegaConnected) {
                val megaVolume = storageVolumes.find { it.id == "cloud_mega" || it.path == "/cloud/mega" }
                    ?: StorageVolume(id = "cloud_mega", name = "MEGA", path = "/cloud/mega", totalBytes = 50L * 1024 * 1024 * 1024L, freeBytes = 50L * 1024 * 1024 * 1024L, typeKey = "CLOUD")

                NavigationDrawerItem(
                    label = {
                        Column {
                            Text("MEGA", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (megaEmail.isNotBlank()) megaEmail else "Conectado • 50 GB",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    selected = selectedVolume?.path == "/cloud/mega",
                    onClick = {
                        onSelectVolume(megaVolume)
                        onCloseDrawer()
                    },
                    icon = {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = "MEGA Cloud",
                            tint = Color(0xFFD9272E)
                        )
                    },
                    badge = {
                        Surface(
                            color = Color(0xFFD9272E).copy(alpha = 0.15f),
                            shape = CircleShape
                        ) {
                            Text(
                                "Ativo",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFD9272E),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            if (isDriveConnected) {
                val matchedSafDrive = storageVolumes.find { it.isSaf && (it.name.contains("Drive", ignoreCase = true) || it.path.contains("drive", ignoreCase = true)) }
                val driveVolume = matchedSafDrive ?: (storageVolumes.find { it.id == "cloud_drive" || it.path == "/cloud/drive" }
                    ?: StorageVolume(id = "cloud_drive", name = "Google Drive", path = "/cloud/drive", totalBytes = 15L * 1024 * 1024 * 1024L, freeBytes = 15L * 1024 * 1024 * 1024L, typeKey = "CLOUD"))

                NavigationDrawerItem(
                    label = {
                        Column {
                            Text("Google Drive", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (driveEmail.isNotBlank()) driveEmail else "Conectado • 15 GB",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    selected = selectedVolume?.path == driveVolume.path || selectedVolume?.path == "/cloud/drive",
                    onClick = {
                        onSelectVolume(driveVolume)
                        onCloseDrawer()
                    },
                    icon = {
                        Icon(
                            Icons.Default.CloudQueue,
                            contentDescription = "Google Drive",
                            tint = Color(0xFF4285F4)
                        )
                    },
                    badge = {
                        Surface(
                            color = Color(0xFF4285F4).copy(alpha = 0.15f),
                            shape = CircleShape
                        ) {
                            Text(
                                "Ativo",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4285F4),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            if (isOnedriveConnected) {
                val matchedSafOd = storageVolumes.find { it.isSaf && (it.name.contains("OneDrive", ignoreCase = true) || it.path.contains("onedrive", ignoreCase = true)) }
                val odVolume = matchedSafOd ?: (storageVolumes.find { it.id == "cloud_onedrive" || it.path == "/cloud/onedrive" }
                    ?: StorageVolume(id = "cloud_onedrive", name = "OneDrive", path = "/cloud/onedrive", totalBytes = 5L * 1024 * 1024 * 1024L, freeBytes = 5L * 1024 * 1024 * 1024L, typeKey = "CLOUD"))

                NavigationDrawerItem(
                    label = {
                        Column {
                            Text("OneDrive", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (onedriveEmail.isNotBlank()) onedriveEmail else "Conectado • 5 GB",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    selected = selectedVolume?.path == odVolume.path || selectedVolume?.path == "/cloud/onedrive",
                    onClick = {
                        onSelectVolume(odVolume)
                        onCloseDrawer()
                    },
                    icon = {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = "OneDrive",
                            tint = Color(0xFF0078D4)
                        )
                    },
                    badge = {
                        Surface(
                            color = Color(0xFF0078D4).copy(alpha = 0.15f),
                            shape = CircleShape
                        ) {
                            Text(
                                "Ativo",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF0078D4),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            if (isDropboxConnected) {
                val matchedSafDbx = storageVolumes.find { it.isSaf && (it.name.contains("Dropbox", ignoreCase = true) || it.path.contains("dropbox", ignoreCase = true)) }
                val dbxVolume = matchedSafDbx ?: (storageVolumes.find { it.id == "cloud_dropbox" || it.path == "/cloud/dropbox" }
                    ?: StorageVolume(id = "cloud_dropbox", name = "Dropbox", path = "/cloud/dropbox", totalBytes = 2L * 1024 * 1024 * 1024L, freeBytes = 2L * 1024 * 1024 * 1024L, typeKey = "CLOUD"))

                NavigationDrawerItem(
                    label = {
                        Column {
                            Text("Dropbox", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (dropboxEmail.isNotBlank()) dropboxEmail else "Conectado • 2 GB",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    selected = selectedVolume?.path == dbxVolume.path || selectedVolume?.path == "/cloud/dropbox",
                    onClick = {
                        onSelectVolume(dbxVolume)
                        onCloseDrawer()
                    },
                    icon = {
                        Icon(
                            Icons.Default.CloudQueue,
                            contentDescription = "Dropbox",
                            tint = Color(0xFF0061FE)
                        )
                    },
                    badge = {
                        Surface(
                            color = Color(0xFF0061FE).copy(alpha = 0.15f),
                            shape = CircleShape
                        ) {
                            Text(
                                "Ativo",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF0061FE),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            if (isMediafireConnected) {
                val matchedSafMf = storageVolumes.find { it.isSaf && (it.name.contains("MediaFire", ignoreCase = true) || it.path.contains("mediafire", ignoreCase = true)) }
                val mfVolume = matchedSafMf ?: (storageVolumes.find { it.id == "cloud_mediafire" || it.path == "/cloud/mediafire" }
                    ?: StorageVolume(id = "cloud_mediafire", name = "MediaFire", path = "/cloud/mediafire", totalBytes = 10L * 1024 * 1024 * 1024L, freeBytes = 10L * 1024 * 1024 * 1024L, typeKey = "CLOUD"))

                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(if (matchedSafMf != null) matchedSafMf.name else "MediaFire", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (matchedSafMf != null) "Nativo Android • SAF" else if (mediafireEmail.isNotBlank()) mediafireEmail else "Conectado • 10 GB",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    selected = selectedVolume?.path == mfVolume.path,
                    onClick = {
                        onSelectVolume(mfVolume)
                        onCloseDrawer()
                    },
                    icon = {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = "MediaFire",
                            tint = Color(0xFF0070F0)
                        )
                    },
                    badge = {
                        Surface(
                            color = Color(0xFF0070F0).copy(alpha = 0.15f),
                            shape = CircleShape
                        ) {
                            Text(
                                "Ativo",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF0070F0),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            if (isWebdavConnected) {
                val webdavVolume = storageVolumes.find { it.id == "cloud_webdav" || it.path == "/cloud/webdav" }
                    ?: StorageVolume(id = "cloud_webdav", name = "WebDAV", path = "/cloud/webdav", totalBytes = 100L * 1024 * 1024 * 1024L, freeBytes = 100L * 1024 * 1024 * 1024L, typeKey = "CLOUD")

                NavigationDrawerItem(
                    label = {
                        Column {
                            Text("WebDAV", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (webdavEmail.isNotBlank()) webdavEmail else "Conectado",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    selected = selectedVolume?.path == "/cloud/webdav",
                    onClick = {
                        onSelectVolume(webdavVolume)
                        onCloseDrawer()
                    },
                    icon = {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = "WebDAV",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    badge = {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = CircleShape
                        ) {
                            Text(
                                "Ativo",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            // Additional SAF cloud drives if registered
            val knownCloudIds = setOf("cloud_mega", "cloud_drive", "cloud_onedrive", "cloud_dropbox", "cloud_mediafire", "cloud_webdav")
            storageVolumes.filter { it.typeKey == "CLOUD" && it.id !in knownCloudIds }.forEach { vol ->
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(vol.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Conectado",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    selected = selectedVolume?.id == vol.id,
                    onClick = {
                        onSelectVolume(vol)
                        onCloseDrawer()
                    },
                    icon = {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = vol.name,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    badge = {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = CircleShape
                        ) {
                            Text(
                                "Ativo",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            // Prominent "Adicionar Nuvem" banner card visible while there are still clouds available to connect
            if (hasAvailableClouds) {
                Surface(
                    onClick = {
                        onOpenCloudManager()
                        onCloseDrawer()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.CloudQueue,
                                        contentDescription = "Adicionar Nuvem",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Adicionar Nuvem",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val subtitleText = if (availableClouds.size == allCloudProviders.size) {
                                    "MEGA, Google Drive, OneDrive, WebDAV..."
                                } else {
                                    availableClouds.take(3).joinToString(", ") + if (availableClouds.size > 3) "..." else ""
                                }
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Conectar",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Navigation Item: Favoritos
            NavigationDrawerItem(
                label = { Text("Favoritos", fontWeight = FontWeight.SemiBold) },
                selected = isFavoritesOnly,
                onClick = {
                    onSelectFavorites()
                    onCloseDrawer()
                },
                icon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300)) },
                badge = {
                    if (favoritesCount > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text("$favoritesCount", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )

            // Navigation Item: Recentes
            NavigationDrawerItem(
                label = { Text("Recentes", fontWeight = FontWeight.SemiBold) },
                selected = isRecentsOnly,
                onClick = {
                    onSelectRecents()
                    onCloseDrawer()
                },
                icon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

            val headerColor = MaterialTheme.colorScheme.primary

            // Section Header: Categorias & Atalhos
            Text(
                text = "CATEGORIAS DE ARQUIVOS",
                style = MaterialTheme.typography.labelSmall,
                color = headerColor,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            DrawerCategoryItem(
                label = "Imagens",
                icon = Icons.Default.Image,
                color = FileType.IMAGE.getCategoryColor(),
                isSelected = currentFilterCategory == FileType.IMAGE,
                onClick = {
                    onSelectCategory(FileType.IMAGE)
                    onCloseDrawer()
                }
            )

            DrawerCategoryItem(
                label = "Vídeos",
                icon = Icons.Default.Movie,
                color = FileType.VIDEO.getCategoryColor(),
                isSelected = currentFilterCategory == FileType.VIDEO,
                onClick = {
                    onSelectCategory(FileType.VIDEO)
                    onCloseDrawer()
                }
            )

            DrawerCategoryItem(
                label = "Músicas & Áudios",
                icon = Icons.Default.MusicNote,
                color = FileType.AUDIO.getCategoryColor(),
                isSelected = currentFilterCategory == FileType.AUDIO,
                onClick = {
                    onSelectCategory(FileType.AUDIO)
                    onCloseDrawer()
                }
            )

            DrawerCategoryItem(
                label = "Documentos",
                icon = Icons.Default.Description,
                color = FileType.DOCUMENT.getCategoryColor(),
                isSelected = currentFilterCategory == FileType.DOCUMENT,
                onClick = {
                    onSelectCategory(FileType.DOCUMENT)
                    onCloseDrawer()
                }
            )

            DrawerCategoryItem(
                label = "Arquivos APK",
                icon = Icons.Default.Android,
                color = FileType.APK.getCategoryColor(),
                isSelected = currentFilterCategory == FileType.APK && !isAppManagerOpen,
                onClick = {
                    onSelectCategory(FileType.APK)
                    onCloseDrawer()
                }
            )

            DrawerCategoryItem(
                label = "Compactados",
                icon = Icons.Default.FolderZip,
                color = FileType.ARCHIVE.getCategoryColor(),
                isSelected = currentFilterCategory == FileType.ARCHIVE,
                onClick = {
                    onSelectCategory(FileType.ARCHIVE)
                    onCloseDrawer()
                }
            )

            DrawerCategoryItem(
                label = "Códigos & Textos",
                icon = Icons.Default.Code,
                color = FileType.CODE.getCategoryColor(),
                isSelected = currentFilterCategory == FileType.CODE,
                onClick = {
                    onSelectCategory(FileType.CODE)
                    onCloseDrawer()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

            // Section Header: Ferramentas & Armazenamento
            Text(
                text = "FERRAMENTAS DE DISCO",
                style = MaterialTheme.typography.labelSmall,
                color = headerColor,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            val toolIconTint = MaterialTheme.colorScheme.primary

            NavigationDrawerItem(
                label = { Text("Aplicativos instalados") },
                selected = isAppManagerOpen,
                onClick = {
                    onOpenAppManager()
                    onCloseDrawer()
                },
                icon = { Icon(Icons.Default.Apps, contentDescription = null, tint = toolIconTint) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )

            NavigationDrawerItem(
                label = { Text("Análise e Limpeza") },
                selected = false,
                onClick = {
                    onOpenStorageDashboard()
                    onCloseDrawer()
                },
                icon = { Icon(Icons.Outlined.PieChart, contentDescription = null, tint = toolIconTint) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

            // Section Header: Configurações & Preferências
            Text(
                text = "CONFIGURAÇÕES",
                style = MaterialTheme.typography.labelSmall,
                color = headerColor,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            NavigationDrawerItem(
                label = { Text("Recursos & Permissões") },
                selected = false,
                onClick = {
                    onOpenWelcomeOnboarding()
                    onCloseDrawer()
                },
                icon = { Icon(Icons.Default.Info, contentDescription = null, tint = toolIconTint) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )

            NavigationDrawerItem(
                label = { Text("Configurações do App") },
                selected = false,
                onClick = {
                    onOpenSettings()
                    onCloseDrawer()
                },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null, tint = toolIconTint) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )

            // Removed Quick Theme Switcher Row inside drawer footer as requested
        }
    }
}

@Composable
fun DrawerCategoryItem(
    label: String,
    icon: ImageVector,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconTint = color

    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
            )
        },
        selected = isSelected,
        onClick = onClick,
        icon = {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.35f)),
                modifier = Modifier
                    .size(30.dp)
                    .background(getVibrantBadgeGradient(color), RoundedCornerShape(10.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
            }
        },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

data class CloudDrawerEntry(
    val provider: CloudProvider,
    val isConnected: Boolean,
    val email: String,
    val volumePath: String,
    val defaultVolumeName: String,
    val totalBytes: Long,
    val usedBytes: Long
)
