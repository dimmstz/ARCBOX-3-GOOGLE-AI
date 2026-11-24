package com.example.ui.components

import android.graphics.Rect as AndroidRect
import android.net.Uri
import android.os.Build
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class CloudProvider(
    val id: String,
    val displayName: String,
    val defaultEmail: String,
    val defaultServerUrl: String,
    val webLoginUrl: String,
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
        webLoginUrl = "https://mega.nz/login",
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
        webLoginUrl = "https://accounts.google.com/signin",
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
        webLoginUrl = "https://login.live.com/",
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
        webLoginUrl = "https://www.dropbox.com/login",
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
        webLoginUrl = "https://www.mediafire.com/login/",
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
        webLoginUrl = "https://cloud.nextcloud.com/login",
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun OAuthCloudConnectModal(
    provider: CloudProvider,
    onAuthorize: (email: String, serverUrl: String, passwordOrToken: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val cloudService = remember(context) { CloudStorageService.getInstance(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = E-mail e Senha, 1 = Chave / Token
    var serverUrlInput by remember { mutableStateOf(provider.defaultServerUrl) }
    var emailInput by remember { mutableStateOf(provider.defaultEmail) }
    var passwordInput by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var isAuthenticating by remember { mutableStateOf(false) }
    var authStepText by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    // Native Android Autofill integration
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current
    val systemAutofillManager = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.getSystemService(AutofillManager::class.java)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    val currentOnEmailFill by rememberUpdatedState<(String) -> Unit> { filled ->
        emailInput = filled
        authError = null
    }
    val currentOnPasswordFill by rememberUpdatedState<(String) -> Unit> { filled ->
        passwordInput = filled
        authError = null
    }

    val emailAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.EmailAddress, AutofillType.Username),
            onFill = { currentOnEmailFill(it) }
        )
    }
    val passwordAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { currentOnPasswordFill(it) }
        )
    }

    DisposableEffect(autofillTree, selectedTab, isAuthenticating) {
        if (selectedTab == 0 && !isAuthenticating) {
            autofillTree += emailAutofillNode
            autofillTree += passwordAutofillNode
        } else {
            autofillTree.children.remove(emailAutofillNode.id)
            autofillTree.children.remove(passwordAutofillNode.id)
        }
        onDispose {
            autofillTree.children.remove(emailAutofillNode.id)
            autofillTree.children.remove(passwordAutofillNode.id)
        }
    }

    fun triggerAutofillPickerForEmail() {
        emailFocusRequester.requestFocus()
        val box = emailAutofillNode.boundingBox
        if (box != null) {
            autofill?.requestAutofillForNode(emailAutofillNode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && systemAutofillManager?.isEnabled == true) {
                try {
                    val rect = AndroidRect(
                        box.left.roundToInt(),
                        box.top.roundToInt(),
                        box.right.roundToInt(),
                        box.bottom.roundToInt()
                    )
                    systemAutofillManager.requestAutofill(hostView, emailAutofillNode.id, rect)
                } catch (_: Exception) {}
            }
        }
    }

    fun dismissSafely() {
        if (isAuthenticating) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                systemAutofillManager?.cancel()
            } catch (_: Exception) {}
        }
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onDismiss()
    }

    fun submitAuth() {
        val email = if (selectedTab == 1 && emailInput.isBlank()) {
            "${provider.id.lowercase()}@cloud.storage"
        } else {
            emailInput.trim()
        }
        val passOrToken = if (selectedTab == 0) passwordInput.trim() else tokenInput.trim()

        if (selectedTab == 0 && email.isBlank()) {
            authError = "Por favor, digite seu e-mail do ${provider.displayName}."
            return
        }
        if (passOrToken.isBlank()) {
            authError = if (selectedTab == 0) {
                "Por favor, digite a senha do usuário."
            } else {
                "Por favor, cole sua chave ou token de acesso."
            }
            return
        }

        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        isAuthenticating = true
        authError = null
        authStepText = "Conectando ao ${provider.displayName}..."

        coroutineScope.launch {
            val result = cloudService.authenticateAndConnect(
                providerId = provider.id,
                serverUrl = serverUrlInput.trim(),
                usernameOrEmail = email,
                passwordOrToken = passOrToken,
                isTemporary = false
            )

            if (result.success) {
                if (selectedTab == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && systemAutofillManager?.isEnabled == true) {
                    try {
                        systemAutofillManager.notifyValueChanged(
                            hostView,
                            emailAutofillNode.id,
                            AutofillValue.forText(email)
                        )
                        systemAutofillManager.notifyValueChanged(
                            hostView,
                            passwordAutofillNode.id,
                            AutofillValue.forText(passOrToken)
                        )
                        systemAutofillManager.commit()
                    } catch (_: Exception) {}
                }
                isAuthenticating = false
                onAuthorize(result.accountDisplayName.ifBlank { email }, serverUrlInput.trim(), passOrToken)
            } else {
                isAuthenticating = false
                authError = result.errorMessage ?: "Falha ao conectar com o ${provider.displayName}."
            }
        }
    }

    Dialog(
        onDismissRequest = { dismissSafely() },
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
                .widthIn(max = 460.dp)
                .wrapContentHeight()
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // Compact Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = provider.primaryColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, provider.primaryColor.copy(alpha = 0.3f)),
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
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = provider.authTypeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { dismissSafely() },
                        enabled = !isAuthenticating,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("cloud_close_button")
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
                    // Clean segmented method selector
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("E-mail e Senha", "Chave / Token").forEachIndexed { index, title ->
                                val isSelected = selectedTab == index
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    shadowElevation = if (isSelected) 1.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedTab = index
                                            authError = null
                                        }
                                        .testTag(if (index == 0) "cloud_tab_credentials" else "cloud_tab_token")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) provider.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (provider == CloudProvider.WEBDAV) {
                        OutlinedTextField(
                            value = serverUrlInput,
                            onValueChange = { serverUrlInput = it; authError = null },
                            label = { Text("URL do Servidor", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = {
                                    if (selectedTab == 0) emailFocusRequester.requestFocus()
                                }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cloud_server_url_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (selectedTab == 0) {
                        val isAutofillSupported = remember(systemAutofillManager) {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                (systemAutofillManager?.isAutofillSupported == true)
                        }

                        // Email field with Android Autofill support
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it; authError = null },
                            label = {
                                Text(
                                    text = "E-mail da conta ${provider.displayName}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            placeholder = {
                                Text("exemplo@email.com", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            trailingIcon = when {
                                emailInput.isNotEmpty() -> {
                                    {
                                        IconButton(onClick = { emailInput = ""; authError = null }) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Limpar e-mail"
                                            )
                                        }
                                    }
                                }
                                isAutofillSupported -> {
                                    {
                                        IconButton(
                                            onClick = { triggerAutofillPickerForEmail() },
                                            modifier = Modifier.testTag("cloud_autofill_button")
                                        ) {
                                            Icon(
                                                Icons.Default.VpnKey,
                                                contentDescription = "Preencher automaticamente",
                                                tint = provider.primaryColor
                                            )
                                        }
                                    }
                                }
                                else -> null
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { passwordFocusRequester.requestFocus() }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(emailFocusRequester)
                                .onGloballyPositioned { coordinates ->
                                    emailAutofillNode.boundingBox = coordinates.boundsInWindow()
                                }
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        if (emailAutofillNode.boundingBox != null) {
                                            autofill?.requestAutofillForNode(emailAutofillNode)
                                        }
                                    } else {
                                        autofill?.cancelAutofillForNode(emailAutofillNode)
                                    }
                                }
                                .testTag("cloud_email_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Password field ("Senha do usuário") with Android Autofill support
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it; authError = null },
                            label = {
                                Text(
                                    text = "Senha do usuário",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            placeholder = {
                                Text("Digite sua senha", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
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
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitAuth() }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(passwordFocusRequester)
                                .onGloballyPositioned { coordinates ->
                                    passwordAutofillNode.boundingBox = coordinates.boundsInWindow()
                                }
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        if (passwordAutofillNode.boundingBox != null) {
                                            autofill?.requestAutofillForNode(passwordAutofillNode)
                                        }
                                    } else {
                                        autofill?.cancelAutofillForNode(passwordAutofillNode)
                                    }
                                }
                                .testTag("cloud_password_input")
                        )
                    } else {
                        val tokenLabel = when (provider) {
                            CloudProvider.GOOGLE_DRIVE -> "Token OAuth2 (ya29...) ou Chave de Acesso"
                            CloudProvider.ONEDRIVE -> "Access Token ou Chave Microsoft Graph"
                            CloudProvider.DROPBOX -> "Token de Acesso / App Key"
                            CloudProvider.MEGA -> "Chave de Sessão (sid) / Token"
                            CloudProvider.MEDIAFIRE -> "Chave de Acesso / API Key"
                            CloudProvider.WEBDAV -> "Token de Aplicação / Senha de App"
                        }

                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it; authError = null },
                            label = { Text(tokenLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            placeholder = { Text("Cole aqui sua chave de acesso ou token") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            singleLine = false,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitAuth() }
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cloud_token_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { dismissSafely() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("cloud_cancel_button")
                        ) {
                            Text("Cancelar")
                        }

                        Button(
                            onClick = { submitAuth() },
                            colors = ButtonDefaults.buttonColors(containerColor = provider.primaryColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("cloud_connect_button")
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Conectar", fontWeight = FontWeight.Bold)
                        }
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
    onRegisterSafDrive: (Uri, String?, String?) -> Unit = { _, _, _ -> },
    onRemoveSafDrive: (String) -> Unit = {},
    onStartOAuthFlow: (CloudProvider) -> Unit,
    onQuickConnectProvider: (CloudProvider) -> Unit = {},
    onConnectAll: () -> Unit = {},
    onDisconnectProvider: (CloudProvider) -> Unit,
    onOpenCloudPath: (String) -> Unit,
    onClose: () -> Unit
) {
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                                text = "Não conectado",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                    val cardContext = LocalContext.current
                    val realProvider = remember(cardContext, provider.id, isConnected) {
                        CloudStorageService.getInstance(cardContext).getProvider(provider.id)
                    }
                    val realTotal = realProvider?.totalSpace?.takeIf { it > 0L } ?: provider.defaultTotalBytes
                    val realUsed = realProvider?.usedSpace?.coerceAtLeast(0L) ?: 0L
                    Text(
                        text = if (realTotal > 0L) "${formatFileSize(realUsed)} / ${formatFileSize(realTotal)}" else "Conectado",
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
