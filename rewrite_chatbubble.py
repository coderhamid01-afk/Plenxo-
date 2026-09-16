import re

with open("app/src/main/java/com/example/ui/chat/components/ChatBubble.kt", "r") as f:
    content = f.read()

# Add peerAvatarUrl to ChatBubble signature
content = content.replace(
    "fun ChatBubble(\n    message: Message,\n    isOutgoing: Boolean,\n    primaryColor: Color = Color(0xFF58A6FF),\n    onRetryClick: ((Message) -> Unit)? = null,\n    onLongClick: (() -> Unit)? = null,\n    onImageClick: ((String) -> Unit)? = null\n) {",
    "fun ChatBubble(\n    message: Message,\n    isOutgoing: Boolean,\n    primaryColor: Color = Color(0xFF58A6FF),\n    peerAvatarUrl: String? = null,\n    peerName: String? = null,\n    onRetryClick: ((Message) -> Unit)? = null,\n    onLongClick: (() -> Unit)? = null,\n    onImageClick: ((String) -> Unit)? = null\n) {"
)

# Fix Column wrapper and add Row for incoming avatar
old_layout = """    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 10.dp),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .testTag("chat_bubble_${message.messageId}")
                .clip(bubbleShape)
                .then(
                    if (isFailed) Modifier.border(1.dp, Color(0xFFEF4444), bubbleShape)
                    else if (!isOutgoing) Modifier.border(1.dp, Color(0xFF374151), bubbleShape)
                    else Modifier
                )
                .clickable {
                    if (isFailed && onRetryClick != null) {
                        onRetryClick(message)
                    } else if (onLongClick != null) {
                        onLongClick()
                    }
                },
            shape = bubbleShape,
            color = bubbleBg,
            shadowElevation = 1.dp
        ) {"""

new_layout = """    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 10.dp),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOutgoing) {
            if (peerAvatarUrl != null) {
                AsyncImage(
                    model = peerAvatarUrl,
                    contentDescription = peerName,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF1E293B)))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .testTag("chat_bubble_${message.messageId}")
                    .clip(bubbleShape)
                    .then(
                        if (isFailed) {
                            Modifier.background(Color(0xFF451A1D)).border(1.dp, Color(0xFFEF4444), bubbleShape)
                        } else if (isOutgoing) {
                            Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF0052D4), Color(0xFF0066FF))))
                        } else {
                            Modifier.background(Color(0xFF12192A).copy(alpha = 0.85f)).border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), bubbleShape)
                        }
                    )
                    .clickable {
                        if (isFailed && onRetryClick != null) {
                            onRetryClick(message)
                        } else if (onLongClick != null) {
                            onLongClick()
                        }
                    }
            ) {"""
content = content.replace(old_layout, new_layout)

content = content.replace("val bubbleBg = if (isOutgoing) {\n        if (message.effectiveStatus == MessageStatus.FAILED) Color(0xFF451A1D) else primaryColor\n    } else {\n        Color(0xFF1F2937)\n    }", "")


# Update Video Card
# Video Cards: Full-bleed rounded thumbnail with cyan outer border glow, center translucent play button, and duration/timestamp badges.
old_video = """                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A))
                                .clickable {"""
new_video = """                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable {"""
content = content.replace(old_video, new_video)

# Fix play button in Video Card to translucent cyan/white
old_play = """                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }"""
new_play = """                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF00B0FF).copy(alpha = 0.3f),
                                modifier = Modifier.size(52.dp).border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), CircleShape)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }"""
content = content.replace(old_play, new_play)

# Close Row properly for ChatBubble
# old was: } \n        // Tap to retry
old_end = """                }
            }
        }
        // Tap to retry caption for failed messages"""
new_end = """                }
            }
        }
        // Tap to retry caption for failed messages"""
content = content.replace(old_end, new_end) # Actually wait, Column wraps the Box now, and Row wraps Column.

# The original had:
# Column {
#   Surface {
#      Column { ... }
#   }
#   Row (Tap to retry)
# }
# New is:
# Row {
#   if (!isOutgoing) { Avatar }
#   Column {
#     Box {
#       Column { ... }
#     }
#     Row (Tap to retry)
#   }
# }

# Let's fix the ending correctly
content = content.replace(
"""        if (isOutgoing && isFailed) {
            Row(
                modifier = Modifier
                    .padding(top = 2.dp, end = 4.dp)
                    .clickable { onRetryClick?.invoke(message) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Failed to send. Tap to retry",
                    color = Color(0xFFEF4444),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}""",
"""        if (isOutgoing && isFailed) {
            Row(
                modifier = Modifier
                    .padding(top = 2.dp, end = 4.dp)
                    .clickable { onRetryClick?.invoke(message) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Failed to send. Tap to retry",
                    color = Color(0xFFEF4444),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    }
}""") # Added one more closing brace for the Row


# Update MessageStatusIcon
old_status = """fun MessageStatusIcon(
    status: MessageStatus,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp
) {
    val isRead = status == MessageStatus.READ
    val isSentOrDelivered = status == MessageStatus.SENT || status == MessageStatus.DELIVERED
    val isFailed = status == MessageStatus.FAILED

    // Smooth color transitions
    val ringColor by animateColorAsState(
        targetValue = when {
            isFailed -> Color(0xFFEF4444)
            isRead -> Color(0xFF2997FF) // Bright blue for SEEN / READ
            isSentOrDelivered -> Color(0xFF9CA3AF) // Clean gray ring for SENT / DELIVERED
            else -> Color(0xFF8E8E93).copy(alpha = 0.5f) // Subtle hollow gray outline for SENDING
        },
        animationSpec = tween(durationMillis = 300),
        label = "status_ring_color"
    )

    // Smooth spring scale for inner dot
    val innerDotScale by animateFloatAsState(
        targetValue = if (isRead) 1f else if (isFailed) 0.6f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "inner_dot_scale"
    )

    val strokeWidthDp = 1.3.dp

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Message status: ${status.name}" }
    ) {
        val strokeWidthPx = strokeWidthDp.toPx()
        val radius = (this.size.minDimension - strokeWidthPx) / 2f
        val centerPoint = center

        // 1. Outer Ring
        drawCircle(
            color = ringColor,
            radius = radius,
            center = centerPoint,
            style = Stroke(width = strokeWidthPx)
        )

        // 2. Solid Inner Center Dot (for SEEN / READ state or FAILED state)
        if (innerDotScale > 0f) {
            val maxInnerRadius = radius - strokeWidthPx * 1.5f
            val currentInnerRadius = (maxInnerRadius * innerDotScale).coerceAtLeast(0f)
            drawCircle(
                color = ringColor,
                radius = currentInnerRadius,
                center = centerPoint
            )
        }
    }
}"""
new_status = """fun MessageStatusIcon(
    status: MessageStatus,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    val isRead = status == MessageStatus.READ
    val isSentOrDelivered = status == MessageStatus.SENT || status == MessageStatus.DELIVERED
    val isFailed = status == MessageStatus.FAILED
    val isSending = status == MessageStatus.SENDING

    val iconColor = when {
        isFailed -> Color(0xFFEF4444)
        isRead || isSentOrDelivered -> Color(0xFF00E5FF) // cyan checkmarks
        else -> Color(0xFF8E8E93).copy(alpha = 0.5f)
    }

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Message status: ${status.name}" }
    ) {
        val strokeWidthPx = 1.5.dp.toPx()
        if (isFailed) {
            drawCircle(color = iconColor, radius = size.toPx() / 3f)
        } else if (isSending) {
            drawCircle(color = iconColor, radius = size.toPx() / 2.5f, style = Stroke(width = 1.dp.toPx()))
        } else {
            // Checkmark 1
            val path1 = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.toPx() * 0.15f, size.toPx() * 0.55f)
                lineTo(size.toPx() * 0.4f, size.toPx() * 0.8f)
                lineTo(size.toPx() * 0.8f, size.toPx() * 0.3f)
            }
            drawPath(
                path = path1,
                color = iconColor,
                style = Stroke(width = strokeWidthPx, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
            )
            // Checkmark 2
            if (isRead || isSentOrDelivered) {
                val path2 = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.toPx() * 0.45f, size.toPx() * 0.55f)
                    lineTo(size.toPx() * 0.7f, size.toPx() * 0.8f)
                    lineTo(size.toPx() * 1.1f, size.toPx() * 0.3f)
                }
                drawPath(
                    path = path2,
                    color = iconColor,
                    style = Stroke(width = strokeWidthPx, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                )
            }
        }
    }
}"""
content = content.replace(old_status, new_status)

# Need to ensure Arrangement is imported
if "import androidx.compose.foundation.layout.Arrangement" not in content:
    content = content.replace("import androidx.compose.foundation.layout.*", "import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.layout.Arrangement")


with open("app/src/main/java/com/example/ui/chat/components/ChatBubble.kt", "w") as f:
    f.write(content)
