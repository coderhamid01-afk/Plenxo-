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
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Real-time activeProfileRing observer function for Jetpack Compose.
 * Listens to Firestore users/{userId} document changes in real-time.
 */
@Composable
fun rememberActiveProfileRing(
    userId: String?,
    initialRingId: String? = null
): State<String> {
    val ringState = remember(userId, initialRingId) {
        mutableStateOf(
            initialRingId?.takeIf { it.isNotBlank() && it != "none" && it != "NONE" } ?: "none"
        )
    }

    DisposableEffect(userId) {
        if (userId.isNullOrBlank()) {
            onDispose { }
        } else {
            val listenerRegistration = FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && snapshot.exists()) {
                        val activeRing = snapshot.getString("activeProfileRing")
                            ?: snapshot.getString("profileRingId")
                            ?: snapshot.getString("profileRing")
                            ?: snapshot.getString("selectedRingId")
                            ?: "none"
                        ringState.value = activeRing
                    }
                }
            onDispose {
                listenerRegistration.remove()
            }
        }
    }

    return ringState
}

@Composable
fun UserProfileCard(
    userId: String,
    displayName: String,
    profilePicUrl: String?,
    plenxoId: String = "",
    bio: String = "",
    initialRingId: String? = null,
    isOnline: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val activeRingId by rememberActiveProfileRing(userId = userId, initialRingId = initialRingId)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
            .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileRingBox(
                ringId = activeRingId,
                ringPadding = 3.dp,
                borderWidth = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer),
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
                            contentDescription = "$displayName's Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = displayName.take(1).uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (isOnline) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                                .border(2.dp, androidx.compose.material3.MaterialTheme.colorScheme.surface, CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName.ifBlank { "Plenxo User" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                )
                if (plenxoId.isNotBlank()) {
                    Text(
                        text = plenxoId,
                        fontSize = 13.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                }
                if (bio.isNotBlank()) {
                    Text(
                        text = bio,
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
