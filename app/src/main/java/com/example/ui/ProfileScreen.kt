package com.example.ui

import androidx.compose.runtime.Composable
import com.example.viewmodel.PlenxoViewModel
import com.example.viewmodel.ProfileSettingsViewModel

@Composable
fun ProfileScreen(
    viewModel: ProfileSettingsViewModel,
    weChatViewModel: PlenxoViewModel,
    onBack: () -> Unit
) {
    ProfileSettingsScreen(
        viewModel = viewModel,
        weChatViewModel = weChatViewModel,
        onBack = onBack
    )
}
