@file:Suppress("DEPRECATION")
package com.example.ui

import androidx.compose.ui.res.stringResource
import com.example.R

import com.example.ui.theme.PlenxoColors
import com.example.ui.theme.PlenxoSpacing
import com.example.ui.theme.PlenxoTypography
import com.example.ui.components.PlenxoAdvancedLoader
import android.net.Uri
import android.util.Log
import android.app.Application
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.util.PermissionManager
import com.example.util.NetworkConnectivityObserver
import com.example.util.NetworkStatus
import com.example.viewmodel.PlenxoScreen
import com.example.viewmodel.PlenxoViewModel
import com.example.viewmodel.ChatRequestViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viewmodel.NormalSettingsViewModel
import com.example.viewmodel.ProfileSettingsViewModel
import com.example.ui.NormalSettingsScreen
import com.example.ui.ProfileSettingsScreen
import com.example.ui.settings.LanguageSelectionScreen
import com.example.viewmodel.SettingsViewModel
import com.example.calling.CallManager
import com.example.calling.model.CallSession
import com.example.calling.model.CallState
import com.example.calling.model.CallType
import com.example.ui.calling.VoiceCallScreen
import com.example.ui.calling.VideoCallScreen
import com.example.ui.calling.IncomingCallOverlay
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

// Define clean Plenxo branding colors
val PlenxoGreen = Color(0xFF07C160)
val PlenxoDarkGreen = Color(0xFF06A752)
val PlenxoLightGreen = Color(0xFFE8F8F0)
val PlenxoBackground = Color(0xFFF7F7F7)

// Dynamic theme color mapper
@Composable
fun getThemeColors(themeName: String): Pair<Color, Color> {
    return when (themeName) {
        "Red" -> Color(0xFFE53935) to Color(0xFFB71C1C)
        "Blue" -> Color(0xFF1E88E5) to Color(0xFF0D47A1)
        "Purple" -> Color(0xFF8E24AA) to Color(0xFF4A148C)
        "Black" -> Color(0xFF212121) to Color(0xFF000000)
        "Golden" -> Color(0xFFFFB300) to Color(0xFFFF6F00)
        else -> PlenxoGreen to PlenxoDarkGreen
    }
}

@Composable
fun PresenceLifecycleTracker(viewModel: PlenxoViewModel) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentUserId = viewModel.currentUserId
    
    DisposableEffect(lifecycleOwner, viewModel, currentUserId) {
        if (currentUserId.isEmpty()) return@DisposableEffect onDispose {}
        
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.setupPresenceSystem()
                viewModel.setPresenceState("online")
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.setPresenceState("offline")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setPresenceState("offline")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlenxoAppContent(viewModel: PlenxoViewModel, permissionManager: PermissionManager) {
    PresenceLifecycleTracker(viewModel = viewModel)
    val currentScreen by viewModel.currentScreen.collectAsState()
    val application = LocalContext.current.applicationContext as Application

    val normalSettingsViewModel: NormalSettingsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )

    val profileSettingsViewModel: ProfileSettingsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )
    
    val authViewModel: com.example.viewmodel.AuthViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )
    
    val isLoading by viewModel.isLoading.collectAsState()

    val errorMessage by viewModel.errorMessage.collectAsState()
    val selectedThemeName by viewModel.selectedTheme.collectAsState()

    val (primaryColor, darkPrimaryColor) = getThemeColors(selectedThemeName)

    val activeCallSession by CallManager.activeCall.collectAsState()

    LaunchedEffect(Unit) {
        CallManager.setLogSaver { log ->
            viewModel.recordCallLog(log)
        }
    }

    val deepLinkResolutionState by viewModel.deepLinkResolutionState.collectAsState()
    val context = LocalContext.current

    // Spectacular Deep Link Resolution Dialog
    DeepLinkResolutionDialog(
        state = deepLinkResolutionState,
        onDismiss = { viewModel.clearDeepLinkResult() },
        onAddFriend = { viewModel.sendDeepLinkFriendRequest() },
        primaryColor = primaryColor
    )

    // Global Back Handler
    androidx.activity.compose.BackHandler(enabled = currentScreen != PlenxoScreen.HOME) {
        if (!viewModel.navigateBack()) {
            viewModel.navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
        }
    }

    val networkObserver = remember { NetworkConnectivityObserver(context) }
    val networkStatus by networkObserver.status.collectAsState()
    var previousStatus by remember { mutableStateOf<NetworkStatus?>(null) }

    LaunchedEffect(networkStatus) {
        if (previousStatus == NetworkStatus.Lost && networkStatus == NetworkStatus.Available) {
            Toast.makeText(context, "Back Online", Toast.LENGTH_SHORT).show()
        }
        previousStatus = networkStatus
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                 androidx.compose.animation.slideInHorizontally(
                     initialOffsetX = { (it * 0.05f).toInt() },
                     animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                 )) togetherWith
                (androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                 androidx.compose.animation.slideOutHorizontally(
                     targetOffsetX = { (-it * 0.05f).toInt() },
                     animationSpec = androidx.compose.animation.core.tween(150, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                 ))
            },
            label = "screen_transition"
        ) { screen ->
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                when (screen) {
            PlenxoScreen.SPLASH,
            PlenxoScreen.PLACEHOLDER_ENTRY,
            PlenxoScreen.EMAIL_VERIFICATION_WAIT -> {
                SplashScreen()
            }
            PlenxoScreen.LOGIN -> {
                com.example.ui.screens.auth.LoginScreen(
                    authViewModel = authViewModel,
                    onNavigateToSignUp = {
                        authViewModel.resetAuthState()
                        viewModel.navigateToScreen(PlenxoScreen.SIGN_UP)
                    },
                    onSignUpSuccess = {
                        com.example.util.SessionManager.saveOnboardingStage(context, com.example.util.SessionManager.STAGE_OTP_PENDING)
                        Log.d("PlenxoAuthFlow", "OTP_SCREEN_NAVIGATION from signup in LoginScreen")
                        viewModel.navigateToScreen(PlenxoScreen.OTP_VERIFICATION)
                    },
                    onNavigateToOtp = {
                        com.example.util.SessionManager.saveOnboardingStage(context, com.example.util.SessionManager.STAGE_OTP_PENDING)
                        Log.d("PlenxoAuthFlow", "OTP_SCREEN_NAVIGATION from onNavigateToOtp")
                        viewModel.navigateToScreen(PlenxoScreen.OTP_VERIFICATION)
                    },
                    onLoginSuccess = { userProfile ->
                        val stage = com.example.util.SessionManager.getSavedOnboardingStage(context)
                        if (stage == com.example.util.SessionManager.STAGE_OTP_PENDING) {
                            Log.d("PlenxoAuthFlow", "onLoginSuccess guard: STAGE_OTP_PENDING detected -> redirecting to OTP")
                            viewModel.navigateToScreen(PlenxoScreen.OTP_VERIFICATION)
                            return@LoginScreen
                        }
                        val isCompleted = stage == com.example.util.SessionManager.STAGE_COMPLETED ||
                            userProfile.isProfileCompleted ||
                            (userProfile.displayName.isNotBlank() && userProfile.displayName != "User") ||
                            (userProfile.name.isNotBlank() && userProfile.name != "User") ||
                            com.example.util.SessionManager.isOnboardingCompleted(context)
                        if (isCompleted) {
                            com.example.util.SessionManager.saveOnboardingStage(context, com.example.util.SessionManager.STAGE_COMPLETED)
                            com.example.util.SessionManager.saveOnboardingCompleted(context, true)
                            viewModel.hydrateUserProfile(userProfile)
                            Log.d("PlenxoAuthFlow", "HOME_NAVIGATION from login success")
                            viewModel.navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
                        } else {
                            when (stage) {
                                com.example.util.SessionManager.STAGE_WELCOME_PENDING -> {
                                    Log.d("PlenxoAuthFlow", "WELCOME_NAVIGATION from login success")
                                    viewModel.navigateToScreen(PlenxoScreen.WELCOME, addToHistory = false, clearHistory = true)
                                }
                                com.example.util.SessionManager.STAGE_REVEAL_PENDING -> {
                                    Log.d("PlenxoAuthFlow", "REVEAL_NAVIGATION from login success")
                                    viewModel.navigateToScreen(PlenxoScreen.PLENXO_ID_REVEAL, addToHistory = false, clearHistory = true)
                                }
                                else -> {
                                    com.example.util.SessionManager.saveOnboardingStage(context, com.example.util.SessionManager.STAGE_PROFILE_SETUP_PENDING)
                                    com.example.util.SessionManager.saveOnboardingCompleted(context, false)
                                    viewModel.hydrateUserProfile(userProfile)
                                    Log.d("PlenxoAuthFlow", "PROFILE_SETUP_NAVIGATION from login success")
                                    viewModel.navigateToScreen(PlenxoScreen.PROFILE_SETUP, addToHistory = false, clearHistory = true)
                                }
                            }
                        }
                    },
                    primaryColor = primaryColor
                )
            }
            PlenxoScreen.SIGN_UP -> {
                com.example.ui.screens.auth.SignUpScreen(
                    authViewModel = authViewModel,
                    onNavigateToLogin = {
                        authViewModel.resetAuthState()
                        viewModel.navigateToScreen(PlenxoScreen.LOGIN)
                    },
                    onSuccess = {
                        com.example.util.SessionManager.saveOnboardingStage(context, com.example.util.SessionManager.STAGE_OTP_PENDING)
                        viewModel.navigateToScreen(PlenxoScreen.OTP_VERIFICATION)
                    },
                    primaryColor = primaryColor
                )
            }
            PlenxoScreen.OTP_VERIFICATION -> {
                com.example.ui.OtpVerificationScreen(
                    authViewModel = authViewModel,
                    onSuccess = {
                        com.example.util.SessionManager.saveOnboardingStage(context, com.example.util.SessionManager.STAGE_WELCOME_PENDING)
                        viewModel.navigateToScreen(PlenxoScreen.WELCOME)
                    },
                    primaryColor = primaryColor
                )
            }
            PlenxoScreen.WELCOME -> {
                com.example.ui.screens.auth.WelcomeScreen(
                    onNext = {
                        authViewModel.onWelcomeNext()
                        com.example.util.SessionManager.saveOnboardingStage(context, com.example.util.SessionManager.STAGE_PROFILE_SETUP_PENDING)
                        viewModel.navigateToScreen(PlenxoScreen.PROFILE_SETUP)
                    },
                    primaryColor = primaryColor
                )
            }
            PlenxoScreen.PROFILE_SETUP -> {
                com.example.ui.profile.SetupProfileScreen(
                    authViewModel = authViewModel,
                    onNext = {
                        com.example.util.SessionManager.saveOnboardingStage(context, com.example.util.SessionManager.STAGE_REVEAL_PENDING)
                        viewModel.navigateToScreen(PlenxoScreen.PLENXO_ID_REVEAL)
                    },
                    primaryColor = primaryColor
                )
            }
            PlenxoScreen.PLENXO_ID_REVEAL -> {
                com.example.ui.profile.PlenxoIdRevealScreen(
                    authViewModel = authViewModel,
                    onDone = { userProfile ->
                        authViewModel.onFinishOnboarding()
                        com.example.util.SessionManager.saveOnboardingStage(context, com.example.util.SessionManager.STAGE_COMPLETED)
                        com.example.util.SessionManager.saveOnboardingCompleted(context, true)
                        viewModel.hydrateUserProfile(userProfile)
                        viewModel.navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
                    },
                    primaryColor = primaryColor
                )
            }
            PlenxoScreen.PERMISSION_GATEWAY -> {
                LaunchedEffect(Unit) {
                    viewModel.navigateToScreen(PlenxoScreen.HOME, addToHistory = false, clearHistory = true)
                }
            }
            PlenxoScreen.HOME -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF131824))
                ) {
                    ChatsListScreen(viewModel = viewModel, primaryColor = primaryColor)
                }
            }
            PlenxoScreen.CHAT_DETAIL -> {
                ChatDetailScreen(viewModel = viewModel, primaryColor = primaryColor, permissionManager = permissionManager)
            }
            PlenxoScreen.USER_PROFILE -> {
                UserProfileScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateBack() }
                )
            }
            PlenxoScreen.SETTINGS -> {
                SettingsScreen(viewModel = viewModel, primaryColor = primaryColor)
            }
            PlenxoScreen.SETTINGS_PRIVACY -> {
                SettingsPrivacyScreen(viewModel = viewModel, primaryColor = primaryColor)
            }
            PlenxoScreen.SETTINGS_BLOCKED -> {
                SettingsBlockedScreen(viewModel = viewModel, primaryColor = primaryColor)
            }
            PlenxoScreen.PROFILE_MANAGEMENT -> {
                ProfileManagementScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.navigateBack() }
                )
            }
            PlenxoScreen.WALLPAPER_GALLERY -> {
                WallpaperGalleryScreen(viewModel = viewModel)
            }
            PlenxoScreen.WALLPAPER_PREVIEW -> {
                WallpaperPreviewScreen(viewModel = viewModel)
            }
            PlenxoScreen.SETTINGS_NORMAL -> {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    NormalSettingsScreen(
                        viewModel = normalSettingsViewModel,
                        weChatViewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }
            PlenxoScreen.SETTINGS_PROFILE -> {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = scaleIn(initialScale = 0.82f) + fadeIn(),
                    exit = scaleOut(targetScale = 0.82f) + fadeOut()
                ) {
                    ProfileSettingsScreen(
                        viewModel = profileSettingsViewModel,
                        weChatViewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }
            PlenxoScreen.CHAT_REQUESTS -> {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    com.example.ui.settings.ChatRequestsScreen(
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }
            PlenxoScreen.PROFILE_RINGS -> {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    ProfileRingsScreen(
                        viewModel = profileSettingsViewModel,
                        weChatViewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }
            PlenxoScreen.DISCOVERY -> {
                com.example.ui.search.UserSearchScreen(
                    onBack = { viewModel.navigateBack() },
                    plenxoViewModel = viewModel
                )
            }
            PlenxoScreen.ACTIVE_SESSIONS -> {
                ActiveSessionsScreen(viewModel = viewModel)
            }
            PlenxoScreen.APP_LOCK_SETUP -> {
                // This is handled by an activity but we need a branch for exhaustiveness
                // or we can just navigate to login if it ever reaches here
            }
            PlenxoScreen.LANGUAGE_SELECTION -> {
                val settingsViewModel: SettingsViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(
                        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
                    )
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                ) {
                    LanguageSelectionScreen(
                        viewModel = settingsViewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }
            PlenxoScreen.CALL_HISTORY -> {
                CallHistoryScreen(viewModel = viewModel, onBack = { viewModel.navigateBack() })
            }
        }
        }
        }

        // Automatic safety endpoint for global loading overlay (never freeze screen indefinitely)
        LaunchedEffect(isLoading) {
            if (isLoading) {
                kotlinx.coroutines.delay(6000L)
                if (viewModel.isLoading.value) {
                    viewModel.clearLoading()
                }
            }
        }

        // Full Screen Advanced Loading Indicator Overlay
        if (isLoading && currentScreen != PlenxoScreen.OTP_VERIFICATION) {
            Dialog(
                onDismissRequest = { viewModel.clearLoading() },
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF131824),
                    border = BorderStroke(
                        1.dp,
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(PlenxoColors.Primary, PlenxoColors.Secondary)
                        )
                    ),
                    modifier = Modifier.testTag("loading_dialog")
                ) {
                    PlenxoAdvancedLoader(
                        modifier = Modifier.padding(24.dp),
                        statusText = "Loading..."
                    )
                }
            }
        }

        // Error Dialog
            errorMessage?.let { error ->
            val safeError = error.ifEmpty { "An unknown error occurred. Please try again." }
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error Logo",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(stringResource(id = R.string.str_notification),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                text = {
                    Text(
                        text = safeError,
                        fontSize = 15.sp,
                        color = Color.DarkGray
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.clearError() },
                        modifier = Modifier.testTag("dismiss_error_button")
                    ) {
                        Text(stringResource(id = R.string.str_dismiss),
                            color = primaryColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White,
                modifier = Modifier.testTag("error_dialog")
            )
        }

        AnimatedVisibility(
            visible = networkStatus == NetworkStatus.Lost || networkStatus == NetworkStatus.Weak,
            enter = fadeIn() + androidx.compose.animation.slideInVertically(),
            exit = fadeOut() + androidx.compose.animation.slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val bannerText = if (networkStatus == NetworkStatus.Lost) {
                "No Internet Connection - Working Offline"
            } else {
                "Weak Connection Detected"
            }
            val bannerColor = if (networkStatus == NetworkStatus.Lost) {
                Color(0xFFFF4D4F)
            } else {
                Color(0xFFFFC107)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bannerColor)
                    .padding(vertical = 4.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bannerText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Stage 1 Audio/Video Calling UI Overlays with complete separation
        activeCallSession?.let { session ->
            if (session.callState == CallState.INCOMING_RINGING) {
                IncomingCallOverlay(
                    session = session,
                    onAccept = { CallManager.acceptCall() },
                    onDecline = { CallManager.declineCall() }
                )
            } else if (session.callType == CallType.VOICE) {
                VoiceCallScreen(session = session)
            } else {
                VideoCallScreen(session = session)
            }
        }
    }
}

@Composable
fun TermsAndPrivacyCheckboxRow(
    viewModel: PlenxoViewModel,
    errorMessage: String?
) {
    val isTermsAccepted by viewModel.isTermsAccepted.collectAsState()
    val context = LocalContext.current
    val isError = errorMessage?.contains("Terms", ignoreCase = true) == true

    val annotatedString = buildAnnotatedString {
        append("I agree to Plenxo's ")
        pushStringAnnotation(tag = "URL", annotation = "https://coderhamid01-afk.github.io/Term/terms.html")
        withStyle(SpanStyle(color = PlenxoColors.Primary, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
            append("Terms & Conditions")
        }
        pop()
        append(" and ")
        pushStringAnnotation(tag = "URL", annotation = "https://coderhamid01-afk.github.io/Term/privacy.html")
        withStyle(SpanStyle(color = PlenxoColors.Primary, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
            append("Privacy Policy")
        }
        pop()
        append(".")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isError) {
                    Modifier
                        .background(Color(0x22FF4D4F), shape = RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFFF4D4F), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp)
                } else {
                    Modifier.padding(vertical = 4.dp)
                }
            )
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                viewModel.isTermsAccepted.value = !isTermsAccepted
                if (errorMessage != null) viewModel.clearError()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isTermsAccepted,
            onCheckedChange = { checked ->
                viewModel.isTermsAccepted.value = checked
                if (errorMessage != null) viewModel.clearError()
            },
            colors = CheckboxDefaults.colors(
                checkedColor = PlenxoColors.Primary,
                uncheckedColor = if (isError) Color(0xFFFF4D4F) else Color.LightGray,
                checkmarkColor = Color.White
            ),
            modifier = Modifier.testTag("terms_checkbox")
        )
        Spacer(modifier = Modifier.width(4.dp))
        ClickableText(
            text = annotatedString,
            style = PlenxoTypography.Body.copy(
                color = Color.White,
                fontSize = 13.sp
            ),
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        com.example.util.LegalWebUtils.openUrl(context, annotation.item)
                    } ?: run {
                        viewModel.isTermsAccepted.value = !isTermsAccepted
                        if (errorMessage != null) viewModel.clearError()
                    }
            },
            modifier = Modifier
                .weight(1f)
                .testTag("terms_checkbox_text")
        )
    }
}

// FINAL COMPLETED LANDING SCREEN - Implemented in HomeScreen.kt

@Composable
fun ProfileRow(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(accentColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color.Black,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * A spectacularly styled dialog for resolving deep-linked friend requests.
 */
@Composable
fun DeepLinkResolutionDialog(
    state: com.example.viewmodel.DeepLinkResolutionState,
    onDismiss: () -> Unit,
    onAddFriend: () -> Unit,
    primaryColor: Color
) {
    if (state == com.example.viewmodel.DeepLinkResolutionState.Idle) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(enabled = state !is com.example.viewmodel.DeepLinkResolutionState.Resolving) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (state) {
                        is com.example.viewmodel.DeepLinkResolutionState.Resolving -> {
                            CircularProgressIndicator(color = primaryColor)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(id = R.string.str_searching_for_friend),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Gray
                            )
                        }
                        is com.example.viewmodel.DeepLinkResolutionState.ValidProfileFound -> {
                            val profile = state.profile
                            
                            Box(
                                modifier = Modifier.size(110.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                com.example.ui.components.ProfileRingBox(ringId = profile.profileRingId, ringPadding = 4.dp, borderWidth = 5.dp) {
                                    Box(
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF0F0F0))
                                    ) {
                                        if (profile.profilePicUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = profile.profilePicUrl,
                                                contentDescription = "Profile Picture",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(0.6f).align(Alignment.Center),
                                                tint = Color.LightGray
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = profile.displayName.ifEmpty { "User ${profile.userCode}" },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            
                            if (profile.userCode.isNotEmpty()) {
                                Text(
                                    text = profile.userCode,
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = profile.statusMessage.ifEmpty { "Hey there! I am using Plenxo." },
                                fontSize = 14.sp,
                                color = Color.DarkGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Button(
                                onClick = onAddFriend,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.str_send_friend_request), fontWeight = FontWeight.Bold)
                            }
                        }
                        is com.example.viewmodel.DeepLinkResolutionState.InvalidOrExpired -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Red
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(id = R.string.str_oops),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.easyMessage,
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                            ) {
                                Text(stringResource(R.string.str_okay), fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
