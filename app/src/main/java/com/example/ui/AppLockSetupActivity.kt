package com.example.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.repository.SecurityRepository
import com.example.ui.components.AnimatedAuthBackground
import com.example.ui.theme.PlenxoColors
import java.security.MessageDigest

class AppLockSetupActivity : com.example.ui.BaseActivity() {

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val securityRepo = SecurityRepository(this)
        
        val chatId = intent.getStringExtra("chatId")
        val isChatLock = chatId != null

        setContent {
            val haptic = LocalHapticFeedback.current
            var selectedOption by remember { mutableStateOf("PIN") }
            var passwordValue by remember { mutableStateOf("") }
            var pinValue by remember { mutableStateOf("") }
            var passwordVisible by remember { mutableStateOf(false) }

            val gradientBrush = Brush.horizontalGradient(
                listOf(Color(0xFF2563EB), Color(0xFF3B82F6), Color(0xFF1D4ED8))
            )
            val activeBorderBrush = Brush.horizontalGradient(
                listOf(Color(0xFF2563EB), Color(0xFF3B82F6))
            )

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = if (isChatLock) "Setup Chat Lock" else "Setup App Lock",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color(0xFF2563EB)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0B0E14))
                    )
                },
                containerColor = Color(0xFF0B0E14)
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    AnimatedAuthBackground(modifier = Modifier.fillMaxSize())

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    Text(
                        text = stringResource(R.string.str_select_lock_type),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Choose your preferred authentication method to fortify privacy.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Glassmorphic Lock Type Selection Cards
                    val lockTypes = listOf(
                        Triple("PIN", "Quick numeric keypad passcode", Icons.Default.Pin),
                        Triple("Pattern", "Draw a connected gesture node line", Icons.Default.Gesture),
                        Triple("Password", "Complex alphanumeric phrase", Icons.Default.Lock),
                        Triple("Biometric", "Device fingerprint or face sensor", Icons.Default.Fingerprint)
                    )

                    lockTypes.forEach { (option, subtitle, icon) ->
                        val isSelected = selectedOption == option
                        val cardBg by animateColorAsState(
                            targetValue = if (isSelected) Color(0xFF1E293B).copy(alpha = 0.8f) else Color(0xFF111827).copy(alpha = 0.6f),
                            label = "cardBg"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBg)
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = Color(0xFF2563EB).copy(alpha = 0.35f))
                                            .border(1.5.dp, activeBorderBrush, RoundedCornerShape(16.dp))
                                    } else {
                                        Modifier.border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    }
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedOption = option
                                }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) Color(0xFF2563EB).copy(alpha = 0.18f)
                                            else Color(0xFF1E293B)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = option,
                                        tint = if (isSelected) Color(0xFF2563EB) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                    )
                                    Text(
                                        text = subtitle,
                                        fontSize = 12.sp,
                                        color = if (isSelected) Color(0xFF2563EB).copy(alpha = 0.8f) else Color(0xFF64748B)
                                    )
                                }
                                // Interactive Selection Indicator (Replacing basic RadioButton)
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.5.dp,
                                            brush = if (isSelected) activeBorderBrush else Brush.linearGradient(listOf(Color(0xFF475569), Color(0xFF334155))),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .background(if (isSelected) Color(0xFF2563EB).copy(alpha = 0.2f) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(Color(0xFF2563EB))
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (selectedOption == "Biometric") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0F172A).copy(alpha = 0.7f))
                                .border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "Secure your messages instantly using your device's biometric sensor (Fingerprint or Face Unlock).",
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (com.example.util.SecurityManager.isBiometricAvailable(this@AppLockSetupActivity)) {
                                    com.example.util.SecurityManager.showPrompt(
                                        activity = this@AppLockSetupActivity,
                                        title = if (isChatLock) "Setup Chat Biometric" else "Setup App Biometric",
                                        subtitle = "Confirm your fingerprint or face to enable biometric lock",
                                        onSuccess = {
                                            if (isChatLock) {
                                                chatId?.let { id ->
                                                    securityRepo.setChatLockType(id, "BIOMETRIC")
                                                    securityRepo.setChatLock(id, "BIOMETRIC_ENABLED")
                                                }
                                            } else {
                                                securityRepo.setGlobalAppLock("BIOMETRIC_ENABLED")
                                                securityRepo.setLockType("BIOMETRIC")
                                                com.example.util.SessionManager.saveGlobalAppLock(this@AppLockSetupActivity, true)
                                            }
                                            finish()
                                        },
                                        onError = { err ->
                                            android.widget.Toast.makeText(this@AppLockSetupActivity, "Biometric error: $err", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    android.widget.Toast.makeText(this@AppLockSetupActivity, "Biometric authentication is not available or not enrolled on this device.", android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .shadow(12.dp, RoundedCornerShape(26.dp))
                                .background(gradientBrush, RoundedCornerShape(26.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(26.dp)
                        ) {
                            Text("Verify & Enable Biometric Lock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    } else if (selectedOption == "PIN") {
                        // Glassmorphic Input Container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0F172A).copy(alpha = 0.7f))
                                .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            OutlinedTextField(
                                value = pinValue,
                                onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) pinValue = it },
                                label = { Text(stringResource(R.string.str_enter_4_6_digit_pin), color = Color(0xFF94A3B8)) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF2563EB),
                                    unfocusedBorderColor = Color(0xFF334155)
                                ),
                                trailingIcon = {
                                    val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(imageVector = image, contentDescription = null, tint = Color(0xFF94A3B8))
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        val isPinValid = pinValue.length in 4..6
                        Button(
                            onClick = {
                                if (isPinValid) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isChatLock) {
                                        chatId?.let { id ->
                                            securityRepo.setChatLockType(id, "PIN")
                                            securityRepo.setChatLock(id, sha256(pinValue))
                                        }
                                    } else {
                                        securityRepo.setGlobalAppLock(sha256(pinValue))
                                        securityRepo.setLockType("PIN")
                                        com.example.util.SessionManager.saveGlobalAppLock(this@AppLockSetupActivity, true)
                                    }
                                    finish()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .shadow(12.dp, RoundedCornerShape(26.dp))
                                .background(
                                    if (isPinValid) gradientBrush else Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B))),
                                    RoundedCornerShape(26.dp)
                                ),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(26.dp),
                            enabled = isPinValid
                        ) {
                            Text(stringResource(R.string.str_save_enable_lock), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    } else if (selectedOption == "Password") {
                        // Glassmorphic Input Container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0F172A).copy(alpha = 0.7f))
                                .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            OutlinedTextField(
                                value = passwordValue,
                                onValueChange = { passwordValue = it },
                                label = { Text(stringResource(R.string.str_enter_alphanumeric_password), color = Color(0xFF94A3B8)) },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF2563EB),
                                    unfocusedBorderColor = Color(0xFF334155)
                                ),
                                trailingIcon = {
                                    val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(imageVector = image, contentDescription = null, tint = Color(0xFF94A3B8))
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        val isPasswordValid = passwordValue.isNotEmpty()
                        Button(
                            onClick = {
                                if (isPasswordValid) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isChatLock) {
                                        chatId?.let { id ->
                                            securityRepo.setChatLockType(id, "PASSWORD")
                                            securityRepo.setChatLock(id, sha256(passwordValue))
                                        }
                                    } else {
                                        securityRepo.setGlobalAppLock(sha256(passwordValue))
                                        securityRepo.setLockType("PASSWORD")
                                        com.example.util.SessionManager.saveGlobalAppLock(this@AppLockSetupActivity, true)
                                    }
                                    finish()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .shadow(12.dp, RoundedCornerShape(26.dp))
                                .background(
                                    if (isPasswordValid) gradientBrush else Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B))),
                                    RoundedCornerShape(26.dp)
                                ),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(26.dp),
                            enabled = isPasswordValid
                        ) {
                            Text(stringResource(R.string.str_save_enable_lock), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    } else if (selectedOption == "Pattern") {
                        Text(
                            text = stringResource(R.string.str_draw_your_pattern),
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        com.example.ui.components.PatternLockView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            onPatternComplete = { pattern ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isChatLock) {
                                    chatId?.let { id ->
                                        securityRepo.setChatLockType(id, "PATTERN")
                                        securityRepo.setChatLock(id, sha256(pattern))
                                    }
                                } else {
                                    securityRepo.setGlobalAppLock(sha256(pattern))
                                    securityRepo.setLockType("PATTERN")
                                    com.example.util.SessionManager.saveGlobalAppLock(this@AppLockSetupActivity, true)
                                }
                                finish()
                            }
                        )
                    }
                }
            }
        }
        }
    }
}
