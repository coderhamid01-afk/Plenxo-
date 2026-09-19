package com.example.ui.calling

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.calling.CallManager
import com.example.calling.model.AudioOutputRoute
import com.example.calling.model.CallSession
import com.example.calling.model.CallState
import com.example.calling.model.NetworkQuality

private val SlateDarkBackground = Color(0xFF0B1120)
private val SlateSurface = Color(0xFF1E293B)
private val FrostedGlassBorder = Color(0x3394A3B8)
private val BlueAccent = Color(0xFF2563EB)
private val EmeraldSuccess = Color(0xFF10B981)
private val RedEndCall = Color(0xFFEF4444)

@Composable
fun VoiceCallScreen(
    session: CallSession,
    modifier: Modifier = Modifier
) {
    var showRouteDialog by remember { mutableStateOf(false) }

    // Pulsing animation for avatar
    val infiniteTransition = rememberInfiniteTransition(label = "VoiceCallPulse")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseRing1"
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha1"
    )

    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseRing2"
    )
    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF0B1120),
                        Color(0xFF020617)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("voice_call_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Top Status & Network Quality Badge
            VoiceCallTopBadge(
                callState = session.callState,
                networkQuality = session.networkQuality,
                durationSeconds = session.durationSeconds
            )

            Spacer(modifier = Modifier.weight(0.7f))

            // Pulsing Avatar with Wave visualizer
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                // Pulse Rings when connected or ringing
                if (session.callState == CallState.CONNECTED || session.callState == CallState.OUTGOING_RINGING) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .scale(pulseScale2)
                            .background(BlueAccent.copy(alpha = pulseAlpha2), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .scale(pulseScale1)
                            .background(BlueAccent.copy(alpha = pulseAlpha1), CircleShape)
                    )
                }

                // Avatar Container
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(SlateSurface)
                        .border(3.dp, BlueAccent.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (session.peerAvatar.isNotBlank() && (session.peerAvatar.startsWith("http") || session.peerAvatar.startsWith("content://"))) {
                        AsyncImage(
                            model = session.peerAvatar,
                            contentDescription = "Caller Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = session.peerName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recipient Details
            Text(
                text = session.peerName,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Plenxo ID Pill
            Surface(
                color = Color(0x332563EB),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "@${session.peerPlenxoId.ifBlank { "PX-USER" }}",
                    color = BlueAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Online Status / Call Status
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusDotColor = when (session.callState) {
                    CallState.CONNECTED -> EmeraldSuccess
                    CallState.OUTGOING_RINGING -> Color(0xFFFBBF24)
                    CallState.RECONNECTING -> Color(0xFFF97316)
                    CallState.ENDED, CallState.FAILED -> Color(0xFFEF4444)
                    else -> Color.Gray
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusDotColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (session.callState) {
                        CallState.OUTGOING_RINGING -> "Ringing..."
                        CallState.CONNECTED -> "Active Voice Call"
                        CallState.RECONNECTING -> "Reconnecting..."
                        CallState.ENDED -> "Call Ended"
                        CallState.FAILED -> "Call Failed"
                        else -> session.peerStatus
                    },
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Dynamic Audio Waveform Visualizer
            AudioWaveformVisualizer(
                isActive = session.callState == CallState.CONNECTED && !session.isMicMuted
            )

            Spacer(modifier = Modifier.weight(1f))

            // Frosted Glass Control Dock (Strictly NO camera controls)
            VoiceCallControlDock(
                isMuted = session.isMicMuted,
                isSpeakerOn = session.isSpeakerOn,
                currentRoute = session.audioRoute,
                onToggleMute = { CallManager.toggleMic() },
                onToggleSpeaker = { CallManager.toggleSpeaker() },
                onOpenRoutePicker = { showRouteDialog = true },
                onEndCall = { CallManager.endCall() }
            )

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Audio Route Selector Dialog
        if (showRouteDialog) {
            AudioRouteSelectorDialog(
                selectedRoute = session.audioRoute,
                onSelectRoute = { route ->
                    CallManager.setAudioRoute(route)
                    showRouteDialog = false
                },
                onDismiss = { showRouteDialog = false }
            )
        }
    }
}

@Composable
private fun VoiceCallTopBadge(
    callState: CallState,
    networkQuality: NetworkQuality,
    durationSeconds: Long
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Live Call Timer
        Text(
            text = formatDuration(durationSeconds),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Network Quality Badge ("HD Voice • Excellent")
        val (qualityText, qualityColor) = when (networkQuality) {
            NetworkQuality.EXCELLENT -> "HD Voice • Excellent" to EmeraldSuccess
            NetworkQuality.GOOD -> "HD Voice • Good" to BlueAccent
            NetworkQuality.POOR -> "Voice • Poor Connection" to Color(0xFFF97316)
            NetworkQuality.DISCONNECTED -> "Reconnecting..." to Color(0xFFEF4444)
        }

        Surface(
            color = Color(0x2B1E293B),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SignalCellularAlt,
                    contentDescription = "Signal",
                    tint = qualityColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = qualityText,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AudioWaveformVisualizer(isActive: Boolean) {
    val barCount = 14
    val transition = rememberInfiniteTransition(label = "WaveformBars")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val animDuration = 450 + (i * 75) % 400
            val animatedHeightFraction by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = if (isActive) (0.4f + ((i % 5) * 0.15f)) else 0.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(animDuration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "WaveBar_$i"
            )

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((40 * animatedHeightFraction).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isActive) {
                            Brush.verticalGradient(
                                colors = listOf(BlueAccent, EmeraldSuccess)
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(Color.DarkGray, Color.Gray.copy(alpha = 0.4f))
                            )
                        }
                    )
            )
        }
    }
}

@Composable
private fun VoiceCallControlDock(
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    currentRoute: AudioOutputRoute,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onOpenRoutePicker: () -> Unit,
    onEndCall: () -> Unit
) {
    Surface(
        color = Color(0xE01E293B),
        shape = RoundedCornerShape(32.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FrostedGlassBorder),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_call_control_dock")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute Button
            VoiceCallControlButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                isActive = isMuted,
                activeColor = Color(0xFFEF4444),
                onClick = onToggleMute,
                testTag = "voice_call_mute_button"
            )

            // Speaker Toggle
            VoiceCallControlButton(
                icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                label = if (isSpeakerOn) "Speaker" else "Earpiece",
                isActive = isSpeakerOn,
                activeColor = BlueAccent,
                onClick = onToggleSpeaker,
                testTag = "voice_call_speaker_button"
            )

            // Audio Route Selector (Bluetooth / Headset / Earpiece)
            val routeIcon = when (currentRoute) {
                AudioOutputRoute.BLUETOOTH -> Icons.Default.Bluetooth
                AudioOutputRoute.HEADSET -> Icons.Default.Headset
                AudioOutputRoute.SPEAKER -> Icons.Default.VolumeUp
                AudioOutputRoute.EARPIECE -> Icons.Default.Hearing
            }
            VoiceCallControlButton(
                icon = routeIcon,
                label = currentRoute.name.lowercase().replaceFirstChar { it.uppercase() },
                isActive = currentRoute != AudioOutputRoute.EARPIECE,
                activeColor = Color(0xFFA855F7),
                onClick = onOpenRoutePicker,
                testTag = "voice_call_audio_route_button"
            )

            // Red End Call FAB
            FloatingActionButton(
                onClick = onEndCall,
                containerColor = RedEndCall,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(8.dp),
                modifier = Modifier
                    .size(56.dp)
                    .testTag("voice_call_end_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun VoiceCallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
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
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.LightGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AudioRouteSelectorDialog(
    selectedRoute: AudioOutputRoute,
    onSelectRoute: (AudioOutputRoute) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Text(
                text = "Select Audio Output",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AudioRouteOptionItem(
                    title = "Phone Earpiece",
                    icon = Icons.Default.Hearing,
                    isSelected = selectedRoute == AudioOutputRoute.EARPIECE,
                    onClick = { onSelectRoute(AudioOutputRoute.EARPIECE) }
                )
                AudioRouteOptionItem(
                    title = "Speakerphone",
                    icon = Icons.Default.VolumeUp,
                    isSelected = selectedRoute == AudioOutputRoute.SPEAKER,
                    onClick = { onSelectRoute(AudioOutputRoute.SPEAKER) }
                )
                AudioRouteOptionItem(
                    title = "Bluetooth Device",
                    icon = Icons.Default.Bluetooth,
                    isSelected = selectedRoute == AudioOutputRoute.BLUETOOTH,
                    onClick = { onSelectRoute(AudioOutputRoute.BLUETOOTH) }
                )
                AudioRouteOptionItem(
                    title = "Wired Headset",
                    icon = Icons.Default.Headset,
                    isSelected = selectedRoute == AudioOutputRoute.HEADSET,
                    onClick = { onSelectRoute(AudioOutputRoute.HEADSET) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = BlueAccent)
            }
        }
    )
}

@Composable
private fun AudioRouteOptionItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0x332563EB) else Color(0x22334155)
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) BlueAccent else Color(0x2264748B)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) BlueAccent else Color.LightGray,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                color = if (isSelected) Color.White else Color.LightGray,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
