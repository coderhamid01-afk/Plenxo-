package com.example.ui.calling

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class CallPermissionController(
    private val context: Context,
    private val requestAudioPermission: () -> Unit,
    private val requestVideoPermissions: () -> Unit,
    private val setAudioGrantedCallback: (() -> Unit) -> Unit,
    private val setVideoGrantedCallback: (() -> Unit) -> Unit
) {
    fun startVoiceCallWithPermission(onGranted: () -> Unit) {
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudio) {
            onGranted()
        } else {
            setAudioGrantedCallback(onGranted)
            requestAudioPermission()
        }
    }

    fun startVideoCallWithPermission(onGranted: () -> Unit) {
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudio && hasCamera) {
            onGranted()
        } else {
            setVideoGrantedCallback(onGranted)
            requestVideoPermissions()
        }
    }
}

@Composable
fun rememberCallPermissionController(): CallPermissionController {
    val context = LocalContext.current
    var audioGrantedAction = remember { { } }
    var videoGrantedAction = remember { { } }

    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            audioGrantedAction()
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required to start a voice call.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true

        if (audioGranted && cameraGranted) {
            videoGrantedAction()
        } else {
            val missing = when {
                !audioGranted && !cameraGranted -> "Camera and Microphone permissions"
                !cameraGranted -> "Camera permission"
                else -> "Microphone permission"
            }
            Toast.makeText(
                context,
                "$missing are required to start a video call.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    return remember(context) {
        CallPermissionController(
            context = context,
            requestAudioPermission = { audioLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            requestVideoPermissions = {
                videoLauncher.launch(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.CAMERA
                    )
                )
            },
            setAudioGrantedCallback = { audioGrantedAction = it },
            setVideoGrantedCallback = { videoGrantedAction = it }
        )
    }
}
