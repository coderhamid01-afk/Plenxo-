package com.example.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * Result data class for user bootstrapping in Firestore.
 */
data class BootstrapResult(
    val success: Boolean,
    val uid: String,
    val plenxoId: String,
    val isNewProfile: Boolean,
    val usersDataSuccess: Boolean = false,
    val presenceSuccess: Boolean = false,
    val errorMessage: String? = null,
    val firestoreErrorCode: FirebaseFirestoreException.Code? = null,
    val rawException: Throwable? = null
)

/**
 * Single, authoritative Firestore User Bootstrapper for Plenxo.
 *
 * Implements strict prioritized, idempotent initialization:
 * PRIORITY 1: users/{uid} (Required - authoritative profile document)
 * PRIORITY 2: users_data/{uid} (Supporting parallel data document)
 * PRIORITY 3: presence/{uid} & users_presence/{uid} (Real-time presence tracking)
 * PRIORITY 4: _meta/initialized (Harmless bootstrap marker)
 */
object FirestoreUserBootstrapper {
    private const val TAG = "PLENXO_FIRESTORE"
    private const val TAG_PROFILE = "PLENXO_PROFILE"

    /**
     * Resolves an authoritative, permanent Plenxo ID in format PX-XXXXXX (6 digits: 100000..999999).
     * Strictly adheres to ONE Firebase Auth UID = ONE Firestore users/{uid} = ONE permanent Plenxo ID.
     */
    suspend fun resolveAuthoritativePlenxoId(
        uid: String,
        existingPxId: String? = null,
        requestedPxId: String? = null,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    ): String {
        // 1. If an existing PX ID is already present on the document, PRESERVE IT ALWAYS
        val cleanExisting = existingPxId?.trim()?.removePrefix("@")?.removePrefix("#")
        if (!cleanExisting.isNullOrBlank()) {
            if (cleanExisting.startsWith("PX-", ignoreCase = true)) {
                val numPart = cleanExisting.substring(3).trim()
                if (numPart.length == 6 && numPart.all { it.isDigit() }) {
                    return "PX-$numPart"
                }
            } else if (cleanExisting.length == 6 && cleanExisting.all { it.isDigit() }) {
                return "PX-$cleanExisting"
            }
        }

        // 2. If caller passed a valid PX ID, validate format
        val cleanRequested = requestedPxId?.trim()?.removePrefix("@")?.removePrefix("#")
        if (!cleanRequested.isNullOrBlank()) {
            if (cleanRequested.startsWith("PX-", ignoreCase = true)) {
                val numPart = cleanRequested.substring(3).trim()
                if (numPart.length == 6 && numPart.all { it.isDigit() }) {
                    return "PX-$numPart"
                }
            } else if (cleanRequested.length == 6 && cleanRequested.all { it.isDigit() }) {
                return "PX-$cleanRequested"
            }
        }

        // 3. Deterministic 6-digit generation from UID hash code
        // Guarantees that the same UID always produces the exact same PX-ID even if interrupted/offline
        val deterministicCode = (kotlin.math.abs(uid.hashCode()) % 900000 + 100000).toString()
        val deterministicPxId = "PX-$deterministicCode"

        // 4. Quick uniqueness check with short timeout
        return try {
            val isTaken = withTimeoutOrNull(2500L) {
                try {
                    val querySnap = firestore.collection("users")
                        .whereEqualTo("plenxoId", deterministicPxId)
                        .limit(1)
                        .get()
                        .await()
                    if (!querySnap.isEmpty) {
                        val existingDoc = querySnap.documents[0]
                        existingDoc.id != uid
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Uniqueness check skipped due to network/error: ${e.message}")
                    false
                }
            } ?: false

            if (isTaken) {
                // Generate a random unique 6-digit code
                var candidate = deterministicPxId
                for (i in 1..5) {
                    val randomCode = Random.nextInt(100000, 1000000).toString()
                    candidate = "PX-$randomCode"
                    val query = firestore.collection("users")
                        .whereEqualTo("plenxoId", candidate)
                        .limit(1)
                        .get()
                        .await()
                    if (query.isEmpty) break
                }
                candidate
            } else {
                deterministicPxId
            }
        } catch (e: Exception) {
            Log.w(TAG, "Using deterministic Plenxo ID fallback ($deterministicPxId): ${e.message}")
            deterministicPxId
        }
    }

    /**
     * Initializes or repairs user collections in Firestore.
     * Guaranteed to be idempotent: never overwrites existing valid profile data.
     */
    suspend fun initializeUser(
        uid: String,
        email: String,
        name: String? = null,
        plenxoId: String? = null,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    ): BootstrapResult = withContext(Dispatchers.IO) {
        if (uid.isBlank()) {
            Log.e(TAG, "Cannot initialize user with blank UID")
            return@withContext BootstrapResult(
                success = false,
                uid = uid,
                plenxoId = "",
                isNewProfile = false,
                errorMessage = "UID cannot be blank"
            )
        }

        Log.d(TAG, "FIRESTORE USER CREATE START: UID=$uid, Email=$email")

        val userDocRef = firestore.collection("users").document(uid)

        // 1. Read existing document safely (quick check, 3500ms max)
        val existingSnap = try {
            withTimeoutOrNull(3500L) {
                userDocRef.get().await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Existing user doc read warning for $uid: ${e.message}")
            null
        }

        val docExists = existingSnap != null && existingSnap.exists()

        // Extract existing fields to guarantee idempotency and avoid overwriting user data
        val existingPxId = existingSnap?.getString("plenxoId") ?: existingSnap?.getString("userCode")
        val existingName = existingSnap?.getString("displayName")?.takeIf { it.isNotBlank() && it != "User" }
            ?: existingSnap?.getString("name")?.takeIf { it.isNotBlank() && it != "User" }
            ?: existingSnap?.getString("current_name")?.takeIf { it.isNotBlank() && it != "User" }
        val existingBio = existingSnap?.getString("bio")?.takeIf { it.isNotBlank() }
            ?: existingSnap?.getString("statusMessage")?.takeIf { it.isNotBlank() }
        val existingPic = existingSnap?.getString("profilePicUrl")?.takeIf { it.isNotBlank() }
            ?: existingSnap?.getString("avatar_url")?.takeIf { it.isNotBlank() }
            ?: existingSnap?.getString("photoUrl")?.takeIf { it.isNotBlank() }
        val isProfileCompleted = existingSnap?.getBoolean("isProfileCompleted")
            ?: existingSnap?.getBoolean("is_profile_completed")
            ?: existingSnap?.getBoolean("isProfileSetupCompleted")
            ?: existingSnap?.getBoolean("profileSetupCompleted")
            ?: (!existingName.isNullOrBlank() && existingName != "User")

        val existingDob = existingSnap?.getString("dob") ?: existingSnap?.getString("dateOfBirth") ?: ""
        val existingGender = existingSnap?.getString("gender") ?: ""
        val existingAge = existingSnap?.get("age")?.toString() ?: ""

        val now = System.currentTimeMillis()
        val createdAt = existingSnap?.getLong("createdAt") ?: now

        // Resolve Plenxo ID using authoritative single source
        val finalPxId = resolveAuthoritativePlenxoId(
            uid = uid,
            existingPxId = existingPxId,
            requestedPxId = plenxoId,
            firestore = firestore
        )
        val numericCode = finalPxId.removePrefix("PX-")

        // Resolve display name: never overwrite custom name with email prefix
        val resolvedName = existingName
            ?: name?.takeIf { it.isNotBlank() && it != "User" }
            ?: (if (email.contains("@")) email.substringBefore("@") else "User").ifBlank { "User" }

        val resolvedBio = existingBio ?: "Hey there! I am using Plenxo."
        val resolvedPic = existingPic ?: ""

        // Comprehensive user document map with all supported schema aliases
        val userMap = hashMapOf<String, Any?>(
            "uid" to uid,
            "id" to uid,
            "email" to email,
            "displayName" to resolvedName,
            "display_name" to resolvedName,
            "name" to resolvedName,
            "current_name" to resolvedName,
            "plenxoId" to finalPxId,
            "plenxo_id" to finalPxId,
            "userCode" to numericCode,
            "user_code" to numericCode,
            "px_id" to finalPxId,
            "px_code" to numericCode,
            "bio" to resolvedBio,
            "statusMessage" to resolvedBio,
            "profilePicUrl" to resolvedPic,
            "avatar_url" to resolvedPic,
            "photoUrl" to resolvedPic,
            "profileUrl" to resolvedPic,
            "status" to "online",
            "lastSeen" to now,
            "createdAt" to createdAt,
            "updatedAt" to now,
            "isProfileCompleted" to isProfileCompleted,
            "is_profile_completed" to isProfileCompleted,
            "isProfileSetupCompleted" to isProfileCompleted,
            "profileSetupCompleted" to isProfileCompleted,
            "isEmailVerified" to (existingSnap?.getBoolean("isEmailVerified") ?: false),
            "dob" to existingDob,
            "gender" to existingGender,
            "age" to existingAge
        )

        // ==========================================
        // PRIORITY 1: users/{uid} PRIMARY CREATION
        // ==========================================
        try {
            Log.d(TAG, "Writing users/$uid with Plenxo ID: $finalPxId")
            val writeSuccess = withTimeoutOrNull(15000L) {
                userDocRef.set(userMap, SetOptions.merge()).await()
                true
            } ?: false

            if (!writeSuccess) {
                val timeoutMsg = "Database write to users/$uid timed out after 15 seconds. Please check your network connection."
                Log.e(TAG, "FIRESTORE USER CREATE FAILURE: $timeoutMsg")
                return@withContext BootstrapResult(
                    success = false,
                    uid = uid,
                    plenxoId = finalPxId,
                    isNewProfile = !docExists,
                    errorMessage = timeoutMsg
                )
            }

            Log.d(TAG, "FIRESTORE USER CREATE SUCCESS: UID=$uid, Plenxo ID=$finalPxId")
            Log.d(TAG_PROFILE, "Profile bootstrapped successfully: UID=$uid, Path=users/$uid, Plenxo ID=$finalPxId")
        } catch (fsEx: FirebaseFirestoreException) {
            val codeStr = fsEx.code.name
            val errorDetails = """
                [Firestore Signup]
                UID = $uid
                Path = users/$uid
                Operation = CREATE_USER_PROFILE
                Code = $codeStr
                Message = ${fsEx.message}
            """.trimIndent()
            Log.e(TAG, "FIRESTORE USER CREATE FAILURE:\n$errorDetails", fsEx)

            val humanMsg = when (fsEx.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Permission denied when initializing user profile. Please verify your account credentials."
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "Firestore service is currently unavailable. Please check your internet connection."
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "Firestore request timed out. Please check your network connection."
                else ->
                    "Firestore error [$codeStr]: ${fsEx.localizedMessage ?: fsEx.message}"
            }

            return@withContext BootstrapResult(
                success = false,
                uid = uid,
                plenxoId = finalPxId,
                isNewProfile = !docExists,
                errorMessage = humanMsg,
                firestoreErrorCode = fsEx.code,
                rawException = fsEx
            )
        } catch (e: Exception) {
            Log.e(TAG, "FIRESTORE USER CREATE FAILURE: UID=$uid, Message=${e.message}", e)
            return@withContext BootstrapResult(
                success = false,
                uid = uid,
                plenxoId = finalPxId,
                isNewProfile = !docExists,
                errorMessage = e.localizedMessage ?: "Failed to write user profile to Firestore",
                rawException = e
            )
        }

        // ==========================================
        // PRIORITY 2: users_data/{uid}
        // ==========================================
        var usersDataSuccess = false
        try {
            withTimeoutOrNull(8000L) {
                firestore.collection("users_data").document(uid)
                    .set(userMap, SetOptions.merge())
                    .await()
            }
            usersDataSuccess = true
            Log.d(TAG, "USERS_DATA CREATE SUCCESS: UID=$uid")
        } catch (udEx: Exception) {
            Log.w(TAG, "USERS_DATA CREATE FAILURE (non-fatal): UID=$uid, Message=${udEx.message}")
        }

        // ==========================================
        // PRIORITY 3: presence/{uid} & users_presence/{uid}
        // ==========================================
        var presenceSuccess = false
        try {
            val presenceMap = mapOf(
                "user_id" to uid,
                "uid" to uid,
                "status" to "online",
                "state" to "online",
                "lastSeen" to now,
                "last_seen" to now,
                "updatedAt" to now
            )
            withTimeoutOrNull(8000L) {
                // Canonical presence collection
                firestore.collection("presence").document(uid)
                    .set(presenceMap, SetOptions.merge())
                    .await()

                // Backward-compatible users_presence collection
                try {
                    firestore.collection("users_presence").document(uid)
                        .set(presenceMap, SetOptions.merge())
                        .await()
                } catch (upEx: Exception) {
                    Log.w(TAG, "users_presence write note: ${upEx.message}")
                }
            }
            presenceSuccess = true
            Log.d(TAG, "PRESENCE CREATE SUCCESS: UID=$uid")
        } catch (pEx: Exception) {
            Log.w(TAG, "PRESENCE CREATE FAILURE (non-fatal): UID=$uid, Message=${pEx.message}")
        }

        // ==========================================
        // PRIORITY 4: Bootstrap Metadata Marker
        // ==========================================
        try {
            val metaMap = mapOf(
                "system" to "Plenxo",
                "initializedAt" to now,
                "version" to "1.0",
                "status" to "ready"
            )
            firestore.collection("_meta").document("initialized")
                .set(metaMap, SetOptions.merge())
        } catch (mEx: Exception) {
            Log.w(TAG, "Optional _meta write note: ${mEx.message}")
        }

        Log.d(
            TAG,
            "FINAL SIGNUP INITIALIZATION RESULT: Success for UID=$uid, Plenxo ID=$finalPxId, usersOk=true, usersDataOk=$usersDataSuccess, presenceOk=$presenceSuccess"
        )

        return@withContext BootstrapResult(
            success = true,
            uid = uid,
            plenxoId = finalPxId,
            isNewProfile = !docExists,
            usersDataSuccess = usersDataSuccess,
            presenceSuccess = presenceSuccess
        )
    }
}
