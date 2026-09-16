import re

with open("app/src/main/java/com/example/ui/chat/ChatDetailScreen.kt", "r") as f:
    content = f.read()

# 1. Update background logic
old_bg = """    Box(
        modifier = Modifier
            .fillMaxSize()
            // Polished dark-gradient fallback instead of a flat near-black box, so the
            // screen never looks like an empty void when no wallpaper is selected.
            .background(
                Brush.verticalGradient(
                    colors = listOf(PlenxoColors.Background, Color(0xFF131824), PlenxoColors.Background)
                )
            )
    ) {
        if (hasCustomWallpaper) {
            WallpaperRenderer(activeWallpaperId)
        }"""
new_bg = """    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (hasCustomWallpaper) Color.Transparent else Color.Black)
    ) {
        if (hasCustomWallpaper) {
            WallpaperRenderer(activeWallpaperId)
            // Subtle dark scrim layer to ensure contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }"""
content = content.replace(old_bg, new_bg)


# 2. Update Top Action Bar
# Search for TopAppBar and replace it with the custom Floating Top Action Bar
old_topbar = """                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.navigateToScreen(PlenxoScreen.HOME) },
                            modifier = Modifier.testTag("chat_detail_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (recipientUid.isNotBlank()) {
                                        viewModel.openUserProfile(recipientUid)
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("chat_recipient_avatar")
                                    .clickable {
                                        if (recipientUid.isNotBlank()) {
                                            viewModel.openUserProfile(recipientUid)
                                        }
                                    }
                            ) {
                                ProfileImageWithRing(
                                    imageUrl = profilePicUrl,
                                    profileRingId = profileRingId,
                                    fallbackInitial = recipientName.take(1).uppercase(),
                                    modifier = Modifier.fillMaxSize(),
                                    ringBorderWidth = 5
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (presenceStatus == "online") Color(0xFF34C759) else Color(0xFF8E8E93))
                                        .border(1.5.dp, Color(0xFF131824), CircleShape)
                                        .align(Alignment.BottomEnd)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = recipientName.ifEmpty { "Chat" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "End-to-End Encrypted",
                                        tint = primaryColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                val isTyping = typingUsers.containsKey(chatId)
                                val cleanRecipientPxId = recipientPlenxoId.trim().removePrefix("@").removePrefix("#")
                                val plenxoIdDisplay = if (cleanRecipientPxId.isNotBlank()) {
                                    if (cleanRecipientPxId.startsWith("PX-", ignoreCase = true)) {
                                        "PX-${cleanRecipientPxId.removePrefix("PX-").removePrefix("px-")}"
                                    } else if (cleanRecipientPxId.length == 6 && cleanRecipientPxId.all { it.isDigit() }) {
                                        "PX-$cleanRecipientPxId"
                                    } else {
                                        "PX-$cleanRecipientPxId"
                                    }
                                } else ""
                                Text(
                                    text = when {
                                        isTyping -> "Typing..."
                                        plenxoIdDisplay.isNotEmpty() -> "$plenxoIdDisplay • ${if (presenceStatus == "online") "Online" else "Offline"}"
                                        presenceStatus == "online" -> "Online"
                                        else -> "Offline"
                                    },
                                    fontSize = 12.sp,
                                    color = if (isTyping) primaryColor else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (recipientUid.isNotBlank()) {
                                    callPermissionController.startVoiceCallWithPermission {
                                        CallManager.startOutgoingCall(
                                            peerUid = recipientUid,
                                            peerName = recipientName.ifBlank { "Plenxo User" },
                                            peerAvatar = profilePicUrl,
                                            peerPlenxoId = recipientPlenxoId,
                                            callType = CallType.VOICE,
                                            onSaveLog = { log -> viewModel.recordCallLog(log) }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag("chat_voice_call_button")
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Voice Call", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                if (recipientUid.isNotBlank()) {
                                    callPermissionController.startVideoCallWithPermission {
                                        CallManager.startOutgoingCall(
                                            peerUid = recipientUid,
                                            peerName = recipientName.ifBlank { "Plenxo User" },
                                            peerAvatar = profilePicUrl,
                                            peerPlenxoId = recipientPlenxoId,
                                            callType = CallType.VIDEO,
                                            onSaveLog = { log -> viewModel.recordCallLog(log) }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag("chat_video_call_button")
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = Color.White)
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = InputBarSurface.copy(alpha = 0.95f)
                    )
                )"""

new_topbar = """                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF0A101D).copy(alpha = 0.8f))
                        .border(1.dp, Color(0xFF00B0FF).copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.navigateToScreen(PlenxoScreen.HOME) },
                            modifier = Modifier.testTag("chat_detail_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (recipientUid.isNotBlank()) {
                                        viewModel.openUserProfile(recipientUid)
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("chat_recipient_avatar")
                            ) {
                                ProfileImageWithRing(
                                    imageUrl = profilePicUrl,
                                    profileRingId = profileRingId,
                                    fallbackInitial = recipientName.take(1).uppercase(),
                                    modifier = Modifier.fillMaxSize(),
                                    ringBorderWidth = 5
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (presenceStatus == "online") Color(0xFF34C759) else Color(0xFF8E8E93))
                                        .border(1.5.dp, Color(0xFF131824), CircleShape)
                                        .align(Alignment.BottomEnd)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = recipientName.ifEmpty { "Chat" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "End-to-End Encrypted",
                                        tint = primaryColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                val isTyping = typingUsers.containsKey(chatId)
                                val cleanRecipientPxId = recipientPlenxoId.trim().removePrefix("@").removePrefix("#")
                                val plenxoIdDisplay = if (cleanRecipientPxId.isNotBlank()) {
                                    if (cleanRecipientPxId.startsWith("PX-", ignoreCase = true)) {
                                        "PX-${cleanRecipientPxId.removePrefix("PX-").removePrefix("px-")}"
                                    } else if (cleanRecipientPxId.length == 6 && cleanRecipientPxId.all { it.isDigit() }) {
                                        "PX-$cleanRecipientPxId"
                                    } else {
                                        "PX-$cleanRecipientPxId"
                                    }
                                } else ""
                                Text(
                                    text = when {
                                        isTyping -> "Typing..."
                                        plenxoIdDisplay.isNotEmpty() -> "$plenxoIdDisplay • ${if (presenceStatus == "online") "Online" else "Offline"}"
                                        presenceStatus == "online" -> "Online"
                                        else -> "Offline"
                                    },
                                    fontSize = 12.sp,
                                    color = if (isTyping) primaryColor else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (recipientUid.isNotBlank()) {
                                    callPermissionController.startVoiceCallWithPermission {
                                        CallManager.startOutgoingCall(
                                            peerUid = recipientUid,
                                            peerName = recipientName.ifBlank { "Plenxo User" },
                                            peerAvatar = profilePicUrl,
                                            peerPlenxoId = recipientPlenxoId,
                                            callType = CallType.VOICE,
                                            onSaveLog = { log -> viewModel.recordCallLog(log) }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag("chat_voice_call_button")
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Voice Call", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                if (recipientUid.isNotBlank()) {
                                    callPermissionController.startVideoCallWithPermission {
                                        CallManager.startOutgoingCall(
                                            peerUid = recipientUid,
                                            peerName = recipientName.ifBlank { "Plenxo User" },
                                            peerAvatar = profilePicUrl,
                                            peerPlenxoId = recipientPlenxoId,
                                            callType = CallType.VIDEO,
                                            onSaveLog = { log -> viewModel.recordCallLog(log) }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag("chat_video_call_button")
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = Color.White)
                        }
                    }
                }"""
content = content.replace(old_topbar, new_topbar)

# 3. Add peerAvatarUrl to ChatBubble call
content = content.replace(
"""                            ChatBubble(
                                message = msg,
                                isOutgoing = isOutgoing,
                                primaryColor = primaryColor,
                                onRetryClick = { failedMsg -> viewModel.retryFailedMessage(failedMsg) }
                            )""",
"""                            ChatBubble(
                                message = msg,
                                isOutgoing = isOutgoing,
                                primaryColor = primaryColor,
                                peerAvatarUrl = profilePicUrl,
                                peerName = recipientName,
                                onRetryClick = { failedMsg -> viewModel.retryFailedMessage(failedMsg) }
                            )"""
)

# 4. Input Dock style changes
# Float bottom pill container (#0A101D) with neon cyan border highlight (#00E5FF)
old_input_bg = """            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF1E293B))
            .border(
                width = 1.dp,
                color = if (isFocused) Color(0xFF22C55E).copy(alpha = 0.60f) else Color(0xFF334155),
                shape = RoundedCornerShape(28.dp)
            )"""
new_input_bg = """            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF0A101D).copy(alpha = 0.8f))
            .border(
                width = 1.dp,
                color = if (isFocused) Color(0xFF00E5FF) else Color(0xFF00E5FF).copy(alpha = 0.5f),
                shape = RoundedCornerShape(28.dp)
            )"""
content = content.replace(old_input_bg, new_input_bg)

# Update Circular blue Send/Mic button in RoundActionButton usages
content = content.replace(
"""                            InputAction.SEND_TEXT -> RoundActionButton(
                                icon = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                testTag = "send_message_button",
                                containerColor = Color(0xFF22C55E),
                                onClick = onSendText
                            )
                            InputAction.SEND_VOICE -> RoundActionButton(
                                icon = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send voice message",
                                testTag = "send_voice_button",
                                containerColor = Color(0xFF22C55E),
                                onClick = { stopAndSendRecording() }
                            )
                            InputAction.RECORD -> RoundActionButton(
                                icon = Icons.Default.Mic,
                                contentDescription = "Record",
                                testTag = "record_voice_button",
                                containerColor = Color(0xFF22C55E),
                                onClick = { beginRecording() }
                            )""",
"""                            InputAction.SEND_TEXT -> RoundActionButton(
                                icon = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                testTag = "send_message_button",
                                containerColor = Color(0xFF0066FF),
                                onClick = onSendText
                            )
                            InputAction.SEND_VOICE -> RoundActionButton(
                                icon = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send voice message",
                                testTag = "send_voice_button",
                                containerColor = Color(0xFF0066FF),
                                onClick = { stopAndSendRecording() }
                            )
                            InputAction.RECORD -> RoundActionButton(
                                icon = Icons.Default.Mic,
                                contentDescription = "Record",
                                testTag = "record_voice_button",
                                containerColor = Color(0xFF0066FF),
                                onClick = { beginRecording() }
                            )"""
)

with open("app/src/main/java/com/example/ui/chat/ChatDetailScreen.kt", "w") as f:
    f.write(content)
