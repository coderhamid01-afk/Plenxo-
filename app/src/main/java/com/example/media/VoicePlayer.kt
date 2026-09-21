package com.example.media

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoicePlayer(private val context: Context) {
    private val audioPlayerManager = AudioPlayerManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    val isPlaying: StateFlow<Boolean>
        get() = MutableStateFlow(audioPlayerManager.playbackState.value.isPlaying).asStateFlow()

    val currentUrl: StateFlow<String?>
        get() = MutableStateFlow(audioPlayerManager.playbackState.value.currentUrl).asStateFlow()

    fun play(url: String) {
        audioPlayerManager.playAudio(url)
    }

    fun pause() {
        audioPlayerManager.pauseAudio()
    }

    fun stop() {
        audioPlayerManager.pauseAudio()
    }

    fun release() {
        // AudioPlayerManager is a singleton, no-op release per instance call
    }
}
