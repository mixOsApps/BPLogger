# ============================================================
# ProGuard / R8 rules for BPLogger App
# ============================================================

# ── AndroidX Room ──────────────────────────────────────────
# Keep data classes, DAOs, and the Database class
-keep class com.bplogger.app.data.db.** { *; }

# ── Kotlin Coroutines ──────────────────────────────────────
# Keeps the Coroutine's Main dispatcher available for an optimized build.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}

# ── Google ML Kit Text Recognition ─────────────────────────
-keep class com.google.mlkit.vision.text.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text.** { *; }

# ── AndroidX Navigation Component ──────────────────────────
-keep class androidx.navigation.** { *; }
-keep class * extends androidx.navigation.Navigator

# ── AndroidX WorkManager ───────────────────────────────────
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── OkHttp ─────────────────────────────────────────────────
# OkHttp includes its own Proguard rules. These are just to suppress warnings.
-dontwarn okhttp3.**
-dontwarn okio.**

# ── App-specific components ────────────────────────────────
# Keep services and receivers that are referenced in the manifest
-keep class com.bplogger.app.receiver.** { *; }

# ── General rules ──────────────────────────────────────────
# Keep enums (used in Room, etc.)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Suppress common harmless warnings ─────────────────────
-dontwarn javax.xml.**
-dontwarn java.awt.**
-dontwarn sun.misc.**
-dontwarn com.sun.**
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# ── Google API Client & Apache HttpClient ──────────────────
# Suppress warnings for classes missing on Android
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**

# Keep Google API Client and Sheets API classes
-keep class com.google.api.services.sheets.** { *; }
-keep class com.google.api.client.** { *; }
