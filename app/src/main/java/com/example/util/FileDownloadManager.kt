package com.example.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility for downloading files from Catbox / HTTP URLs directly to user's public Downloads folder.
 * Path: Download/Plenxo/Plenxo Downloads/<filename>
 * Safe for modern Android Scoped Storage (API 29+ MediaStore Downloads collection).
 */
object FileDownloadManager {

    private const val TAG = "FileDownloadManager"
    private val downloadingUrls = ConcurrentHashMap<String, Boolean>()

    suspend fun downloadFile(
        context: Context,
        fileUrl: String,
        suggestedFileName: String,
        onProgress: (Int) -> Unit = {},
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        if (fileUrl.isBlank()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Invalid file URL", Toast.LENGTH_SHORT).show()
                onResult(false, "Invalid URL")
            }
            return@withContext
        }

        if (downloadingUrls[fileUrl] == true) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download already in progress...", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        downloadingUrls[fileUrl] = true

        try {
            var url = fileUrl
            var connection: HttpURLConnection? = null
            var redirectCount = 0
            val maxRedirects = 5

            while (redirectCount < maxRedirects) {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.requestMethod = "GET"
                connection.connect()

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER
                ) {
                    val newUrl = connection.getHeaderField("Location")
                    if (!newUrl.isNullOrBlank()) {
                        url = newUrl
                        redirectCount++
                        connection.disconnect()
                    } else {
                        break
                    }
                } else {
                    break
                }
            }

            val finalConn = connection ?: throw IllegalStateException("Could not open connection")
            if (finalConn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Server returned HTTP ${finalConn.responseCode}")
            }

            val contentLength = finalConn.contentLength
            val contentType = finalConn.contentType

            // Determine and sanitize filename
            val rawFileName = when {
                suggestedFileName.isNotBlank() && suggestedFileName != "Attachment File" -> suggestedFileName
                else -> {
                    val disposition = finalConn.getHeaderField("Content-Disposition")
                    if (!disposition.isNullOrBlank() && disposition.contains("filename=")) {
                        disposition.substringAfter("filename=").replace("\"", "").trim()
                    } else {
                        val pathSegment = Uri.parse(url).lastPathSegment
                        if (!pathSegment.isNullOrBlank()) pathSegment else "Plenxo_File_${System.currentTimeMillis()}"
                    }
                }
            }

            val sanitizedFileName = sanitizeFileName(rawFileName)

            var outputStream: OutputStream? = null
            var savedPathDisplay = ""

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, contentType ?: "*/*")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Plenxo/Plenxo Downloads")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val itemUri = resolver.insert(collection, contentValues)
                    ?: throw IllegalStateException("Failed to create MediaStore entry")

                outputStream = resolver.openOutputStream(itemUri)
                    ?: throw IllegalStateException("Failed to open MediaStore output stream")

                copyStreamWithProgress(finalConn.inputStream, outputStream, contentLength) { pct ->
                    onProgress(pct)
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)

                savedPathDisplay = "Download/Plenxo/Plenxo Downloads/$sanitizedFileName"
            } else {
                val targetDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Plenxo/Plenxo Downloads"
                )
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                var targetFile = File(targetDir, sanitizedFileName)
                if (targetFile.exists()) {
                    val nameWithoutExt = targetFile.nameWithoutExtension
                    val ext = targetFile.extension
                    var counter = 1
                    while (targetFile.exists()) {
                        val newName = if (ext.isNotEmpty()) "$nameWithoutExt ($counter).$ext" else "$nameWithoutExt ($counter)"
                        targetFile = File(targetDir, newName)
                        counter++
                    }
                }

                outputStream = FileOutputStream(targetFile)
                copyStreamWithProgress(finalConn.inputStream, outputStream, contentLength) { pct ->
                    onProgress(pct)
                }

                savedPathDisplay = targetFile.absolutePath
            }

            finalConn.disconnect()

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Saved to Downloads/Plenxo/Plenxo Downloads", Toast.LENGTH_LONG).show()
                onResult(true, savedPathDisplay)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error for $fileUrl: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                onResult(false, e.localizedMessage)
            }
        } finally {
            downloadingUrls.remove(fileUrl)
        }
    }

    private fun copyStreamWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Int,
        onProgress: (Int) -> Unit
    ) {
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalRead = 0L

        input.use { inStream ->
            output.use { outStream ->
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    outStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        val percent = ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                        onProgress(percent)
                    }
                }
                outStream.flush()
            }
        }
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}
