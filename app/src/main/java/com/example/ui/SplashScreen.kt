package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_anim")

    // Animate progress for the loading bar from 0.0 to 1.0 continuously
    val progress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loading_progress"
    )

    // Animate gradient offset for active loading line
    val offsetAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)) // Pure OLED Black
    ) {
        // Ambient Radial Background Glows
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Top-left dark blue radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF004499).copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(width * 0.1f, height * 0.1f),
                    radius = width * 0.8f
                ),
                center = Offset(width * 0.1f, height * 0.1f),
                radius = width * 0.8f
            )

            // Bottom-right dark blue radial glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF0033AA).copy(alpha = 0.4f), Color.Transparent),
                    center = Offset(width * 0.9f, height * 0.9f),
                    radius = width * 0.9f
                ),
                center = Offset(width * 0.9f, height * 0.9f),
                radius = width * 0.9f
            )

            // Top-Left Glowing Curved Arc Line (matching reference screenshot)
            val topLeftArc = Path().apply {
                moveTo(width * 0.37f, 0f)
                cubicTo(
                    width * 0.37f, height * 0.15f,
                    width * 0.1f, height * 0.28f,
                    -50f, height * 0.32f
                )
            }
            drawPath(
                path = topLeftArc,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00E5FF), Color(0xFF0055FF), Color.Transparent),
                    start = Offset(width * 0.37f, 0f),
                    end = Offset(0f, height * 0.32f)
                ),
                style = Stroke(width = 3.dp.toPx())
            )

            // Outer soft glow for top-left arc
            drawPath(
                path = topLeftArc,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.3f), Color.Transparent),
                    start = Offset(width * 0.37f, 0f),
                    end = Offset(0f, height * 0.32f)
                ),
                style = Stroke(width = 12.dp.toPx())
            )

            // Bottom-Right Neon Wave Curves (matching reference screenshot)
            val bottomRightWave1 = Path().apply {
                moveTo(width * 0.38f, height)
                cubicTo(
                    width * 0.65f, height * 0.88f,
                    width * 0.82f, height * 0.72f,
                    width + 50f, height * 0.64f
                )
            }
            drawPath(
                path = bottomRightWave1,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0066FF).copy(alpha = 0.8f), Color(0xFF0033BB).copy(alpha = 0.4f)),
                    start = Offset(width * 0.38f, height),
                    end = Offset(width, height * 0.64f)
                ),
                style = Stroke(width = 2.5.dp.toPx())
            )

            val bottomRightWave2 = Path().apply {
                moveTo(width * 0.5f, height)
                cubicTo(
                    width * 0.75f, height * 0.85f,
                    width * 0.9f, height * 0.78f,
                    width + 50f, height * 0.72f
                )
            }
            drawPath(
                path = bottomRightWave2,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.6f), Color(0xFF0044FF).copy(alpha = 0.2f)),
                    start = Offset(width * 0.5f, height),
                    end = Offset(width, height * 0.72f)
                ),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Center Branding (Logo, Title, Tagline)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Plenxo Logo",
                modifier = Modifier.size(110.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color.White)) {
                        append("Plen")
                    }
                    withStyle(style = SpanStyle(color = Color(0xFF00E5FF))) { // Electric Cyan accent
                        append("xo")
                    }
                },
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "CONNECT   /   CHAT   /   BEYOND",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8), // Muted slate gray
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.5.sp
            )
        }

        // Bottom Loading Bar Section (Matching UI Specification)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Rounded 4.dp slim progress bar container
            Box(
                modifier = Modifier
                    .width(210.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF1E293B)) // Dark background track
            ) {
                // Active cyan/electric blue gradient loading fill
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progress)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF),
                                    Color(0xFF0066FF),
                                    Color(0xFF00E5FF)
                                ),
                                startX = offsetAnim - 300f,
                                endX = offsetAnim
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "LOADING...",
                fontSize = 10.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.5.sp
            )
        }
    }
}
