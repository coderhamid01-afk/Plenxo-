package com.example.util

import android.content.Context
import android.net.Uri
import java.io.File

object CatboxUploader {

    suspend fun uploadImage(context: Context, imageUri: Uri): String {
        return com.example.network.CatboxUploader.uploadImage(context, imageUri)
    }

    suspend fun uploadFile(
        file: File,
        mimeType: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): String {
        return com.example.network.CatboxUploader.uploadFile(file, mimeType, onProgress)
    }

    suspend fun uploadUri(
        context: Context,
        uri: Uri,
        mimeType: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): String {
        return com.example.network.CatboxUploader.uploadUri(context, uri, mimeType, onProgress)
    }

    suspend fun uploadVoiceNote(
        file: File,
        onProgress: ((Int) -> Unit)? = null
    ): String {
        return com.example.network.CatboxUploader.uploadVoiceNote(file, onProgress)
    }

    suspend fun uploadVoiceNote(
        context: Context,
        voiceUri: Uri,
        onProgress: ((Int) -> Unit)? = null
    ): String {
        return com.example.network.CatboxUploader.uploadVoiceNote(context, voiceUri, onProgress)
    }

    suspend fun uploadVideo(
        context: Context,
        videoUri: Uri,
        onProgress: ((Int) -> Unit)? = null
    ): String {
        return com.example.network.CatboxUploader.uploadVideo(context, videoUri, onProgress)
    }

    suspend fun uploadVideoFile(
        file: File,
        onProgress: ((Int) -> Unit)? = null
    ): String {
        return com.example.network.CatboxUploader.uploadVideoFile(file, onProgress)
    }

    suspend fun uploadByteArray(
        byteArray: ByteArray,
        fileName: String = "upload.jpg",
        mimeType: String = "image/jpeg"
    ): String {
        return com.example.network.CatboxUploader.uploadByteArray(byteArray, fileName, mimeType)
    }

    suspend fun uploadTextPayload(
        text: String,
        fileName: String = "text_payload.txt",
        onProgress: ((Int) -> Unit)? = null
    ): String {
        return com.example.network.CatboxUploader.uploadTextPayload(text, fileName, onProgress)
    }
}
