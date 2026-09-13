package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AnimatedAuthBackground

/**
 * 10. Setup App Lock Redesign (SetupAppLockScreen):
 * - Interactive Glassmorphic Selection Cards (glowing cyan/violet border on focus/selection)
 * - Glassmorphic PIN input
 * - Primary gradient pill button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupAppLockScreen(
    isChatLock: Boolean = false,
    onBack: () -> Unit,
    onSavePin: (String) -> Unit,
    onSavePassword: (String) -> Unit,
    onSavePattern: (String) -> Unit,
    onEnableBiometrics: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var selectedOption by remember { mutableStateOf("PIN") }
    var pinValue by remember { mutableStateOf("") }
    var passwordValue by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val gradientBrush = Brush.horizontalGradient(
        listOf(Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFF6366F1))
    )
    val activeBorderBrush = Brush.horizontalGradient(
        listOf(Color(0xFF00F2FE), Color(0xFF8B5CF6))
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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF00F2FE)
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
                text = "Select Lock Type",
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

            // Glassmorphic Selection Cards
            val lockTypes = listOf(
                Triple("PIN", "Quick numeric keypad passcode", Icons.Default.Pin),
                Triple("Pattern", "Draw a connected gesture node line", Icons.Default.Gesture),
                Triple("Password", "Complex alphanumeric phrase", Icons.Default.Lock),
                Triple("Biometric", "Device fingerprint or face sensor", Icons.Default.Fingerprint)
            )

            lockTypes.forEach { (option, subtitle, icon) ->
                val isSelected = selectedOption == option
                val cardBg by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF1E293B).copy(alpha = 0.85f) else Color(0xFF111827).copy(alpha = 0.6f),
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
                                    .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = Color(0xFF00F2FE).copy(alpha = 0.35f))
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
                                    if (isSelected) Color(0xFF00F2FE).copy(alpha = 0.18f)
                                    else Color(0xFF1E293B)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = option,
                                tint = if (isSelected) Color(0xFF00F2FE) else Color(0xFF94A3B8),
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
                                color = if (isSelected) Color(0xFF00F2FE).copy(alpha = 0.8f) else Color(0xFF64748B)
                            )
                        }

                        // Glowing selection circle indicator
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isSelected) 2.dp else 1.5.dp,
                                    brush = if (isSelected) activeBorderBrush else Brush.linearGradient(listOf(Color(0xFF475569), Color(0xFF334155))),
                                    shape = CircleShape
                                )
                                .background(if (isSelected) Color(0xFF00F2FE).copy(alpha = 0.2f) else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00F2FE))
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (selectedOption) {
                "PIN" -> {
                    // Glassmorphic PIN Input Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.75f))
                            .border(1.2.dp, Color(0xFF00F2FE).copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                            .padding(18.dp)
                    ) {
                        Column {
                            Text(
                                text = "Setup 4-6 Digit Security PIN",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF00F2FE)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = pinValue,
                                onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) pinValue = it },
                                label = { Text("Enter 4-6 digit PIN", color = Color(0xFF94A3B8)) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF00F2FE),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedContainerColor = Color(0xFF0B0E14).copy(alpha = 0.6f),
                                    unfocusedContainerColor = Color(0xFF0B0E14).copy(alpha = 0.4f)
                                ),
                                trailingIcon = {
                                    val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(imageVector = image, contentDescription = null, tint = Color(0xFF94A3B8))
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    val isPinValid = pinValue.length in 4..6
                    // Primary Gradient Pill Button
                    Button(
                        onClick = {
                            if (isPinValid) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSavePin(pinValue)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = Color(0xFF00F2FE).copy(alpha = 0.4f))
                            .background(
                                if (isPinValid) gradientBrush else Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B))),
                                RoundedCornerShape(26.dp)
                            ),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(26.dp),
                        enabled = isPinValid
                    ) {
                        Text("Save & Enable PIN Lock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                "Password" -> {
                    // Glassmorphic Password Input Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.75f))
                            .border(1.2.dp, Color(0xFF00F2FE).copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                            .padding(18.dp)
                    ) {
                        Column {
                            Text(
                                text = "Setup Security Password",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF00F2FE)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = passwordValue,
                                onValueChange = { passwordValue = it },
                                label = { Text("Enter alphanumeric password", color = Color(0xFF94A3B8)) },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF00F2FE),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedContainerColor = Color(0xFF0B0E14).copy(alpha = 0.6f),
                                    unfocusedContainerColor = Color(0xFF0B0E14).copy(alpha = 0.4f)
                                ),
                                trailingIcon = {
                                    val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(imageVector = image, contentDescription = null, tint = Color(0xFF94A3B8))
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    val isPasswordValid = passwordValue.isNotEmpty()
                    // Primary Gradient Pill Button
                    Button(
                        onClick = {
                            if (isPasswordValid) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSavePassword(passwordValue)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = Color(0xFF00F2FE).copy(alpha = 0.4f))
                            .background(
                                if (isPasswordValid) gradientBrush else Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B))),
                                RoundedCornerShape(26.dp)
                            ),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(26.dp),
                        enabled = isPasswordValid
                    ) {
                        Text("Save & Enable Password Lock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                "Biometric" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.7f))
                            .border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(18.dp))
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

                    // Primary Gradient Pill Button
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEnableBiometrics()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(14.dp, RoundedCornerShape(26.dp), ambientColor = Color(0xFF00F2FE).copy(alpha = 0.4f))
                            .background(gradientBrush, RoundedCornerShape(26.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Text("Verify & Enable Biometric Lock", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                "Pattern" -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Connect at least 4 dots to configure your lock pattern",
                            fontSize = 13.sp,
                            color = Color(0xFF00F2FE),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF0F172A).copy(alpha = 0.7f))
                                .border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                                .padding(16.dp)
                        ) {
                            com.example.ui.components.PatternLockView(
                                modifier = Modifier.fillMaxWidth(),
                                onPatternComplete = { pattern ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSavePattern(pattern)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
}
