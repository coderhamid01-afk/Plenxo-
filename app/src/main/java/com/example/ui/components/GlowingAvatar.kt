package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

/**
 * Production-ready Coil Avatar component with Neon Glowing Border and Initial Letter Fallback.
 * Ensures zero broken icons or black circles when avatarUrl is null, empty, or fails to load.
 */
@Composable
fun GlowingAvatar(
    avatarUrl: String?,
    displayName: String?,
    size: Dp = 48.dp,
    borderWidth: Dp = 2.dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val initial = remember(displayName) {
        displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(1)?.uppercase() ?: "P"
    }

    val neonGlowBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFF00E5FF),
                Color(0xFF8A2BE2),
                Color(0xFFFF007F),
                Color(0xFF00E5FF)
            )
        )
    }

    val avatarGradient = remember {
        Brush.linearGradient(
            listOf(
                Color(0xFF6366F1), // Indigo
                Color(0xFF8B5CF6), // Purple
                Color(0xFF06B6D4)  // Neon Cyan
            )
        )
    }

    val boxModifier = modifier
        .size(size)
        .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color(0xFF00E5FF))
        .clip(CircleShape)
        .border(borderWidth, neonGlowBrush, CircleShape)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center
    ) {
        val validUrl = avatarUrl?.trim()?.takeIf {
            it.isNotEmpty() && (it.startsWith("http://") || it.startsWith("https://") || it.startsWith("content://") || it.startsWith("file://"))
        }

        if (validUrl != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(validUrl)
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = displayName ?: "User avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    GlowingAvatarInitialFallback(
                        initial = initial,
                        gradient = avatarGradient,
                        fontSize = (size.value * 0.42f).sp
                    )
                },
                error = {
                    GlowingAvatarInitialFallback(
                        initial = initial,
                        gradient = avatarGradient,
                        fontSize = (size.value * 0.42f).sp
                    )
                }
            )
        } else {
            GlowingAvatarInitialFallback(
                initial = initial,
                gradient = avatarGradient,
                fontSize = (size.value * 0.42f).sp
            )
        }
    }
}

@Composable
fun GlowingAvatarInitialFallback(
    initial: String,
    gradient: Brush,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize
        )
    }
}
