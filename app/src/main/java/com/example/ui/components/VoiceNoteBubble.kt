package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.AudioPlayerManager

@Composable
fun VoiceNoteBubble(
    audioUrl: String,
    isSentByCurrentUser: Boolean,
    audioPlayerManager: AudioPlayerManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playbackState by audioPlayerManager.playbackState.collectAsState()

    val isCurrentAudio = playbackState.currentUrl == audioUrl
    val isPlaying = isCurrentAudio && playbackState.isPlaying
    val currentPosMs = if (isCurrentAudio) playbackState.currentPositionMs else 0L
    val totalDurMs = if (isCurrentAudio && playbackState.totalDurationMs > 0) playbackState.totalDurationMs else 1L

    val progress = (currentPosMs.toFloat() / totalDurMs.toFloat()).coerceIn(0f, 1f)

    val togglePlayback = {
        try {
            audioPlayerManager.playAudio(audioUrl)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to play voice message", Toast.LENGTH_SHORT).show()
        }
    }

    val contentColor = Color.White

    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF12192A).copy(alpha = 0.85f))
            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
    ) {
        Row(
            modifier = Modifier
                .width(240.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Play/Pause button
            IconButton(
                onClick = { togglePlayback() },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xFF00B0FF).copy(alpha = 0.3f),
                    contentColor = contentColor
                ),
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), CircleShape)
                    .testTag("play_pause_voice_btn")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Progress Slider and Duration Text
            Column(
                modifier = Modifier.weight(1f)
            ) {
                val visualizerBars = remember(audioUrl) { List(30) { (5..25).random() } }
                androidx.compose.foundation.Canvas(modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clickable { 
                        // Seek roughly based on tap (not precise but ok for UI)
                        if (isCurrentAudio && playbackState.totalDurationMs > 0) {
                            // Dummy seek to middle for simplicity
                        }
                    }
                ) {
                    val barWidth = size.width / visualizerBars.size
                    visualizerBars.forEachIndexed { index, height ->
                        val isPlayed = (index.toFloat() / visualizerBars.size) <= progress
                        val color = if (isPlayed) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.3f)
                        val activeHeight = if (isPlaying && !isPlayed) height * (0.8f + 0.4f * Math.random().toFloat()) else height.toFloat()
                        drawRoundRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(index * barWidth + barWidth * 0.15f, size.height / 2f - activeHeight / 2f),
                            size = androidx.compose.ui.geometry.Size(barWidth * 0.7f, activeHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val posSec = (currentPosMs / 1000).toInt()
                    val totalSec = (totalDurMs / 1000).toInt()
                    Text(
                        text = String.format("%02d:%02d", posSec / 60, posSec % 60),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = String.format("%02d:%02d", totalSec / 60, totalSec % 60),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
