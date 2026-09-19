package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerificationScreen(
    authViewModel: AuthViewModel,
    onSuccess: () -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary
) {
    val otpInput by authViewModel.otpInput.collectAsState()
    val secondsRemaining by authViewModel.secondsRemaining.collectAsState()
    val isTimerRunning by authViewModel.isTimerRunning.collectAsState()
    val otpError by authViewModel.otpError.collectAsState()
    val isVerifying by authViewModel.isVerifyingOtp.collectAsState()
    val otpSuccess by authViewModel.otpSuccess.collectAsState()
    val signUpEmail by authViewModel.signUpEmail.collectAsState()

    LaunchedEffect(otpSuccess) {
        if (otpSuccess) {
            onSuccess()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Email Verification",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Subtle ambient aura
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.12f), Color.Transparent),
                            radius = 550f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header Icon Badge
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, primaryColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                        .shadow(4.dp, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MarkEmailRead,
                        contentDescription = "Email Verified",
                        tint = primaryColor,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Title & Instructions
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Enter Security Code",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val emailText = if (signUpEmail.isNotBlank()) signUpEmail else "your registered email address"
                    Text(
                        text = "We have sent a 6-digit secure code to $emailText. Please enter it below to confirm your account.",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // Error Card
                AnimatedVisibility(visible = otpError != null) {
                    otpError?.let {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Modern OTP Input Glass Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "6-DIGIT SECURITY CODE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        val focusRequester = remember { FocusRequester() }
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseAlpha"
                        )

                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                            val clip = clipboardManager.getText()?.text?.trim()?.filter { it.isDigit() }
                            if (clip != null && clip.length == 6 && otpInput.isEmpty()) {
                                authViewModel.otpInput.value = clip
                                authViewModel.verifyOtp()
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { focusRequester.requestFocus() },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicTextField(
                                value = otpInput,
                                onValueChange = { newValue ->
                                    val filtered = newValue.filter { it.isDigit() }.take(6)
                                    authViewModel.otpInput.value = filtered
                                    if (filtered.length == 6) {
                                        authViewModel.verifyOtp()
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                                    .testTag("otp_input")
                                    .size(1.dp)
                                    .alpha(0.01f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (i in 0 until 6) {
                                    val char = otpInput.getOrNull(i)
                                    val isCurrent = (i == otpInput.length) || (otpInput.length == 6 && i == 5)
                                    val isFilled = char != null

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(
                                                width = if (isCurrent) 2.dp else 1.dp,
                                                color = when {
                                                    isCurrent -> primaryColor.copy(alpha = pulseAlpha)
                                                    isFilled -> primaryColor
                                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                                },
                                                shape = RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = char?.toString() ?: "",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        val clipText = clipboardManager.getText()?.text?.trim()?.filter { it.isDigit() }?.take(6)
                        if (!clipText.isNullOrEmpty() && clipText.length == 6 && otpInput != clipText) {
                            TextButton(
                                onClick = {
                                    authViewModel.otpInput.value = clipText
                                    authViewModel.verifyOtp()
                                },
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = "Paste code from clipboard ($clipText)",
                                    fontSize = 12.sp,
                                    color = primaryColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Resend Timer Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isTimerRunning) {
                        Text(
                            text = "Resend code in $secondsRemaining seconds",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { authViewModel.resendOtp() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Resend Code",
                                tint = primaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Resend Code",
                                color = primaryColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.testTag("resend_button")
                            )
                        }
                    }
                }

                // Verify Button
                Button(
                    onClick = { authViewModel.verifyOtp() },
                    enabled = !isVerifying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("verify_button")
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = "Verify & Continue",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
