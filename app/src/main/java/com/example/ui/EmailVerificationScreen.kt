package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.viewmodel.AuthViewModel

/**
 * EmailVerificationScreen wrapper delegating to OtpVerificationScreen
 * Provides a single 8-digit segmented PIN box supporting auto-paste and manual entry.
 */
@Composable
fun EmailVerificationScreen(
    authViewModel: AuthViewModel,
    onSuccess: () -> Unit,
    primaryColor: Color = Color(0xFF059669)
) {
    OtpVerificationScreen(
        authViewModel = authViewModel,
        onSuccess = onSuccess,
        primaryColor = primaryColor
    )
}
