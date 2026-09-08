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
        defaultEmail = "",
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
        defaultEmail = "",
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
        defaultEmail = "",
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
        defaultEmail = "",
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
        defaultEmail = "",
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
        defaultEmail = "",
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
    val cloudService = remember(context) { CloudStorageService.getInstance(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Email/Password, 1 = Token/Session
    var serverUrlInput by remember { mutableStateOf(provider.defaultServerUrl) }
    var emailInput by remember { mutableStateOf(provider.defaultEmail) }
    var passwordInput by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var isAuthenticating by remember { mutableStateOf(false) }
    var authStepText by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Dialog(
        onDismissRequest = {
            if (!isAuthenticating) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DisposableEffect(Unit) {
            onDispose {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = provider.primaryColor.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, provider.primaryColor.copy(alpha = 0.3f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CloudBrandIcon(
                                provider = provider,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Conectar ao ${provider.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Navegue em seus arquivos diretamente no ArcBox",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isAuthenticating,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Error alert
                AnimatedVisibility(visible = authError != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = authError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (!isAuthenticating) {
                    // Method selection tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        contentColor = provider.primaryColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0; authError = null },
                            text = { Text("E-mail e Senha", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1; authError = null },
                            text = { Text("Token / Chave", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (provider == CloudProvider.WEBDAV) {
                        OutlinedTextField(
                            value = serverUrlInput,
                            onValueChange = { serverUrlInput = it; authError = null },
                            label = { Text("URL do Servidor") },
                            leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (selectedTab == 0) {
                        // Email field
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it; authError = null },
                            label = { Text("E-mail da conta ${provider.displayName}") },
                            placeholder = { Text("exemplo@email.com") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Password field
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it; authError = null },
                            label = { Text("Senha da conta") },
                            placeholder = { Text("Digite sua senha") },
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Token field
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it; authError = null },
                            label = { Text("Chave de Sessão / Token (sid)") },
                            placeholder = { Text("Cole o token ou sid aqui") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Recomendado se a sua conta possuir verificação em duas etapas (2FA) ativada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancelar")
                        }

                        Button(
                            onClick = {
                                val email = emailInput.trim()
                                val passOrToken = if (selectedTab == 0) passwordInput.trim() else tokenInput.trim()

                                if (selectedTab == 0 && email.isBlank()) {
                                    authError = "Por favor, digite seu e-mail do ${provider.displayName}."
                                    return@Button
                                }
                                if (passOrToken.isBlank()) {
                                    authError = if (selectedTab == 0) "Por favor, digite sua senha." else "Por favor, cole seu token de sessão."
                                    return@Button
                                }

                                isAuthenticating = true
                                authError = null
                                authStepText = "Conectando aos servidores do ${provider.displayName}..."

                                coroutineScope.launch {
                                    val result = cloudService.authenticateAndConnect(
                                        providerId = provider.id,
                                        serverUrl = serverUrlInput.trim(),
                                        usernameOrEmail = email,
                                        passwordOrToken = passOrToken,
                                        isTemporary = false
                                    )

                                    if (result.success) {
                                        isAuthenticating = false
                                        onAuthorize(result.accountDisplayName.ifBlank { email }, serverUrlInput.trim(), passOrToken)
                                    } else {
                                        isAuthenticating = false
                                        authError = result.errorMessage ?: "Falha ao conectar com o ${provider.displayName}."
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = provider.primaryColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Conectar", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Offline safe storage option
                    OutlinedButton(
                        onClick = {
                            val email = if (emailInput.isNotBlank()) emailInput.trim() else "local@${provider.id}.storage"
                            isAuthenticating = true
                            authError = null
                            authStepText = "Criando armazenamento seguro local..."
                            coroutineScope.launch {
                                val result = cloudService.authenticateAndConnect(
                                    providerId = provider.id,
                                    serverUrl = serverUrlInput.trim(),
                                    usernameOrEmail = email,
                                    passwordOrToken = "local_${provider.id}",
                                    isTemporary = false
                                )
                                isAuthenticating = false
                                if (result.success) {
                                    onAuthorize(email, serverUrlInput.trim(), "local_${provider.id}")
                                } else {
                                    authError = result.errorMessage ?: "Erro ao inicializar pasta local."
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderShared, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Criar Pasta Segura Offline (${provider.displayName})", fontSize = 12.sp)
                    }
                } else {
                    // Authenticating progress view
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp)
                    ) {
                        CircularProgressIndicator(
                            color = provider.primaryColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = authStepText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Estabelecendo comunicação segura...",
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
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
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
                if (isConnected) onExplorePath() else onConnect()
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

                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = provider.primaryColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CONECTAR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
