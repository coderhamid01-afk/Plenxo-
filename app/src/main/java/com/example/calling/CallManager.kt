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
    private var lastProcessedOfferSdp: String? = null
    private var lastProcessedAnswerSdp: String? = null
    private var isCallAccepted: Boolean = false
    private var isAnswerCreated: Boolean = false
    private var isAnswerProcessed: Boolean = false

    private var timerJob: Job? = null
    private var ringTimerJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 2
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
        engine.currentCallId = callId
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
            handleIceStateChanged(state, isCaller = true)
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
                        val current = _activeCall.value
                        if (current == null || current.callId != callId) {
                            Log.w(TAG, "Ignoring SDP answer for stale call $callId")
                            return@listenForAnswer
                        }
                        if (isAnswerProcessed || answerSdp == lastProcessedAnswerSdp) {
                            Log.d(TAG, "Answer already processed for call $callId, ignoring duplicate answer snapshot")
                            return@listenForAnswer
                        }
                        isAnswerProcessed = true
                        lastProcessedAnswerSdp = answerSdp
                        val answerDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                        engine.setRemoteDescription(answerDesc) { success ->
                            if (!success) {
                                terminateCall(callId, CallState.FAILED, "Failed to connect")
                            }
                        }
                    },
                    onStatusChanged = { status ->
                        val current = _activeCall.value
                        if (current != null && current.callId == callId) {
                            if (status == "ENDED" || status == "DECLINED" || status == "CANCELLED" || status == "TIMEOUT") {
                                onRemoteEnded(callId, status)
                            }
                        } else {
                            Log.w(TAG, "Ignoring status change ($status) for stale call $callId")
                        }
                    }
                )

                // Listen for Remote ICE candidates
                callRepository.listenForRemoteIceCandidates(callId, isCaller = true) { candidate ->
                    val current = _activeCall.value
                    if (current == null || current.callId != callId) {
                        Log.w(TAG, "Ignoring remote ICE candidate for stale call $callId")
                        return@listenForRemoteIceCandidates
                    }
                    engine.addIceCandidate(candidate, callIdCheck = callId)
                }
            } else {
                terminateCall(callId, CallState.FAILED, "Failed to start call session")
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

        // Listen for document changes (e.g., Offer SDP arrival or caller cancellation)
        callRepository.listenToCallDocument(callId) { snapshot ->
            val current = _activeCall.value
            if (current == null || current.callId != callId) {
                Log.w(TAG, "Ignoring call document snapshot for stale call $callId")
                return@listenToCallDocument
            }
            val status = snapshot.getString("status") ?: ""
            if (status == "ENDED" || status == "DECLINED" || status == "CANCELLED" || status == "TIMEOUT") {
                onRemoteEnded(callId, status)
                return@listenToCallDocument
            }

            @Suppress("UNCHECKED_CAST")
            val offerMap = snapshot.get("offer") as? Map<String, Any?>
            val offerSdp = offerMap?.get("sdp") as? String

            if (!offerSdp.isNullOrBlank()) {
                incomingOfferSdp = offerSdp
                Log.d(TAG, "Incoming Offer SDP received for call $callId")
                if (isCallAccepted && offerSdp != lastProcessedOfferSdp) {
                    Log.d(TAG, "Call was already accepted by user! Processing Offer and generating Answer now.")
                    processOfferAndSendAnswer(callId, offerSdp)
                }
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

        isCallAccepted = true
        val callId = current.callId
        val initialRoute = if (current.callType == CallType.VIDEO) AudioOutputRoute.SPEAKER else AudioOutputRoute.EARPIECE

        _activeCall.value = current.copy(peerStatus = "Connecting...")

        // 1. Audio Routing
        audioRouteManager = AudioRouteManager(appContext).apply {
            startCommunication(initialRoute)
        }

        // 2. WebRTC Engine
        val engine = WebRtcEngine(appContext)
        engine.currentCallId = callId
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
            handleIceStateChanged(state, isCaller = false)
        }

        engine.setupLocalMedia(isVideo = (current.callType == CallType.VIDEO), useFrontCamera = true)
        engine.createPeerConnection()

        // Listen for remote ICE candidates from Caller
        callRepository.listenForRemoteIceCandidates(callId, isCaller = false) { candidate ->
            val active = _activeCall.value
            if (active == null || active.callId != callId) {
                Log.w(TAG, "Ignoring remote ICE candidate for stale call $callId")
                return@listenForRemoteIceCandidates
            }
            engine.addIceCandidate(candidate, callIdCheck = callId)
        }

        // 3. Set Remote Offer & Create Answer if Offer SDP is available
        val offerSdp = incomingOfferSdp
        if (!offerSdp.isNullOrBlank() && offerSdp != lastProcessedOfferSdp) {
            Log.d(TAG, "Offer SDP is already available. Processing Offer and generating Answer now.")
            processOfferAndSendAnswer(callId, offerSdp)
        } else {
            Log.d(TAG, "User accepted call, but Offer SDP has not arrived in Firestore yet. Waiting for Offer in document listener...")
        }
    }

    private fun handleIceStateChanged(state: org.webrtc.PeerConnection.IceConnectionState, isCaller: Boolean) {
        val current = _activeCall.value ?: return
        Log.d(TAG, "[CallId: ${current.callId}] ICE connection state changed: $state")

        when (state) {
            org.webrtc.PeerConnection.IceConnectionState.CONNECTED,
            org.webrtc.PeerConnection.IceConnectionState.COMPLETED -> {
                reconnectJob?.cancel()
                reconnectJob = null
                reconnectAttempts = 0
                if (current.callState != CallState.CONNECTED) {
                    transitionToConnected()
                }
            }
            org.webrtc.PeerConnection.IceConnectionState.DISCONNECTED -> {
                if (current.callState == CallState.CONNECTED || current.callState == CallState.RECONNECTING) {
                    _activeCall.value = current.copy(
                        callState = CallState.RECONNECTING,
                        peerStatus = "Reconnecting..."
                    )
                    scheduleReconnectionRecovery(isCaller)
                }
            }
            org.webrtc.PeerConnection.IceConnectionState.FAILED -> {
                if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    reconnectAttempts++
                    Log.w(TAG, "[CallId: ${current.callId}] ICE state FAILED. Reconnection attempt $reconnectAttempts of $MAX_RECONNECT_ATTEMPTS...")
                    _activeCall.value = current.copy(
                        callState = CallState.RECONNECTING,
                        peerStatus = "Reconnecting (Attempt $reconnectAttempts)..."
                    )
                    if (isCaller) {
                        webRtcEngine?.restartIce(
                            onSuccess = { offer ->
                                scope.launch { callRepository.sendOffer(current.callId, offer.description) }
                            },
                            onFailure = { err ->
                                Log.e(TAG, "ICE restart failed: $err")
                            }
                        )
                    }
                    scheduleReconnectionRecovery(isCaller)
                } else {
                    Log.e(TAG, "[CallId: ${current.callId}] ICE state FAILED and max retries exhausted.")
                    terminateCall(CallState.FAILED, "Connection Lost")
                }
            }
            else -> {}
        }
    }

    private fun scheduleReconnectionRecovery(isCaller: Boolean) {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            // Wait 5s: if still disconnected, trigger ICE restart on caller
            delay(5000L)
            val session = _activeCall.value
            if (session != null && session.callState == CallState.RECONNECTING) {
                if (isCaller) {
                    Log.d(TAG, "[CallId: ${session.callId}] Triggering ICE restart after 5s disconnection...")
                    webRtcEngine?.restartIce(
                        onSuccess = { offer ->
                            scope.launch { callRepository.sendOffer(session.callId, offer.description) }
                        },
                        onFailure = { err ->
                            Log.e(TAG, "ICE restart failed: $err")
                        }
                    )
                }
            }

            // Total 15s window before declaring recovery failure
            delay(10000L)
            val finalSession = _activeCall.value
            if (finalSession != null && finalSession.callState == CallState.RECONNECTING) {
                Log.e(TAG, "[CallId: ${finalSession.callId}] Reconnection recovery timed out after 15s")
                terminateCall(CallState.FAILED, "Connection Lost")
            }
        }
    }

    private fun processOfferAndSendAnswer(callId: String, offerSdp: String) {
        val current = _activeCall.value
        if (current == null || current.callId != callId) {
            Log.w(TAG, "Cannot process offer for stale call $callId")
            return
        }
        val engine = webRtcEngine ?: run {
            Log.e(TAG, "Cannot process offer: webRtcEngine is null")
            return
        }
        if (offerSdp == lastProcessedOfferSdp) {
            Log.w(TAG, "Offer already processed for call $callId, skipping duplicate processing")
            return
        }
        lastProcessedOfferSdp = offerSdp
        isAnswerCreated = true

        val offerDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        engine.setRemoteDescription(offerDesc) { success ->
            if (success) {
                engine.createAnswer(
                    onSuccess = { answerDesc ->
                        scope.launch {
                            callRepository.sendAnswer(callId, answerDesc.description)
                            Log.d(TAG, "SDP Answer successfully sent to Firestore for call $callId")
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to create answer: $error")
                        terminateCall(callId, CallState.FAILED, "Failed to create answer")
                    }
                )
            } else {
                Log.e(TAG, "Failed to set remote description")
                terminateCall(callId, CallState.FAILED, "Failed to set remote description")
            }
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
        terminateCall(null, reason, statusText, overrideDirection)
    }

    private fun terminateCall(targetCallId: String?, reason: CallState, statusText: String, overrideDirection: String? = null) {
        val current = _activeCall.value ?: return
        if (targetCallId != null && current.callId != targetCallId) {
            Log.w(TAG, "Ignoring terminateCall for stale call $targetCallId (active call is ${current.callId})")
            return
        }
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
                terminateCall(current.callId, CallState.TIMEOUT, "No Answer", if (current.callState == CallState.INCOMING_RINGING) "MISSED" else "OUTGOING")
            }
        }
    }

    /**
     * Decline incoming call
     */
    fun declineCall() {
        val current = _activeCall.value ?: return
        terminateCall(current.callId, CallState.REJECTED, "Call Declined", "MISSED")
    }

    /**
     * End active call
     */
    fun endCall() {
        val current = _activeCall.value ?: return
        if (current.callState == CallState.OUTGOING_RINGING) {
            terminateCall(current.callId, CallState.CANCELLED, "Call Cancelled")
        } else {
            terminateCall(current.callId, CallState.ENDED, "Call Ended")
        }
    }

    private fun onRemoteEnded(targetCallId: String, status: String) {
        val current = _activeCall.value
        if (current == null || current.callId != targetCallId) {
            Log.w(TAG, "Ignoring onRemoteEnded for stale call $targetCallId")
            return
        }
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
        terminateCall(targetCallId, reason, label)
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
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        incomingOfferSdp = null
        lastProcessedOfferSdp = null
        lastProcessedAnswerSdp = null
        isCallAccepted = false
        isAnswerCreated = false
        isAnswerProcessed = false
        
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
