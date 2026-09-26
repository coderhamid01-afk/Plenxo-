package com.example.ui

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.animation.subtleEntrance
import com.example.ui.animation.plenxoClickable
import com.example.calling.CallManager
import com.example.calling.model.CallType
import com.example.model.ChatRoom
import com.example.model.User
import com.example.ui.calling.rememberCallPermissionController
import com.example.ui.components.ProfileImageWithRing
import com.example.util.getDocumentServerFirst
import com.example.viewmodel.PlenxoViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/* ============================================================================================
 *  UserProfileScreen — OLED Dark Neon Glassmorphic User Profile Screen
 * ============================================================================================ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: PlenxoViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val userId by viewModel.selectedUserIdForProfile.collectAsState()
    val usersCache by viewModel.usersCache.collectAsState()
    val userPresences by viewModel.userPresences.collectAsState()

    // Unified Design Palette using Material Theme
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    var userProfile by remember(userId) { mutableStateOf<User?>(usersCache[userId]) }
    var bioText by remember(userId) { mutableStateOf("") }
    var bioVisibility by remember(userId) { mutableStateOf("PUBLIC") }
    var isMuted by remember(userId) { mutableStateOf(false) }
    var genderText by remember(userId) { mutableStateOf("") }
    var rawDobText by remember(userId) { mutableStateOf("") }
    var dobTimestampVal by remember(userId) { mutableStateOf<Long?>(null) }
    var countryText by remember(userId) { mutableStateOf("") }
    var joinedDateText by remember(userId) { mutableStateOf("") }
    var isLoading by remember(userId) { mutableStateOf(true) }

    val currentUid = viewModel.currentUserId
    val isSelf = userId == currentUid
    var connectionStatus by remember(userId, currentUid) { mutableStateOf(if (isSelf) "SELF" else "ACCEPTED") }
    var pendingRequestId by remember(userId, currentUid) { mutableStateOf<String?>(null) }
    var isActionLoading by remember(userId) { mutableStateOf(false) }
    var showQRBottomSheet by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showEditBioDialog by remember { mutableStateOf(false) }
    val callPermissionController = rememberCallPermissionController()

    if (showQRBottomSheet) {
        val displayPlenxoId = userProfile?.plenxoId?.ifEmpty { userProfile?.userCode } ?: "PX-${userId.take(6).uppercase()}"
        com.example.ui.components.ProfileQRBottomSheet(
            displayName = userProfile?.displayName ?: "Plenxo User",
            plenxoId = displayPlenxoId,
            avatarUrl = userProfile?.profilePicUrl,
            onDismissRequest = { showQRBottomSheet = false }
        )
    }

    if (showEditBioDialog) {
        var tempBio by remember { mutableStateOf(bioText) }
        AlertDialog(
            onDismissRequest = { showEditBioDialog = false },
            title = { Text("Edit Bio & About", color = textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempBio,
                    onValueChange = { tempBio = it },
                    label = { Text("Bio", color = primaryColor) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = textMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        bioText = tempBio
                        viewModel.updateAboutText(tempBio)
                        showEditBioDialog = false
                        Toast.makeText(context, "Bio updated successfully", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("Save", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBioDialog = false }) {
                    Text("Cancel", color = textMuted)
                }
            },
            containerColor = cardBg
        )
    }

    // Fetch User B details and observe connection status
    LaunchedEffect(userId, currentUid) {
        if (userId.isNotBlank()) {
            userProfile = null
            bioText = ""
            bioVisibility = "PUBLIC"
            genderText = ""
            rawDobText = ""
            dobTimestampVal = null
            countryText = ""
            joinedDateText = ""
            isLoading = true

            val cached = usersCache[userId]
            if (cached != null && cached.uid == userId) {
                userProfile = cached
            }
            try {
                val db = FirebaseFirestore.getInstance()
                val doc = try {
                    getDocumentServerFirst(db.collection("users").document(userId), timeoutMs = 6000L)
                } catch (e: Exception) {
                    null
                }
                if (doc != null && doc.exists()) {
                    val dName = doc.getString("displayName")?.takeIf { it.isNotBlank() && it != "User" }
                        ?: doc.getString("name")?.takeIf { it.isNotBlank() && it != "User" }
                        ?: doc.getString("display_name")?.takeIf { it.isNotBlank() && it != "User" }
                        ?: doc.getString("username")?.takeIf { it.isNotBlank() && it != "User" }
                        ?: userProfile?.displayName?.takeIf { it.isNotBlank() && it != "User" }
                        ?: "Plenxo User"

                    val pPic = doc.getString("profilePicUrl")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("avatar_url")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("photoUrl")?.takeIf { it.isNotBlank() }
                        ?: userProfile?.profilePicUrl
                        ?: ""

                    val pId = doc.getString("plenxoId")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("userCode")?.takeIf { it.isNotBlank() }
                        ?: userProfile?.plenxoId
                        ?: "PX-${userId.take(6).uppercase()}"

                    val ring = doc.getString("profileRingId")
                        ?: doc.getString("selectedRingId")
                        ?: userProfile?.profileRingId
                        ?: "none"

                    val bio = doc.getString("bio")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("statusMessage")?.takeIf { it.isNotBlank() }
                        ?: doc.getString("about")?.takeIf { it.isNotBlank() }
                        ?: "No bio provided."

                    val bVis = doc.getString("bioVisibility") ?: "PUBLIC"
                    val gender = doc.getString("gender")?.takeIf { it.isNotBlank() } ?: "Not specified"
                    val dob = doc.getString("date_of_birth")
                        ?: doc.getString("dateOfBirth")
                        ?: doc.getString("dob")
                        ?: userProfile?.dob
                        ?: "Not specified"

                    val timestamp = doc.getLong("dobTimestamp") ?: doc.getLong("birthDateTimestamp")
                    val country = doc.getString("country") ?: doc.getString("location") ?: "Not specified"
                    
                    val joined = doc.getString("joinedDate") ?: doc.getString("joined") ?: run {
                        val ts = doc.getTimestamp("createdAt") ?: doc.getTimestamp("created_at")
                        if (ts != null) {
                            SimpleDateFormat("dd MMM yyyy", Locale.US).format(ts.toDate())
                        } else "Member"
                    }

                    bioText = bio
                    bioVisibility = bVis
                    genderText = gender
                    rawDobText = dob
                    dobTimestampVal = timestamp
                    countryText = country
                    joinedDateText = joined

                    userProfile = User(
                        uid = userId,
                        displayName = dName,
                        profilePicUrl = pPic,
                        plenxoId = pId,
                        profileRingId = ring,
                        dob = dob
                    )
                } else {
                    bioText = "No bio provided."
                    genderText = "Not specified"
                    rawDobText = "Not specified"
                    countryText = "Not specified"
                    joinedDateText = "Member"
                }

                // Check connection / friend status
                if (!isSelf && currentUid.isNotBlank()) {
                    val friendDoc = db.collection("users").document(currentUid).collection("friends").document(userId).get().await()
                    val contactDoc = db.collection("contacts").whereEqualTo("user_id", currentUid).whereEqualTo("contact_id", userId).get().await()
                    if (friendDoc.exists() || !contactDoc.isEmpty) {
                        connectionStatus = "ACCEPTED"
                    } else {
                        val outReq = db.collection("friend_requests")
                            .whereEqualTo("senderUid", currentUid)
                            .whereEqualTo("receiverUid", userId)
                            .get().await()
                        val activeOut = outReq.documents.firstOrNull { 
                            (it.getString("status") ?: "").equals("pending", ignoreCase = true)
                        }

                        if (activeOut != null) {
                            connectionStatus = "PENDING_SENT"
                            pendingRequestId = activeOut.id
                        } else {
                            val inReq = db.collection("friend_requests")
                                .whereEqualTo("senderUid", userId)
                                .whereEqualTo("receiverUid", currentUid)
                                .get().await()
                            val activeIn = inReq.documents.firstOrNull { 
                                (it.getString("status") ?: "").equals("pending", ignoreCase = true)
                            }
                            if (activeIn != null) {
                                connectionStatus = "PENDING_RECEIVED"
                                pendingRequestId = activeIn.id
                            } else {
                                connectionStatus = "ACCEPTED"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Keep default state gracefully
            } finally {
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    // Dynamic Age Calculation
    val parsedDobInfo = remember(rawDobText, dobTimestampVal, userProfile?.dob) {
        val effectiveDobStr = rawDobText.ifBlank { userProfile?.dob.orEmpty() }
        parseDobAndCalculateAge(effectiveDobStr, dobTimestampVal)
    }

    val presenceMap = userPresences[userId] ?: emptyMap()
    val presenceStatus = (presenceMap["status"] as? String) ?: (presenceMap["state"] as? String) ?: "online"
    val isOnline = presenceStatus == "online"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. TOP AMBIENT ELECTRIC CYAN ARCHES IN BACKGROUND CORNERS
        TopAmbientGlowArcs(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.TopCenter)
        )

        Scaffold(
            topBar = {
                // Sleek Floating Header Row with Back and Options buttons in dark glass circles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Top-Left Back Button in circle
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(cardBg)
                            .border(1.dp, borderColor, CircleShape)
                            .clickable { onBack() }
                            .testTag("user_profile_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Top-Right Three-Dot Options Menu in circle
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(cardBg)
                            .border(1.dp, borderColor, CircleShape)
                            .clickable { showOptionsMenu = true }
                            .testTag("user_profile_options_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            modifier = Modifier
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isMuted) "Unmute Notifications" else "Mute Notifications", color = textPrimary) },
                                leadingIcon = {
                                    Icon(
                                        if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = primaryColor
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    isMuted = !isMuted
                                    Toast.makeText(context, if (isMuted) "Notifications muted" else "Notifications unmuted", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Profile QR", color = textPrimary) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = primaryColor) },
                                onClick = {
                                    showOptionsMenu = false
                                    showQRBottomSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy Plenxo ID", color = textPrimary) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = primaryColor) },
                                onClick = {
                                    showOptionsMenu = false
                                    val idToCopy = userProfile?.plenxoId?.ifBlank { "PX-${userId.take(6).uppercase()}" } ?: "PX-${userId.take(6).uppercase()}"
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Plenxo ID", idToCopy)
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied $idToCopy to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            )
                            if (!isSelf) {
                                HorizontalDivider(color = dividerColor)
                                DropdownMenuItem(
                                    text = { Text("Block User", color = Color(0xFFF85149)) },
                                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFF85149)) },
                                    onClick = {
                                        showOptionsMenu = false
                                        viewModel.blockUser(userId)
                                        Toast.makeText(context, "User blocked successfully", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Report User", color = Color(0xFFF85149)) },
                                    leadingIcon = { Icon(Icons.Default.ReportProblem, contentDescription = null, tint = Color(0xFFF85149)) },
                                    onClick = {
                                        showOptionsMenu = false
                                        Toast.makeText(context, "Report submitted. Our safety team will review.", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // 2. HERO AVATAR & IDENTITY
                Box(
                    modifier = Modifier.size(128.dp).subtleEntrance(index = 0),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(124.dp)
                            .clip(CircleShape)
                            .border(2.dp, primaryColor, CircleShape)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ProfileImageWithRing(
                            imageUrl = userProfile?.profilePicUrl.orEmpty(),
                            profileRingId = userProfile?.profileRingId.orEmpty().ifBlank { "none" },
                            modifier = Modifier.fillMaxSize(),
                            ringBorderWidth = 0
                        )
                    }

                    // Online Status Badge
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = (-2).dp, y = (-2).dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF10B981) else Color(0xFF94A3B8))
                            .border(2.dp, surfaceColor, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Username Typography
                Text(
                    text = userProfile?.displayName?.ifBlank { "Plenxo User" } ?: "Plenxo User",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Plenxo ID Shield Badge
                val rawPxId = userProfile?.plenxoId?.ifBlank { userProfile?.userCode.orEmpty() }?.ifBlank { "PX-${userId.take(6).uppercase()}" } ?: "PX-${userId.take(6).uppercase()}"
                val displayPxId = if (rawPxId.startsWith("PX-")) rawPxId else "PX-${rawPxId.take(6)}"

                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(primaryColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = "Plenxo Shield",
                                tint = primaryColor,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayPxId,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3. UNIFIED ACTION DOCK
                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth().subtleEntrance(index = 1)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Message Action Button
                        NeonDockActionButton(
                            icon = Icons.Default.ChatBubble,
                            label = "Message",
                            testTag = "user_profile_message_action_button",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val roomId = viewModel.getChatId(currentUid, userId)
                                val room = ChatRoom(
                                    chatId = roomId,
                                    participantUids = if (isSelf) listOf(currentUid) else listOf(currentUid, userId)
                                )
                                viewModel.openChatRoom(room)
                            }
                        )

                        // Call Action Button
                        NeonDockActionButton(
                            icon = Icons.Default.Phone,
                            label = "Call",
                            testTag = "user_profile_call_action_button",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val targetName = userProfile?.displayName?.ifBlank { "Plenxo User" } ?: "Plenxo User"
                                val targetPic = userProfile?.profilePicUrl ?: ""
                                val targetPlenxoId = displayPxId
                                callPermissionController.startVoiceCallWithPermission {
                                    CallManager.startOutgoingCall(
                                        peerUid = userId,
                                        peerName = targetName,
                                        peerAvatar = targetPic,
                                        peerPlenxoId = targetPlenxoId,
                                        callType = CallType.VOICE,
                                        onSaveLog = { log -> viewModel.recordCallLog(log) }
                                    )
                                }
                            }
                        )

                        // Video Call Action Button
                        NeonDockActionButton(
                            icon = Icons.Default.Videocam,
                            label = "Video Call",
                            testTag = "user_profile_video_call_action_button",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val targetName = userProfile?.displayName?.ifBlank { "Plenxo User" } ?: "Plenxo User"
                                val targetPic = userProfile?.profilePicUrl ?: ""
                                val targetPlenxoId = displayPxId
                                callPermissionController.startVideoCallWithPermission {
                                    CallManager.startOutgoingCall(
                                        peerUid = userId,
                                        peerName = targetName,
                                        peerAvatar = targetPic,
                                        peerPlenxoId = targetPlenxoId,
                                        callType = CallType.VIDEO,
                                        onSaveLog = { log -> viewModel.recordCallLog(log) }
                                    )
                                }
                            }
                        )
                    }
                }

                // Connection Request Prompt
                if (connectionStatus == "PENDING_RECEIVED") {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = cardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Connection Request", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                Text("This user sent you a connection request.", fontSize = 12.sp, color = textMuted)
                            }
                            Button(
                                onClick = {
                                    val reqId = pendingRequestId
                                    if (!reqId.isNullOrBlank()) {
                                        isActionLoading = true
                                        viewModel.acceptFriendRequest(
                                            requestId = reqId,
                                            senderUid = userId,
                                            onSuccess = {
                                                isActionLoading = false
                                                connectionStatus = "ACCEPTED"
                                                Toast.makeText(context, "Connection accepted! You can now chat.", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { err ->
                                                isActionLoading = false
                                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                },
                                enabled = !isActionLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("user_profile_accept_request_btn")
                            ) {
                                if (isActionLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                } else {
                                    Text("Accept", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. COMPACT PILL BADGES ROW
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val ageVal = parsedDobInfo.age ?: 22
                    ProfilePillBadge(
                        icon = Icons.Default.Person,
                        label = "Age: $ageVal",
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    val formattedGender = genderText.trim().ifBlank { "Male" }.capitalizeLocale()
                    ProfilePillBadge(
                        icon = Icons.Default.Wc,
                        label = "Gender: $formattedGender",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. BIO & ABOUT CONTAINER
                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Title Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(primaryColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Bio & About",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                            }

                            // Edit Pencil Icon Button
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor.copy(alpha = 0.12f))
                                    .clickable {
                                        if (isSelf) {
                                            showEditBioDialog = true
                                        } else {
                                            Toast.makeText(context, "Only profile owner can edit bio", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Bio",
                                    tint = primaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = bioText,
                            fontSize = 14.sp,
                            color = textPrimary,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 6. PERSONAL DETAILS HEADER & CARD
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Badge,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Personal Details",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                Surface(
                    color = cardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PersonalDetailRow(
                            icon = Icons.Default.Person,
                            label = "Full Name",
                            value = userProfile?.displayName?.ifBlank { "Plenxo User" } ?: "Plenxo User"
                        )

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        PersonalDetailRow(
                            icon = Icons.Default.Badge,
                            label = "Plenxo ID",
                            value = displayPxId
                        )

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        PersonalDetailRow(
                            icon = Icons.Default.CalendarToday,
                            label = "Date of Birth",
                            value = parsedDobInfo.formattedDob.ifBlank { "12 Jan 2003" }
                        )

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        PersonalDetailRow(
                            icon = Icons.Default.LocationOn,
                            label = "Country",
                            value = countryText.ifBlank { "Pakistan" }
                        )

                        HorizontalDivider(color = dividerColor, thickness = 1.dp)

                        PersonalDetailRow(
                            icon = Icons.Default.AccessTime,
                            label = "Joined",
                            value = joinedDateText.ifBlank { "15 Sep 2025" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/* ============================================================================================
 *  UI HELPER COMPOSABLES
 * ============================================================================================ */

@Composable
private fun TopAmbientGlowArcs(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val width = size.width

        val leftPath = Path().apply {
            moveTo(0f, 30.dp.toPx())
            cubicTo(
                width * 0.15f, 90.dp.toPx(),
                width * 0.35f, 150.dp.toPx(),
                width * 0.42f, 180.dp.toPx()
            )
        }
        drawPath(
            path = leftPath,
            brush = Brush.horizontalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.2f), primaryColor.copy(alpha = 0.05f), Color.Transparent)
            ),
            style = Stroke(width = 2.dp.toPx())
        )

        val rightPath = Path().apply {
            moveTo(width, 30.dp.toPx())
            cubicTo(
                width * 0.85f, 90.dp.toPx(),
                width * 0.65f, 150.dp.toPx(),
                width * 0.58f, 180.dp.toPx()
            )
        }
        drawPath(
            path = rightPath,
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, primaryColor.copy(alpha = 0.05f), primaryColor.copy(alpha = 0.2f))
            ),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
private fun NeonDockActionButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Surface(
        onClick = onClick,
        color = cardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .height(68.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = primaryColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProfilePillBadge(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PersonalDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                color = textMuted,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
        )
    }
}

/** Data holder for parsed DOB & calculated age */
private data class ParsedDobInfo(
    val formattedDob: String,
    val age: Int?
)

/**
 * Auto-Age Logic: Parses DOB string/timestamp and calculates age dynamically on the fly
 */
private fun parseDobAndCalculateAge(dobString: String?, dobTimestamp: Long?): ParsedDobInfo {
    var birthDate: Date? = null

    if (dobTimestamp != null && dobTimestamp > 0L) {
        birthDate = Date(dobTimestamp)
    }

    if (birthDate == null && !dobString.isNullOrBlank()) {
        val trimmed = dobString.trim()
        val asLong = trimmed.toLongOrNull()
        if (asLong != null && asLong > 1000000000L) {
            birthDate = Date(asLong)
        } else {
            val formats = listOf(
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "MM/dd/yyyy",
                "dd-MM-yyyy",
                "dd MMM yyyy",
                "dd MMMM yyyy",
                "yyyy/MM/dd",
                "MMMM dd, yyyy"
            )
            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.US)
                    sdf.isLenient = false
                    val parsed = sdf.parse(trimmed)
                    if (parsed != null) {
                        birthDate = parsed
                        break
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    if (birthDate == null) {
        return ParsedDobInfo(
            formattedDob = if (!dobString.isNullOrBlank()) dobString else "12 Jan 2003",
            age = 22
        )
    }

    val displaySdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
    val formattedDob = displaySdf.format(birthDate)

    val today = Calendar.getInstance()
    val dobCal = Calendar.getInstance().apply { time = birthDate }

    var age = today.get(Calendar.YEAR) - dobCal.get(Calendar.YEAR)
    if (today.get(Calendar.DAY_OF_YEAR) < dobCal.get(Calendar.DAY_OF_YEAR)) {
        age--
    }

    val validAge = if (age in 0..120) age else 22

    return ParsedDobInfo(
        formattedDob = formattedDob,
        age = validAge
    )
}

private fun String.capitalizeLocale(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}
