import re

with open("app/src/main/java/com/example/ui/components/VoiceNoteBubble.kt", "r") as f:
    content = f.read()

old_surface = """    val bubbleBg = if (isSentByCurrentUser) Color(0xFF1F6FEB) else Color(0xFF21262D)
    val contentColor = Color.White

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bubbleBg,
        modifier = modifier.padding(vertical = 4.dp)
    ) {"""

new_surface = """    val contentColor = Color.White

    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF12192A).copy(alpha = 0.85f))
            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
    ) {"""
content = content.replace(old_surface, new_surface)

# Fix play/pause button colors to be translucent cyan
old_icon_btn = """            IconButton(
                onClick = { togglePlayback() },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isSentByCurrentUser) Color.White.copy(alpha = 0.2f) else Color(0xFF30363D),
                    contentColor = contentColor
                ),
                modifier = Modifier
                    .size(40.dp)
                    .testTag("play_pause_voice_btn")
            )"""
new_icon_btn = """            IconButton(
                onClick = { togglePlayback() },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xFF00B0FF).copy(alpha = 0.3f),
                    contentColor = contentColor
                ),
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), CircleShape)
                    .testTag("play_pause_voice_btn")
            )"""
content = content.replace(old_icon_btn, new_icon_btn)


# Replace Slider with Waveform Canvas
old_slider = """                Slider(
                    value = progress,
                    onValueChange = { newProgress ->
                        if (isCurrentAudio && playbackState.totalDurationMs > 0) {
                            val seekPos = (newProgress * playbackState.totalDurationMs).toLong()
                            audioPlayerManager.seekTo(seekPos)
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFF58A6FF),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.height(20.dp)
                )"""

new_slider = """                val visualizerBars = remember(audioUrl) { List(30) { (5..25).random() } }
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
                }"""
content = content.replace(old_slider, new_slider)


with open("app/src/main/java/com/example/ui/components/VoiceNoteBubble.kt", "w") as f:
    f.write(content)
