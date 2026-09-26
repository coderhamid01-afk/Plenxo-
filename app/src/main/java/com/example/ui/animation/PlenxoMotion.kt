package com.example.ui.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Plenxo Premium Motion System
 * Centralized durations, easings, and reusable animation specs.
 */
object PlenxoMotion {
    // Durations
    const val DurationShort = 160
    const val DurationMedium = 240
    const val DurationLong = 320

    // Easings
    val StandardEasing = FastOutSlowInEasing
    val DecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val AccelerateEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

    // Animation Specs
    fun <T> tweenSpec(duration: Int = DurationMedium, easing: Easing = StandardEasing) =
        tween<T>(durationMillis = duration, easing = easing)

    fun <T> springSpec(
        dampingRatio: Float = Spring.DampingRatioNoBouncy,
        stiffness: Float = Spring.StiffnessLow
    ) = spring<T>(dampingRatio = dampingRatio, stiffness = stiffness)

    // Interaction Specs
    val PressInteractionSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    // Screen Transitions
    val ScreenEnterTransition = fadeIn(animationSpec = tween(DurationMedium)) +
            slideInHorizontally(
                initialOffsetX = { (it * 0.08f).toInt() },
                animationSpec = tween(DurationMedium, easing = DecelerateEasing)
            )

    val ScreenExitTransition = fadeOut(animationSpec = tween(DurationShort)) +
            slideOutHorizontally(
                targetOffsetX = { (-it * 0.08f).toInt() },
                animationSpec = tween(DurationShort, easing = AccelerateEasing)
            )

    val ScreenPopEnterTransition = fadeIn(animationSpec = tween(DurationMedium)) +
            slideInHorizontally(
                initialOffsetX = { (-it * 0.08f).toInt() },
                animationSpec = tween(DurationMedium, easing = DecelerateEasing)
            )

    val ScreenPopExitTransition = fadeOut(animationSpec = tween(DurationShort)) +
            slideOutHorizontally(
                targetOffsetX = { (it * 0.08f).toInt() },
                animationSpec = tween(DurationShort, easing = AccelerateEasing)
            )
}

/**
 * Premium button click modifier with subtle scale feedback.
 */
fun Modifier.plenxoClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = PlenxoMotion.PressInteractionSpec,
        label = "PressScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = androidx.compose.material3.ripple(),
            enabled = enabled,
            onClick = {
                // We use a small delay or manual state toggling if detectTapGestures is used,
                // but standard clickable with interactionSource is safer for accessibility.
                onClick()
            }
        )
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                        isPressed = true
                    } else if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Release ||
                        event.type == androidx.compose.ui.input.pointer.PointerEventType.Exit) {
                        isPressed = false
                    }
                }
            }
        }
}

/**
 * A modifier that applies a subtle entrance animation to content.
 */
fun Modifier.subtleEntrance(
    index: Int = 0,
    delay: Int = 40
): Modifier = composed {
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = PlenxoMotion.DurationMedium,
                delayMillis = index * delay,
                easing = PlenxoMotion.DecelerateEasing
            )
        )
    }

    this.graphicsLayer {
        alpha = animProgress.value
        translationY = (1f - animProgress.value) * 16.dp.toPx()
        scaleX = 0.98f + (animProgress.value * 0.02f)
        scaleY = 0.98f + (animProgress.value * 0.02f)
    }
}
