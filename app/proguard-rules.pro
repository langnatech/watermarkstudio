# Watermark Studio - Production ProGuard Rules
# Keep line numbers for debugging stack traces in Crashlytics/Play Console
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes *Annotation*

# Google Play Billing Library
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.api.**

# Google Mobile Ads (AdMob)
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Media3 / ExoPlayer / Transformer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# Coil Image Loading
-keep class coil.** { *; }
-dontwarn coil.**

# Jetpack Compose & AndroidX Lifecycle
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# Application Models & ViewModels (referenced by name in Compose/StateFlow)
-keep class com.example.model.** { *; }
-keep class com.example.viewmodel.** { *; }

# Navigation & Activity (keep class names for deep links / navigation)
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
