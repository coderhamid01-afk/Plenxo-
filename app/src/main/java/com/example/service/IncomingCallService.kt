package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.calling.IncomingCallActivity
import com.example.calling.CallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IncomingCallService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_START_INCOMING_CALL -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Unknown Caller"
                val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "VOICE"
                startIncomingCall(callId, callerName, callType)
            }
            ACTION_ACCEPT_CALL -> {
                val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val needsCamera = CallManager.activeCall.value?.callType == com.example.calling.model.CallType.VIDEO
                
                if (hasAudio && (!needsCamera || hasCamera)) {
                    CallManager.acceptCall()
                    val activityIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(activityIntent)
                } else {
                    val activityIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("AUTO_ACCEPT_CALL", true)
                    }
                    startActivity(activityIntent)
                }
                stopIncomingCall()
            }
            ACTION_DECLINE_CALL -> {
                CallManager.declineCall()
                stopIncomingCall()
            }
            ACTION_STOP_SERVICE -> {
                stopIncomingCall()
            }
        }
        return START_NOT_STICKY
    }

    private fun startIncomingCall(callId: String, callerName: String, callType: String) {
        val channelId = "incoming_calls"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Full-Screen Intent
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_CALL_TYPE, callType)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Accept Intent
        val acceptIntent = Intent(this, IncomingCallService::class.java).apply {
            action = ACTION_ACCEPT_CALL
        }
        val acceptPendingIntent = PendingIntent.getService(
            this,
            callId.hashCode() + 1,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline Intent
        val declineIntent = Intent(this, IncomingCallService::class.java).apply {
            action = ACTION_DECLINE_CALL
        }
        val declinePendingIntent = PendingIntent.getService(
            this,
            callId.hashCode() + 2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming video and voice calls"
                setSound(
                    ringtoneUri,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 1000, 1000)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val title = "Incoming ${if (callType == "VIDEO") "Video" else "Voice"} Call"
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(callerName)
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_call, "Accept", acceptPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var types = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (callType == "VIDEO") {
                types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    startForeground(NOTIFICATION_ID, notification, types)
                } catch (e: Exception) {
                    Log.w("IncomingCallService", "Failed startForeground with PHONE_CALL type, retrying with MIC/CAMERA: ${e.message}")
                    try {
                        val fallbackTypes = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                                if (callType == "VIDEO") android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
                        startForeground(NOTIFICATION_ID, notification, fallbackTypes)
                    } catch (e2: Exception) {
                        Log.w("IncomingCallService", "Failed startForeground with fallback types, trying default: ${e2.message}")
                        startForeground(NOTIFICATION_ID, notification)
                    }
                }
            } else {
                startForeground(NOTIFICATION_ID, notification, types)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startVibrating()
    }

    private fun startVibrating() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 1000, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopIncomingCall() {
        vibrator?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        vibrator?.cancel()
        serviceJob.cancel()
    }

    companion object {
        const val ACTION_START_INCOMING_CALL = "ACTION_START_INCOMING_CALL"
        const val ACTION_ACCEPT_CALL = "ACTION_ACCEPT_CALL"
        const val ACTION_DECLINE_CALL = "ACTION_DECLINE_CALL"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        
        const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
        const val EXTRA_CALLER_NAME = "EXTRA_CALLER_NAME"
        const val EXTRA_CALL_TYPE = "EXTRA_CALL_TYPE"
        
        const val NOTIFICATION_ID = 1001
    }
}
