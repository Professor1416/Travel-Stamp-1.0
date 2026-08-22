package com.example.data.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

object DateUtils {

    private val formatPatterns = listOf(
        "d MMM yyyy",
        "dd MMM yyyy",
        "d MMMM yyyy",
        "dd MMMM yyyy",
        "yyyy-MM-dd",
        "d/M/yyyy",
        "dd/MM/yyyy",
        "MM/dd/yyyy",
        "d-M-yyyy",
        "dd-MM-yyyy",
        "yyyy/MM/dd",
        "d.M.yyyy",
        "dd.MM.yyyy"
    )

    private val formatters = formatPatterns.map { pattern ->
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(pattern)
            .toFormatter(Locale.ENGLISH)
    }

    /**
     * Returns today's date in user's default local timezone.
     */
    fun getTodayLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
        return LocalDate.now(zoneId)
    }

    /**
     * Parses a trip date string into a LocalDate.
     * Returns null if unparseable.
     */
    fun parseTripDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        val cleaned = dateStr.trim()
        for (formatter in formatters) {
            try {
                return LocalDate.parse(cleaned, formatter)
            } catch (_: Exception) {
                // continue to next formatter
            }
        }
        return null
    }

    /**
     * Converts a trip date string into an epoch day (days since 1970-01-01) for strict chronological sorting.
     * If the date string cannot be parsed, falls back to the provided epoch millisecond timestamp (e.g. createdAt/issuedAt).
     */
    fun getEpochDay(dateStr: String?, fallbackMillis: Long = 0L): Long {
        val parsed = parseTripDate(dateStr)
        return parsed?.toEpochDay() ?: (fallbackMillis / (1000L * 60L * 60L * 24L))
    }

    /**
     * Checks if a given trip date is strictly in the future compared to today's local date.
     */
    fun isFutureDate(dateStr: String?, today: LocalDate = getTodayLocalDate()): Boolean {
        val tripDate = parseTripDate(dateStr) ?: return false
        return tripDate.isAfter(today)
    }

    /**
     * Checks if a given trip date is today or in the past (eligible for completion).
     */
    fun isEligibleForCompletion(dateStr: String?, today: LocalDate = getTodayLocalDate()): Boolean {
        val tripDate = parseTripDate(dateStr) ?: return true // If date is unknown/custom, treat as eligible
        return !tripDate.isAfter(today)
    }

    /**
     * Formats a LocalDate into standard app display string "15 Aug 2026".
     */
    fun formatDisplayDate(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
    }

    /**
     * Formats canonical minutes since midnight (0..1439) into localized 12-hour AM/PM string.
     * Examples: 0 -> "12:00 AM", 420 -> "7:00 AM", 720 -> "12:00 PM", 1439 -> "11:59 PM".
     * Returns null if minutes is null or outside the valid 0..1439 boundary.
     */
    fun formatTimeMinutes(minutes: Int?, use24Hour: Boolean = false): String? {
        if (minutes == null || minutes !in 0..1439) return null
        val hours = minutes / 60
        val mins = minutes % 60
        return if (use24Hour) {
            String.format(Locale.ENGLISH, "%02d:%02d", hours, mins)
        } else {
            val amPm = if (hours < 12) "AM" else "PM"
            val displayHour = when {
                hours == 0 -> 12
                hours > 12 -> hours - 12
                else -> hours
            }
            String.format(Locale.ENGLISH, "%d:%02d %s", displayHour, mins, amPm)
        }
    }

    /**
     * Formats trip date with optional start time for trip details display.
     * Examples:
     * - ("23 August 2026", 420) -> "23 August 2026 • 7:00 AM"
     * - ("23 August 2026", null) -> "23 August 2026"
     */
    fun formatTripDateWithTime(dateStr: String?, startTimeMinutes: Int?): String {
        if (dateStr.isNullOrBlank()) return ""
        val formattedTime = formatTimeMinutes(startTimeMinutes)
        return if (formattedTime != null) {
            "$dateStr • $formattedTime"
        } else {
            dateStr
        }
    }
}

