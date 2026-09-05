package com.example.data.model

import androidx.annotation.StringRes
import com.example.R

enum class TripReminderPreset(
    val displayName: String,
    val descriptionText: String,
    val hoursBefore: Long?,
    @get:StringRes val labelResId: Int,
    @get:StringRes val helperCopyResId: Int
) {
    ONE_DAY_BEFORE(
        displayName = "1 Day Before",
        descriptionText = "24 hours before journey starts",
        hoursBefore = 24L,
        labelResId = R.string.journey_reminder_one_day,
        helperCopyResId = R.string.journey_reminder_helper_one_day
    ),
    MORNING_OF(
        displayName = "Morning of Journey",
        descriptionText = "7:00 AM on departure day",
        hoursBefore = null,
        labelResId = R.string.journey_reminder_morning,
        helperCopyResId = R.string.journey_reminder_helper_morning
    ),
    TWO_HOURS_BEFORE(
        displayName = "2 Hours Before",
        descriptionText = "2 hours before scheduled departure",
        hoursBefore = 2L,
        labelResId = R.string.journey_reminder_two_hours,
        helperCopyResId = R.string.journey_reminder_helper_two_hours
    ),
    ONE_WEEK_BEFORE(
        displayName = "1 Week Before",
        descriptionText = "7 days prior to departure",
        hoursBefore = 168L,
        labelResId = R.string.journey_reminder_one_week,
        helperCopyResId = R.string.journey_reminder_helper_one_week
    );

    companion object {
        val ALL_PRESETS_IN_ORDER = listOf(
            ONE_WEEK_BEFORE,
            ONE_DAY_BEFORE,
            MORNING_OF,
            TWO_HOURS_BEFORE
        )

        fun fromString(name: String?): TripReminderPreset {
            if (name.isNullOrBlank()) return ONE_DAY_BEFORE
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ONE_DAY_BEFORE
        }
    }
}
