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
     * Auto-loads all registered users from Firestore `users` collection on screen launch.
     * Maps retrieved documents to UserModel objects and filters out the current logged-in user.
     */
    fun loadInitialUsers() {
        if (_allUsers.value.isNotEmpty()) {
            if (_searchQuery.value.isBlank()) {
                _searchResults.value = _allUsers.value
                _userModelResults.value = _allUserModels.value
            }
            return
        }
        _isSearching.value = true
        _searchError.value = null
        viewModelScope.launch {
            try {
                val currentAuthUid = auth.currentUser?.uid
                val snapshot = firestore.collection("users").get().await()
                val userList = mutableListOf<Map<String, Any>>()
                val modelList = mutableListOf<UserModel>()

                for (doc in snapshot.documents) {
                    val uid = doc.id
                    if (currentAuthUid != null && uid == currentAuthUid) continue

                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["uid"] = uid
                    data["id"] = uid
                    data["docId"] = uid

                    val dName = doc.getString("displayName")?.takeIf { it.isNotBlank() && it != "User" }
                        ?: doc.getString("name")?.takeIf { it.isNotBlank() && it != "User" }
                        ?: doc.getString("display_name")?.takeIf { it.isNotBlank() && it != "User" }
                        ?: doc.getString("fullName")?.takeIf { it.isNotBlank() && it != "User" }
                        ?: doc.getString("displayName")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("name")?.takeIf { it.isNotBlank() }
                        ?: "Plenxo User"
                    data["displayName"] = dName
                    data["name"] = dName

                    val rawPid = doc.getString("plenxoId")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("userCode")?.takeIf { it.isNotBlank() }
                        ?: "PX-${uid.take(6).uppercase()}"
                    val pId = if (rawPid.startsWith("PX-", ignoreCase = true)) {
                        "PX-" + rawPid.substring(3)
                    } else if (rawPid.length == 6 && rawPid.all { it.isDigit() }) {
                        "PX-$rawPid"
                    } else {
                        rawPid
                    }
                    data["plenxoId"] = pId

                    val bio = doc.getString("bio")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("statusMessage")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("bioStatus")?.takeIf { it.isNotBlank() }
                        ?: ""
                    data["bio"] = bio
                    data["statusMessage"] = bio

                    val pic = doc.getString("profilePicUrl")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("avatar_url")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("avatarUrl")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("photoUrl")?.takeIf { it.isNotBlank() }
                        ?: ""
                    data["profilePicUrl"] = pic

                    val email = doc.getString("email") ?: ""
                    data["email"] = email

                    val ringId = doc.getString("profileRingId")
                        ?: doc.getString("selectedRingId")
                        ?: "none"
                    data["profileRingId"] = ringId

                    userList.add(data)

                    val userModel = UserModel(
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
                    modelList.add(userModel)
                }

                _allUsers.value = userList
                _allUserModels.value = modelList

                if (_searchQuery.value.isBlank()) {
                    _searchResults.value = userList
                    _userModelResults.value = modelList
                }
            } catch (e: Exception) {
                Log.e("UserSearchViewModel", "Error loading initial users: ${e.message}", e)
                _searchError.value = "Failed to load users: ${e.localizedMessage}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * Real-time dynamic filtering strictly by Plenxo ID.
     * When search query is empty, restores full auto-loaded list.
     * When Plenxo ID is entered, filters locally or queries Firestore by `plenxoId`.
     */
    fun updateSearchQuery(query: String) {
        val q = query.take(30)
        _searchQuery.value = q
        _searchError.value = null
        if (q.isBlank()) {
            _searchResults.value = _allUsers.value
            _userModelResults.value = _allUserModels.value
            _hasSearched.value = false
            return
        }

        _hasSearched.value = true
        val normalized = normalizePlenxoId(q)
        val currentAuthUid = auth.currentUser?.uid

        if (normalized != null) {
            val filteredMap = _allUsers.value.filter { u ->
                val rawPid = (u["plenxoId"] as? String) ?: ""
                val pid = if (rawPid.startsWith("PX-", ignoreCase = true)) {
                    "PX-" + rawPid.substring(3)
                } else if (rawPid.length == 6 && rawPid.all { it.isDigit() }) {
                    "PX-$rawPid"
                } else {
                    rawPid.uppercase()
                }
                val uid = (u["uid"] as? String) ?: (u["id"] as? String) ?: ""
                pid.equals(normalized, ignoreCase = true) && (currentAuthUid == null || uid != currentAuthUid)
            }

            val filteredModels = _allUserModels.value.filter { m ->
                val rawPid = m.plenxoId
                val pid = if (rawPid.startsWith("PX-", ignoreCase = true)) {
                    "PX-" + rawPid.substring(3)
                } else if (rawPid.length == 6 && rawPid.all { it.isDigit() }) {
                    "PX-$rawPid"
                } else {
                    rawPid.uppercase()
                }
                pid.equals(normalized, ignoreCase = true) && (currentAuthUid == null || m.uid != currentAuthUid)
            }

            _searchResults.value = filteredMap
            _userModelResults.value = filteredModels

            // If not found in preloaded cache, trigger direct Firestore query
            if (filteredMap.isEmpty()) {
                executeSearch()
            }
        } else {
            // For partial or invalid input, clear results (no matching users)
            _searchResults.value = emptyList()
            _userModelResults.value = emptyList()
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = _allUsers.value
        _userModelResults.value = _allUserModels.value
        _hasSearched.value = false
        _searchError.value = null
    }

    /**
     * Direct explicit search strictly by permanent Plenxo ID (`plenxoId`).
     */
    fun executeSearch() {
        val rawInput = _searchQuery.value.trim().removePrefix("@").removePrefix("#").trim()
        if (rawInput.isBlank()) {
            _searchResults.value = _allUsers.value
            _userModelResults.value = _allUserModels.value
            _hasSearched.value = false
            return
        }

        val normalized = normalizePlenxoId(rawInput)
        if (normalized == null) {
            _hasSearched.value = true
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
                    _searchError.value = "You cannot add yourself"
                    _searchResults.value = emptyList()
                    _userModelResults.value = emptyList()
                    _isSearching.value = false
                    return@launch
                }

                // Check local cache first
                val localMatches = _allUsers.value.filter { u ->
                    val rawPid = (u["plenxoId"] as? String) ?: ""
                    val pid = if (rawPid.startsWith("PX-", ignoreCase = true)) {
                        "PX-" + rawPid.substring(3)
                    } else if (rawPid.length == 6 && rawPid.all { it.isDigit() }) {
                        "PX-$rawPid"
                    } else {
                        rawPid.uppercase()
                    }
                    val uid = (u["uid"] as? String) ?: (u["id"] as? String) ?: ""
                    pid.equals(normalized, ignoreCase = true) && (currentAuthUid.isBlank() || uid != currentAuthUid)
                }

                if (localMatches.isNotEmpty()) {
                    _searchResults.value = localMatches
                    _userModelResults.value = _allUserModels.value.filter { m ->
                        val rawPid = m.plenxoId
                        val pid = if (rawPid.startsWith("PX-", ignoreCase = true)) {
                            "PX-" + rawPid.substring(3)
                        } else if (rawPid.length == 6 && rawPid.all { it.isDigit() }) {
                            "PX-$rawPid"
                        } else {
                            rawPid.uppercase()
                        }
                        pid.equals(normalized, ignoreCase = true) && (currentAuthUid.isBlank() || m.uid != currentAuthUid)
                    }
                    _isSearching.value = false
                    return@launch
                }

                // Query Firestore strictly by plenxoId field
                val numericPart = normalized.removePrefix("PX-")
                val searchKeys = listOf(normalized, normalized.lowercase(), numericPart)
                val list = mutableListOf<Map<String, Any>>()
                val modelList = mutableListOf<UserModel>()

                for (key in searchKeys) {
                    val query = firestore.collection("users").whereEqualTo("plenxoId", key)
                    val snapshot = try {
                        getQuerySnapshotServerFirst(query, timeoutMs = 5000L)
                    } catch (e: Exception) {
                        Log.w("UserSearchViewModel", "Search query failed for key $key: ${e.message}")
                        null
                    } ?: continue

                    if (!snapshot.isEmpty) {
                        snapshot.documents.forEach { doc ->
                            val uid = doc.id
                            if (currentAuthUid.isNotBlank() && uid == currentAuthUid) return@forEach

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
                        break
                    }
                }

                if (list.isEmpty()) {
                    val repoResults = userRepository.searchUsersByPlenxoId(normalized)
                    if (repoResults.isNotEmpty()) {
                        repoResults.forEach { r ->
                            val uid = (r["uid"] as? String) ?: (r["docId"] as? String) ?: ""
                            if (currentAuthUid.isBlank() || uid != currentAuthUid) {
                                list.add(r)
                            }
                        }
                    }
                }

                val distinctResults = list.distinctBy { (it["uid"] as? String) ?: (it["id"] as? String) ?: it.hashCode().toString() }
                val distinctModels = modelList.distinctBy { it.uid }

                _searchResults.value = distinctResults
                _userModelResults.value = distinctModels
            } catch (e: Exception) {
                Log.e("UserSearchViewModel", "Search error: ${e.message}", e)
                _searchError.value = "Search failed: ${e.localizedMessage}"
            } finally {
                _isSearching.value = false
            }
        }
    }
}
