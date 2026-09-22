package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.content.Intent
import com.example.MainActivity

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    private const val PREFS_NAME = "app_settings"
    private const val KEY_SELECTED_SOUND = "notification_ringtone_sp"
    
    const val BASE_CHANNEL_ID = "plenxo_messages"
    const val CHANNEL_FRIEND_REQUESTS = "plenxo_friend_requests"
    const val CHANNEL_CALLS = "incoming_call_channel"
    const val CHANNEL_MISSED_CALLS = "plenxo_missed_calls"
    const val CHANNEL_GENERAL = "plenxo_general"
    private const val CHANNEL_NAME = "Plenxo Chat Messages"

    /**
     * Set up all required notification channels for the app.
     */
    fun setupAllNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val ringtone = getSelectedSoundName(context)
            recreateNotificationChannel(context, ringtone)

            val friendChannel = NotificationChannel(
                CHANNEL_FRIEND_REQUESTS,
                "Friend Requests",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for incoming friend and chat requests"
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                enableVibration(true)
            }

            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming WebRTC voice and video calls"
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
                enableVibration(true)
            }

            val missedCallChannel = NotificationChannel(
                CHANNEL_MISSED_CALLS,
                "Missed Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for missed voice and video calls"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
            }

            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "General Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General updates and system alerts"
            }

            notificationManager.createNotificationChannels(
                listOf(friendChannel, callChannel, missedCallChannel, generalChannel)
            )
            Log.d(TAG, "Initialized all Plenxo notification channels successfully")
        }
    }

    /**
     * Gets the selected notification sound name from SharedPreferences.
     * Defaults to "minimal_ping".
     */
    fun getSelectedSoundName(context: Context): String {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_SELECTED_SOUND, "minimal_ping") ?: "minimal_ping"
    }

    /**
     * Returns the raw resource URI for the specified sound name.
     */
    fun getSoundUri(context: Context, soundName: String): Uri {
        val resId = context.resources.getIdentifier(soundName, "raw", context.packageName)
        return if (resId != 0) {
            Uri.parse("android.resource://${context.packageName}/$resId")
        } else {
            // Fallback to minimal_ping
            val fallbackResId = context.resources.getIdentifier("minimal_ping", "raw", context.packageName)
            Uri.parse("android.resource://${context.packageName}/$fallbackResId")
        }
    }

    /**
     * Generates a dynamic notification channel ID by appending the sound name.
     * Android 8.0+ caches channel configurations. To force a change in sound, we must use a new ID.
     */
    fun getDynamicChannelId(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundName = getSelectedSoundName(context)
            return "${BASE_CHANNEL_ID}_$soundName"
        }
        return BASE_CHANNEL_ID
    }

    /**
     * Updates/Recreates the notification channel with the new sound name, deleting any older custom sound channels.
     */
    fun recreateNotificationChannel(context: Context, soundName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val dynamicChannelId = "${BASE_CHANNEL_ID}_$soundName"
            val soundUri = getSoundUri(context, soundName)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // Define the new channel
            val newChannel = NotificationChannel(
                dynamicChannelId,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(soundUri, audioAttributes)
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                enableVibration(true)
                description = "Channel for real-time instant chat message alerts with custom sound: $soundName"
            }

            // Delete older channels matching our pattern to avoid clutter,
            // but preserve the newly created one.
            val activeChannels = notificationManager.notificationChannels
            for (channel in activeChannels) {
                if (channel.id.startsWith("${BASE_CHANNEL_ID}_") && channel.id != dynamicChannelId) {
                    try {
                        notificationManager.deleteNotificationChannel(channel.id)
                        Log.d(TAG, "Deleted old notification channel: ${channel.id}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete old channel: ${channel.id}", e)
                    }
                } else if (channel.id == BASE_CHANNEL_ID) {
                    // Also clean up default/base channel if it exists
                    try {
                        notificationManager.deleteNotificationChannel(channel.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete base channel", e)
                    }
                }
            }

            // Create the new channel
            notificationManager.createNotificationChannel(newChannel)
            Log.d(TAG, "Created dynamic notification channel: $dynamicChannelId with sound URI: $soundUri")
        }
    }

    /**
     * Saves the sound name in SharedPreferences, recreate the notification channel dynamically,
     * and triggers channel update immediately.
     */
    fun saveSelectedSound(context: Context, soundName: String) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_SELECTED_SOUND, soundName).apply()
        recreateNotificationChannel(context, soundName)
        Log.d(TAG, "Saved sound '$soundName' to SharedPreferences and re-engineered channel")
    }

    /**
     * Helper to construct a NotificationCompat.Builder with the correct channel and custom sound fallback
     * (the sound URI is also set on the builder for pre-Oreo devices).
     */
    fun createNotificationBuilder(
        context: Context,
        title: String,
        body: String,
        pendingIntent: PendingIntent
    ): NotificationCompat.Builder {
        val channelId = getDynamicChannelId(context)
        val soundName = getSelectedSoundName(context)
        val soundUri = getSoundUri(context, soundName)

        val iconRes = com.example.R.drawable.ic_stat_plenxo_notification

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri) // Sound fallback for pre-Oreo (Android < 8.0)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        targetScreen: String,
        extraData: Map<String, String>
    ) {
        setupAllNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", targetScreen)
            extraData.forEach { (key, value) -> putExtra(key, value) }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (title + message + targetScreen).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (targetScreen == "CHAT_REQUESTS") CHANNEL_FRIEND_REQUESTS else getDynamicChannelId(context)
        val iconRes = com.example.R.drawable.ic_stat_plenxo_notification
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(if (targetScreen == "CHAT_REQUESTS") NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        NotificationManagerCompat.from(context).apply {
            try {
                notify((title + message + targetScreen).hashCode(), builder.build())
            } catch (e: SecurityException) {
                Log.e(TAG, "Notification permission not granted", e)
            }
        }
    }

    fun showMissedCallNotification(context: Context, callerName: String, callType: String) {
        setupAllNotificationChannels(context)

        val typeText = if (callType.equals("VIDEO", ignoreCase = true)) "Video" else "Voice"
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "CALL_HISTORY")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("missed_call_" + callerName + System.currentTimeMillis()).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = com.example.R.drawable.ic_stat_plenxo_notification
        val builder = NotificationCompat.Builder(context, CHANNEL_MISSED_CALLS)
            .setSmallIcon(iconRes)
            .setContentTitle("Missed $typeText Call")
            .setContentText("Missed call from $callerName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(android.graphics.Color.RED)

        try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing notification permissions: ${e.message}")
        }
    }
}
