package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun ProfileImageWithRing(
    imageUrl: String?,
    profileRingId: String?,
    modifier: Modifier = Modifier,
    ringBorderWidth: Int = 5, // in dp
    fallbackInitial: String? = null,
    userId: String? = null,
    onClick: (() -> Unit)? = null
) {
    val liveRingId by rememberActiveProfileRing(userId = userId, initialRingId = profileRingId)
    val ringId = liveRingId.ifEmpty { profileRingId ?: "none" }
    val hasRing = ringId.isNotEmpty() && ringId != "none" && ringId != "NONE"

    val ringBrush = when (ringId.lowercase()) {
        "ring_neon", "ring_blue" -> Brush.sweepGradient(listOf(Color(0xFF2563EB), Color(0xFF3B82F6), Color(0xFF60A5FA), Color(0xFF2563EB)))
        "ring_gold" -> Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFB8860B), Color(0xFFFFD700)))
        "ring_ruby" -> Brush.linearGradient(listOf(Color(0xFFE11D48), Color(0xFF9F1239), Color(0xFFE11D48)))
        "ring_emerald" -> Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF047857), Color(0xFF059669)))
        "ring_dark" -> Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFF1E293B)))
        "ring_platinum" -> Brush.linearGradient(listOf(Color(0xFFCBD5E1), Color(0xFF94A3B8), Color(0xFFCBD5E1)))
        "none", "" -> null
        else -> Brush.sweepGradient(listOf(Color(0xFF2563EB), Color(0xFF3B82F6), Color(0xFF2563EB)))
    }

    val clickableModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Box(
        modifier = modifier.then(clickableModifier),
        contentAlignment = Alignment.Center
    ) {
        // Pad the image inside to make sure the ring circles it beautifully without cutting face
        val imagePadding = if (hasRing) (ringBorderWidth + 1).dp else 0.dp
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(imagePadding)
                .clip(CircleShape)
                .background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrEmpty() && (imageUrl.startsWith("http") || imageUrl.startsWith("content://") || imageUrl.startsWith("file://"))) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build(),
                    contentDescription = "Profile Picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (!fallbackInitial.isNullOrBlank()) {
                Text(
                    text = fallbackInitial.trim().take(1).uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Placeholder",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.fillMaxSize(0.6f)
                )
            }
        }

        if (hasRing && ringBrush != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = ringBorderWidth.dp,
                        brush = ringBrush,
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Universal UserAvatar composable supporting profileRing and online indicator.
 */
@Composable
fun UserAvatar(
    profilePicUrl: String?,
    profileRing: String? = null,
    profileRingId: String? = null,
    displayName: String = "",
    plenxoId: String = "",
    userId: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val initialRing = profileRing?.takeIf { it.isNotBlank() && it != "none" }
        ?: profileRingId?.takeIf { it.isNotBlank() && it != "none" }
        ?: "none"
    val liveRingId by rememberActiveProfileRing(userId = userId, initialRingId = initialRing)
    val ringId = liveRingId.ifEmpty { initialRing }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        ProfileRingBox(
            ringId = ringId,
            ringPadding = 2.dp,
            borderWidth = 3.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.LightGray.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (!profilePicUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(profilePicUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "$displayName Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val initials = if (displayName.isNotBlank()) {
                        displayName.trim().take(2).uppercase()
                    } else if (plenxoId.isNotBlank()) {
                        plenxoId.trim().removePrefix("@").take(2).uppercase()
                    } else "P"

                    Text(
                        text = initials,
                        fontSize = (size.value * 0.38f).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(size * 0.26f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
                    .border(1.5.dp, Color.White, CircleShape)
            )
        }
    }
}

