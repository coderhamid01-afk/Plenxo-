package com.example.model

import android.util.Log
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import com.example.util.getDocumentServerFirst
import com.example.util.getQuerySnapshotServerFirst
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.PropertyName
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Keep
data class UserDocReadResult(
    val snapshot: com.google.firebase.firestore.DocumentSnapshot?,
    val readConfirmed: Boolean
)

suspend fun fetchUserDocumentSafely(
    uid: String,
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    maxAttempts: Int = 3,
    emailFallback: String? = null
): UserDocReadResult {
    if (uid.isBlank()) return UserDocReadResult(null, false)
    val userDocRef = firestore.collection("users").document(uid)

    for (attempt in 1..maxAttempts) {
        try {
            val snap = getDocumentServerFirst(userDocRef, timeoutMs = 6000L)
            if (snap.exists()) {
                return UserDocReadResult(snap, true)
            } else {
                // If direct doc lookup by uid didn't find anything, try query by email or uid field
                val targetEmail = emailFallback ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
                if (!targetEmail.isNullOrBlank()) {
                    val query = firestore.collection("users")
                        .whereEqualTo("email", targetEmail.trim())
                        .limit(1)
                    val querySnap = try {
                        getQuerySnapshotServerFirst(query, timeoutMs = 5000L)
                    } catch (_: Exception) {
                        null
                    }
                    if (querySnap != null && !querySnap.isEmpty) {
                        return UserDocReadResult(querySnap.documents[0], true)
                    }
                }
                return UserDocReadResult(snap, true)
            }
        } catch (e: Exception) {
            Log.w("PlenxoUserFetch", "Attempt $attempt failed reading users/$uid: ${e.message}")
        }
        if (attempt < maxAttempts) kotlinx.coroutines.delay(250L * attempt)
    }
    // Every attempt failed
    Log.w("PlenxoUserFetch", "Could not confirm users/$uid after $maxAttempts attempts.")
    return UserDocReadResult(null, false)
}

@Keep
@Serializable
@Immutable
data class UserModel(
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("displayName") @set:PropertyName("displayName") var displayName: String = "",
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("email") @set:PropertyName("email") var email: String = "",
    @get:PropertyName("bio") @set:PropertyName("bio") var bio: String = "",
    @get:PropertyName("statusMessage") @set:PropertyName("statusMessage") var statusMessage: String = "",
    @get:PropertyName("bioStatus") @set:PropertyName("bioStatus") var bioStatus: String = "",
    @get:PropertyName("profilePicUrl") @set:PropertyName("profilePicUrl") var profilePicUrl: String = "",
    @get:PropertyName("plenxoId") @set:PropertyName("plenxoId") var plenxoId: String = "",
    @get:PropertyName("activeProfileRing") @set:PropertyName("activeProfileRing") var activeProfileRing: String? = null,
    @get:PropertyName("profileRingId") @set:PropertyName("profileRingId") var profileRingId: String = "none",
    @get:PropertyName("profileRing") @set:PropertyName("profileRing") var profileRing: String? = null,
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis()
) {
    val effectiveRingId: String
        get() = activeProfileRing?.takeIf { it.isNotBlank() && it != "none" && it != "NONE" }
            ?: profileRingId.takeIf { it.isNotBlank() && it != "none" && it != "NONE" }
            ?: profileRing?.takeIf { it.isNotBlank() && it != "none" && it != "NONE" }
            ?: "none"

    val resolvedDisplayName: String
        get() = displayName.takeIf { it.isNotBlank() && it != "User" }
            ?: name.takeIf { it.isNotBlank() && it != "User" }
            ?: displayName.ifBlank { name }.ifBlank { "User" }

    val resolvedBio: String
        get() = bio.takeIf { it.isNotBlank() }
            ?: statusMessage.takeIf { it.isNotBlank() }
            ?: bioStatus.takeIf { it.isNotBlank() }
            ?: ""

    @PropertyName("biographyStatus")
    fun setBiographyStatus(b: String) {
        if (bio.isBlank()) {
            bio = b
        }
    }
}

/**
 * Generates a strictly 6-digit numeric Plenxo ID in range 100000..999999
 * formatted with the fixed 'PX-' prefix (e.g., PX-102938).
 */
fun generateUniquePlenxoId(): String {
    val numericCode = Random.nextInt(100000, 1000000).toString()
    return "PX-$numericCode"
}

/**
 * Single authoritative primitive that generates a candidate 6-digit Plenxo ID (PX-XXXXXX)
 * and verifies its uniqueness in Firestore. Called strictly via [getOrCreatePermanentPlenxoId].
 */
suspend fun generateUniqueNumericPlenxoId(
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
): String {
    val fallbackCode = Random.nextInt(100000, 1000000).toString()
    val fallbackPxId = "PX-$fallbackCode"
    return try {
        kotlinx.coroutines.withTimeoutOrNull(5000L) {
            var attempts = 0
            while (attempts < 5) {
                val numericCode = Random.nextInt(100000, 1000000).toString()
                val candidatePxId = "PX-$numericCode"

                try {
                    val lookupDoc = firestore.collection("user_lookup")
                        .document(candidatePxId)
                        .get()
                        .await()

                    if (!lookupDoc.exists()) {
                        return@withTimeoutOrNull candidatePxId
                    }
                } catch (e: Exception) {
                    Log.e("UserModels", "Error verifying Plenxo ID uniqueness: ${e.message}")
                    return@withTimeoutOrNull candidatePxId
                }
                attempts++
            }
            fallbackPxId
        } ?: fallbackPxId
    } catch (e: Exception) {
        fallbackPxId
    }
}

/**
 * Single, authoritative entry point for getting or creating a user's permanent Plenxo ID.
 *
 * ARCHITECTURAL SPECIFICATION:
 * «ONE USER = ONE FIREBASE AUTH UID = ONE FIRESTORE USER DOCUMENT = ONE PERMANENT PLENXO ID.»
 *
 * Priority order:
 * 1. Firebase Auth UID
 * 2. Firestore /users/{uid} -> existing plenxoId
 * 3. Return existing ID (NEVER replace or regenerate)
 * 4. Only if Firestore document confirmed absent/empty: generate atomic unique PX-XXXXXX and save.
 */
suspend fun getOrCreatePermanentPlenxoId(
    uid: String,
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
): String {
    require(uid.isNotBlank()) { "User UID cannot be blank when resolving Plenxo ID." }

    val userDocRef = firestore.collection("users").document(uid)

    val existingSnap = try {
        kotlinx.coroutines.withTimeoutOrNull(3000L) {
            userDocRef.get().await()
        }
    } catch (e: Exception) {
        Log.w("PlenxoIdResolver", "Warning reading Plenxo ID for $uid: ${e.message}")
        null
    }

    val existingPxId = existingSnap?.getString("plenxoId") ?: existingSnap?.getString("userCode")

    val finalPxId = com.example.repository.FirestoreUserBootstrapper.resolveAuthoritativePlenxoId(
        uid = uid,
        existingPxId = existingPxId,
        firestore = firestore
    )

    try {
        val appCtx = com.example.PlenxoApplication.instance
        val currentLocal = com.example.util.SessionManager.getUserProfileLocally(appCtx)
        com.example.util.SessionManager.saveUserProfileLocally(
            appCtx,
            plenxoId = finalPxId,
            displayName = currentLocal.displayName,
            bio = currentLocal.bio,
            profilePicUrl = currentLocal.profilePicUrl,
            dob = currentLocal.dob,
            gender = currentLocal.gender,
            age = currentLocal.age
        )
    } catch (_: Exception) {}

    Log.d("PlenxoIdResolver", "Resolved permanent Plenxo ID: $finalPxId for UID: $uid")
    return finalPxId
}

/**
 * Backward compatibility alias for [getOrCreatePermanentPlenxoId].
 */
suspend fun resolveOrCreatePlenxoId(
    uid: String,
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
): String = getOrCreatePermanentPlenxoId(uid, firestore)

@Keep
@Serializable
@Immutable
data class UserProfile(
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("email") @set:PropertyName("email") var email: String = "",
    @get:PropertyName("displayName") @set:PropertyName("displayName") var displayName: String = "",
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("userCode") @set:PropertyName("userCode") var userCode: String = "",
    @get:PropertyName("profilePicUrl") @set:PropertyName("profilePicUrl") var profilePicUrl: String = "",
    @get:PropertyName("avatarUrl") @set:PropertyName("avatarUrl") var avatarUrl: String = "",
    @get:PropertyName("statusMessage") @set:PropertyName("statusMessage") var statusMessage: String = "",
    @get:PropertyName("bio") @set:PropertyName("bio") var bio: String = "",
    @get:PropertyName("bioStatus") @set:PropertyName("bioStatus") var bioStatus: String = "",
    @get:PropertyName("activeProfileRing") @set:PropertyName("activeProfileRing") var activeProfileRing: String? = null,
    @get:PropertyName("selectedRingId") @set:PropertyName("selectedRingId") var selectedRingId: String = "NONE",
    @get:PropertyName("profileRingId") @set:PropertyName("profileRingId") var profileRingId: String = "none",
    @get:PropertyName("profileRing") @set:PropertyName("profileRing") var profileRing: String? = null,
    @get:PropertyName("selectedFontId") @set:PropertyName("selectedFontId") var selectedFontId: String = "DEFAULT",
    @get:PropertyName("publicKey") @set:PropertyName("publicKey") var publicKey: String = "",
    @get:PropertyName("phoneNumber") @set:PropertyName("phoneNumber") var phoneNumber: String = "",
    @get:PropertyName("plenxoId") @set:PropertyName("plenxoId") var plenxoId: String = "",
    @get:PropertyName("securityData") @set:PropertyName("securityData") var securityData: SecurityData = SecurityData(),
    @get:PropertyName("lastSeenTimestamp") @set:PropertyName("lastSeenTimestamp") var lastSeenTimestamp: Long = 0L,
    @get:PropertyName("lastLoginTimestamp") @set:PropertyName("lastLoginTimestamp") var lastLoginTimestamp: Long = 0L,
    @get:PropertyName("termsAccepted") @set:PropertyName("termsAccepted") var termsAccepted: Boolean = false,
    @get:PropertyName("termsAcceptedAt") @set:PropertyName("termsAcceptedAt") var termsAcceptedAt: String = "",
    @get:PropertyName("isProfileCompleted") @set:PropertyName("isProfileCompleted") var isProfileCompleted: Boolean = false,
    @get:PropertyName("leagueData") @set:PropertyName("leagueData") var leagueData: LeagueData = LeagueData()
) {
    val effectiveRingId: String
        get() = activeProfileRing?.takeIf { it.isNotBlank() && it != "none" && it != "NONE" }
            ?: profileRingId.takeIf { it.isNotBlank() && it != "none" && it != "NONE" }
            ?: profileRing?.takeIf { it.isNotBlank() && it != "none" && it != "NONE" }
            ?: selectedRingId.takeIf { it.isNotBlank() && it != "none" && it != "NONE" }
            ?: "none"

    val resolvedDisplayName: String
        get() = displayName.takeIf { it.isNotBlank() && it != "User" }
            ?: name.takeIf { it.isNotBlank() && it != "User" }
            ?: displayName.ifBlank { name }.ifBlank { "User" }

    val resolvedBio: String
        get() = bio.takeIf { it.isNotBlank() }
            ?: statusMessage.takeIf { it.isNotBlank() }
            ?: bioStatus.takeIf { it.isNotBlank() }
            ?: ""

    @PropertyName("biographyStatus")
    fun setBiographyStatus(b: String) {
        if (bio.isBlank()) {
            bio = b
        }
        if (statusMessage.isBlank()) {
            statusMessage = b
        }
    }

    @PropertyName("avatar_url")
    fun setAvatarUrlSnake(url: String) {
        if (avatarUrl.isBlank() && url.isNotBlank()) {
            avatarUrl = url
        }
        if (profilePicUrl.isBlank() && url.isNotBlank()) {
            profilePicUrl = url
        }
    }

    @PropertyName("photoUrl")
    fun setPhotoUrl(url: String) {
        if (avatarUrl.isBlank() && url.isNotBlank()) {
            avatarUrl = url
        }
        if (profilePicUrl.isBlank() && url.isNotBlank()) {
            profilePicUrl = url
        }
    }

    @PropertyName("profilePic")
    fun setProfilePic(url: String) {
        if (avatarUrl.isBlank() && url.isNotBlank()) {
            avatarUrl = url
        }
        if (profilePicUrl.isBlank() && url.isNotBlank()) {
            profilePicUrl = url
        }
    }

    @PropertyName("profileUrl")
    fun setProfileUrl(url: String) {
        if (avatarUrl.isBlank() && url.isNotBlank()) {
            avatarUrl = url
        }
        if (profilePicUrl.isBlank() && url.isNotBlank()) {
            profilePicUrl = url
        }
    }

    val effectiveAvatarUrl: String
        get() = avatarUrl.takeIf { it.isNotBlank() }
            ?: profilePicUrl.takeIf { it.isNotBlank() }
            ?: ""
}

fun UserProfile.toUserModel(): UserModel = UserModel(
    uid = uid.ifEmpty { id },
    displayName = resolvedDisplayName,
    name = resolvedDisplayName,
    email = email,
    bio = resolvedBio,
    statusMessage = resolvedBio,
    profilePicUrl = profilePicUrl,
    plenxoId = plenxoId,
    profileRingId = profileRingId.ifEmpty { selectedRingId },
    createdAt = lastLoginTimestamp
)

fun UserModel.toUserProfile(): UserProfile = UserProfile(
    uid = uid,
    id = uid,
    displayName = resolvedDisplayName,
    name = resolvedDisplayName,
    email = email,
    bio = resolvedBio,
    statusMessage = resolvedBio,
    profilePicUrl = profilePicUrl,
    plenxoId = plenxoId,
    profileRingId = profileRingId,
    selectedRingId = profileRingId,
    userCode = plenxoId
)

@Keep
@Serializable
data class LeagueData(
    val currentCrown: String = "Bronze",
    val activeAccumulatedSeconds: Int = 0,
    val unlockedRings: List<String> = listOf("bronze_ring_1"),
    val unlockedFonts: List<String> = listOf("DEFAULT"),
    val lastClaimedTimestamp: String = ""
)

@Keep
@Serializable
data class SecurityData(
    val failedLoginCount: Int = 0,
    val lockoutUntil: Long = 0L,
    val totalLifetimeFails: Int = 0
)

@Keep
@Serializable
data class ActiveSession(
    val sessionId: String = "",
    val deviceName: String = "",
    val deviceModel: String = "",
    val operatingSystem: String = "",
    val ipAddress: String = "",
    val timestamp: Long = 0L,
    val lastActiveTime: Long = 0L,
    val isCurrentDevice: Boolean = false
)

@Keep
@Serializable
@Immutable
data class ConnectedFriend(
    val uid: String = "",
    val displayName: String = "",
    val bio: String = "",
    val profilePicUrl: String = "",
    val plenxoId: String = "",
    val email: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Keep
@Serializable
@Immutable
data class FriendRequest(
    val requestId: String = "",
    val senderUid: String = "",
    val senderPlenxoId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String = "",
    val receiverUid: String = "",
    val receiverPlenxoId: String = "",
    val status: String = "PENDING",
    val timestamp: Long = 0L,
    val senderPhone: String = "",
    val id: String = requestId,
    val senderId: String = senderUid,
    val receiverId: String = receiverUid,
    val senderProfilePic: String = senderPhotoUrl
)

@Keep
@Serializable
data class MessagePayload(
    val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val messageText: String = "",
    val messageType: String = "TEXT",
    val mediaUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val replyToMessageId: String? = null,
    val isEdited: Boolean = false,
    val status: String = "SENT",
    val expiresAt: Long? = null,
    val senderActiveFontId: String = "DEFAULT"
)

@Keep
@Serializable
data class CallSession(
    val callId: String = "",
    val callerId: String = "",
    val receiverId: String = "",
    val state: String = "",
    val callType: String = "AUDIO", // AUDIO, VIDEO
    val timestamp: Long = 0L
)
