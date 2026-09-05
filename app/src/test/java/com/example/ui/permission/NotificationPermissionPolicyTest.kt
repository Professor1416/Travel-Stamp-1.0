package com.example.ui.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationPermissionPolicyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `TEST 1 - API below 33 does not require POST_NOTIFICATIONS runtime request`() {
        val status = NotificationPermissionPolicy.evaluateStatus(
            sdkInt = Build.VERSION_CODES.S_V2, // API 32
            notificationsEnabled = true,
            postNotificationsGranted = false, // Even if false, below 33 it is not required
            shouldShowRationale = false,
            hasRequestedBefore = false
        )
        assertEquals(NotificationPermissionStatus.Granted, status)
    }

    @Test
    fun `TEST 2 - API 33+ with runtime permission granted yields status Granted`() {
        val status = NotificationPermissionPolicy.evaluateStatus(
            sdkInt = Build.VERSION_CODES.TIRAMISU, // API 33
            notificationsEnabled = true,
            postNotificationsGranted = true,
            shouldShowRationale = false,
            hasRequestedBefore = false
        )
        assertEquals(NotificationPermissionStatus.Granted, status)
    }

    @Test
    fun `TEST 3 - API 33+ denied runtime permission is not Granted`() {
        val status = NotificationPermissionPolicy.evaluateStatus(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            notificationsEnabled = true,
            postNotificationsGranted = false,
            shouldShowRationale = true,
            hasRequestedBefore = true
        )
        assertEquals(NotificationPermissionStatus.Requestable, status)
    }

    @Test
    fun `TEST 4 - Android notifications disabled at system level yields Blocked status`() {
        val status = NotificationPermissionPolicy.evaluateStatus(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            notificationsEnabled = false,
            postNotificationsGranted = true,
            shouldShowRationale = false,
            hasRequestedBefore = false
        )
        assertEquals(NotificationPermissionStatus.Blocked, status)

        // Even below API 33, if notifications are disabled at system level, it is Blocked
        val statusApi30 = NotificationPermissionPolicy.evaluateStatus(
            sdkInt = Build.VERSION_CODES.R,
            notificationsEnabled = false,
            postNotificationsGranted = false,
            shouldShowRationale = false,
            hasRequestedBefore = false
        )
        assertEquals(NotificationPermissionStatus.Blocked, statusApi30)
    }

    @Test
    fun `TEST 12 - Open Settings intent contains current package`() {
        val intent = NotificationSettingsLauncher.createNotificationSettingsIntent(context)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))

        val fallbackIntent = NotificationSettingsLauncher.createApplicationDetailsIntent(context)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, fallbackIntent.action)
        assertEquals(Uri.fromParts("package", context.packageName, null), fallbackIntent.data)
    }

    @Test
    fun `TEST 14 - API below 33 with system notifications enabled evaluates to Granted without permission request`() {
        val status = NotificationPermissionPolicy.evaluateStatus(
            sdkInt = 30,
            notificationsEnabled = true,
            postNotificationsGranted = false,
            shouldShowRationale = false,
            hasRequestedBefore = false
        )
        assertEquals(NotificationPermissionStatus.Granted, status)
    }

    @Test
    fun `TEST 15 - API 34 with permission already granted evaluates to Granted`() {
        val status = NotificationPermissionPolicy.evaluateStatus(
            sdkInt = 34,
            notificationsEnabled = true,
            postNotificationsGranted = true,
            shouldShowRationale = false,
            hasRequestedBefore = true
        )
        assertEquals(NotificationPermissionStatus.Granted, status)
    }

    @Test
    fun `TEST Permanently denied (has requested before, rationale false) evaluates to Blocked`() {
        val status = NotificationPermissionPolicy.evaluateStatus(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            notificationsEnabled = true,
            postNotificationsGranted = false,
            shouldShowRationale = false,
            hasRequestedBefore = true
        )
        assertEquals(NotificationPermissionStatus.Blocked, status)
    }
}
