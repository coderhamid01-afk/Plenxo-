package com.example.ui.chat.components

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private val videoThumbnailCache = LruCache<String, ImageBitmap>(50)

@Composable
fun rememberVideoThumbnail(videoUrl: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(videoUrl) { mutableStateOf<ImageBitmap?>(videoThumbnailCache[videoUrl]) }

    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotBlank() && bitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                        retriever.setDataSource(videoUrl, HashMap<String, String>())
                    } else {
                        retriever.setDataSource(context, Uri.parse(videoUrl))
                    }
                    val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                    retriever.release()
                    frame?.let {
                        val imageBitmap = it.asImageBitmap()
                        videoThumbnailCache.put(videoUrl, imageBitmap)
                        bitmap = imageBitmap
                    }
                } catch (e: Exception) {
                    Log.w("VideoThumbnail", "Failed to retrieve frame for $videoUrl: ${e.message}")
                }
            }
        }
    }
    return bitmap
}

/**
 * Modern ChatBubble component with:
 * 1. Plenxo circular message status indicators (SENDING rotating ring, SENT hollow ring, READ hollow ring with blue dot, FAILED red ring).
 * 2. Real native video thumbnail rendering using MediaMetadataRetriever.
 * 3. Support for Text, Image, Video, File, and Voice Note media types.
 * 4. Pure OLED dark aesthetic matching reference image.
 */
@Composable
fun ChatBubble(
    message: Message,
    isOutgoing: Boolean,
    primaryColor: Color = Color(0xFF0084FF),
    peerAvatarUrl: String? = null,
    peerName: String? = null,
    onRetryClick: ((Message) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    onVideoClick: ((String) -> Unit)? = null
) {
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    val isFailed = message.effectiveStatus == MessageStatus.FAILED
    val outgoingBubbleBg = Color(0xFF0084FF)
    val incomingBubbleBg = Color(0xFF1A2433)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 12.dp),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOutgoing) {
            if (!peerAvatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = peerAvatarUrl,
                    contentDescription = peerName,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (peerName?.take(1) ?: "U").uppercase(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
                            Modifier.background(outgoingBubbleBg)
                        } else {
                            Modifier.background(incomingBubbleBg)
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
                                    color = Color.White,
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
                            var showVideoOverlay by remember { mutableStateOf(false) }

                            if (showVideoOverlay && videoSource.isNotBlank()) {
                                VideoPlayerOverlay(
                                    videoUrl = videoSource,
                                    onDismiss = { showVideoOverlay = false }
                                )
                            }

                            val videoThumbnail = rememberVideoThumbnail(videoSource)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (videoSource.isNotBlank()) {
                                            if (onVideoClick != null) {
                                                onVideoClick(videoSource)
                                            } else {
                                                showVideoOverlay = true
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (videoThumbnail != null) {
                                    Image(
                                        bitmap = videoThumbnail,
                                        contentDescription = "Video Thumbnail",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.25f))
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Videocam,
                                            contentDescription = "Video Message",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Video", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                    }
                                }
                                
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF0084FF).copy(alpha = 0.85f),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
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
                            val context = LocalContext.current
                            var isDownloading by remember { mutableStateOf(false) }
                            var downloadProgress by remember { mutableIntStateOf(0) }
                            var isDownloaded by remember { mutableStateOf(false) }
                            val coroutineScope = rememberCoroutineScope()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.2f))
                                    .clickable {
                                        if (fileUrl.isNotBlank() && !isDownloading && message.effectiveStatus != MessageStatus.SENDING) {
                                            isDownloading = true
                                            coroutineScope.launch {
                                                com.example.util.FileDownloadManager.downloadFile(
                                                    context = context,
                                                    fileUrl = fileUrl,
                                                    suggestedFileName = message.messageText,
                                                    onProgress = { pct -> downloadProgress = pct },
                                                    onResult = { success, _ ->
                                                        isDownloading = false
                                                        if (success) isDownloaded = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.InsertDriveFile,
                                                contentDescription = "File Attachment",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
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
                                    val statusText = when {
                                        message.effectiveStatus == MessageStatus.SENDING -> "Uploading... ${message.uploadProgress}%"
                                        isDownloading -> if (downloadProgress > 0) "Downloading $downloadProgress%" else "Downloading..."
                                        isDownloaded -> "Downloaded"
                                        else -> "Tap to download"
                                    }
                                    Text(
                                        text = statusText,
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
                            val context = LocalContext.current
                            val audioPlayerManager = remember(context) { com.example.media.AudioPlayerManager.getInstance(context) }
                            VoiceNoteBubble(
                                audioUrl = audioUrl,
                                isSentByCurrentUser = isOutgoing,
                                audioPlayerManager = audioPlayerManager
                            )
                        }
                        else -> {
                            Text(
                                text = message.messageText,
                                color = Color.White,
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

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
                                size = 13.dp
                            )
                        }
                    }
                }
            }

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
                        text = "Failed. Tap to retry",
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
 * Custom Plenxo Circular Message Status Indicator Component:
 * - SENDING: Smooth 360-degree rotating hollow circular ring.
 * - SENT / DELIVERED: Clean static hollow circular ring.
 * - READ / SEEN: Hollow circular ring with a solid blue center dot.
 * - FAILED: Red circular error indicator.
 */
@Composable
fun MessageStatusIcon(
    status: MessageStatus,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    val isRead = status == MessageStatus.READ
    val isSending = status == MessageStatus.SENDING
    val isFailed = status == MessageStatus.FAILED

    val infiniteTransition = rememberInfiniteTransition(label = "sending_rotation")
    val rotationAngle by if (isSending) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val blueAccent = Color(0xFF0084FF)
    val strokeColor = when {
        isFailed -> Color(0xFFEF4444)
        isRead -> blueAccent
        else -> Color.White.copy(alpha = 0.75f)
    }

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Message status: ${status.name}" }
    ) {
        val radius = size.toPx() / 2f
        val centerOffset = Offset(size.toPx() / 2f, size.toPx() / 2f)
        val strokeWidth = 1.25.dp.toPx()

        if (isFailed) {
            drawCircle(color = strokeColor, radius = radius - strokeWidth, style = Stroke(width = strokeWidth))
            drawCircle(color = strokeColor, center = centerOffset, radius = radius * 0.4f)
        } else if (isSending) {
            rotate(rotationAngle, pivot = centerOffset) {
                drawArc(
                    color = strokeColor,
                    startAngle = 0f,
                    sweepAngle = 280f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        } else if (isRead) {
            drawCircle(color = blueAccent, radius = radius - strokeWidth, style = Stroke(width = strokeWidth))
            drawCircle(color = blueAccent, center = centerOffset, radius = radius * 0.45f)
        } else {
            drawCircle(color = strokeColor, radius = radius - strokeWidth, style = Stroke(width = strokeWidth))
        }
    }
}
