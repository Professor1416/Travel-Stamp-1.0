package com.example.data.notification

import com.example.data.local.entity.TripEntity
import com.example.data.model.TripReminderPreset
import com.example.data.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class TripReminderWorkerSafetyTest {

    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val tripDate: String = "2026-10-15"
    private val departureMinutes: Int = 600 // 10:00 AM

    private lateinit var validTriggerAt: Instant
    private var validTriggerAtMillis: Long = 0L

    @Before
    fun setup() {
        val scheduleResult = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = departureMinutes,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = Instant.parse("2026-10-01T00:00:00Z"),
            zoneId = zoneId
        )
        assertTrue(scheduleResult is ReminderScheduleResult.Schedulable)
        validTriggerAt = (scheduleResult as ReminderScheduleResult.Schedulable).triggerAt
        validTriggerAtMillis = validTriggerAt.toEpochMilli()
    }

    private fun createValidTripEntity(
        id: Long = 100L,
        date: String = tripDate,
        startTimeMinutes: Int? = departureMinutes,
        reminderEnabled: Boolean = true,
        reminderPreset: String = TripReminderPreset.ONE_DAY_BEFORE.name,
        status: String = "UPCOMING",
        stampEarned: Boolean = false,
        completedAt: Long? = null,
        deletedAt: Long? = null
    ): TripEntity = TripEntity(
        id = id,
        name = "Kalsubai Peak Trek",
        destination = "Igatpuri, Maharashtra",
        date = date,
        startTimeMinutes = startTimeMinutes,
        peopleCount = 4,
        description = "Monsoon trek",
        status = status,
        stampEarned = stampEarned,
        completedAt = completedAt,
        reminderEnabled = reminderEnabled,
        reminderPreset = reminderPreset,
        deletedAt = deletedAt
    )

    @Test
    fun `test 01 - missing or non-positive tripId aborts with no notification`() {
        val decisionNegative = TripReminderWorker.evaluateSafety(
            tripId = -1L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decisionNegative is TripReminderWorker.WorkerSafetyResult.Abort)

        val decisionZero = TripReminderWorker.evaluateSafety(
            tripId = 0L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decisionZero is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 02 - missing or blank KEY_REMINDER_PRESET aborts with no notification`() {
        val decisionNull = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = null,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decisionNull is TripReminderWorker.WorkerSafetyResult.Abort)

        val decisionBlank = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = "   ",
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decisionBlank is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 03 - missing or non-positive KEY_TRIGGER_AT aborts with no notification`() {
        val decisionNegative = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = -1L,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decisionNegative is TripReminderWorker.WorkerSafetyResult.Abort)

        val decisionZero = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = 0L,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decisionZero is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 04 - invalid preset string aborts with no notification`() {
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = "INVALID_UNKNOWN_PRESET",
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 05 - invalid non-positive trigger timestamp aborts with no notification`() {
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = -100_000L,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 06 - trip not found in database aborts with no notification`() {
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = null,
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 07 - deletedAt not null aborts with no notification`() {
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(deletedAt = 1700000000000L),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 08 - status COMPLETED aborts with no notification`() {
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(status = "COMPLETED"),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 09 - stampEarned true aborts with no notification`() {
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(stampEarned = true),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 10 - completedAt not null aborts with no notification`() {
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(completedAt = 1700000000000L),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 11 - global reminders OFF aborts with no notification`() {
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = false,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 12 - individual reminder OFF aborts with no notification`() {
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(reminderEnabled = false),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 13 - scheduled preset differs from current trip preset aborts with no notification`() {
        // Scheduled with ONE_DAY_BEFORE, but user changed trip preset to MORNING_OF
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(reminderPreset = TripReminderPreset.MORNING_OF.name),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 14 - trip date edited so recalculated trigger differs aborts with no notification`() {
        // Trip date edited from 2026-10-15 to 2026-10-16
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(date = "2026-10-16"),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 15 - departure time edited so recalculated trigger differs aborts with no notification`() {
        // Departure time edited from 600 (10:00 AM) to 720 (12:00 PM)
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(startTimeMinutes = 720),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 16 - worker executes before scheduled trigger aborts with no notification`() {
        val earlyWorkerNow = validTriggerAt.minusSeconds(30)
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = true,
            workerNow = earlyWorkerNow,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 17 - worker executes greater than 2 hours late aborts with no notification`() {
        val staleWorkerNow = validTriggerAt.plus(Duration.ofHours(2)).plusMillis(1)
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = true,
            workerNow = staleWorkerNow,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 18 - worker executes exactly +2 hours late is allowed to proceed`() {
        // Exactly +2 hours after trigger
        val exactLimitWorkerNow = validTriggerAt.plus(Duration.ofHours(2))
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = true,
            workerNow = exactLimitWorkerNow,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Proceed)
        assertEquals(100L, (decision as TripReminderWorker.WorkerSafetyResult.Proceed).trip.id)
    }

    @Test
    fun `test 19 - explicit departure already occurred aborts with no notification`() {
        // Trip is 2026-10-15 at 10:00 AM
        val localDate = DateUtils.parseTripDate(tripDate)!!
        val departureInstant = localDate.atTime(LocalTime.of(10, 0)).atZone(zoneId).toInstant()

        // TWO_HOURS_BEFORE: trigger at 08:00 AM
        val scheduleResult = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = departureMinutes,
            preset = TripReminderPreset.TWO_HOURS_BEFORE,
            now = Instant.parse("2026-10-01T00:00:00Z"),
            zoneId = zoneId
        ) as ReminderScheduleResult.Schedulable

        // Worker executes at departure time (10:00 AM)
        val decisionAtDeparture = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.TWO_HOURS_BEFORE.name,
            scheduledTriggerAtMillis = scheduleResult.triggerAt.toEpochMilli(),
            tripEntity = createValidTripEntity(reminderPreset = TripReminderPreset.TWO_HOURS_BEFORE.name),
            globalRemindersEnabled = true,
            workerNow = departureInstant,
            zoneId = zoneId
        )
        assertTrue(decisionAtDeparture is TripReminderWorker.WorkerSafetyResult.Abort)

        // Worker executes after departure time (10:05 AM)
        val decisionAfterDeparture = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.TWO_HOURS_BEFORE.name,
            scheduledTriggerAtMillis = scheduleResult.triggerAt.toEpochMilli(),
            tripEntity = createValidTripEntity(reminderPreset = TripReminderPreset.TWO_HOURS_BEFORE.name),
            globalRemindersEnabled = true,
            workerNow = departureInstant.plusSeconds(300),
            zoneId = zoneId
        )
        assertTrue(decisionAfterDeparture is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 20 - TWO_HOURS_BEFORE current trip missing departure aborts with no notification`() {
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.TWO_HOURS_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(
                startTimeMinutes = null,
                reminderPreset = TripReminderPreset.TWO_HOURS_BEFORE.name
            ),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 21 - invalid current trip date aborts with no notification`() {
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(date = "invalid-not-a-date"),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 22 - invalid current departure minutes aborts with no notification`() {
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(startTimeMinutes = 1500),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Abort)

        val decisionNegative = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(startTimeMinutes = -10),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(decisionNegative is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 23 - valid current state matching metadata within grace window proceeds`() {
        // Executes 15 minutes after scheduled trigger (well within +2 hour grace window)
        val workerNow = validTriggerAt.plus(Duration.ofMinutes(15))
        val decision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = validTriggerAtMillis,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = true,
            workerNow = workerNow,
            zoneId = zoneId
        )
        assertTrue(decision is TripReminderWorker.WorkerSafetyResult.Proceed)
        val proceed = decision as TripReminderWorker.WorkerSafetyResult.Proceed
        assertEquals(100L, proceed.trip.id)
        assertEquals("Kalsubai Peak Trek", proceed.trip.name)
        assertEquals(TripReminderPreset.ONE_DAY_BEFORE, proceed.trip.reminderPreset)
    }

    @Test
    fun `test 24 - legacy pre-N5-2 work containing only tripId fails closed with no notification`() {
        // Pre-N5.2 work has only KEY_TRIP_ID, missing scheduledPresetName and scheduledTriggerAtMillis
        val legacyDecision = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = null,
            scheduledTriggerAtMillis = -1L,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = true,
            workerNow = validTriggerAt,
            zoneId = zoneId
        )
        assertTrue(legacyDecision is TripReminderWorker.WorkerSafetyResult.Abort)
    }

    @Test
    fun `test 25 - timezone change fails closed with no notification`() {
        // Scheduled in UTC, but worker executes in Asia/Kolkata
        val utcZone = ZoneId.of("UTC")
        val kolkataZone = ZoneId.of("Asia/Kolkata")

        val scheduleResultUtc = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = departureMinutes,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = Instant.parse("2026-10-01T00:00:00Z"),
            zoneId = utcZone
        ) as ReminderScheduleResult.Schedulable

        val utcTriggerMillis = scheduleResultUtc.triggerAt.toEpochMilli()

        val decisionTimezoneMismatch = TripReminderWorker.evaluateSafety(
            tripId = 100L,
            scheduledPresetName = TripReminderPreset.ONE_DAY_BEFORE.name,
            scheduledTriggerAtMillis = utcTriggerMillis,
            tripEntity = createValidTripEntity(),
            globalRemindersEnabled = true,
            workerNow = scheduleResultUtc.triggerAt,
            zoneId = kolkataZone
        )
        // Recalculating in Kolkata produces a different epoch milli than UTC trigger -> Abort
        assertTrue(decisionTimezoneMismatch is TripReminderWorker.WorkerSafetyResult.Abort)
    }
}
