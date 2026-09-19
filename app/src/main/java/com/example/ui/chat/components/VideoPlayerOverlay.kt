package com.example.ui.chat.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.util.Log
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

// Global cache for video resume positions
private val playbackPositionCache = ConcurrentHashMap<String, Long>()

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Advanced OTT-Grade Fullscreen Video Player with Gestures, Speed Control, Aspect Ratio Toggle,
 * Volume/Brightness Controls, Double-Tap Seeking, and Dark Neon Glassmorphic Styling.
 */
@Composable
fun VideoPlayerOverlay(
    videoUrl: String,
    title: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Resume position lookup
    val savedPosition = remember(videoUrl) { playbackPositionCache[videoUrl] ?: 0L }

    // State parameters
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(savedPosition) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }

    // Aspect Ratio / Resize mode: FIT -> ZOOM (Crop) -> FILL
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Playback Speed
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    // Double-tap seek animation indicators
    var doubleTapSeekLeft by remember { mutableStateOf(false) }
    var doubleTapSeekRight by remember { mutableStateOf(false) }

    // Swipe gesture overlays
    var isAdjustingBrightness by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableFloatStateOf(0.7f) }

    var isAdjustingVolume by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(1.0f) }

    // Initialize Media3 ExoPlayer with Plenxo User-Agent & Catbox redirect support
    val exoPlayer = remember(videoUrl) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Plenxo-Android-App")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val mediaSourceFactory = DefaultMediaSourceFactory(context.applicationContext)
            .setDataSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                val uri = Uri.parse(videoUrl)
                setMediaItem(MediaItem.fromUri(uri))
                playWhenReady = true
                prepare()
            }
    }

    // Attach ExoPlayer error and state listener
    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    if (savedPosition > 0L && exoPlayer.currentPosition < 1000L) {
                        exoPlayer.seekTo(savedPosition)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("VideoPlayerOverlay", "ExoPlayer playback error [${error.errorCodeName}]: ${error.message}", error)
            }
        }
        exoPlayer.addListener(listener)

        while (true) {
            if (exoPlayer.isPlaying) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                currentPositionMs = pos
                playbackPositionCache[videoUrl] = pos
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(250)
        }
    }

    // Auto-hide controls after 3.5 seconds of inactivity
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3500)
            showControls = false
        }
    }

    // Reset double-tap indicators after brief display
    LaunchedEffect(doubleTapSeekLeft) {
        if (doubleTapSeekLeft) {
            delay(700)
            doubleTapSeekLeft = false
        }
    }
    LaunchedEffect(doubleTapSeekRight) {
        if (doubleTapSeekRight) {
            delay(700)
            doubleTapSeekRight = false
        }
    }

    // Reset gesture indicators after drag end
    LaunchedEffect(isAdjustingBrightness) {
        if (isAdjustingBrightness) {
            delay(1200)
            isAdjustingBrightness = false
        }
    }
    LaunchedEffect(isAdjustingVolume) {
        if (isAdjustingVolume) {
            delay(1200)
            isAdjustingVolume = false
        }
    }

    // Cleanup and save position on dispose
    DisposableEffect(exoPlayer) {
        onDispose {
            playbackPositionCache[videoUrl] = exoPlayer.currentPosition.coerceAtLeast(0L)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Extracted Display Title
    val displayTitle = remember(videoUrl, title) {
        if (!title.isNullOrBlank()) title
        else {
            val fileName = videoUrl.substringAfterLast("/").substringBefore("?")
            if (fileName.length in 3..28) fileName else "Video Message"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("video_player_fullscreen_overlay")
        ) {
            // Media3 Surface Layer
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        this.resizeMode = resizeMode
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )

            // Touch Gestures Layer (Single-tap toggle, Double-tap seek, Drag for Brightness/Volume)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                            },
                            onDoubleTap = { offset ->
                                val screenWidth = size.width
                                val tapX = offset.x
                                if (tapX < screenWidth * 0.40f) {
                                    // Rewind 10 seconds
                                    val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(newPos)
                                    currentPositionMs = newPos
                                    doubleTapSeekLeft = true
                                } else if (tapX > screenWidth * 0.60f) {
                                    // Forward 10 seconds
                                    val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(durationMs)
                                    exoPlayer.seekTo(newPos)
                                    currentPositionMs = newPos
                                    doubleTapSeekRight = true
                                } else {
                                    // Toggle Play/Pause on center double tap
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val screenWidth = size.width
                                if (offset.x < screenWidth * 0.5f) {
                                    isAdjustingBrightness = true
                                } else {
                                    isAdjustingVolume = true
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val deltaY = -dragAmount.y / 400f
                                if (change.position.x < size.width * 0.5f) {
                                    isAdjustingBrightness = true
                                    brightnessLevel = (brightnessLevel + deltaY).coerceIn(0.05f, 1.0f)
                                    activity?.window?.attributes = activity?.window?.attributes?.apply {
                                        screenBrightness = brightnessLevel
                                    }
                                } else {
                                    isAdjustingVolume = true
                                    volumeLevel = (volumeLevel + deltaY).coerceIn(0.0f, 1.0f)
                                    exoPlayer.volume = if (isMuted) 0f else volumeLevel
                                }
                            },
                            onDragEnd = {
                                // Keep overlay briefly
                            }
                        )
                    }
            )

            // Double-Tap Rewind Indicator Overlay
            AnimatedVisibility(
                visible = doubleTapSeekLeft,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2563EB).copy(alpha = 0.25f))
                        .border(1.5.dp, Color(0xFF2563EB), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10s",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "-10s",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Double-Tap Forward Indicator Overlay
            AnimatedVisibility(
                visible = doubleTapSeekRight,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2563EB).copy(alpha = 0.25f))
                        .border(1.5.dp, Color(0xFF2563EB), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 10s",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "+10s",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Vertical Brightness Gesture Overlay
            AnimatedVisibility(
                visible = isAdjustingBrightness,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A101D).copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, Color(0xFF2563EB).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Brightness6,
                            contentDescription = "Brightness",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(120.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(brightnessLevel)
                                    .align(Alignment.BottomCenter)
                                    .background(Color(0xFF2563EB), CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${(brightnessLevel * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Vertical Volume Gesture Overlay
            AnimatedVisibility(
                visible = isAdjustingVolume,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A101D).copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, Color(0xFF2563EB).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (volumeLevel == 0f || isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Volume",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(120.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(volumeLevel)
                                    .align(Alignment.BottomCenter)
                                    .background(Color(0xFF2563EB), CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${(volumeLevel * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Full Overlay UI Controls Dock
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.90f)
                                )
                            )
                        )
                ) {
                    // TOP BAR DOCK
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF12192A).copy(alpha = 0.85f), CircleShape)
                                    .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.5f), CircleShape)
                                    .testTag("video_overlay_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = displayTitle,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Aspect Ratio Mode Toggle Button (FIT / ZOOM / FILL)
                            val aspectLabel = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "FIT"
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "CROP"
                                else -> "FILL"
                            }
                            OutlinedButton(
                                onClick = {
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF2563EB)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = aspectLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Playback Speed Selector Dropdown
                            Box {
                                OutlinedButton(
                                    onClick = { showSpeedMenu = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2563EB)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.7f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "${currentSpeed}x",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false },
                                    modifier = Modifier
                                        .background(Color(0xFF0A101D))
                                        .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                ) {
                                    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                                    speeds.forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${speed}x",
                                                    color = if (speed == currentSpeed) Color(0xFF2563EB) else Color.White,
                                                    fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                currentSpeed = speed
                                                exoPlayer.playbackParameters = PlaybackParameters(speed)
                                                showSpeedMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // CENTER CONTROLS DOCK
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // [-10s] Rewind Button
                        IconButton(
                            onClick = {
                                val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(newPos)
                                currentPositionMs = newPos
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color(0xFF12192A).copy(alpha = 0.85f), CircleShape)
                                .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.6f), CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Glowing Blue Center Play/Pause Button
                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                        exoPlayer.seekTo(0)
                                    }
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFF2563EB).copy(alpha = 0.35f), CircleShape)
                                .border(2.dp, Color(0xFF2563EB), CircleShape)
                                .testTag("toggle_overlay_play_btn")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        // [+10s] Forward Button
                        IconButton(
                            onClick = {
                                val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(durationMs)
                                exoPlayer.seekTo(newPos)
                                currentPositionMs = newPos
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color(0xFF12192A).copy(alpha = 0.85f), CircleShape)
                                .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.6f), CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // BOTTOM CONTROLS DOCK
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        val sliderPosition = if (durationMs > 0) {
                            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        Slider(
                            value = sliderPosition,
                            onValueChange = { fraction ->
                                if (durationMs > 0) {
                                    val targetMs = (fraction * durationMs).toLong()
                                    exoPlayer.seekTo(targetMs)
                                    currentPositionMs = targetMs
                                }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF2563EB),
                                activeTrackColor = Color(0xFF2563EB),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .testTag("video_overlay_seekbar")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Formatted Current Time + Total Duration (e.g. 01:20 / 04:15)
                            val posSec = (currentPositionMs / 1000).toInt()
                            val durSec = (durationMs / 1000).toInt()

                            val posStr = String.format("%02d:%02d", posSec / 60, posSec % 60)
                            val durStr = String.format("%02d:%02d", durSec / 60, durSec % 60)

                            Text(
                                text = "$posStr / $durStr",
                                color = Color(0xFF2563EB),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )

                            // Mute/Unmute Toggle Button
                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    exoPlayer.volume = if (isMuted) 0f else volumeLevel
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Mute Toggle",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
