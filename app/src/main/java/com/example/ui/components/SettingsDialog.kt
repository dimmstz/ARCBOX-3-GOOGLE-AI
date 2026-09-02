package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.FolderTransitionType
import com.example.data.models.ThemeMode
import com.example.ui.theme.AccentColorOption
import com.example.ui.theme.PredefinedCustomColors
import com.example.ui.theme.CustomColorPreset
import com.example.ui.theme.findCustomColorPreset
import com.example.util.BiometricAuthHelper
import com.example.util.findFragmentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcboxSettingsModal(
    currentThemeMode: ThemeMode,
    currentAccent: AccentColorOption,
    customAccentColorHex: Long = 0xFF4F46E5L,
    currentFolderTransition: FolderTransitionType = FolderTransitionType.MATERIAL_SLIDE,
    onSelectFolderTransition: (FolderTransitionType) -> Unit = {},
    deletePermanently: Boolean,
    onToggleDeletePermanently: (Boolean) -> Unit,
    confirmDelete: Boolean,
    onToggleConfirmDelete: (Boolean) -> Unit,
    showThumbnails: Boolean = true,
    onToggleShowThumbnails: (Boolean) -> Unit = {},
    showHiddenFiles: Boolean = false,
    onToggleShowHiddenFiles: (Boolean) -> Unit = {},
    showExtensions: Boolean = true,
    onToggleShowExtensions: (Boolean) -> Unit = {},
    parallelDirectoryReading: Boolean = true,
    onToggleParallelDirectoryReading: (Boolean) -> Unit = {},
    compressionLevel: String = "Normal",
    onSelectCompressionLevel: (String) -> Unit = {},
    biometricLock: Boolean = false,
    onToggleBiometricLock: (Boolean) -> Unit = {},
    autoLockVault: Boolean = true,
    onToggleAutoLockVault: (Boolean) -> Unit = {},
    keepHistory: Boolean = true,
    onToggleKeepHistory: (Boolean) -> Unit = {},
    trashAutoCleanDays: String = "30 dias",
    onSelectTrashAutoCleanDays: (String) -> Unit = {},
    onRestoreDefaults: () -> Unit = {},
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectAccent: (AccentColorOption) -> Unit,
    onSelectCustomColor: (Long) -> Unit = {},
    isMegaConnected: Boolean,
    onToggleMegaConnected: (Boolean) -> Unit,
    isDriveConnected: Boolean,
    onToggleDriveConnected: (Boolean) -> Unit,
    isMediafireConnected: Boolean,
    onToggleMediafireConnected: (Boolean) -> Unit,
    isOnedriveConnected: Boolean,
    onToggleOnedriveConnected: (Boolean) -> Unit,
    isDropboxConnected: Boolean,
    onToggleDropboxConnected: (Boolean) -> Unit,
    megaEmail: String = "conta.mega@arcbox.com",
    driveEmail: String = "usuario.drive@gmail.com",
    mediafireEmail: String = "usuario.mfire@mediafire.com",
    onedriveEmail: String = "usuario.office@outlook.com",
    dropboxEmail: String = "usuario.dbx@dropbox.com",
    isRootAvailable: Boolean = false,
    isRootGranted: Boolean = false,
    rootStatusDetails: String = "",
    onRequestRootAccess: () -> Unit = {},
    onRemountSystemRw: () -> Unit = {},
    onOpenCloudManager: () -> Unit = {},
    onOpenWelcomeOnboarding: () -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val scrollState = rememberScrollState()

    var cacheSizeMb by remember { mutableStateOf(42.5f) }

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
                            Text(
                                text = "Configurações Avançadas",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Personalização, Desempenho & Segurança",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                onRestoreDefaults()
                            }
                        ) {
                            Text("Restaurar", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {

                    // CATEGORY 1: APARÊNCIA & PERSONALIZAÇÃO
                    SettingsSectionCard(
                        title = "Aparência & Personalização",
                        icon = Icons.Default.Palette
                    ) {
                        // Theme Mode Selector
                        Text(
                            text = "Modo do Tema",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeModeCard(
                                title = "Claro",
                                isSelected = currentThemeMode == ThemeMode.LIGHT,
                                onClick = { onSelectThemeMode(ThemeMode.LIGHT) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeModeCard(
                                title = "Escuro",
                                isSelected = currentThemeMode == ThemeMode.DARK,
                                onClick = { onSelectThemeMode(ThemeMode.DARK) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeModeCard(
                                title = "Automático",
                                isSelected = currentThemeMode == ThemeMode.SYSTEM,
                                onClick = { onSelectThemeMode(ThemeMode.SYSTEM) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Accent Colors
                        Text(
                            text = "Cor de Destaque",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val systemDark = isSystemInDarkTheme()
                            val isDark = when (currentThemeMode) {
                                ThemeMode.DARK -> true
                                ThemeMode.LIGHT -> false
                                ThemeMode.SYSTEM -> systemDark
                            }
                            val activeCustomPreset = remember(customAccentColorHex) {
                                findCustomColorPreset(customAccentColorHex)
                            }
                            val activeCustomColor = remember(customAccentColorHex, activeCustomPreset, isDark) {
                                if (activeCustomPreset != null) {
                                    if (isDark) activeCustomPreset.darkColor else activeCustomPreset.color
                                } else {
                                    Color(customAccentColorHex)
                                }
                            }

                            // 4 Cores Principais no Topo (Azul Claro, Roxo, Preto/Branco, Personalizado)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AccentColorOption.values().forEach { option ->
                                    val isSelected = currentAccent == option
                                    val isPersonalizado = option == AccentColorOption.PERSONALIZADO
                                    val isPreto = option == AccentColorOption.PRETO

                                    val customGradient = remember {
                                        Brush.sweepGradient(
                                            colors = listOf(
                                                Color(0xFFEF4444),
                                                Color(0xFFF97316),
                                                Color(0xFFEAB308),
                                                Color(0xFF22C55E),
                                                Color(0xFF06B6D4),
                                                Color(0xFF6366F1),
                                                Color(0xFFA855F7),
                                                Color(0xFFEF4444)
                                            )
                                        )
                                    }

                                    val cardBorderColor = if (isSelected) {
                                        when {
                                            isPreto -> if (isDark) Color(0xFFE2E8F0) else Color(0xFF18181B)
                                            isPersonalizado -> activeCustomColor
                                            else -> if (isDark) option.darkColor else option.color
                                        }
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    }

                                    val cardBgColor = if (isSelected) {
                                        when {
                                            isPreto -> if (isDark) Color(0xFF27272A) else Color(0xFF18181B).copy(alpha = 0.08f)
                                            isPersonalizado -> activeCustomColor.copy(alpha = 0.12f)
                                            else -> (if (isDark) option.darkColor else option.color).copy(alpha = 0.12f)
                                        }
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    }

                                    Surface(
                                        onClick = { onSelectAccent(option) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = cardBgColor,
                                        shadowElevation = 0.dp,
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = cardBorderColor
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .then(
                                                        if (isPersonalizado) {
                                                            Modifier.background(customGradient).border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                                                        } else if (isPreto) {
                                                            val pretoBg = if (isDark) Color(0xFFE2E8F0) else Color(0xFF18181B)
                                                            Modifier.background(pretoBg).border(1.dp, if (isDark) Color(0xFF94A3B8) else Color(0xFF71717A), CircleShape)
                                                        } else {
                                                            Modifier.background(option.color).border(1.dp, Color.White, CircleShape)
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = if (isPreto && isDark) Color(0xFF0F172A) else Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = option.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = if (option == AccentColorOption.PRETO || option == AccentColorOption.PERSONALIZADO) 8.5.sp else 9.5.sp,
                                                color = if (isSelected) {
                                                    when {
                                                        isPreto -> if (isDark) Color.White else Color(0xFF18181B)
                                                        isPersonalizado -> activeCustomColor
                                                        else -> if (isDark) option.darkColor else option.color
                                                    }
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // 16 Cores Variadas e Exclusivas Abaixo
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (currentAccent == AccentColorOption.PERSONALIZADO) activeCustomColor.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Palette,
                                                contentDescription = null,
                                                tint = if (currentAccent == AccentColorOption.PERSONALIZADO) activeCustomColor else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "16 Cores & Variações",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        if (currentAccent == AccentColorOption.PERSONALIZADO) {
                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = activeCustomColor.copy(alpha = 0.15f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, activeCustomColor.copy(alpha = 0.4f))
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .clip(CircleShape)
                                                            .background(activeCustomColor)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(
                                                        text = activeCustomPreset?.name ?: "Personalizada",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = activeCustomColor,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Grade 4x4 com as 16 cores
                                    PredefinedCustomColors.chunked(4).forEach { colorRow ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                             colorRow.forEach { preset ->
                                                val isPresetSelected = currentAccent == AccentColorOption.PERSONALIZADO && 
                                                    (customAccentColorHex == preset.hexValue || activeCustomPreset?.name == preset.name)
                                                val targetPresetColor = if (isDark) preset.darkColor else preset.color

                                                Surface(
                                                    onClick = {
                                                        onSelectAccent(AccentColorOption.PERSONALIZADO)
                                                        onSelectCustomColor(preset.hexValue)
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isPresetSelected) targetPresetColor.copy(alpha = 0.18f) else Color.Transparent,
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        width = if (isPresetSelected) 1.5.dp else 0.5.dp,
                                                        color = if (isPresetSelected) targetPresetColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(58.dp)
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center,
                                                        modifier = Modifier.padding(2.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .clip(CircleShape)
                                                                .background(preset.color)
                                                                .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isPresetSelected) {
                                                                val isLightBg = preset.color.luminance() > 0.55f
                                                                Icon(
                                                                    Icons.Default.Check,
                                                                    contentDescription = null,
                                                                    tint = if (isLightBg) Color(0xFF0F172A) else Color.White,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.height(2.dp))

                                                        Text(
                                                            text = preset.name,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 9.5.sp,
                                                            fontWeight = if (isPresetSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isPresetSelected) {
                                                                if (isDark) preset.darkColor else preset.color
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            },
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            }
                                            if (colorRow.size < 4) {
                                                repeat(4 - colorRow.size) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Toggle Options
                        SwitchSettingRow(
                            title = "Mostrar Arquivos e Pastas Ocultos",
                            subtitle = "Exibe itens que começam com ponto (.) no sistema",
                            icon = Icons.Outlined.Visibility,
                            checked = showHiddenFiles,
                            onCheckedChange = { onToggleShowHiddenFiles(it) }
                        )

                        SwitchSettingRow(
                            title = "Mostrar Extensões nos Nomes",
                            subtitle = "Exibe o sufixo .pdf, .jpg, .zip nos arquivos",
                            icon = Icons.Outlined.Extension,
                            checked = showExtensions,
                            onCheckedChange = { onToggleShowExtensions(it) }
                        )
                    }

                    // CATEGORY: TRANSIÇÕES ENTRE PASTAS (5 ESTILOS SUAVES)
                    SettingsSectionCard(
                        title = "Transição entre Pastas",
                        icon = Icons.Default.Animation
                    ) {
                        Text(
                            text = "Selecione o estilo visual e dinâmica da transição suave de 120Hz ao abrir pastas e voltar aos diretórios anteriores.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FolderTransitionType.values().forEach { transition ->
                                val isSelected = currentFolderTransition == transition

                                val (icon, badgeLabel) = when (transition) {
                                    FolderTransitionType.MATERIAL_SLIDE -> Pair(Icons.Default.SwapHoriz, "Padrão M3")
                                    FolderTransitionType.ZOOM_EXPAND -> Pair(Icons.Default.ZoomIn, "Zoom 3D")
                                    FolderTransitionType.VERTICAL_SLIDE -> Pair(Icons.Default.SwapVert, "Gaveta")
                                    FolderTransitionType.FADE_THROUGH -> Pair(Icons.Default.BlurOn, "Esmaecer")
                                    FolderTransitionType.STACK_OVERLAY -> Pair(Icons.Default.Layers, "Camadas")
                                }

                                Surface(
                                    onClick = { onSelectFolderTransition(transition) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = transition.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                            else MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 2.dp)
                                                ) {
                                                    Text(
                                                        text = badgeLabel,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = transition.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.5.sp,
                                                lineHeight = 15.sp
                                            )
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { onSelectFolderTransition(transition) },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // CATEGORY 2: DESEMPENHO & CACHE
                    SettingsSectionCard(
                        title = "Desempenho & Memória",
                        icon = Icons.Default.Speed
                    ) {
                        SwitchSettingRow(
                            title = "Exibir Miniaturas de Fotos e Vídeos",
                            subtitle = "Mostrar pré-visualizações visuais na grade e na lista de arquivos",
                            icon = Icons.Outlined.Image,
                            checked = showThumbnails,
                            onCheckedChange = { onToggleShowThumbnails(it) }
                        )

                        SwitchSettingRow(
                            title = "Leitura Paralela de Diretórios",
                            subtitle = "Acelera a abertura de pastas com milhares de itens",
                            icon = Icons.Outlined.Bolt,
                            checked = parallelDirectoryReading,
                            onCheckedChange = { onToggleParallelDirectoryReading(it) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Cache clean row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cache de Miniaturas & Dados",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Espaço ocupado atual: ${"%.1f".format(cacheSizeMb)} MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    try {
                                        val coilLoader = coil.Coil.imageLoader(context)
                                        coilLoader.memoryCache?.clear()
                                        coilLoader.diskCache?.clear()
                                    } catch (_: Exception) {}
                                    try {
                                        context.cacheDir.resolve("arcbox_thumbnails").deleteRecursively()
                                    } catch (_: Exception) {}
                                    cacheSizeMb = 0.0f
                                    Toast.makeText(context, "Cache limpo com sucesso!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Limpar", fontSize = 12.sp)
                            }
                        }
                    }

                    // CATEGORY 3: COMPRESSÃO & COMPACTAÇÃO
                    SettingsSectionCard(
                        title = "Compactação de Arquivos",
                        icon = Icons.Default.FolderZip
                    ) {
                        Text(
                            text = "Nível de Compressão ZIP Padrão",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Rápida", "Normal", "Máxima").forEach { level ->
                                val isSelected = compressionLevel.equals(level, ignoreCase = true) || (level == "Normal" && (compressionLevel.equals("Balanceada", ignoreCase = true) || compressionLevel.equals("Normal", ignoreCase = true)))
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectCompressionLevel(level) },
                                    modifier = Modifier.weight(1f),
                                    label = {
                                        Text(
                                            text = level,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    // CATEGORY 4: SEGURANÇA DO APLICATIVO
                    SettingsSectionCard(
                        title = "Segurança do Aplicativo",
                        icon = Icons.Default.Security
                    ) {
                        SwitchSettingRow(
                            title = "Bloqueio por Biometria",
                            subtitle = "Exigir autenticação biométrica ao abrir o aplicativo",
                            icon = Icons.Outlined.Fingerprint,
                            checked = biometricLock,
                            onCheckedChange = { targetState ->
                                if (targetState && activity != null) {
                                    BiometricAuthHelper.promptBiometric(
                                        activity = activity,
                                        title = "Ativar Bloqueio por Biometria",
                                        subtitle = "Confirme sua digital ou credencial do dispositivo",
                                        onSuccess = { onToggleBiometricLock(true) },
                                        onError = { _, _ -> /* cancelled or failed */ },
                                        onFailed = { /* failed */ }
                                    )
                                } else {
                                    onToggleBiometricLock(targetState)
                                }
                            }
                        )
                    }

                    // CATEGORY 5: GERENCIAMENTO & LIXEIRA
                    SettingsSectionCard(
                        title = "Operações & Lixeira",
                        icon = Icons.Default.Delete
                    ) {
                        SwitchSettingRow(
                            title = "Solicitar Confirmação ao Excluir",
                            subtitle = "Evita exclusões acidentais antes de mover para a Lixeira",
                            icon = Icons.Outlined.Warning,
                            checked = confirmDelete,
                            onCheckedChange = { onToggleConfirmDelete(it) }
                        )

                        SwitchSettingRow(
                            title = "Excluir permanentemente",
                            subtitle = "Exclui direto sem enviar para a Lixeira",
                            icon = Icons.Outlined.DeleteForever,
                            checked = deletePermanently,
                            onCheckedChange = { onToggleDeletePermanently(it) }
                        )

                        SwitchSettingRow(
                            title = "Salvar Histórico de Operações",
                            subtitle = "Registra cópias e movimentações realizadas",
                            icon = Icons.Outlined.History,
                            checked = keepHistory,
                            onCheckedChange = { onToggleKeepHistory(it) }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Exclusão Automática da Lixeira",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Nunca", "7 dias", "30 dias").forEach { opt ->
                                val isSelected = trashAutoCleanDays.equals(opt, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectTrashAutoCleanDays(opt) },
                                    modifier = Modifier.weight(1f),
                                    label = {
                                        Text(
                                            text = opt,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    // CATEGORY: CLOUD SYNC & STORAGE UNITS
                    SettingsSectionCard(
                        title = "Sincronização de Nuvem & Contas",
                        icon = Icons.Default.Cloud
                    ) {
                        Text(
                            text = "Acesse seus arquivos no Mega, Google Drive, Mediafire, OneDrive e Dropbox com login e senha.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedButton(
                            onClick = onOpenCloudManager,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Abrir Gerenciador de Nuvem", fontWeight = FontWeight.Bold)
                        }
                        
                        CloudSyncRow(
                            name = "Mega",
                            isConnected = isMegaConnected,
                            userEmail = megaEmail,
                            onToggleConnect = onToggleMegaConnected
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        
                        CloudSyncRow(
                            name = "Google Drive",
                            isConnected = isDriveConnected,
                            userEmail = driveEmail,
                            onToggleConnect = onToggleDriveConnected
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        
                        CloudSyncRow(
                            name = "Mediafire",
                            isConnected = isMediafireConnected,
                            userEmail = mediafireEmail,
                            onToggleConnect = onToggleMediafireConnected
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        
                        CloudSyncRow(
                            name = "OneDrive",
                            isConnected = isOnedriveConnected,
                            userEmail = onedriveEmail,
                            onToggleConnect = onToggleOnedriveConnected
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        
                        CloudSyncRow(
                            name = "Dropbox",
                            isConnected = isDropboxConnected,
                            userEmail = dropboxEmail,
                            onToggleConnect = onToggleDropboxConnected
                        )
                    }

                    // CATEGORY 7: ACESSO SUPERUSUÁRIO (ROOT)
                    SettingsSectionCard(
                        title = "Acesso Superusuário (Root)",
                        icon = Icons.Default.Security
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isRootGranted) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else if (isRootAvailable) {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(
                                    imageVector = if (isRootGranted) Icons.Default.CheckCircle else if (isRootAvailable) Icons.Default.Security else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isRootGranted) MaterialTheme.colorScheme.primary else if (isRootAvailable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (isRootGranted) "Superusuário Concedido (UID 0)" else if (isRootAvailable) "Binário SU Detectado" else "Sem Acesso Root",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isRootGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (rootStatusDetails.isNotBlank()) rootStatusDetails else if (isRootAvailable) "Dispositivo rooteado. A unidade 'Raiz' está disponível para exploração total." else "O armazenamento 'Raiz' só é exibido quando privilégios de superusuário estão disponíveis.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onRequestRootAccess,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isRootGranted) "Reverificar Root" else "Solicitar Root (su)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (isRootAvailable) {
                                OutlinedButton(
                                    onClick = onRemountSystemRw,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Remontar R/W", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // CATEGORY 8: SOBRE O APP
                    SettingsSectionCard(
                        title = "Sobre o Arcbox Storage",
                        icon = Icons.Default.Info
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ArcboxLogoIcon(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Arcbox File Manager",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Versão 2.8.5",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
fun SwitchSettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ThemeModeCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun CloudSyncRow(
    name: String,
    isConnected: Boolean,
    userEmail: String = "",
    onToggleConnect: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isConnected) "Conectado • ${userEmail.ifEmpty { "Sincronizado" }}" else "Não conectado",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        
        Button(
            onClick = { onToggleConnect(!isConnected) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isConnected) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text(
                text = if (isConnected) "Desconectar" else "Conectar",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

