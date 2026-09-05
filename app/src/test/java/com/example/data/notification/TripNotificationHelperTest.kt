package com.example.data.notification

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.R
import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripNotificationHelperTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var shadowNotificationManager: ShadowNotificationManager

    private val sampleTrip = Trip(
        id = 42L,
        name = "Harishchandragad Trek",
        destination = "Ahmednagar, Maharashtra",
        date = "2026-11-20",
        startTimeMinutes = 360, // 6:00 AM
        reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = Shadows.shadowOf(notificationManager)

        // Grant notification permission for SDK 34 testing
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `test 01 - channel ID remains trip_reminders_channel`() {
        TripNotificationHelper.ensureNotificationChannel(context)
        val channel = notificationManager.getNotificationChannel(TripNotificationHelper.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals("trip_reminders_channel", channel.id)
    }

    @Test
    fun `test 02 - new channel importance is IMPORTANCE_DEFAULT and retains name`() {
        TripNotificationHelper.ensureNotificationChannel(context)
        val channel = notificationManager.getNotificationChannel(TripNotificationHelper.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals("Journey Reminders", channel.name)
    }

    @Test
    fun `test 03 - channel creation is idempotent and preserves channel if already exists`() {
        TripNotificationHelper.ensureNotificationChannel(context)
        val channelFirst = notificationManager.getNotificationChannel(TripNotificationHelper.CHANNEL_ID)
        assertNotNull(channelFirst)

        // Second call must not throw or overwrite
        TripNotificationHelper.ensureNotificationChannel(context)
        val channelSecond = notificationManager.getNotificationChannel(TripNotificationHelper.CHANNEL_ID)
        assertNotNull(channelSecond)
        assertEquals(channelFirst.id, channelSecond.id)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channelSecond.importance)
    }

    @Test
    fun `test 04 - notification uses custom monochrome small icon`() {
        TripNotificationHelper.showTripReminderNotification(context, sampleTrip)

        val posted = shadowNotificationManager.allNotifications.lastOrNull()
        assertNotNull("Expected notification to be posted", posted)
        assertEquals(R.drawable.ic_notification_travel_stamp, posted!!.smallIcon.resId)
    }

    @Test
    fun `test 05 - notification priority is PRIORITY_DEFAULT`() {
        TripNotificationHelper.showTripReminderNotification(context, sampleTrip)

        val posted = shadowNotificationManager.allNotifications.lastOrNull()
        assertNotNull(posted)
        assertEquals(NotificationCompat.PRIORITY_DEFAULT, posted!!.priority)
    }

    @Test
    fun `test 06 - notification title matches ReminderCopyProvider`() {
        val expectedCopy = ReminderCopyProvider.create(sampleTrip)
        TripNotificationHelper.showTripReminderNotification(context, sampleTrip)

        val posted = shadowNotificationManager.allNotifications.lastOrNull()
        assertNotNull(posted)
        val extras = NotificationCompat.getExtras(posted!!)
        assertNotNull(extras)
        assertEquals(expectedCopy.title, extras?.getString(NotificationCompat.EXTRA_TITLE))
    }

    @Test
    fun `test 07 - notification body matches ReminderCopyProvider`() {
        val expectedCopy = ReminderCopyProvider.create(sampleTrip)
        TripNotificationHelper.showTripReminderNotification(context, sampleTrip)

        val posted = shadowNotificationManager.allNotifications.lastOrNull()
        assertNotNull(posted)
        val extras = NotificationCompat.getExtras(posted!!)
        assertNotNull(extras)
        assertEquals(expectedCopy.body, extras?.getString(NotificationCompat.EXTRA_TEXT))
    }

    @Test
    fun `test 08 - BigTextStyle is applied with full body text`() {
        val expectedCopy = ReminderCopyProvider.create(sampleTrip)
        TripNotificationHelper.showTripReminderNotification(context, sampleTrip)

        val posted = shadowNotificationManager.allNotifications.lastOrNull()
        assertNotNull(posted)
        val extras = NotificationCompat.getExtras(posted!!)
        assertNotNull(extras)
        val bigText = extras?.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString()
        assertEquals(expectedCopy.body, bigText)
    }

    @Test
    fun `test 09 - autoCancel behavior remains enabled`() {
        TripNotificationHelper.showTripReminderNotification(context, sampleTrip)

        val posted = shadowNotificationManager.allNotifications.lastOrNull()
        assertNotNull(posted)
        assertTrue(
            "Expected FLAG_AUTO_CANCEL to be set",
            (posted!!.flags and Notification.FLAG_AUTO_CANCEL) != 0
        )
    }

    @Test
    fun `test 10 - existing PendingIntent is present with extra trip ID`() {
        TripNotificationHelper.showTripReminderNotification(context, sampleTrip)

        val posted = shadowNotificationManager.allNotifications.lastOrNull()
        assertNotNull(posted)
        val pendingIntent = posted!!.contentIntent
        assertNotNull("Expected contentIntent to be present", pendingIntent)

        val shadowPendingIntent = Shadows.shadowOf(pendingIntent)
        val savedIntent = shadowPendingIntent.savedIntent
        assertNotNull("Expected savedIntent in pendingIntent", savedIntent)
        assertEquals(sampleTrip.id, savedIntent.getLongExtra(TripNotificationHelper.EXTRA_TRIP_ID, -1L))
        assertEquals(MainActivity::class.java.name, savedIntent.component?.className)
    }

    @Test
    fun `test 11 - notification permission guard suppresses dispatch when revoked`() {
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        TripNotificationHelper.showTripReminderNotification(context, sampleTrip.copy(id = 999L))

        val notification = shadowNotificationManager.getNotification("trip_reminder_999", 999)
        assertNull("Notification must not be posted without POST_NOTIFICATIONS permission", notification)
    }

    @Test
    fun `test 12 - notification tag and id format is preserved`() {
        TripNotificationHelper.showTripReminderNotification(context, sampleTrip)

        val notification = shadowNotificationManager.getNotification("trip_reminder_42", 42)
        assertNotNull("Expected notification at tag trip_reminder_42 and id 42", notification)
    }

    @Test
    fun `test 13 - PendingIntent contains reminder-navigation marker`() {
        TripNotificationHelper.showTripReminderNotification(context, sampleTrip)

        val posted = shadowNotificationManager.allNotifications.lastOrNull()
        assertNotNull(posted)
        val shadowPendingIntent = Shadows.shadowOf(posted!!.contentIntent)
        val savedIntent = shadowPendingIntent.savedIntent
        assertTrue(
            "Expected EXTRA_OPEN_TRIP_FROM_REMINDER to be true",
            savedIntent.getBooleanExtra(TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER, false)
        )
    }

    @Test
    fun `test 14 - Trip A and Trip B PendingIntents have isolated trip IDs`() {
        val tripA = sampleTrip.copy(id = 101L, name = "Trip A")
        val tripB = sampleTrip.copy(id = 202L, name = "Trip B")

        TripNotificationHelper.showTripReminderNotification(context, tripA)
        val notifA = shadowNotificationManager.getNotification("trip_reminder_101", 101)
        assertNotNull(notifA)
        val intentA = Shadows.shadowOf(notifA!!.contentIntent).savedIntent
        assertEquals(101L, intentA.getLongExtra(TripNotificationHelper.EXTRA_TRIP_ID, -1L))

        TripNotificationHelper.showTripReminderNotification(context, tripB)
        val notifB = shadowNotificationManager.getNotification("trip_reminder_202", 202)
        assertNotNull(notifB)
        val intentB = Shadows.shadowOf(notifB!!.contentIntent).savedIntent
        assertEquals(202L, intentB.getLongExtra(TripNotificationHelper.EXTRA_TRIP_ID, -1L))
    }

    @Test
    fun `test 15 - deterministic requestCode derived from trip id`() {
        val tripA = sampleTrip.copy(id = 505L)
        val tripB = sampleTrip.copy(id = 606L)

        TripNotificationHelper.showTripReminderNotification(context, tripA)
        val notifA = shadowNotificationManager.getNotification("trip_reminder_505", 505)
        val requestCodeA = Shadows.shadowOf(notifA!!.contentIntent).requestCode
        assertEquals(505L.hashCode(), requestCodeA)

        TripNotificationHelper.showTripReminderNotification(context, tripB)
        val notifB = shadowNotificationManager.getNotification("trip_reminder_606", 606)
        val requestCodeB = Shadows.shadowOf(notifB!!.contentIntent).requestCode
        assertEquals(606L.hashCode(), requestCodeB)
    }

    @Test
    fun `test 16 - immutable flags are preserved on PendingIntent`() {
        TripNotificationHelper.showTripReminderNotification(context, sampleTrip)

        val posted = shadowNotificationManager.allNotifications.lastOrNull()
        assertNotNull(posted)
        val shadowPendingIntent = Shadows.shadowOf(posted!!.contentIntent)
        val flags = shadowPendingIntent.flags
        assertTrue(
            "Expected FLAG_IMMUTABLE to be present",
            (flags and PendingIntent.FLAG_IMMUTABLE) != 0
        )
        assertTrue(
            "Expected FLAG_UPDATE_CURRENT to be present",
            (flags and PendingIntent.FLAG_UPDATE_CURRENT) != 0
        )
    }
}
