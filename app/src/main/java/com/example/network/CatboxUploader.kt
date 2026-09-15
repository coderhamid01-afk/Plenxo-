package com.example.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Dedicated Catbox.moe media uploader utility.
 * API Endpoint: POST https://catbox.moe/user/api.php
 * Form Parameters:
 *   - reqtype: "fileupload"
 *   - userhash: "9522593a4a22790d1bf20a178"
 *   - fileToUpload: Binary file payload (.m4a/.mp3/.mp4/images)
 * Response: Direct plain text CDN URL string (e.g., https://files.catbox.moe/xxxxxx.m4a).
 */
object CatboxUploader {

    private const val TAG = "CatboxUploader"
    const val CATBOX_URL = "https://catbox.moe/user/api.php"
    const val CATBOX_USERHASH = "9522593a4a22790d1bf20a178"
    const val REQTYPE_FILEUPLOAD = "fileupload"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Uploads a File payload to Catbox API using multipart/form-data.
     * Returns direct RAW PLAIN TEXT URL string (e.g., https://files.catbox.moe/abc123.m4a).
     */
    suspend fun uploadFile(
        file: File,
        mimeType: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            throw IllegalArgumentException("File is empty or does not exist: ${file.absolutePath}")
        }

        val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: determineMimeType(file.name)
        Log.d(TAG, "Uploading file '${file.name}' (${file.length()} bytes, mime: $resolvedMime) to Catbox...")

        onProgress?.invoke(5)

        val rawRequestBody = file.asRequestBody(resolvedMime.toMediaTypeOrNull())
        val uploadRequestBody = if (onProgress != null) {
            ProgressRequestBody(rawRequestBody, onProgress)
        } else {
            rawRequestBody
        }

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", REQTYPE_FILEUPLOAD)
            .addFormDataPart("userhash", CATBOX_USERHASH)
            .addFormDataPart("fileToUpload", file.name, uploadRequestBody)
            .build()

        val request = Request.Builder()
            .url(CATBOX_URL)
            .post(multipartBody)
            .build()

        val response = client.newCall(request).execute()
        val responseCode = response.code
        val responseText = response.body?.string()?.trim() ?: ""

        if (!response.isSuccessful || responseText.isEmpty() || !responseText.startsWith("http")) {
            Log.e(TAG, "Catbox upload failed. HTTP $responseCode: $responseText")
            throw IllegalStateException("Failed to upload file to Catbox. HTTP $responseCode: $responseText")
        }

        onProgress?.invoke(100)
        Log.d(TAG, "Catbox upload succeeded! URL: $responseText")
        responseText
    }

    /**
     * Uploads a Uri payload to Catbox API by streaming it to a temporary cache file.
     */
    suspend fun uploadUri(
        context: Context,
        uri: Uri,
        mimeType: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val fileName = getFileNameFromUri(context, uri)
        val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: getMimeTypeFromUri(context, uri)
        val tempFile = File(context.cacheDir, "catbox_${System.currentTimeMillis()}_$fileName")

        try {
            onProgress?.invoke(5)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("Cannot open input stream for Uri: $uri")

            onProgress?.invoke(15)
            uploadFile(tempFile, resolvedMime) { progress ->
                // Scale progress from 15% to 100%
                val scaled = 15 + (progress * 85 / 100)
                onProgress?.invoke(scaled)
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Uploads an image Uri to Catbox API with optimization.
     */
    suspend fun uploadImage(context: Context, imageUri: Uri): String = withContext(Dispatchers.IO) {
        val tempFile = try {
            com.example.util.BitmapUtils.getOptimizedCompressedFile(context, imageUri)
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap compression failed, fallback to direct stream copy: ${e.message}")
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: throw IllegalArgumentException("Cannot open image stream for Uri: $imageUri")
            val file = File(context.cacheDir, "catbox_upload_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { output -> inputStream.copyTo(output) }
            file
        }

        try {
            uploadFile(tempFile, "image/jpeg")
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Uploads a recorded voice note audio file to Catbox.
     */
    suspend fun uploadVoiceNote(
        file: File,
        onProgress: ((Int) -> Unit)? = null
    ): String {
        return uploadFile(file, "audio/mp4", onProgress)
    }

    /**
     * Uploads a voice note Uri to Catbox.
     */
    suspend fun uploadVoiceNote(
        context: Context,
        voiceUri: Uri,
        onProgress: ((Int) -> Unit)? = null
    ): String {
        return uploadUri(context, voiceUri, "audio/mp4", onProgress)
    }

    /**
     * Uploads a video file or Uri to Catbox.
     */
    suspend fun uploadVideo(
        context: Context,
        videoUri: Uri,
        onProgress: ((Int) -> Unit)? = null
    ): String {
        return uploadUri(context, videoUri, "video/mp4", onProgress)
    }

    suspend fun uploadVideoFile(
        file: File,
        onProgress: ((Int) -> Unit)? = null
    ): String {
        return uploadFile(file, "video/mp4", onProgress)
    }

    /**
     * Uploads a ByteArray payload to Catbox API.
     */
    suspend fun uploadByteArray(
        byteArray: ByteArray,
        fileName: String = "upload.jpg",
        mimeType: String = "image/jpeg"
    ): String = withContext(Dispatchers.IO) {
        if (byteArray.isEmpty()) {
            throw IllegalArgumentException("ByteArray is empty.")
        }

        Log.d(TAG, "Uploading byte array (${byteArray.size} bytes) to Catbox...")

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", REQTYPE_FILEUPLOAD)
            .addFormDataPart("userhash", CATBOX_USERHASH)
            .addFormDataPart(
                "fileToUpload",
                fileName,
                byteArray.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(CATBOX_URL)
            .post(multipartBody)
            .build()

        val response = client.newCall(request).execute()
        val responseCode = response.code
        val responseText = response.body?.string()?.trim() ?: ""

        if (!response.isSuccessful || responseText.isEmpty() || !responseText.startsWith("http")) {
            Log.e(TAG, "Catbox upload failed. HTTP $responseCode: $responseText")
            throw IllegalStateException("Failed to upload byte array to Catbox. HTTP $responseCode: $responseText")
        }

        Log.d(TAG, "Catbox upload succeeded! URL: $responseText")
        responseText
    }

    /**
     * Uploads a text payload as a text file asset to Catbox.
     */
    suspend fun uploadTextPayload(
        text: String,
        fileName: String = "text_payload.txt",
        onProgress: ((Int) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        uploadByteArray(bytes, fileName, "text/plain")
    }

    private fun determineMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        name = it.getString(idx)
                    }
                }
            }
        }
        if (name.isNullOrBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')
        }
        return name ?: "upload_${System.currentTimeMillis()}"
    }

    private fun getMimeTypeFromUri(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri)
            ?: determineMimeType(getFileNameFromUri(context, uri))
    }

    /**
     * RequestBody wrapper with real-time transfer progress callback.
     */
    private class ProgressRequestBody(
        private val delegate: RequestBody,
        private val onProgress: (Int) -> Unit
    ) : RequestBody() {

        override fun contentType(): MediaType? = delegate.contentType()

        override fun contentLength(): Long = delegate.contentLength()

        override fun writeTo(sink: BufferedSink) {
            val countingSink = object : ForwardingSink(sink) {
                private var bytesWritten = 0L
                private val totalBytes = contentLength()
                private var lastPercent = -1

                override fun write(source: Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    bytesWritten += byteCount
                    if (totalBytes > 0) {
                        val percent = ((bytesWritten * 100) / totalBytes).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
            val bufferedSink = countingSink.buffer()
            delegate.writeTo(bufferedSink)
            bufferedSink.flush()
        }
    }
}
