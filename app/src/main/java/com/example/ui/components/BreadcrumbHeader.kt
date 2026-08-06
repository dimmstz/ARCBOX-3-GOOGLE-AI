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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun ArcboxBreadcrumbHeader(
    currentPath: String,
    onNavigateToPath: (String) -> Unit,
    onNavigateUp: () -> Unit,
    selectedCount: Int = 0
) {
    val segments = parsePathSegments(currentPath)
    val listState = rememberLazyListState()

    LaunchedEffect(segments.size) {
        if (segments.isNotEmpty()) {
            listState.animateScrollToItem(segments.size - 1)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Up button
        IconButton(
            onClick = onNavigateUp,
            enabled = currentPath.length > 1 && currentPath != "/storage/emulated/0",
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar pasta anterior",
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { onNavigateToPath(segment.fullPath) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isLast) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        tonalElevation = if (isLast) 2.dp else 0.dp
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
                                    tint = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = segment.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
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
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = "$selectedCount selec.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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

private fun parsePathSegments(path: String): List<PathSegment> {
    if (path.isBlank() || path == "/") {
        return listOf(PathSegment("Raíz", "/"))
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
        result.add(PathSegment("Raíz", "/"))
        for (part in parts) {
            accum += "/$part"
            result.add(PathSegment(part, accum))
        }
    }

    return result
}
