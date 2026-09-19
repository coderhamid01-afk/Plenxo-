package com.example.ui.chat.components

import androidx.compose.runtime.Composable

@Composable
fun VideoPlayerDialog(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    VideoPlayerOverlay(
        videoUrl = videoUrl,
        onDismiss = onDismiss
    )
}
