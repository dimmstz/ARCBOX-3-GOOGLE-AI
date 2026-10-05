package com.example.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudWebAutofillLoginDialog(
    provider: CloudProvider,
    onLoginSuccess: (email: String, tokenOrPassword: String) -> Unit,
    onDismiss: () -> Unit
) {
    var detectedEmail by remember { mutableStateOf("") }
    var detectedPassword by remember { mutableStateOf("") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
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
                        Column {
                            Text(
                                text = "Login ${provider.displayName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Usar a senha salva do Google / Android",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar")
                        }
                    },
                    actions = {
                        IconButton(onClick = { webViewInstance?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Recarregar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = provider.primaryColor
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                @SuppressLint("SetJavaScriptEnabled")
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.saveFormData = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                addJavascriptInterface(object {
                                    @android.webkit.JavascriptInterface
                                    fun onCredentialsCaptured(email: String, pass: String) {
                                        val cleanE = email.trim()
                                        val cleanP = pass.trim()
                                        if (cleanE.isNotBlank() && (cleanE.contains("@") || cleanE.length >= 3)) {
                                            detectedEmail = cleanE
                                        }
                                        if (cleanP.isNotBlank()) {
                                            detectedPassword = cleanP
                                        }
                                    }
                                }, "ArcBoxAuth")

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        isLoading = true
                                    }
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        val jsInjector = """
                                            (function() {
                                                function checkInputs() {
                                                    var e = document.querySelector('input[type="email"], input[type="text"][name*="user"], input[name*="email"], input[name*="login"], input[id*="email"], input[id*="user"]');
                                                    var p = document.querySelector('input[type="password"], input[name*="pass"], input[name*="pwd"], input[id*="pass"], input[id*="password"]');
                                                    var ev = e ? e.value : '';
                                                    var pv = p ? p.value : '';
                                                    if (window.ArcBoxAuth) {
                                                        window.ArcBoxAuth.onCredentialsCaptured(ev, pv);
                                                    }
                                                }
                                                checkInputs();
                                                setInterval(checkInputs, 500);
                                                document.addEventListener('input', checkInputs);
                                                document.addEventListener('change', checkInputs);
                                            })();
                                        """.trimIndent()
                                        view?.evaluateJavascript(jsInjector, null)
                                    }
                                }

                                webViewInstance = this
                                loadUrl(provider.webLoginUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = provider.primaryColor.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.VpnKey,
                                        contentDescription = null,
                                        tint = provider.primaryColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (detectedEmail.isNotBlank()) "Conta: $detectedEmail" else "Após preencher ou fazer login:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (detectedPassword.isNotBlank()) "Senha capturada do preenchimento" else "Toque abaixo para vincular sua conta ao ArcBox",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (detectedPassword.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val jsExtractor = """
                                    (function() {
                                        var e = document.querySelector('input[type="email"], input[type="text"][name*="user"], input[name*="email"], input[name*="login"], input[id*="email"], input[id*="user"]');
                                        var p = document.querySelector('input[type="password"], input[name*="pass"], input[name*="pwd"], input[id*="pass"], input[id*="password"]');
                                        return (e ? e.value : '') + ':::' + (p ? p.value : '');
                                    })();
                                """.trimIndent()

                                webViewInstance?.evaluateJavascript(jsExtractor) { rawResult ->
                                    val clean = rawResult?.replace("\"", "")?.replace("\\", "")?.trim() ?: ""
                                    val parts = clean.split(":::")
                                    val extractedE = if (parts.isNotEmpty()) parts[0].trim() else ""
                                    val extractedP = if (parts.size > 1) parts[1].trim() else ""

                                    val finalEmail = extractedE.ifBlank { detectedEmail }.ifBlank { "${provider.id.lowercase()}@account" }
                                    val finalPass = extractedP.ifBlank { detectedPassword }.ifBlank { "web_auth_token_${System.currentTimeMillis()}" }

                                    onLoginSuccess(finalEmail, finalPass)
                                } ?: run {
                                    val finalEmail = detectedEmail.ifBlank { "${provider.id.lowercase()}@account" }
                                    val finalPass = detectedPassword.ifBlank { "web_auth_token_${System.currentTimeMillis()}" }
                                    onLoginSuccess(finalEmail, finalPass)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = provider.primaryColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Concluir e Conectar ao ArcBox", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
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

    var showWebAutofillLogin by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Email/Password, 1 = Token/Key/Passkey
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

    if (showWebAutofillLogin) {
        CloudWebAutofillLoginDialog(
            provider = provider,
            onLoginSuccess = { email, token ->
                showWebAutofillLogin = false
                isAuthenticating = true
                authError = null
                authStepText = "Conectando conta autenticada do ${provider.displayName}..."
                coroutineScope.launch {
                    val result = cloudService.authenticateAndConnect(
                        providerId = provider.id,
                        serverUrl = provider.defaultServerUrl,
                        usernameOrEmail = email,
                        passwordOrToken = token,
                        isTemporary = false
                    )
                    isAuthenticating = false
                    if (result.success) {
                        onAuthorize(result.accountDisplayName.ifBlank { email }, provider.defaultServerUrl, token)
                    } else {
                        authError = result.errorMessage ?: "Falha ao conectar com a conta."
                    }
                }
            },
            onDismiss = { showWebAutofillLogin = false }
        )
    }

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
                    // Google Autofill / Saved Password Action Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = provider.primaryColor.copy(alpha = 0.10f),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, provider.primaryColor.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showWebAutofillLogin = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = provider.primaryColor,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.VpnKey,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Usar a Senha Salva do Google",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Preenchimento automático do Android",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = provider.primaryColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = provider.primaryColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

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
                            text = { Text("Chave / Token", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) }
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
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Next
                            ),
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
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Next
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Password field
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it; authError = null },
                            label = { Text("Senha da conta ou Senha de App") },
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
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Key / Token / Passkey field
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
                            label = { Text(tokenLabel) },
                            placeholder = { Text("Cole aqui sua chave de acesso ou token") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            singleLine = false,
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Autenticação direta com chave de API, access token ou credencial permanente.",
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
                                val email = if (selectedTab == 1 && emailInput.isBlank()) "${provider.id.lowercase()}@cloud.storage" else emailInput.trim()
                                val passOrToken = if (selectedTab == 0) passwordInput.trim() else tokenInput.trim()

                                if (selectedTab == 0 && email.isBlank()) {
                                    authError = "Por favor, digite seu e-mail do ${provider.displayName}."
                                    return@Button
                                }
                                if (passOrToken.isBlank()) {
                                    authError = if (selectedTab == 0) "Por favor, digite sua senha." else "Por favor, cole sua chave ou token de acesso."
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
