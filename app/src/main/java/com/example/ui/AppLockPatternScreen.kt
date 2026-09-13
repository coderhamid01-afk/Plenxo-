package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AnimatedAuthBackground
import com.example.ui.components.PatternLockView
import kotlinx.coroutines.launch

/**
 * 11. Pattern Lock Upgrade (AppLockPatternScreen):
 * - 3x3 pattern grid with glowing glassmorphic nodes
 * - Electric cyan/neon purple drag path canvas
 * - Frosted glass lock badge
 * - Animated shake warning on "Invalid Pattern"
 */
@Composable
fun AppLockPatternScreen(
    modifier: Modifier = Modifier,
    title: String = "Draw Pattern to Unlock",
    subtitle: String = "Connect the dots to authenticate",
    onPatternEntered: (String, (Boolean) -> Unit) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val shakeOffset = remember { Animatable(0f) }

    val electricGradient = Brush.horizontalGradient(
        listOf(Color(0xFF00F2FE), Color(0xFF8B5CF6))
    )

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AnimatedAuthBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        // Frosted Glass Lock Badge with glowing ambient shadow
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(16.dp, CircleShape, ambientColor = Color(0xFF00F2FE).copy(alpha = 0.45f))
                .clip(CircleShape)
                .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                .border(
                    1.5.dp,
                    if (errorMessage != null) Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
                    else electricGradient,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "App Lock",
                tint = if (errorMessage != null) Color(0xFFEF4444) else Color(0xFF00F2FE),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF8FAFC),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Animated Shake Warning on "Invalid Pattern"
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .offset(x = shakeOffset.value.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF7F1D1D).copy(alpha = 0.8f))
                    .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFCA5A5),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = errorMessage ?: "Invalid Pattern",
                    color = Color(0xFFFEE2E2),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3x3 Pattern Grid Container with electric cyan / neon purple drag path
        Box(
            modifier = Modifier
                .offset(x = shakeOffset.value.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                .border(
                    1.dp,
                    if (errorMessage != null) Color(0xFFEF4444).copy(alpha = 0.6f)
                    else Color(0xFF334155).copy(alpha = 0.5f),
                    RoundedCornerShape(24.dp)
                )
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            PatternLockView(
                modifier = Modifier.fillMaxWidth(),
                onPatternComplete = { pattern ->
                    errorMessage = null
                    onPatternEntered(pattern) { isCorrect ->
                        if (!isCorrect) {
                            errorMessage = "Invalid Pattern. Please try again."
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            coroutineScope.launch {
                                shakeOffset.snapTo(0f)
                                shakeOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = keyframes {
                                        durationMillis = 400
                                        -24f at 50
                                        24f at 100
                                        -18f at 150
                                        18f at 200
                                        -10f at 250
                                        10f at 300
                                        -4f at 350
                                        0f at 400
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}
}
