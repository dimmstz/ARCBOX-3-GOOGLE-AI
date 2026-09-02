package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcboxPdfViewerModal(
    fileItem: FileItem,
    onClose: () -> Unit,
    onOpenWithThirdParty: ((FileItem) -> Unit)? = null
) {
    val context = LocalContext.current
    var pageCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var renderedPages by remember { mutableStateOf<Map<Int, Bitmap>>(emptyMap()) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val listState = rememberLazyListState()

    LaunchedEffect(fileItem.path) {
        isLoading = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                pfd = openPfdForPdf(context, fileItem)
                if (pfd != null) {
                    renderer = PdfRenderer(pfd)
                    val count = renderer.pageCount
                    pageCount = count

                    val initialMap = mutableMapOf<Int, Bitmap>()
                    val preRenderCount = minOf(4, count)
                    for (i in 0 until preRenderCount) {
                        renderPageBitmap(renderer, i)?.let { bmp ->
                            initialMap[i] = bmp
                        }
                    }
                    renderedPages = initialMap
                } else {
                    errorMessage = "Não foi possível abrir o arquivo PDF."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Erro ao renderizar PDF: ${e.localizedMessage ?: "Arquivo corrompido ou protegido por senha."}"
            } finally {
                try { renderer?.close() } catch (_: Exception) {}
                try { pfd?.close() } catch (_: Exception) {}
                isLoading = false
            }
        }
    }

    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleItemIndex, pageCount) {
        if (pageCount == 0 || errorMessage != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val start = (firstVisibleItemIndex - 2).coerceAtLeast(0)
            val end = (firstVisibleItemIndex + 4).coerceAtMost(pageCount - 1)
            val missingIndices = (start..end).filter { !renderedPages.containsKey(it) }
            if (missingIndices.isNotEmpty()) {
                var pfd: ParcelFileDescriptor? = null
                var renderer: PdfRenderer? = null
                try {
                    pfd = openPfdForPdf(context, fileItem)
                    if (pfd != null) {
                        renderer = PdfRenderer(pfd)
                        val newMap = renderedPages.toMutableMap()
                        for (idx in missingIndices) {
                            renderPageBitmap(renderer, idx)?.let { bmp ->
                                newMap[idx] = bmp
                            }
                        }
                        val keysToRemove = newMap.keys.filter { it < start - 5 || it > end + 5 }
                        for (k in keysToRemove) {
                            newMap.remove(k)?.recycle()
                        }
                        renderedPages = newMap
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try { renderer?.close() } catch (_: Exception) {}
                    try { pfd?.close() } catch (_: Exception) {}
                }
            }
        }
    }

    DisposableEffect(fileItem.path) {
        onDispose {
            renderedPages.values.forEach { bmp ->
                try { if (!bmp.isRecycled) bmp.recycle() } catch (_: Exception) {}
            }
        }
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
            color = Color(0xFF1E1E1E)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = fileItem.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (pageCount > 0) {
                                Text(
                                    text = "Página ${firstVisibleItemIndex + 1} de $pageCount",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                        }
                    },
                    actions = {
                        if (onOpenWithThirdParty != null) {
                            IconButton(onClick = { onOpenWithThirdParty(fileItem) }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Abrir em aplicativo externo", tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF121212)
                    )
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Carregando PDF...",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                        errorMessage != null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color(0xFFEF4444)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = errorMessage ?: "Erro ao ler PDF",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Você pode tentar abrir este arquivo em um leitor de PDF externo.",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                if (onOpenWithThirdParty != null) {
                                    Button(
                                        onClick = { onOpenWithThirdParty(fileItem) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Abrir em app externo")
                                    }
                                }
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 4f)
                                            if (scale == 1f) {
                                                offsetX = 0f
                                                offsetY = 0f
                                            } else {
                                                offsetX += pan.x
                                                offsetY += pan.y
                                            }
                                        }
                                    }
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = scale,
                                            scaleY = scale,
                                            translationX = offsetX,
                                            translationY = offsetY
                                        ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    items(pageCount) { pageIdx ->
                                        val bitmap = renderedPages[pageIdx]
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .wrapContentHeight(),
                                            shape = RoundedCornerShape(8.dp),
                                            shadowElevation = 4.dp,
                                            color = Color.White
                                        ) {
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = "Página ${pageIdx + 1}",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(400.dp)
                                                        .background(Color.White),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(color = Color(0xFF0284C7))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (pageCount > 0 && errorMessage == null) {
                    Surface(
                        color = Color(0xFF121212),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total: $pageCount páginas",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                            if (scale > 1f) {
                                TextButton(onClick = {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }) {
                                    Text("Resetar Zoom", color = Color(0xFF38BDF8))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openPfdForPdf(context: Context, item: FileItem): ParcelFileDescriptor? {
    return try {
        if (item.path.startsWith("content://") || item.safUriString != null) {
            val uri = Uri.parse(item.safUriString ?: item.path)
            context.contentResolver.openFileDescriptor(uri, "r")
        } else {
            val file = File(item.path)
            if (file.exists() && file.canRead()) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun renderPageBitmap(renderer: PdfRenderer, pageIndex: Int): Bitmap? {
    return try {
        val page = renderer.openPage(pageIndex)
        // Scaled resolution (max 1440px width) to preserve sharpness without excessive RAM consumption
        val targetW = (page.width * 2.0f).toInt().coerceIn(720, 1440)
        val scaleRatio = targetW.toFloat() / page.width.toFloat()
        val targetH = (page.height * scaleRatio).toInt().coerceAtLeast(400)

        val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
