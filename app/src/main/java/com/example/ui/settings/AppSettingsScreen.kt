package com.example.ui.settings

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.PlenxoTopAppBar
import com.example.ui.components.bounceClick
import com.example.ui.theme.GlassTheme
import com.example.viewmodel.PlenxoScreen
import com.example.viewmodel.PlenxoViewModel

/**
 * AppSettingsScreen provides a comprehensive settings experience supporting:
 * - Dynamic Light / Dark / System Theme toggle
 * - Frosted light-glass cards in Light Theme
 * - Deep obsidian containers in Dark Theme
 * - Automatic status bar / navigation bar icon contrast adaptation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    viewModel: PlenxoViewModel,
    primaryColor: Color
) {
    val context = LocalContext.current
    val appThemeMode by viewModel.appThemeMode.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }

    val systemInDark = isSystemInDarkTheme()
    val isDark = when (appThemeMode.uppercase()) {
        "LIGHT" -> false
        "DARK" -> true
        else -> systemInDark
    }

    // Dynamic High-Contrast & Frosted Light-Glass Colors
    val screenBg = GlassTheme.getBackgroundColor(isDark)
    val cardBg = GlassTheme.getGlassCardBackground(isDark)
    val cardBorder = GlassTheme.getGlassBorderColor(isDark)
    val primaryText = GlassTheme.getPrimaryTextColor(isDark)
    val secondaryText = GlassTheme.getSecondaryTextColor(isDark)
    val dividerColor = GlassTheme.getDividerColor(isDark)
    val accentColor = MaterialTheme.colorScheme.primary

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(
                    text = "Theme / Appearance",
                    color = primaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            containerColor = cardBg,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    listOf(
                        "LIGHT" to "Light Theme",
                        "DARK" to "Dark Theme",
                        "SYSTEM_DEFAULT" to "System Default"
                    ).forEach { (value, label) ->
                        val isSelected = appThemeMode.equals(value, ignoreCase = true) ||
                            (value == "SYSTEM_DEFAULT" && (appThemeMode.isBlank() || appThemeMode.equals("system", ignoreCase = true)))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.updateAppThemeMode(value)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.updateAppThemeMode(value)
                                    showThemeDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = accentColor,
                                    unselectedColor = secondaryText
                                ),
                                modifier = Modifier.testTag("theme_radio_${value.lowercase()}")
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                color = if (isSelected) primaryText else secondaryText,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close", color = accentColor, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            PlenxoTopAppBar(
                title = stringResource(R.string.str_settings),
                onBackClick = {
                    if (!viewModel.navigateBack()) {
                        viewModel.navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
                    }
                }
            )
        },
        containerColor = screenBg
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                // Theme / Appearance Section
                Text(
                    text = "APPEARANCE & THEME",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = secondaryText,
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(20.dp)),
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick { showThemeDialog = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Brightness4,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "App Theme Mode",
                                        fontSize = 15.sp,
                                        color = primaryText,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val currentModeLabel = when {
                                        appThemeMode.equals("LIGHT", ignoreCase = true) -> "Light Theme"
                                        appThemeMode.equals("DARK", ignoreCase = true) -> "Dark Theme"
                                        else -> "System Default (${if (systemInDark) "Dark" else "Light"})"
                                    }
                                    Text(
                                        text = currentModeLabel,
                                        fontSize = 13.sp,
                                        color = secondaryText
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = secondaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            item {
                // Privacy & Permissions Section
                Text(
                    text = "SECURITY & PERMISSIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = secondaryText,
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(20.dp)),
                    color = cardBg,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    var blockScreenshots by remember {
                        mutableStateOf(com.example.util.SessionManager.isScreenshotsBlocked(context))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Block Screenshots",
                                    fontSize = 15.sp,
                                    color = primaryText,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Enhance chat privacy across screens",
                                    fontSize = 13.sp,
                                    color = secondaryText
                                )
                            }
                        }
                        Switch(
                            checked = blockScreenshots,
                            onCheckedChange = {
                                blockScreenshots = it
                                com.example.util.SessionManager.saveScreenshotsBlocked(context, it)
                                (context as? Activity)?.recreate()
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
