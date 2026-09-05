package com.example.ui.reminder

import androidx.annotation.StringRes
import com.example.R
import com.example.data.model.TripReminderPreset
import com.example.data.notification.ReminderScheduleCalculator
import com.example.data.notification.ReminderScheduleResult
import java.time.Instant
import java.time.ZoneId

sealed interface ReminderFormValidation {
    data object Valid : ReminderFormValidation
    data object StartTimeRequired : ReminderFormValidation
    data object TriggerAlreadyPassed : ReminderFormValidation
    data object InvalidTripDate : ReminderFormValidation
    data object InvalidDepartureTime : ReminderFormValidation

    val isValid: Boolean get() = this is Valid

    @get:StringRes
    val errorMessageResId: Int?
        get() = when (this) {
            Valid -> null
            StartTimeRequired -> R.string.journey_reminder_requires_start_time
            TriggerAlreadyPassed -> R.string.journey_reminder_time_passed
            InvalidTripDate -> null
            InvalidDepartureTime -> null
        }
}

object ReminderFormValidator {

    fun validateReminderForm(
        reminderEnabled: Boolean,
        tripDate: String,
        startTimeMinutes: Int?,
        preset: TripReminderPreset,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ReminderFormValidation {
        if (!reminderEnabled) {
            return ReminderFormValidation.Valid
        }

        return when (ReminderScheduleCalculator.calculate(tripDate, startTimeMinutes, preset, now, zoneId)) {
            is ReminderScheduleResult.Schedulable -> ReminderFormValidation.Valid
            is ReminderScheduleResult.DepartureTimeRequired -> ReminderFormValidation.StartTimeRequired
            is ReminderScheduleResult.TriggerAlreadyPassed -> ReminderFormValidation.TriggerAlreadyPassed
            is ReminderScheduleResult.InvalidTripDate -> ReminderFormValidation.InvalidTripDate
            is ReminderScheduleResult.InvalidDepartureTime -> ReminderFormValidation.InvalidDepartureTime
        }
    }
}

fun validateReminderForm(
    reminderEnabled: Boolean,
    tripDate: String,
    startTimeMinutes: Int?,
    preset: TripReminderPreset,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
): ReminderFormValidation = ReminderFormValidator.validateReminderForm(
    reminderEnabled = reminderEnabled,
    tripDate = tripDate,
    startTimeMinutes = startTimeMinutes,
    preset = preset,
    now = now,
    zoneId = zoneId
)
