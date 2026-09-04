package com.example.data.notification

import com.example.data.model.TripReminderPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure JVM unit test suite for [ReminderScheduleCalculator].
 *
 * Tests are fully deterministic using injected [Instant] and [ZoneId] instances,
 * with zero reliance on the host system clock or default timezone.
 */
class ReminderScheduleCalculatorTest {

    private val kolkataZone = ZoneId.of("Asia/Kolkata")
    private val newYorkZone = ZoneId.of("America/New_York")
    private val londonZone = ZoneId.of("Europe/London")

    // Default reference "now" timestamp set well before test trip dates
    private val baseNow = Instant.parse("2026-09-01T00:00:00Z")

    // =========================================================================
    // ONE_DAY_BEFORE Tests
    // =========================================================================

    @Test
    fun `test 1 - ONE_DAY_BEFORE with explicit departure time triggers 24 hours prior`() {
        // Trip: 15 Sep 2026 at 10:30 (10 * 60 + 30 = 630 minutes)
        val tripDate = "15 Sep 2026"
        val startTimeMinutes = 10 * 60 + 30

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = startTimeMinutes,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        // Expected: 14 Sep 2026 at 10:30 local in Asia/Kolkata
        val expectedLocal = LocalDateTime.of(2026, 9, 14, 10, 30)
        val expectedInstant = expectedLocal.atZone(kolkataZone).toInstant()

        assertEquals(expectedInstant, schedulable.triggerAt)
    }

    @Test
    fun `test 2 - ONE_DAY_BEFORE without departure time triggers previous calendar day at 09 00 local`() {
        // Trip: 15 Sep 2026 untimed
        val tripDate = "15 Sep 2026"

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = null,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        // Expected: 14 Sep 2026 at 09:00 local in Asia/Kolkata
        val expectedLocal = LocalDateTime.of(2026, 9, 14, 9, 0)
        val expectedInstant = expectedLocal.atZone(kolkataZone).toInstant()

        assertEquals(expectedInstant, schedulable.triggerAt)
    }

    // =========================================================================
    // ONE_WEEK_BEFORE Tests
    // =========================================================================

    @Test
    fun `test 3 - ONE_WEEK_BEFORE with explicit departure time triggers 7 calendar days before at departure time`() {
        // Trip: 15 Sep 2026 at 10:30 (630 min)
        val tripDate = "15 Sep 2026"
        val startTimeMinutes = 10 * 60 + 30

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = startTimeMinutes,
            preset = TripReminderPreset.ONE_WEEK_BEFORE,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        // Expected: 8 Sep 2026 at 10:30 local
        val expectedLocal = LocalDateTime.of(2026, 9, 8, 10, 30)
        val expectedInstant = expectedLocal.atZone(kolkataZone).toInstant()

        assertEquals(expectedInstant, schedulable.triggerAt)
    }

    @Test
    fun `test 4 - ONE_WEEK_BEFORE without departure time triggers 7 calendar days before at 09 00 local`() {
        val tripDate = "15 Sep 2026"

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = null,
            preset = TripReminderPreset.ONE_WEEK_BEFORE,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        // Expected: 8 Sep 2026 at 09:00 local
        val expectedLocal = LocalDateTime.of(2026, 9, 8, 9, 0)
        val expectedInstant = expectedLocal.atZone(kolkataZone).toInstant()

        assertEquals(expectedInstant, schedulable.triggerAt)
    }

    // =========================================================================
    // MORNING_OF Tests
    // =========================================================================

    @Test
    fun `test 5 - MORNING_OF without departure time triggers at 07 00 local`() {
        val tripDate = "15 Sep 2026"

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = null,
            preset = TripReminderPreset.MORNING_OF,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        val expectedLocal = LocalDateTime.of(2026, 9, 15, 7, 0)
        val expectedInstant = expectedLocal.atZone(kolkataZone).toInstant()

        assertEquals(expectedInstant, schedulable.triggerAt)
    }

    @Test
    fun `test 6 - MORNING_OF with departure 05 00 triggers at 04 00 local (1h prior)`() {
        val tripDate = "15 Sep 2026"
        val departure5am = 5 * 60 // 300 min

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = departure5am,
            preset = TripReminderPreset.MORNING_OF,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        val expectedLocal = LocalDateTime.of(2026, 9, 15, 4, 0)
        val expectedInstant = expectedLocal.atZone(kolkataZone).toInstant()

        assertEquals(expectedInstant, schedulable.triggerAt)
    }

    @Test
    fun `test 7 - MORNING_OF with departure 06 30 triggers at 05 30 local (1h prior)`() {
        val tripDate = "15 Sep 2026"
        val departure630am = 6 * 60 + 30 // 390 min

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = departure630am,
            preset = TripReminderPreset.MORNING_OF,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        val expectedLocal = LocalDateTime.of(2026, 9, 15, 5, 30)
        val expectedInstant = expectedLocal.atZone(kolkataZone).toInstant()

        assertEquals(expectedInstant, schedulable.triggerAt)
    }

    @Test
    fun `test 8 - MORNING_OF with departure 07 00 triggers at 06 00 local (equal boundary triggers 1h prior)`() {
        val tripDate = "15 Sep 2026"
        val departure7am = 7 * 60 // 420 min

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = departure7am,
            preset = TripReminderPreset.MORNING_OF,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        // 07:00 is an early/equal boundary, so it must trigger 1 hour before departure (06:00)
        val expectedLocal = LocalDateTime.of(2026, 9, 15, 6, 0)
        val expectedInstant = expectedLocal.atZone(kolkataZone).toInstant()

        assertEquals(expectedInstant, schedulable.triggerAt)
    }

    @Test
    fun `test 9 - MORNING_OF with departure 08 00 triggers at 07 00 local`() {
        val tripDate = "15 Sep 2026"
        val departure8am = 8 * 60 // 480 min

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = departure8am,
            preset = TripReminderPreset.MORNING_OF,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        val expectedLocal = LocalDateTime.of(2026, 9, 15, 7, 0)
        val expectedInstant = expectedLocal.atZone(kolkataZone).toInstant()

        assertEquals(expectedInstant, schedulable.triggerAt)
    }

    @Test
    fun `test 10 - MORNING_OF with departure later than 08 00 triggers at 07 00 local`() {
        val tripDate = "15 Sep 2026"
        val departure10am = 10 * 60 // 600 min

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = departure10am,
            preset = TripReminderPreset.MORNING_OF,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        val expectedLocal = LocalDateTime.of(2026, 9, 15, 7, 0)
        val expectedInstant = expectedLocal.atZone(kolkataZone).toInstant()

        assertEquals(expectedInstant, schedulable.triggerAt)
    }

    // =========================================================================
    // TWO_HOURS_BEFORE Tests
    // =========================================================================

    @Test
    fun `test 11 - TWO_HOURS_BEFORE with valid departure triggers exactly 2 hours prior`() {
        val tripDate = "15 Sep 2026"
        val departure10am = 10 * 60 // 600 min

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = departure10am,
            preset = TripReminderPreset.TWO_HOURS_BEFORE,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        val expectedLocal = LocalDateTime.of(2026, 9, 15, 8, 0)
        val expectedInstant = expectedLocal.atZone(kolkataZone).toInstant()

        assertEquals(expectedInstant, schedulable.triggerAt)
    }

    @Test
    fun `test 12 - TWO_HOURS_BEFORE without departure returns DepartureTimeRequired`() {
        val tripDate = "15 Sep 2026"

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = null,
            preset = TripReminderPreset.TWO_HOURS_BEFORE,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertEquals(ReminderScheduleResult.DepartureTimeRequired, result)
    }

    // =========================================================================
    // Input Validation Tests
    // =========================================================================

    @Test
    fun `test 13 - malformed trip date returns InvalidTripDate`() {
        val malformedDate = "invalid-date-string"

        val result = ReminderScheduleCalculator.calculate(
            tripDate = malformedDate,
            startTimeMinutes = 600,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertEquals(ReminderScheduleResult.InvalidTripDate, result)
    }

    @Test
    fun `test 14 - startTimeMinutes negative returns InvalidDepartureTime`() {
        val tripDate = "15 Sep 2026"

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = -1,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertEquals(ReminderScheduleResult.InvalidDepartureTime, result)
    }

    @Test
    fun `test 15 - startTimeMinutes 1440 returns InvalidDepartureTime`() {
        val tripDate = "15 Sep 2026"

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = 1440,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertEquals(ReminderScheduleResult.InvalidDepartureTime, result)
    }

    @Test
    fun `test 16 - startTimeMinutes 0 is valid and accepted`() {
        // 0 = 00:00 midnight
        val tripDate = "15 Sep 2026"

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = 0,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        val expectedLocal = LocalDateTime.of(2026, 9, 14, 0, 0)
        assertEquals(expectedLocal.atZone(kolkataZone).toInstant(), schedulable.triggerAt)
    }

    @Test
    fun `test 17 - startTimeMinutes 1439 is valid and accepted`() {
        // 1439 = 23:59
        val tripDate = "15 Sep 2026"

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = 1439,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        val expectedLocal = LocalDateTime.of(2026, 9, 14, 23, 59)
        assertEquals(expectedLocal.atZone(kolkataZone).toInstant(), schedulable.triggerAt)
    }

    // =========================================================================
    // Past-Trigger Tests
    // =========================================================================

    @Test
    fun `test 18 - trigger strictly before now returns TriggerAlreadyPassed`() {
        val tripDate = "15 Sep 2026"
        val triggerLocal = LocalDateTime.of(2026, 9, 14, 9, 0)
        val triggerInstant = triggerLocal.atZone(kolkataZone).toInstant()

        // "now" is 1 second after trigger
        val nowAfterTrigger = triggerInstant.plusSeconds(1)

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = null,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = nowAfterTrigger,
            zoneId = kolkataZone
        )

        assertEquals(ReminderScheduleResult.TriggerAlreadyPassed, result)
    }

    @Test
    fun `test 19 - trigger exactly equal to now returns TriggerAlreadyPassed`() {
        val tripDate = "15 Sep 2026"
        val triggerLocal = LocalDateTime.of(2026, 9, 14, 9, 0)
        val triggerInstant = triggerLocal.atZone(kolkataZone).toInstant()

        // "now" exactly equals the trigger instant
        val nowExactTrigger = triggerInstant

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = null,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = nowExactTrigger,
            zoneId = kolkataZone
        )

        assertEquals(ReminderScheduleResult.TriggerAlreadyPassed, result)
    }

    @Test
    fun `test 20 - trigger strictly after now returns Schedulable`() {
        val tripDate = "15 Sep 2026"
        val triggerLocal = LocalDateTime.of(2026, 9, 14, 9, 0)
        val triggerInstant = triggerLocal.atZone(kolkataZone).toInstant()

        // "now" is 1 second before trigger
        val nowBeforeTrigger = triggerInstant.minusSeconds(1)

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = null,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = nowBeforeTrigger,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable
        assertEquals(triggerInstant, schedulable.triggerAt)
    }

    // =========================================================================
    // Timezone Tests
    // =========================================================================

    @Test
    fun `test 21 - fixed Asia Kolkata timezone calculation`() {
        val tripDate = "15 Sep 2026"
        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = null,
            preset = TripReminderPreset.MORNING_OF,
            now = baseNow,
            zoneId = kolkataZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        // 15 Sep 2026 07:00 IST (+05:30) -> 01:30 UTC
        val expectedUtc = Instant.parse("2026-09-15T01:30:00Z")
        assertEquals(expectedUtc, schedulable.triggerAt)
    }

    @Test
    fun `test 22 - fixed non-IST Europe London timezone calculation`() {
        val tripDate = "15 Sep 2026"
        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = null,
            preset = TripReminderPreset.MORNING_OF,
            now = baseNow,
            zoneId = londonZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        // In Sep 2026 London is on BST (+01:00). 07:00 BST -> 06:00 UTC
        val expectedUtc = Instant.parse("2026-09-15T06:00:00Z")
        assertEquals(expectedUtc, schedulable.triggerAt)
    }

    // =========================================================================
    // Daylight Saving Time (DST) Tests
    // =========================================================================

    @Test
    fun `test 23 - ONE_DAY_BEFORE across Fall Back DST transition triggers exactly 24 elapsed hours prior`() {
        // America/New_York DST Fall Back in 2025: Sunday, Nov 2, 2025 at 02:00 AM.
        // Clocks shift back 1 hour from EDT (UTC-4) to EST (UTC-5), making Nov 2 a 25-hour day.
        // Departure: Nov 2, 2025 at 12:00 PM (noon = 720 min).
        // 12:00 PM EST (UTC-5) -> 2025-11-02T17:00:00Z.
        val tripDate = "2 Nov 2025"
        val departureNoon = 12 * 60 // 720 min

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = departureNoon,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = Instant.parse("2025-10-01T00:00:00Z"),
            zoneId = newYorkZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        // Exactly 24 elapsed hours before 2025-11-02T17:00:00Z is 2025-11-01T17:00:00Z.
        // On Nov 1 (EDT UTC-4), 17:00 UTC is 1:00 PM local (13:00).
        val expectedTrigger = Instant.parse("2025-11-01T17:00:00Z")
        assertEquals(expectedTrigger, schedulable.triggerAt)

        val departureInstant = LocalDateTime.of(2025, 11, 2, 12, 0)
            .atZone(newYorkZone)
            .toInstant()
        assertEquals(Duration.ofHours(24), Duration.between(schedulable.triggerAt, departureInstant))
    }

    @Test
    fun `test 24 - ONE_WEEK_BEFORE across DST transition preserves 7 local calendar days at same wall clock time`() {
        // America/New_York DST Fall Back in 2025: Sunday, Nov 2, 2025.
        // Trip: Tuesday, Nov 4, 2025 at 10:30 AM (630 min) in EST (UTC-5).
        // Departure Instant: 2025-11-04T15:30:00Z.
        // Seven calendar days before is Tuesday, Oct 28, 2025.
        // In local wall-clock time, ONE_WEEK_BEFORE fires at 10:30 AM EDT (UTC-4) on Oct 28.
        // 10:30 EDT -> 2025-10-28T14:30:00Z.
        // Note: Elapsed time is 169 hours (7 days * 24h + 1h for the 25-hour DST day),
        // proving calendar-day semantics rather than elapsed-hour subtraction.
        val tripDate = "4 Nov 2025"
        val departure1030am = 10 * 60 + 30

        val result = ReminderScheduleCalculator.calculate(
            tripDate = tripDate,
            startTimeMinutes = departure1030am,
            preset = TripReminderPreset.ONE_WEEK_BEFORE,
            now = Instant.parse("2025-10-01T00:00:00Z"),
            zoneId = newYorkZone
        )

        assertTrue(result is ReminderScheduleResult.Schedulable)
        val schedulable = result as ReminderScheduleResult.Schedulable

        val expectedTrigger = LocalDateTime.of(2025, 10, 28, 10, 30)
            .atZone(newYorkZone)
            .toInstant()
        assertEquals(expectedTrigger, schedulable.triggerAt)

        val departureInstant = LocalDateTime.of(2025, 11, 4, 10, 30)
            .atZone(newYorkZone)
            .toInstant()
        assertEquals(Duration.ofHours(169), Duration.between(schedulable.triggerAt, departureInstant))
    }
}
