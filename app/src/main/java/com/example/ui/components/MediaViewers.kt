package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import coil.decode.VideoFrameDecoder
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.models.FileItem
import com.example.data.models.FileType
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch
import kotlin.math.sin

fun resolveMediaFile(context: android.content.Context, path: String): File {
    return if (path.startsWith("/cloud/")) {
        val relative = path.removePrefix("/cloud/").removePrefix("/")
        val cloudDir = File(context.filesDir, "cloud_storage")
        File(cloudDir, relative)
    } else {
        File(path)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcboxMediaViewerModal(
    item: FileItem,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onClose: () -> Unit,
    onDelete: ((FileItem) -> Unit)? = null,
    onEditWithThirdParty: ((FileItem) -> Unit)? = null
) {
    val context = LocalContext.current
    val resolvedFile = remember(item.path) { resolveMediaFile(context, item.path) }
    var showInfo by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        if (item.fileType == FileType.AUDIO) {
            AudioPlayerContent(
                file = resolvedFile,
                onNext = onNext,
                onPrevious = onPrevious,
                onClose = onClose
            )
        } else if (item.fileType == FileType.IMAGE) {
            // New Dedicated Image Viewer with Dynamic Dominant Color Gradient & Custom Toolbar
            ArcboxImageViewerScreen(
                item = item,
                file = resolvedFile,
                onClose = onClose,
                onDelete = onDelete,
                onEditWithThirdParty = onEditWithThirdParty,
                onNext = onNext,
                onPrevious = onPrevious
            )
        } else {
            var showControls by remember { mutableStateOf(true) }
            var interactionToken by remember { mutableIntStateOf(0) }

            fun resetControlsTimer() {
                showControls = true
                interactionToken++
            }

            fun toggleControls() {
                if (showControls) {
                    showControls = false
                } else {
                    resetControlsTimer()
                }
            }

            LaunchedEffect(showControls, interactionToken, item.path) {
                if (showControls) {
                    kotlinx.coroutines.delay(5000L)
                    showControls = false
                }
            }

            LaunchedEffect(item.path) {
                resetControlsTimer()
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.95f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(item.path) {
                            detectTapGestures(
                                onTap = { toggleControls() }
                            )
                        }
                ) {
                    // Center Media Content
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoPlayerContent(
                            file = resolvedFile,
                            showControls = showControls,
                            onToggleControls = { toggleControls() },
                            onResetControlsTimer = { resetControlsTimer() }
                        )
                    }

                    // Top Header (hides with showControls)
                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = {
                                resetControlsTimer()
                                onClose()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    text = formatFileSize(item.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (onEditWithThirdParty != null) {
                                    IconButton(onClick = {
                                        resetControlsTimer()
                                        onEditWithThirdParty(item)
                                    }) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Editar com app de terceiros",
                                            tint = Color.White
                                        )
                                    }
                                }
                                if (onDelete != null) {
                                    IconButton(onClick = {
                                        resetControlsTimer()
                                        onDelete(item)
                                    }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Excluir para Lixeira",
                                            tint = Color(0xFFEF4444)
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    resetControlsTimer()
                                    showInfo = !showInfo
                                }) {
                                    Icon(Icons.Default.Info, contentDescription = "Informações", tint = Color.White)
                                }
                            }
                        }
                    }

                    // Navigation Side Buttons (Left / Right arrows) (hide with showControls)
                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    resetControlsTimer()
                                    onPrevious()
                                },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                    .size(48.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Anterior",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    resetControlsTimer()
                                    onNext()
                                },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                    .size(48.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Próxima",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Info Overlay
                    if (showInfo && showControls) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 70.dp, end = 16.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    "Caminho: ${item.path}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Tamanho: ${formatFileSize(item.size)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Modificado: ${formatDate(item.lastModified)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcboxImageViewerScreen(
    item: FileItem,
    file: File,
    onClose: () -> Unit,
    onDelete: ((FileItem) -> Unit)? = null,
    onEditWithThirdParty: ((FileItem) -> Unit)? = null,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {}
) {
    val context = LocalContext.current
    val imageSource = remember(item.path, item.safUriString) {
        if (item.path.startsWith("content://") || item.safUriString != null) {
            Uri.parse(item.safUriString ?: item.path)
        } else {
            file
        }
    }

    // Dynamic dominant color extraction with smooth gradient
    var dominantColors by remember(item.path, item.safUriString) {
        mutableStateOf(Pair(Color(0xFF6BA3F5), Color(0xFFE8F0FE)))
    }
    var dimensions by remember(item.path, item.safUriString) {
        mutableStateOf(Pair(0, 0))
    }
    var showInfoModal by remember { mutableStateOf(false) }
    var showCropSheet by remember { mutableStateOf(false) }
    var activeFilterIndex by remember { mutableIntStateOf(0) }
    var backgroundModeIndex by remember { mutableIntStateOf(0) } // 0 = Gradiente, 1 = Preto, 2 = Branco
    var contentScaleIndex by remember { mutableIntStateOf(0) }
    var toastFeedback by remember { mutableStateOf<String?>(null) }
    var imageVersion by remember(item.path, item.safUriString) { mutableLongStateOf(file.lastModified()) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(item.path, item.safUriString) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            dominantColors = extractDominantColors(context, item, file)
            dimensions = getImageDimensions(context, item, file)
        }
    }

    LaunchedEffect(toastFeedback) {
        if (toastFeedback != null) {
            kotlinx.coroutines.delay(1800L)
            toastFeedback = null
        }
    }

    val contentScales = listOf(
        Pair(ContentScale.Fit, "Ajustado à tela"),
        Pair(ContentScale.Crop, "Preencher tela"),
        Pair(ContentScale.Inside, "Tamanho real")
    )

    val filterNames = listOf("Original", "Preto & Branco", "Alto Contraste", "Sépia Quente", "Invertido")
    val colorFilter = when (activeFilterIndex) {
        1 -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) })
        2 -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(
                floatArrayOf(
                    1.4f, 0f, 0f, 0f, -30f,
                    0f, 1.4f, 0f, 0f, -30f,
                    0f, 0f, 1.4f, 0f, -30f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        3 -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        4 -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        else -> null
    }

    var scale by remember(file.path) { mutableFloatStateOf(1f) }
    var offset by remember(file.path) { mutableStateOf(Offset.Zero) }
    var rotation by remember(file.path) { mutableFloatStateOf(0f) }

    val animateScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "imageScale"
    )

    val animateRotation by animateFloatAsState(
        targetValue = rotation,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "imageRotation"
    )

    val currentRotation by rememberUpdatedState(rotation)
    val currentFile by rememberUpdatedState(file)

    DisposableEffect(file.path) {
        onDispose {
            val normalizedDegrees = ((currentRotation % 360f) + 360f) % 360f
            if (normalizedDegrees.toInt() % 360 != 0) {
                saveRotatedImage(currentFile, normalizedDegrees)
            }
        }
    }

    val handleClose = {
        val normalizedDegrees = ((currentRotation % 360f) + 360f) % 360f
        if (normalizedDegrees.toInt() % 360 != 0) {
            saveRotatedImage(currentFile, normalizedDegrees)
            rotation = 0f
        }
        onClose()
    }

    val backgroundModifier = when (backgroundModeIndex) {
        1 -> Modifier.background(Color(0xFF0D0D0D))
        2 -> Modifier.background(Color.White)
        else -> Modifier.background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(
                    dominantColors.first,
                    dominantColors.second
                )
            )
        )
    }

    val topBarTextColor = when (backgroundModeIndex) {
        1 -> Color.White
        else -> Color(0xFF0F172A)
    }

    val topBarSubtitleColor = when (backgroundModeIndex) {
        1 -> Color.White.copy(alpha = 0.7f)
        2 -> Color(0xFF475569)
        else -> Color(0xFF334155)
    }

    val iconTint = when (backgroundModeIndex) {
        1 -> Color.White
        else -> Color(0xFF0F172A)
    }

    // Dynamic background container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        // Main Gestures & AsyncImage Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(file.path) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1.2f) 1f else 2.2f
                            offset = Offset.Zero
                        }
                    )
                }
                .pointerInput(file.path) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.8f, 5f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageSource)
                    .memoryCacheKey("${item.path}_$imageVersion")
                    .diskCacheKey("${item.path}_$imageVersion")
                    .crossfade(false)
                    .build(),
                contentDescription = item.name,
                contentScale = contentScales[contentScaleIndex].first,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = animateScale,
                        scaleY = animateScale,
                        translationX = offset.x,
                        translationY = offset.y,
                        rotationZ = animateRotation
                    )
            )
        }

        // Top App Bar matching Screenshot (Back arrow, title, size & resolution, info button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = handleClose,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = topBarTextColor,
                    maxLines = 1,
                    fontSize = 18.sp
                )

                val dimText = if (dimensions.first > 0 && dimensions.second > 0) {
                    " • ${dimensions.first} × ${dimensions.second}"
                } else ""

                Text(
                    text = "${formatFileSize(item.size)}$dimText",
                    style = MaterialTheme.typography.bodySmall,
                    color = topBarSubtitleColor,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.5.sp,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = { showInfoModal = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(if (backgroundModeIndex == 1) Color(0x33FFFFFF) else Color(0x1F0F172A), CircleShape)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Informações da imagem",
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Feedback Toast
        AnimatedVisibility(
            visible = toastFeedback != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xCC0F172A),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = toastFeedback ?: "",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Bottom Action Bar with 4 Circular Buttons exactly as in screenshot
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Change Background Mode (Gradiente, Preto, Branco)
                Surface(
                    onClick = {
                        backgroundModeIndex = (backgroundModeIndex + 1) % 3
                        toastFeedback = when (backgroundModeIndex) {
                            0 -> "Fundo: Gradiente"
                            1 -> "Fundo: Preto"
                            else -> "Fundo: Branco"
                        }
                    },
                    shape = CircleShape,
                    color = if (backgroundModeIndex == 1) Color(0x44FFFFFF) else Color(0x33000000),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.WaterDrop,
                            contentDescription = "Mudar fundo",
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 2. Crop Image (Popular Aspect Ratios 16:9, 9:16, 4:3, etc.)
                Surface(
                    onClick = {
                        showCropSheet = true
                    },
                    shape = CircleShape,
                    color = if (backgroundModeIndex == 1) Color(0x44FFFFFF) else Color(0x33000000),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.CropFree,
                            contentDescription = "Cortar aspecto",
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 3. Rotate Icon (Saves state real on exit)
                Surface(
                    onClick = {
                        rotation += 90f
                        val currentDegrees = ((rotation % 360f) + 360f) % 360f
                        toastFeedback = "Rotacionado (${currentDegrees.toInt()}°)"
                    },
                    shape = CircleShape,
                    color = if (backgroundModeIndex == 1) Color(0x44FFFFFF) else Color(0x33000000),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.RotateRight,
                            contentDescription = "Girar imagem",
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 4. Edit Pencil Icon
                Surface(
                    onClick = {
                        if (onEditWithThirdParty != null) {
                            onEditWithThirdParty(item)
                        } else {
                            toastFeedback = "Abrindo editor..."
                        }
                    },
                    shape = CircleShape,
                    color = if (backgroundModeIndex == 1) Color(0x44FFFFFF) else Color(0x33000000),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar imagem",
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    // Info Details Modal Bottom Sheet
    if (showInfoModal) {
        ModalBottomSheet(
            onDismissRequest = { showInfoModal = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Detalhes da Imagem",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        DetailRow(label = "Nome", value = item.name)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        val dimStr = if (dimensions.first > 0 && dimensions.second > 0) {
                            "${dimensions.first} × ${dimensions.second} pixels"
                        } else "Indisponível"
                        DetailRow(label = "Resolução", value = dimStr)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        DetailRow(label = "Tamanho", value = formatFileSize(item.size))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        DetailRow(label = "Modificado", value = formatDate(item.lastModified))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        DetailRow(label = "Caminho", value = item.path)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (onDelete != null) {
                        OutlinedButton(
                            onClick = {
                                showInfoModal = false
                                onDelete(item)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFEF4444)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Excluir")
                        }
                    }

                    Button(
                        onClick = { showInfoModal = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Fechar")
                    }
                }
            }
        }
    }

    // Crop Aspect Ratio Modal Bottom Sheet
    if (showCropSheet) {
        val aspectRatios = listOf(
            Triple("16:9", Pair(16f, 9f), "Widescreen / TV (16:9)"),
            Triple("9:16", Pair(9f, 16f), "Stories / Reels / TikTok (9:16)"),
            Triple("4:3", Pair(4f, 3f), "Fotografia Padrão (4:3)"),
            Triple("3:4", Pair(3f, 4f), "Retrato (3:4)"),
            Triple("1:1", Pair(1f, 1f), "Quadrado / Perfil (1:1)"),
            Triple("2:3", Pair(2f, 3f), "Retrato Padrão (2:3)"),
            Triple("3:2", Pair(3f, 2f), "Paisagem (3:2)")
        )

        ModalBottomSheet(
            onDismissRequest = { showCropSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Crop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Cortar Aspecto da Imagem",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    aspectRatios.forEach { (label, ratio, desc) ->
                        Surface(
                            onClick = {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val croppedResult = cropImageFile(file, ratio.first, ratio.second)
                                    if (croppedResult != null) {
                                        dimensions = croppedResult
                                        imageVersion = System.currentTimeMillis()
                                        scale = 1f
                                        offset = Offset.Zero
                                        toastFeedback = "Imagem cortada ($label)"
                                    } else {
                                        toastFeedback = "Não foi possível cortar a imagem"
                                    }
                                    showCropSheet = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

fun extractDominantColors(context: Context, item: FileItem, file: File): Pair<Color, Color> {
    return try {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 8
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = if (item.path.startsWith("content://") || item.safUriString != null) {
            val uri = Uri.parse(item.safUriString ?: item.path)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } else if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath, options)
        } else null

        if (bitmap == null) return Pair(Color(0xFF6BA3F5), Color(0xFFE8F0FE))
        val width = bitmap.width
        val height = bitmap.height

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0
        var maxSat = 0f
        var mostVibrantColor = Color(0xFF6BA3F5)

        val stepX = (width / 16).coerceAtLeast(1)
        val stepY = (height / 16).coerceAtLeast(1)

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val a = (pixel shr 24) and 0xff
                if (a < 128) continue
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff

                val hsv = FloatArray(3)
                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                val sat = hsv[1]
                val value = hsv[2]

                // Prioritize vibrant colors over pure white / black
                if (value in 0.15f..0.98f && sat > 0.12f) {
                    if (sat > maxSat) {
                        maxSat = sat
                        mostVibrantColor = Color(r, g, b)
                    }
                }

                rSum += r
                gSum += g
                bSum += b
                count++
            }
        }
        bitmap.recycle()

        val dominant = if (maxSat > 0.18f) {
            mostVibrantColor
        } else if (count > 0) {
            Color((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
        } else {
            Color(0xFF6BA3F5)
        }

        // Top: Dominant color tint, Bottom: soft pastel gradient of that color
        val topColor = dominant
        val bottomColor = Color(
            red = (dominant.red * 0.20f + 0.80f).coerceIn(0f, 1f),
            green = (dominant.green * 0.20f + 0.80f).coerceIn(0f, 1f),
            blue = (dominant.blue * 0.20f + 0.80f).coerceIn(0f, 1f)
        )
        Pair(topColor, bottomColor)
    } catch (_: Exception) {
        Pair(Color(0xFF6BA3F5), Color(0xFFE8F0FE))
    }
}

fun extractDominantColors(file: File): Pair<Color, Color> {
    if (!file.exists()) return Pair(Color(0xFF6BA3F5), Color(0xFFE8F0FE))
    return try {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 8
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return Pair(Color(0xFF6BA3F5), Color(0xFFE8F0FE))
        val width = bitmap.width
        val height = bitmap.height

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0
        var maxSat = 0f
        var mostVibrantColor = Color(0xFF6BA3F5)

        val stepX = (width / 16).coerceAtLeast(1)
        val stepY = (height / 16).coerceAtLeast(1)

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val a = (pixel shr 24) and 0xff
                if (a < 128) continue
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff

                val hsv = FloatArray(3)
                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                val sat = hsv[1]
                val value = hsv[2]

                // Prioritize vibrant colors over pure white / black
                if (value in 0.15f..0.98f && sat > 0.12f) {
                    if (sat > maxSat) {
                        maxSat = sat
                        mostVibrantColor = Color(r, g, b)
                    }
                }

                rSum += r
                gSum += g
                bSum += b
                count++
            }
        }
        bitmap.recycle()

        val dominant = if (maxSat > 0.18f) {
            mostVibrantColor
        } else if (count > 0) {
            Color((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
        } else {
            Color(0xFF6BA3F5)
        }

        // Top: Dominant color tint, Bottom: soft pastel gradient of that color
        val topColor = dominant
        val bottomColor = Color(
            red = (dominant.red * 0.20f + 0.80f).coerceIn(0f, 1f),
            green = (dominant.green * 0.20f + 0.80f).coerceIn(0f, 1f),
            blue = (dominant.blue * 0.20f + 0.80f).coerceIn(0f, 1f)
        )
        Pair(topColor, bottomColor)
    } catch (_: Exception) {
        Pair(Color(0xFF6BA3F5), Color(0xFFE8F0FE))
    }
}

fun getImageDimensions(context: Context, item: FileItem, file: File): Pair<Int, Int> {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        if (item.path.startsWith("content://") || item.safUriString != null) {
            val uri = Uri.parse(item.safUriString ?: item.path)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } else if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath, options)
        }
        Pair(options.outWidth, options.outHeight)
    } catch (_: Exception) {
        Pair(0, 0)
    }
}

fun getImageDimensions(file: File): Pair<Int, Int> {
    if (!file.exists()) return Pair(0, 0)
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        Pair(options.outWidth, options.outHeight)
    } catch (_: Exception) {
        Pair(0, 0)
    }
}

@Composable
fun ImageViewerContent(
    file: File,
    item: FileItem? = null,
    showControls: Boolean = true,
    onToggleControls: () -> Unit = {},
    onResetControlsTimer: () -> Unit = {},
    onDelete: ((FileItem) -> Unit)? = null,
    onEditWithThirdParty: ((FileItem) -> Unit)? = null
) {
    var scale by remember(file.path) { mutableFloatStateOf(1f) }
    var offset by remember(file.path) { mutableStateOf(Offset.Zero) }
    var rotation by remember(file.path) { mutableFloatStateOf(0f) }

    val currentRotation by rememberUpdatedState(rotation)
    val currentFile by rememberUpdatedState(file)

    val animateScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scaleAnimation"
    )

    DisposableEffect(file.path) {
        onDispose {
            val normalizedDegrees = ((currentRotation % 360f) + 360f) % 360f
            if (normalizedDegrees.toInt() % 360 != 0) {
                saveRotatedImage(currentFile, normalizedDegrees)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(file.path) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = {
                        onResetControlsTimer()
                        scale = if (scale > 1.2f) 1f else 2f
                        offset = Offset.Zero
                    }
                )
            }
            .pointerInput(file.path) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.8f, 5f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(file)
                .memoryCacheKey("${file.absolutePath}_${file.lastModified()}")
                .diskCacheKey("${file.absolutePath}_${file.lastModified()}")
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = animateScale,
                    scaleY = animateScale,
                    translationX = offset.x,
                    translationY = offset.y,
                    rotationZ = rotation
                )
        )
        
        // Rotate Buttons (hides with showControls)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item != null && onEditWithThirdParty != null) {
                    IconButton(onClick = {
                        onResetControlsTimer()
                        onEditWithThirdParty(item)
                    }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar nos apps de terceiros",
                            tint = Color.White
                        )
                    }
                }
                if (item != null && onDelete != null) {
                    IconButton(onClick = {
                        onResetControlsTimer()
                        onDelete(item)
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Excluir para Lixeira",
                            tint = Color(0xFFEF4444)
                        )
                    }
                }
                IconButton(onClick = {
                    onResetControlsTimer()
                    rotation -= 90f
                }) {
                    Icon(Icons.Default.RotateLeft, contentDescription = "Rotacionar Esquerda", tint = Color.White)
                }
                IconButton(onClick = {
                    onResetControlsTimer()
                    rotation += 90f
                }) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotacionar Direita", tint = Color.White)
                }
            }
        }
    }
}

private fun saveRotatedImage(file: File, degrees: Float) {
    val normalizedDegrees = ((degrees % 360f) + 360f) % 360f
    val intDegrees = normalizedDegrees.toInt()
    if (intDegrees % 360 == 0 || !file.exists() || !file.canWrite()) return

    try {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return

        val matrix = Matrix().apply {
            postRotate(intDegrees.toFloat())
        }
        val rotatedBitmap = Bitmap.createBitmap(
            originalBitmap,
            0,
            0,
            originalBitmap.width,
            originalBitmap.height,
            matrix,
            true
        )

        val format = when (file.extension.lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }

        FileOutputStream(file).use { out ->
            rotatedBitmap.compress(format, 95, out)
        }

        try {
            val exif = android.media.ExifInterface(file.absolutePath)
            exif.setAttribute(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL.toString()
            )
            exif.saveAttributes()
        } catch (_: Exception) {}

        file.setLastModified(System.currentTimeMillis())

        if (rotatedBitmap != originalBitmap) {
            rotatedBitmap.recycle()
        }
        originalBitmap.recycle()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun cropImageFile(file: File, aspectWidth: Float, aspectHeight: Float): Pair<Int, Int>? {
    if (!file.exists() || !file.canWrite()) return null
    try {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null

        val origWidth = originalBitmap.width
        val origHeight = originalBitmap.height
        val targetAspect = aspectWidth / aspectHeight
        val currentAspect = origWidth.toFloat() / origHeight.toFloat()

        var cropWidth = origWidth
        var cropHeight = origHeight

        if (currentAspect > targetAspect) {
            cropWidth = (origHeight * targetAspect).toInt().coerceIn(1, origWidth)
        } else {
            cropHeight = (origWidth / targetAspect).toInt().coerceIn(1, origHeight)
        }

        val startX = (origWidth - cropWidth) / 2
        val startY = (origHeight - cropHeight) / 2

        val croppedBitmap = Bitmap.createBitmap(originalBitmap, startX, startY, cropWidth, cropHeight)

        val format = when (file.extension.lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }

        FileOutputStream(file).use { out ->
            croppedBitmap.compress(format, 95, out)
        }

        try {
            val exif = android.media.ExifInterface(file.absolutePath)
            exif.setAttribute(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL.toString()
            )
            exif.saveAttributes()
        } catch (_: Exception) {}

        file.setLastModified(System.currentTimeMillis())

        if (croppedBitmap != originalBitmap) {
            croppedBitmap.recycle()
        }
        originalBitmap.recycle()

        return Pair(cropWidth, cropHeight)
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

@Composable
fun VideoPlayerContent(
    file: File,
    showControls: Boolean = true,
    onToggleControls: () -> Unit = {},
    onResetControlsTimer: () -> Unit = {}
) {
    val context = LocalContext.current
    var isPlaying by remember(file.path) { mutableStateOf(true) }
    var currentPositionMs by remember(file.path) { mutableLongStateOf(0L) }
    var totalDurationMs by remember(file.path) { mutableLongStateOf(270000L) } // default 04:30
    
    var isVideoPrepared by remember(file.path) { mutableStateOf(false) }
    var videoError by remember(file.path) { mutableStateOf(false) }
    var mediaPlayerRef by remember(file.path) { mutableStateOf<MediaPlayer?>(null) }
    var videoViewRef by remember(file.path) { mutableStateOf<VideoView?>(null) }

    // Video Options State
    var isLooping by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    val aspectRatios = listOf(16 / 9f, 4 / 3f, 1f, 21 / 9f)
    val aspectRatioLabels = listOf("16:9", "4:3", "1:1", "21:9")
    var aspectRatioIndex by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }

    var aspectToastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(aspectToastMessage) {
        if (aspectToastMessage != null) {
            kotlinx.coroutines.delay(2000L)
            aspectToastMessage = null
        }
    }

    LaunchedEffect(isPlaying, isVideoPrepared, videoError) {
        while (isPlaying) {
            kotlinx.coroutines.delay(250L)
            if (isVideoPrepared && !videoError && videoViewRef != null) {
                try {
                    val pos = videoViewRef?.currentPosition ?: 0
                    val dur = videoViewRef?.duration ?: 0
                    if (dur > 0) {
                        totalDurationMs = dur.toLong()
                        currentPositionMs = pos.toLong()
                    }
                } catch (_: Exception) {}
            } else {
                val totalSec = (totalDurationMs / 1000L).coerceAtLeast(1L)
                val nextMs = currentPositionMs + 250L
                if (nextMs >= totalDurationMs) {
                    if (isLooping) {
                        currentPositionMs = 0L
                    } else {
                        currentPositionMs = totalDurationMs
                        isPlaying = false
                    }
                } else {
                    currentPositionMs = nextMs
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 48.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isFullscreen) 9 / 16f else aspectRatios[aspectRatioIndex])
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F172A))
                .align(Alignment.Center)
                .clickable {
                    onToggleControls()
                },
            contentAlignment = Alignment.Center
        ) {
            if (!videoError && file.exists() && file.length() > 0) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(Uri.fromFile(file))
                            setOnPreparedListener { mp ->
                                mediaPlayerRef = mp
                                isVideoPrepared = true
                                val dur = mp.duration
                                if (dur > 0) {
                                    totalDurationMs = dur.toLong()
                                }
                                mp.isLooping = isLooping
                                if (isMuted) {
                                    mp.setVolume(0f, 0f)
                                } else {
                                    mp.setVolume(1f, 1f)
                                }
                                if (isPlaying) {
                                    start()
                                }
                            }
                            setOnErrorListener { _, _, _ ->
                                videoError = true
                                true
                            }
                            setOnCompletionListener {
                                if (!isLooping) {
                                    isPlaying = false
                                }
                            }
                            videoViewRef = this
                        }
                    },
                    update = { view ->
                        videoViewRef = view
                        if (isVideoPrepared) {
                            try {
                                mediaPlayerRef?.isLooping = isLooping
                                if (isMuted) {
                                    mediaPlayerRef?.setVolume(0f, 0f)
                                } else {
                                    mediaPlayerRef?.setVolume(1f, 1f)
                                }
                                if (isPlaying && !view.isPlaying) {
                                    view.start()
                                } else if (!isPlaying && view.isPlaying) {
                                    view.pause()
                                }
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Fallback / Cover / Preview image when videoError or before prepared
            if (videoError || !isVideoPrepared) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(file)
                            .decoderFactory(VideoFrameDecoder.Factory())
                            .crossfade(true)
                            .build(),
                        contentDescription = file.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = file.name,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // Aspect Ratio Toast Notification
            androidx.compose.animation.AnimatedVisibility(
                visible = aspectToastMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                    shape = CircleShape,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = aspectToastMessage ?: "",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // Play/Pause Overlay Button
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(
                    onClick = {
                        if (!isPlaying && currentPositionMs >= totalDurationMs) {
                            currentPositionMs = 0L
                            if (isVideoPrepared && !videoError) {
                                try { videoViewRef?.seekTo(0) } catch (_: Exception) {}
                            }
                        }
                        isPlaying = !isPlaying
                        onResetControlsTimer()
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Video Progress & Controls (hides automatically in 5s)
        androidx.compose.animation.AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val currentProgress = (currentPositionMs.toFloat() / totalDurationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)

                // Progress Slider
                Slider(
                    value = currentProgress,
                    onValueChange = { newProgress ->
                        currentPositionMs = (newProgress * totalDurationMs).toLong()
                        if (isVideoPrepared && !videoError && videoViewRef != null) {
                            try {
                                videoViewRef?.seekTo(currentPositionMs.toInt())
                            } catch (_: Exception) {}
                        }
                        onResetControlsTimer()
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )

                val currentSeconds = (currentPositionMs / 1000L).toInt()
                val totalSeconds = (totalDurationMs / 1000L).toInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(currentSeconds), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text(formatTime(totalSeconds), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Video Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Loop Button
                    IconButton(onClick = {
                        isLooping = !isLooping
                        try {
                            mediaPlayerRef?.isLooping = isLooping
                        } catch (_: Exception) {}
                        onResetControlsTimer()
                    }) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = "Loop",
                            tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                        )
                    }
                    
                    // Aspect Ratio Button
                    IconButton(onClick = { 
                        if (!isFullscreen) {
                            aspectRatioIndex = (aspectRatioIndex + 1) % aspectRatios.size 
                            aspectToastMessage = "Proporção: ${aspectRatioLabels[aspectRatioIndex]}"
                        } else {
                            aspectToastMessage = "Modo Tela Cheia"
                        }
                        onResetControlsTimer()
                    }) {
                        Icon(
                            Icons.Default.AspectRatio,
                            contentDescription = "Aspect Ratio",
                            tint = if (!isFullscreen) Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }
                    
                    // Volume / Mute Button
                    IconButton(onClick = {
                        isMuted = !isMuted
                        try {
                            if (isMuted) {
                                mediaPlayerRef?.setVolume(0f, 0f)
                            } else {
                                mediaPlayerRef?.setVolume(1f, 1f)
                            }
                        } catch (_: Exception) {}
                        onResetControlsTimer()
                    }) {
                        Icon(
                            if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mute",
                            tint = if (isMuted) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.6f)
                        )
                    }
                    
                    // Fullscreen Button
                    IconButton(onClick = {
                        isFullscreen = !isFullscreen
                        onResetControlsTimer()
                    }) {
                        Icon(
                            if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = if (isFullscreen) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioPlayerContent(
    file: File,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    var isPlaying by remember(file.path) { mutableStateOf(true) }
    var progress by remember(file.path) { mutableFloatStateOf(0f) }
    var isLooping by remember { mutableStateOf(false) }
    var isAutoPlayNext by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val totalDurationSeconds = 210 // 03:30

    LaunchedEffect(showControls, isPlaying, lastInteractionTime) {
        if (showControls && isPlaying) {
            kotlinx.coroutines.delay(5000L)
            showControls = false
        }
    }

    val resetTimer: () -> Unit = {
        showControls = true
        lastInteractionTime = System.currentTimeMillis()
    }

    LaunchedEffect(isPlaying, progress, isLooping, isAutoPlayNext) {
        while (isPlaying) {
            kotlinx.coroutines.delay(1000L)
            if (progress >= 1f) {
                if (isLooping) {
                    progress = 0f
                } else if (isAutoPlayNext) {
                    progress = 0f
                    onNext()
                    break
                } else {
                    isPlaying = false
                }
            } else {
                progress = (progress + 1f / totalDurationSeconds).coerceAtMost(1f)
            }
        }
    }

    // Waveform Animation
    val infiniteTransition = rememberInfiniteTransition(label = "WaveAnimation")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (showControls) {
                            showControls = false
                        } else {
                            resetTimer()
                        }
                    }
                )
            },
        color = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = {
                        resetTimer()
                        onClose()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Reprodutor de Áudio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Áudio MP3 / WAV",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    Box(modifier = Modifier.size(48.dp))
                }
            }

            // Center Hero Artwork
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = file.nameWithoutExtension,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Waveform & Progress Slider
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    val barCount = 36
                    val barWidth = size.width / barCount
                    for (i in 0 until barCount) {
                        val heightFactor = if (isPlaying) {
                            (sin((i * 0.4f) + wavePhase) * 0.4f + 0.5f).coerceIn(0.12f, 1f)
                        } else 0.2f
                        val barHeight = size.height * heightFactor
                        val x = i * barWidth
                        val y = (size.height - barHeight) / 2

                        drawRoundRect(
                            color = primaryColor,
                            topLeft = Offset(x + 2f, y),
                            size = androidx.compose.ui.geometry.Size(barWidth - 4f, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = progress,
                            onValueChange = {
                                resetTimer()
                                progress = it
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )

                        val currentSeconds = (progress * totalDurationSeconds).toInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTime(currentSeconds), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            Text(formatTime(totalDurationSeconds), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                }
            }

            // Controls Row (Loop, Previous, Play/Pause, Next, AutoPlay)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Leftmost: Infinite Loop / Repeat
                    IconButton(onClick = {
                        resetTimer()
                        isLooping = !isLooping
                    }) {
                        Icon(
                            imageVector = if (isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Loop Infinito",
                            tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Previous
                    IconButton(onClick = {
                        resetTimer()
                        progress = 0f
                        onPrevious()
                    }) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Anterior",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play / Pause
                    IconButton(
                        onClick = {
                            resetTimer()
                            isPlaying = !isPlaying
                        },
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Tocar",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Next
                    IconButton(onClick = {
                        resetTimer()
                        progress = 0f
                        onNext()
                    }) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Próximo",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Rightmost: Continuous Playback (Auto-advance to next track)
                    IconButton(onClick = {
                        resetTimer()
                        isAutoPlayNext = !isAutoPlayNext
                    }) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = "Reprodução Contínua",
                            tint = if (isAutoPlayNext) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", m, s)
}

private fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

