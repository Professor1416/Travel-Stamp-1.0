package com.example.data.notification

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.TripEntity
import com.example.data.model.TripReminderPreset
import com.example.ui.permission.NotificationPermissionPolicy
import com.example.ui.permission.NotificationPermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderLifecycleSafetyIntegrationTest {

    private lateinit var context: Context
    private val zoneId: ZoneId = ZoneId.of("UTC")
    private val tripDate: String = "15 Sep 2026"
    private val startTimeMinutes: Int = 600 // 10:00 AM UTC

    // Reference trigger: 14 Sep 2026 10:00 AM UTC
    private val triggerInstant: Instant = Instant.parse("2026-09-14T10:00:00Z")
    private val triggerMillis: Long = triggerInstant.toEpochMilli()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun baseTrip(
        id: Long = 100L,
        status: String = "UPCOMING",
        stampEarned: Boolean = false,
        completedAt: Long? = null,
        deletedAt: Long? = null,
        reminderEnabled: Boolean = true,
        reminderPreset: String = TripReminderPreset.ONE_DAY_BEFORE.name,
        date: String = tripDate,
        startMin: Int? = startTimeMinutes
    ): TripEntity {
        return TripEntity(
            id = id,
            name = "Peak Expedition",
            destination = "Sahyadri",
            date = date,
            startTimeMinutes = startMin,
            peopleCount = 2,
            description = "Trek",
            status = status,
            stampEarned = stampEarned,
            completedAt = completedAt,
            deletedAt = deletedAt,
            reminderEnabled = reminderEnabled,
            reminderPreset = reminderPreset,
            reminderTimeMinutes = null,
            createdAt = 1000L
        )
    }

    @Test
    fun `test 1 - Stale worker aborts when trip status became COMPLETED`() {
        val completedTrip = baseTrip(status = "COMPLETED", completedAt = triggerMillis - 1000)
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = triggerMillis,
            tripEntity = completedTrip,
            globalRemindersEnabled = true,
            workerNow = triggerInstant.plusSeconds(30),
            zoneId = zoneId
        )
        assertEquals(TripReminderWorker.WorkerSafetyResult.Abort, decision)
    }

    @Test
    fun `test 2 - Stale worker aborts when stamp was earned`() {
        val stampedTrip = baseTrip(stampEarned = true)
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = triggerMillis,
            tripEntity = stampedTrip,
            globalRemindersEnabled = true,
            workerNow = triggerInstant.plusSeconds(30),
            zoneId = zoneId
        )
        assertEquals(TripReminderWorker.WorkerSafetyResult.Abort, decision)
    }

    @Test
    fun `test 3 - Stale worker aborts when trip was soft-deleted`() {
        val deletedTrip = baseTrip(deletedAt = triggerMillis - 500)
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = triggerMillis,
            tripEntity = deletedTrip,
            globalRemindersEnabled = true,
            workerNow = triggerInstant.plusSeconds(30),
            zoneId = zoneId
        )
        assertEquals(TripReminderWorker.WorkerSafetyResult.Abort, decision)
    }

    @Test
    fun `test 4 - Stale worker aborts when per-trip reminder was disabled`() {
        val disabledTrip = baseTrip(reminderEnabled = false)
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = triggerMillis,
            tripEntity = disabledTrip,
            globalRemindersEnabled = true,
            workerNow = triggerInstant.plusSeconds(30),
            zoneId = zoneId
        )
        assertEquals(TripReminderWorker.WorkerSafetyResult.Abort, decision)
    }

    @Test
    fun `test 5 - Stale worker aborts when global reminders preference was disabled`() {
        val trip = baseTrip()
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = triggerMillis,
            tripEntity = trip,
            globalRemindersEnabled = false, // Global disabled!
            workerNow = triggerInstant.plusSeconds(30),
            zoneId = zoneId
        )
        assertEquals(TripReminderWorker.WorkerSafetyResult.Abort, decision)
    }

    @Test
    fun `test 6 - Stale worker aborts when scheduled preset mismatches entity preset`() {
        val trip = baseTrip(reminderPreset = TripReminderPreset.MORNING_OF.name)
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name, // Stale scheduled preset
            scheduledTriggerAtMillis = triggerMillis,
            tripEntity = trip,
            globalRemindersEnabled = true,
            workerNow = triggerInstant.plusSeconds(30),
            zoneId = zoneId
        )
        assertEquals(TripReminderWorker.WorkerSafetyResult.Abort, decision)
    }

    @Test
    fun `test 7 - Stale worker aborts when departure date changed, causing trigger recalculation mismatch`() {
        val tripWithDifferentDate = baseTrip(date = "16 Sep 2026")
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = triggerMillis, // Stale trigger from 15 Sep
            tripEntity = tripWithDifferentDate,
            globalRemindersEnabled = true,
            workerNow = triggerInstant.plusSeconds(30),
            zoneId = zoneId
        )
        assertEquals(TripReminderWorker.WorkerSafetyResult.Abort, decision)
    }

    @Test
    fun `test 8 - Worker metadata safety fails closed for invalid or missing inputs`() {
        val trip = baseTrip()

        // Negative tripId
        assertEquals(TripReminderWorker.WorkerSafetyResult.Abort,
            TripReminderWorker.evaluateSafety(-1L, TripReminderPreset.ONE_DAY_BEFORE.name, triggerMillis, trip, true, triggerInstant, zoneId))

        // Null preset name
        assertEquals(TripReminderWorker.WorkerSafetyResult.Abort,
            TripReminderWorker.evaluateSafety(100L, null, triggerMillis, trip, true, triggerInstant, zoneId))

        // Blank preset name
        assertEquals(TripReminderWorker.WorkerSafetyResult.Abort,
            TripReminderWorker.evaluateSafety(100L, "", triggerMillis, trip, true, triggerInstant, zoneId))

        // Corrupt preset name
        assertEquals(TripReminderWorker.WorkerSafetyResult.Abort,
            TripReminderWorker.evaluateSafety(100L, "UNKNOWN_PRESET", triggerMillis, trip, true, triggerInstant, zoneId))

        // Zero or negative trigger millis
        assertEquals(TripReminderWorker.WorkerSafetyResult.Abort,
            TripReminderWorker.evaluateSafety(100L, TripReminderPreset.ONE_DAY_BEFORE.name, 0L, trip, true, triggerInstant, zoneId))

        // Null trip entity (trip completely deleted from DB)
        assertEquals(TripReminderWorker.WorkerSafetyResult.Abort,
            TripReminderWorker.evaluateSafety(100L, TripReminderPreset.ONE_DAY_BEFORE.name, triggerMillis, null, true, triggerInstant, zoneId))
    }

    @Test
    fun `test 9 - Worker execution window boundary testing`() {
        val trip = baseTrip()

        // Early execution: 1 millisecond before trigger -> ABORT
        val early = TripReminderWorker.evaluateSafety(
            100L, TripReminderPreset.ONE_DAY_BEFORE.name, triggerMillis, trip, true,
            triggerInstant.minusMillis(1), zoneId
        )
        assertEquals(TripReminderWorker.WorkerSafetyResult.Abort, early)

        // Exact trigger -> PROCEED
        val exact = TripReminderWorker.evaluateSafety(
            100L, TripReminderPreset.ONE_DAY_BEFORE.name, triggerMillis, trip, true,
            triggerInstant, zoneId
        )
        assertTrue("Exact trigger must proceed", exact is TripReminderWorker.WorkerSafetyResult.Proceed)

        // Trigger + 1 hour 59 minutes -> PROCEED
        val withinGrace = TripReminderWorker.evaluateSafety(
            100L, TripReminderPreset.ONE_DAY_BEFORE.name, triggerMillis, trip, true,
            triggerInstant.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(59)), zoneId
        )
        assertTrue("Within grace must proceed", withinGrace is TripReminderWorker.WorkerSafetyResult.Proceed)

        // Trigger + exactly 2 hours -> PROCEED
        val exactBoundary = TripReminderWorker.evaluateSafety(
            100L, TripReminderPreset.ONE_DAY_BEFORE.name, triggerMillis, trip, true,
            triggerInstant.plus(Duration.ofHours(2)), zoneId
        )
        assertTrue("Exact 2-hour boundary must proceed", exactBoundary is TripReminderWorker.WorkerSafetyResult.Proceed)

        // Trigger + 2 hours + 1 millisecond -> ABORT
        val beyondGrace = TripReminderWorker.evaluateSafety(
            100L, TripReminderPreset.ONE_DAY_BEFORE.name, triggerMillis, trip, true,
            triggerInstant.plus(Duration.ofHours(2)).plusMillis(1), zoneId
        )
        assertEquals("Beyond 2 hours must abort", TripReminderWorker.WorkerSafetyResult.Abort, beyondGrace)
    }

    @Test
    fun `test 10 - Departure already occurred aborts worker execution even if within grace`() {
        // Departure is at 10:00 AM UTC (15 Sep 2026: 2026-09-15T10:00:00Z)
        // Preset is TWO_HOURS_BEFORE -> trigger is 8:00 AM UTC (2026-09-15T08:00:00Z)
        val departureInstant = Instant.parse("2026-09-15T10:00:00Z")
        val twoHoursBeforeTrigger = Instant.parse("2026-09-15T08:00:00Z")
        val trip = baseTrip(
            reminderPreset = TripReminderPreset.TWO_HOURS_BEFORE.name,
            startMin = 600
        )

        // Worker executes at 10:01 AM UTC (departure already occurred!)
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.TWO_HOURS_BEFORE.name,
            scheduledTriggerAtMillis = twoHoursBeforeTrigger.toEpochMilli(),
            tripEntity = trip,
            globalRemindersEnabled = true,
            workerNow = departureInstant.plusSeconds(60), // After departure
            zoneId = zoneId
        )
        assertEquals("Must abort if departure already occurred", TripReminderWorker.WorkerSafetyResult.Abort, decision)
    }

    @Test
    fun `test 11 - Permission evaluation handles API 32 and API 33 correctly`() {
        // API 32 (Android 12) -> Granted without runtime permission
        val statusApi32 = NotificationPermissionPolicy.evaluateStatus(
            sdkInt = 32,
            notificationsEnabled = true,
            postNotificationsGranted = false,
            shouldShowRationale = false,
            hasRequestedBefore = false
        )
        assertEquals(NotificationPermissionStatus.Granted, statusApi32)

        // API 33 (Android 13) with runtime permission not granted -> Requestable
        val statusApi33Requestable = NotificationPermissionPolicy.evaluateStatus(
            sdkInt = 33,
            notificationsEnabled = true,
            postNotificationsGranted = false,
            shouldShowRationale = false,
            hasRequestedBefore = false
        )
        assertEquals(NotificationPermissionStatus.Requestable, statusApi33Requestable)

        // API 33 with runtime permission granted -> Granted
        val statusApi33Granted = NotificationPermissionPolicy.evaluateStatus(
            sdkInt = 33,
            notificationsEnabled = true,
            postNotificationsGranted = true,
            shouldShowRationale = false,
            hasRequestedBefore = true
        )
        assertEquals(NotificationPermissionStatus.Granted, statusApi33Granted)

        // System app notifications disabled -> Blocked on all APIs
        val statusBlocked = NotificationPermissionPolicy.evaluateStatus(
            sdkInt = 34,
            notificationsEnabled = false,
            postNotificationsGranted = true,
            shouldShowRationale = false,
            hasRequestedBefore = true
        )
        assertEquals(NotificationPermissionStatus.Blocked, statusBlocked)
    }
}
