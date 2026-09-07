package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.update.UpdateConfig
import com.example.data.update.UpdateReleaseInfo
import com.example.data.update.UpdateStatus

@Composable
fun UpdateNotificationModal(
    updateStatus: UpdateStatus,
    onDownloadAndInstall: (UpdateReleaseInfo) -> Unit,
    onInstallDownloadedApk: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val releaseInfo = when (updateStatus) {
        is UpdateStatus.UpdateAvailable -> updateStatus.releaseInfo
        is UpdateStatus.Downloading -> updateStatus.releaseInfo
        is UpdateStatus.DownloadCompleted -> updateStatus.releaseInfo
        else -> null
    } ?: return

    val isMandatory = releaseInfo.isMandatory
    var showFullChangelog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 440.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Icon, Title & Close Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isMandatory) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (updateStatus is UpdateStatus.DownloadCompleted) Icons.Default.CheckCircle
                                else if (isMandatory) Icons.Default.Warning
                                else Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = if (updateStatus is UpdateStatus.DownloadCompleted) Color(0xFF10B981)
                                else if (isMandatory) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isMandatory) "Atualização Obrigatória"
                            else if (updateStatus is UpdateStatus.DownloadCompleted) "Pronto para Instalar"
                            else "Nova Versão Disponível",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ArcBox v${releaseInfo.targetVersionName} (Atual: v${UpdateConfig.CURRENT_VERSION_NAME})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fechar modal",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Release Specs Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.FolderZip,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formatFileSize(releaseInfo.apkSizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (releaseInfo.publishedAt.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Event,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = releaseInfo.publishedAt.take(10),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Changelog Box
                Text(
                    text = "Novidades e Melhorias:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (showFullChangelog) 220.dp else 110.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = releaseInfo.changelog.ifBlank { "Melhorias de estabilidade, novas ferramentas e otimizações de segurança do sistema." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                if (releaseInfo.changelog.length > 150) {
                    TextButton(
                        onClick = { showFullChangelog = !showFullChangelog },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (showFullChangelog) "Recolher" else "Ver completo",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Downloading Progress UI
                if (updateStatus is UpdateStatus.Downloading) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Baixando pacote oficial...",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${updateStatus.progressPercent}%",
                                style = MaterialTheme.typography.bodyMedium,
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
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            TextButton(
                                onClick = onCancelDownload,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Cancelar", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Action Buttons
                when (updateStatus) {
                    is UpdateStatus.DownloadCompleted -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onInstallDownloadedApk,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Instalar Atualização Agora", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp)
                            ) {
                                Text("Lembrar Mais Tarde / Voltar")
                            }
                        }
                    }
                    is UpdateStatus.Downloading -> {
                        // Handled above in progress card
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isMandatory) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(0.75f)
                                        .defaultMinSize(minHeight = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "Depois",
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            Button(
                                onClick = { onDownloadAndInstall(releaseInfo) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(if (isMandatory) 1f else 1.6f)
                                    .defaultMinSize(minHeight = 48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isMandatory) "Atualizar Agora" else "Baixar Atualização",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
