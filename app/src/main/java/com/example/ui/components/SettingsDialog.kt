package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.ThemeMode
import com.example.ui.theme.AccentColorOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcboxSettingsModal(
    currentThemeMode: ThemeMode,
    currentAccent: AccentColorOption,
    deletePermanently: Boolean,
    onToggleDeletePermanently: (Boolean) -> Unit,
    confirmDelete: Boolean,
    onToggleConfirmDelete: (Boolean) -> Unit,
    showThumbnails: Boolean = true,
    onToggleShowThumbnails: (Boolean) -> Unit = {},
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectAccent: (AccentColorOption) -> Unit,
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
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Config states (toggles & preferences)
    var showHiddenFiles by remember { mutableStateOf(false) }
    var showExtensions by remember { mutableStateOf(true) }
    var hdThumbnails by remember { mutableStateOf(true) }
    var confirmDeleteLocal by remember(confirmDelete) { mutableStateOf(confirmDelete) }
    var deletePermanentlyLocal by remember(deletePermanently) { mutableStateOf(deletePermanently) }
    var keepHistory by remember { mutableStateOf(true) }
    var biometricLock by remember { mutableStateOf(false) }
    var autoLockVault by remember { mutableStateOf(true) }
    var autoHttpServer by remember { mutableStateOf(false) }
    var parallelDirectoryReading by remember { mutableStateOf(true) }
    var compressionLevel by remember { mutableStateOf("Balanceada (Padrão)") }
    var trashAutoCleanDays by remember { mutableStateOf("30 Dias") }
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
                                onSelectThemeMode(ThemeMode.LIGHT)
                                onSelectAccent(AccentColorOption.AZUL_CLARO)
                                showHiddenFiles = false
                                showExtensions = true
                                hdThumbnails = true
                                confirmDeleteLocal = true
                                deletePermanentlyLocal = false
                                onToggleConfirmDelete(true)
                                onToggleDeletePermanently(false)
                                keepHistory = true
                                biometricLock = false
                                Toast.makeText(context, "Configurações restauradas para o padrão", Toast.LENGTH_SHORT).show()
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
                            AccentColorOption.values().toList().chunked(4).forEach { rowOptions ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowOptions.forEach { option ->
                                        val isSelected = currentAccent == option
                                        Surface(
                                            onClick = { onSelectAccent(option) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) option.color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) option.color else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
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
                                                        .background(option.color)
                                                        .border(1.dp, Color.White, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = option.label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 10.5.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                    if (rowOptions.size < 4) {
                                        repeat(4 - rowOptions.size) {
                                            Spacer(modifier = Modifier.weight(1f))
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
                            onCheckedChange = { showHiddenFiles = it }
                        )

                        SwitchSettingRow(
                            title = "Mostrar Extensões nos Nomes",
                            subtitle = "Exibe o sufixo .pdf, .jpg, .zip nos arquivos",
                            icon = Icons.Outlined.Extension,
                            checked = showExtensions,
                            onCheckedChange = { showExtensions = it }
                        )
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
                            onCheckedChange = { parallelDirectoryReading = it }
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Rápida", "Balanceada (Padrão)", "Máxima").forEach { level ->
                                val isSelected = compressionLevel == level
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { compressionLevel = level },
                                    label = { Text(level, fontSize = 11.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    // CATEGORY 4: SEGURANÇA & COFRE
                    SettingsSectionCard(
                        title = "Segurança & Cofre Criptografado",
                        icon = Icons.Default.Security
                    ) {
                        SwitchSettingRow(
                            title = "Bloqueio por PIN / Biometria",
                            subtitle = "Exigir autenticação ao abrir o gerenciador",
                            icon = Icons.Outlined.Fingerprint,
                            checked = biometricLock,
                            onCheckedChange = { biometricLock = it }
                        )

                        SwitchSettingRow(
                            title = "Autobloqueio do Cofre ao Sair",
                            subtitle = "Bloqueia automaticamente o cofre ao minimizar o app",
                            icon = Icons.Outlined.Lock,
                            checked = autoLockVault,
                            onCheckedChange = { autoLockVault = it }
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Algoritmo de Criptografia: AES-256 GCM (Hardware Acceleration)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                            checked = confirmDeleteLocal,
                            onCheckedChange = { 
                                confirmDeleteLocal = it
                                onToggleConfirmDelete(it)
                            }
                        )

                        SwitchSettingRow(
                            title = "Excluir permanentemente",
                            subtitle = "Ative para excluir de forma permanente e desative para enviar à lixeira por padrão",
                            icon = Icons.Outlined.DeleteForever,
                            checked = deletePermanentlyLocal,
                            onCheckedChange = {
                                deletePermanentlyLocal = it
                                onToggleDeletePermanently(it)
                            }
                        )

                        SwitchSettingRow(
                            title = "Salvar Histórico de Operações",
                            subtitle = "Registra cópias, movimentações e exclusões para auditoria",
                            icon = Icons.Outlined.History,
                            checked = keepHistory,
                            onCheckedChange = { keepHistory = it }
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Nunca", "7 Dias", "30 Dias").forEach { opt ->
                                val isSelected = trashAutoCleanDays == opt
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { trashAutoCleanDays = opt },
                                    label = { Text(opt, fontSize = 11.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    // CATEGORY 6: REDE & NUVEM
                    SettingsSectionCard(
                        title = "Rede & Transferência Wi-Fi",
                        icon = Icons.Default.Wifi
                    ) {
                        SwitchSettingRow(
                            title = "Iniciar Servidor HTTP com o App",
                            subtitle = "Permite acessar arquivos pelo navegador no computador",
                            icon = Icons.Outlined.Http,
                            checked = autoHttpServer,
                            onCheckedChange = { autoHttpServer = it }
                        )
                    }

                    // CATEGORY: CLOUD SYNC & STORAGE UNITS
                    SettingsSectionCard(
                        title = "Sincronização de Nuvem & Contas",
                        icon = Icons.Default.Cloud
                    ) {
                        Text(
                            text = "Conecte suas contas de armazenamento em nuvem para sincronizar arquivos e exibi-los em suas unidades de disco.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        CloudSyncRow(
                            name = "Mega",
                            isConnected = isMegaConnected,
                            onToggleConnect = onToggleMegaConnected
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        
                        CloudSyncRow(
                            name = "Google Drive",
                            isConnected = isDriveConnected,
                            onToggleConnect = onToggleDriveConnected
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        
                        CloudSyncRow(
                            name = "Mediafire",
                            isConnected = isMediafireConnected,
                            onToggleConnect = onToggleMediafireConnected
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        
                        CloudSyncRow(
                            name = "OneDrive",
                            isConnected = isOnedriveConnected,
                            onToggleConnect = onToggleOnedriveConnected
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        
                        CloudSyncRow(
                            name = "Dropbox",
                            isConnected = isDropboxConnected,
                            onToggleConnect = onToggleDropboxConnected
                        )
                    }

                    // CATEGORY 7: SOBRE O APP
                    SettingsSectionCard(
                        title = "Sobre o Arcbox Storage",
                        icon = Icons.Default.Info
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.FolderSpecial,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Arcbox File Manager Pro",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Versão 2.8.5 • Build 2026 Android 10-16",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Kotlin Nativo • Jetpack Compose • Clean Architecture",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salvar & Concluir", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
                    text = if (isConnected) "Sincronizado • Exibido em unidades" else "Nuvem não conectada",
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

