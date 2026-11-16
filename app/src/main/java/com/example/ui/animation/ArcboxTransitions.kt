package com.example.ui.animation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.example.data.models.FolderTransitionType

// High-speed Material 3 Easing Curves tuned for 90Hz & 120Hz displays
val M3EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
val M3EmphasizedAccel = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

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

/**
 * Returns a high-performance, butter-smooth ContentTransform for folder transitions.
 * Timings and offsets are calibrated for clearly perceptible, fluid motion (340-400ms).
 */
fun getOptimizedFolderTransition(
    type: FolderTransitionType,
    initialState: String,
    targetState: String,
    enabled: Boolean = true
): ContentTransform {
    if (!enabled) {
        return (fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0)))
    }

    val initialDepth = initialState.count { it == '/' }
    val targetDepth = targetState.count { it == '/' }
    val isSubfolder = (targetState.startsWith(initialState) && targetState.length > initialState.length) || targetDepth > initialDepth
    val isParent = (initialState.startsWith(targetState) && initialState.length > targetState.length) || targetDepth < initialDepth

    return when (type) {
        FolderTransitionType.MATERIAL_SLIDE -> {
            if (isSubfolder) {
                // Forward navigation: entering from right (+38%), exiting to left (-18%) with visible depth
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 380, easing = M3EmphasizedDecel),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.38f).toInt() }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)
                ) + scaleIn(
                    animationSpec = tween(durationMillis = 380, easing = M3EmphasizedDecel),
                    initialScale = 0.92f
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 320, easing = M3EmphasizedAccel),
                        targetOffsetX = { fullWidth -> -(fullWidth * 0.18f).toInt() }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
                    ) + scaleOut(
                        animationSpec = tween(durationMillis = 320, easing = M3EmphasizedAccel),
                        targetScale = 0.94f
                    )
                ).apply {
                    targetContentZIndex = 1f
                }
            } else if (isParent) {
                // Backward navigation: entering from left (-18%), exiting to right (+38%)
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 380, easing = M3EmphasizedDecel),
                    initialOffsetX = { fullWidth -> -(fullWidth * 0.18f).toInt() }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)
                ) + scaleIn(
                    animationSpec = tween(durationMillis = 380, easing = M3EmphasizedDecel),
                    initialScale = 1.05f
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 320, easing = M3EmphasizedAccel),
                        targetOffsetX = { fullWidth -> (fullWidth * 0.38f).toInt() }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
                    ) + scaleOut(
                        animationSpec = tween(durationMillis = 320, easing = M3EmphasizedAccel),
                        targetScale = 1.05f
                    )
                ).apply {
                    targetContentZIndex = -1f
                }
            } else {
                // Peer folder navigation
                (fadeIn(animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)) +
                 slideInHorizontally(animationSpec = tween(durationMillis = 320, easing = M3EmphasizedDecel)) { (it * 0.18f).toInt() }
                ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)) +
                    slideOutHorizontally(animationSpec = tween(durationMillis = 280, easing = M3EmphasizedAccel)) { -(it * 0.18f).toInt() }
                )
            }
        }

        FolderTransitionType.ZOOM_EXPAND -> {
            if (isSubfolder) {
                (scaleIn(
                    animationSpec = tween(durationMillis = 380, easing = M3EmphasizedDecel),
                    initialScale = 0.78f
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 280, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    scaleOut(
                        animationSpec = tween(durationMillis = 320, easing = M3EmphasizedAccel),
                        targetScale = 1.18f
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
                    )
                ).apply {
                    targetContentZIndex = 1f
                }
            } else if (isParent) {
                (scaleIn(
                    animationSpec = tween(durationMillis = 380, easing = M3EmphasizedDecel),
                    initialScale = 1.18f
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 280, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    scaleOut(
                        animationSpec = tween(durationMillis = 320, easing = M3EmphasizedAccel),
                        targetScale = 0.78f
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
                    )
                ).apply {
                    targetContentZIndex = -1f
                }
            } else {
                (scaleIn(
                    animationSpec = tween(durationMillis = 300, easing = M3EmphasizedDecel),
                    initialScale = 0.90f
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 240, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    scaleOut(
                        animationSpec = tween(durationMillis = 280, easing = M3EmphasizedAccel),
                        targetScale = 1.08f
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                    )
                )
            }
        }

        FolderTransitionType.VERTICAL_SLIDE -> {
            if (isSubfolder) {
                (slideInVertically(
                    animationSpec = tween(durationMillis = 380, easing = M3EmphasizedDecel),
                    initialOffsetY = { fullHeight -> (fullHeight * 0.35f).toInt() }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)
                ) + scaleIn(
                    animationSpec = tween(durationMillis = 380, easing = M3EmphasizedDecel),
                    initialScale = 0.93f
                )).togetherWith(
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 320, easing = M3EmphasizedAccel),
                        targetOffsetY = { fullHeight -> -(fullHeight * 0.18f).toInt() }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                    )
                ).apply {
                    targetContentZIndex = 1f
                }
            } else if (isParent) {
                (slideInVertically(
                    animationSpec = tween(durationMillis = 380, easing = M3EmphasizedDecel),
                    initialOffsetY = { fullHeight -> -(fullHeight * 0.18f).toInt() }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)
                ) + scaleIn(
                    animationSpec = tween(durationMillis = 380, easing = M3EmphasizedDecel),
                    initialScale = 1.04f
                )).togetherWith(
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 320, easing = M3EmphasizedAccel),
                        targetOffsetY = { fullHeight -> (fullHeight * 0.35f).toInt() }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                    )
                ).apply {
                    targetContentZIndex = -1f
                }
            } else {
                (fadeIn(animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)) +
                 slideInVertically(animationSpec = tween(durationMillis = 320, easing = M3EmphasizedDecel)) { (it * 0.12f).toInt() }
                ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)) +
                    slideOutVertically(animationSpec = tween(durationMillis = 280, easing = M3EmphasizedAccel)) { -(it * 0.12f).toInt() }
                )
            }
        }

        FolderTransitionType.FADE_THROUGH -> {
            (fadeIn(
                animationSpec = tween(durationMillis = 320, easing = LinearOutSlowInEasing)
            ) + scaleIn(
                animationSpec = tween(durationMillis = 340, easing = M3EmphasizedDecel),
                initialScale = 0.90f
            )).togetherWith(
                fadeOut(
                    animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
                ) + scaleOut(
                    animationSpec = tween(durationMillis = 240, easing = M3EmphasizedAccel),
                    targetScale = 1.06f
                )
            )
        }

        FolderTransitionType.STACK_OVERLAY -> {
            if (isSubfolder) {
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 400, easing = M3EmphasizedDecel),
                    initialOffsetX = { fullWidth -> fullWidth }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    scaleOut(
                        animationSpec = tween(durationMillis = 360, easing = M3EmphasizedAccel),
                        targetScale = 0.90f
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 260, easing = FastOutLinearInEasing)
                    )
                ).apply {
                    targetContentZIndex = 2f
                }
            } else if (isParent) {
                (scaleIn(
                    animationSpec = tween(durationMillis = 400, easing = M3EmphasizedDecel),
                    initialScale = 0.90f
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 360, easing = M3EmphasizedAccel),
                        targetOffsetX = { fullWidth -> fullWidth }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
                    )
                ).apply {
                    targetContentZIndex = -1f
                }
            } else {
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 320, easing = M3EmphasizedDecel),
                    initialOffsetX = { (it * 0.30f).toInt() }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 240, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 280, easing = M3EmphasizedAccel),
                        targetOffsetX = { -(it * 0.30f).toInt() }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing)
                    )
                )
            }
        }
    }
}
