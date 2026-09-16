package com.example.calling

import android.content.Context
import android.util.Log
import com.example.PlenxoApplication
import com.example.calling.model.AudioOutputRoute
import com.example.calling.model.CallSession
import com.example.calling.model.CallState
import com.example.calling.model.CallType
import com.example.calling.model.NetworkQuality
import com.example.model.CallLog
import com.example.webrtc.CallRepository
import com.example.webrtc.WebRtcEngine
import com.example.service.IncomingCallService
import com.example.util.NotificationHelper
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID

/**
 * CallManager coordinates the WebRTC Engine, Audio Routing, and Firestore Signaling Pipeline
 * while exposing observable StateFlows to the Jetpack Compose UI.
 */
object CallManager {
    private const val TAG = "CallManager"
    private val scope = CoroutineScope(Dispatchers.Main)

    val callRepository = CallRepository()
    private var webRtcEngine: WebRtcEngine? = null
    private var audioRouteManager: AudioRouteManager? = null

    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private var incomingOfferSdp: String? = null

    private var timerJob: Job? = null
    private var ringTimerJob: Job? = null
    private val collectorJobs = mutableListOf<Job>()
    private var logSaverCallback: ((CallLog) -> Unit)? = null

    val eglBaseContext: EglBase.Context?
        get() = webRtcEngine?.eglBase?.eglBaseContext

    private val appContext: Context
        get() = PlenxoApplication.instance.applicationContext

    fun setLogSaver(callback: (CallLog) -> Unit) {
        logSaverCallback = callback
    }

    /**
     * Start an outgoing call (Caller Flow)
     */
    fun startOutgoingCall(
        peerUid: String,
        peerName: String,
        peerAvatar: String,
        peerPlenxoId: String,
        callType: CallType,
        onSaveLog: ((CallLog) -> Unit)? = null
    ) {
        if (onSaveLog != null) {
            logSaverCallback = onSaveLog
        }

        cleanupPreviousSession()

        val callId = UUID.randomUUID().toString()
        val initialRoute = if (callType == CallType.VIDEO) AudioOutputRoute.SPEAKER else AudioOutputRoute.EARPIECE
        val session = CallSession(
            callId = callId,
            peerUid = peerUid,
            peerName = peerName.ifBlank { "Plenxo User" },
            peerAvatar = peerAvatar,
            peerPlenxoId = peerPlenxoId,
            peerStatus = "Ringing...",
            callType = callType,
            callState = CallState.OUTGOING_RINGING,
            durationSeconds = 0L,
            networkQuality = NetworkQuality.EXCELLENT,
            audioRoute = initialRoute,
            isMicMuted = false,
            isSpeakerOn = callType == CallType.VIDEO,
            isCameraOn = callType == CallType.VIDEO,
            isFrontCamera = true,
            isBlurEnabled = false,
            isIncoming = false,
            timestamp = System.currentTimeMillis()
        )
        _activeCall.value = session

        // 1. Audio Routing
        audioRouteManager = AudioRouteManager(appContext).apply {
            startCommunication(initialRoute)
        }

        // 2. WebRTC Engine
        val engine = WebRtcEngine(appContext)
        webRtcEngine = engine

        collectorJobs += scope.launch {
            engine.localVideoTrackFlow.collect { track ->
                _localVideoTrack.value = track
            }
        }
        collectorJobs += scope.launch {
            engine.remoteVideoTrackFlow.collect { track ->
                _remoteVideoTrack.value = track
            }
        }
        collectorJobs += scope.launch {
            engine.networkQuality.collect { quality ->
                setNetworkQuality(quality)
            }
        }

        engine.onIceCandidateGathered = { candidate ->
            callRepository.sendIceCandidate(callId, candidate, isCaller = true)
        }

        engine.onIceConnectionChanged = { quality ->
            setNetworkQuality(quality)
        }

        engine.onIceStateChanged = { state ->
            val current = _activeCall.value
            if (current != null) {
                when (state) {
                    org.webrtc.PeerConnection.IceConnectionState.CONNECTED,
                    org.webrtc.PeerConnection.IceConnectionState.COMPLETED -> {
                        if (current.callState != CallState.CONNECTED) {
                            transitionToConnected()
                        }
                    }
                    org.webrtc.PeerConnection.IceConnectionState.DISCONNECTED -> {
                        if (current.callState == CallState.CONNECTED) {
                            _activeCall.value = current.copy(callState = CallState.RECONNECTING, peerStatus = "Reconnecting...")
                        }
                    }
                    org.webrtc.PeerConnection.IceConnectionState.FAILED -> {
                        terminateCall(CallState.FAILED, "Connection Failed")
                    }
                    else -> {}
                }
            }
        }

        engine.setupLocalMedia(isVideo = (callType == CallType.VIDEO), useFrontCamera = true)
        engine.createPeerConnection()

        // 3. Firestore Signaling Pipeline
        val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        scope.launch {
            val created = callRepository.createCallSession(
                callId = callId,
                callerUid = myUid,
                callerName = "You",
                callerAvatar = "",
                callerPlenxoId = "",
                receiverUid = peerUid,
                receiverName = peerName,
                receiverAvatar = peerAvatar,
                receiverPlenxoId = peerPlenxoId,
                callType = callType
            )

            if (created) {
                // Create SDP Offer
                engine.createOffer(
                    onSuccess = { offerDesc ->
                        scope.launch {
                            callRepository.sendOffer(callId, offerDesc.description)
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to create SDP offer: $error")
                        terminateCall(CallState.FAILED, "Call Failed: $error")
                    }
                )

                // Listen for SDP Answer
                callRepository.listenForAnswer(
                    callId = callId,
                    onAnswerReceived = { answerSdp ->
                        val answerDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                        engine.setRemoteDescription(answerDesc) { success ->
                            if (!success) {
                                terminateCall(CallState.FAILED, "Failed to connect")
                            }
                        }
                    },
                    onStatusChanged = { status ->
                        if (status == "ENDED" || status == "DECLINED") {
                            onRemoteEnded(status)
                        }
                    }
                )

                // Listen for Remote ICE candidates
                callRepository.listenForRemoteIceCandidates(callId, isCaller = true) { candidate ->
                    engine.addIceCandidate(candidate)
                }
            } else {
                terminateCall(CallState.FAILED, "Failed to start call session")
            }
        }
        
        startRingTimer()
    }

    /**
     * Handle incoming call detected via Firestore signaling
     */
    fun handleIncomingCallFromSignaling(callDoc: DocumentSnapshot) {
        val callId = callDoc.getString("callId") ?: callDoc.id
        // Avoid duplicate triggers if already handling this call
        if (_activeCall.value?.callId == callId) return

        val callerUid = callDoc.getString("callerUid") ?: ""
        val callerName = callDoc.getString("callerName") ?: "Incoming Caller"
        val callerAvatar = callDoc.getString("callerAvatar") ?: ""
        val callerPlenxoId = callDoc.getString("callerPlenxoId") ?: ""
        val typeStr = callDoc.getString("callType") ?: "VOICE"
        val callType = if (typeStr.equals("VIDEO", ignoreCase = true)) CallType.VIDEO else CallType.VOICE

        @Suppress("UNCHECKED_CAST")
        val offerMap = callDoc.get("offer") as? Map<String, Any?>
        val offerSdp = offerMap?.get("sdp") as? String

        incomingOfferSdp = offerSdp

        triggerIncomingCall(
            callId = callId,
            peerUid = callerUid,
            peerName = callerName,
            peerAvatar = callerAvatar,
            peerPlenxoId = callerPlenxoId,
            callType = callType
        )
    }

    /**
     * Trigger an incoming call locally
     */
    fun triggerIncomingCall(
        callId: String = UUID.randomUUID().toString(),
        peerUid: String,
        peerName: String,
        peerAvatar: String,
        peerPlenxoId: String,
        callType: CallType,
        onSaveLog: ((CallLog) -> Unit)? = null
    ) {
        if (onSaveLog != null) {
            logSaverCallback = onSaveLog
        }

        cleanupPreviousSession()

        val session = CallSession(
            callId = callId,
            peerUid = peerUid,
            peerName = peerName.ifBlank { "Incoming Caller" },
            peerAvatar = peerAvatar,
            peerPlenxoId = peerPlenxoId,
            peerStatus = "Incoming call...",
            callType = callType,
            callState = CallState.INCOMING_RINGING,
            durationSeconds = 0L,
            networkQuality = NetworkQuality.EXCELLENT,
            audioRoute = if (callType == CallType.VIDEO) AudioOutputRoute.SPEAKER else AudioOutputRoute.EARPIECE,
            isMicMuted = false,
            isSpeakerOn = callType == CallType.VIDEO,
            isCameraOn = callType == CallType.VIDEO,
            isFrontCamera = true,
            isBlurEnabled = false,
            isIncoming = true,
            timestamp = System.currentTimeMillis()
        )

        _activeCall.value = session

        // Listen if caller cancels before we answer
        callRepository.listenToCallDocument(callId) { status ->
            if (status == "ENDED" || status == "DECLINED") {
                onRemoteEnded(status)
            }
        }
        
        startRingTimer()
    }

    /**
     * Accept an incoming call (Receiver Flow)
     */
    fun acceptCall() {
        val current = _activeCall.value ?: return
        if (current.callState != CallState.INCOMING_RINGING) return

        val callId = current.callId
        val initialRoute = if (current.callType == CallType.VIDEO) AudioOutputRoute.SPEAKER else AudioOutputRoute.EARPIECE

        // 1. Audio Routing
        audioRouteManager = AudioRouteManager(appContext).apply {
            startCommunication(initialRoute)
        }

        // 2. WebRTC Engine
        val engine = WebRtcEngine(appContext)
        webRtcEngine = engine

        collectorJobs += scope.launch {
            engine.localVideoTrackFlow.collect { track ->
                _localVideoTrack.value = track
            }
        }
        collectorJobs += scope.launch {
            engine.remoteVideoTrackFlow.collect { track ->
                _remoteVideoTrack.value = track
            }
        }
        collectorJobs += scope.launch {
            engine.networkQuality.collect { quality ->
                setNetworkQuality(quality)
            }
        }

        engine.onIceCandidateGathered = { candidate ->
            callRepository.sendIceCandidate(callId, candidate, isCaller = false)
        }

        engine.onIceConnectionChanged = { quality ->
            setNetworkQuality(quality)
        }

        engine.onIceStateChanged = { state ->
            val current = _activeCall.value
            if (current != null) {
                when (state) {
                    org.webrtc.PeerConnection.IceConnectionState.CONNECTED,
                    org.webrtc.PeerConnection.IceConnectionState.COMPLETED -> {
                        if (current.callState != CallState.CONNECTED) {
                            transitionToConnected()
                        }
                    }
                    org.webrtc.PeerConnection.IceConnectionState.DISCONNECTED -> {
                        if (current.callState == CallState.CONNECTED) {
                            _activeCall.value = current.copy(callState = CallState.RECONNECTING, peerStatus = "Reconnecting...")
                        }
                    }
                    org.webrtc.PeerConnection.IceConnectionState.FAILED -> {
                        terminateCall(CallState.FAILED, "Connection Failed")
                    }
                    else -> {}
                }
            }
        }

        engine.setupLocalMedia(isVideo = (current.callType == CallType.VIDEO), useFrontCamera = true)
        engine.createPeerConnection()

        // 3. Set Remote Offer & Create Answer
        val offerSdp = incomingOfferSdp
        if (!offerSdp.isNullOrBlank()) {
            val offerDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
            engine.setRemoteDescription(offerDesc) { success ->
                if (success) {
                    engine.createAnswer(
                        onSuccess = { answerDesc ->
                            scope.launch {
                                callRepository.sendAnswer(callId, answerDesc.description)
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to create answer: $error")
                            terminateCall(CallState.FAILED, "Failed to create answer")
                        }
                    )
                } else {
                    terminateCall(CallState.FAILED, "Failed to set remote description")
                }
            }
        }

        // Listen for remote ICE candidates from Caller
        callRepository.listenForRemoteIceCandidates(callId, isCaller = false) { candidate ->
            engine.addIceCandidate(candidate)
        }
    }

    private fun transitionToConnected() {
        ringTimerJob?.cancel()
        val current = _activeCall.value ?: return
        _activeCall.value = current.copy(
            callState = CallState.CONNECTED,
            peerStatus = "Connected"
        )
        startDurationTimer()
    }

    private fun terminateCall(reason: CallState, statusText: String, overrideDirection: String? = null) {
        val current = _activeCall.value ?: return
        val finalDirection = overrideDirection ?: if (current.isIncoming) {
            if (current.callState == CallState.INCOMING_RINGING) "MISSED" else "INCOMING"
        } else {
            "OUTGOING"
        }

        val log = CallLog(
            callId = current.callId,
            peerUid = current.peerUid,
            peerName = current.peerName,
            peerPhotoUrl = current.peerAvatar,
            peerPlenxoId = current.peerPlenxoId,
            callType = if (current.callType == CallType.VIDEO) "VIDEO" else "AUDIO",
            direction = finalDirection,
            timestamp = current.timestamp,
            durationSeconds = current.durationSeconds
        )

        val firestoreStatus = when (reason) {
            CallState.ENDED -> "ENDED"
            CallState.FAILED -> "FAILED"
            CallState.TIMEOUT -> "TIMEOUT"
            CallState.REJECTED -> "DECLINED"
            CallState.CANCELLED -> "CANCELLED"
            else -> "ENDED"
        }

        callRepository.updateCallStatus(current.callId, firestoreStatus)
        cleanupPreviousSession()

        _activeCall.value = current.copy(callState = reason, peerStatus = statusText)
        saveCallLog(log)

        if (reason == CallState.TIMEOUT && current.callState == CallState.INCOMING_RINGING) {
            NotificationHelper.showMissedCallNotification(appContext, current.peerName, current.callType.name)
        }

        scope.launch {
            delay(1000)
            _activeCall.value = null
        }
    }

    private fun startDurationTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                delay(1000)
                val active = _activeCall.value
                if (active != null && active.callState == CallState.CONNECTED) {
                    _activeCall.value = active.copy(durationSeconds = active.durationSeconds + 1)
                } else {
                    break
                }
            }
        }
    }

    private fun startRingTimer() {
        ringTimerJob?.cancel()
        ringTimerJob = scope.launch {
            delay(30_000L) // 30 seconds ring timeout
            val current = _activeCall.value ?: return@launch
            if (current.callState == CallState.OUTGOING_RINGING || current.callState == CallState.INCOMING_RINGING) {
                // Timeout logic
                terminateCall(CallState.TIMEOUT, "No Answer", if (current.callState == CallState.INCOMING_RINGING) "MISSED" else "OUTGOING")
            }
        }
    }

    /**
     * Decline incoming call
     */
    fun declineCall() {
        terminateCall(CallState.REJECTED, "Call Declined", "MISSED")
    }

    /**
     * End active call
     */
    fun endCall() {
        val current = _activeCall.value ?: return
        if (current.callState == CallState.OUTGOING_RINGING) {
            terminateCall(CallState.CANCELLED, "Call Cancelled")
        } else {
            terminateCall(CallState.ENDED, "Call Ended")
        }
    }

    private fun onRemoteEnded(status: String) {
        val reason = when(status) {
            "DECLINED" -> CallState.REJECTED
            "CANCELLED" -> CallState.CANCELLED
            "TIMEOUT" -> CallState.TIMEOUT
            "FAILED" -> CallState.FAILED
            else -> CallState.ENDED
        }
        val label = when(status) {
            "DECLINED" -> "Call Declined"
            "CANCELLED" -> "Call Cancelled"
            "TIMEOUT" -> "No Answer"
            "FAILED" -> "Call Failed"
            else -> "Call Ended"
        }
        terminateCall(reason, label)
    }

    private fun saveCallLog(log: CallLog) {
        try {
            logSaverCallback?.invoke(log)
            
            // Also write directly to Firestore call history
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (currentUid != null) {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val isCaller = log.direction == "OUTGOING"
                
                val historyMap = hashMapOf(
                    // Requested fields
                    "callerUid" to (if (isCaller) currentUid else log.peerUid),
                    "receiverUid" to (if (isCaller) log.peerUid else currentUid),
                    "callerName" to (if (isCaller) "Me" else log.peerName),
                    "callerAvatar" to (if (isCaller) "" else log.peerPhotoUrl),
                    "plenxoId" to log.peerPlenxoId,
                    "callType" to log.callType,
                    "status" to log.direction,
                    
                    // Original fields for CallLog data class compatibility
                    "callId" to log.callId,
                    "peerUid" to log.peerUid,
                    "peerName" to log.peerName,
                    "peerPhotoUrl" to log.peerPhotoUrl,
                    "peerPlenxoId" to log.peerPlenxoId,
                    "direction" to log.direction,
                    
                    // Shared fields
                    "timestamp" to log.timestamp,
                    "durationSeconds" to log.durationSeconds
                )
                
                db.collection("users").document(currentUid)
                    .collection("call_history").document(log.callId)
                    .set(historyMap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving call log: ${e.message}", e)
        }
    }

    fun toggleMic() {
        val current = _activeCall.value ?: return
        val newMute = !current.isMicMuted
        _activeCall.value = current.copy(isMicMuted = newMute)
        webRtcEngine?.toggleMic(!newMute)
    }

    fun toggleSpeaker() {
        val current = _activeCall.value ?: return
        val newSpeaker = !current.isSpeakerOn
        val newRoute = if (newSpeaker) AudioOutputRoute.SPEAKER else AudioOutputRoute.EARPIECE
        _activeCall.value = current.copy(isSpeakerOn = newSpeaker, audioRoute = newRoute)
        audioRouteManager?.setAudioRoute(newRoute)
    }

    fun setAudioRoute(route: AudioOutputRoute) {
        val current = _activeCall.value ?: return
        val isSpeaker = (route == AudioOutputRoute.SPEAKER)
        _activeCall.value = current.copy(audioRoute = route, isSpeakerOn = isSpeaker)
        audioRouteManager?.setAudioRoute(route)
    }

    fun toggleCamera() {
        val current = _activeCall.value ?: return
        val newCameraOn = !current.isCameraOn
        _activeCall.value = current.copy(isCameraOn = newCameraOn)
        webRtcEngine?.toggleCamera(newCameraOn)
    }

    fun switchCamera() {
        val current = _activeCall.value ?: return
        webRtcEngine?.switchCamera { isFront ->
            _activeCall.value = _activeCall.value?.copy(isFrontCamera = isFront)
        }
    }

    fun toggleBlur() {
        val current = _activeCall.value ?: return
        _activeCall.value = current.copy(isBlurEnabled = !current.isBlurEnabled)
    }

    fun setNetworkQuality(quality: NetworkQuality) {
        val current = _activeCall.value ?: return
        _activeCall.value = current.copy(networkQuality = quality)
    }

    private fun cleanupPreviousSession() {
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        timerJob?.cancel()
        timerJob = null
        ringTimerJob?.cancel()
        ringTimerJob = null
        incomingOfferSdp = null
        
        try {
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancel(IncomingCallService.NOTIFICATION_ID)
            appContext.stopService(android.content.Intent(appContext, IncomingCallService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop IncomingCallService: ${e.message}")
        }

        callRepository.cleanupCallListeners()

        try {
            webRtcEngine?.dispose()
            webRtcEngine = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing WebRtcEngine: ${e.message}")
        }

        try {
            audioRouteManager?.release()
            audioRouteManager = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRouteManager: ${e.message}")
        }

        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
    }
}
