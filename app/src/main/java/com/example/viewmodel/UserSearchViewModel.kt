package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.UserModel
import com.example.repository.UserRepository
import com.example.repository.UserRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
     * Direct document lookup strictly by permanent Plenxo ID on `/user_lookup/{normalized}`.
     * Performs a single document fetch (get) on the exact document ID with zero collection queries.
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

                // 1. Direct document fetch on /user_lookup/{normalized}
                val lookupDocRef = firestore.collection("user_lookup").document(normalized)
                val snapshot = try {
                    com.example.util.getDocumentServerFirst(lookupDocRef, timeoutMs = 5000L)
                } catch (e: Exception) {
                    Log.w(TAG_SEARCH, "Direct lookup read error for $normalized: ${e.message}")
                    null
                }

                if (snapshot != null && snapshot.exists()) {
                    val uid = snapshot.getString("uid") ?: snapshot.id
                    if (currentAuthUid.isNotBlank() && uid == currentAuthUid) {
                        _searchError.value = "You cannot add yourself."
                        _searchResults.value = emptyList()
                        _userModelResults.value = emptyList()
                        _isSearching.value = false
                        return@launch
                    }

                    val data = snapshot.data?.toMutableMap() ?: mutableMapOf()
                    data["docId"] = uid
                    data["uid"] = uid

                    val dName = snapshot.getString("displayName")?.takeIf { it.isNotBlank() && it != "User" }
                        ?: "Plenxo User"
                    data["displayName"] = dName
                    data["name"] = dName

                    val pId = snapshot.getString("plenxoId") ?: normalized
                    data["plenxoId"] = pId

                    val bio = snapshot.getString("bio") ?: ""
                    data["bio"] = bio
                    data["statusMessage"] = bio

                    val pic = snapshot.getString("profilePicUrl") ?: ""
                    data["profilePicUrl"] = pic

                    val ringId = snapshot.getString("profileRingId") ?: "none"
                    data["profileRingId"] = ringId

                    _searchResults.value = listOf(data)
                    _userModelResults.value = listOf(
                        UserModel(
                            uid = uid,
                            displayName = dName,
                            name = dName,
                            email = "",
                            bio = bio,
                            statusMessage = bio,
                            profilePicUrl = pic,
                            plenxoId = pId,
                            profileRingId = ringId
                        )
                    )
                    Log.d(TAG_SEARCH, "Operation: EXACT_DOC_LOOKUP, doc: user_lookup/$normalized, status: SUCCESS")
                } else {
                    // Fallback to repository getUserByPlenxoId
                    val repoUser = userRepository.getUserByPlenxoId(normalized)
                    if (repoUser != null) {
                        val uid = (repoUser["uid"] as? String) ?: (repoUser["docId"] as? String) ?: ""
                        if (currentAuthUid.isNotBlank() && uid == currentAuthUid) {
                            _searchError.value = "You cannot add yourself."
                            _searchResults.value = emptyList()
                            _userModelResults.value = emptyList()
                        } else {
                            val dName = (repoUser["displayName"] as? String) ?: "Plenxo User"
                            val pId = (repoUser["plenxoId"] as? String) ?: normalized
                            val pic = (repoUser["profilePicUrl"] as? String) ?: ""
                            val bio = (repoUser["bio"] as? String) ?: ""
                            val ringId = (repoUser["profileRingId"] as? String) ?: "none"

                            _searchResults.value = listOf(repoUser)
                            _userModelResults.value = listOf(
                                UserModel(
                                    uid = uid,
                                    displayName = dName,
                                    name = dName,
                                    email = "",
                                    bio = bio,
                                    statusMessage = bio,
                                    profilePicUrl = pic,
                                    plenxoId = pId,
                                    profileRingId = ringId
                                )
                            )
                        }
                    } else {
                        _searchResults.value = emptyList()
                        _userModelResults.value = emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG_SEARCH, "Operation: EXACT_DOC_LOOKUP, doc: user_lookup/$normalized, error: ${e.message}", e)
                _searchError.value = "Search failed: ${e.localizedMessage}"
            } finally {
                _isSearching.value = false
            }
        }
    }
}
