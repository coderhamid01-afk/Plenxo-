# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# 1. Protect App Data Models used for Firestore, Moshi, and serialization
-keep class com.example.model.** { *; }
-keepclassmembers class com.example.model.** { *; }
-keep class com.example.network.SendOtpRequest { *; }
-keep class com.example.network.SendOtpResponse { *; }
-keep class com.example.network.VerifyOtpRequest { *; }
-keep class com.example.network.VerifyOtpResponse { *; }
-keepclassmembers class ** { @com.google.gson.annotations.SerializedName <fields>; }
-keepclassmembers class ** { @com.squareup.moshi.Json <fields>; }
-keepclassmembers class ** { @com.squareup.moshi.JsonClass <fields>; }

# 2. Protect SQLCipher & Room
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# 3. Protect Firebase & Firestore (To prevent runtime crashes during DB read/write)
-keep class com.google.firebase.** { *; }
-keepclassmembers class * { *** get*(); void set*(***); }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 4. Protect Retrofit & OkHttp (Crucial for Brevo OTP & Catbox APIs)
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keepattributes Exceptions

# 5. Protect Android Lifecycle & ViewModels
-keep class androidx.lifecycle.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable

# 6. Kotlin Coroutines & Serialization Support
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-dontwarn kotlinx.serialization.**

# 7. WebRTC SDK Classes
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# 8. Android KeyStore & Cryptographic Helper Models
-keep class com.example.util.EncryptionManager { *; }
-keep class com.example.util.SecurityManager { *; }

# 9. Defensive logging strip for release builds (Strips Log.d, Log.v and System.out.println)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

-assumenosideeffects class java.io.PrintStream {
    public static *** println(...);
}
