package com.example.webrtc

import android.util.Log
import org.webrtc.PeerConnection

/**
 * IceServerConfig centralizes STUN and TURN server definitions
 * and WebRTC RTCConfiguration properties.
 */
object IceServerConfig {
    private const val TAG = "IceServerConfig"

    /**
     * Default list of STUN and TURN servers for production calls.
     * TURN credentials can be configured dynamically via environment/server configuration.
     */
    fun createIceServers(): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()

        // Public Google STUN servers
        servers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        servers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
        servers.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer())

        // Configurable OpenRelay TURN servers for fallback NAT traversal (UDP, TCP, TLS/443)
        try {
            val openRelayUser = "openrelayproject"
            val openRelayPass = "openrelayproject"

            servers.add(
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                    .setUsername(openRelayUser)
                    .setPassword(openRelayPass)
                    .createIceServer()
            )
            servers.add(
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                    .setUsername(openRelayUser)
                    .setPassword(openRelayPass)
                    .createIceServer()
            )
            servers.add(
                PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                    .setUsername(openRelayUser)
                    .setPassword(openRelayPass)
                    .createIceServer()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build TURN ice servers: ${e.message}")
        }

        return servers
    }

    /**
     * Build optimized RTCConfiguration adhering to WebRTC best practices.
     */
    fun buildRtcConfiguration(): PeerConnection.RTCConfiguration {
        val iceServers = createIceServers()
        return PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }
    }
}
