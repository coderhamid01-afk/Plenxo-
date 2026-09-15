package com.example.webrtc

import android.util.Log
import com.example.calling.model.CallType
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate

/**
 * CallRepository manages real-time Firestore signaling under /calls/{callId}
 * including SDP Offer/Answer exchange and ICE candidate streaming.
 */
class CallRepository(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val TAG = "CallRepository"

    private var callDocListener: ListenerRegistration? = null
    private var candidatesListener: ListenerRegistration? = null
    private var incomingCallsListener: ListenerRegistration? = null

    /**
     * Caller creates the call document in Firestore.
     */
    suspend fun createCallSession(
        callId: String,
        callerUid: String,
        callerName: String,
        callerAvatar: String,
        callerPlenxoId: String,
        receiverUid: String,
        receiverName: String,
        receiverAvatar: String,
        receiverPlenxoId: String,
        callType: CallType
    ): Boolean {
        return try {
            val callData = hashMapOf(
                "callId" to callId,
                "callerUid" to callerUid,
                "callerName" to callerName,
                "callerAvatar" to callerAvatar,
                "callerPlenxoId" to callerPlenxoId,
                "receiverUid" to receiverUid,
                "receiverName" to receiverName,
                "receiverAvatar" to receiverAvatar,
                "receiverPlenxoId" to receiverPlenxoId,
                "callType" to callType.name,
                "status" to "CALLING",
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("calls").document(callId).set(callData).await()
            Log.d(TAG, "Call session created in Firestore: $callId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating call session: ${e.message}", e)
            false
        }
    }

    /**
     * Caller sends SDP Offer.
     */
    suspend fun sendOffer(callId: String, offerSdp: String): Boolean {
        return try {
            val offerMap = mapOf(
                "type" to "OFFER",
                "sdp" to offerSdp
            )
            firestore.collection("calls").document(callId)
                .update("offer", offerMap)
                .await()
            Log.d(TAG, "SDP offer saved in /calls/$callId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving SDP offer: ${e.message}", e)
            false
        }
    }

    /**
     * Receiver sends SDP Answer.
     */
    suspend fun sendAnswer(callId: String, answerSdp: String): Boolean {
        return try {
            val answerMap = mapOf(
                "type" to "ANSWER",
                "sdp" to answerSdp
            )
            firestore.collection("calls").document(callId)
                .update(
                    mapOf(
                        "answer" to answerMap,
                        "status" to "CONNECTED"
                    )
                ).await()
            Log.d(TAG, "SDP answer saved in /calls/$callId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving SDP answer: ${e.message}", e)
            false
        }
    }

    /**
     * Caller listens for SDP Answer from Receiver.
     */
    fun listenForAnswer(
        callId: String,
        onAnswerReceived: (String) -> Unit,
        onStatusChanged: (String) -> Unit
    ) {
        callDocListener?.remove()
        callDocListener = firestore.collection("calls").document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for answer: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val status = snapshot.getString("status") ?: ""
                    onStatusChanged(status)

                    @Suppress("UNCHECKED_CAST")
                    val answer = snapshot.get("answer") as? Map<String, Any?>
                    val sdp = answer?.get("sdp") as? String
                    if (!sdp.isNullOrBlank()) {
                        Log.d(TAG, "Remote answer received for call: $callId")
                        onAnswerReceived(sdp)
                    }
                }
            }
    }

    /**
     * Receiver listens for call document changes (e.g. caller cancels).
     */
    fun listenToCallDocument(
        callId: String,
        onStatusChanged: (String) -> Unit
    ) {
        callDocListener?.remove()
        callDocListener = firestore.collection("calls").document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val status = snapshot.getString("status") ?: ""
                onStatusChanged(status)
            }
    }

    /**
     * Stream gathered local ICE candidate to Firestore.
     */
    fun sendIceCandidate(callId: String, candidate: IceCandidate, isCaller: Boolean) {
        val targetCollection = if (isCaller) "callerCandidates" else "receiverCandidates"
        val candidateData = hashMapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "sdp" to candidate.sdp,
            "timestamp" to System.currentTimeMillis()
        )

        firestore.collection("calls").document(callId)
            .collection(targetCollection)
            .add(candidateData)
            .addOnSuccessListener {
                Log.d(TAG, "Sent ICE candidate to $targetCollection for call $callId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send ICE candidate: ${e.message}")
            }
    }

    /**
     * Listen for remote ICE candidates in real time.
     */
    fun listenForRemoteIceCandidates(
        callId: String,
        isCaller: Boolean,
        onCandidateReceived: (IceCandidate) -> Unit
    ) {
        // If we are caller, listen to receiverCandidates; if receiver, listen to callerCandidates
        val targetCollection = if (isCaller) "receiverCandidates" else "callerCandidates"
        candidatesListener?.remove()

        val processedCandidateIds = mutableSetOf<String>()

        candidatesListener = firestore.collection("calls").document(callId)
            .collection(targetCollection)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for ICE candidates: ${error.message}")
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    val doc = change.document
                    if (!processedCandidateIds.contains(doc.id)) {
                        processedCandidateIds.add(doc.id)
                        val sdpMid = doc.getString("sdpMid") ?: ""
                        val sdpMLineIndex = doc.getLong("sdpMLineIndex")?.toInt() ?: 0
                        val sdp = doc.getString("sdp") ?: ""

                        if (sdp.isNotEmpty()) {
                            val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                            Log.d(TAG, "Received remote ICE candidate from $targetCollection")
                            onCandidateReceived(candidate)
                        }
                    }
                }
            }
    }

    /**
     * Update call status (e.g. ENDED, DECLINED).
     */
    fun updateCallStatus(callId: String, status: String) {
        if (callId.isBlank()) return
        firestore.collection("calls").document(callId)
            .update("status", status)
            .addOnSuccessListener {
                Log.d(TAG, "Call $callId status updated to $status")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update call status: ${e.message}")
            }
    }

    /**
     * Listen for incoming calls for the current user.
     */
    fun startListeningForIncomingCalls(
        currentUserId: String,
        onIncomingCall: (callDoc: DocumentSnapshot) -> Unit
    ) {
        if (currentUserId.isBlank()) return
        incomingCallsListener?.remove()

        val now = System.currentTimeMillis()
        incomingCallsListener = firestore.collection("calls")
            .whereEqualTo("receiverUid", currentUserId)
            .whereEqualTo("status", "CALLING")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for incoming calls: ${error.message}")
                    return@addSnapshotListener
                }

                snapshots?.documents?.forEach { doc ->
                    val createdAt = doc.getLong("createdAt") ?: 0L
                    // Only react to fresh calls (less than 45 seconds old)
                    if (System.currentTimeMillis() - createdAt < 45000L) {
                        onIncomingCall(doc)
                    }
                }
            }
    }

    fun stopListeningForIncomingCalls() {
        incomingCallsListener?.remove()
        incomingCallsListener = null
    }

    /**
     * Clean up all active listeners for the current call.
     */
    fun cleanupCallListeners() {
        callDocListener?.remove()
        callDocListener = null
        candidatesListener?.remove()
        candidatesListener = null
    }
}
