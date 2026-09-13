package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.R
import com.example.ui.components.GlowingAvatar
import com.example.viewmodel.PlenxoViewModel

/**
 * HomeScreen with production-ready Coil avatar rendering and neon glowing initial fallback.
 * Guarantees zero broken icons or black circles when avatarUrl is null, empty, or broken.
 */
@Composable
fun HomeScreen(
    viewModel: PlenxoViewModel,
    primaryColor: Color = MaterialTheme.colorScheme.primary
) {
    val email by viewModel.email.collectAsState()
    val displayName by viewModel.displayName.collectAsState()
    val bDay by viewModel.birthDay.collectAsState()
    val bMonth by viewModel.birthMonth.collectAsState()
    val bYear by viewModel.birthYear.collectAsState()
    val userCode by viewModel.userCode.collectAsState()
    val avatarType by viewModel.avatarType.collectAsState()
    val selectedIndex by viewModel.selectedAvatarIndex.collectAsState()
    val galleryImageUriStr by viewModel.galleryImageUriString.collectAsState()
    val selectedEmoji by viewModel.selectedEmoji.collectAsState()
    val selectedThemeName by viewModel.selectedTheme.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()

    val effectiveAvatarUrl = currentUserProfile?.profilePicUrl?.takeIf { it.isNotBlank() }
        ?: galleryImageUriStr?.takeIf { it.isNotBlank() }

    val initialLetter = remember(displayName) {
        displayName.trim().takeIf { it.isNotEmpty() }?.take(1)?.uppercase() ?: "P"
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

    val avatarFallbackGradient = remember {
        Brush.linearGradient(
            listOf(
                Color(0xFF6366F1),
                Color(0xFF8B5CF6),
                Color(0xFF06B6D4)
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Success Logo Banner
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(primaryColor.copy(alpha = 0.12f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success tick logo",
                tint = primaryColor,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(id = R.string.str_welcome_to_plenxo_1),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = stringResource(id = R.string.str_your_account_registration_has_been),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Consolidated Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Circular Avatar Frame with Neon Glowing Border and Initial Letter Fallback
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.5.dp, neonGlowBrush, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        avatarType == "placeholder" -> {
                            val avatarList = viewModel.maleAvatars + viewModel.femaleAvatars
                            if (selectedIndex in avatarList.indices) {
                                Text(
                                    text = avatarList[selectedIndex].second,
                                    fontSize = 44.sp
                                )
                            } else {
                                HomeAvatarFallbackBox(initialLetter, avatarFallbackGradient)
                            }
                        }
                        avatarType == "emoji" && selectedEmoji.isNotBlank() -> {
                            Text(
                                text = selectedEmoji,
                                fontSize = 50.sp
                            )
                        }
                        else -> {
                            // Avatar URL with Coil AsyncImage and Neon Glowing Fallback
                            if (!effectiveAvatarUrl.isNullOrEmpty() &&
                                (effectiveAvatarUrl.startsWith("http://") ||
                                 effectiveAvatarUrl.startsWith("https://") ||
                                 effectiveAvatarUrl.startsWith("content://") ||
                                 effectiveAvatarUrl.startsWith("file://"))
                            ) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(effectiveAvatarUrl)
                                        .crossfade(true)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .build(),
                                    contentDescription = "User profile avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    loading = {
                                        HomeAvatarFallbackBox(initialLetter, avatarFallbackGradient)
                                    },
                                    error = {
                                        HomeAvatarFallbackBox(initialLetter, avatarFallbackGradient)
                                    }
                                )
                            } else {
                                HomeAvatarFallbackBox(initialLetter, avatarFallbackGradient)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // User display metadata
                Text(
                    text = displayName.ifBlank { "Plenxo User" },
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = "User Code tag",
                        tint = primaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Plenxo Code: ${userCode.ifBlank { "PX-000000" }}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // Profile Details Key-Value structure
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileDetailRow(icon = Icons.Default.Email, label = "Email", value = email.ifBlank { "Not provided" }, accentColor = primaryColor)
                    ProfileDetailRow(icon = Icons.Default.Cake, label = "Birthday (DOB)", value = "$bDay $bMonth, $bYear", accentColor = primaryColor)
                    ProfileDetailRow(icon = Icons.Default.Palette, label = "App Theme Accent", value = "$selectedThemeName Choice", accentColor = primaryColor)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Back to signup exit button
        OutlinedButton(
            onClick = { viewModel.navigateBackToSignup() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("logout_button"),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor),
            border = BorderStroke(1.5.dp, primaryColor)
        ) {
            Text(
                text = stringResource(id = R.string.str_back_to_signup),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HomeAvatarFallbackBox(initial: String, gradient: Brush) {
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
            fontSize = 42.sp
        )
    }
}

@Composable
private fun ProfileDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
