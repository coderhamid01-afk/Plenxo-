package com.example.ui.screens.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.model.UserProfile
import com.example.viewmodel.AuthViewModel

/**
 * Login Screen wrapper delegating to the unified modern AuthScreen and AuthHeader.
 */
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onNavigateToSignUp: () -> Unit,
    onLoginSuccess: (UserProfile) -> Unit,
    onSignUpSuccess: () -> Unit = onNavigateToSignUp,
    onNavigateToOtp: () -> Unit = onSignUpSuccess,
    primaryColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary
) {
    AuthScreen(
        authViewModel = authViewModel,
        initialMode = AuthMode.LOGIN,
        onLoginSuccess = onLoginSuccess,
        onSignUpSuccess = onSignUpSuccess,
        onNavigateToOtp = onNavigateToOtp,
        onNavigateToOther = onNavigateToSignUp,
        primaryColor = primaryColor
    )
}

