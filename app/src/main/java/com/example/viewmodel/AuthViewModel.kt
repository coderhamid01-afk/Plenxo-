package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.UserProfile
import com.example.model.toUserModel
import com.example.network.CatboxUploader
import com.example.network.OtpApiService
import com.example.network.SendOtpRequest
import com.example.repository.FirestoreUserBootstrapper
import com.example.repository.UserRepository
import com.example.repository.UserRepositoryImpl
import com.example.util.SessionManager
import com.example.util.EmailUtils
import com.example.util.getDocumentServerFirst
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import kotlin.math.abs
import kotlin.random.Random

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG_AUTH = "PLENXO_AUTH"
        private const val TAG_OTP = "PLENXO_OTP"
        private const val TAG_PROFILE = "PLENXO_PROFILE"
        private const val TAG_FS = "PLENXO_FIRESTORE"
    }

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val userRepository: UserRepository by lazy { UserRepositoryImpl() }
    private val otpApiService by lazy { OtpApiService.create() }
    private var secretOtp: String = ""

    // SignUp States
    val signUpEmail = MutableStateFlow("")
    val signUpPassword = MutableStateFlow("")
    val confirmPassword = MutableStateFlow("")
    val isTermsAccepted = MutableStateFlow(false)
    val signUpCaptchaInput = MutableStateFlow("")
    val signUpCaptchaDisplay = MutableStateFlow("")
    private var expectedSignUpCaptcha = ""
    
    val isSignUpLoading = MutableStateFlow(false)
    val signUpError = MutableStateFlow<String?>(null)
    val signUpSuccess = MutableStateFlow(false)

    // Login States
    val loginEmail = MutableStateFlow("")
    val loginPassword = MutableStateFlow("")
    val loginCaptchaInput = MutableStateFlow("")
    val loginCaptchaDisplay = MutableStateFlow("")
    val isLoginTermsAccepted = MutableStateFlow(false)
    private var expectedLoginCaptcha = ""
    
    val isLoginLoading = MutableStateFlow(false)
    val loginError = MutableStateFlow<String?>(null)
    val loginSuccess = MutableStateFlow(false)

    // OTP States
    val otpInput = MutableStateFlow("")
    val activeOtp = MutableStateFlow("")
    val secondsRemaining = MutableStateFlow(60)
    val isTimerRunning = MutableStateFlow(false)
    val otpError = MutableStateFlow<String?>(null)
    val isVerifyingOtp = MutableStateFlow(false)
    val otpSuccess = MutableStateFlow(false)
    val requiresOtp = MutableStateFlow(false)
    private var timerJob: Job? = null

    // Profile Setup States
    val profilePicUrl = MutableStateFlow("")
    val name = MutableStateFlow("")
    val bio = MutableStateFlow("")
    val dob = MutableStateFlow("") // Format: DD-MM-YYYY or YYYY-MM-DD
    val calculatedAge = MutableStateFlow("")
    val gender = MutableStateFlow("Male") // Default selection
    val isProfileSetupLoading = MutableStateFlow(false)
    val profileSetupError = MutableStateFlow<String?>(null)
    val profileSetupSuccess = MutableStateFlow(false)

    // Plenxo ID Reveal States
    val plenxoId = MutableStateFlow("")
    val isSavingProfileAndId = MutableStateFlow(false)
    val plenxoIdRevealSuccess = MutableStateFlow(false)

    init {
        generateSignUpCaptcha()
        generateLoginCaptcha()
    }

    // Captcha Generators
    fun generateSignUpCaptcha() {
        val num1 = Random.nextInt(10, 99)
        val num2 = Random.nextInt(1, 10)
        val isPlus = Random.nextBoolean()
        if (isPlus) {
            signUpCaptchaDisplay.value = "$num1 + $num2"
            expectedSignUpCaptcha = (num1 + num2).toString()
        } else {
            signUpCaptchaDisplay.value = "$num1 - $num2"
            expectedSignUpCaptcha = (num1 - num2).toString()
        }
        signUpCaptchaInput.value = ""
    }

    fun markSignUpCaptchaVerified() {
        signUpCaptchaInput.value = expectedSignUpCaptcha
    }

    fun generateLoginCaptcha() {
        val num1 = Random.nextInt(10, 99)
        val num2 = Random.nextInt(1, 10)
        val isPlus = Random.nextBoolean()
        if (isPlus) {
            loginCaptchaDisplay.value = "$num1 + $num2"
            expectedLoginCaptcha = (num1 + num2).toString()
        } else {
            loginCaptchaDisplay.value = "$num1 - $num2"
            expectedLoginCaptcha = (num1 - num2).toString()
        }
        loginCaptchaInput.value = ""
    }

    fun markLoginCaptchaVerified() {
        loginCaptchaInput.value = expectedLoginCaptcha
    }

    // Real-time DoB Age Calculation
    fun updateDob(newDob: String) {
        dob.value = newDob
        val age = calculateAge(newDob)
        if (age != null) {
            calculatedAge.value = age.toString()
        } else {
            calculatedAge.value = ""
        }
    }

    private fun calculateAge(dobString: String): Int? {
        return try {
            val parts = dobString.split("-", "/", ".")
            if (parts.size == 3) {
                val year = if (parts[2].length == 4) parts[2].toInt() else if (parts[0].length == 4) parts[0].toInt() else return null
                val month = if (parts[2].length == 4) parts[1].toInt() else parts[1].toInt()
                val day = if (parts[2].length == 4) parts[0].toInt() else parts[2].toInt()

                val birthCalendar = Calendar.getInstance().apply {
                    set(year, month - 1, day)
                }
                val today = Calendar.getInstance()
                var age = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
                if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
                    age--
                }
                if (age >= 0) age else 0
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // OTP Timer
    fun startOtpTimer() {
        timerJob?.cancel()
        secondsRemaining.value = 60
        isTimerRunning.value = true
        timerJob = viewModelScope.launch {
            while (secondsRemaining.value > 0) {
                delay(1000L)
                secondsRemaining.value--
            }
            isTimerRunning.value = false
        }
    }

    fun resendOtp() {
        val mail = signUpEmail.value.trim().ifEmpty {
            auth.currentUser?.email
                ?: SessionManager.getUserEmail(getApplication())
                ?: SessionManager.getPendingOtp(getApplication())?.first
                ?: loginEmail.value.trim()
        }
        if (mail.isBlank()) {
            otpError.value = "Email address not found. Please log in again."
            return
        }
        signUpEmail.value = mail
        val generated = Random.nextInt(100000, 1000000).toString()
        secretOtp = generated
        otpInput.value = ""
        otpError.value = null

        SessionManager.savePendingOtp(getApplication(), mail, generated)
        SessionManager.saveOnboardingStage(getApplication(), SessionManager.STAGE_OTP_PENDING)
        Log.d("PlenxoAuthFlow", "RESEND_OTP initiated for $mail")

        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    otpApiService.sendOtp(
                        SendOtpRequest(
                            email = mail,
                            purpose = "signup",
                            otp = generated
                        )
                    )
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Verification code: $generated", Toast.LENGTH_LONG).show()
                    if (response.isSuccessful) {
                        startOtpTimer()
                    } else {
                        otpError.value = "Email delivery failed, but you can use the code shown on screen."
                        startOtpTimer()
                    }
                }
            } catch (e: Exception) {
                Log.e("PlenxoAuthFlow", "Resend OTP failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Verification code: $generated", Toast.LENGTH_LONG).show()
                    startOtpTimer()
                }
            }
        }
    }

    // 1. Sign Up Flow
    fun performSignUp() {
        val mail = signUpEmail.value.trim()
        val pwd = signUpPassword.value
        val conf = confirmPassword.value
        val captchaVal = signUpCaptchaInput.value.trim()
        val terms = isTermsAccepted.value || isLoginTermsAccepted.value

        signUpError.value = null

        if (mail.isEmpty() || pwd.isEmpty() || conf.isEmpty()) {
            signUpError.value = "All fields are required"
            return
        }
        if (!EmailUtils.isAllowedEmailDomain(mail)) {
            signUpError.value = EmailUtils.INVALID_DOMAIN_ERROR_MESSAGE
            return
        }
        if (pwd != conf) {
            signUpError.value = "Passwords do not match"
            return
        }
        if (pwd.length < 6) {
            signUpError.value = "Password must be at least 6 characters"
            return
        }
        if (captchaVal != expectedSignUpCaptcha) {
            signUpError.value = "Incorrect Captcha answer"
            generateSignUpCaptcha()
            return
        }
        if (!terms) {
            signUpError.value = "You must accept the Terms and Services"
            return
        }

        isSignUpLoading.value = true
        viewModelScope.launch {
            try {
                // 1. Firebase Authentication: Create User Account with 15s timeout guard
                Log.d("PlenxoSignup", "AUTH START for $mail")
                val authResult = withTimeoutOrNull(15000L) {
                    auth.createUserWithEmailAndPassword(mail, pwd).await()
                } ?: throw Exception("Signup timed out. Please check your internet connection and try again.")
                
                val user = authResult.user ?: auth.currentUser ?: throw Exception("Failed to obtain User object from Firebase Authentication")
                val uid = auth.currentUser?.uid ?: user.uid
                val userEmail = user.email ?: auth.currentUser?.email ?: mail

                Log.d(TAG_AUTH, "Operation: SIGNUP_AUTH_SUCCESS, UID: $uid, Email: $userEmail")

                // 2. Generate exactly 6-digit OTP
                val secretCode = Random.nextInt(100000, 1000000).toString()
                secretOtp = secretCode
                Log.d(TAG_OTP, "Operation: GENERATE_OTP, status: SUCCESS, purpose: signup")

                // 3. Save local session, encrypted pending OTP, and onboarding state
                SessionManager.saveLoginState(getApplication(), uid, userEmail)
                SessionManager.savePendingOtp(getApplication(), userEmail, secretCode)
                SessionManager.saveOnboardingStage(getApplication(), SessionManager.STAGE_OTP_PENDING)

                // 4. Fire-and-forget background OTP email dispatch
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        otpApiService.sendOtp(
                            SendOtpRequest(
                                email = userEmail,
                                purpose = "signup",
                                otp = secretCode
                            )
                        )
                        Log.d(TAG_OTP, "Operation: SEND_OTP_EMAIL, status: SUCCESS")
                    } catch (apiEx: Exception) {
                        Log.w(TAG_OTP, "Operation: SEND_OTP_EMAIL, status: WARNING, error: ${apiEx.message}")
                    }
                }

                // 5. Bootstrap Firestore Collections via FirestoreUserBootstrapper (Priority: users -> users_data -> presence)
                Log.d(TAG_FS, "Operation: BOOTSTRAP_USER_START, UID: $uid, Path: users/$uid")
                val bootstrapResult = withContext(Dispatchers.IO) {
                    FirestoreUserBootstrapper.initializeUser(uid, userEmail)
                }

                if (!bootstrapResult.success) {
                    val detailedMsg = bootstrapResult.errorMessage ?: "Failed to initialize user document in database"
                    Log.e(TAG_FS, "Operation: BOOTSTRAP_USER_FAILURE, UID: $uid, error: $detailedMsg")
                    throw Exception(detailedMsg)
                }

                Log.d(TAG_PROFILE, "Operation: BOOTSTRAP_USER_SUCCESS, UID: $uid, Plenxo ID: ${bootstrapResult.plenxoId}")
                Log.d("PlenxoSignup", "FINAL SIGNUP INITIALIZATION RESULT: Success for UID=$uid")

                // Pre-populate Plenxo ID in local session and state
                this@AuthViewModel.plenxoId.value = bootstrapResult.plenxoId
                SessionManager.saveUserProfileLocally(
                    getApplication(),
                    plenxoId = bootstrapResult.plenxoId,
                    displayName = if (userEmail.contains("@")) userEmail.substringBefore("@") else "User"
                )

                // 6. Navigate to OTP verification only after Firestore document write succeeds
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Account created! Verification code: $secretCode", Toast.LENGTH_LONG).show()
                    isSignUpLoading.value = false
                    startOtpTimer()
                    signUpSuccess.value = true
                    requiresOtp.value = true
                    Log.d("PlenxoAuthFlow", "OTP_SCREEN_NAVIGATION from signup flow")
                }
            } catch (e: FirebaseAuthWeakPasswordException) {
                Log.e("AuthViewModel", "Signup weak password: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val msg = "Password is too weak. Please use at least 6 characters with letters and numbers."
                    signUpError.value = msg
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: FirebaseAuthUserCollisionException) {
                Log.e("AuthViewModel", "Signup user collision: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val msg = "An account with this email address already exists. Please login instead."
                    signUpError.value = msg
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                Log.e("AuthViewModel", "Signup invalid credentials: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val msg = "Invalid email format. Please check your email address."
                    signUpError.value = msg
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: FirebaseNetworkException) {
                Log.e("AuthViewModel", "Signup network error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val msg = "Network connection failed. Please check your internet connection."
                    signUpError.value = msg
                    Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Signup failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val rawMsg = e.localizedMessage ?: e.message ?: "Failed to create account"
                    val errorMsg = when {
                        rawMsg.contains("already in use", ignoreCase = true) || rawMsg.contains("email-already-in-use", ignoreCase = true) ->
                            "An account with this email address already exists. Please login instead."
                        rawMsg.contains("badly formatted", ignoreCase = true) || rawMsg.contains("invalid-email", ignoreCase = true) ->
                            "Please enter a valid email address."
                        rawMsg.contains("weak-password", ignoreCase = true) ->
                            "Password is too weak. Please use at least 6 characters."
                        auth.currentUser != null ->
                            "Account created but profile setup failed: $rawMsg. Please try logging in."
                        else -> "Sign up failed: $rawMsg"
                    }
                    signUpError.value = errorMsg
                    Toast.makeText(getApplication(), errorMsg, Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSignUpLoading.value = false
                }
            }
        }
    }

    // 2. OTP Verification
    fun verifyOtp() {
        val entered = otpInput.value.trim()
        val persistedPending = SessionManager.getPendingOtp(getApplication())
        val localSecret = secretOtp.trim().ifEmpty { persistedPending?.second ?: "" }
        val userEmail = auth.currentUser?.email
            ?: SessionManager.getUserEmail(getApplication())
            ?: persistedPending?.first
            ?: ""

        otpError.value = null
        if (entered.isEmpty()) {
            otpError.value = "Please enter verification code"
            return
        }

        isVerifyingOtp.value = true
        viewModelScope.launch {
            try {
                var isVerified = false

                // 1. Attempt server-side verification if email is available
                if (userEmail.isNotEmpty()) {
                    try {
                        val serverResponse = withTimeoutOrNull(5000L) {
                            otpApiService.verifyOtp(
                                com.example.network.VerifyOtpRequest(
                                    email = userEmail,
                                    otp = entered,
                                    purpose = "signup"
                                )
                            )
                        }
                        if (serverResponse != null && serverResponse.isSuccessful) {
                            val body = serverResponse.body()
                            if (body?.success == true || body?.valid == true) {
                                isVerified = true
                            }
                        }
                    } catch (netEx: Exception) {
                        Log.w("AuthViewModel", "Server OTP verification request fallback: ${netEx.message}")
                    }
                }

                // 2. Fallback to persisted / in-memory OTP check
                if (!isVerified) {
                    if (localSecret.isNotEmpty() && entered == localSecret) {
                        isVerified = true
                    }
                }

                if (isVerified) {
                    Log.d("PlenxoAuthFlow", "OTP_VERIFICATION_SUCCESS for $userEmail")
                    timerJob?.cancel()
                    isTimerRunning.value = false
                    SessionManager.clearPendingOtp(getApplication())
                    SessionManager.saveOnboardingStage(getApplication(), SessionManager.STAGE_WELCOME_PENDING)
                    otpSuccess.value = true
                } else {
                    Log.w("PlenxoAuthFlow", "OTP_VERIFICATION_FAILURE: Invalid OTP code")
                    otpError.value = "Invalid OTP code, please try again"
                }
            } catch (e: Exception) {
                Log.e("PlenxoAuthFlow", "OTP verification failed: ${e.message}", e)
                otpError.value = e.localizedMessage ?: "Verification failed. Please try again."
            } finally {
                isVerifyingOtp.value = false
            }
        }
    }

    fun onWelcomeNext() {
        SessionManager.saveOnboardingStage(getApplication(), SessionManager.STAGE_PROFILE_SETUP_PENDING)
    }

    // 3. Profile Setup Step
    fun completeProfileSetup() {
        val n = name.value.trim()
        val b = bio.value.trim()
        val d = dob.value.trim()
        val a = calculatedAge.value.trim()
        val g = gender.value

        profileSetupError.value = null
        profileSetupSuccess.value = false

        if (n.isEmpty()) {
            profileSetupError.value = "Display Name is required"
            isProfileSetupLoading.value = false
            return
        }

        isProfileSetupLoading.value = true
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid
                    ?: SessionManager.getUserId(getApplication()).takeIf { it.isNotBlank() && !it.startsWith("guest_") }
                    ?: throw Exception("Firebase user is not authenticated. Please log in again.")
                val emailAddr = auth.currentUser?.email
                    ?: SessionManager.getUserEmail(getApplication())

                // 1. Resolve or generate permanent Plenxo ID
                val uniqueId = withContext(Dispatchers.IO) {
                    com.example.model.getOrCreatePermanentPlenxoId(uid, firestore)
                }
                plenxoId.value = uniqueId
                val numericCode = uniqueId.removePrefix("PX-")

                // Handle Catbox upload if image is a local URI
                var finalPic = profilePicUrl.value.trim()
                if (finalPic.startsWith("content://") || finalPic.startsWith("file://")) {
                    try {
                        val uploaded = CatboxUploader.uploadImage(getApplication(), Uri.parse(finalPic))
                        if (uploaded.isNotBlank() && uploaded.startsWith("http")) {
                            finalPic = uploaded
                            profilePicUrl.value = uploaded
                        } else {
                            throw Exception("Failed to receive image URL from Catbox")
                        }
                    } catch (uploadEx: Exception) {
                        Log.e("AuthViewModel", "Catbox upload failed in completeProfileSetup: ${uploadEx.message}", uploadEx)
                        throw Exception("Failed to upload profile picture: ${uploadEx.localizedMessage ?: "Image upload failed"}")
                    }
                }
                if (finalPic.isBlank() || finalPic.startsWith("content://") || finalPic.startsWith("file://")) {
                    finalPic = "https://placehold.co/150/07C160/ffffff?text=" + n.take(1)
                }

                // 2. Persist profile to Firestore with safety timeout and failure handling
                val now = System.currentTimeMillis()
                val userMap = mapOf(
                    "uid" to uid,
                    "id" to uid,
                    "email" to emailAddr,
                    "name" to n,
                    "displayName" to n,
                    "display_name" to n,
                    "bio" to b,
                    "statusMessage" to b,
                    "dob" to d,
                    "dateOfBirth" to d,
                    "age" to a,
                    "gender" to g,
                    "profilePicUrl" to finalPic,
                    "avatar_url" to finalPic,
                    "photoUrl" to finalPic,
                    "profileUrl" to finalPic,
                    "plenxoId" to uniqueId,
                    "plenxo_id" to uniqueId,
                    "userCode" to numericCode,
                    "user_code" to numericCode,
                    "px_id" to uniqueId,
                    "px_code" to numericCode,
                    "status" to "online",
                    "createdAt" to now,
                    "updatedAt" to now,
                    "isProfileCompleted" to true,
                    "is_profile_completed" to true,
                    "isProfileSetupCompleted" to true,
                    "profileSetupCompleted" to true
                )

                withContext(Dispatchers.IO) {
                    try {
                        val writeSuccess = withTimeoutOrNull(10000L) {
                            val updated = userRepository.updateUserProfile(uid, userMap)
                            if (!updated) {
                                throw Exception("Failed to update profile in database")
                            }
                            true
                        } ?: false

                        if (!writeSuccess) {
                            throw Exception("Firestore write timed out. Please check your network connection and retry.")
                        }

                        // Also populate users_data collection
                        try {
                            firestore.collection("users_data").document(uid)
                                .set(userMap, SetOptions.merge())
                                .await()
                        } catch (udEx: Exception) {
                            Log.w("AuthViewModel", "Optional users_data write warning: ${udEx.message}")
                        }

                        Log.d("AuthViewModel", "Successfully saved /users/$uid document via UserRepository")
                    } catch (fsEx: Exception) {
                        Log.e("AuthViewModel", "Firestore set failed in completeProfileSetup: ${fsEx.message}", fsEx)
                        throw fsEx
                    }

                    // Save locally for instant offline/session restore
                    SessionManager.saveUserProfileLocally(
                        getApplication(),
                        plenxoId = uniqueId,
                        displayName = n,
                        bio = b,
                        profilePicUrl = finalPic,
                        age = a,
                        dob = d,
                        gender = g
                    )
                    SessionManager.saveLoginState(getApplication(), uid, emailAddr)
                }

                withContext(Dispatchers.Main) {
                    SessionManager.saveOnboardingStage(getApplication(), SessionManager.STAGE_REVEAL_PENDING)
                    profileSetupSuccess.value = true
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Profile setup failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val errMsg = e.localizedMessage ?: "Failed to save profile. Please try again."
                    profileSetupError.value = errMsg
                    profileSetupSuccess.value = false
                    isProfileSetupLoading.value = false
                    Toast.makeText(getApplication(), errMsg, Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isProfileSetupLoading.value = false
                    isSavingProfileAndId.value = false
                }
            }
        }
    }

    // 4. Save Final Profile & Plenxo ID
    fun saveFinalProfileAndReveal(onSuccess: (UserProfile) -> Unit) {
        val uid = auth.currentUser?.uid
            ?: SessionManager.getUserId(getApplication()).takeIf { it.isNotBlank() && !it.startsWith("guest_") }
            ?: run {
                isSavingProfileAndId.value = false
                return
            }
        val emailAddr = auth.currentUser?.email ?: SessionManager.getUserEmail(getApplication())
        
        isSavingProfileAndId.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val pxId = if (plenxoId.value.isNotBlank()) {
                plenxoId.value
            } else {
                com.example.model.getOrCreatePermanentPlenxoId(uid, firestore)
            }
            plenxoId.value = pxId
            val numericCode = pxId.removePrefix("PX-")

            try {
                val now = System.currentTimeMillis()
                val savedLocal = SessionManager.getUserProfileLocally(getApplication())
                val resolvedDisplayName = name.value.trim().ifBlank { savedLocal.displayName }.ifBlank { "User" }
                val resolvedBio = bio.value.trim().ifBlank { savedLocal.bio }
                
                var finalPic = profilePicUrl.value.ifBlank { savedLocal.profilePicUrl }
                if (finalPic.startsWith("content://") || finalPic.startsWith("file://")) {
                    try {
                        val uploaded = CatboxUploader.uploadImage(getApplication(), Uri.parse(finalPic))
                        if (uploaded.isNotBlank() && uploaded.startsWith("http")) {
                            finalPic = uploaded
                            profilePicUrl.value = uploaded
                        }
                    } catch (uploadEx: Exception) {
                        Log.e("AuthViewModel", "Catbox upload failed in saveFinalProfileAndReveal: ${uploadEx.message}", uploadEx)
                    }
                }
                if (finalPic.isBlank() || finalPic.startsWith("content://") || finalPic.startsWith("file://")) {
                    finalPic = "https://placehold.co/150/07C160/ffffff?text=" + resolvedDisplayName.take(1)
                }

                val userMap = mapOf(
                    "uid" to uid,
                    "id" to uid,
                    "email" to emailAddr,
                    "name" to resolvedDisplayName,
                    "displayName" to resolvedDisplayName,
                    "display_name" to resolvedDisplayName,
                    "bio" to resolvedBio,
                    "statusMessage" to resolvedBio,
                    "dob" to dob.value.trim().ifBlank { savedLocal.dob },
                    "dateOfBirth" to dob.value.trim().ifBlank { savedLocal.dob },
                    "age" to calculatedAge.value.trim().ifBlank { savedLocal.age },
                    "gender" to gender.value.ifBlank { savedLocal.gender },
                    "profilePicUrl" to finalPic,
                    "avatar_url" to finalPic,
                    "photoUrl" to finalPic,
                    "profileUrl" to finalPic,
                    "plenxoId" to pxId,
                    "plenxo_id" to pxId,
                    "userCode" to numericCode,
                    "user_code" to numericCode,
                    "px_id" to pxId,
                    "px_code" to numericCode,
                    "status" to "online",
                    "createdAt" to now,
                    "updatedAt" to now,
                    "isProfileCompleted" to true,
                    "is_profile_completed" to true,
                    "isProfileSetupCompleted" to true,
                    "profileSetupCompleted" to true
                )

                try {
                    val writeSuccess = withTimeoutOrNull(10000L) {
                        val updated = userRepository.updateUserProfile(uid, userMap)
                        if (!updated) {
                            throw Exception("Failed to update profile in database")
                        }
                        true
                    } ?: false

                    if (!writeSuccess) {
                        throw Exception("Firestore save timed out. Please check your connection.")
                    }
                    Log.d("AuthViewModel", "Successfully saved /users/$uid in saveFinalProfileAndReveal via UserRepository")
                } catch (fsEx: Exception) {
                    Log.e("AuthViewModel", "Firestore save error in saveFinalProfileAndReveal: ${fsEx.message}", fsEx)
                    throw fsEx
                }

                // Save locally too
                SessionManager.saveUserProfileLocally(
                    getApplication(),
                    plenxoId = pxId,
                    displayName = resolvedDisplayName,
                    bio = resolvedBio,
                    profilePicUrl = finalPic,
                    age = calculatedAge.value.trim().ifBlank { savedLocal.age },
                    dob = dob.value.trim().ifBlank { savedLocal.dob },
                    gender = gender.value.ifBlank { savedLocal.gender }
                )
                SessionManager.saveLoginState(getApplication(), uid, emailAddr)
                SessionManager.saveOnboardingStage(getApplication(), SessionManager.STAGE_COMPLETED)
                SessionManager.saveOnboardingCompleted(getApplication(), true)

                val domainModel = UserProfile(
                    uid = uid,
                    id = uid,
                    email = emailAddr,
                    displayName = resolvedDisplayName,
                    bio = resolvedBio,
                    statusMessage = resolvedBio,
                    profilePicUrl = finalPic,
                    plenxoId = pxId,
                    userCode = numericCode
                )

                withContext(Dispatchers.Main) {
                    plenxoIdRevealSuccess.value = true
                    onSuccess(domainModel)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to save final profile to Firestore: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val errMsg = e.localizedMessage ?: "Failed to save identity to database"
                    profileSetupError.value = errMsg
                    isSavingProfileAndId.value = false
                    Toast.makeText(getApplication(), errMsg, Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSavingProfileAndId.value = false
                }
            }
        }
    }

    fun onFinishOnboarding() {
        SessionManager.saveOnboardingStage(getApplication(), SessionManager.STAGE_COMPLETED)
        SessionManager.saveOnboardingCompleted(getApplication(), true)
    }

    // 5. Login Flow
    fun performLogin(
        onSuccess: (UserProfile) -> Unit,
        onNavigateToOtp: (() -> Unit)? = null
    ) {
        val mail = loginEmail.value.trim()
        val pwd = loginPassword.value
        val captchaVal = loginCaptchaInput.value.trim()
        val terms = isLoginTermsAccepted.value || isTermsAccepted.value

        loginError.value = null

        if (mail.isEmpty() || pwd.isEmpty()) {
            loginError.value = "Email and Password are required"
            return
        }
        if (!EmailUtils.isAllowedEmailDomain(mail)) {
            loginError.value = EmailUtils.INVALID_DOMAIN_ERROR_MESSAGE
            return
        }
        if (captchaVal != expectedLoginCaptcha) {
            loginError.value = "Incorrect Captcha answer"
            generateLoginCaptcha()
            return
        }
        if (!terms) {
            loginError.value = "You must accept the Terms and Services"
            return
        }

        if (isLoginLoading.value) return

        isLoginLoading.value = true
        Log.d(TAG_AUTH, "Operation: LOGIN_START, email: $mail")

        viewModelScope.launch {
            try {
                // 1. Authenticate with 10s timeout guard
                val result = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    auth.signInWithEmailAndPassword(mail, pwd).await()
                } ?: throw Exception("Login timed out. Please check your network connection.")

                val firebaseUser = result.user ?: throw Exception("Auth returned null user ID")
                val uid = firebaseUser.uid
                val userEmail = firebaseUser.email ?: mail

                Log.d(TAG_AUTH, "Operation: LOGIN_AUTH_SUCCESS, UID: $uid, email: $userEmail")

                SessionManager.saveLoginState(getApplication(), uid, userEmail)

                // 2. CHECK ONBOARDING STAGE IMMEDIATELY (Priority #1)
                val onboardingStage = SessionManager.getSavedOnboardingStage(getApplication())
                val pendingOtpPair = SessionManager.getPendingOtp(getApplication())
                val isOtpPending = onboardingStage == SessionManager.STAGE_OTP_PENDING ||
                    (pendingOtpPair != null && (pendingOtpPair.first.equals(userEmail, ignoreCase = true) || pendingOtpPair.first.equals(mail, ignoreCase = true)))

                Log.d(TAG_AUTH, "Operation: CHECK_ONBOARDING_STAGE, stage: $onboardingStage, isOtpPending: $isOtpPending")

                if (isOtpPending) {
                    Log.d(TAG_OTP, "Operation: RESTORE_PENDING_OTP, UID: $uid, email: $userEmail, navigating to OTP")
                    SessionManager.saveOnboardingStage(getApplication(), SessionManager.STAGE_OTP_PENDING)
                    SessionManager.saveOnboardingCompleted(getApplication(), false)

                    if (pendingOtpPair != null) {
                        secretOtp = pendingOtpPair.second
                        signUpEmail.value = pendingOtpPair.first
                        otpError.value = null
                        Log.d("PlenxoAuthFlow", "OTP_STATE_RESTORED: Pending OTP restored from SessionManager for ${pendingOtpPair.first}")
                        startOtpTimer()
                    } else {
                        secretOtp = ""
                        signUpEmail.value = userEmail
                        otpError.value = "Your verification code is no longer available. Tap Resend Code."
                        secondsRemaining.value = 0
                        isTimerRunning.value = false
                        Log.w("PlenxoAuthFlow", "OTP_STATE_RESTORED: Pending OTP missing/expired; prompted resend")
                    }

                    otpInput.value = ""
                    otpSuccess.value = false

                    withContext(Dispatchers.Main) {
                        isLoginLoading.value = false
                        requiresOtp.value = true
                        onNavigateToOtp?.invoke()
                        Log.d("PlenxoAuthFlow", "OTP_SCREEN_NAVIGATION dispatched")
                    }
                    return@launch
                }

                // 3. Fetch user document from Firestore (Server-first with cache fallback & email query fallback)
                val readResult = try {
                    withTimeoutOrNull(5000L) {
                        com.example.model.fetchUserDocumentSafely(uid, firestore, emailFallback = userEmail)
                    }
                } catch (e: Exception) {
                    Log.w("PlenxoAuthFlow", "Resilient document fetch error for $uid: ${e.message}")
                    null
                }

                val doc = readResult?.snapshot

                val parsedUserModel = try {
                    doc?.toObject(com.example.model.UserModel::class.java)
                } catch (_: Exception) {
                    null
                }

                val rawPxId = parsedUserModel?.plenxoId?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("plenxoId") 
                    ?: doc?.getString("userCode")
                    ?: ""
                val cleanPxId = rawPxId.trim().removePrefix("@").removePrefix("#")
                val formattedPxId = when {
                    cleanPxId.startsWith("PX-", ignoreCase = true) -> "PX-${cleanPxId.removePrefix("PX-").removePrefix("px-")}"
                    cleanPxId.isNotBlank() -> "PX-$cleanPxId"
                    else -> ""
                }

                // Prioritize Firestore user document FIRST
                val storedName = parsedUserModel?.displayName?.takeIf { it.isNotBlank() && it != "User" }
                    ?: doc?.getString("displayName")?.takeIf { it.isNotBlank() && it != "User" }
                    ?: doc?.getString("display_name")?.takeIf { it.isNotBlank() && it != "User" }
                    ?: doc?.getString("name")?.takeIf { it.isNotBlank() && it != "User" }
                    ?: doc?.getString("current_name")?.takeIf { it.isNotBlank() && it != "User" }
                    ?: doc?.getString("fullName")?.takeIf { it.isNotBlank() && it != "User" }
                    ?: doc?.getString("full_name")?.takeIf { it.isNotBlank() && it != "User" }
                    ?: doc?.getString("username")?.takeIf { it.isNotBlank() && it != "User" }
                    ?: ""
                val storedBio = parsedUserModel?.bio?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("bio")?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("statusMessage")?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("bioStatus")?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("bio_status")?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("status_message")?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("current_bio")?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("about")?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("status")?.takeIf { it.isNotBlank() }
                    ?: ""
                val storedPic = parsedUserModel?.profilePicUrl?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("avatarUrl")?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("avatar_url")?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("profilePicUrl")?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("profilePic")?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("photoUrl")?.takeIf { it.isNotBlank() }
                    ?: doc?.getString("profileUrl")?.takeIf { it.isNotBlank() }
                    ?: ""
                val storedDob = doc?.getString("dob") 
                    ?: doc?.getString("dateOfBirth") 
                    ?: doc?.getString("date_of_birth") 
                    ?: doc?.get("dobMillis")?.toString() 
                    ?: ""
                val storedGender = doc?.getString("gender") 
                    ?: ""
                val storedAge = doc?.get("age")?.toString() ?: ""

                val isProfileCompletedInDoc = doc?.getBoolean("isProfileCompleted") == true 
                    || doc?.getBoolean("is_profile_completed") == true 
                    || doc?.getBoolean("isProfileSetupCompleted") == true 
                    || doc?.getBoolean("profileSetupCompleted") == true
                    || (storedName.isNotBlank() && storedName != "User")

                val localProfile = SessionManager.getUserProfileLocally(getApplication())

                val deterministicCode = (kotlin.math.abs(uid.hashCode()) % 900000 + 100000).toString()
                val fallbackPxId = "PX-$deterministicCode"

                val finalPlenxoId = formattedPxId.ifBlank {
                    val localPx = localProfile.plenxoId.trim().removePrefix("@").removePrefix("#")
                    if (localPx.startsWith("PX-", ignoreCase = true)) localPx else if (localPx.isNotBlank()) "PX-$localPx" else fallbackPxId
                }
                // STRICT RULE: Only fallback to email prefix IF AND ONLY IF Firestore document does not exist or displayName is completely blank
                val finalName = storedName.ifBlank {
                    localProfile.displayName.takeIf { it.isNotBlank() && it != "User" }
                        ?: if (userEmail.contains("@")) userEmail.substringBefore("@") else "User"
                }
                val finalBio = storedBio.ifBlank { localProfile.bio }
                val finalPic = storedPic.ifBlank { localProfile.profilePicUrl }
                val finalDob = storedDob.ifBlank { localProfile.dob }
                val finalGender = storedGender.ifBlank { localProfile.gender }

                SessionManager.saveUserProfileLocally(
                    getApplication(),
                    plenxoId = finalPlenxoId,
                    displayName = finalName,
                    bio = finalBio,
                    profilePicUrl = finalPic,
                    dob = finalDob,
                    gender = finalGender,
                    age = storedAge
                )
                SessionManager.saveLoginState(getApplication(), uid, userEmail)
                if (isProfileCompletedInDoc) {
                    SessionManager.saveOnboardingStage(getApplication(), SessionManager.STAGE_COMPLETED)
                    SessionManager.saveOnboardingCompleted(getApplication(), true)
                } else {
                    SessionManager.saveOnboardingStage(getApplication(), SessionManager.STAGE_PROFILE_SETUP_PENDING)
                    SessionManager.saveOnboardingCompleted(getApplication(), false)
                }

                // If Firestore document is missing or unpopulated, repair user profile in Firestore immediately
                if (doc == null || !doc.exists()) {
                    Log.i("PlenxoFirestoreBootstrap", "Auto-healing missing user document for $uid on login")
                    withContext(Dispatchers.IO) {
                        try {
                            FirestoreUserBootstrapper.initializeUser(
                                uid = uid,
                                email = userEmail,
                                name = finalName.takeIf { it != "User" },
                                plenxoId = finalPlenxoId.takeIf { it.startsWith("PX-") }
                            )
                        } catch (e: Exception) {
                            Log.w("PlenxoFirestoreBootstrap", "Auto-heal exception during login: ${e.message}")
                        }
                    }
                }

                this@AuthViewModel.plenxoId.value = finalPlenxoId
                this@AuthViewModel.name.value = finalName
                this@AuthViewModel.bio.value = finalBio
                this@AuthViewModel.profilePicUrl.value = finalPic
                if (finalDob.isNotBlank()) this@AuthViewModel.dob.value = finalDob
                if (finalGender.isNotBlank()) this@AuthViewModel.gender.value = finalGender

                val storedRing = doc?.getString("profileRingId") 
                    ?: doc?.getString("selectedRingId") 
                    ?: "none"

                val domainModel = UserProfile(
                    uid = uid,
                    id = uid,
                    email = userEmail,
                    displayName = finalName,
                    name = finalName,
                    bio = finalBio,
                    statusMessage = finalBio,
                    profilePicUrl = finalPic,
                    plenxoId = finalPlenxoId,
                    userCode = finalPlenxoId.removePrefix("PX-"),
                    profileRingId = storedRing,
                    isProfileCompleted = isProfileCompletedInDoc
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Authentication successful.", Toast.LENGTH_SHORT).show()
                    loginSuccess.value = true
                    isLoginLoading.value = false
                    onSuccess(domainModel)
                }

            } catch (e: FirebaseAuthInvalidCredentialsException) {
                Log.e("PlenxoAuthFlow", "AUTH_LOGIN_FAILURE: Invalid credentials [${e.errorCode}]: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    loginError.value = "Invalid email or password. Please check your credentials."
                    generateLoginCaptcha()
                }
            } catch (e: FirebaseAuthInvalidUserException) {
                Log.e("PlenxoAuthFlow", "AUTH_LOGIN_FAILURE: Invalid user [${e.errorCode}]: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    loginError.value = "No account found with this email address. Please sign up first."
                    generateLoginCaptcha()
                }
            } catch (e: FirebaseNetworkException) {
                Log.e("PlenxoAuthFlow", "AUTH_LOGIN_FAILURE: Network error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    loginError.value = "Network error. Please check your internet connection."
                    generateLoginCaptcha()
                }
            } catch (e: Exception) {
                Log.e("PlenxoAuthFlow", "AUTH_LOGIN_FAILURE: Unexpected error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val rawMsg = e.localizedMessage ?: "Invalid credentials"
                    loginError.value = when {
                        rawMsg.contains("user-not-found", ignoreCase = true) || rawMsg.contains("no user record", ignoreCase = true) ->
                            "No account found with this email address. Please sign up first."
                        rawMsg.contains("wrong-password", ignoreCase = true) || rawMsg.contains("invalid-credential", ignoreCase = true) ->
                            "Invalid email or password. Please check your credentials."
                        else -> rawMsg
                    }
                    generateLoginCaptcha()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoginLoading.value = false
                }
            }
        }
    }

    // Reset Auth States on Logout or fresh entry
    fun resetAuthState() {
        signUpEmail.value = ""
        signUpPassword.value = ""
        confirmPassword.value = ""
        signUpCaptchaInput.value = ""
        isTermsAccepted.value = false
        signUpSuccess.value = false
        signUpError.value = null
        
        loginEmail.value = ""
        loginPassword.value = ""
        loginCaptchaInput.value = ""
        isLoginTermsAccepted.value = false
        loginSuccess.value = false
        loginError.value = null

        otpInput.value = ""
        activeOtp.value = ""
        otpSuccess.value = false
        otpError.value = null
        requiresOtp.value = false
        timerJob?.cancel()
        isTimerRunning.value = false

        profilePicUrl.value = ""
        name.value = ""
        bio.value = ""
        dob.value = ""
        calculatedAge.value = ""
        gender.value = "Male"
        profileSetupSuccess.value = false
        profileSetupError.value = null

        plenxoId.value = ""
        plenxoIdRevealSuccess.value = false

        generateSignUpCaptcha()
        generateLoginCaptcha()
    }
}
