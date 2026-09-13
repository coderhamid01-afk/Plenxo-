@file:Suppress("DEPRECATION")
package com.example.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.model.Message
import com.example.model.MessagePayload
import com.example.model.MessageStatus
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

sealed interface MediaUploadState {
    object Idle : MediaUploadState
    data class Progress(val percentage: Int) : MediaUploadState
    data class Success(val downloadUrl: String) : MediaUploadState
    data class Error(val message: String, val throwable: Throwable? = null) : MediaUploadState
}

class CloudChatRepositoryImpl(private val context: Context) : ChatDataRepository {

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private val catboxRepo: CatboxRepository by lazy { CatboxRepositoryImpl() }

    override suspend fun saveMessage(message: MessagePayload) {
        try {
            val messageData = mapOf(
                "messageId" to message.messageId,
                "chatId" to message.chatId,
                "senderId" to message.senderId,
                "receiverId" to message.receiverId,
                "messageText" to message.messageText,
                "messageType" to message.messageType,
                "mediaUrl" to message.mediaUrl,
                "timestamp" to message.timestamp,
                "status" to message.status,
                "expiresAt" to message.expiresAt,
                "replyToMessageId" to message.replyToMessageId,
                "isEdited" to message.isEdited,
                "senderActiveFontId" to message.senderActiveFontId,
                "createdAt" to message.timestamp
            )

            firestore.collection("messages").document(message.messageId).set(messageData, SetOptions.merge()).await()

            if (message.chatId.isNotBlank()) {
                try {
                    firestore.collection("chats").document(message.chatId)
                        .collection("messages").document(message.messageId)
                        .set(messageData, SetOptions.merge()).await()
                } catch (subEx: Exception) {
                    Log.e("CloudChatRepo", "Failed to save message under /chats/${message.chatId}/messages: ${subEx.message}")
                }
            }

            val lastMessageSummary = when (message.messageType.uppercase()) {
                "IMAGE" -> "📷 Photo"
                "VIDEO" -> "📹 Video"
                "VOICE", "AUDIO" -> "🎤 Voice Note"
                "FILE" -> "📁 File Attachment"
                "TEXT_ASSET" -> "📄 Large Text Document"
                else -> if (message.messageText.startsWith("http")) "📄 Document Attachment" else message.messageText
            }

            val participantsList = if (message.senderId.isNotBlank() && message.receiverId.isNotBlank()) {
                listOf(message.senderId, message.receiverId)
            } else if (message.chatId.contains("_")) {
                message.chatId.split("_")
            } else {
                emptyList()
            }
            val participantsMap = participantsList.associateWith { true }

            val chatUpdate = mutableMapOf<String, Any>(
                "chatId" to message.chatId,
                "lastMessage" to lastMessageSummary,
                "lastMessageTimestamp" to message.timestamp,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (participantsList.isNotEmpty()) {
                chatUpdate["participantUids"] = participantsList
                chatUpdate["participants"] = participantsMap
                chatUpdate["user1Id"] = participantsList[0]
                if (participantsList.size > 1) {
                    chatUpdate["user2Id"] = participantsList[1]
                }
            }

            if (message.chatId.isNotBlank()) {
                firestore.collection("chats").document(message.chatId)
                    .set(chatUpdate, SetOptions.merge())
                    .await()
            }
        } catch (e: Exception) {
            Log.e("CloudChatRepo", "Failed to save message in Firestore: ${e.message}", e)
            throw e
        }
    }

    suspend fun saveFullMessage(message: Message) {
        val payload = MessagePayload(
            messageId = message.messageId,
            chatId = message.chatId,
            senderId = message.senderId,
            receiverId = message.receiverId,
            messageText = message.messageText,
            messageType = message.messageType,
            mediaUrl = message.mediaUrl,
            timestamp = message.timestamp ?: System.currentTimeMillis(),
            status = message.effectiveStatus.name,
            expiresAt = message.expiresAt
        )
        saveMessage(payload)
    }

    override suspend fun uploadMediaAsset(fileUri: Uri): String {
        val randomId = UUID.randomUUID().toString()
        return uploadChatImage(chatId = "general", messageId = randomId, fileUri = fileUri)
    }

    /**
     * Uploads an image directly to Catbox with real-time progress reporting.
     */
    suspend fun uploadChatImage(
        chatId: String,
        messageId: String,
        fileUri: Uri,
        onProgress: (Int) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        if (chatId.isBlank() || messageId.isBlank()) {
            throw IllegalArgumentException("Invalid chatId or messageId for image upload")
        }

        Log.d("CloudChatRepo", "Starting Catbox image upload for chat $chatId, message $messageId, uri: $fileUri")

        try {
            val downloadUrl = catboxRepo.uploadUri(context, fileUri, "image/jpeg", onProgress)
            Log.d("CloudChatRepo", "Image successfully uploaded to Catbox: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e("CloudChatRepo", "Catbox image upload failed for message $messageId: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to upload image to Catbox. Please try again.", Toast.LENGTH_SHORT).show()
            }
            throw e
        }
    }

    /**
     * Uploads a video directly to Catbox with real-time progress reporting.
     */
    suspend fun uploadChatVideo(
        chatId: String,
        messageId: String,
        fileUri: Uri,
        onProgress: (Int) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        if (chatId.isBlank() || messageId.isBlank()) {
            throw IllegalArgumentException("Invalid chatId or messageId for video upload")
        }

        Log.d("CloudChatRepo", "Starting Catbox video upload for chat $chatId, message $messageId, uri: $fileUri")

        try {
            val downloadUrl = catboxRepo.uploadUri(context, fileUri, "video/mp4", onProgress)
            Log.d("CloudChatRepo", "Video successfully uploaded to Catbox: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e("CloudChatRepo", "Catbox video upload failed for message $messageId: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to upload video to Catbox. Please try again.", Toast.LENGTH_SHORT).show()
            }
            throw e
        }
    }

    /**
     * Uploads a generic file/document to Catbox with real-time progress reporting.
     */
    suspend fun uploadChatFile(
        chatId: String,
        messageId: String,
        fileUri: Uri,
        mimeType: String = "application/octet-stream",
        onProgress: (Int) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        if (chatId.isBlank() || messageId.isBlank()) {
            throw IllegalArgumentException("Invalid chatId or messageId for file upload")
        }

        Log.d("CloudChatRepo", "Starting Catbox file upload for chat $chatId, message $messageId, uri: $fileUri")

        try {
            val downloadUrl = catboxRepo.uploadUri(context, fileUri, mimeType, onProgress)
            Log.d("CloudChatRepo", "File successfully uploaded to Catbox: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e("CloudChatRepo", "Catbox file upload failed for message $messageId: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to upload file to Catbox. Please try again.", Toast.LENGTH_SHORT).show()
            }
            throw e
        }
    }

    /**
     * Uploads a voice note directly to Catbox.
     */
    suspend fun uploadVoiceNote(
        chatId: String,
        messageId: String,
        fileUri: Uri,
        onProgress: (Int) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        if (chatId.isBlank() || messageId.isBlank()) {
            throw IllegalArgumentException("Invalid chatId or messageId for voice note upload")
        }

        Log.d("CloudChatRepo", "Starting Catbox voice note upload for chat $chatId, message $messageId, uri: $fileUri")

        try {
            val downloadUrl = catboxRepo.uploadUri(context, fileUri, "audio/mp4", onProgress)
            Log.d("CloudChatRepo", "Voice note successfully uploaded to Catbox: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e("CloudChatRepo", "Catbox voice note upload failed: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to upload voice note to Catbox. Please try again.", Toast.LENGTH_SHORT).show()
            }
            throw e
        }
    }

    /**
     * Uploads a heavy text payload to Catbox.
     */
    suspend fun uploadLargeTextPayload(
        text: String,
        onProgress: (Int) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        try {
            val fileName = "text_payload_${System.currentTimeMillis()}.txt"
            catboxRepo.uploadTextPayload(text, fileName, onProgress)
        } catch (e: Exception) {
            Log.e("CloudChatRepo", "Catbox text payload upload failed: ${e.message}", e)
            throw e
        }
    }

    suspend fun sendImageMessage(
        chatId: String,
        senderId: String,
        receiverId: String,
        imageUri: Uri,
        onProgress: (Int) -> Unit = {}
    ): MessagePayload {
        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val initialPayload = MessagePayload(
            messageId = messageId,
            chatId = chatId,
            senderId = senderId,
            receiverId = receiverId,
            messageText = "📷 Photo",
            messageType = "IMAGE",
            mediaUrl = "",
            timestamp = now,
            status = "SENDING"
        )

        try {
            saveMessage(initialPayload)
        } catch (e: Exception) {
            Log.w("CloudChatRepo", "Failed to write initial sending placeholder message: ${e.message}")
        }

        return try {
            val downloadUrl = uploadChatImage(chatId, messageId, imageUri, onProgress)

            val finalPayload = initialPayload.copy(
                mediaUrl = downloadUrl,
                messageText = "📷 Photo",
                status = "SENT"
            )

            saveMessage(finalPayload)
            Log.d("CloudChatRepo", "Successfully completed image sending flow for message $messageId")
            finalPayload
        } catch (e: Exception) {
            Log.e("CloudChatRepo", "Image message sending flow failed for $messageId: ${e.message}", e)

            val failedPayload = initialPayload.copy(status = "FAILED")
            try {
                saveMessage(failedPayload)
            } catch (saveEx: Exception) {
                Log.e("CloudChatRepo", "Failed to update message status to FAILED: ${saveEx.message}")
            }
            throw e
        }
    }

    override fun streamMessages(chatId: String, limit: Int, offset: Int): Flow<List<MessagePayload>> = callbackFlow {
        if (chatId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val query = firestore.collection("messages")
            .whereEqualTo("chatId", chatId)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("CloudChatRepo", "Error listening for messages in chat $chatId: ${error.message}", error)
                return@addSnapshotListener
            }
            if (snapshot == null) {
                trySend(emptyList())
                return@addSnapshotListener
            }

            val messages = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val msgId = (data["messageId"] as? String) ?: doc.id
                val sId = (data["senderId"] as? String) ?: (data["senderUid"] as? String) ?: ""
                val rId = (data["receiverId"] as? String) ?: (data["receiverUid"] as? String) ?: ""
                val msgText = (data["messageText"] as? String) ?: (data["text"] as? String) ?: ""
                val msgType = (data["messageType"] as? String) ?: (data["type"] as? String) ?: "TEXT"
                val mUrl = (data["mediaUrl"] as? String) ?: ""
                val ts = (data["timestamp"] as? Long) ?: (data["createdAt"] as? Long) ?: System.currentTimeMillis()
                val st = (data["status"] as? String) ?: "SENT"
                val exp = (data["expiresAt"] as? Long)
                val replyTo = (data["replyToMessageId"] as? String)
                val isEd = (data["isEdited"] as? Boolean) ?: false
                val font = (data["senderActiveFontId"] as? String) ?: "DEFAULT"

                MessagePayload(
                    messageId = msgId,
                    chatId = (data["chatId"] as? String) ?: chatId,
                    senderId = sId,
                    receiverId = rId,
                    messageText = msgText,
                    messageType = msgType,
                    mediaUrl = mUrl,
                    timestamp = ts,
                    status = st,
                    expiresAt = exp,
                    replyToMessageId = replyTo,
                    isEdited = isEd,
                    senderActiveFontId = font
                )
            }.sortedBy { it.timestamp }

            trySend(messages)
        }

        awaitClose {
            listener.remove()
        }
    }

    override suspend fun getMoreMessages(chatId: String, limit: Int, offset: Int): List<MessagePayload> {
        if (chatId.isBlank()) return emptyList()

        return try {
            val snapshot = firestore.collection("messages")
                .whereEqualTo("chatId", chatId)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val msgId = (data["messageId"] as? String) ?: doc.id
                val sId = (data["senderId"] as? String) ?: (data["senderUid"] as? String) ?: ""
                val rId = (data["receiverId"] as? String) ?: (data["receiverUid"] as? String) ?: ""
                val msgText = (data["messageText"] as? String) ?: (data["text"] as? String) ?: ""
                val msgType = (data["messageType"] as? String) ?: (data["type"] as? String) ?: "TEXT"
                val mUrl = (data["mediaUrl"] as? String) ?: ""
                val ts = (data["timestamp"] as? Long) ?: (data["createdAt"] as? Long) ?: System.currentTimeMillis()
                val st = (data["status"] as? String) ?: "SENT"
                val exp = (data["expiresAt"] as? Long)
                val replyTo = (data["replyToMessageId"] as? String)
                val isEd = (data["isEdited"] as? Boolean) ?: false
                val font = (data["senderActiveFontId"] as? String) ?: "DEFAULT"

                MessagePayload(
                    messageId = msgId,
                    chatId = (data["chatId"] as? String) ?: chatId,
                    senderId = sId,
                    receiverId = rId,
                    messageText = msgText,
                    messageType = msgType,
                    mediaUrl = mUrl,
                    timestamp = ts,
                    status = st,
                    expiresAt = exp,
                    replyToMessageId = replyTo,
                    isEdited = isEd,
                    senderActiveFontId = font
                )
            }.sortedBy { it.timestamp }
        } catch (e: Exception) {
            Log.e("CloudChatRepo", "Failed to fetch more messages for $chatId: ${e.message}")
            emptyList()
        }
    }
}
