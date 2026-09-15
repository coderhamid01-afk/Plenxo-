package com.example.calling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.calling.model.AudioOutputRoute

/**
 * AudioRouteManager handles audio focus, communication mode, and dynamic
 * routing between SPEAKER, EARPIECE, BLUETOOTH, and HEADSET.
 */
class AudioRouteManager(private val context: Context) {
    private val TAG = "AudioRouteManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousIsSpeakerphoneOn: Boolean = false
    private var previousIsMicrophoneMute: Boolean = false
    private var focusRequest: AudioFocusRequest? = null
    private var headsetReceiver: BroadcastReceiver? = null
    private var isRoutingActive = false

    var onRouteChanged: ((AudioOutputRoute) -> Unit)? = null

    init {
        registerHeadsetReceiver()
    }

    /**
     * Activate communication mode and request audio focus.
     */
    fun startCommunication(initialRoute: AudioOutputRoute) {
        val am = audioManager ?: return
        if (isRoutingActive) return
        isRoutingActive = true

        try {
            previousAudioMode = am.mode
            previousIsSpeakerphoneOn = am.isSpeakerphoneOn
            previousIsMicrophoneMute = am.isMicrophoneMute

            requestAudioFocus()

            am.mode = AudioManager.MODE_IN_COMMUNICATION
            setAudioRoute(initialRoute)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting communication audio: ${e.message}", e)
        }
    }

    /**
     * Seamlessly route audio to the selected output route.
     */
    fun setAudioRoute(route: AudioOutputRoute) {
        val am = audioManager ?: return
        Log.d(TAG, "Setting audio route to: $route")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+) setCommunicationDevice API
                val devices = am.availableCommunicationDevices
                val targetDevice = when (route) {
                    AudioOutputRoute.SPEAKER -> {
                        devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    }
                    AudioOutputRoute.EARPIECE -> {
                        devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    }
                    AudioOutputRoute.BLUETOOTH -> {
                        devices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                        }
                    }
                    AudioOutputRoute.HEADSET -> {
                        devices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                        }
                    }
                }

                if (targetDevice != null) {
                    val success = am.setCommunicationDevice(targetDevice)
                    Log.d(TAG, "setCommunicationDevice(${targetDevice.type}) result: $success")
                } else {
                    // Fallback to legacy methods if specific device type not detected
                    applyLegacyRoute(route)
                }
            } else {
                applyLegacyRoute(route)
            }

            onRouteChanged?.invoke(route)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying audio route: ${e.message}", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLegacyRoute(route: AudioOutputRoute) {
        val am = audioManager ?: return
        when (route) {
            AudioOutputRoute.SPEAKER -> {
                if (am.isBluetoothScoOn) {
                    am.isBluetoothScoOn = false
                    am.stopBluetoothSco()
                }
                am.isSpeakerphoneOn = true
            }
            AudioOutputRoute.EARPIECE -> {
                if (am.isBluetoothScoOn) {
                    am.isBluetoothScoOn = false
                    am.stopBluetoothSco()
                }
                am.isSpeakerphoneOn = false
            }
            AudioOutputRoute.BLUETOOTH -> {
                am.isSpeakerphoneOn = false
                try {
                    am.startBluetoothSco()
                    am.isBluetoothScoOn = true
                } catch (e: Exception) {
                    Log.e(TAG, "Error enabling Bluetooth SCO: ${e.message}")
                }
            }
            AudioOutputRoute.HEADSET -> {
                if (am.isBluetoothScoOn) {
                    am.isBluetoothScoOn = false
                    am.stopBluetoothSco()
                }
                am.isSpeakerphoneOn = false
            }
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(TAG, "Audio focus changed: $focusChange")
                    }
                    .build()

                focusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus: ${e.message}", e)
        }
    }

    /**
     * Stop communication mode, release audio focus, and restore normal audio.
     */
    fun stopCommunication() {
        if (!isRoutingActive) return
        isRoutingActive = false
        val am = audioManager ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            }

            @Suppress("DEPRECATION")
            if (am.isBluetoothScoOn) {
                am.isBluetoothScoOn = false
                am.stopBluetoothSco()
            }

            am.isSpeakerphoneOn = previousIsSpeakerphoneOn

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }

            am.mode = previousAudioMode
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping communication audio: ${e.message}", e)
        }
    }

    private fun registerHeadsetReceiver() {
        try {
            headsetReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_HEADSET_PLUG) {
                        val state = intent.getIntExtra("state", -1)
                        if (state == 1 && isRoutingActive) {
                            Log.d(TAG, "Wired headset plugged in")
                            setAudioRoute(AudioOutputRoute.HEADSET)
                        } else if (state == 0 && isRoutingActive) {
                            Log.d(TAG, "Wired headset unplugged, returning to earpiece")
                            setAudioRoute(AudioOutputRoute.EARPIECE)
                        }
                    }
                }
            }
            val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
            context.registerReceiver(headsetReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering headset receiver: ${e.message}")
        }
    }

    fun release() {
        stopCommunication()
        try {
            headsetReceiver?.let { context.unregisterReceiver(it) }
            headsetReceiver = null
        } catch (e: Exception) {
            // Ignore unregister exception if already unregistered
        }
    }
}
