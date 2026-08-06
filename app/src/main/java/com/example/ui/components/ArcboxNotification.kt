package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun ArcboxNotification(
    message: String?,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayMessage by remember { mutableStateOf<String?>(null) }
    val anim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        if (message != null) {
            displayMessage = message
            // Animate open from 0f to 1f over 700ms
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
            )
            // Show for 3500ms
            delay(3500)
            // Animate close (reverse effect) over 700ms
            anim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
            )
            displayMessage = null
            onDismissed()
        } else {
            if (anim.value > 0f) {
                anim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
                )
                displayMessage = null
                onDismissed()
            }
        }
    }

    val phase = anim.value

    if (phase > 0f && displayMessage != null) {
        // Calculate dimensions based on the animation phase
        // 0.0 -> 0.3: Tiny circle grows to full-height circle
        // 0.3 -> 1.0: Circle expands horizontally to a rounded bar/capsule
        val minSize = 14f
        val circleSize = 56f
        val finalBarWidth = 320f

        val currentWidth = if (phase < 0.3f) {
            val t = phase / 0.3f
            (minSize + (circleSize - minSize) * t).dp
        } else {
            val t = (phase - 0.3f) / 0.7f
            (circleSize + (finalBarWidth - circleSize) * t).dp
        }

        val currentHeight = if (phase < 0.3f) {
            val t = phase / 0.3f
            (minSize + (circleSize - minSize) * t).dp
        } else {
            circleSize.dp
        }

        val opacity = if (phase < 0.3f) {
            phase / 0.3f
        } else {
            1f
        }

        val textOpacity = if (phase > 0.5f) {
            ((phase - 0.5f) / 0.5f).coerceIn(0f, 1f)
        } else {
            0f
        }

        Surface(
            modifier = modifier
                .width(currentWidth)
                .height(currentHeight)
                .graphicsLayer(alpha = opacity)
                .clip(RoundedCornerShape(currentHeight / 2))
                .clickable {
                    // Quick dismiss when clicked
                    displayMessage = null
                    onDismissed()
                },
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            if (phase >= 0.5f) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .graphicsLayer(alpha = textOpacity),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = displayMessage ?: "",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
