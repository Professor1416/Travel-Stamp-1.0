package com.example.data.notification

import com.example.data.model.TripReminderPreset
import com.example.data.util.DateUtils
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Result representation for pre-trip reminder schedule calculation.
 */
sealed interface ReminderScheduleResult {

    data class Schedulable(
        val triggerAt: Instant
    ) : ReminderScheduleResult

    data object DepartureTimeRequired :
        ReminderScheduleResult

    data object TriggerAlreadyPassed :
        ReminderScheduleResult

    data object InvalidTripDate :
        ReminderScheduleResult

    data object InvalidDepartureTime :
        ReminderScheduleResult
}

/**
 * Pure Kotlin calculator for pre-trip reminder schedule triggers.
 *
 * This component has zero dependencies on Android framework classes, Room, WorkManager,
 * or UI components. It operates strictly on [java.time] abstractions.
 */
object ReminderScheduleCalculator {

    /**
     * Calculates the trigger [Instant] for a pre-trip reminder according to locked product rules.
     *
     * @param tripDate Canonical date string of the trip (e.g. "15 Sep 2026").
     * @param startTimeMinutes Departure time in minutes since local midnight (0..1439), or null if untimed.
     * @param preset The reminder preset selected by the user.
     * @param now Current timestamp for determining whether the calculated trigger is in the future.
     * @param zoneId The local timezone to evaluate wall-clock times against.
     * @return [ReminderScheduleResult] indicating whether the reminder can be scheduled or why it cannot.
     */
    fun calculate(
        tripDate: String,
        startTimeMinutes: Int?,
        preset: TripReminderPreset,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ReminderScheduleResult {
        val localDate = DateUtils.parseTripDate(tripDate)
            ?: return ReminderScheduleResult.InvalidTripDate

        if (startTimeMinutes != null && startTimeMinutes !in 0..1439) {
            return ReminderScheduleResult.InvalidDepartureTime
        }

        if (preset == TripReminderPreset.TWO_HOURS_BEFORE && startTimeMinutes == null) {
            return ReminderScheduleResult.DepartureTimeRequired
        }

        val triggerInstant: Instant = when (preset) {
            TripReminderPreset.ONE_DAY_BEFORE -> {
                if (startTimeMinutes != null) {
                    val departureTime = LocalTime.of(startTimeMinutes / 60, startTimeMinutes % 60)
                    val departureInstant = LocalDateTime.of(localDate, departureTime).atZone(zoneId).toInstant()
                    departureInstant.minus(Duration.ofHours(24))
                } else {
                    val reminderDateTime = LocalDateTime.of(localDate.minusDays(1), LocalTime.of(9, 0))
                    reminderDateTime.atZone(zoneId).toInstant()
                }
            }
            TripReminderPreset.ONE_WEEK_BEFORE -> {
                if (startTimeMinutes != null) {
                    val departureTime = LocalTime.of(startTimeMinutes / 60, startTimeMinutes % 60)
                    val reminderDateTime = LocalDateTime.of(localDate.minusDays(7), departureTime)
                    reminderDateTime.atZone(zoneId).toInstant()
                } else {
                    val reminderDateTime = LocalDateTime.of(localDate.minusDays(7), LocalTime.of(9, 0))
                    reminderDateTime.atZone(zoneId).toInstant()
                }
            }
            TripReminderPreset.MORNING_OF -> {
                val normalMorningTime = LocalTime.of(7, 0)
                if (startTimeMinutes != null) {
                    val departureTime = LocalTime.of(startTimeMinutes / 60, startTimeMinutes % 60)
                    if (!departureTime.isAfter(normalMorningTime)) {
                        val departureInstant = LocalDateTime.of(localDate, departureTime).atZone(zoneId).toInstant()
                        departureInstant.minus(Duration.ofHours(1))
                    } else {
                        LocalDateTime.of(localDate, normalMorningTime).atZone(zoneId).toInstant()
                    }
                } else {
                    LocalDateTime.of(localDate, normalMorningTime).atZone(zoneId).toInstant()
                }
            }
            TripReminderPreset.TWO_HOURS_BEFORE -> {
                val departureTime = LocalTime.of(startTimeMinutes!! / 60, startTimeMinutes % 60)
                val departureInstant = LocalDateTime.of(localDate, departureTime).atZone(zoneId).toInstant()
                departureInstant.minus(Duration.ofHours(2))
            }
        }

        if (!triggerInstant.isAfter(now)) {
            return ReminderScheduleResult.TriggerAlreadyPassed
        }

        return ReminderScheduleResult.Schedulable(triggerInstant)
    }
}
