package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.calling.CallManager
import com.example.calling.model.CallType
import com.example.model.CallLog
import com.example.ui.calling.rememberCallPermissionController
import com.example.ui.theme.PlenxoColors
import com.example.ui.theme.PlenxoTypography
import com.example.viewmodel.PlenxoViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CyanAccent = Color(0xFF38BDF8)
private val PurpleAccent = Color(0xFFA855F7)
private val EmeraldSuccess = Color(0xFF10B981)
private val CoralRed = Color(0xFFFF7B72)

@Composable
fun CallHistoryScreen(
    viewModel: PlenxoViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val callLogs by viewModel.callLogs.collectAsState()
    var selectedLogForCall by remember { mutableStateOf<CallLog?>(null) }
    val permissionController = rememberCallPermissionController()

    LaunchedEffect(Unit) {
        viewModel.startListeningForCallLogs()
    }

    Box(modifier = modifier.fillMaxSize().background(PlenxoColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("call_history_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Call History",
                    style = PlenxoTypography.Title.copy(fontSize = 22.sp, color = Color.White),
                    fontWeight = FontWeight.Bold
                )
            }

            if (callLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "No Calls",
                            tint = Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Call Logs Found",
                            style = PlenxoTypography.Title.copy(color = Color.White, fontSize = 16.sp),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Start high quality audio and video calls with your connected friends.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(items = callLogs, key = { it.callId }) { log ->
                        CallLogItemRow(
                            log = log,
                            onClick = { selectedLogForCall = log },
                            onAudioCall = {
                                permissionController.startVoiceCallWithPermission {
                                    CallManager.startOutgoingCall(
                                        peerUid = log.peerUid,
                                        peerName = log.peerName,
                                        peerAvatar = log.peerPhotoUrl,
                                        peerPlenxoId = log.peerPlenxoId,
                                        callType = CallType.VOICE,
                                        onSaveLog = { newLog -> viewModel.recordCallLog(newLog) }
                                    )
                                }
                            },
                            onVideoCall = {
                                permissionController.startVideoCallWithPermission {
                                    CallManager.startOutgoingCall(
                                        peerUid = log.peerUid,
                                        peerName = log.peerName,
                                        peerAvatar = log.peerPhotoUrl,
                                        peerPlenxoId = log.peerPlenxoId,
                                        callType = CallType.VIDEO,
                                        onSaveLog = { newLog -> viewModel.recordCallLog(newLog) }
                                    )
                                }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            thickness = 0.5.dp,
                            color = PlenxoColors.Divider
                        )
                    }
                }
            }
        }

        // Call Initiation Dialog
        selectedLogForCall?.let { log ->
            AlertDialog(
                onDismissRequest = { selectedLogForCall = null },
                containerColor = Color(0xFF161B22),
                title = {
                    Text(
                        text = "Call ${log.peerName}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "Choose call type to connect with ${log.peerName} (@${log.peerPlenxoId.ifBlank { "PX-xxxxxx" }}):",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = {
                                val currentLog = log
                                selectedLogForCall = null
                                permissionController.startVoiceCallWithPermission {
                                    CallManager.startOutgoingCall(
                                        peerUid = currentLog.peerUid,
                                        peerName = currentLog.peerName,
                                        peerAvatar = currentLog.peerPhotoUrl,
                                        peerPlenxoId = currentLog.peerPlenxoId,
                                        callType = CallType.VOICE,
                                        onSaveLog = { newLog -> viewModel.recordCallLog(newLog) }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PlenxoColors.Primary),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("dialog_start_voice_call_button")
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Audio Call", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Voice", color = Color.White)
                        }

                        Button(
                            onClick = {
                                val currentLog = log
                                selectedLogForCall = null
                                permissionController.startVideoCallWithPermission {
                                    CallManager.startOutgoingCall(
                                        peerUid = currentLog.peerUid,
                                        peerName = currentLog.peerName,
                                        peerAvatar = currentLog.peerPhotoUrl,
                                        peerPlenxoId = currentLog.peerPlenxoId,
                                        callType = CallType.VIDEO,
                                        onSaveLog = { newLog -> viewModel.recordCallLog(newLog) }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("dialog_start_video_call_button")
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Video", color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedLogForCall = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
fun CallLogItemRow(
    log: CallLog,
    onClick: () -> Unit,
    onAudioCall: () -> Unit = {},
    onVideoCall: () -> Unit = {}
) {
    val isVideo = log.callType.equals("VIDEO", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E2230))
                .border(1.dp, Color(0xFF30363D), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (log.peerPhotoUrl.isNotEmpty() && (log.peerPhotoUrl.startsWith("http") || log.peerPhotoUrl.startsWith("content://"))) {
                AsyncImage(
                    model = log.peerPhotoUrl,
                    contentDescription = "Peer Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(PlenxoColors.Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = log.peerName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Peer Name & Direction + Timestamp
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = log.peerName,
                    style = PlenxoTypography.Body.copy(color = Color.White, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Voice / Video Badge
                Surface(
                    color = if (isVideo) PurpleAccent.copy(alpha = 0.2f) else CyanAccent.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = if (isVideo) "Video" else "Voice",
                            tint = if (isVideo) PurpleAccent else CyanAccent,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isVideo) "Video" else "Voice",
                            color = if (isVideo) PurpleAccent else CyanAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Directional indicator & timestamp
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (directionIcon, directionColor) = when (log.direction) {
                    "INCOMING" -> Icons.Default.CallReceived to EmeraldSuccess
                    "OUTGOING" -> Icons.Default.CallMade to CyanAccent
                    else -> Icons.Default.CallMissed to CoralRed
                }

                Icon(
                    imageVector = directionIcon,
                    contentDescription = log.direction,
                    tint = directionColor,
                    modifier = Modifier.size(14.dp)
                )

                Spacer(modifier = Modifier.width(5.dp))

                Text(
                    text = formatCallTimestamp(log.timestamp),
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                if (log.direction != "MISSED" && log.durationSeconds > 0) {
                    Text(
                        text = " • ${formatDuration(log.durationSeconds)}",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Direct Call Redial Action
        IconButton(
            onClick = {
                if (isVideo) onVideoCall() else onAudioCall()
            },
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
        ) {
            Icon(
                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = "Redial",
                tint = if (isVideo) PurpleAccent else CyanAccent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatCallTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02dm %02ds", m, s)
}
