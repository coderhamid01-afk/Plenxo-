package com.example.webrtc

import android.content.Context
import android.util.Log
import com.example.calling.model.NetworkQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.*

/**
 * WebRtcEngine manages the WebRTC PeerConnectionFactory, local audio/video media sources,
 * PeerConnection lifecycle, camera switching, SDP negotiation, and ICE candidate exchange.
 */
class WebRtcEngine(private val context: Context) {
    private val TAG = "WebRtcEngine"
    private val scope = CoroutineScope(Dispatchers.Main)

    val eglBase: EglBase = EglBase.create()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null

    private val _localVideoTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrackFlow.asStateFlow()

    private val _remoteVideoTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrackFlow: StateFlow<VideoTrack?> = _remoteVideoTrackFlow.asStateFlow()

    private val _networkQuality = MutableStateFlow(NetworkQuality.EXCELLENT)
    val networkQuality: StateFlow<NetworkQuality> = _networkQuality.asStateFlow()

    var onIceCandidateGathered: ((IceCandidate) -> Unit)? = null
    var onIceConnectionChanged: ((NetworkQuality) -> Unit)? = null
    var onIceStateChanged: ((PeerConnection.IceConnectionState) -> Unit)? = null

    private var isVideoCall = false
    private var isFrontFacingCamera = true
    var currentCallId: String? = null
    
    private val pendingRemoteIceCandidates = mutableListOf<IceCandidate>()
    private val successfullyAddedCandidateKeys = mutableSetOf<String>()
    private var isFlushingIceCandidates = false

    private fun getCandidateKey(candidate: IceCandidate): String {
        return "${candidate.sdpMid}_${candidate.sdpMLineIndex}_${candidate.sdp}"
    }

    init {
        initializeFactory()
    }

    private fun initializeFactory() {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true, // enableIntelVp8Encoder
            true  // enableH264HighProfile
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    /**
     * Set up local media tracks.
     * Video is initialized ONLY for video calls.
     */
    fun setupLocalMedia(isVideo: Boolean, useFrontCamera: Boolean = true) {
        isVideoCall = isVideo
        isFrontFacingCamera = useFrontCamera
        val factory = peerConnectionFactory ?: return

        // 1. Audio Setup
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
        localAudioTrack?.setEnabled(true)

        // 2. Video Setup (Active ONLY during VIDEO calls)
        if (isVideo) {
            setupVideoMedia(useFrontCamera)
        }
    }

    private fun setupVideoMedia(useFrontCamera: Boolean) {
        val factory = peerConnectionFactory ?: return
        try {
            surfaceTextureHelper = SurfaceTextureHelper.create("WebRtcCaptureThread", eglBase.eglBaseContext)
            videoSource = factory.createVideoSource(false)

            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames

            var targetDevice: String? = null
            for (name in deviceNames) {
                if (useFrontCamera && enumerator.isFrontFacing(name)) {
                    targetDevice = name
                    break
                } else if (!useFrontCamera && enumerator.isBackFacing(name)) {
                    targetDevice = name
                    break
                }
            }

            if (targetDevice == null && deviceNames.isNotEmpty()) {
                targetDevice = deviceNames[0]
            }

            if (targetDevice != null) {
                val capturer = enumerator.createCapturer(targetDevice, null)
                videoCapturer = capturer
                capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
                
                // Start capturing at 720p 30fps
                try {
                    capturer.startCapture(1280, 720, 30)
                } catch (e: Exception) {
                    Log.w(TAG, "720p capture failed, falling back to 640x480: ${e.message}")
                    capturer.startCapture(640, 480, 30)
                }

                localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource)
                localVideoTrack?.setEnabled(true)
                _localVideoTrackFlow.value = localVideoTrack
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up video media: ${e.message}", e)
        }
    }

    /**
     * Create PeerConnection with STUN/TURN servers and Unified Plan.
     */
    fun createPeerConnection() {
        resetIceCandidateQueue()
        val factory = peerConnectionFactory ?: return

        val rtcConfig = IceServerConfig.buildRtcConfiguration()

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "[CallId: $currentCallId] onSignalingChange: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "[CallId: $currentCallId] onIceConnectionChange: $state")
                if (state != null) {
                    scope.launch { onIceStateChanged?.invoke(state) }
                }
                val quality = when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> NetworkQuality.EXCELLENT
                    PeerConnection.IceConnectionState.CHECKING -> NetworkQuality.GOOD
                    PeerConnection.IceConnectionState.DISCONNECTED -> NetworkQuality.POOR
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> NetworkQuality.DISCONNECTED
                    else -> NetworkQuality.GOOD
                }
                _networkQuality.value = quality
                scope.launch { onIceConnectionChanged?.invoke(quality) }

                if (quality == NetworkQuality.POOR) {
                    adaptVideoForPoorNetwork()
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "[CallId: $currentCallId] onIceGatheringChange: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    Log.d(TAG, "[CallId: $currentCallId] Gathered local ICE candidate: sdpMid=${candidate.sdpMid}, index=${candidate.sdpMLineIndex}")
                    scope.launch { onIceCandidateGathered?.invoke(candidate) }
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "onAddStream: ${stream?.id}")
                if (stream != null && stream.videoTracks.isNotEmpty()) {
                    val remoteTrack = stream.videoTracks[0]
                    _remoteVideoTrackFlow.value = remoteTrack
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {
                Log.d(TAG, "onRemoveStream: ${stream?.id}")
            }

            override fun onDataChannel(channel: DataChannel?) {}

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded")
            }

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                Log.d(TAG, "onAddTrack: ${receiver?.id()}")
                val track = receiver?.track()
                if (track is VideoTrack) {
                    _remoteVideoTrackFlow.value = track
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                Log.d(TAG, "onTrack transceiver: ${transceiver?.receiver?.id()}")
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    _remoteVideoTrackFlow.value = track
                }
            }
        })

        // Add local tracks
        localAudioTrack?.let {
            peerConnection?.addTrack(it, listOf("ARDAMS"))
        }
        localVideoTrack?.let {
            peerConnection?.addTrack(it, listOf("ARDAMS"))
        }
    }

    /**
     * Create SDP Offer
     */
    fun createOffer(onSuccess: (SessionDescription) -> Unit, onFailure: (String) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            if (isVideoCall) {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local SDP offer set successfully")
                            scope.launch { onSuccess(desc) }
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set local SDP offer: $error")
                            scope.launch { onFailure(error ?: "Unknown error") }
                        }
                    }, desc)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create SDP offer: $error")
                scope.launch { onFailure(error ?: "Unknown error") }
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    /**
     * Create SDP Answer
     */
    fun createAnswer(onSuccess: (SessionDescription) -> Unit, onFailure: (String) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            if (isVideoCall) {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local SDP answer set successfully")
                            scope.launch { onSuccess(desc) }
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set local SDP answer: $error")
                            scope.launch { onFailure(error ?: "Unknown error") }
                        }
                    }, desc)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "[CallId: $currentCallId] Failed to create SDP answer: $error")
                scope.launch { onFailure(error ?: "Unknown error") }
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    /**
     * Create SDP Offer with IceRestart flag enabled for reconnection.
     */
    fun restartIce(onSuccess: (SessionDescription) -> Unit, onFailure: (String) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            if (isVideoCall) {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "[CallId: $currentCallId] Local ICE restart SDP offer set successfully")
                            scope.launch { onSuccess(desc) }
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "[CallId: $currentCallId] Failed to set local ICE restart offer: $error")
                            scope.launch { onFailure(error ?: "Unknown error") }
                        }
                    }, desc)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "[CallId: $currentCallId] Failed to create ICE restart offer: $error")
                scope.launch { onFailure(error ?: "Unknown error") }
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    /**
     * Reset pending ICE candidate queue and added candidate key tracking.
     */
    fun resetIceCandidateQueue() {
        synchronized(pendingRemoteIceCandidates) {
            pendingRemoteIceCandidates.clear()
        }
        synchronized(successfullyAddedCandidateKeys) {
            successfullyAddedCandidateKeys.clear()
        }
        Log.d(TAG, "[CallId: $currentCallId] [ICE] Pending candidate queue and deduplication keys reset")
    }

    /**
     * Set Remote Description (Offer or Answer)
     */
    fun setRemoteDescription(sdp: SessionDescription, onComplete: ((Boolean) -> Unit)? = null) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "[CallId: $currentCallId] Remote description set successfully (${sdp.type})")
                flushPendingIceCandidates()
                scope.launch { onComplete?.invoke(true) }
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "[CallId: $currentCallId] Failed to set remote description: $error")
                scope.launch { onComplete?.invoke(false) }
            }
        }, sdp)
    }

    /**
     * Flush queued remote ICE candidates after remote description is set.
     */
    fun flushPendingIceCandidates() {
        val pc = peerConnection ?: return
        if (pc.remoteDescription == null) return

        val candidatesToProcess: List<IceCandidate>
        synchronized(pendingRemoteIceCandidates) {
            if (isFlushingIceCandidates) {
                Log.d(TAG, "[CallId: $currentCallId] [ICE] Flush already in progress, skipping concurrent flush")
                return
            }
            isFlushingIceCandidates = true
            candidatesToProcess = ArrayList(pendingRemoteIceCandidates)
            pendingRemoteIceCandidates.clear()
        }

        var succeededCount = 0
        var failedCount = 0
        val retryCandidates = mutableListOf<IceCandidate>()

        for (candidate in candidatesToProcess) {
            val key = getCandidateKey(candidate)
            var alreadyAdded = false
            synchronized(successfullyAddedCandidateKeys) {
                alreadyAdded = successfullyAddedCandidateKeys.contains(key)
            }
            if (alreadyAdded) continue

            Log.d(TAG, "[CallId: $currentCallId] [ICE] Flushing candidate: sdpMid=${candidate.sdpMid}, index=${candidate.sdpMLineIndex}")
            val success = try {
                pc.addIceCandidate(candidate)
            } catch (e: Exception) {
                Log.e(TAG, "[CallId: $currentCallId] Exception flushing candidate sdpMid=${candidate.sdpMid}: ${e.message}")
                false
            }

            if (success) {
                succeededCount++
                synchronized(successfullyAddedCandidateKeys) {
                    successfullyAddedCandidateKeys.add(key)
                }
            } else {
                failedCount++
                retryCandidates.add(candidate)
                Log.w(TAG, "[CallId: $currentCallId] [ICE] addIceCandidate returned false during flush: sdpMid=${candidate.sdpMid}, index=${candidate.sdpMLineIndex}")
            }
        }

        synchronized(pendingRemoteIceCandidates) {
            for (failedCand in retryCandidates) {
                val failedKey = getCandidateKey(failedCand)
                if (pendingRemoteIceCandidates.none { getCandidateKey(it) == failedKey }) {
                    pendingRemoteIceCandidates.add(failedCand)
                }
            }
            isFlushingIceCandidates = false
        }

        Log.d(TAG, "[CallId: $currentCallId] [ICE] Flushed candidate batch summary: total=${candidatesToProcess.size}, attempted=${candidatesToProcess.size}, successful=$succeededCount, failed=$failedCount, remainingPending=${pendingRemoteIceCandidates.size}")
    }

    /**
     * Add Remote ICE Candidate with deduplication and stale call check
     */
    fun addIceCandidate(candidate: IceCandidate, callIdCheck: String? = null) {
        if (callIdCheck != null && currentCallId != null && callIdCheck != currentCallId) {
            Log.w(TAG, "[CallId: $currentCallId] Ignoring remote ICE candidate for stale callId: $callIdCheck")
            return
        }
        if (candidate.sdp.isBlank()) return

        try {
            val key = getCandidateKey(candidate)
            synchronized(successfullyAddedCandidateKeys) {
                if (successfullyAddedCandidateKeys.contains(key)) {
                    Log.d(TAG, "[CallId: $currentCallId] [ICE] Duplicate candidate ignored: sdpMid=${candidate.sdpMid}, index=${candidate.sdpMLineIndex}")
                    return
                }
            }

            if (peerConnection?.remoteDescription == null) {
                synchronized(pendingRemoteIceCandidates) {
                    if (pendingRemoteIceCandidates.none { getCandidateKey(it) == key }) {
                        pendingRemoteIceCandidates.add(candidate)
                        Log.d(TAG, "[CallId: $currentCallId] [ICE] Queued remote candidate before remote SDP: sdpMid=${candidate.sdpMid}, index=${candidate.sdpMLineIndex}")
                    }
                }
            } else {
                Log.d(TAG, "[CallId: $currentCallId] [ICE] Attempting immediate addIceCandidate: sdpMid=${candidate.sdpMid}, index=${candidate.sdpMLineIndex}")
                val added = try {
                    peerConnection?.addIceCandidate(candidate) ?: false
                } catch (e: Exception) {
                    Log.e(TAG, "[CallId: $currentCallId] Exception adding ICE candidate: ${e.message}")
                    false
                }

                if (added) {
                    synchronized(successfullyAddedCandidateKeys) {
                        successfullyAddedCandidateKeys.add(key)
                    }
                    Log.d(TAG, "[CallId: $currentCallId] [ICE] Candidate successfully added: sdpMid=${candidate.sdpMid}, index=${candidate.sdpMLineIndex}")
                } else {
                    Log.w(TAG, "[CallId: $currentCallId] [ICE] addIceCandidate returned false immediately for sdpMid=${candidate.sdpMid}, index=${candidate.sdpMLineIndex}. Preserving candidate for retry.")
                    synchronized(pendingRemoteIceCandidates) {
                        if (pendingRemoteIceCandidates.none { getCandidateKey(it) == key }) {
                            pendingRemoteIceCandidates.add(candidate)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[CallId: $currentCallId] Error processing ICE candidate: ${e.message}")
        }
    }

    /**
     * Toggle Local Microphone
     */
    fun toggleMic(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    /**
     * Toggle Local Camera
     */
    fun toggleCamera(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    /**
     * Switch Front/Rear Camera
     */
    fun switchCamera(onSwitched: ((Boolean) -> Unit)? = null) {
        val capturer = videoCapturer as? CameraVideoCapturer ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                isFrontFacingCamera = isFront
                scope.launch { onSwitched?.invoke(isFront) }
            }

            override fun onCameraSwitchError(error: String?) {
                Log.e(TAG, "Camera switch error: $error")
            }
        })
    }

    /**
     * Adaptive video: When network drops to POOR, reduce resolution or fps to prioritize audio keep-alive.
     */
    fun adaptVideoForPoorNetwork() {
        if (!isVideoCall) return
        Log.w(TAG, "Network is POOR: adapting video capture to conserve bandwidth and keep call alive")
        try {
            val capturer = videoCapturer as? CameraVideoCapturer
            capturer?.changeCaptureFormat(320, 240, 15)
        } catch (e: Exception) {
            Log.e(TAG, "Error adapting video format: ${e.message}")
        }
    }

    /**
     * Proper lifecycle cleanup to prevent memory leaks and clear active camera/mic indicators.
     */
    fun dispose() {
        try {
            _localVideoTrackFlow.value = null
            _remoteVideoTrackFlow.value = null

            try {
                videoCapturer?.stopCapture()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping video capturer: ${e.message}")
            }
            videoCapturer?.dispose()
            videoCapturer = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            localVideoTrack?.dispose()
            localVideoTrack = null

            videoSource?.dispose()
            videoSource = null

            localAudioTrack?.dispose()
            localAudioTrack = null

            audioSource?.dispose()
            audioSource = null

            synchronized(pendingRemoteIceCandidates) {
                pendingRemoteIceCandidates.clear()
            }
            synchronized(successfullyAddedCandidateKeys) {
                successfullyAddedCandidateKeys.clear()
            }

            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null

            eglBase.release()
            Log.d(TAG, "WebRtcEngine disposed cleanly")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing WebRtcEngine: ${e.message}", e)
        }
    }
}
