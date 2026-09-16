package com.example.data.util

import com.example.data.model.JourneyLocation

object JourneyLocationValidator {
    fun isValid(location: JourneyLocation): Boolean {
        return isValid(
            label = location.label,
            latitude = location.latitude,
            longitude = location.longitude
        )
    }

    fun isValid(label: String, latitude: Double, longitude: Double): Boolean {
        if (label.isBlank()) return false
        if (latitude.isNaN() || latitude.isInfinite()) return false
        if (longitude.isNaN() || longitude.isInfinite()) return false
        if (latitude !in -90.0..90.0) return false
        if (longitude !in -180.0..180.0) return false
        return true
    }
}
