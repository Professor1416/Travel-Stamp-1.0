package com.example.data.notification

import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
import com.example.data.util.DateUtils

data class ReminderNotificationCopy(
    val title: String,
    val body: String
)

object ReminderCopyProvider {

    /**
     * Pure, deterministic notification copy provider.
     * Generates calm, preparation-oriented notification copy without external claims
     * (e.g. no claims about packing checklists or trail notes).
     */
    fun create(
        tripName: String,
        destination: String?,
        preset: TripReminderPreset,
        formattedDepartureTime: String? = null
    ): ReminderNotificationCopy {
        val name = tripName.trim()
        val dest = destination?.trim().orEmpty()
        val hasDestination = dest.isNotEmpty()

        val title = when (preset) {
            TripReminderPreset.ONE_WEEK_BEFORE -> "$name is one week away"
            TripReminderPreset.ONE_DAY_BEFORE -> "$name is tomorrow"
            TripReminderPreset.MORNING_OF -> "$name is today"
            TripReminderPreset.TWO_HOURS_BEFORE -> "$name starts in about 2 hours"
        }

        val body = when (preset) {
            TripReminderPreset.ONE_WEEK_BEFORE -> {
                if (hasDestination) {
                    "A good time to start preparing for $dest."
                } else {
                    "A good time to start preparing."
                }
            }
            TripReminderPreset.ONE_DAY_BEFORE -> {
                if (hasDestination) {
                    "Take a moment to make sure everything is ready for $dest."
                } else {
                    "Take a moment to make sure everything is ready."
                }
            }
            TripReminderPreset.MORNING_OF -> {
                val timeSuffix = if (!formattedDepartureTime.isNullOrBlank()) {
                    " Departure $formattedDepartureTime."
                } else {
                    ""
                }
                if (hasDestination) {
                    "Your journey to $dest starts today.$timeSuffix"
                } else {
                    "Your journey starts today.$timeSuffix"
                }
            }
            TripReminderPreset.TWO_HOURS_BEFORE -> {
                if (hasDestination) {
                    "Departure for $dest is coming up soon."
                } else {
                    "Departure is coming up soon."
                }
            }
        }

        return ReminderNotificationCopy(title = title, body = body)
    }

    /**
     * Convenience overload taking a domain [Trip] and deriving formatted departure time.
     */
    fun create(
        trip: Trip,
        preset: TripReminderPreset = trip.reminderPreset
    ): ReminderNotificationCopy {
        val formattedTime = DateUtils.formatTimeMinutes(trip.startTimeMinutes)
        return create(
            tripName = trip.name,
            destination = trip.destination,
            preset = preset,
            formattedDepartureTime = formattedTime
        )
    }
}
