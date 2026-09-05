package com.example.data.notification

import android.content.Intent

/**
 * Immutable parsed representation of a journey reminder deep-link navigation request.
 * Guarantees that only intents explicitly marked as reminder navigations with positive
 * non-zero trip IDs are processed.
 */
data class ReminderNavigationRequest(
    val tripId: Long
) {
    companion object {
        fun fromIntent(intent: Intent?): ReminderNavigationRequest? {
            if (intent == null) return null
            val isFromReminder = intent.getBooleanExtra(
                TripNotificationHelper.EXTRA_OPEN_TRIP_FROM_REMINDER,
                false
            )
            if (!isFromReminder) return null

            val tripId = intent.getLongExtra(TripNotificationHelper.EXTRA_TRIP_ID, -1L)
            if (tripId <= 0L) return null

            return ReminderNavigationRequest(tripId)
        }
    }
}
