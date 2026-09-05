package com.example.data.notification

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderNavigationRequestTest {

    @Test
    fun `test 01 - valid reminder intent parses tripId correctly`() {
        val intent = Intent().apply {
            putExtra(TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER, true)
            putExtra(TripNotificationHelper.EXTRA_TRIP_ID, 42L)
        }

        val request = ReminderNavigationRequest.fromIntent(intent)
        assertNotNull(request)
        assertEquals(42L, request!!.tripId)
    }

    @Test
    fun `test 02 - missing reminder marker returns null`() {
        val intent = Intent().apply {
            putExtra(TripNotificationHelper.EXTRA_TRIP_ID, 42L)
        }

        val request = ReminderNavigationRequest.fromIntent(intent)
        assertNull(request)
    }

    @Test
    fun `test 03 - false reminder marker returns null`() {
        val intent = Intent().apply {
            putExtra(TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER, false)
            putExtra(TripNotificationHelper.EXTRA_TRIP_ID, 42L)
        }

        val request = ReminderNavigationRequest.fromIntent(intent)
        assertNull(request)
    }

    @Test
    fun `test 04 - missing tripId extra returns null`() {
        val intent = Intent().apply {
            putExtra(TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER, true)
        }

        val request = ReminderNavigationRequest.fromIntent(intent)
        assertNull(request)
    }

    @Test
    fun `test 05 - tripId zero returns null`() {
        val intent = Intent().apply {
            putExtra(TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER, true)
            putExtra(TripNotificationHelper.EXTRA_TRIP_ID, 0L)
        }

        val request = ReminderNavigationRequest.fromIntent(intent)
        assertNull(request)
    }

    @Test
    fun `test 06 - negative tripId returns null`() {
        val intent = Intent().apply {
            putExtra(TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER, true)
            putExtra(TripNotificationHelper.EXTRA_TRIP_ID, -10L)
        }

        val request = ReminderNavigationRequest.fromIntent(intent)
        assertNull(request)
    }

    @Test
    fun `test 07 - unrelated intent returns null`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            putExtra("unrelated_key", "some_data")
        }

        val request = ReminderNavigationRequest.fromIntent(intent)
        assertNull(request)
    }

    @Test
    fun `test 08 - null intent returns null`() {
        val request = ReminderNavigationRequest.fromIntent(null)
        assertNull(request)
    }

    @Test
    fun `test 09 - normal launcher start intent returns null`() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val request = ReminderNavigationRequest.fromIntent(intent)
        assertNull(request)
    }
}
