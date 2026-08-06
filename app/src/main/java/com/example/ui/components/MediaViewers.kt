package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.models.FileItem
import com.example.data.models.FileType
import java.io.File
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcboxMediaViewerModal(
    item: FileItem,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onClose: () -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.95f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
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
                        IconButton(onClick = { showInfo = !showInfo }) {
                            Icon(Icons.Default.Info, contentDescription = "Informações", tint = Color.White)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (item.fileType) {
                            FileType.IMAGE -> ImageViewerContent(file = File(item.path))
                            FileType.VIDEO -> VideoPlayerContent(file = File(item.path))
                            FileType.AUDIO -> AudioPlayerContent(file = File(item.path))
                            else -> {
                                Text("Formato de mídia não suportado para pré-visualização direta.", color = Color.White)
                            }
                        }
                    }
                }
                
                // Navigation Buttons overlay
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            .size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Anterior", tint = Color.White)
                    }
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            .size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Próxima", tint = Color.White)
                    }
                }

                if (showInfo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 70.dp, end = 16.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text("Caminho: ${item.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Tamanho: ${formatFileSize(item.size)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Modificado: ${formatDate(item.lastModified)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageViewerContent(file: File) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.8f, 5f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    rotationZ = rotation
                )
        )
        
        // Rotate Buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = { rotation -= 90f }) {
                Icon(Icons.Default.RotateLeft, contentDescription = "Rotacionar Esquerda", tint = Color.White)
            }
            IconButton(onClick = { rotation += 90f }) {
                Icon(Icons.Default.RotateRight, contentDescription = "Rotacionar Direita", tint = Color.White)
            }
        }
    }
}

@Composable
fun VideoPlayerContent(file: File) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableFloatStateOf(0.25f) }
    val totalDurationSeconds = 270 // 04:30
    
    // Video Options State
    var isLooping by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    val aspectRatios = listOf(16 / 9f, 4 / 3f, 1f, 21 / 9f)
    val aspectRatioLabels = listOf("16:9", "4:3", "1:1", "21:9")
    var aspectRatioIndex by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Auto-hide controls & Toast state
    var showControls by remember { mutableStateOf(true) }
    var interactionToken by remember { mutableIntStateOf(0) }
    var aspectToastMessage by remember { mutableStateOf<String?>(null) }

    fun resetControlsTimer() {
        showControls = true
        interactionToken++
    }

    LaunchedEffect(showControls, interactionToken, isPlaying) {
        if (showControls) {
            kotlinx.coroutines.delay(5000L)
            showControls = false
        }
    }

    LaunchedEffect(aspectToastMessage) {
        if (aspectToastMessage != null) {
            kotlinx.coroutines.delay(2000L)
            aspectToastMessage = null
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            kotlinx.coroutines.delay(1000L)
            val nextProgress = currentProgress + 1f / totalDurationSeconds
            if (nextProgress >= 1f) {
                currentProgress = 0f
                if (!isLooping) {
                    isPlaying = false
                }
            } else {
                currentProgress = nextProgress
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isFullscreen) 9 / 16f else aspectRatios[aspectRatioIndex])
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E293B))
                .align(Alignment.Center)
                .clickable {
                    showControls = !showControls
                    if (showControls) resetControlsTimer()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(file.name, color = Color.White, fontWeight = FontWeight.SemiBold)
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

            // Play/Pause Overlay Button (hides after 5s)
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(
                    onClick = {
                        if (!isPlaying && currentProgress >= 1f) {
                            currentProgress = 0f
                        }
                        isPlaying = !isPlaying
                        resetControlsTimer()
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
                // Progress Slider
                Slider(
                    value = currentProgress,
                    onValueChange = {
                        currentProgress = it
                        resetControlsTimer()
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )

                val currentSeconds = (currentProgress * totalDurationSeconds).toInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTime(currentSeconds), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text(formatTime(totalDurationSeconds), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
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
                        resetControlsTimer()
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
                        resetControlsTimer()
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
                        resetControlsTimer()
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
                        resetControlsTimer()
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
fun AudioPlayerContent(file: File) {
    var isPlaying by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0.25f) }
    val totalDurationSeconds = 210 // 03:30

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            kotlinx.coroutines.delay(1000L)
            if (progress >= 1f) {
                isPlaying = false
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
            .fillMaxWidth(0.9f)
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF1E293B)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                file.nameWithoutExtension,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                "Áudio MP3 / WAV",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Mini Waveform Visualizer
            val primaryColor = MaterialTheme.colorScheme.primary
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                val barCount = 32
                val barWidth = size.width / barCount
                for (i in 0 until barCount) {
                    val heightFactor = if (isPlaying) {
                        (sin((i * 0.4f) + wavePhase) * 0.4f + 0.5f).coerceIn(0.1f, 1f)
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

            Spacer(modifier = Modifier.height(16.dp))

            Slider(
                value = progress,
                onValueChange = { progress = it },
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

            Spacer(modifier = Modifier.height(16.dp))

            // Controls Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                IconButton(onClick = { progress = 0f }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                IconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Tocar",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { progress = 1f }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Próximo", tint = Color.White, modifier = Modifier.size(32.dp))
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

