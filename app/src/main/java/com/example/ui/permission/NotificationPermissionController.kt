package com.example.ui.permission

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.R
import com.example.ui.theme.ForestPine

/**
 * Controller responsible for orchestrating the notification permission lifecycle for journey reminders.
 *
 * It decouples permission checks and educational rationales from the WorkManager scheduling logic,
 * ensuring reminders cannot appear enabled without valid OS-level notification privileges.
 */
class NotificationPermissionController(
    private val context: Context,
    private var launcher: ((String) -> Unit)? = null
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var hasRequestedBefore: Boolean
        get() = prefs.getBoolean(KEY_HAS_REQUESTED_BEFORE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_HAS_REQUESTED_BEFORE, value).apply()
        }

    var showExplanationDialog by mutableStateOf(false)
        private set

    var showBlockedDialog by mutableStateOf(false)
        private set

    var feedbackMessage by mutableStateOf<String?>(null)
        private set

    private var pendingOnGranted: (() -> Unit)? = null

    fun setLauncher(newLauncher: ((String) -> Unit)?) {
        this.launcher = newLauncher
    }

    /**
     * Entry point when user attempts to toggle or enable a reminder.
     *
     * @param onGranted Callback executed only if and when notification capability is confirmed.
     */
    fun requestPermission(onGranted: () -> Unit) {
        val status = NotificationPermissionPolicy.evaluate(
            context = context,
            hasRequestedBefore = hasRequestedBefore
        )

        when (status) {
            is NotificationPermissionStatus.Granted -> {
                showExplanationDialog = false
                showBlockedDialog = false
                feedbackMessage = null
                onGranted()
            }
            is NotificationPermissionStatus.Requestable -> {
                pendingOnGranted = onGranted
                showExplanationDialog = true
                showBlockedDialog = false
                feedbackMessage = null
            }
            is NotificationPermissionStatus.Blocked -> {
                pendingOnGranted = null
                showExplanationDialog = false
                showBlockedDialog = true
                feedbackMessage = context.getString(R.string.notification_permission_denied)
            }
        }
    }

    /**
     * User chose to proceed from the educational explanation dialog.
     */
    fun onContinueExplanation() {
        showExplanationDialog = false
        hasRequestedBefore = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher?.invoke(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Below API 33, runtime request is not applicable.
            // Check system-level notifications directly.
            if (NotificationPermissionPolicy.isNotificationPermissionGranted(context)) {
                val action = pendingOnGranted
                pendingOnGranted = null
                action?.invoke()
            } else {
                pendingOnGranted = null
                showBlockedDialog = true
                feedbackMessage = context.getString(R.string.notification_permission_denied)
            }
        }
    }

    /**
     * User dismissed the educational explanation dialog ("Not now").
     */
    fun onDismissExplanation() {
        showExplanationDialog = false
        pendingOnGranted = null
    }

    /**
     * Handles the result of the system POST_NOTIFICATIONS permission request.
     */
    fun onPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            if (NotificationPermissionPolicy.isNotificationPermissionGranted(context)) {
                val action = pendingOnGranted
                pendingOnGranted = null
                feedbackMessage = null
                action?.invoke()
            } else {
                pendingOnGranted = null
                showBlockedDialog = true
                feedbackMessage = context.getString(R.string.notification_permission_denied)
            }
        } else {
            pendingOnGranted = null
            feedbackMessage = context.getString(R.string.notification_permission_denied)
        }
    }

    /**
     * Navigates the user to the app's notification settings screen.
     */
    fun onOpenSettings() {
        showBlockedDialog = false
        NotificationSettingsLauncher.openAppNotificationSettings(context)
    }

    /**
     * User dismissed the blocked settings recovery dialog.
     */
    fun onDismissBlocked() {
        showBlockedDialog = false
    }

    fun clearFeedbackMessage() {
        feedbackMessage = null
    }

    companion object {
        private const val PREFS_NAME = "notification_permission_prefs"
        private const val KEY_HAS_REQUESTED_BEFORE = "key_has_requested_before"
    }
}

/**
 * Creates and remembers a [NotificationPermissionController] tied to the Composable lifecycle.
 */
@Composable
fun rememberNotificationPermissionController(): NotificationPermissionController {
    val context = LocalContext.current
    var controllerRef by remember { mutableStateOf<NotificationPermissionController?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            controllerRef?.onPermissionResult(isGranted)
        }
    )

    val controller = remember(context) {
        NotificationPermissionController(
            context = context,
            launcher = { perm -> launcher.launch(perm) }
        ).also { controllerRef = it }
    }

    DisposableEffect(launcher) {
        controller.setLauncher { perm -> launcher.launch(perm) }
        controllerRef = controller
        onDispose { }
    }

    return controller
}

/**
 * Dialog host that renders contextual permission explanation or settings recovery dialogs.
 */
@Composable
fun NotificationPermissionDialogHost(
    controller: NotificationPermissionController,
    modifier: Modifier = Modifier
) {
    if (controller.showExplanationDialog) {
        AlertDialog(
            onDismissRequest = { controller.onDismissExplanation() },
            title = {
                Text(
                    text = stringResource(R.string.notification_permission_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.notification_permission_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { controller.onContinueExplanation() },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPine),
                    modifier = Modifier.testTag("notification_permission_continue_button")
                ) {
                    Text(stringResource(R.string.notification_permission_continue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { controller.onDismissExplanation() },
                    modifier = Modifier.testTag("notification_permission_not_now_button")
                ) {
                    Text(stringResource(R.string.notification_permission_not_now))
                }
            },
            modifier = modifier.testTag("notification_permission_explanation_dialog")
        )
    }

    if (controller.showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { controller.onDismissBlocked() },
            title = {
                Text(
                    text = stringResource(R.string.notification_permission_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.notification_permission_denied),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { controller.onOpenSettings() },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestPine),
                    modifier = Modifier.testTag("notification_permission_open_settings_button")
                ) {
                    Text(stringResource(R.string.notification_permission_open_settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { controller.onDismissBlocked() },
                    modifier = Modifier.testTag("notification_permission_blocked_dismiss_button")
                ) {
                    Text(stringResource(R.string.notification_permission_not_now))
                }
            },
            modifier = modifier.testTag("notification_permission_blocked_dialog")
        )
    }
}
