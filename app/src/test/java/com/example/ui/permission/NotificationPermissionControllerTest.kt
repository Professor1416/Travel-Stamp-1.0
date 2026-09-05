package com.example.ui.permission

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationPermissionControllerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Ensure clean SharedPreferences for each test
        context.getSharedPreferences("notification_permission_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `TEST 5 - User requests reminder ON while granted allows enable action immediately`() {
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        var enableActionExecuted = false
        var launcherTriggered = false

        val controller = NotificationPermissionController(
            context = context,
            launcher = { launcherTriggered = true }
        )

        controller.requestPermission {
            enableActionExecuted = true
        }

        assertTrue("Enable action should execute immediately when permission is granted", enableActionExecuted)
        assertFalse("Launcher should not be triggered when permission is already granted", launcherTriggered)
        assertFalse("Explanation dialog should not be shown when permission is already granted", controller.showExplanationDialog)
        assertFalse("Blocked dialog should not be shown when permission is already granted", controller.showBlockedDialog)
    }

    @Test
    fun `TEST 6 - User requests reminder ON while permission absent shows explanation first`() {
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        var enableActionExecuted = false
        var launcherTriggered = false

        val controller = NotificationPermissionController(
            context = context,
            launcher = { launcherTriggered = true }
        )

        controller.requestPermission {
            enableActionExecuted = true
        }

        assertFalse("Enable action must NOT execute before permission is granted", enableActionExecuted)
        assertFalse("Launcher must NOT be triggered before user confirms explanation", launcherTriggered)
        assertTrue("Explanation dialog must be displayed first", controller.showExplanationDialog)
    }

    @Test
    fun `TEST 7 - Not now closes explanation dialog and leaves reminder state OFF`() {
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        var enableActionExecuted = false
        val controller = NotificationPermissionController(context = context)

        controller.requestPermission {
            enableActionExecuted = true
        }
        assertTrue(controller.showExplanationDialog)

        // User dismisses explanation dialog
        controller.onDismissExplanation()

        assertFalse("Explanation dialog should be dismissed", controller.showExplanationDialog)
        assertFalse("Enable action must remain unexecuted", enableActionExecuted)
    }

    @Test
    fun `TEST 8 - Continue invokes permission launcher request`() {
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        var launcherRequestedPermission: String? = null
        val controller = NotificationPermissionController(
            context = context,
            launcher = { perm -> launcherRequestedPermission = perm }
        )

        controller.requestPermission { }
        assertTrue(controller.showExplanationDialog)

        controller.onContinueExplanation()

        assertFalse("Explanation dialog should be closed after Continue", controller.showExplanationDialog)
        assertEquals(android.Manifest.permission.POST_NOTIFICATIONS, launcherRequestedPermission)
        assertTrue("hasRequestedBefore should be recorded", controller.hasRequestedBefore)
    }

    @Test
    fun `TEST 9 - Permission granted result executes requested reminder enable action`() {
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        var enableActionExecuted = false
        val controller = NotificationPermissionController(context = context)

        controller.requestPermission {
            enableActionExecuted = true
        }
        controller.onContinueExplanation()

        // System grants permission
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        controller.onPermissionResult(isGranted = true)

        assertTrue("Enable action must execute after permission is granted", enableActionExecuted)
        assertNull("Feedback message should be cleared on success", controller.feedbackMessage)
    }

    @Test
    fun `TEST 10 and 11 - Permission denied result leaves reminder OFF and does not schedule`() {
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        var enableActionExecuted = false
        val controller = NotificationPermissionController(context = context)

        controller.requestPermission {
            enableActionExecuted = true
        }
        controller.onContinueExplanation()

        // System denies permission
        controller.onPermissionResult(isGranted = false)

        assertFalse("Enable action must NEVER execute when permission is denied", enableActionExecuted)
        assertEquals(
            context.getString(R.string.notification_permission_denied),
            controller.feedbackMessage
        )
    }

    @Test
    fun `TEST 13 - Normal UI state does not auto-open system settings or dialogs`() {
        val controller = NotificationPermissionController(context = context)
        assertFalse(controller.showExplanationDialog)
        assertFalse(controller.showBlockedDialog)
        assertNull(controller.feedbackMessage)
    }

    @Test
    fun `TEST 19 - Denied feedback is provided when permission denied`() {
        val controller = NotificationPermissionController(context = context)
        controller.requestPermission { }
        controller.onContinueExplanation()

        controller.onPermissionResult(isGranted = false)

        assertEquals(
            context.getString(R.string.notification_permission_denied),
            controller.feedbackMessage
        )
    }
}
