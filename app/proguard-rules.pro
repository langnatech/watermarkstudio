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

# WorkManager (AdMob also pulls work-runtime). Startup is disabled, but keep
# its Room database classes in case a dependency lazily initializes it later.
-keep class androidx.work.** { *; }
-keep class androidx.work.impl.** { *; }
-keep class androidx.work.impl.WorkDatabase { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.impl.model.** { *; }
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase {
    *;
    <init>(...);
}
-keep @androidx.room.Entity class *
-dontwarn androidx.work.**
-dontwarn androidx.room.**

# Media3 / ExoPlayer / Transformer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# FFmpeg-kit (LGPL — see README phase 3c)
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

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

# Application Models & ViewModels
-keep class com.watermarkstudio.model.** { *; }
-keep class com.watermarkstudio.viewmodel.** { *; }
-keep class com.watermarkstudio.billing.** { *; }
-keep class com.watermarkstudio.removal.** { *; }
-keep class com.watermarkstudio.removal.native.RemovalNative { *; }

# OpenCV JNI — R8 must not strip or optimize native bindings (release SIGSEGV)
-keep class org.opencv.** { *; }
-keepclassmembers class org.opencv.** {
    native <methods>;
}
-dontwarn org.opencv.**

# All JNI entry points (removal_native + OpenCV)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Navigation & Activity (keep class names for deep links / navigation)
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
