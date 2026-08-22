package com.example.ui.about

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.BuildConfig

data class AppVersionInfo(
    val versionName: String,
    val versionCode: Long
)

object AppInfoConfig {
    const val APP_NAME = "Travel Stamp"
    const val TAGLINE = "Your Journey, Your Memories, Your Collection."
    const val DESCRIPTION = "Travel Stamp is your personal digital travel passport for recording journeys, preserving memories, and building your travel collection."
    const val COPYRIGHT = "© 2026 Travel Stamp\nAll rights reserved."
    const val SUPPORT_EMAIL = "support@travelstamp.app"
    const val PRIVACY_POLICY_URL = "https://travelstamp.app/privacy"

    fun getAppVersionInfo(context: Context): AppVersionInfo {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }

            val vName = packageInfo.versionName ?: BuildConfig.VERSION_NAME
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            AppVersionInfo(
                versionName = if (vName.isNullOrBlank()) "1.0" else vName,
                versionCode = if (vCode <= 0L) 1L else vCode
            )
        } catch (e: Exception) {
            AppVersionInfo(
                versionName = BuildConfig.VERSION_NAME.ifBlank { "1.0" },
                versionCode = if (BuildConfig.VERSION_CODE > 0) BuildConfig.VERSION_CODE.toLong() else 1L
            )
        }
    }
}

data class OpenSourceLibrary(
    val name: String,
    val developer: String,
    val licenseName: String,
    val description: String
)

object OpenSourceLicensesCatalog {
    val libraries = listOf(
        OpenSourceLibrary(
            name = "AndroidX & Jetpack Compose",
            developer = "Google LLC & The Android Open Source Project",
            licenseName = "Apache License 2.0",
            description = "Modern toolkit for native Android UI development, Material Design 3, lifecycle management, and navigation."
        ),
        OpenSourceLibrary(
            name = "Room Persistence Library",
            developer = "Google LLC & The Android Open Source Project",
            licenseName = "Apache License 2.0",
            description = "Robust SQLite object mapping for local offline passport database persistence."
        ),
        OpenSourceLibrary(
            name = "WorkManager",
            developer = "Google LLC & The Android Open Source Project",
            licenseName = "Apache License 2.0",
            description = "Reliable background scheduling for offline pre-trip reminders and notifications."
        ),
        OpenSourceLibrary(
            name = "Kotlin & Coroutines",
            developer = "JetBrains s.r.o.",
            licenseName = "Apache License 2.0",
            description = "Pragmatic, expressive programming language and asynchronous concurrency library for Android."
        ),
        OpenSourceLibrary(
            name = "Coil Image Loader",
            developer = "Coinverse & Coil Contributors",
            licenseName = "Apache License 2.0",
            description = "Fast, lightweight image loading for Android backed by Kotlin Coroutines."
        ),
        OpenSourceLibrary(
            name = "Moshi & OkHttp & Retrofit",
            developer = "Square, Inc.",
            licenseName = "Apache License 2.0",
            description = "Modern JSON parsing, type conversion, and network transport libraries."
        ),
        OpenSourceLibrary(
            name = "Roborazzi & Robolectric",
            developer = "Roborazzi & Robolectric Contributors",
            licenseName = "MIT License & Apache License 2.0",
            description = "Local JVM testing frameworks for visual regression and Android unit tests."
        )
    )
}
