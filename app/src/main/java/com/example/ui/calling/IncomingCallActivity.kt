package com.example.ui.calling

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainActivity
import com.example.calling.CallManager
import com.example.calling.model.CallState
import com.example.service.IncomingCallService
import com.example.ui.theme.PlenxoTheme

class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Flags to show over lock screen and wake up screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Dismiss Keyguard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }

        setContent {
            PlenxoTheme {
                val activeCall by CallManager.activeCall.collectAsStateWithLifecycle()

                LaunchedEffect(activeCall?.callState) {
                    val state = activeCall?.callState
                    if (activeCall == null || state == CallState.ENDED || state == CallState.FAILED || state == CallState.TIMEOUT || state == CallState.REJECTED || state == CallState.CANCELLED) {
                        finish()
                    }
                }

                if (activeCall != null) {
                    IncomingCallOverlay(
                        session = activeCall!!,
                        onAccept = {
                            CallManager.acceptCall()
                            val intent = Intent(this@IncomingCallActivity, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(intent)
                            finish()
                        },
                        onDecline = {
                            CallManager.endCall()
                            finish()
                        }
                    )
                }
            }
        }
    }
}
