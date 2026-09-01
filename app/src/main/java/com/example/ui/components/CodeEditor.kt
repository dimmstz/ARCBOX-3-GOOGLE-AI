package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.FileItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcboxCodeEditorModal(
    fileItem: FileItem,
    initialContent: String,
    onSaveContent: (String) -> Unit,
    onClose: () -> Unit
) {
    val isBinaryContent = remember(initialContent) {
        initialContent.take(2048).any { it == '\u0000' }
    }
    var textContent by remember(initialContent) { mutableStateOf(initialContent) }
    var lastSavedContent by remember(initialContent) { mutableStateOf(initialContent) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }

    val isSaved = textContent == lastSavedContent
    val lines = remember(textContent) { textContent.split('\n') }
    val lineCountCap = lines.size.coerceAtMost(2000)
    val scrollState = rememberScrollState()

    val lineNumbersText = remember(lineCountCap) {
        (1..lineCountCap.coerceAtLeast(1)).joinToString("\n")
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
                // Editor Header
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = fileItem.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${lines.size} linhas • ${textContent.length} caracteres",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar Editor")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar no texto")
                        }
                        Button(
                            onClick = {
                                onSaveContent(textContent)
                                lastSavedContent = textContent
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = if (isSaved) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                if (isSaved) Icons.Default.Check else Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isSaved) "Salvo" else "Salvar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                if (isSearchVisible) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Localizar texto...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Editor Body with Line Numbers
                if (isBinaryContent) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Este arquivo contém dados binários e não pode ser exibido no editor de texto.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    ) {
                        // Line numbers sidebar
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Text(
                                text = lineNumbersText,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                ),
                                lineHeight = 20.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Text Editor Area
                        BasicTextField(
                            value = textContent,
                            onValueChange = { textContent = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(12.dp)
                                .fillMaxHeight(),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}
