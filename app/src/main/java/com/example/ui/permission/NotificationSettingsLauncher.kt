package com.example.ui.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Safe navigation utility to open notification settings or application details.
 */
object NotificationSettingsLauncher {

    /**
     * Builds the primary Intent targeting the app's notification settings screen.
     */
    fun createNotificationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            createApplicationDetailsIntent(context)
        }
    }

    /**
     * Builds the fallback Intent targeting the app's application details screen.
     */
    fun createApplicationDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }

    /**
     * Attempts to open the OS notification settings for this application.
     * Falls back to general application details settings if notification settings cannot be launched.
     *
     * @return true if an activity was successfully started, false otherwise.
     */
    fun openAppNotificationSettings(context: Context): Boolean {
        val primaryIntent = createNotificationSettingsIntent(context)
        if (context !is Activity) {
            primaryIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(primaryIntent)
            true
        } catch (_: Exception) {
            try {
                val fallbackIntent = createApplicationDetailsIntent(context)
                if (context !is Activity) {
                    fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
