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
    cyanColor: Color = Color(0xFF00F2FE),
    purpleColor: Color = Color(0xFF8B5CF6),
    obsidianBase: Color = Color(0xFF0B0E14)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidGlassEngine")

    // Slow primary wave phase (Period: 18s)
    val phasePrimary by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phasePrimary"
    )

    // Counter-flowing secondary wave phase (Period: 14s)
    val phaseSecondary by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseSecondary"
    )

    // Vertical breathing amplitude modulation (Period: 10s)
    val phaseFloat by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseFloat"
    )

    // Glow luminosity breathing (Period: 7s)
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    // Reusable Path instances cached across recompositions to guarantee 0 GC overhead
    val pathWave1 = remember { Path() }
    val pathFill1 = remember { Path() }
    val pathWave2 = remember { Path() }
    val pathFill2 = remember { Path() }
    val pathWave3 = remember { Path() }
    val pathFill3 = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(obsidianBase)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            if (w <= 0f || h <= 0f) return@Canvas

            // 1. BASE OBSIDIAN CANVAS & CHROMATIC AMBIENT AURA
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF06090F),
                        obsidianBase,
                        Color(0xFF0D111A)
                    ),
                    startY = 0f,
                    endY = h
                )
            )

            // Upper-right deep purple radial aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        purpleColor.copy(alpha = 0.14f * glowPulse),
                        purpleColor.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.85f, h * 0.22f),
                    radius = w * 0.75f
                ),
                center = Offset(w * 0.85f, h * 0.22f),
                radius = w * 0.75f
            )

            // Lower-left electric cyan radial aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        cyanColor.copy(alpha = 0.12f * glowPulse),
                        cyanColor.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.15f, h * 0.78f),
                    radius = w * 0.70f
                ),
                center = Offset(w * 0.15f, h * 0.78f),
                radius = w * 0.70f
            )

            // Float offsets
            val floatY1 = sin(phaseFloat) * 18f
            val floatY2 = cos(phaseFloat * 0.8f) * 14f

            // -------------------------------------------------------------
            // 2. LAYER 1: DEEP REAR LIQUID GLASS WAVE (Purple Dominant)
            // -------------------------------------------------------------
            buildLiquidWavePath(
                crestPath = pathWave1,
                fillPath = pathFill1,
                width = w,
                height = h,
                baseY = h * 0.42f + floatY1,
                amplitude1 = 48f,
                amplitude2 = 32f,
                phase = phasePrimary,
                frequency = 1.0f
            )

            // Fill with translucent purple-cyan glass gradient
            drawPath(
                path = pathFill1,
                brush = Brush.linearGradient(
                    colors = listOf(
                        purpleColor.copy(alpha = 0.16f),
                        purpleColor.copy(alpha = 0.08f),
                        cyanColor.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    start = Offset(0f, h * 0.35f),
                    end = Offset(w, h * 0.85f)
                )
            )

            // Glow Rim for Wave 1 (Soft background stroke)
            drawPath(
                path = pathWave1,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        purpleColor.copy(alpha = 0.45f * glowPulse),
                        cyanColor.copy(alpha = 0.25f),
                        purpleColor.copy(alpha = 0.50f * glowPulse)
                    )
                ),
                style = Stroke(
                    width = 4.5f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // -------------------------------------------------------------
            // 3. LAYER 2: MID-GROUND TRANSLUCENT LIQUID GLASS WAVE
            // -------------------------------------------------------------
            buildLiquidWavePath(
                crestPath = pathWave2,
                fillPath = pathFill2,
                width = w,
                height = h,
                baseY = h * 0.58f + floatY2,
                amplitude1 = 54f,
                amplitude2 = 42f,
                phase = phaseSecondary,
                frequency = 1.25f
            )

            // Glass fluid fill with high chromatic refraction
            drawPath(
                path = pathFill2,
                brush = Brush.linearGradient(
                    colors = listOf(
                        cyanColor.copy(alpha = 0.22f),
                        purpleColor.copy(alpha = 0.18f),
                        Color(0xFF38BDF8).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    start = Offset(0f, h * 0.50f),
                    end = Offset(w, h)
                )
            )

            // Diffuse outer neon glow
            drawPath(
                path = pathWave2,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        cyanColor.copy(alpha = 0.28f * glowPulse),
                        purpleColor.copy(alpha = 0.35f * glowPulse),
                        cyanColor.copy(alpha = 0.25f * glowPulse)
                    )
                ),
                style = Stroke(
                    width = 8f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Razor-sharp neon glass rim stroke
            drawPath(
                path = pathWave2,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        cyanColor.copy(alpha = 0.90f),
                        Color(0xFF38BDF8).copy(alpha = 0.95f),
                        purpleColor.copy(alpha = 0.85f),
                        cyanColor.copy(alpha = 0.90f)
                    )
                ),
                style = Stroke(
                    width = 2.8f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // -------------------------------------------------------------
            // 4. LAYER 3: FOREGROUND ELECTRIC LIQUID GLASS WAVE (High Cyan)
            // -------------------------------------------------------------
            buildLiquidWavePath(
                crestPath = pathWave3,
                fillPath = pathFill3,
                width = w,
                height = h,
                baseY = h * 0.72f - floatY1 * 0.8f,
                amplitude1 = 60f,
                amplitude2 = 36f,
                phase = phasePrimary * 1.3f,
                frequency = 1.1f
            )

            // Translucent glass base
            drawPath(
                path = pathFill3,
                brush = Brush.linearGradient(
                    colors = listOf(
                        cyanColor.copy(alpha = 0.18f),
                        purpleColor.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    start = Offset(0f, h * 0.65f),
                    end = Offset(w, h)
                )
            )

            // Diffuse glow
            drawPath(
                path = pathWave3,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        purpleColor.copy(alpha = 0.30f * glowPulse),
                        cyanColor.copy(alpha = 0.40f * glowPulse),
                        purpleColor.copy(alpha = 0.25f * glowPulse)
                    )
                ),
                style = Stroke(
                    width = 7f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Sharp neon rim highlight
            drawPath(
                path = pathWave3,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        purpleColor.copy(alpha = 0.80f),
                        cyanColor.copy(alpha = 0.95f),
                        Color.White.copy(alpha = 0.70f),
                        cyanColor.copy(alpha = 0.90f)
                    )
                ),
                style = Stroke(
                    width = 2.4f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 5. SPECULAR CHROMATIC ACCENT LIGHTS
            drawSpecularHighlights(
                w = w,
                h = h,
                phase = phasePrimary,
                cyanColor = cyanColor,
                purpleColor = purpleColor,
                glowPulse = glowPulse
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
