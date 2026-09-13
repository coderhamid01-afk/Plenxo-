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
    primary = PlenxoElectricViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B0764),
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = PlenxoNeonCyan,
    onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF004F56),
    onSecondaryContainer = Color(0xFF97F0FF),
    tertiary = Color(0xFFD8B4FE),
    onTertiary = Color(0xFF4C1D95),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF131824),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1E2638),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0x4DFFFFFF),
    error = PlenxoError,
    onError = Color.White
)

val PlenxoLightColorScheme = lightColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF4C1D95),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = Color(0xFF6D28D9),
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = PlenxoError,
    onError = Color.White
)

/**
 * Styling helpers for frosted light-glass & dark-glass UI elements
 */
object GlassTheme {
    fun getBackgroundColor(isDark: Boolean): Color {
        return if (isDark) Color(0xFF0B0E14) else Color(0xFFF8FAFC)
    }

    fun getSurfaceColor(isDark: Boolean): Color {
        return if (isDark) Color(0xFF131824) else Color(0xFFFFFFFF)
    }

    fun getGlassCardBackground(isDark: Boolean): Color {
        return if (isDark) Color(0xFF161C2C).copy(alpha = 0.92f) else Color(0xFFFFFFFF).copy(alpha = 0.90f)
    }

    fun getGlassBorderColor(isDark: Boolean): Color {
        return if (isDark) Color(0xFF2E3B5E).copy(alpha = 0.60f) else Color(0xFFE2E8F0).copy(alpha = 0.85f)
    }

    fun getPrimaryTextColor(isDark: Boolean): Color {
        return if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    }

    fun getSecondaryTextColor(isDark: Boolean): Color {
        return if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
    }

    fun getMutedTextColor(isDark: Boolean): Color {
        return if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
    }

    fun getDividerColor(isDark: Boolean): Color {
        return if (isDark) Color(0xFF242F48) else Color(0xFFE2E8F0)
    }

    fun getBrightGradient(isDark: Boolean): Brush {
        return if (isDark) {
            Brush.horizontalGradient(listOf(Color(0xFF8A2BE2), Color(0xFF00E5FF)))
        } else {
            Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFF0284C7)))
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
            typography = androidx.compose.material3.Typography()
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
                        val glowColor = if (isDark) Color(0xFF00E5FF) else Color(0xFF7C3AED)

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
