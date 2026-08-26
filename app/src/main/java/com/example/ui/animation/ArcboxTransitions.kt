package com.example.ui.animation

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

val HighRefreshSpringSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

val HighRefreshSpringDpSpec = spring<androidx.compose.ui.unit.Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

val SnappySpringSpec = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = Spring.StiffnessMedium
)

val ArcboxModalEnter = scaleIn(
    animationSpec = HighRefreshSpringSpec,
    initialScale = 0.94f
) + fadeIn(
    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
)

val ArcboxModalExit = scaleOut(
    targetScale = 0.96f,
    animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing)
) + fadeOut(
    animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing)
)

