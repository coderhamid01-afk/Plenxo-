package com.example.network

import android.content.Context
import android.net.Uri
import java.io.File

object CatboxStorageManager {

    const val CATBOX_USERHASH = CatboxUploader.CATBOX_USERHASH

    suspend fun uploadImage(context: Context, imageUri: Uri): String {
        return CatboxUploader.uploadImage(context, imageUri)
    }

    suspend fun uploadImageFile(file: File): String {
        return CatboxUploader.uploadFile(file, "image/jpeg")
    }

    suspend fun uploadVoiceNote(file: File): String {
        return CatboxUploader.uploadVoiceNote(file)
    }

    suspend fun uploadVoiceNote(context: Context, voiceUri: Uri): String {
        return CatboxUploader.uploadVoiceNote(context, voiceUri)
    }

    suspend fun uploadVideo(context: Context, videoUri: Uri): String {
        return CatboxUploader.uploadVideo(context, videoUri)
    }

    suspend fun uploadVideoFile(file: File): String {
        return CatboxUploader.uploadVideoFile(file)
    }
}
