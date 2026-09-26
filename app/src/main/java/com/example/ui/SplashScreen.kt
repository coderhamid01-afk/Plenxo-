package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.animation.PlenxoMotion
import kotlinx.coroutines.launch

@Composable
fun SplashScreen() {
    val coroutineScope = rememberCoroutineScope()
    val logoScale = remember { Animatable(0.92f) }
    val logoAlpha = remember { Animatable(0f) }
    val contentOffsetY = remember { Animatable(10f) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            logoScale.animateTo(1f, tween(800, easing = PlenxoMotion.DecelerateEasing))
        }
        coroutineScope.launch {
            logoAlpha.animateTo(1f, tween(600))
        }
        coroutineScope.launch {
            contentOffsetY.animateTo(0f, tween(700, easing = PlenxoMotion.DecelerateEasing))
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_anim")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = PlenxoMotion.StandardEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loading_progress"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Subtle ambient radial blue glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(width * 0.5f, height * 0.4f),
                    radius = width * 0.8f
                ),
                center = Offset(width * 0.5f, height * 0.4f),
                radius = width * 0.8f
            )
        }

        // Center Branding
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    alpha = logoAlpha.value
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                    translationY = contentOffsetY.value
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Plenxo Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = textColor)) {
                        append("Plen")
                    }
                    withStyle(style = SpanStyle(color = primaryColor)) {
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
                color = mutedColor,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.5.sp
            )
        }

        // Bottom Loading Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(210.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progress)
                        .clip(RoundedCornerShape(2.dp))
                        .background(primaryColor)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "LOADING...",
                fontSize = 10.sp,
                color = mutedColor,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.5.sp
            )
        }
    }
}
