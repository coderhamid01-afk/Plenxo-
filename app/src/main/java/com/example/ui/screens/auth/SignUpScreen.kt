package com.example.ui.screens.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.viewmodel.AuthViewModel

/**
 * SignUp Screen wrapper delegating to the unified modern AuthScreen and AuthHeader.
 */
@Composable
fun SignUpScreen(
    authViewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit,
    onSuccess: () -> Unit,
    primaryColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary
) {
    AuthScreen(
        authViewModel = authViewModel,
        initialMode = AuthMode.SIGNUP,
        onLoginSuccess = { /* Handled in AuthScreen */ },
        onSignUpSuccess = onSuccess,
        onNavigateToOther = onNavigateToLogin,
        primaryColor = primaryColor
    )
}

