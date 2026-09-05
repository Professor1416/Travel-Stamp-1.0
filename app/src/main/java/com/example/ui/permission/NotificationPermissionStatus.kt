package com.example.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Represents the permission status for posting journey reminder notifications.
 */
sealed interface NotificationPermissionStatus {
    /** Notifications are fully enabled and runtime permission (if required) is granted. */
    data object Granted : NotificationPermissionStatus

    /**
     * Runtime permission has not yet been granted, but can be requested from the user.
     * An educational rationale explanation should be presented before launching the OS dialog.
     */
    data object Requestable : NotificationPermissionStatus

    /**
     * Notifications cannot be enabled directly through a runtime dialog (e.g. permanently denied,
     * or system-level notifications disabled for the entire app). Requires "Open Settings" recovery.
     */
    data object Blocked : NotificationPermissionStatus
}

/**
 * Pure, testable decision engine for notification permission states.
 */
object NotificationPermissionPolicy {

    /**
     * Evaluates permission status based on OS capability, app-level settings,
     * runtime permission status, and prior user interaction signals.
     */
    fun evaluateStatus(
        sdkInt: Int,
        notificationsEnabled: Boolean,
        postNotificationsGranted: Boolean,
        shouldShowRationale: Boolean,
        hasRequestedBefore: Boolean
    ): NotificationPermissionStatus {
        // If notifications are disabled at the system level (e.g. app toggle in OS settings),
        // notifications cannot be shown regardless of runtime permission.
        if (!notificationsEnabled) {
            return NotificationPermissionStatus.Blocked
        }

        // On Android 12 and below (API < 33), POST_NOTIFICATIONS runtime permission does not exist.
        // Notifications are available if system notifications are enabled.
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
            return NotificationPermissionStatus.Granted
        }

        // On Android 13+ (API 33+), runtime permission is required.
        if (postNotificationsGranted) {
            return NotificationPermissionStatus.Granted
        }

        // POST_NOTIFICATIONS is not granted.
        // If user already requested before and shouldShowRationale is false,
        // it indicates user chose "Don't ask again" / permanently denied by OS.
        if (hasRequestedBefore && !shouldShowRationale) {
            return NotificationPermissionStatus.Blocked
        }

        return NotificationPermissionStatus.Requestable
    }

    /**
     * Evaluates permission status for a live Context.
     */
    fun evaluate(
        context: Context,
        hasRequestedBefore: Boolean = false,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): NotificationPermissionStatus {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        val postNotificationsGranted = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val activity = findActivity(context)
        val shouldShowRationale = if (activity != null && sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            false
        }

        return evaluateStatus(
            sdkInt = sdkInt,
            notificationsEnabled = notificationsEnabled,
            postNotificationsGranted = postNotificationsGranted,
            shouldShowRationale = shouldShowRationale,
            hasRequestedBefore = hasRequestedBefore
        )
    }

    /**
     * Quick check if notifications are fully operational.
     */
    fun isNotificationPermissionGranted(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
