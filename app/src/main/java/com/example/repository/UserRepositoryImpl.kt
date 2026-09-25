package com.example.repository

import android.util.Log
import com.example.util.getDocumentServerFirst
import com.example.util.getQuerySnapshotServerFirst
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface UserRepository {
    suspend fun createUserProfile(uid: String, email: String, name: String? = null, plenxoId: String? = null): Boolean

    suspend fun syncUserDataOnAuth(
        uid: String,
        email: String,
        displayName: String? = null,
        photoUrl: String? = null,
        fcmToken: String? = null,
        status: String = "online"
    ): Boolean

    suspend fun updateUserProfile(uid: String, updates: Map<String, Any?>): Boolean
    suspend fun updateUserStatus(uid: String, status: String, lastSeen: Long = System.currentTimeMillis()): Boolean
    suspend fun updateFcmToken(uid: String, token: String): Boolean
    suspend fun updateActiveProfileRing(uid: String, selectedRingId: String): Boolean
    fun observeUserData(uid: String): Flow<Map<String, Any>?>
    suspend fun getUserData(uid: String): Map<String, Any>?

    /**
     * Strictly searches the Firestore 'users' collection by 'plenxoId' field ONLY.
     * Completely disables/removes email searching capability.
     */
    suspend fun searchUsersByPlenxoId(plenxoIdQuery: String): List<Map<String, Any>>

    /**
     * Repository function to search user by exact Plenxo ID using whereEqualTo("plenxoId", plenxoId).
     */
    suspend fun searchUserByPlenxoId(plenxoId: String): List<Map<String, Any>>

    /**
     * Resolves a Plenxo ID to its linked user email address for authentication purposes.
     */
    suspend fun getEmailByPlenxoId(plenxoId: String): String?

    /**
     * Fetches user document by Plenxo ID.
     */
    suspend fun getUserByPlenxoId(plenxoId: String): Map<String, Any>?
}

class UserRepositoryImpl : UserRepository {

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    override suspend fun createUserProfile(uid: String, email: String, name: String?, plenxoId: String?): Boolean {
        if (uid.isBlank()) return false
        Log.d("PlenxoUserRepository", "createUserProfile called for UID=$uid, email=$email, requestedPxId=$plenxoId")
        val result = FirestoreUserBootstrapper.initializeUser(
            uid = uid,
            email = email,
            name = name,
            plenxoId = plenxoId,
            firestore = firestore
        )
        if (!result.success) {
            Log.e("PlenxoUserRepository", "createUserProfile failed for UID=$uid: ${result.errorMessage}")
        } else {
            Log.d("PlenxoUserRepository", "createUserProfile succeeded for UID=$uid with Plenxo ID: ${result.plenxoId}")
        }
        return result.success
    }

    override suspend fun syncUserDataOnAuth(
        uid: String,
        email: String,
        displayName: String?,
        photoUrl: String?,
        fcmToken: String?,
        status: String
    ): Boolean {
        if (uid.isBlank()) return false

        val userDocRef = firestore.collection("users").document(uid)
        val userSnap = try {
            kotlinx.coroutines.withTimeoutOrNull(3500L) {
                userDocRef.get().await()
            }
        } catch (e: Exception) {
            Log.w("PlenxoUserRepository", "syncUserDataOnAuth read warning for $uid: ${e.message}")
            null
        }

        if (userSnap != null && userSnap.exists()) {
            Log.d("PlenxoUserRepository", "User $uid exists. Updating status/fcmToken while preserving profile.")
            try {
                val updates = mutableMapOf<String, Any>(
                    "status" to status,
                    "lastSeen" to System.currentTimeMillis()
                )
                if (!fcmToken.isNullOrBlank()) {
                    updates["fcmToken"] = fcmToken
                }
                userDocRef.update(updates).await()
            } catch (e: Exception) {
                Log.w("PlenxoUserRepository", "Non-profile update note: ${e.message}")
            }
            return true
        } else {
            return createUserProfile(uid, email, displayName, null)
        }
    }

    override suspend fun updateUserProfile(uid: String, updates: Map<String, Any?>): Boolean {
        if (uid.isBlank()) return false
        val mutableUpdates = updates.toMutableMap()
        mutableUpdates["updatedAt"] = FieldValue.serverTimestamp()

        return try {
            firestore.collection("users").document(uid)
                .set(mutableUpdates, SetOptions.merge())
                .await()

            // Also maintain user_lookup entry client-side for immediate discovery sync
            try {
                val existingPxId = (mutableUpdates["plenxoId"] as? String)
                    ?: (getUserData(uid)?.get("plenxoId") as? String)
                    ?: ""
                val cleanPxId = existingPxId.trim().removePrefix("@").removePrefix("#")
                val formattedPxId = if (cleanPxId.startsWith("PX-", ignoreCase = true)) {
                    "PX-${cleanPxId.substring(3).trim()}"
                } else if (cleanPxId.length == 6 && cleanPxId.all { it.isDigit() }) {
                    "PX-$cleanPxId"
                } else {
                    cleanPxId
                }

                if (formattedPxId.isNotBlank()) {
                    val lookupMap = mutableMapOf<String, Any>(
                        "plenxoId" to formattedPxId,
                        "uid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    (mutableUpdates["displayName"] ?: mutableUpdates["name"])?.let { lookupMap["displayName"] = it }
                    (mutableUpdates["profilePicUrl"] ?: mutableUpdates["photoUrl"])?.let { lookupMap["profilePicUrl"] = it }
                    (mutableUpdates["bio"] ?: mutableUpdates["statusMessage"])?.let { lookupMap["bio"] = it }
                    (mutableUpdates["profileRingId"] ?: mutableUpdates["selectedRingId"])?.let { lookupMap["profileRingId"] = it }

                    firestore.collection("user_lookup").document(formattedPxId).set(lookupMap, SetOptions.merge()).await()
                }
            } catch (lkEx: Exception) {
                Log.w("UserRepositoryImpl", "user_lookup update note for $uid: ${lkEx.message}")
            }

            Log.d("UserRepositoryImpl", "Updated profile for user $uid")
            true
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Failed to update user profile for $uid: ${e.message}", e)
            false
        }
    }

    override suspend fun updateUserStatus(uid: String, status: String, lastSeen: Long): Boolean {
        if (uid.isBlank()) return false
        val updates = mapOf(
            "status" to status,
            "lastSeen" to lastSeen,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return updateUserProfile(uid, updates)
    }

    override suspend fun updateFcmToken(uid: String, token: String): Boolean {
        if (uid.isBlank() || token.isBlank()) return false
        val updates = mapOf(
            "fcmToken" to token,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return updateUserProfile(uid, updates)
    }

    override suspend fun updateActiveProfileRing(uid: String, selectedRingId: String): Boolean {
        if (uid.isBlank()) return false
        val updates = mapOf(
            "activeProfileRing" to selectedRingId,
            "profileRingId" to selectedRingId,
            "profileRing" to selectedRingId,
            "selectedRingId" to selectedRingId,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return updateUserProfile(uid, updates)
    }

    override fun observeUserData(uid: String): Flow<Map<String, Any>?> = callbackFlow {
        if (uid.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val docRef = firestore.collection("users").document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("UserRepositoryImpl", "Error observing user data: ${error.message}")
                trySend(null)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                trySend(snapshot.data)
            } else {
                trySend(null)
            }
        }

        awaitClose { listener.remove() }
    }

    override suspend fun getUserData(uid: String): Map<String, Any>? {
        if (uid.isBlank()) return null
        return try {
            val docRef = firestore.collection("users").document(uid)
            val snapshot = getDocumentServerFirst(docRef)
            if (snapshot.exists()) snapshot.data else null
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Failed to fetch user data for $uid: ${e.message}")
            null
        }
    }

    /**
     * STRICT DIRECT DOCUMENT LOOKUP ONLY ON /user_lookup/{canonicalPxId}.
     * Completely eliminates collection queries, list enumeration, and private field exposure.
     */
    override suspend fun getUserByPlenxoId(plenxoId: String): Map<String, Any>? {
        val cleaned = plenxoId.trim().removePrefix("@").removePrefix("#").trim()
        if (cleaned.isBlank()) return null

        val canonicalPxId = if (cleaned.startsWith("PX-", ignoreCase = true)) {
            "PX-${cleaned.substring(3).trim()}"
        } else if (cleaned.length == 6 && cleaned.all { it.isDigit() }) {
            "PX-$cleaned"
        } else {
            cleaned
        }

        return try {
            val docRef = firestore.collection("user_lookup").document(canonicalPxId)
            val snapshot = getDocumentServerFirst(docRef)
            if (snapshot.exists()) {
                val data = snapshot.data?.toMutableMap() ?: mutableMapOf()
                data["docId"] = (data["uid"] as? String) ?: snapshot.id
                data
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Failed direct document lookup for $canonicalPxId: ${e.message}", e)
            null
        }
    }

    override suspend fun searchUsersByPlenxoId(plenxoIdQuery: String): List<Map<String, Any>> {
        val userMap = getUserByPlenxoId(plenxoIdQuery)
        return if (userMap != null) listOf(userMap) else emptyList()
    }

    override suspend fun searchUserByPlenxoId(plenxoId: String): List<Map<String, Any>> {
        val userMap = getUserByPlenxoId(plenxoId)
        return if (userMap != null) listOf(userMap) else emptyList()
    }

    override suspend fun getEmailByPlenxoId(plenxoId: String): String? {
        val userMap = getUserByPlenxoId(plenxoId)
        return userMap?.get("email") as? String
    }
}
