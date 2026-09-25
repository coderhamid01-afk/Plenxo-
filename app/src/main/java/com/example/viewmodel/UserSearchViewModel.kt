package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.UserModel
import com.example.repository.UserRepository
import com.example.repository.UserRepositoryImpl
import com.example.util.getQuerySnapshotServerFirst
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class UserSearchViewModel @JvmOverloads constructor(
    application: Application,
    private val userRepository: UserRepository = UserRepositoryImpl()
) : AndroidViewModel(application) {

    companion object {
        private const val TAG_SEARCH = "PLENXO_SEARCH"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val searchResults: StateFlow<List<Map<String, Any>>> = _searchResults.asStateFlow()

    private val _userModelResults = MutableStateFlow<List<UserModel>>(emptyList())
    val userModelResults: StateFlow<List<UserModel>> = _userModelResults.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _allUsers = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val allUsers: StateFlow<List<Map<String, Any>>> = _allUsers.asStateFlow()

    private val _allUserModels = MutableStateFlow<List<UserModel>>(emptyList())
    val allUserModels: StateFlow<List<UserModel>> = _allUserModels.asStateFlow()

    /**
     * Normalizes user input into canonical format: PX-123456 (PX- followed by exactly 6 digits).
     * Accepts variations like: PX-123456, px-123456, 123456, @PX-123456, #PX-123456.
     * Returns null for invalid formats (e.g. PX-12345, ABC-123456, PX-ABCDEF).
     */
    fun normalizePlenxoId(input: String): String? {
        val clean = input.trim()
            .removePrefix("@")
            .removePrefix("#")
            .trim()
        if (clean.isBlank()) return null

        val pxRegex = Regex("^(?i)PX-(\\d{6})$")
        val pxMatch = pxRegex.matchEntire(clean)
        if (pxMatch != null) {
            val digits = pxMatch.groupValues[1]
            return "PX-$digits"
        }

        val digitsRegex = Regex("^(\\d{6})$")
        val digitsMatch = digitsRegex.matchEntire(clean)
        if (digitsMatch != null) {
            val digits = digitsMatch.groupValues[1]
            return "PX-$digits"
        }

        return null
    }

    /**
     * No-op: Automatic user enumeration has been completely removed for privacy compliance.
     * Users are no longer auto-loaded or cached client-side.
     */
    fun loadInitialUsers() {
        // Intentionally empty. No initial user loading allowed.
        _searchResults.value = emptyList()
        _userModelResults.value = emptyList()
    }

    /**
     * Updates search query input.
     * Searches strictly when a valid 6-digit Plenxo ID is supplied.
     * Clears results when query is empty or incomplete.
     */
    fun updateSearchQuery(query: String) {
        val q = query.take(30)
        _searchQuery.value = q
        _searchError.value = null

        if (q.isBlank()) {
            clearSearch()
            return
        }

        val normalized = normalizePlenxoId(q)
        if (normalized != null) {
            executeSearch()
        } else {
            // Clear results for incomplete or invalid query
            _searchResults.value = emptyList()
            _userModelResults.value = emptyList()
            _hasSearched.value = false
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _userModelResults.value = emptyList()
        _hasSearched.value = false
        _searchError.value = null
    }

    /**
     * Direct explicit search strictly by permanent Plenxo ID (`plenxoId`) using limit(1).
     */
    fun executeSearch() {
        val rawInput = _searchQuery.value.trim().removePrefix("@").removePrefix("#").trim()
        if (rawInput.isBlank()) {
            clearSearch()
            return
        }

        val normalized = normalizePlenxoId(rawInput)
        if (normalized == null) {
            _hasSearched.value = true
            _searchError.value = "Enter a valid 6-digit Plenxo ID"
            _searchResults.value = emptyList()
            _userModelResults.value = emptyList()
            return
        }

        _isSearching.value = true
        _hasSearched.value = true
        _searchError.value = null

        viewModelScope.launch {
            try {
                val currentAuthUid = auth.currentUser?.uid ?: ""

                // Self search prevention
                val currentUserData = if (currentAuthUid.isNotBlank()) userRepository.getUserData(currentAuthUid) else null
                val myPlenxoId = ((currentUserData?.get("plenxoId") as? String) ?: "").trim()
                val myNormalized = if (myPlenxoId.isNotBlank()) normalizePlenxoId(myPlenxoId) else null

                if (myNormalized != null && myNormalized.equals(normalized, ignoreCase = true)) {
                    _searchError.value = "You cannot add yourself."
                    _searchResults.value = emptyList()
                    _userModelResults.value = emptyList()
                    _isSearching.value = false
                    return@launch
                }

                // Query Firestore strictly by plenxoId field with limit(1)
                val searchKeys = listOf(normalized, normalized.lowercase())
                val list = mutableListOf<Map<String, Any>>()
                val modelList = mutableListOf<UserModel>()

                for (key in searchKeys) {
                    val query = firestore.collection("users")
                        .whereEqualTo("plenxoId", key)
                        .limit(1)

                    val snapshot = try {
                        getQuerySnapshotServerFirst(query, timeoutMs = 5000L)
                    } catch (e: Exception) {
                        Log.w("UserSearchViewModel", "Search query failed for key $key: ${e.message}")
                        null
                    } ?: continue

                    if (!snapshot.isEmpty) {
                        snapshot.documents.forEach { doc ->
                            val uid = doc.id
                            if (currentAuthUid.isNotBlank() && uid == currentAuthUid) {
                                _searchError.value = "You cannot add yourself."
                                return@forEach
                            }

                            val data = doc.data?.toMutableMap() ?: mutableMapOf()
                            data["docId"] = uid
                            data["uid"] = (data["uid"] as? String) ?: uid

                            val dName = doc.getString("displayName")?.takeIf { it.isNotBlank() && it != "User" }
                                ?: doc.getString("name")?.takeIf { it.isNotBlank() && it != "User" }
                                ?: doc.getString("display_name")?.takeIf { it.isNotBlank() && it != "User" }
                                ?: doc.getString("fullName")?.takeIf { it.isNotBlank() && it != "User" }
                                ?: doc.getString("displayName")?.takeIf { it.isNotBlank() }
                                ?: doc.getString("name")?.takeIf { it.isNotBlank() }
                                ?: "Plenxo User"
                            data["displayName"] = dName
                            data["name"] = dName

                            val pId = doc.getString("plenxoId")?.takeIf { it.isNotBlank() }
                                ?: doc.getString("userCode")?.takeIf { it.isNotBlank() }
                                ?: normalized
                            data["plenxoId"] = pId

                            val bio = doc.getString("bio")?.takeIf { it.isNotBlank() }
                                ?: doc.getString("statusMessage")?.takeIf { it.isNotBlank() }
                                ?: doc.getString("bioStatus")?.takeIf { it.isNotBlank() }
                                ?: ""
                            data["bio"] = bio
                            data["statusMessage"] = bio

                            val pic = doc.getString("profilePicUrl")?.takeIf { it.isNotBlank() }
                                ?: doc.getString("avatar_url")?.takeIf { it.isNotBlank() }
                                ?: doc.getString("photoUrl")?.takeIf { it.isNotBlank() }
                                ?: ""
                            data["profilePicUrl"] = pic

                            val email = doc.getString("email") ?: ""
                            data["email"] = email

                            val ringId = doc.getString("profileRingId")
                                ?: doc.getString("selectedRingId")
                                ?: "none"
                            data["profileRingId"] = ringId

                            list.add(data)
                            modelList.add(
                                UserModel(
                                    uid = uid,
                                    displayName = dName,
                                    name = dName,
                                    email = email,
                                    bio = bio,
                                    statusMessage = bio,
                                    profilePicUrl = pic,
                                    plenxoId = pId,
                                    profileRingId = ringId
                                )
                            )
                        }
                        if (list.isNotEmpty()) break
                    }
                }

                if (list.isEmpty() && _searchError.value == null) {
                    val repoResults = userRepository.searchUsersByPlenxoId(normalized)
                    if (repoResults.isNotEmpty()) {
                        repoResults.forEach { r ->
                            val uid = (r["uid"] as? String) ?: (r["docId"] as? String) ?: ""
                            if (currentAuthUid.isBlank() || uid != currentAuthUid) {
                                list.add(r)
                            } else {
                                _searchError.value = "You cannot add yourself."
                            }
                        }
                    }
                }

                val distinctResults = list.distinctBy { (it["uid"] as? String) ?: (it["id"] as? String) ?: it.hashCode().toString() }
                val distinctModels = modelList.distinctBy { it.uid }

                _searchResults.value = distinctResults
                _userModelResults.value = distinctModels
                Log.d(TAG_SEARCH, "Operation: SEARCH_BY_PX_ID, query: $normalized, matches: ${distinctResults.size}, status: SUCCESS")
            } catch (e: Exception) {
                Log.e(TAG_SEARCH, "Operation: SEARCH_BY_PX_ID, query: $normalized, status: FAILURE, error: ${e.message}", e)
                _searchError.value = "Search failed: ${e.localizedMessage}"
            } finally {
                _isSearching.value = false
            }
        }
    }
}
