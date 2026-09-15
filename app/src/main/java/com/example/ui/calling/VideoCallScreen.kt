package com.example.ui.calling

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.calling.CallManager
import com.example.calling.model.CallSession
import com.example.calling.model.CallState
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack
import kotlin.math.roundToInt

private val CyanAccent = Color(0xFF38BDF8)
private val EmeraldSuccess = Color(0xFF10B981)
private val RedEndCall = Color(0xFFEF4444)
private val FrostedBorder = Color(0x3394A3B8)
private val DarkSlateOverlay = Color(0xD90B1120)

@Composable
fun VideoCallScreen(
    session: CallSession,
    modifier: Modifier = Modifier
) {
    val localVideoTrack by CallManager.localVideoTrack.collectAsStateWithLifecycle()
    val remoteVideoTrack by CallManager.remoteVideoTrack.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .testTag("video_call_screen")
    ) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val pipWidthDp = 110.dp
        val pipHeightDp = 160.dp
        val pipWidthPx = with(density) { pipWidthDp.toPx() }
        val pipHeightPx = with(density) { pipHeightDp.toPx() }

        // Initial PIP position at top-right
        var pipOffsetX by remember { mutableFloatStateOf(screenWidthPx - pipWidthPx - 40f) }
        var pipOffsetY by remember { mutableFloatStateOf(200f) }

        // Full-screen remote video feed container
        RemoteVideoFeedContainer(session = session, remoteVideoTrack = remoteVideoTrack)

        // Top Header Bar
        VideoCallTopHeaderBar(
            session = session,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // Floating Picture-in-Picture (PIP) local camera preview
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        pipOffsetX.roundToInt().coerceIn(20, (screenWidthPx - pipWidthPx - 20f).toInt()),
                        pipOffsetY.roundToInt().coerceIn(160, (screenHeightPx - pipHeightPx - 240f).toInt())
                    )
                }
                .size(pipWidthDp, pipHeightDp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        pipOffsetX += dragAmount.x
                        pipOffsetY += dragAmount.y
                    }
                }
                .testTag("video_call_pip_card")
        ) {
            LocalPipCameraCard(
                isCameraOn = session.isCameraOn,
                isFrontCamera = session.isFrontCamera,
                isBlurEnabled = session.isBlurEnabled,
                userAvatar = "", // Local user
                userName = "You",
                localVideoTrack = localVideoTrack
            )
        }

        // Bottom Frosted Glass Control Dock
        VideoCallControlDock(
            isMicMuted = session.isMicMuted,
            isCameraOn = session.isCameraOn,
            isFrontCamera = session.isFrontCamera,
            isSpeakerOn = session.isSpeakerOn,
            isBlurEnabled = session.isBlurEnabled,
            onToggleMic = { CallManager.toggleMic() },
            onToggleCamera = { CallManager.toggleCamera() },
            onSwitchCamera = { CallManager.switchCamera() },
            onToggleSpeaker = { CallManager.toggleSpeaker() },
            onToggleBlur = { CallManager.toggleBlur() },
            onEndCall = { CallManager.endCall() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        )
    }
}

@Composable
private fun RemoteVideoFeedContainer(session: CallSession, remoteVideoTrack: VideoTrack?) {
    val isConnected = session.callState == CallState.CONNECTED

    Box(modifier = Modifier.fillMaxSize()) {
        if (isConnected) {
            if (remoteVideoTrack != null) {
                WebRtcVideoView(
                    videoTrack = remoteVideoTrack,
                    modifier = Modifier.fillMaxSize(),
                    scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL,
                    mirror = false
                )
            } else {
                // Background gradient when remote video is off or missing
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF1E293B),
                                    Color(0xFF0F172A),
                                    Color(0xFF020617)
                                )
                            )
                        )
                )

                // Animated subtle video scanlines/vibe
                val infiniteTransition = rememberInfiniteTransition(label = "VideoScan")
                val gradientShift by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "ScanGradient"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    CyanAccent.copy(alpha = 0.04f * gradientShift),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Remote peer representation
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .border(2.dp, CyanAccent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (session.peerAvatar.isNotBlank() && (session.peerAvatar.startsWith("http") || session.peerAvatar.startsWith("content://"))) {
                            AsyncImage(
                                model = session.peerAvatar,
                                contentDescription = "Peer Remote Feed",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = session.peerName.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0x66000000),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "No video stream",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        } else {
            // Ringing / Connecting backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = session.peerName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (session.callState == CallState.OUTGOING_RINGING) "Calling..." else session.peerStatus,
                        color = Color.LightGray,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalPipCameraCard(
    isCameraOn: Boolean,
    isFrontCamera: Boolean,
    isBlurEnabled: Boolean,
    userAvatar: String,
    userName: String,
    localVideoTrack: VideoTrack?
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, CyanAccent.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
        color = Color(0xFF1E293B),
        shadowElevation = 10.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isCameraOn && localVideoTrack != null) {
                val modifier = if (isBlurEnabled) Modifier.fillMaxSize().blur(10.dp) else Modifier.fillMaxSize()
                WebRtcVideoView(
                    videoTrack = localVideoTrack,
                    modifier = modifier,
                    scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL,
                    mirror = isFrontCamera
                )
            } else if (isCameraOn) {
                // Simulated camera feed view
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF334155),
                                    Color(0xFF1E293B)
                                )
                            )
                        )
                        .then(if (isBlurEnabled) Modifier.blur(12.dp) else Modifier)
                )

                // Lens perspective hint
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x33000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User Lens",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                // Camera Off placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "Camera Off",
                            tint = Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Off", color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }

            // Pip Badges: "You" and Camera Lens Mode
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "You",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isFrontCamera) "Front" else "Rear",
                    color = CyanAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun VideoCallTopHeaderBar(
    session: CallSession,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DarkSlateOverlay,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FrostedBorder),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Recipient Avatar Thumbnail
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .border(1.dp, CyanAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (session.peerAvatar.isNotBlank() && (session.peerAvatar.startsWith("http") || session.peerAvatar.startsWith("content://"))) {
                    AsyncImage(
                        model = session.peerAvatar,
                        contentDescription = "Peer",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = session.peerName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Name & Plenxo ID
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.peerName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${session.peerPlenxoId.ifBlank { "PX-USER" }}",
                    color = CyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Live Call Timer
            Surface(
                color = Color(0x33000000),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = formatDuration(session.durationSeconds),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Network Quality Indicator ("1080p 60fps")
            Surface(
                color = EmeraldSuccess.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = "HD Quality",
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "1080p 60fps",
                        color = EmeraldSuccess,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCallControlDock(
    isMicMuted: Boolean,
    isCameraOn: Boolean,
    isFrontCamera: Boolean,
    isSpeakerOn: Boolean,
    isBlurEnabled: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleBlur: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_call_control_dock"),
        color = DarkSlateOverlay,
        shape = RoundedCornerShape(32.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FrostedBorder),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute Mic
            VideoCallControlButton(
                icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (isMicMuted) "Unmute" else "Mute",
                isActive = isMicMuted,
                activeColor = RedEndCall,
                onClick = onToggleMic,
                testTag = "video_call_mute_button"
            )

            // Camera On/Off
            VideoCallControlButton(
                icon = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                label = if (isCameraOn) "Cam On" else "Cam Off",
                isActive = !isCameraOn,
                activeColor = RedEndCall,
                onClick = onToggleCamera,
                testTag = "video_call_camera_toggle_button"
            )

            // Switch Camera (Front/Rear)
            VideoCallControlButton(
                icon = Icons.Default.Cameraswitch,
                label = if (isFrontCamera) "Front" else "Rear",
                isActive = !isFrontCamera,
                activeColor = CyanAccent,
                onClick = onSwitchCamera,
                testTag = "video_call_switch_camera_button"
            )

            // Speakerphone Toggle
            VideoCallControlButton(
                icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                label = if (isSpeakerOn) "Speaker" else "Earpiece",
                isActive = isSpeakerOn,
                activeColor = CyanAccent,
                onClick = onToggleSpeaker,
                testTag = "video_call_speaker_button"
            )

            // Background Blur
            VideoCallControlButton(
                icon = Icons.Default.BlurOn,
                label = "Blur",
                isActive = isBlurEnabled,
                activeColor = Color(0xFFA855F7),
                onClick = onToggleBlur,
                testTag = "video_call_blur_button"
            )

            // Red End Call FAB
            FloatingActionButton(
                onClick = onEndCall,
                containerColor = RedEndCall,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(6.dp),
                modifier = Modifier
                    .size(50.dp)
                    .testTag("video_call_end_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoCallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor.copy(alpha = 0.25f) else Color(0x33334155))
                .border(
                    width = 1.dp,
                    color = if (isActive) activeColor else Color(0x3364748B),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
