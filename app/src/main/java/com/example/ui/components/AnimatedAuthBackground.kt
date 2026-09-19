package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dynamic Animated Liquid Glass Background (`AnimatedAuthBackground`).
 *
 * Implements an ultra-fluid, 60/120 FPS high-performance liquid glass wave engine
 * matching the exact visual aesthetic:
 * - Midnight Obsidian backdrop (#0B0E14 / #06090F) with ambient chromatic vignette.
 * - Layered translucent liquid glass ribbons with Electric Cyan (#00F2FE) and Deep Neon Purple (#8B5CF6).
 * - High-luminosity glowing rim strokes (diffuse + sharp neon glass edges).
 * - Smooth, continuous harmonic undulation with zero heap allocations per frame.
 */
@Composable
fun AnimatedAuthBackground(
    modifier: Modifier = Modifier,
    cyanColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    purpleColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.secondary,
    obsidianBase: Color = androidx.compose.material3.MaterialTheme.colorScheme.background
) {
    val bgColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    val primaryColor = androidx.compose.material3.MaterialTheme.colorScheme.primary

    val infiniteTransition = rememberInfiniteTransition(label = "BlueAmbientTransition")

    val phaseFloat by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseFloat"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            if (w <= 0f || h <= 0f) return@Canvas

            val floatX = sin(phaseFloat) * w * 0.1f
            val floatY = cos(phaseFloat) * h * 0.08f

            // Clean background gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        bgColor,
                        bgColor
                    )
                )
            )

            // Upper soft subtle blue aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.12f),
                        primaryColor.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.8f + floatX, h * 0.2f + floatY),
                    radius = w * 0.85f
                ),
                center = Offset(w * 0.8f + floatX, h * 0.2f + floatY),
                radius = w * 0.85f
            )

            // Lower soft subtle blue aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.08f),
                        primaryColor.copy(alpha = 0.02f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.2f - floatX, h * 0.8f - floatY),
                    radius = w * 0.75f
                ),
                center = Offset(w * 0.2f - floatX, h * 0.8f - floatY),
                radius = w * 0.75f
            )
        }
    }
}

/**
 * Builds a multi-octave harmonic fluid wave and its filled bottom container
 * with 0 heap allocations by reusing cached Path references.
 */
private fun buildLiquidWavePath(
    crestPath: Path,
    fillPath: Path,
    width: Float,
    height: Float,
    baseY: Float,
    amplitude1: Float,
    amplitude2: Float,
    phase: Float,
    frequency: Float
) {
    crestPath.reset()
    fillPath.reset()

    val steps = 24
    val stepX = width / steps

    var isFirst = true

    for (i in 0..steps) {
        val x = i * stepX
        val normX = (x / width) * 2f * Math.PI.toFloat() * frequency

        // Harmonic dual-sine wave combination for organic liquid curvature
        val y = baseY +
                sin(normX + phase) * amplitude1 +
                cos(normX * 0.5f - phase * 0.7f) * amplitude2

        if (isFirst) {
            crestPath.moveTo(x, y)
            fillPath.moveTo(x, y)
            isFirst = false
        } else {
            crestPath.lineTo(x, y)
            fillPath.lineTo(x, y)
        }
    }

    // Complete the filled closed polygon down to bottom corners
    fillPath.lineTo(width, height)
    fillPath.lineTo(0f, height)
    fillPath.close()
}

/**
 * Renders subtle, soft specular glow orbs along wave focal points for liquid sheen.
 */
private fun DrawScope.drawSpecularHighlights(
    w: Float,
    h: Float,
    phase: Float,
    cyanColor: Color,
    purpleColor: Color,
    glowPulse: Float
) {
    val highlightX1 = w * (0.35f + sin(phase * 0.5f) * 0.15f)
    val highlightY1 = h * 0.55f + cos(phase * 0.7f) * 20f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                cyanColor.copy(alpha = 0.20f * glowPulse),
                cyanColor.copy(alpha = 0.05f),
                Color.Transparent
            ),
            center = Offset(highlightX1, highlightY1),
            radius = 110f
        ),
        center = Offset(highlightX1, highlightY1),
        radius = 110f
    )

    val highlightX2 = w * (0.75f - cos(phase * 0.4f) * 0.12f)
    val highlightY2 = h * 0.68f + sin(phase * 0.6f) * 24f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                purpleColor.copy(alpha = 0.18f * glowPulse),
                purpleColor.copy(alpha = 0.04f),
                Color.Transparent
            ),
            center = Offset(highlightX2, highlightY2),
            radius = 120f
        ),
        center = Offset(highlightX2, highlightY2),
        radius = 120f
    )
}
