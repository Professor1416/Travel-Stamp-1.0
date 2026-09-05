package com.example.ui.reminder

import com.example.data.model.TripReminderPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderFormValidatorTest {

    private val fixedNow: Instant = Instant.parse("2026-09-10T10:00:00Z")
    private val zoneUtc: ZoneId = ZoneId.of("UTC")

    @Test
    fun `TEST 01 - reminder OFF is always Valid regardless of past date or missing time`() {
        // Past date
        val result1 = validateReminderForm(
            reminderEnabled = false,
            tripDate = "01 Jan 2020",
            startTimeMinutes = null,
            preset = TripReminderPreset.TWO_HOURS_BEFORE,
            now = fixedNow,
            zoneId = zoneUtc
        )
        assertEquals(ReminderFormValidation.Valid, result1)
        assertTrue(result1.isValid)
        assertNull(result1.errorMessageResId)

        // Invalid date
        val result2 = validateReminderForm(
            reminderEnabled = false,
            tripDate = "garbage-date",
            startTimeMinutes = -99,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = fixedNow,
            zoneId = zoneUtc
        )
        assertEquals(ReminderFormValidation.Valid, result2)
    }

    @Test
    fun `TEST 02 - ONE_DAY_BEFORE future date without start time is Valid`() {
        val result = validateReminderForm(
            reminderEnabled = true,
            tripDate = "15 Sep 2026",
            startTimeMinutes = null,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = fixedNow,
            zoneId = zoneUtc
        )
        assertEquals(ReminderFormValidation.Valid, result)
        assertTrue(result.isValid)
    }

    @Test
    fun `TEST 03 - ONE_DAY_BEFORE future date with start time is Valid`() {
        val result = validateReminderForm(
            reminderEnabled = true,
            tripDate = "15 Sep 2026",
            startTimeMinutes = 480, // 08:00
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = fixedNow,
            zoneId = zoneUtc
        )
        assertEquals(ReminderFormValidation.Valid, result)
        assertTrue(result.isValid)
    }

    @Test
    fun `TEST 04 - ONE_WEEK_BEFORE future date is Valid`() {
        val result = validateReminderForm(
            reminderEnabled = true,
            tripDate = "25 Sep 2026",
            startTimeMinutes = null,
            preset = TripReminderPreset.ONE_WEEK_BEFORE,
            now = fixedNow,
            zoneId = zoneUtc
        )
        assertEquals(ReminderFormValidation.Valid, result)
        assertTrue(result.isValid)
    }

    @Test
    fun `TEST 05 - MORNING_OF future date is Valid`() {
        val result = validateReminderForm(
            reminderEnabled = true,
            tripDate = "12 Sep 2026",
            startTimeMinutes = 540, // 09:00
            preset = TripReminderPreset.MORNING_OF,
            now = fixedNow,
            zoneId = zoneUtc
        )
        assertEquals(ReminderFormValidation.Valid, result)
        assertTrue(result.isValid)
    }

    @Test
    fun `TEST 06 - TWO_HOURS_BEFORE with start time in future is Valid`() {
        val result = validateReminderForm(
            reminderEnabled = true,
            tripDate = "11 Sep 2026",
            startTimeMinutes = 600, // 10:00 (trigger is 08:00 UTC on 11 Sep, after now 10 Sep 10:00)
            preset = TripReminderPreset.TWO_HOURS_BEFORE,
            now = fixedNow,
            zoneId = zoneUtc
        )
        assertEquals(ReminderFormValidation.Valid, result)
        assertTrue(result.isValid)
    }

    @Test
    fun `TEST 07 - TWO_HOURS_BEFORE without start time returns StartTimeRequired`() {
        val result = validateReminderForm(
            reminderEnabled = true,
            tripDate = "15 Sep 2026",
            startTimeMinutes = null,
            preset = TripReminderPreset.TWO_HOURS_BEFORE,
            now = fixedNow,
            zoneId = zoneUtc
        )
        assertEquals(ReminderFormValidation.StartTimeRequired, result)
        assertEquals(com.example.R.string.journey_reminder_requires_start_time, result.errorMessageResId)
    }

    @Test
    fun `TEST 08 - Trip date in past with reminder ON returns TriggerAlreadyPassed`() {
        val result = validateReminderForm(
            reminderEnabled = true,
            tripDate = "01 Sep 2026",
            startTimeMinutes = 480,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = fixedNow, // 10 Sep 2026
            zoneId = zoneUtc
        )
        assertEquals(ReminderFormValidation.TriggerAlreadyPassed, result)
        assertEquals(com.example.R.string.journey_reminder_time_passed, result.errorMessageResId)
    }

    @Test
    fun `TEST 09 - Same-day or near-term trip where trigger time already passed returns TriggerAlreadyPassed`() {
        // Trip is on 10 Sep 2026 at 11:00 UTC.
        // ONE_DAY_BEFORE trigger would be 09 Sep 2026 at 11:00 UTC (in the past relative to 10 Sep 10:00 UTC).
        val result = validateReminderForm(
            reminderEnabled = true,
            tripDate = "10 Sep 2026",
            startTimeMinutes = 660, // 11:00
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = fixedNow,
            zoneId = zoneUtc
        )
        assertEquals(ReminderFormValidation.TriggerAlreadyPassed, result)
        assertEquals(com.example.R.string.journey_reminder_time_passed, result.errorMessageResId)
    }

    @Test
    fun `TEST 10 - Invalid trip date string returns InvalidTripDate`() {
        val result = validateReminderForm(
            reminderEnabled = true,
            tripDate = "Invalid-date-format",
            startTimeMinutes = 480,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = fixedNow,
            zoneId = zoneUtc
        )
        assertEquals(ReminderFormValidation.InvalidTripDate, result)
    }

    @Test
    fun `TEST 11 - Invalid departure time returns InvalidDepartureTime`() {
        val resultNeg = validateReminderForm(
            reminderEnabled = true,
            tripDate = "15 Sep 2026",
            startTimeMinutes = -10,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = fixedNow,
            zoneId = zoneUtc
        )
        assertEquals(ReminderFormValidation.InvalidDepartureTime, resultNeg)

        val resultOver = validateReminderForm(
            reminderEnabled = true,
            tripDate = "15 Sep 2026",
            startTimeMinutes = 1500,
            preset = TripReminderPreset.ONE_DAY_BEFORE,
            now = fixedNow,
            zoneId = zoneUtc
        )
        assertEquals(ReminderFormValidation.InvalidDepartureTime, resultOver)
    }
}
