package com.example.ui.theme

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * App Theme Mode Constants
 */
object AppThemeMode {
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"
    const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
}

/**
 * Modern High-Contrast Color Schemes
 */
val PlenxoDarkColorScheme = darkColorScheme(
    primary = PlenxoBlueDarkPrimary,
    onPrimary = Color.White,
    primaryContainer = PlenxoBlueDarkContainer,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = PlenxoBlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFF60A5FA),
    onTertiary = Color(0xFF0F172A),
    background = PlenxoNavyBackground,
    onBackground = PlenxoTextPrimaryDark,
    surface = PlenxoNavySurface,
    onSurface = PlenxoTextPrimaryDark,
    surfaceVariant = PlenxoNavySurfaceVariant,
    onSurfaceVariant = PlenxoTextSecondaryDark,
    outline = PlenxoBorderDark,
    error = PlenxoError,
    onError = Color.White
)

val PlenxoLightColorScheme = lightColorScheme(
    primary = PlenxoBluePrimary,
    onPrimary = Color.White,
    primaryContainer = PlenxoBlueLightContainer,
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = PlenxoBlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F9FF),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = Color(0xFF3B82F6),
    onTertiary = Color.White,
    background = PlenxoLightBackground,
    onBackground = PlenxoTextPrimaryLight,
    surface = PlenxoLightSurface,
    onSurface = PlenxoTextPrimaryLight,
    surfaceVariant = PlenxoLightSurfaceVariant,
    onSurfaceVariant = PlenxoTextSecondaryLight,
    outline = PlenxoBorderLight,
    error = PlenxoError,
    onError = Color.White
)

/**
 * Styling helpers for UI card & surface elements
 */
object GlassTheme {
    fun getBackgroundColor(isDark: Boolean): Color {
        return if (isDark) PlenxoNavyBackground else PlenxoLightBackground
    }

    fun getSurfaceColor(isDark: Boolean): Color {
        return if (isDark) PlenxoNavySurface else PlenxoLightSurface
    }

    fun getGlassCardBackground(isDark: Boolean): Color {
        return if (isDark) PlenxoNavySurfaceVariant else PlenxoLightSurface
    }

    fun getGlassBorderColor(isDark: Boolean): Color {
        return if (isDark) PlenxoBorderDark else PlenxoBorderLight
    }

    fun getPrimaryTextColor(isDark: Boolean): Color {
        return if (isDark) PlenxoTextPrimaryDark else PlenxoTextPrimaryLight
    }

    fun getSecondaryTextColor(isDark: Boolean): Color {
        return if (isDark) PlenxoTextSecondaryDark else PlenxoTextSecondaryLight
    }

    fun getMutedTextColor(isDark: Boolean): Color {
        return if (isDark) PlenxoTextSecondaryDark else PlenxoTextSecondaryLight
    }

    fun getDividerColor(isDark: Boolean): Color {
        return if (isDark) PlenxoBorderDark else PlenxoBorderLight
    }

    fun getBrightGradient(isDark: Boolean): Brush {
        return if (isDark) {
            Brush.horizontalGradient(listOf(PlenxoBlueDarkPrimary, PlenxoBlueSecondary))
        } else {
            Brush.horizontalGradient(listOf(PlenxoBluePrimary, PlenxoBlueSecondary))
        }
    }
}

/**
 * Modifier extension for frosted glass card styling
 */
fun Modifier.glassCard(
    isDark: Boolean,
    cornerRadius: Dp = 16.dp,
    elevation: Dp = if (isDark) 0.dp else 4.dp
): Modifier = this
    .shadow(elevation, RoundedCornerShape(cornerRadius), clip = false)
    .clip(RoundedCornerShape(cornerRadius))
    .background(GlassTheme.getGlassCardBackground(isDark))
    .border(
        BorderStroke(1.dp, GlassTheme.getGlassBorderColor(isDark)),
        RoundedCornerShape(cornerRadius)
    )

/**
 * Production-ready AppTheme wrapper with dynamic Light/Dark/System toggle,
 * automatic status bar & navigation bar dark/light icon switching,
 * and top-to-bottom animated visual sweep transition.
 */
@Composable
fun AppTheme(
    themeMode: String = AppThemeMode.SYSTEM_DEFAULT,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode.uppercase()) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        else -> systemInDark
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    // When light theme is active, status bar icons MUST be dark (!isDark = true)
                    // When dark theme is active, status bar icons MUST be white/light (!isDark = false)
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }
    }

    var previousIsDark by remember { mutableStateOf<Boolean?>(null) }
    var snapshotBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val transitionProgress = remember { Animatable(1f) }
    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(isDark) {
        if (previousIsDark != null && previousIsDark != isDark) {
            val bitmap = captureAppViewToBitmap(view)
            if (bitmap != null) {
                snapshotBitmap = bitmap
                isAnimating = true
                transitionProgress.snapTo(0f)
            }
            previousIsDark = isDark

            if (isAnimating) {
                transitionProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = FastOutSlowInEasing
                    )
                )
                isAnimating = false
                snapshotBitmap = null
            }
        } else {
            previousIsDark = isDark
        }
    }

    val colorScheme = if (isDark) PlenxoDarkColorScheme else PlenxoLightColorScheme

    val currentLang by com.example.util.LocaleHelper.currentLanguage.collectAsState()
    val baseContext = LocalContext.current
    val localizedContext = remember(currentLang, baseContext) {
        com.example.util.LocaleHelper.getLocalizedContext(baseContext, currentLang)
    }
    val layoutDirection = if (com.example.util.LocaleHelper.isRtlLanguage(currentLang)) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = com.example.ui.theme.Typography
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
            ) {
                content()

                val currentSnapshot = snapshotBitmap
                val progress = transitionProgress.value

                if (isAnimating && currentSnapshot != null && progress < 1f) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {} // Block touch interactions during theme transition
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val wipeY = canvasHeight * progress

                        // Draw the OLD theme snapshot clipped from wipeY down to canvasHeight
                        clipRect(
                            left = 0f,
                            top = wipeY,
                            right = canvasWidth,
                            bottom = canvasHeight
                        ) {
                            drawImage(
                                image = currentSnapshot,
                                dstSize = IntSize(canvasWidth.toInt(), canvasHeight.toInt())
                            )
                        }

                        // Glowing dividing ribbon at wipe position
                        val ribbonHeight = 6.dp.toPx()
                        val glowColor = if (isDark) PlenxoBlueDarkPrimary else PlenxoBluePrimary

                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = 0f),
                                    glowColor,
                                    glowColor.copy(alpha = 0f)
                                ),
                                startY = (wipeY - ribbonHeight).coerceAtLeast(0f),
                                endY = wipeY + ribbonHeight
                            ),
                            topLeft = Offset(0f, (wipeY - ribbonHeight / 2).coerceAtLeast(0f)),
                            size = Size(canvasWidth, ribbonHeight)
                        )
                    }
                }
            }
        }
    }
}

private fun captureAppViewToBitmap(view: View): ImageBitmap? {
    if (view.width <= 0 || view.height <= 0) return null
    return try {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
