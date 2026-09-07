package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.data.update.UpdateConfig
import com.example.data.update.UpdateReleaseInfo
import com.example.data.update.UpdateStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AppUpdateSection(
    updateStatus: UpdateStatus,
    autoCheckEnabled: Boolean,
    wifiOnlyEnabled: Boolean,
    lastCheckedTime: Long,
    repoOwner: String,
    repoName: String,
    githubPatToken: String = "",
    onCheckForUpdates: () -> Unit,
    onDownloadAndInstall: (UpdateReleaseInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstallDownloadedApk: () -> Unit,
    onToggleAutoCheck: (Boolean) -> Unit,
    onToggleWifiOnly: (Boolean) -> Unit,
    onSaveCustomRepo: (String, String, String) -> Unit,
    onRequestInstallPermission: () -> Unit
) {
    val context = LocalContext.current
    var showChangelogModal by remember { mutableStateOf(false) }

    SettingsSectionCard(
        title = "Atualizações do ArcBox",
        icon = Icons.Default.SystemUpdate
    ) {
        // Status & Version Banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = when (updateStatus) {
                        is UpdateStatus.UpdateAvailable -> MaterialTheme.colorScheme.primaryContainer
                        is UpdateStatus.DownloadCompleted -> Color(0xFF10B981).copy(alpha = 0.2f)
                        is UpdateStatus.Checking, is UpdateStatus.Downloading -> MaterialTheme.colorScheme.tertiaryContainer
                        is UpdateStatus.Error -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when (updateStatus) {
                            is UpdateStatus.Checking -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            is UpdateStatus.UpdateAvailable -> {
                                Icon(
                                    imageVector = Icons.Default.NewReleases,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            is UpdateStatus.Downloading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            is UpdateStatus.DownloadCompleted -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            is UpdateStatus.Error -> {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Versão Atual Instalada: v${UpdateConfig.CURRENT_VERSION_NAME}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (updateStatus) {
                            is UpdateStatus.Checking -> "Consultando GitHub Releases..."
                            is UpdateStatus.UpdateAvailable -> "Nova versão v${updateStatus.releaseInfo.targetVersionName} disponível!"
                            is UpdateStatus.Downloading -> "Baixando atualização: ${updateStatus.progressPercent}%"
                            is UpdateStatus.DownloadCompleted -> "Download concluído e verificado (SHA-256 válido)"
                            is UpdateStatus.NoUpdateAvailable -> "O ArcBox está atualizado para a versão mais recente."
                            is UpdateStatus.Error -> "Falha: ${updateStatus.message}"
                            else -> "Nenhuma verificação pendente"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (updateStatus) {
                            is UpdateStatus.UpdateAvailable -> MaterialTheme.colorScheme.primary
                            is UpdateStatus.DownloadCompleted -> Color(0xFF10B981)
                            is UpdateStatus.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (lastCheckedTime > 0) {
                        val formattedDate = remember(lastCheckedTime) {
                            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            sdf.format(Date(lastCheckedTime))
                        }
                        Text(
                            text = "Última verificação: $formattedDate",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Actions and Dynamic Update Card
        AnimatedVisibility(
            visible = updateStatus is UpdateStatus.UpdateAvailable,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (updateStatus is UpdateStatus.UpdateAvailable) {
                val info = updateStatus.releaseInfo
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = info.releaseName.ifBlank { "ArcBox v${info.targetVersionName}" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Tamanho: ${formatFileSize(info.apkSizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (info.isMandatory) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = "Importante",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Brief preview of changelog
                        val previewText = info.changelog.take(160).trim()
                        Text(
                            text = previewText + if (info.changelog.length > 160) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showChangelogModal = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ver Changelog", fontSize = 12.sp)
                            }

                            Button(
                                onClick = { onDownloadAndInstall(info) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Atualizar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Downloading Progress Card
        AnimatedVisibility(
            visible = updateStatus is UpdateStatus.Downloading,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (updateStatus is UpdateStatus.Downloading) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Baixando atualização...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${updateStatus.progressPercent}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { updateStatus.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatFileSize(updateStatus.downloadedBytes)} / ${formatFileSize(updateStatus.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            TextButton(
                                onClick = onCancelDownload,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Cancelar", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Download Completed Ready to Install Card
        AnimatedVisibility(
            visible = updateStatus is UpdateStatus.DownloadCompleted,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF10B981).copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Pronto para Instalar",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF065F46)
                            )
                            Text(
                                text = "APK verificado com sucesso. Toque abaixo para prosseguir com a instalação do sistema.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onInstallDownloadedApk,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Instalar Nova Versão Agora", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Check Button
        Button(
            onClick = onCheckForUpdates,
            enabled = updateStatus !is UpdateStatus.Checking && updateStatus !is UpdateStatus.Downloading,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (updateStatus is UpdateStatus.Checking) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verificando...", fontSize = 12.sp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verificar Atualizações", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Auto Check Switch
        SwitchSettingRow(
            title = "Verificar Automaticamente",
            subtitle = "Buscar novas versões ao inicializar o gerenciador",
            icon = Icons.Outlined.Autorenew,
            checked = autoCheckEnabled,
            onCheckedChange = onToggleAutoCheck
        )

        // Wi-Fi Only Switch
        SwitchSettingRow(
            title = "Baixar Apenas via Wi-Fi",
            subtitle = "Economiza a franquia de dados móveis 3G/4G/5G",
            icon = Icons.Outlined.Wifi,
            checked = wifiOnlyEnabled,
            onCheckedChange = onToggleWifiOnly
        )

        // Unknown Sources Permission Shortcut
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
                    text = "Permissão de Instalação",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Permitir ao ArcBox instalar atualizações sem passar pela Google Play Store",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onRequestInstallPermission,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Ajustar", fontSize = 12.sp)
            }
        }
    }

    // Modal de Changelog Completo
    if (showChangelogModal && updateStatus is UpdateStatus.UpdateAvailable) {
        val info = updateStatus.releaseInfo
        AlertDialog(
            onDismissRequest = { showChangelogModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Changelog ${info.tagName}")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Data de Publicação: ${info.publishedAt.take(10)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!info.expectedSha256.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SHA-256: ${info.expectedSha256}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = info.changelog.ifBlank { "Nenhuma nota de versão detalhada fornecida nesta release." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showChangelogModal = false
                        onDownloadAndInstall(info)
                    }
                ) {
                    Text("Baixar e Atualizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangelogModal = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}
