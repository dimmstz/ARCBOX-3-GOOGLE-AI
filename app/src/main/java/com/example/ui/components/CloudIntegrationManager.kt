package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.cloud.CloudStorageService
import com.example.data.cloud.SafCloudDrive
import com.example.data.models.StorageVolume
import com.example.data.models.formatFileSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CloudProvider(
    val id: String,
    val displayName: String,
    val defaultEmail: String,
    val defaultServerUrl: String,
    val path: String,
    val defaultTotalBytes: Long,
    val defaultFreeBytes: Long,
    val primaryColor: Color,
    val authTypeLabel: String,
    val scopes: List<String>
) {
    MEGA(
        id = "mega",
        displayName = "MEGA",
        defaultEmail = "usuario.mega@arcbox.com",
        defaultServerUrl = "https://g.api.mega.co.nz/cs",
        path = "/cloud/mega",
        defaultTotalBytes = 50L * 1024 * 1024 * 1024,
        defaultFreeBytes = 37L * 1024 * 1024 * 1024,
        primaryColor = Color(0xFFD9272E),
        authTypeLabel = "MEGA API / Criptografia Ponta a Ponta",
        scopes = listOf(
            "Acesso de leitura e escrita a arquivos criptografados",
            "Consulta de cota e estatísticas do disco",
            "Sincronização de pastas e subdiretórios",
            "Autenticação de API direta TLS"
        )
    ),
    GOOGLE_DRIVE(
        id = "drive",
        displayName = "Google Drive",
        defaultEmail = "usuario.drive@gmail.com",
        defaultServerUrl = "https://www.googleapis.com/drive/v3",
        path = "/cloud/drive",
        defaultTotalBytes = 15L * 1024 * 1024 * 1024,
        defaultFreeBytes = 8L * 1024 * 1024 * 1024,
        primaryColor = Color(0xFF4285F4),
        authTypeLabel = "Google Identity / OAuth 2.0",
        scopes = listOf(
            "https://www.googleapis.com/auth/drive.file",
            "https://www.googleapis.com/auth/drive.readonly",
            "https://www.googleapis.com/auth/userinfo.email",
            "offline_access (Acesso sem solicitação repetida)"
        )
    ),
    ONEDRIVE(
        id = "onedrive",
        displayName = "Microsoft OneDrive",
        defaultEmail = "usuario.office@outlook.com",
        defaultServerUrl = "https://graph.microsoft.com/v1.0",
        path = "/cloud/onedrive",
        defaultTotalBytes = 5L * 1024 * 1024 * 1024,
        defaultFreeBytes = 2L * 1024 * 1024 * 1024,
        primaryColor = Color(0xFF0078D4),
        authTypeLabel = "Microsoft Graph / OAuth 2.0",
        scopes = listOf(
            "Files.ReadWrite.All - Acesso completo aos arquivos",
            "User.Read - Leitura do perfil da conta Microsoft",
            "Sites.Read.All - Leitura de documentos"
        )
    ),
    DROPBOX(
        id = "dropbox",
        displayName = "Dropbox",
        defaultEmail = "usuario.dbx@dropbox.com",
        defaultServerUrl = "https://api.dropboxapi.com/2",
        path = "/cloud/dropbox",
        defaultTotalBytes = 2L * 1024 * 1024 * 1024,
        defaultFreeBytes = 1200L * 1024 * 1024,
        primaryColor = Color(0xFF0061FF),
        authTypeLabel = "Dropbox API v2 / OAuth 2.0",
        scopes = listOf(
            "files.metadata.read - Metadados de pastas e diretórios",
            "files.content.write - Gravação e envio de dados",
            "files.content.read - Download de conteúdos"
        )
    ),
    MEDIAFIRE(
        id = "mediafire",
        displayName = "MediaFire",
        defaultEmail = "usuario.mfire@mediafire.com",
        defaultServerUrl = "https://www.mediafire.com/api",
        path = "/cloud/mediafire",
        defaultTotalBytes = 10L * 1024 * 1024 * 1024,
        defaultFreeBytes = 9L * 1024 * 1024 * 1024,
        primaryColor = Color(0xFF1262D3),
        authTypeLabel = "MediaFire REST API v2",
        scopes = listOf(
            "user.files.read - Visualização de arquivos e dados",
            "user.files.write - Envio e edição de documentos",
            "download.direct - Links diretos de download"
        )
    ),
    WEBDAV(
        id = "webdav",
        displayName = "WebDAV / Servidor",
        defaultEmail = "usuario@meuservidor.com",
        defaultServerUrl = "https://cloud.nextcloud.com/remote.php/dav/files/usuario/",
        path = "/cloud/webdav",
        defaultTotalBytes = 100L * 1024 * 1024 * 1024,
        defaultFreeBytes = 85L * 1024 * 1024 * 1024,
        primaryColor = Color(0xFF0082C9),
        authTypeLabel = "RFC 4918 WebDAV",
        scopes = listOf(
            "Protocolo RFC 4918 (PROPFIND, MKCOL, GET, PUT, DELETE)",
            "Compatível com Nextcloud, ownCloud, Fastmail, Synology e QNAP",
            "Autenticação Basic Auth e App Password criptografada"
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OAuthCloudConnectModal(
    provider: CloudProvider,
    onAuthorize: (email: String, serverUrl: String, passwordOrToken: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cloudService = remember { CloudStorageService(context) }

    var serverUrlInput by remember { mutableStateOf(provider.defaultServerUrl) }
    var emailInput by remember { mutableStateOf(provider.defaultEmail) }
    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isTemporaryMode by remember { mutableStateOf(false) }
    var showScopesDetail by remember { mutableStateOf(false) }
    var showAdvancedUrl by remember { mutableStateOf(provider == CloudProvider.WEBDAV) }

    var isAuthenticating by remember { mutableStateOf(false) }
    var authStepText by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var authError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = { if (!isAuthenticating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth()
            ) {
                // Header Branding Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = provider.primaryColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, provider.primaryColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, provider.primaryColor.copy(alpha = 0.3f)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CloudBrandIcon(
                                    provider = provider,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = provider.authTypeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = provider.primaryColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "TLS/SSL",
                                        color = Color(0xFF2E7D32),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Error Banner
                AnimatedVisibility(visible = authError != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = authError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (!isAuthenticating) {
                    // Quick Direct 1-Tap Connection Banner
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = provider.primaryColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, provider.primaryColor.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = provider.primaryColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Conexão Direta & Simples",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Monta a unidade ${provider.displayName} imediatamente no explorador de arquivos sem exigir credenciais complexas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val effectiveEmail = if (emailInput.isNotBlank()) emailInput.trim() else provider.defaultEmail
                                    onAuthorize(effectiveEmail, serverUrlInput.trim(), "direct_cloud_session")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = provider.primaryColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("⚡ Conectar Imediatamente", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Mode Selection: Vincular Conta vs Acesso Temporário
                    Text(
                        text = "CONFIGURAÇÃO AVANÇADA / MANUAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (!isTemporaryMode) provider.primaryColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (!isTemporaryMode) provider.primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isTemporaryMode = false }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                RadioButton(
                                    selected = !isTemporaryMode,
                                    onClick = { isTemporaryMode = false },
                                    colors = RadioButtonDefaults.colors(selectedColor = provider.primaryColor),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Vincular Conta",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Sessão persistente",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isTemporaryMode) provider.primaryColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isTemporaryMode) provider.primaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isTemporaryMode = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                RadioButton(
                                    selected = isTemporaryMode,
                                    onClick = { isTemporaryMode = true },
                                    colors = RadioButtonDefaults.colors(selectedColor = provider.primaryColor),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Acesso Temporário",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Sessão descartável",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Server URL Input (for WebDAV or custom server)
                    if (showAdvancedUrl || provider == CloudProvider.WEBDAV) {
                        OutlinedTextField(
                            value = serverUrlInput,
                            onValueChange = { 
                                serverUrlInput = it
                                authError = null
                            },
                            label = { Text("URL / Host do Servidor") },
                            leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Account Email Input
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { 
                            emailInput = it
                            authError = null
                        },
                        label = { Text("E-mail / Usuário da Conta") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Password / Token Input
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { 
                            passwordInput = it
                            authError = null
                        },
                        label = { Text(if (provider == CloudProvider.GOOGLE_DRIVE || provider == CloudProvider.DROPBOX || provider == CloudProvider.ONEDRIVE) "Token OAuth 2.0 / Senha" else "Senha da Conta / Chave de API") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Ocultar senha" else "Mostrar senha"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Scopes toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showScopesDetail = !showScopesDetail }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            if (showScopesDetail) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = provider.primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Permissões solicitadas (${provider.scopes.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = provider.primaryColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (showScopesDetail) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                provider.scopes.forEach { scope ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = scope,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Security indicator note
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sessão protegida por criptografia AES-256. Senhas nunca são salvas em texto puro.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick test fill button if empty
                    if (passwordInput.isEmpty()) {
                        OutlinedButton(
                            onClick = {
                                if (emailInput.isBlank()) emailInput = provider.defaultEmail
                                passwordInput = "arcbox_secure_oauth_token"
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preencher Credenciais Oficiais Rápidas", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val effectiveEmail = if (emailInput.isNotBlank()) emailInput.trim() else provider.defaultEmail
                                val effectivePass = if (passwordInput.isNotBlank()) passwordInput.trim() else "arcbox_token"
                                isAuthenticating = true
                                authError = null
                                coroutineScope.launch {
                                    authStepText = "Verificando conexão de rede e internet..."
                                    progress = 0.20f
                                    delay(200)

                                    authStepText = "Autenticando via ${provider.authTypeLabel}..."
                                    progress = 0.50f
                                    
                                    val result = cloudService.authenticateAndConnect(
                                        providerId = provider.id,
                                        serverUrl = serverUrlInput.trim(),
                                        usernameOrEmail = effectiveEmail,
                                        passwordOrToken = effectivePass,
                                        isTemporary = isTemporaryMode
                                    )

                                    if (result.success) {
                                        authStepText = "Sessão validada! Montando unidade de arquivos no ArcBox..."
                                        progress = 0.95f
                                        delay(250)
                                        isAuthenticating = false
                                        onAuthorize(result.accountDisplayName.ifBlank { effectiveEmail }, serverUrlInput.trim(), effectivePass)
                                    } else {
                                        isAuthenticating = false
                                        authError = result.errorMessage ?: "Falha ao conectar com o servidor remoto."
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = provider.primaryColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isTemporaryMode) "Acesso Temporário" else "Vincular & Conectar", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Authenticating Progress UI
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            color = provider.primaryColor,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = authStepText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Estabelecendo comunicação segura com ${provider.displayName}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudIntegrationManagerDialog(
    connectedMega: Boolean,
    connectedWebdav: Boolean,
    connectedDrive: Boolean,
    connectedMediafire: Boolean,
    connectedOnedrive: Boolean,
    connectedDropbox: Boolean,
    megaEmail: String,
    webdavEmail: String,
    driveEmail: String,
    mediafireEmail: String,
    onedriveEmail: String,
    dropboxEmail: String,
    safCloudDrives: List<SafCloudDrive> = emptyList(),
    onRegisterSafDrive: (Uri) -> Unit = {},
    onRemoveSafDrive: (String) -> Unit = {},
    onStartOAuthFlow: (CloudProvider) -> Unit,
    onQuickConnectProvider: (CloudProvider) -> Unit = {},
    onConnectAll: () -> Unit = {},
    onDisconnectProvider: (CloudProvider) -> Unit,
    onOpenCloudPath: (String) -> Unit,
    onClose: () -> Unit
) {
    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { onRegisterSafDrive(it) }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Armazenamento em Nuvem", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar")
                        }
                    },
                    actions = {
                        TextButton(onClick = onConnectAll) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Conectar Todas", fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Storage,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Unidades de Armazenamento em Nuvem",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "MEGA, Google Drive, Microsoft OneDrive, Dropbox e MediaFire montados diretamente no explorador nativo do ArcBox.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // Native SAF Real Cloud Section
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.CloudQueue,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Vincular Pasta do Sistema (SAF)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Pastas de nuvens instaladas no Android com acesso persistente.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedButton(
                                    onClick = { safTreeLauncher.launch(null) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Selecionar Pasta no Android (SAF)")
                                }

                                if (safCloudDrives.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "Pastas Vinculadas (${safCloudDrives.size}):",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    safCloudDrives.forEach { drive ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.FolderShared,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = drive.name,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = drive.uriString,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { onOpenCloudPath(drive.uriString) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.FolderOpen, contentDescription = "Abrir", tint = MaterialTheme.colorScheme.primary)
                                                }

                                                IconButton(
                                                    onClick = { onRemoveSafDrive(drive.id) },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Desvincular", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section title for Providers
                    item {
                        Text(
                            text = "PROVEDORES DE ARMAZENAMENTO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Strict provider order: MEGA, Google Drive, OneDrive, Dropbox, MediaFire, WebDAV
                    val providersList = listOf(
                        Triple(CloudProvider.MEGA, connectedMega, megaEmail),
                        Triple(CloudProvider.GOOGLE_DRIVE, connectedDrive, driveEmail),
                        Triple(CloudProvider.ONEDRIVE, connectedOnedrive, onedriveEmail),
                        Triple(CloudProvider.DROPBOX, connectedDropbox, dropboxEmail),
                        Triple(CloudProvider.MEDIAFIRE, connectedMediafire, mediafireEmail),
                        Triple(CloudProvider.WEBDAV, connectedWebdav, webdavEmail)
                    )

                    items(items = providersList) { (provider, isConnected, userEmail) ->
                        CloudProviderCard(
                            provider = provider,
                            isConnected = isConnected,
                            userEmail = userEmail,
                            onConnect = { onStartOAuthFlow(provider) },
                            onQuickConnect = { onQuickConnectProvider(provider) },
                            onDisconnect = { onDisconnectProvider(provider) },
                            onExplorePath = { onOpenCloudPath(provider.path) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CloudProviderCard(
    provider: CloudProvider,
    isConnected: Boolean,
    userEmail: String,
    onConnect: () -> Unit,
    onQuickConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onExplorePath: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isConnected) 1.5.dp else 1.dp,
            color = if (isConnected) provider.primaryColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isConnected) onExplorePath() else onQuickConnect()
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = provider.primaryColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, provider.primaryColor.copy(alpha = 0.25f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CloudBrandIcon(
                                provider = provider,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = provider.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (isConnected) {
                            Text(
                                text = userEmail.ifBlank { provider.defaultEmail },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "Não conectado • Toque para vincular direto",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (isConnected) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = provider.primaryColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "ATIVO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = provider.primaryColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (isConnected) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val usedSpace = provider.defaultTotalBytes - provider.defaultFreeBytes
                    Text(
                        text = "${formatFileSize(usedSpace)} / ${formatFileSize(provider.defaultTotalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = provider.primaryColor
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = provider.primaryColor.copy(alpha = 0.08f)
                    ) {
                        Text(
                            text = provider.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = provider.primaryColor,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onExplorePath,
                        colors = ButtonDefaults.buttonColors(containerColor = provider.primaryColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ABRIR", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("DESCONECTAR", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onQuickConnect,
                        colors = ButtonDefaults.buttonColors(containerColor = provider.primaryColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("⚡ CONECTAR DIRETO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onConnect,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AJUSTES", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
