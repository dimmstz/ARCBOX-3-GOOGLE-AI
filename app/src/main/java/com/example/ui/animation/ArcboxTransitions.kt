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
 * Durations and offsets are carefully tuned to avoid GPU overdraw and texture thrashing.
 */
fun getOptimizedFolderTransition(
    type: FolderTransitionType,
    initialState: String,
    targetState: String
): ContentTransform {
    val initialDepth = initialState.count { it == '/' }
    val targetDepth = targetState.count { it == '/' }
    val isSubfolder = (targetState.startsWith(initialState) && targetState.length > initialState.length) || targetDepth > initialDepth
    val isParent = (initialState.startsWith(targetState) && initialState.length > targetState.length) || targetDepth < initialDepth

    return when (type) {
        FolderTransitionType.MATERIAL_SLIDE -> {
            if (isSubfolder) {
                // Forward navigation: entering from right (+16%), exiting to left (-8%)
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 210, easing = M3EmphasizedDecel),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.16f).toInt() }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)
                ) + scaleIn(
                    animationSpec = tween(durationMillis = 210, easing = M3EmphasizedDecel),
                    initialScale = 0.98f
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 160, easing = M3EmphasizedAccel),
                        targetOffsetX = { fullWidth -> -(fullWidth * 0.08f).toInt() }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 130, easing = FastOutLinearInEasing)
                    )
                ).apply {
                    targetContentZIndex = 1f
                }
            } else if (isParent) {
                // Backward navigation: entering from left (-8%), exiting to right (+16%)
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 210, easing = M3EmphasizedDecel),
                    initialOffsetX = { fullWidth -> -(fullWidth * 0.08f).toInt() }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 170, easing = M3EmphasizedAccel),
                        targetOffsetX = { fullWidth -> (fullWidth * 0.16f).toInt() }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 130, easing = FastOutLinearInEasing)
                    ) + scaleOut(
                        animationSpec = tween(durationMillis = 170, easing = M3EmphasizedAccel),
                        targetScale = 0.98f
                    )
                ).apply {
                    targetContentZIndex = -1f
                }
            } else {
                // Peer folder navigation
                (fadeIn(animationSpec = tween(durationMillis = 170, easing = LinearOutSlowInEasing)) +
                 slideInHorizontally(animationSpec = tween(durationMillis = 190, easing = M3EmphasizedDecel)) { (it * 0.06f).toInt() }
                ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing)) +
                    slideOutHorizontally(animationSpec = tween(durationMillis = 160, easing = M3EmphasizedAccel)) { -(it * 0.06f).toInt() }
                )
            }
        }

        FolderTransitionType.ZOOM_EXPAND -> {
            if (isSubfolder) {
                (scaleIn(
                    animationSpec = tween(durationMillis = 210, easing = M3EmphasizedDecel),
                    initialScale = 0.92f
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    scaleOut(
                        animationSpec = tween(durationMillis = 160, easing = M3EmphasizedAccel),
                        targetScale = 1.05f
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 130, easing = FastOutLinearInEasing)
                    )
                ).apply {
                    targetContentZIndex = 1f
                }
            } else if (isParent) {
                (scaleIn(
                    animationSpec = tween(durationMillis = 210, easing = M3EmphasizedDecel),
                    initialScale = 1.05f
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    scaleOut(
                        animationSpec = tween(durationMillis = 160, easing = M3EmphasizedAccel),
                        targetScale = 0.92f
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 130, easing = FastOutLinearInEasing)
                    )
                ).apply {
                    targetContentZIndex = -1f
                }
            } else {
                (scaleIn(
                    animationSpec = tween(durationMillis = 180, easing = M3EmphasizedDecel),
                    initialScale = 0.96f
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    scaleOut(
                        animationSpec = tween(durationMillis = 150, easing = M3EmphasizedAccel),
                        targetScale = 1.04f
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 120, easing = FastOutLinearInEasing)
                    )
                )
            }
        }

        FolderTransitionType.VERTICAL_SLIDE -> {
            if (isSubfolder) {
                (slideInVertically(
                    animationSpec = tween(durationMillis = 210, easing = M3EmphasizedDecel),
                    initialOffsetY = { fullHeight -> (fullHeight * 0.14f).toInt() }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 160, easing = M3EmphasizedAccel),
                        targetOffsetY = { fullHeight -> -(fullHeight * 0.08f).toInt() }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 130, easing = FastOutLinearInEasing)
                    )
                ).apply {
                    targetContentZIndex = 1f
                }
            } else if (isParent) {
                (slideInVertically(
                    animationSpec = tween(durationMillis = 210, easing = M3EmphasizedDecel),
                    initialOffsetY = { fullHeight -> -(fullHeight * 0.08f).toInt() }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 160, easing = M3EmphasizedAccel),
                        targetOffsetY = { fullHeight -> (fullHeight * 0.14f).toInt() }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 130, easing = FastOutLinearInEasing)
                    )
                ).apply {
                    targetContentZIndex = -1f
                }
            } else {
                (fadeIn(animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)) +
                 slideInVertically(animationSpec = tween(durationMillis = 180, easing = M3EmphasizedDecel)) { (it * 0.05f).toInt() }
                ).togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 130, easing = FastOutLinearInEasing)) +
                    slideOutVertically(animationSpec = tween(durationMillis = 150, easing = M3EmphasizedAccel)) { -(it * 0.05f).toInt() }
                )
            }
        }

        FolderTransitionType.FADE_THROUGH -> {
            (fadeIn(
                animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing)
            ) + scaleIn(
                animationSpec = tween(durationMillis = 180, easing = M3EmphasizedDecel),
                initialScale = 0.97f
            )).togetherWith(
                fadeOut(
                    animationSpec = tween(durationMillis = 130, easing = FastOutLinearInEasing)
                ) + scaleOut(
                    animationSpec = tween(durationMillis = 130, easing = M3EmphasizedAccel),
                    targetScale = 1.02f
                )
            )
        }

        FolderTransitionType.STACK_OVERLAY -> {
            if (isSubfolder) {
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 220, easing = M3EmphasizedDecel),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.65f).toInt() }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    scaleOut(
                        animationSpec = tween(durationMillis = 220, easing = M3EmphasizedAccel),
                        targetScale = 0.95f
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 160, easing = FastOutLinearInEasing)
                    )
                ).apply {
                    targetContentZIndex = 2f
                }
            } else if (isParent) {
                (scaleIn(
                    animationSpec = tween(durationMillis = 220, easing = M3EmphasizedDecel),
                    initialScale = 0.95f
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 200, easing = M3EmphasizedAccel),
                        targetOffsetX = { fullWidth -> (fullWidth * 0.65f).toInt() }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing)
                    )
                ).apply {
                    targetContentZIndex = -1f
                }
            } else {
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 180, easing = M3EmphasizedDecel),
                    initialOffsetX = { (it * 0.15f).toInt() }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing)
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 150, easing = M3EmphasizedAccel),
                        targetOffsetX = { -(it * 0.15f).toInt() }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 130, easing = FastOutLinearInEasing)
                    )
                )
            }
        }
    }
}
