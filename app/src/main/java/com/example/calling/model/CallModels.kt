package com.example.calling.model

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable

@Keep
enum class CallType {
    VOICE,
    VIDEO
}

@Keep
enum class CallState {
    IDLE,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    CONNECTED,
    RECONNECTING,
    ENDED,
    FAILED,
    TIMEOUT,
    REJECTED,
    CANCELLED
}

@Keep
enum class NetworkQuality {
    EXCELLENT,
    GOOD,
    POOR,
    DISCONNECTED
}

@Keep
enum class AudioOutputRoute {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
    HEADSET
}

@Keep
@Immutable
data class CallSession(
    val callId: String = "",
    val peerUid: String = "",
    val peerName: String = "",
    val peerAvatar: String = "",
    val peerPlenxoId: String = "",
    val peerStatus: String = "Online",
    val callType: CallType = CallType.VOICE,
    val callState: CallState = CallState.IDLE,
    val durationSeconds: Long = 0L,
    val networkQuality: NetworkQuality = NetworkQuality.EXCELLENT,
    val audioRoute: AudioOutputRoute = AudioOutputRoute.EARPIECE,
    val isMicMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraOn: Boolean = false,
    val isFrontCamera: Boolean = true,
    val isBlurEnabled: Boolean = false,
    val isIncoming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
