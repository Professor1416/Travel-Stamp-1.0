package com.example.data.notification

import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCopyProviderTest {

    private val sampleTrip = Trip(
        id = 10L,
        name = "Kalsubai Peak Trek",
        destination = "Igatpuri, Maharashtra",
        date = "2026-10-15",
        startTimeMinutes = 390, // 6:30 AM
        reminderPreset = TripReminderPreset.ONE_WEEK_BEFORE
    )

    @Test
    fun `test 01 - ONE_WEEK_BEFORE title`() {
        val copy = ReminderCopyProvider.create(sampleTrip, TripReminderPreset.ONE_WEEK_BEFORE)
        assertEquals("Kalsubai Peak Trek is one week away", copy.title)
    }

    @Test
    fun `test 02 - ONE_WEEK_BEFORE body`() {
        val copy = ReminderCopyProvider.create(sampleTrip, TripReminderPreset.ONE_WEEK_BEFORE)
        assertEquals("A good time to start preparing for Igatpuri, Maharashtra.", copy.body)
    }

    @Test
    fun `test 03 - ONE_DAY_BEFORE title`() {
        val copy = ReminderCopyProvider.create(sampleTrip, TripReminderPreset.ONE_DAY_BEFORE)
        assertEquals("Kalsubai Peak Trek is tomorrow", copy.title)
    }

    @Test
    fun `test 04 - ONE_DAY_BEFORE body`() {
        val copy = ReminderCopyProvider.create(sampleTrip, TripReminderPreset.ONE_DAY_BEFORE)
        assertEquals("Take a moment to make sure everything is ready for Igatpuri, Maharashtra.", copy.body)
    }

    @Test
    fun `test 05 - MORNING_OF title`() {
        val copy = ReminderCopyProvider.create(sampleTrip, TripReminderPreset.MORNING_OF)
        assertEquals("Kalsubai Peak Trek is today", copy.title)
    }

    @Test
    fun `test 06 - MORNING_OF with departure time includes formatted time`() {
        val copy = ReminderCopyProvider.create(sampleTrip, TripReminderPreset.MORNING_OF)
        assertEquals(
            "Your journey to Igatpuri, Maharashtra starts today. Departure 6:30 AM.",
            copy.body
        )
    }

    @Test
    fun `test 07 - MORNING_OF without departure time remains grammatical`() {
        val untimedTrip = sampleTrip.copy(startTimeMinutes = null)
        val copy = ReminderCopyProvider.create(untimedTrip, TripReminderPreset.MORNING_OF)
        assertEquals("Kalsubai Peak Trek is today", copy.title)
        assertEquals("Your journey to Igatpuri, Maharashtra starts today.", copy.body)
    }

    @Test
    fun `test 08 - TWO_HOURS_BEFORE title uses about 2 hours`() {
        val copy = ReminderCopyProvider.create(sampleTrip, TripReminderPreset.TWO_HOURS_BEFORE)
        assertEquals("Kalsubai Peak Trek starts in about 2 hours", copy.title)
    }

    @Test
    fun `test 09 - TWO_HOURS_BEFORE body`() {
        val copy = ReminderCopyProvider.create(sampleTrip, TripReminderPreset.TWO_HOURS_BEFORE)
        assertEquals("Departure for Igatpuri, Maharashtra is coming up soon.", copy.body)
    }

    @Test
    fun `test 10 - blank destination fallback ONE_WEEK_BEFORE`() {
        val blankDestTrip = sampleTrip.copy(destination = "")
        val copy = ReminderCopyProvider.create(blankDestTrip, TripReminderPreset.ONE_WEEK_BEFORE)
        assertEquals("Kalsubai Peak Trek is one week away", copy.title)
        assertEquals("A good time to start preparing.", copy.body)

        val whitespaceDestTrip = sampleTrip.copy(destination = "   ")
        val copyWhitespace = ReminderCopyProvider.create(whitespaceDestTrip, TripReminderPreset.ONE_WEEK_BEFORE)
        assertEquals("A good time to start preparing.", copyWhitespace.body)
    }

    @Test
    fun `test 11 - blank destination fallback ONE_DAY_BEFORE`() {
        val blankDestTrip = sampleTrip.copy(destination = "")
        val copy = ReminderCopyProvider.create(blankDestTrip, TripReminderPreset.ONE_DAY_BEFORE)
        assertEquals("Kalsubai Peak Trek is tomorrow", copy.title)
        assertEquals("Take a moment to make sure everything is ready.", copy.body)
    }

    @Test
    fun `test 12 - blank destination fallback MORNING_OF`() {
        val blankDestTripUntimed = sampleTrip.copy(destination = "", startTimeMinutes = null)
        val copyUntimed = ReminderCopyProvider.create(blankDestTripUntimed, TripReminderPreset.MORNING_OF)
        assertEquals("Kalsubai Peak Trek is today", copyUntimed.title)
        assertEquals("Your journey starts today.", copyUntimed.body)

        val blankDestTripTimed = sampleTrip.copy(destination = "  ", startTimeMinutes = 480) // 8:00 AM
        val copyTimed = ReminderCopyProvider.create(blankDestTripTimed, TripReminderPreset.MORNING_OF)
        assertEquals("Kalsubai Peak Trek is today", copyTimed.title)
        assertEquals("Your journey starts today. Departure 8:00 AM.", copyTimed.body)
    }

    @Test
    fun `test 13 - blank destination fallback TWO_HOURS_BEFORE`() {
        val blankDestTrip = sampleTrip.copy(destination = "")
        val copy = ReminderCopyProvider.create(blankDestTrip, TripReminderPreset.TWO_HOURS_BEFORE)
        assertEquals("Kalsubai Peak Trek starts in about 2 hours", copy.title)
        assertEquals("Departure is coming up soon.", copy.body)
    }

    @Test
    fun `test 14 - deterministic output same input returns exact same copy`() {
        val copy1 = ReminderCopyProvider.create(sampleTrip, TripReminderPreset.ONE_DAY_BEFORE)
        val copy2 = ReminderCopyProvider.create(sampleTrip, TripReminderPreset.ONE_DAY_BEFORE)
        assertEquals(copy1.title, copy2.title)
        assertEquals(copy1.body, copy2.body)

        val copyMorning1 = ReminderCopyProvider.create(sampleTrip, TripReminderPreset.MORNING_OF)
        val copyMorning2 = ReminderCopyProvider.create(sampleTrip, TripReminderPreset.MORNING_OF)
        assertEquals(copyMorning1, copyMorning2)
    }

    @Test
    fun `test 15 - copy contains no unconditional checklist or trail note claims`() {
        for (preset in TripReminderPreset.entries) {
            val copyWithDest = ReminderCopyProvider.create(sampleTrip, preset)
            assertFalse(copyWithDest.body.contains("checklist", ignoreCase = true))
            assertFalse(copyWithDest.body.contains("trail note", ignoreCase = true))
            assertFalse(copyWithDest.body.contains("bags are packed", ignoreCase = true))

            val copyWithoutDest = ReminderCopyProvider.create(sampleTrip.copy(destination = ""), preset)
            assertFalse(copyWithoutDest.body.contains("checklist", ignoreCase = true))
            assertFalse(copyWithoutDest.body.contains("trail note", ignoreCase = true))
            assertFalse(copyWithoutDest.body.contains("bags are packed", ignoreCase = true))
        }
    }

    @Test
    fun `test 16 - default preset parameter uses trip reminderPreset`() {
        val trip = sampleTrip.copy(reminderPreset = TripReminderPreset.TWO_HOURS_BEFORE)
        val copy = ReminderCopyProvider.create(trip)
        assertEquals("Kalsubai Peak Trek starts in about 2 hours", copy.title)
    }
}
