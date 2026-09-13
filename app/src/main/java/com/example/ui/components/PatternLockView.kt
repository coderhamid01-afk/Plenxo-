package com.example.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    onPatternComplete: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var selectedDots by remember { mutableStateOf(listOf<Int>()) }
    var currentTouchPos by remember { mutableStateOf<Offset?>(null) }
    var dotCenters by remember { mutableStateOf(mapOf<Int, Offset>()) }

    Box(modifier = modifier.aspectRatio(1f)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            currentTouchPos = Offset(event.x, event.y)
                            
                            // Check if touching any dot
                            dotCenters.forEach { (index, center) ->
                                val distance = Math.hypot(
                                    (center.x - event.x).toDouble(),
                                    (center.y - event.y).toDouble()
                                )
                                if (distance < 90f && !selectedDots.contains(index)) { // 90f hit radius
                                    selectedDots = selectedDots + index
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            currentTouchPos = null
                            if (selectedDots.isNotEmpty()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPatternComplete(selectedDots.joinToString(","))
                                selectedDots = emptyList()
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val spacingX = width / 4
            val spacingY = height / 4

            // Update dot centers if needed
            if (dotCenters.isEmpty()) {
                val newCenters = mutableMapOf<Int, Offset>()
                for (row in 0..2) {
                    for (col in 0..2) {
                        newCenters[row * 3 + col] = Offset(
                            spacingX * (col + 1),
                            spacingY * (row + 1)
                        )
                    }
                }
                dotCenters = newCenters
            }

            // Draw lines between selected dots
            if (selectedDots.size > 1) {
                for (i in 0 until selectedDots.size - 1) {
                    val p1 = dotCenters[selectedDots[i]]
                    val p2 = dotCenters[selectedDots[i + 1]]
                    if (p1 != null && p2 != null) {
                        val lineBrush = Brush.linearGradient(
                            colors = listOf(Color(0xFF00F2FE), Color(0xFF8B5CF6)),
                            start = p1,
                            end = p2
                        )
                        // Ambient outer glow for line
                        drawLine(
                            brush = lineBrush,
                            start = p1,
                            end = p2,
                            strokeWidth = 24f,
                            cap = StrokeCap.Round,
                            alpha = 0.28f
                        )
                        // Sharp core line
                        drawLine(
                            brush = lineBrush,
                            start = p1,
                            end = p2,
                            strokeWidth = 10f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Draw line from last dot to current touch position
            val touchPos = currentTouchPos
            if (selectedDots.isNotEmpty() && touchPos != null) {
                val lastDot = dotCenters[selectedDots.last()]
                if (lastDot != null) {
                    val dragBrush = Brush.linearGradient(
                        colors = listOf(Color(0xFF00F2FE), Color(0xFF8B5CF6).copy(alpha = 0.6f)),
                        start = lastDot,
                        end = touchPos
                    )
                    drawLine(
                        brush = dragBrush,
                        start = lastDot,
                        end = touchPos,
                        strokeWidth = 10f,
                        cap = StrokeCap.Round,
                        alpha = 0.7f
                    )
                }
            }

            // Draw glassmorphic glowing nodes
            dotCenters.forEach { (index, center) ->
                val isSelected = selectedDots.contains(index)
                if (isSelected) {
                    // Outer ambient neon glow
                    drawCircle(
                        color = Color(0xFF00F2FE).copy(alpha = 0.22f),
                        radius = 42f,
                        center = center
                    )
                    // Outer neon border ring
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF00F2FE), Color(0xFF8B5CF6)),
                            center = center,
                            radius = 28f
                        ),
                        radius = 28f,
                        center = center,
                        style = Stroke(width = 4f)
                    )
                    // Inner glowing white-cyan core
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, Color(0xFF00F2FE)),
                            center = center,
                            radius = 14f
                        ),
                        radius = 14f,
                        center = center
                    )
                } else {
                    // Subtle unselected outer ring
                    drawCircle(
                        color = Color(0xFF334155).copy(alpha = 0.5f),
                        radius = 28f,
                        center = center,
                        style = Stroke(width = 2f)
                    )
                    // Inner node dot
                    drawCircle(
                        color = Color(0xFF64748B),
                        radius = 12f,
                        center = center
                    )
                }
            }
        }
    }
}
