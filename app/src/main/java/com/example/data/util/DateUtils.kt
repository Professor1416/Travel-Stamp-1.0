package com.example.data.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

object DateUtils {

    private val formatters = listOf(
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH)
    )

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
}
