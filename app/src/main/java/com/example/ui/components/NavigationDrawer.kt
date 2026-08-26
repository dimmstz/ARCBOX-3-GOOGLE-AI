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

                    // Storage Capacity Summary Card
                    selectedVolume?.let { volume ->
                        Surface(
                            onClick = {
                                onSelectVolume(volume)
                                onCloseDrawer()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
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
                                                "CLOUD" -> Icons.Default.Cloud
                                                else -> Icons.Default.Storage
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = volume.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = "Abrir diretório",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    color = MaterialTheme.colorScheme.primary,
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
                                        text = "${formatFileSize(volume.usedBytes)} de ${formatFileSize(volume.totalBytes)} usados",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text(
                                        text = "${(volume.usedRatio * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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

            // Section 1: Discos Locais / Dispositivo
            val localVolumes = storageVolumes.filter { it.typeKey != "CLOUD" }
            Text(
                text = "DISPOSITIVO & DISCOS LOCAIS",
                style = MaterialTheme.typography.labelSmall,
                color = headerColor,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            localVolumes.forEach { volume ->
                val isSelected = selectedVolume?.id == volume.id && !isFavoritesOnly && currentFilterCategory == null

                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(
                                text = volume.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (volume.totalBytes > 0) {
                                Text(
                                    text = "${formatFileSize(volume.usedBytes)} / ${formatFileSize(volume.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    selected = isSelected,
                    onClick = {
                        onSelectVolume(volume)
                        onCloseDrawer()
                    },
                    icon = {
                        Icon(
                            when (volume.typeKey) {
                                "SDCARD" -> Icons.Default.SdCard
                                "OTG", "USB" -> Icons.Default.Usb
                                "ROOT" -> Icons.Default.Security
                                else -> Icons.Default.Storage
                            },
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }



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

            NavigationDrawerItem(
                label = { Text("Lixeira") },
                selected = false,
                onClick = {
                    onOpenTrashBin()
                    onCloseDrawer()
                },
                icon = {
                    ModernTrashCanCanvas(
                        modifier = Modifier.size(20.dp),
                        accentColor = toolIconTint
                    )
                },
                badge = {
                    if (trashCount > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text("$trashCount", color = Color.White)
                        }
                    }
                },
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
                label = { Text("Configurações do App") },
                selected = false,
                onClick = {
                    onOpenSettings()
                    onCloseDrawer()
                },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null, tint = toolIconTint) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
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
