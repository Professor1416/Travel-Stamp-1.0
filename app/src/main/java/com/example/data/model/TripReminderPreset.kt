package com.example.data.model

enum class TripReminderPreset(
    val displayName: String,
    val descriptionText: String,
    val hoursBefore: Long?
) {
    ONE_DAY_BEFORE(
        displayName = "1 Day Before",
        descriptionText = "24 hours before journey starts",
        hoursBefore = 24L
    ),
    MORNING_OF(
        displayName = "Morning of Journey",
        descriptionText = "7:00 AM on departure day",
        hoursBefore = null
    ),
    TWO_HOURS_BEFORE(
        displayName = "2 Hours Before",
        descriptionText = "2 hours before scheduled departure",
        hoursBefore = 2L
    ),
    ONE_WEEK_BEFORE(
        displayName = "1 Week Before",
        descriptionText = "7 days prior to departure",
        hoursBefore = 168L
    );

    companion object {
        fun fromString(name: String?): TripReminderPreset {
            if (name.isNullOrBlank()) return ONE_DAY_BEFORE
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ONE_DAY_BEFORE
        }
    }
}
