package com.example.media

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val currentUrl: String? = null
)

class AudioPlayerManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioPlayerManager"

        @Volatile
        private var instance: AudioPlayerManager? = null

        fun getInstance(context: Context): AudioPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: AudioPlayerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var exoPlayer: ExoPlayer? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var progressPollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
            if (isPlaying) {
                startProgressPolling()
            } else {
                stopProgressPolling()
            }
        }

        override fun onPlaybackStateChanged(playbackStateInt: Int) {
            when (playbackStateInt) {
                Player.STATE_READY -> {
                    val duration = exoPlayer?.duration ?: 0L
                    _playbackState.value = _playbackState.value.copy(
                        totalDurationMs = if (duration > 0) duration else _playbackState.value.totalDurationMs
                    )
                }
                Player.STATE_ENDED -> {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = 0L
                    )
                    exoPlayer?.seekTo(0)
                    exoPlayer?.pause()
                    stopProgressPolling()
                }
                Player.STATE_IDLE -> {
                    stopProgressPolling()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Audio playback error: ${error.message}", error)
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
            stopProgressPolling()
        }
    }

    private fun ensurePlayerInitialized() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
                addListener(playerListener)
            }
        }
    }

    /**
     * Streams and plays target Catbox or local audio URL.
     * Stops any currently playing voice message before starting a new one.
     */
    fun playAudio(url: String) {
        if (url.isBlank()) {
            Log.w(TAG, "Cannot play audio with empty URL")
            return
        }

        ensurePlayerInitialized()
        val player = exoPlayer ?: return

        val currentState = _playbackState.value

        if (currentState.currentUrl == url) {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        } else {
            // Stop any currently playing voice message
            player.stop()
            _playbackState.value = PlaybackState(
                isPlaying = false,
                currentPositionMs = 0L,
                totalDurationMs = 0L,
                currentUrl = url
            )

            try {
                val mediaItem = MediaItem.fromUri(url)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start audio playback for $url", e)
            }
        }
    }

    /**
     * Pauses current playback.
     */
    fun pauseAudio() {
        exoPlayer?.pause()
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }

    /**
     * Seeks to a specific position in milliseconds.
     */
    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    /**
     * Releases ExoPlayer resources.
     */
    fun release() {
        stopProgressPolling()
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        _playbackState.value = PlaybackState()
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressPollingJob = scope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = player.currentPosition,
                            totalDurationMs = if (player.duration > 0) player.duration else _playbackState.value.totalDurationMs
                        )
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = null
    }
}
