package com.example.ui.chat.components

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Message
import com.example.model.MessageStatus
import com.example.ui.components.VoiceNoteBubble
import java.text.SimpleDateFormat
import java.util.*

/**
 * Modern ChatBubble component with:
 * 1. Inline message status indicators (SENDING clock, SENT ✓, DELIVERED ✓✓, READ ✓✓ blue, FAILED ⚠).
 * 2. Tap-to-retry logic for failed message dispatches.
 * 3. Support for Text, Image (local Uri & remote URL), and Voice Note media types.
 * 4. Zero blocking modal overlays.
 */
@Composable
fun ChatBubble(
    message: Message,
    isOutgoing: Boolean,
    primaryColor: Color = Color(0xFF58A6FF),
    peerAvatarUrl: String? = null,
    peerName: String? = null,
    onRetryClick: ((Message) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null
) {
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    

    val textColor = Color.White
    val isFailed = message.effectiveStatus == MessageStatus.FAILED

    Row(
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
            ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Content based on messageType
                when (message.messageType.uppercase()) {
                    "IMAGE" -> {
                        val imageSource = message.localUri ?: message.mediaUrl
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable {
                                    if (!imageSource.isNullOrEmpty() && onImageClick != null) {
                                        onImageClick(imageSource)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!imageSource.isNullOrEmpty()) {
                                AsyncImage(
                                    model = imageSource,
                                    contentDescription = "Shared Image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            // Inline upload overlay on top of image
                            if (message.effectiveStatus == MessageStatus.SENDING) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        progress = { message.uploadProgress / 100f },
                                        modifier = Modifier.size(36.dp),
                                        color = Color.White,
                                        trackColor = Color.White.copy(alpha = 0.3f),
                                    )
                                }
                            }
                        }
                        if (message.messageText.isNotBlank() && message.messageText != "📷 Photo") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = message.messageText,
                                color = textColor,
                                fontSize = 15.sp,
                                lineHeight = 21.sp
                            )
                        }
                    }
                    "VIDEO" -> {
                        val videoSource = if (!message.mediaUrl.isNullOrBlank()) {
                            message.mediaUrl
                        } else {
                            message.localUri ?: ""
                        }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable {
                                    if (videoSource.isNotBlank()) {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.parse(videoSource), "video/*")
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            try {
                                                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(videoSource)).apply {
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(fallbackIntent)
                                            } catch (e2: Exception) {
                                                android.widget.Toast.makeText(context, "Cannot play video: ${e.message ?: e2.message}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (videoSource.isNotBlank()) {
                                AsyncImage(
                                    model = videoSource,
                                    contentDescription = "Video Thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            Surface(
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
                            }

                            if (message.effectiveStatus == MessageStatus.SENDING) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            progress = { message.uploadProgress / 100f },
                                            modifier = Modifier.size(36.dp),
                                            color = Color.White,
                                            trackColor = Color.White.copy(alpha = 0.3f),
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("${message.uploadProgress}%", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    "FILE" -> {
                        val fileUrl = message.mediaUrl.ifBlank { message.localUri ?: "" }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .clickable {
                                    if (fileUrl.isNotBlank()) {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(fileUrl)).apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Opening file link...", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = primaryColor.copy(alpha = 0.3f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = "File Attachment",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.messageText.ifBlank { "Attachment File" },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (message.effectiveStatus == MessageStatus.SENDING) "Uploading... ${message.uploadProgress}%" else "Tap to download/view",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    "TEXT_ASSET" -> {
                        val fileUrl = message.mediaUrl
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .clickable {
                                    if (fileUrl.isNotBlank()) {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(fileUrl)).apply {
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Opening document...", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Large Document",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.messageText,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (message.effectiveStatus == MessageStatus.SENDING) "Uploading text asset... ${message.uploadProgress}%" else "Stored on Catbox • Tap to open",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    "VOICE", "AUDIO", "VOICE_NOTE" -> {
                        val audioUrl = if (message.mediaUrl.isNotBlank() && (message.mediaUrl.startsWith("http://") || message.mediaUrl.startsWith("https://"))) {
                            message.mediaUrl
                        } else {
                            message.localUri?.takeIf { it.isNotBlank() } ?: message.mediaUrl
                        }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val audioPlayerManager = remember(context) { com.example.media.AudioPlayerManager.getInstance(context) }
                        VoiceNoteBubble(
                            audioUrl = audioUrl,
                            isSentByCurrentUser = isOutgoing,
                            audioPlayerManager = audioPlayerManager
                        )
                    }
                    else -> {
                        // Standard Text Message
                        Text(
                            text = message.messageText,
                            color = textColor,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Footer row: Timestamp & Inline Status Indicator
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val formattedTime = remember(message.timestamp) {
                        message.timestamp?.let {
                            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it))
                        } ?: ""
                    }

                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )

                    if (isOutgoing) {
                        MessageStatusIcon(
                            status = message.effectiveStatus,
                            size = 12.dp
                        )
                    }
                }
            }
        }

        // Tap to retry caption for failed messages
        if (isOutgoing && isFailed) {
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
}

/**
 * Custom Message Status Indicator component for chat bubbles with Canvas rendering:
 * 1. SENDING: Subtle hollow gray circle outline.
 * 2. SENT (Delivered): Clean gray circle ring (hollow center, thin gray border).
 * 3. SEEN (Read): Bright blue circle ring with a solid blue dot filled right in the center.
 * 4. FAILED: Red circle ring with red dot indicator.
 */
@Composable
fun MessageStatusIcon(
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
}
