package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.R
import com.example.model.ChatRoom
import com.example.model.FriendRequest
import com.example.ui.components.ProfileRingBox
import com.example.ui.components.bounceCombinedClickable
import com.example.ui.animation.PlenxoMotion
import com.example.ui.animation.plenxoClickable
import com.example.ui.animation.subtleEntrance
import com.example.ui.animation.shimmerSkeletonLoader
import com.example.viewmodel.PlenxoScreen
import com.example.viewmodel.PlenxoViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    viewModel: PlenxoViewModel,
    primaryColor: Color
) {
    val chats by viewModel.chats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val usersCache by viewModel.usersCache.collectAsState()
    val currentUserProfile by viewModel.currentUserProfile.collectAsState()
    val galleryImageUriString by viewModel.galleryImageUriString.collectAsState()
    val currentUserId = viewModel.currentUserId
    val pinnedChatIds by viewModel.pinnedChatIds.collectAsState()
    val lockedChatIds by viewModel.lockedChatIds.collectAsState()
    val userPresences by viewModel.userPresences.collectAsState()
    val pendingFriendRequests by viewModel.pendingFriendRequests.collectAsState()

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            viewModel.startListeningForChats()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Unread", "Pinned"

    // Dialog state for locked chats
    var chatToUnlock by remember { mutableStateOf<ChatRoom?>(null) }
    var showUnlockPasswordDialog by remember { mutableStateOf(false) }
    var unlockPasswordText by remember { mutableStateOf("") }
    var unlockPasswordError by remember { mutableStateOf<String?>(null) }

    // Dialog state for deleting chats
    val context = LocalContext.current
    var chatToDelete by remember { mutableStateOf<ChatRoom?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDeletePasswordDialog by remember { mutableStateOf(false) }
    var deletePasswordText by remember { mutableStateOf("") }
    var deletePasswordError by remember { mutableStateOf<String?>(null) }

    // Security Unlock Dialog
    val localChatUnlock = chatToUnlock
    if (showUnlockPasswordDialog && localChatUnlock != null) {
        val targetChat = localChatUnlock
        val contextLocal = LocalContext.current
        val correctPin = remember(targetChat.chatId) {
            com.example.repository.SecurityRepository(contextLocal).getChatLock(targetChat.chatId)
        }
        AlertDialog(
            onDismissRequest = {
                showUnlockPasswordDialog = false
                chatToUnlock = null
                unlockPasswordText = ""
                unlockPasswordError = null
            },
            title = { Text("Unlock Chat", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text(
                        "This conversation is locked. Enter your security PIN to unlock.",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = unlockPasswordText,
                        onValueChange = {
                            unlockPasswordText = it
                            unlockPasswordError = null
                        },
                        placeholder = { Text("Enter Chat PIN", color = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        isError = unlockPasswordError != null,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (unlockPasswordError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = unlockPasswordError ?: "", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (unlockPasswordText == correctPin) {
                            showUnlockPasswordDialog = false
                            chatToUnlock = null
                            unlockPasswordText = ""
                            unlockPasswordError = null
                            viewModel.openChatRoom(targetChat)
                        } else {
                            unlockPasswordError = "Incorrect PIN."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("Unlock", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnlockPasswordDialog = false
                    chatToUnlock = null
                    unlockPasswordText = ""
                    unlockPasswordError = null
                }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Delete Verification Dialog
    val localChat1 = chatToDelete
    if (showDeletePasswordDialog && localChat1 != null) {
        val targetChat = localChat1
        val contextLocal = LocalContext.current
        val correctPin = remember(targetChat.chatId) {
            com.example.repository.SecurityRepository(contextLocal).getChatLock(targetChat.chatId)
        }
        AlertDialog(
            onDismissRequest = {
                showDeletePasswordDialog = false
                deletePasswordText = ""
                deletePasswordError = null
            },
            title = { Text(stringResource(R.string.str_verify_chat_pin), fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text(
                        stringResource(id = R.string.str_this_chat_is_locked_sensitive),
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = deletePasswordText,
                        onValueChange = {
                            deletePasswordText = it
                            deletePasswordError = null
                        },
                        placeholder = { Text(stringResource(R.string.str_enter_chat_pin), color = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        isError = deletePasswordError != null,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (deletePasswordError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = deletePasswordError ?: "", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (deletePasswordText == correctPin) {
                            viewModel.deleteChat(targetChat.chatId)
                            showDeletePasswordDialog = false
                            chatToDelete = null
                            deletePasswordText = ""
                            deletePasswordError = null
                            Toast.makeText(contextLocal, "Chat deleted successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            deletePasswordError = "Incorrect PIN. Deletion denied."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text(stringResource(R.string.str_verify_purge), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeletePasswordDialog = false
                    deletePasswordText = ""
                    deletePasswordError = null
                }) {
                    Text(stringResource(R.string.cancel), color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Simple Delete Confirm Dialog
    val localChat2 = chatToDelete
    if (showDeleteConfirmDialog && localChat2 != null) {
        val targetChat = localChat2
        val contextLocal = LocalContext.current
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
            },
            title = { Text(stringResource(R.string.str_delete_chat), fontWeight = FontWeight.Bold, color = Color(0xFFEF4444)) },
            text = {
                Text(
                    stringResource(id = R.string.str_are_you_sure_you_want),
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteChat(targetChat.chatId)
                        showDeleteConfirmDialog = false
                        chatToDelete = null
                        Toast.makeText(contextLocal, "Chat deleted permanently", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text(stringResource(R.string.str_delete_permanently), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    chatToDelete = null
                }) {
                    Text(stringResource(R.string.cancel), color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Sort and filter conversations
    val sortedChats = remember(chats, pinnedChatIds) {
        chats.sortedWith(
            compareByDescending<ChatRoom> { pinnedChatIds.contains(it.chatId) }
                .thenByDescending { it.lastMessageTimestamp }
        )
    }

    val filteredChats = remember(sortedChats, searchQuery, selectedFilter, usersCache) {
        sortedChats.filter { chat ->
            val recipientUid = chat.participantUids.firstOrNull { it != currentUserId } ?: ""
            val recipientUser = usersCache[recipientUid]
            val unreadCount = chat.unreadCounts[currentUserId] ?: 0
            val isPinned = pinnedChatIds.contains(chat.chatId)

            val matchesFilter = when (selectedFilter) {
                "Unread" -> unreadCount > 0
                "Pinned" -> isPinned
                else -> true
            }

            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val nameMatch = recipientUser?.displayName?.contains(searchQuery, ignoreCase = true) == true
                val emailMatch = recipientUser?.email?.contains(searchQuery, ignoreCase = true) == true
                val userCodeMatch = recipientUser?.userCode?.contains(searchQuery, ignoreCase = true) == true
                val plenxoIdMatch = recipientUser?.plenxoId?.contains(searchQuery, ignoreCase = true) == true
                nameMatch || emailMatch || userCodeMatch || plenxoIdMatch
            }

            matchesFilter && matchesSearch
        }
    }

    val unreadTotalCount by remember(chats, currentUserId) {
        derivedStateOf { chats.count { (it.unreadCounts[currentUserId] ?: 0) > 0 } }
    }
    val pinnedTotalCount by remember(chats, pinnedChatIds) {
        derivedStateOf { chats.count { pinnedChatIds.contains(it.chatId) } }
    }
    val chatListState = rememberLazyListState()

    val darkSurfaceBg = MaterialTheme.colorScheme.background
    val electricCyan = MaterialTheme.colorScheme.primary
    val electricBlue = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            // Dark Neon Glassmorphic Top Bar matching Image 1
            Surface(
                color = darkSurfaceBg,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Profile Picture in Dark Glass Circle with Cyan Border & Green Online Dot
                        val contextLocal = LocalContext.current
                        val localRingId = com.example.util.SessionManager.getProfileRingId(contextLocal)
                        val userRingId = if (localRingId != "none") localRingId else (currentUserProfile?.profileRingId ?: "none")
                        val displayAvatarUrl = currentUserProfile?.avatarUrl?.takeIf { it.isNotBlank() }
                            ?: currentUserProfile?.profilePicUrl?.takeIf { it.isNotBlank() }
                            ?: galleryImageUriString?.takeIf { it.isNotEmpty() }

                        Box(
                            modifier = Modifier
                                .testTag("profile_settings_avatar_button")
                                .clickable {
                                    viewModel.navigateToScreen(PlenxoScreen.SETTINGS_PROFILE)
                                }
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0B182B))
                                    .border(2.dp, Color(0xFF00A3FF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val initialChar = currentUserProfile?.displayName?.takeIf { it.isNotBlank() }?.take(1)?.uppercase() ?: "H"
                                val fallbackGradient = Brush.linearGradient(
                                    listOf(Color(0xFF1D4ED8), Color(0xFF2563EB))
                                )

                                if (!displayAvatarUrl.isNullOrEmpty() && (displayAvatarUrl.startsWith("http") || displayAvatarUrl.startsWith("content://") || displayAvatarUrl.startsWith("file://"))) {
                                    coil.compose.SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(contextLocal)
                                            .data(displayAvatarUrl)
                                            .crossfade(true)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .build(),
                                        contentDescription = "Profile Settings",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        loading = {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(fallbackGradient),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(initialChar, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                            }
                                        },
                                        error = {
                                            Box(
                                                modifier = Modifier.fillMaxSize().background(fallbackGradient),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(initialChar, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                            }
                                        }
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(fallbackGradient),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initialChar,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                }
                            }

                            // Green online status badge at top right
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00FF66))
                                    .border(2.dp, Color(0xFF0B0E14), CircleShape)
                                    .align(Alignment.TopEnd)
                            )
                        }

                        // Center Branding: Stylized Cyan 'P' Icon + "LENXO"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Stylized Cyan 'P' Mark
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier.size(width = 22.dp, height = 24.dp)
                            ) {
                                val w = size.width
                                val h = size.height
                                val cyanColor = Color(0xFF00A3FF)

                                val pPath = androidx.compose.ui.graphics.Path().apply {
                                    fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                                    // Outer P contour
                                    moveTo(0f, 0f)
                                    lineTo(w * 0.62f, 0f)
                                    cubicTo(w * 1.05f, 0f, w * 1.05f, h * 0.58f, w * 0.62f, h * 0.58f)
                                    lineTo(w * 0.36f, h * 0.58f)
                                    lineTo(w * 0.36f, h)
                                    lineTo(0f, h)
                                    close()

                                    // Inner P loop cutout
                                    moveTo(w * 0.36f, h * 0.18f)
                                    lineTo(w * 0.60f, h * 0.18f)
                                    cubicTo(w * 0.78f, h * 0.18f, w * 0.78f, h * 0.40f, w * 0.60f, h * 0.40f)
                                    lineTo(w * 0.36f, h * 0.40f)
                                    close()
                                }
                                drawPath(pPath, color = cyanColor)
                            }

                            Spacer(modifier = Modifier.width(7.dp))

                            Text(
                                text = "LENXO",
                                fontWeight = FontWeight.Black,
                                fontSize = 21.sp,
                                letterSpacing = 2.sp,
                                color = Color.White
                            )
                        }

                        // Right: Circular Dark-Blue Container Buttons for Notifications & Settings
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Notifications Button
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0C1D36))
                                    .border(1.dp, Color(0xFF003870), CircleShape)
                                    .clickable { viewModel.navigateToScreen(PlenxoScreen.CHAT_REQUESTS) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = "Notifications",
                                    tint = Color(0xFF00A3FF),
                                    modifier = Modifier.size(20.dp)
                                )
                                if (pendingFriendRequests.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00A3FF))
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                    )
                                }
                            }

                            // Settings Gear Button
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0C1D36))
                                    .border(1.dp, Color(0xFF003870), CircleShape)
                                    .clickable { viewModel.navigateToScreen(PlenxoScreen.SETTINGS_NORMAL) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color(0xFF00A3FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search added users & messages...",
                                color = Color(0xFF6A88A8),
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF00A3FF),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear Search",
                                        tint = Color(0xFF6A88A8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            color = Color.White,
                            lineHeight = 18.sp,
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0A182B),
                            unfocusedContainerColor = Color(0xFF0A182B),
                            focusedBorderColor = Color(0xFF00A3FF),
                            unfocusedBorderColor = Color(0xFF003870),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp, max = 52.dp)
                            .testTag("chats_search_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Modern Category Filter Pills matching Image 1
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterPill(
                                label = "All",
                                count = chats.size,
                                isSelected = selectedFilter == "All",
                                activeColor = electricCyan,
                                onClick = { selectedFilter = "All" }
                            )
                        }
                        item {
                            FilterPill(
                                label = "Unread",
                                count = unreadTotalCount,
                                isSelected = selectedFilter == "Unread",
                                activeColor = electricCyan,
                                onClick = { selectedFilter = "Unread" }
                            )
                        }
                        item {
                            FilterPill(
                                label = "Pinned",
                                count = pinnedTotalCount,
                                isSelected = selectedFilter == "Pinned",
                                activeColor = electricCyan,
                                onClick = { selectedFilter = "Pinned" }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // Floating Action Button
            Box(
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 12.dp)
                    .size(56.dp)
                    .shadow(4.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(electricCyan)
                    .clickable { viewModel.navigateToScreen(PlenxoScreen.DISCOVERY) }
                    .testTag("fab_add_friend"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Search and Add Users",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        containerColor = darkSurfaceBg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(darkSurfaceBg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Main Content List or Empty State
                Box(modifier = Modifier.weight(1f)) {
                    if (isLoading && chats.isEmpty()) {
                        // Premium Shimmer Loading Skeleton
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            repeat(8) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(76.dp)
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .shimmerSkeletonLoader()
                                )
                            }
                        }
                    } else if (filteredChats.isEmpty()) {
                        // EXACT USER SPECIFICATION: Display "Your list has been empty."
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(
                                                primaryColor.copy(alpha = 0.2f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                    .border(1.5.dp, primaryColor.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = "Empty State",
                                    tint = primaryColor,
                                    modifier = Modifier.size(44.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Exact string requirement
                            Text(
                                text = "Your list has been empty.",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (searchQuery.isNotEmpty()) {
                                    "No users found matching \"$searchQuery\"."
                                } else {
                                    "Search for friends using their 6-digit Plenxo ID to connect and chat securely."
                                },
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { viewModel.navigateToScreen(PlenxoScreen.DISCOVERY) },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .testTag("empty_state_add_user_button")
                                    .shadow(6.dp, RoundedCornerShape(24.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Search by Plenxo ID",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        // Display Main Users List
                        LazyColumn(
                            state = chatListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            itemsIndexed(filteredChats, key = { _, chat -> chat.chatId }) { index, chat ->
                                val recipientUid = chat.participantUids.firstOrNull { it != currentUserId } ?: "Unknown"
                                val recipientUser = usersCache[recipientUid]
                                val displayName = recipientUser?.displayName?.takeIf { it.isNotBlank() } ?: "User"
                                val rawPlenxoId = recipientUser?.plenxoId?.ifBlank { recipientUser.userCode.orEmpty() } ?: ""
                                val unreadCount = chat.unreadCounts[currentUserId] ?: 0
                                val isPinned = pinnedChatIds.contains(chat.chatId)
                                val isLocked = lockedChatIds.contains(chat.chatId)

                                val presenceMap = userPresences[recipientUid] ?: emptyMap()
                                val presenceState = presenceMap["state"] as? String ?: "offline"

                                LaunchedEffect(recipientUid) {
                                    if (recipientUid.isNotEmpty()) {
                                        viewModel.startListeningToPresence(recipientUid)
                                    }
                                }

                                ModernChatCardItem(
                                    modifier = Modifier.subtleEntrance(index = index % 10),
                                    chat = chat,
                                    recipientName = displayName,
                                    plenxoId = rawPlenxoId,
                                    profilePicUrl = recipientUser?.effectiveAvatarUrl ?: recipientUser?.profilePicUrl ?: "",
                                    profileRingId = recipientUser?.profileRingId ?: "none",
                                    unreadCount = unreadCount,
                                    primaryColor = primaryColor,
                                    isPinned = isPinned,
                                    isLocked = isLocked,
                                    presenceState = presenceState,
                                    onAvatarClick = {
                                        if (recipientUid.isNotBlank()) {
                                            viewModel.openUserProfile(recipientUid)
                                        }
                                    },
                                    onPinToggle = { viewModel.toggleChatPin(chat.chatId) },
                                    onLockToggle = {
                                        if (isLocked) {
                                            viewModel.toggleChatLock(chat.chatId)
                                            com.example.repository.SecurityRepository(context).setChatLock(chat.chatId, null)
                                            com.example.repository.SecurityRepository(context).setChatLockType(chat.chatId, null)
                                        } else {
                                            val intent = android.content.Intent(context, com.example.ui.AppLockSetupActivity::class.java).apply {
                                                putExtra("chatId", chat.chatId)
                                                if (context !is android.app.Activity) {
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            }
                                            context.startActivity(intent)
                                            viewModel.toggleChatLock(chat.chatId)
                                        }
                                    },
                                    onDeleteChat = {
                                        chatToDelete = chat
                                        if (isLocked) {
                                            showDeletePasswordDialog = true
                                        } else {
                                            showDeleteConfirmDialog = true
                                        }
                                    },
                                    onClick = {
                                        if (isLocked) {
                                            chatToUnlock = chat
                                            showUnlockPasswordDialog = true
                                            unlockPasswordText = ""
                                            unlockPasswordError = null
                                        } else {
                                            viewModel.openChatRoom(chat)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Filter Pill Component for Top Bar Selection matching Image 1
 */
@Composable
private fun FilterPill(
    label: String,
    count: Int,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val pillBg = if (isSelected) Color(0xFF0080FF) else Color(0xFF081426)
    val borderColor = if (isSelected) Color(0xFF00A3FF) else Color(0xFF004888)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(pillBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = Color.White
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.28f) else Color(0xFF003870)
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Modern Card Item for Main Screen User Conversations matching Image 1
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernChatCardItem(
    modifier: Modifier = Modifier,
    chat: ChatRoom,
    recipientName: String,
    plenxoId: String = "",
    profilePicUrl: String,
    profileRingId: String,
    unreadCount: Int,
    primaryColor: Color,
    isPinned: Boolean,
    isLocked: Boolean,
    presenceState: String = "offline",
    onAvatarClick: () -> Unit = {},
    onPinToggle: () -> Unit,
    onLockToggle: () -> Unit,
    onDeleteChat: () -> Unit,
    onClick: () -> Unit
) {
    val electricCyan = MaterialTheme.colorScheme.primary
    val electricBlue = MaterialTheme.colorScheme.primary
    val formatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = remember(chat.lastMessageTimestamp) {
        chat.lastMessageTimestamp?.let { formatter.format(Date(it)) } ?: ""
    }
    var showMenu by remember { mutableStateOf(false) }

    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val cardBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bounceCombinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Frame with Glowing Ring & Green Online Indicator matching Image 1
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A0E17))
                        .border(2.dp, Brush.linearGradient(listOf(electricCyan, electricBlue)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (profilePicUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(profilePicUrl)
                                .crossfade(true)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            placeholder = painterResource(android.R.drawable.ic_menu_gallery),
                            error = painterResource(android.R.drawable.ic_menu_report_image),
                            contentDescription = "User Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = recipientName.take(1).uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = electricCyan
                        )
                    }
                }

                // Green Presence Dot
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00FF66))
                        .border(2.dp, cardBg, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // User Info, Plenxo ID & Last Message matching Image 1
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = recipientName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (isPinned) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = electricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        if (isLocked) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Timestamp
                    if (timeString.isNotBlank()) {
                        Text(
                            text = timeString,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Plenxo ID Pill Text
                if (plenxoId.isNotBlank()) {
                    val cleanPx = plenxoId.trim().removePrefix("@").removePrefix("#")
                    val displayPx = if (cleanPx.startsWith("PX-", ignoreCase = true)) cleanPx.uppercase() else "PX-$cleanPx"
                    Text(
                        text = displayPx,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = electricCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Last Message Preview with Icon & Unread Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val isVoiceNote = chat.lastMessage.contains("voice", ignoreCase = true) || chat.lastMessage.contains("audio", ignoreCase = true)
                        Icon(
                            imageVector = if (isVoiceNote) Icons.Default.Mic else Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (chat.lastMessage.isBlank()) "Chat started" else chat.lastMessage,
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(electricCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        // Long Press Context Menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (isPinned) "Unpin Conversation" else "Pin Conversation") },
                leadingIcon = {
                    Icon(
                        imageVector = if (isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                        contentDescription = null
                    )
                },
                onClick = {
                    onPinToggle()
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text(if (isLocked) "Unlock (Disable App Lock)" else "Lock Conversation") },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Lock, contentDescription = null)
                },
                onClick = {
                    onLockToggle()
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.str_delete_chat_1), color = Color(0xFFEF4444)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                },
                onClick = {
                    onDeleteChat()
                    showMenu = false
                }
            )
        }
    }
}
