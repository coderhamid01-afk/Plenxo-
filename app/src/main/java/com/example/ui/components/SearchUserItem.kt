package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

@Composable
fun SearchUserItem(
    userId: String,
    displayName: String,
    profilePicUrl: String?,
    plenxoId: String = "",
    initialRingId: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val activeRingId by rememberActiveProfileRing(userId = userId, initialRingId = initialRingId)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileRingBox(
            ringId = activeRingId,
            ringPadding = 2.dp,
            borderWidth = 3.dp
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                if (!profilePicUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(profilePicUrl)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = "$displayName Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = displayName.take(1).uppercase(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName.ifBlank { "Plenxo User" },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            if (plenxoId.isNotBlank()) {
                Text(
                    text = plenxoId,
                    fontSize = 12.sp,
                    color = Color(0xFF3B82F6)
                )
            }
        }
    }
}
