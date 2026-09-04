package com.example.data.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import com.example.data.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripReminderSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: TripReminderSchedulerImpl

    private val futureDateStr: String
        get() = LocalDate.now().plusDays(10).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))

    private val pastDateStr: String
        get() = LocalDate.now().minusDays(5).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        try {
            val config = androidx.work.Configuration.Builder().build()
            WorkManager.initialize(context, config)
        } catch (_: Exception) {
            // Already initialized
        }
        workManager = WorkManager.getInstance(context)
        scheduler = TripReminderSchedulerImpl(context, workManager)
    }

    @Test
    fun `test 1 - valid Schedulable result enqueues work`() {
        val trip = Trip(
            id = 101L,
            name = "Upcoming Trek",
            destination = "Kalsubai",
            date = futureDateStr,
            startTimeMinutes = 600,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )

        scheduler.scheduleReminder(trip)

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(101L)).get()
        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos[0].state)
    }

    @Test
    fun `test 2 - unique work name follows trip_reminder_tripId pattern`() {
        val workName = TripReminderScheduler.getUniqueWorkName(102L)
        assertEquals("trip_reminder_102", workName)
    }

    @Test
    fun `test 3 - broad tag trip_reminder is present on work request`() {
        val trip = Trip(
            id = 103L,
            name = "Upcoming Trek",
            destination = "Kalsubai",
            date = futureDateStr,
            startTimeMinutes = 600,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )

        scheduler.scheduleReminder(trip)

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(103L)).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos[0].tags.contains("trip_reminder"))
    }

    @Test
    fun `test 4 - per-trip tag trip_tripId is present on work request`() {
        val trip = Trip(
            id = 104L,
            name = "Upcoming Trek",
            destination = "Kalsubai",
            date = futureDateStr,
            startTimeMinutes = 600,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )

        scheduler.scheduleReminder(trip)

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(104L)).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos[0].tags.contains("trip_104"))
    }

    @Test
    fun `test 5 - changing or rescheduling same trip uses unique replacement semantics`() {
        val trip = Trip(
            id = 105L,
            name = "Upcoming Trek",
            destination = "Kalsubai",
            date = futureDateStr,
            startTimeMinutes = 600,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )

        scheduler.scheduleReminder(trip)
        val initialEnqueued = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(105L)).get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(1, initialEnqueued.size)

        // Reschedule with MORNING_OF
        val updatedTrip = trip.copy(reminderPreset = TripReminderPreset.MORNING_OF)
        scheduler.scheduleReminder(updatedTrip)

        val replacedInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(105L)).get()
        val activeEnqueued = replacedInfos.filter { it.state == WorkInfo.State.ENQUEUED }
        // Replacement policy guarantees exactly 1 active ENQUEUED work
        assertEquals(1, activeEnqueued.size)
    }

    @Test
    fun `test 6 - reminderEnabled false cancels work`() {
        val trip = Trip(
            id = 106L,
            name = "Trek",
            destination = "Dest",
            date = futureDateStr,
            startTimeMinutes = 600,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )

        scheduler.scheduleReminder(trip)
        assertEquals(1, workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(106L)).get().filter { it.state == WorkInfo.State.ENQUEUED }.size)

        scheduler.scheduleReminder(trip.copy(reminderEnabled = false))
        val enqueuedAfter = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(106L)).get().filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(0, enqueuedAfter.size)
    }

    @Test
    fun `test 7 - COMPLETED trip cancels work`() {
        val trip = Trip(
            id = 107L,
            name = "Trek",
            destination = "Dest",
            date = futureDateStr,
            startTimeMinutes = 600,
            reminderEnabled = true
        )

        scheduler.scheduleReminder(trip)
        assertEquals(1, workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(107L)).get().filter { it.state == WorkInfo.State.ENQUEUED }.size)

        scheduler.scheduleReminder(trip.copy(status = TripStatus.COMPLETED))
        val enqueuedAfter = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(107L)).get().filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(0, enqueuedAfter.size)
    }

    @Test
    fun `test 8 - deleted trip cancels work`() {
        val trip = Trip(
            id = 108L,
            name = "Trek",
            destination = "Dest",
            date = futureDateStr,
            startTimeMinutes = 600,
            reminderEnabled = true
        )

        scheduler.scheduleReminder(trip)
        assertEquals(1, workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(108L)).get().filter { it.state == WorkInfo.State.ENQUEUED }.size)

        scheduler.scheduleReminder(trip.copy(deletedAt = System.currentTimeMillis()))
        val enqueuedAfter = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(108L)).get().filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(0, enqueuedAfter.size)
    }

    @Test
    fun `test 9 - stampEarned true cancels work`() {
        val trip = Trip(
            id = 109L,
            name = "Trek",
            destination = "Dest",
            date = futureDateStr,
            startTimeMinutes = 600,
            reminderEnabled = true
        )

        scheduler.scheduleReminder(trip)
        assertEquals(1, workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(109L)).get().filter { it.state == WorkInfo.State.ENQUEUED }.size)

        scheduler.scheduleReminder(trip.copy(stampEarned = true))
        val enqueuedAfter = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(109L)).get().filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(0, enqueuedAfter.size)
    }

    @Test
    fun `test 10 - completedAt not null cancels work`() {
        val trip = Trip(
            id = 110L,
            name = "Trek",
            destination = "Dest",
            date = futureDateStr,
            startTimeMinutes = 600,
            reminderEnabled = true
        )

        scheduler.scheduleReminder(trip)
        assertEquals(1, workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(110L)).get().filter { it.state == WorkInfo.State.ENQUEUED }.size)

        scheduler.scheduleReminder(trip.copy(completedAt = System.currentTimeMillis()))
        val enqueuedAfter = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(110L)).get().filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(0, enqueuedAfter.size)
    }

    @Test
    fun `test 11 - past trigger does NOT enqueue immediate work`() {
        val pastTrip = Trip(
            id = 111L,
            name = "Past Trek",
            destination = "Dest",
            date = pastDateStr,
            startTimeMinutes = 600,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )

        scheduler.scheduleReminder(pastTrip)

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(111L)).get()
        val enqueued = workInfos.filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(0, enqueued.size)
    }

    @Test
    fun `test 12 - TWO_HOURS_BEFORE with missing departure time does not enqueue`() {
        val untimedTrip = Trip(
            id = 112L,
            name = "Untimed Trek",
            destination = "Dest",
            date = futureDateStr,
            startTimeMinutes = null,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.TWO_HOURS_BEFORE
        )

        scheduler.scheduleReminder(untimedTrip)

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(112L)).get()
        val enqueued = workInfos.filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(0, enqueued.size)
    }

    @Test
    fun `test 13 - malformed date does not enqueue`() {
        val malformedTrip = Trip(
            id = 113L,
            name = "Malformed Trek",
            destination = "Dest",
            date = "not-a-valid-date",
            startTimeMinutes = 600,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )

        scheduler.scheduleReminder(malformedTrip)

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(113L)).get()
        val enqueued = workInfos.filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(0, enqueued.size)
    }

    @Test
    fun `test 14 - invalid departure time does not enqueue`() {
        val invalidTimeTrip = Trip(
            id = 114L,
            name = "Invalid Time Trek",
            destination = "Dest",
            date = futureDateStr,
            startTimeMinutes = 1440,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE
        )

        scheduler.scheduleReminder(invalidTimeTrip)

        val workInfos = workManager.getWorkInfosForUniqueWork(TripReminderScheduler.getUniqueWorkName(114L)).get()
        val enqueued = workInfos.filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(0, enqueued.size)
    }

    @Test
    fun `test 15 - WorkRequest input contains correct tripId`() {
        val trip = Trip(
            id = 115L,
            name = "Metadata Trek",
            destination = "Sinhagad",
            date = futureDateStr,
            startTimeMinutes = 480,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.MORNING_OF
        )
        val triggerAt = Instant.parse("2026-10-15T01:30:00Z")
        val delayMillis = 50000L

        val workRequest = scheduler.buildWorkRequest(trip, triggerAt, delayMillis)
        val input = workRequest.workSpec.input
        assertEquals(115L, input.getLong(TripReminderWorker.KEY_TRIP_ID, -1L))
    }

    @Test
    fun `test 16 - WorkRequest input contains correct reminder preset name`() {
        val trip = Trip(
            id = 116L,
            name = "Metadata Trek",
            destination = "Sinhagad",
            date = futureDateStr,
            startTimeMinutes = 480,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.TWO_HOURS_BEFORE
        )
        val triggerAt = Instant.parse("2026-10-15T01:30:00Z")
        val delayMillis = 50000L

        val workRequest = scheduler.buildWorkRequest(trip, triggerAt, delayMillis)
        val input = workRequest.workSpec.input
        assertEquals("TWO_HOURS_BEFORE", input.getString(TripReminderWorker.KEY_REMINDER_PRESET))
    }

    @Test
    fun `test 17 - WorkRequest input contains correct triggerAt epoch millis`() {
        val trip = Trip(
            id = 117L,
            name = "Metadata Trek",
            destination = "Sinhagad",
            date = futureDateStr,
            startTimeMinutes = 480,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_WEEK_BEFORE
        )
        val triggerAt = Instant.parse("2026-10-15T01:30:00Z")
        val delayMillis = 50000L

        val workRequest = scheduler.buildWorkRequest(trip, triggerAt, delayMillis)
        val input = workRequest.workSpec.input
        assertEquals(triggerAt.toEpochMilli(), input.getLong(TripReminderWorker.KEY_TRIGGER_AT, -1L))
    }

    @Test
    @Suppress("DEPRECATION")
    fun `test 18 - reminderTimeMinutes is ignored by scheduler and does not alter scheduling`() {
        val tripWithLegacy = Trip(
            id = 118L,
            name = "Trek Legacy",
            destination = "Dest",
            date = futureDateStr,
            startTimeMinutes = 600,
            reminderEnabled = true,
            reminderPreset = TripReminderPreset.ONE_DAY_BEFORE,
            reminderTimeMinutes = 120
        )
        val tripWithoutLegacy = tripWithLegacy.copy(reminderTimeMinutes = null)

        val triggerWithLegacy = TripReminderScheduler.calculateReminderTriggerMillis(tripWithLegacy)
        val triggerWithoutLegacy = TripReminderScheduler.calculateReminderTriggerMillis(tripWithoutLegacy)

        assertNotNull(triggerWithLegacy)
        assertEquals(triggerWithoutLegacy, triggerWithLegacy)
    }
}
