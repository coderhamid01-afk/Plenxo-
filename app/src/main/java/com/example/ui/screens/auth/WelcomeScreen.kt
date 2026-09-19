package com.example.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AnimatedAuthBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onNext: () -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary
) {
    val bg = MaterialTheme.colorScheme.background
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        containerColor = bg
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Dynamic Animated Liquid Glass Wave Background
            AnimatedAuthBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Intro Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(
                        text = "Plenxo",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Fast, secure, and private messaging built for everyone.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // Central Feature Cards
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                ) {
                    ModernFeatureCard(
                        icon = Icons.Default.Shield,
                        title = "100% Private Chats",
                        description = "Your messages and media are fully protected.",
                        accentColor = primaryColor
                    )

                    ModernFeatureCard(
                        icon = Icons.Default.Security,
                        title = "Your Unique Plenxo ID",
                        description = "Your personal PX-ID to connect safely with friends.",
                        accentColor = primaryColor
                    )

                    ModernFeatureCard(
                        icon = Icons.Default.Bolt,
                        title = "Lightning Fast & Offline",
                        description = "Messages load instantly even on low internet.",
                        accentColor = primaryColor
                    )
                }

                // Bottom Action Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("welcome_next_button")
                            .shadow(2.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Get Started",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = "By continuing, you agree to our Terms of Service and Privacy Policy",
                        fontSize = 11.sp,
                        color = textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ModernFeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: Color
) {
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = textMuted
                )
            }
        }
    }
}
