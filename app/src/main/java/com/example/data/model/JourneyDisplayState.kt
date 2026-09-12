package com.example.data.model

import java.time.LocalDate
import com.example.data.util.DateUtils

enum class JourneyDisplayState {
    UPCOMING,
    IN_PROGRESS,
    READY_TO_COMPLETE,
    STAMP_EARNED
}

object JourneyDisplayStateResolver {
    fun resolve(
        trip: Trip,
        today: LocalDate = DateUtils.getTodayLocalDate()
    ): JourneyDisplayState {
        if (trip.status == TripStatus.COMPLETED) {
            return JourneyDisplayState.STAMP_EARNED
        }
        val tripDate = DateUtils.parseTripDate(trip.date) ?: return when (trip.status) {
            TripStatus.COMPLETED -> JourneyDisplayState.STAMP_EARNED
            TripStatus.UPCOMING -> JourneyDisplayState.UPCOMING
            TripStatus.IN_PROGRESS -> JourneyDisplayState.IN_PROGRESS
        }
        
        return when {
            tripDate.isAfter(today) -> JourneyDisplayState.UPCOMING
            tripDate.isEqual(today) -> JourneyDisplayState.IN_PROGRESS
            else -> JourneyDisplayState.READY_TO_COMPLETE
        }
    }
}
