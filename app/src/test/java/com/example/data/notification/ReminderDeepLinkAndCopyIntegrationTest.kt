package com.example.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import com.example.data.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderDeepLinkAndCopyIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun dummyTrip(
        id: Long,
        preset: TripReminderPreset = TripReminderPreset.ONE_DAY_BEFORE
    ): Trip = Trip(
        id = id,
        name = "Konkan Odyssey",
        destination = "Ratnagiri",
        date = "20 Oct 2026",
        startTimeMinutes = 540,
        peopleCount = 2,
        description = "Coastline drive",
        status = TripStatus.UPCOMING,
        reminderEnabled = true,
        reminderPreset = preset
    )

    @Test
    fun `test 1 - Cold deep-link intent parsing extracts valid tripId and flag`() {
        val intent = Intent().apply {
            putExtra(TripNotificationHelper.EXTRA_TRIP_ID, 42L)
            putExtra(TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER, true)
        }

        val request = ReminderNavigationRequest.fromIntent(intent)
        assertNotNull(request)
        assertEquals(42L, request?.tripId)
    }

    @Test
    fun `test 2 - Deep-link intent without reminder flag or invalid tripId returns null`() {
        // Missing flag
        val intentWithoutFlag = Intent().apply {
            putExtra(TripNotificationHelper.EXTRA_TRIP_ID, 42L)
        }
        assertNull(ReminderNavigationRequest.fromIntent(intentWithoutFlag))

        // Non-positive trip ID
        val intentWithZeroId = Intent().apply {
            putExtra(TripNotificationHelper.EXTRA_TRIP_ID, 0L)
            putExtra(TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER, true)
        }
        assertNull(ReminderNavigationRequest.fromIntent(intentWithZeroId))

        // Null intent
        assertNull(ReminderNavigationRequest.fromIntent(null))
    }

    @Test
    fun `test 3 - PendingIntent isolation between two different journeys`() {
        val tripA = dummyTrip(101L)
        val tripB = dummyTrip(202L)

        val intentA = Intent().apply {
            putExtra(TripNotificationHelper.EXTRA_TRIP_ID, tripA.id)
            putExtra(TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER, true)
        }
        val intentB = Intent().apply {
            putExtra(TripNotificationHelper.EXTRA_TRIP_ID, tripB.id)
            putExtra(TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER, true)
        }

        val requestA = ReminderNavigationRequest.fromIntent(intentA)
        val requestB = ReminderNavigationRequest.fromIntent(intentB)

        assertEquals(101L, requestA?.tripId)
        assertEquals(202L, requestB?.tripId)
        assertTrue("Trip IDs must be isolated", requestA?.tripId != requestB?.tripId)
    }

    @Test
    fun `test 4 - Notification copy mapping for all four presets matches curated copy`() {
        val trip = dummyTrip(1L)

        // 1. ONE_WEEK_BEFORE
        val copyWeek = ReminderCopyProvider.create(trip.copy(reminderPreset = TripReminderPreset.ONE_WEEK_BEFORE))
        assertTrue("Title should mention one week away", copyWeek.title.contains("one week away", ignoreCase = true))
        assertTrue("Body should mention start preparing", copyWeek.body.contains("start preparing", ignoreCase = true))

        // 2. ONE_DAY_BEFORE
        val copyDay = ReminderCopyProvider.create(trip.copy(reminderPreset = TripReminderPreset.ONE_DAY_BEFORE))
        assertTrue("Title should mention tomorrow", copyDay.title.contains("tomorrow", ignoreCase = true))
        assertTrue("Body should mention make sure everything is ready", copyDay.body.contains("make sure everything is ready", ignoreCase = true))

        // 3. MORNING_OF
        val copyMorning = ReminderCopyProvider.create(trip.copy(reminderPreset = TripReminderPreset.MORNING_OF))
        assertTrue("Title should mention today", copyMorning.title.contains("today", ignoreCase = true))
        assertTrue("Body should mention starts today", copyMorning.body.contains("starts today", ignoreCase = true))

        // 4. TWO_HOURS_BEFORE
        val copyHours = ReminderCopyProvider.create(trip.copy(reminderPreset = TripReminderPreset.TWO_HOURS_BEFORE))
        assertTrue("Title should mention starts in about 2 hours", copyHours.title.contains("starts in about 2 hours", ignoreCase = true))
        assertTrue("Body should mention coming up soon", copyHours.body.contains("coming up soon", ignoreCase = true))
    }

    @Test
    fun `test 5 - Notification channel configuration has default importance and correct id`() {
        TripNotificationHelper.ensureNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(TripNotificationHelper.CHANNEL_ID)

        assertNotNull("Notification channel must exist", channel)
        assertEquals(TripNotificationHelper.CHANNEL_ID, channel.id)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals("Journey Reminders", channel.name.toString())
    }

    @Test
    fun `test 6 - Custom notification icon resource exists and is valid`() {
        val iconRes = R.drawable.ic_notification_travel_stamp
        assertTrue("Icon resource must be a positive integer ID", iconRes > 0)
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, iconRes)
        assertNotNull("Custom notification icon drawable must be loadable", drawable)
    }

    @Test
    fun `test 7 - Timezone determinism across different geographic zones`() {
        val dateStr = "25 Dec 2026"
        val departureMinutes = 600 // 10:00 AM local
        val fixedNow = Instant.parse("2026-10-01T00:00:00Z")

        val kolkataZone = ZoneId.of("Asia/Kolkata")
        val nyZone = ZoneId.of("America/New_York")

        val resultKolkata = ReminderScheduleCalculator.calculate(
            tripDate = dateStr,
            startTimeMinutes = departureMinutes,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = fixedNow,
            zoneId = kolkataZone
        ) as ReminderScheduleResult.Schedulable

        val resultNy = ReminderScheduleCalculator.calculate(
            tripDate = dateStr,
            startTimeMinutes = departureMinutes,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = fixedNow,
            zoneId = nyZone
        ) as ReminderScheduleResult.Schedulable

        val kolkataLocal = ZonedDateTime.ofInstant(resultKolkata.triggerAt, kolkataZone)
        val nyLocal = ZonedDateTime.ofInstant(resultNy.triggerAt, nyZone)

        // Both must trigger at 10:00 AM in their respective local timezones on 24 Dec 2026
        assertEquals(LocalDate.of(2026, 12, 24), kolkataLocal.toLocalDate())
        assertEquals(LocalTime.of(10, 0), kolkataLocal.toLocalTime())

        assertEquals(LocalDate.of(2026, 12, 24), nyLocal.toLocalDate())
        assertEquals(LocalTime.of(10, 0), nyLocal.toLocalTime())
    }

    @Test
    fun `test 8 - DST transitions preserve wall clock for ONE_WEEK and exact 24 hours for ONE_DAY`() {
        val nyZone = ZoneId.of("America/New_York")
        // US Eastern Daylight Time ended on Sunday, Nov 1, 2026 (clock falls back 1 hour from EDT to EST)
        // Trip is on Nov 4, 2026 at 10:00 AM
        val tripDate = "04 Nov 2026"
        val startTime = 600 // 10:00 AM
        val now = Instant.parse("2026-10-15T00:00:00Z")

        // 1. ONE_WEEK_BEFORE (Oct 28 at 10:00 AM EDT)
        val weekResult = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = startTime,
            preset = TripReminderPreset.ONE_WEEK_BEFORE,
            now = now,
            zoneId = nyZone
        ) as ReminderScheduleResult.Schedulable

        val weekZdt = ZonedDateTime.ofInstant(weekResult.triggerAt, nyZone)
        assertEquals(LocalDate.of(2026, 10, 28), weekZdt.toLocalDate())
        assertEquals(LocalTime.of(10, 0), weekZdt.toLocalTime())

        // 2. ONE_DAY_BEFORE (Nov 3 at 10:00 AM EST)
        val dayResult = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = startTime,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = now,
            zoneId = nyZone
        ) as ReminderScheduleResult.Schedulable

        val dayZdt = ZonedDateTime.ofInstant(dayResult.triggerAt, nyZone)
        assertEquals(LocalDate.of(2026, 11, 3), dayZdt.toLocalDate())
        assertEquals(LocalTime.of(10, 0), dayZdt.toLocalTime())

        // Departure instant: Nov 4, 2026 10:00 AM EST
        val departureInstant = ZonedDateTime.of(LocalDate.of(2026, 11, 4), LocalTime.of(10, 0), nyZone).toInstant()
        // Difference between departure and ONE_DAY_BEFORE trigger must be exactly 24 hours (86400 seconds)
        assertEquals(86400L, Duration.between(dayResult.triggerAt, departureInstant).seconds)
    }

    @Test
    fun `test 9 - MORNING_OF boundary times clamp correctly`() {
        val tripDate = "15 Oct 2026"
        val now = Instant.parse("2026-10-01T00:00:00Z")
        val zone = ZoneId.of("UTC")

        // 1. Departure at 00:00 (0 mins) -> 1 hour before midnight is 23:00 previous day
        val res0 = ReminderScheduleCalculator.calculate(tripDate, 0, TripReminderPreset.MORNING_OF, now, zone) as ReminderScheduleResult.Schedulable
        assertEquals(LocalTime.of(23, 0), ZonedDateTime.ofInstant(res0.triggerAt, zone).toLocalTime())

        // 2. Departure at 05:00 (300 mins) -> 04:00 AM
        val res5 = ReminderScheduleCalculator.calculate(tripDate, 300, TripReminderPreset.MORNING_OF, now, zone) as ReminderScheduleResult.Schedulable
        assertEquals(LocalTime.of(4, 0), ZonedDateTime.ofInstant(res5.triggerAt, zone).toLocalTime())

        // 3. Departure at 06:30 (390 mins) -> 05:30 AM
        val res630 = ReminderScheduleCalculator.calculate(tripDate, 390, TripReminderPreset.MORNING_OF, now, zone) as ReminderScheduleResult.Schedulable
        assertEquals(LocalTime.of(5, 30), ZonedDateTime.ofInstant(res630.triggerAt, zone).toLocalTime())

        // 4. Departure at 07:00 (420 mins) -> 06:00 AM
        val res7 = ReminderScheduleCalculator.calculate(tripDate, 420, TripReminderPreset.MORNING_OF, now, zone) as ReminderScheduleResult.Schedulable
        assertEquals(LocalTime.of(6, 0), ZonedDateTime.ofInstant(res7.triggerAt, zone).toLocalTime())

        // 5. Departure at 08:00 (480 mins) -> 07:00 AM
        val res8 = ReminderScheduleCalculator.calculate(tripDate, 480, TripReminderPreset.MORNING_OF, now, zone) as ReminderScheduleResult.Schedulable
        assertEquals(LocalTime.of(7, 0), ZonedDateTime.ofInstant(res8.triggerAt, zone).toLocalTime())
    }

    @Test
    fun `test 10 - Untimed journey default triggers`() {
        val tripDate = "15 Oct 2026"
        val now = Instant.parse("2026-10-01T00:00:00Z")
        val zone = ZoneId.of("UTC")

        // Untimed ONE_WEEK_BEFORE -> 9:00 AM
        val weekRes = ReminderScheduleCalculator.calculate(tripDate, null, TripReminderPreset.ONE_WEEK_BEFORE, now, zone) as ReminderScheduleResult.Schedulable
        assertEquals(LocalTime.of(9, 0), ZonedDateTime.ofInstant(weekRes.triggerAt, zone).toLocalTime())

        // Untimed ONE_DAY_BEFORE -> 9:00 AM
        val dayRes = ReminderScheduleCalculator.calculate(tripDate, null, TripReminderPreset.ONE_DAY_BEFORE, now, zone) as ReminderScheduleResult.Schedulable
        assertEquals(LocalTime.of(9, 0), ZonedDateTime.ofInstant(dayRes.triggerAt, zone).toLocalTime())

        // Untimed MORNING_OF -> 7:00 AM
        val morningRes = ReminderScheduleCalculator.calculate(tripDate, null, TripReminderPreset.MORNING_OF, now, zone) as ReminderScheduleResult.Schedulable
        assertEquals(LocalTime.of(7, 0), ZonedDateTime.ofInstant(morningRes.triggerAt, zone).toLocalTime())

        // Untimed TWO_HOURS_BEFORE -> DepartureTimeRequired (non-schedulable)
        val twoHoursRes = ReminderScheduleCalculator.calculate(tripDate, null, TripReminderPreset.TWO_HOURS_BEFORE, now, zone)
        assertEquals(ReminderScheduleResult.DepartureTimeRequired, twoHoursRes)
    }
}
