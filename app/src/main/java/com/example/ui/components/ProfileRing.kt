package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ProfileRing(
    tier: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val ringColor = when (tier) {
        "Bronze" -> Color(0xFFCD7F32)
        "Silver" -> Color(0xFFC0C0C0)
        "Gold" -> Color(0xFFFFD700)
        "Diamond" -> Color(0xFFB9F2FF)
        "Platinum" -> Color(0xFFE5E4E2)
        "Red Ruby" -> Color(0xFFFF0000)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .padding(4.dp)
            .border(
                width = if (tier != "None" && tier != "") 3.dp else 0.dp,
                color = ringColor,
                shape = CircleShape
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
        
        if (tier != "None" && tier != "") {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.BottomEnd)
                    .background(ringColor, CircleShape)
                    .border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun ProfileRingBox(
    ringId: String?,
    modifier: Modifier = Modifier,
    ringPadding: Dp = 4.dp,
    borderWidth: Dp = 5.dp,
    content: @Composable () -> Unit
) {
    val normalizedId = ringId?.lowercase()?.trim() ?: "none"
    
    val brush = when (normalizedId) {
        "ring_neon", "ring_blue" -> Brush.sweepGradient(colors = listOf(Color(0xFF2563EB), Color(0xFF3B82F6), Color(0xFF60A5FA), Color(0xFF2563EB)))
        "ring_gold" -> Brush.linearGradient(colors = listOf(Color(0xFFFFD700), Color(0xFFB8860B), Color(0xFFFFD700)))
        "ring_ruby" -> Brush.linearGradient(colors = listOf(Color(0xFFE11D48), Color(0xFF9F1239), Color(0xFFE11D48)))
        "ring_emerald" -> Brush.linearGradient(colors = listOf(Color(0xFF059669), Color(0xFF047857), Color(0xFF059669)))
        "ring_dark" -> Brush.linearGradient(colors = listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFF1E293B)))
        "none", "" -> null
        else -> Brush.sweepGradient(colors = listOf(Color(0xFF2563EB), Color(0xFF3B82F6), Color(0xFF2563EB)))
    }

    if (brush != null) {
        Box(
            modifier = modifier
                .border(width = borderWidth, brush = brush, shape = CircleShape)
                .padding(ringPadding),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
