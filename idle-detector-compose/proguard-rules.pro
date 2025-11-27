# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ==============================================================================
# Idle Detector Compose Library - ProGuard Rules
# ==============================================================================

# Keep public API - IdleDetectorProvider composable function
-keep public class ke.co.banit.idle_detector_compose.IdleDetectorProviderKt {
    public *;
}

# Keep IdleOrigin sealed interface and its implementations
-keep interface ke.co.banit.idle_detector_compose.IdleOrigin { *; }
-keep class ke.co.banit.idle_detector_compose.IdleOrigin$** { *; }

# Keep IdleDetectorConfig for configuration builder pattern
-keep public class ke.co.banit.idle_detector_compose.IdleDetectorConfig { *; }
-keep public class ke.co.banit.idle_detector_compose.IdleDetectorConfig$Builder { *; }

# Keep IdleDetectorLogger for public logging configuration
-keep public class ke.co.banit.idle_detector_compose.IdleDetectorLogger { *; }

# Keep BackgroundTimeoutWorker - Required by WorkManager
-keep class ke.co.banit.idle_detector_compose.BackgroundTimeoutWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep Composition Locals - Required for Compose integration
-keepclassmembers class ke.co.banit.idle_detector_compose.IdleDetectorProviderKt {
    public static ** getLocalIdleDetectorState();
    public static ** getLocalIdleReset();
}

# WorkManager rules - Prevent stripping of worker class
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Kotlin Coroutines - Preserve volatile fields for thread safety
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Kotlin Duration API - Used for timeout configuration
-keep class kotlin.time.Duration { *; }
-keep class kotlin.time.** { *; }

# Compose Runtime - Preserve state delegates
-keepclassmembers class androidx.compose.runtime.** { *; }

# Keep all public API that may be used by consumers
-keep public class ke.co.banit.idle_detector_compose.** {
    public protected *;
}

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable

# Keep generic signatures for proper type inference
-keepattributes Signature

# Keep annotations for runtime inspection
-keepattributes *Annotation*