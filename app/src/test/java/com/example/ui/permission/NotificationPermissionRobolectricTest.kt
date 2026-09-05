package com.example.ui.permission

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationPermissionRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("notification_permission_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `TEST 16, 17, 18 - Explanation dialog UI visible, Continue invokes launcher, Not now closes`() {
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        var launcherTriggered = false
        var reminderTurnedOn = false

        val controller = NotificationPermissionController(
            context = context,
            launcher = { launcherTriggered = true }
        )

        // Request permission before or after setContent
        controller.requestPermission {
            reminderTurnedOn = true
        }
        assertTrue(controller.showExplanationDialog)

        composeTestRule.setContent {
            MyApplicationTheme {
                NotificationPermissionDialogHost(controller = controller)
            }
        }

        // Verify title and message are visible
        composeTestRule.onNodeWithTag("notification_permission_explanation_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.notification_permission_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.notification_permission_message)).assertIsDisplayed()

        // Click Not now
        composeTestRule.onNodeWithTag("notification_permission_not_now_button").performClick()
        assertFalse(controller.showExplanationDialog)
        assertFalse(reminderTurnedOn)
        assertFalse(launcherTriggered)

        // Request again, then click Continue
        controller.requestPermission {
            reminderTurnedOn = true
        }
        composeTestRule.onNodeWithTag("notification_permission_continue_button").performClick()
        assertTrue("Launcher should be invoked when Continue is clicked", launcherTriggered)
        assertFalse("Explanation dialog should be dismissed", controller.showExplanationDialog)
        assertFalse("Reminder should not be turned on before permission result", reminderTurnedOn)
    }

    @Test
    fun `TEST 20 - Open Settings recovery action appears for blocked state`() {
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val controller = NotificationPermissionController(context = context)
        controller.hasRequestedBefore = true

        // Request permission while blocked (has requested before, rationale false in shadow)
        controller.requestPermission { }
        assertTrue(controller.showBlockedDialog)

        composeTestRule.setContent {
            MyApplicationTheme {
                NotificationPermissionDialogHost(controller = controller)
            }
        }

        composeTestRule.onNodeWithTag("notification_permission_blocked_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notification_permission_open_settings_button").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.notification_permission_open_settings)).assertIsDisplayed()

        // Click dismiss
        composeTestRule.onNodeWithTag("notification_permission_blocked_dismiss_button").performClick()
        assertFalse(controller.showBlockedDialog)
    }
}
