package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.StorageVolume
import java.io.File

@Composable
fun ArcboxBreadcrumbHeader(
    currentPath: String,
    onNavigateToPath: (String) -> Unit,
    onNavigateUp: () -> Unit,
    storageVolumes: List<StorageVolume> = emptyList(),
    selectedCount: Int = 0,
    isFilterActive: Boolean = false
) {
    val segments = parsePathSegments(currentPath, storageVolumes)
    val listState = rememberLazyListState()

    val matchedVolume = storageVolumes.filter { currentPath.startsWith(it.path) }.maxByOrNull { it.path.length }
    val canGoUp = if (matchedVolume != null) {
        currentPath.length > matchedVolume.path.length || isFilterActive
    } else {
        (currentPath.length > 1 && currentPath != "/storage/emulated/0") || isFilterActive
    }

    LaunchedEffect(segments.size) {
        if (segments.isNotEmpty()) {
            listState.animateScrollToItem(segments.size - 1)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Up button
        IconButton(
            onClick = onNavigateUp,
            enabled = canGoUp,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (isFilterActive) "Limpar filtro e voltar" else "Voltar pasta anterior",
                tint = if (canGoUp) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Breadcrumb Trail
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(segments) { index, segment ->
                val isLast = index == segments.size - 1
                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                val lastContainerColor = MaterialTheme.colorScheme.primaryContainer
                val lastContentColor = MaterialTheme.colorScheme.primary

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { onNavigateToPath(segment.fullPath) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isLast) lastContainerColor else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isLast) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else if (isDark) Color(0xFF3F3F46) else Color(0xFFCBD5E1)
                        ),
                        shadowElevation = if (isLast && !isDark) 1.dp else 0.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            if (index == 0) {
                                Icon(
                                    Icons.Default.Home,
                                    contentDescription = "Início",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isLast) lastContentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = segment.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                color = if (isLast) lastContentColor else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!isLast) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        if (selectedCount > 0) {
            val badgeContainer = MaterialTheme.colorScheme.primaryContainer
            val badgeContent = MaterialTheme.colorScheme.primary
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = badgeContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                shadowElevation = 0.dp,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = "$selectedCount selec.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeContent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 11.sp
                )
            }
        }
    }
}

data class PathSegment(
    val displayName: String,
    val fullPath: String
)

private fun parsePathSegments(
    path: String,
    storageVolumes: List<StorageVolume> = emptyList()
): List<PathSegment> {
    if (path.isBlank()) {
        return listOf(PathSegment("Armazenamento", "/storage/emulated/0"))
    }
    if (path == "/") {
        return listOf(PathSegment("Raiz", "/"))
    }

    val matchingVolume = storageVolumes
        .filter { path.startsWith(it.path) }
        .maxByOrNull { it.path.length }

    if (matchingVolume != null && (matchingVolume.path != "/" || !storageVolumes.any { it.path != "/" && path.startsWith(it.path) })) {
        val result = mutableListOf<PathSegment>()
        val rootName = when (matchingVolume.typeKey) {
            "INTERNAL" -> "Armazenamento"
            "ROOT" -> "Raiz"
            else -> matchingVolume.name
        }
        val basePath = if (matchingVolume.path == "/") "/" else matchingVolume.path.removeSuffix("/")
        result.add(PathSegment(rootName, basePath))

        val subPath = path.removePrefix(basePath).trim('/')
        if (subPath.isNotBlank()) {
            var accum = basePath
            val parts = subPath.split('/')
            for (part in parts) {
                accum = if (accum == "/") "/$part" else "$accum/$part"
                result.add(PathSegment(part, accum))
            }
        }
        return result
    }

    val result = mutableListOf<PathSegment>()

    if (path.startsWith("/storage/emulated/0")) {
        result.add(PathSegment("Armazenamento", "/storage/emulated/0"))
        val subPath = path.removePrefix("/storage/emulated/0").trim('/')
        if (subPath.isNotBlank()) {
            var accum = "/storage/emulated/0"
            val parts = subPath.split('/')
            for (part in parts) {
                accum += "/$part"
                result.add(PathSegment(part, accum))
            }
        }
    } else {
        var accum = ""
        val parts = path.split('/').filter { it.isNotBlank() }
        result.add(PathSegment("Raiz", "/"))
        for (part in parts) {
            accum = "$accum/$part"
            result.add(PathSegment(part, accum))
        }
    }

    return result
}
