package com.example.data.datasource

import com.example.data.model.LocationCategory
import com.example.data.model.LocationSuggestion
import com.example.data.repository.TripRepository
import kotlinx.coroutines.flow.first

interface UserHistorySuggestionSource {
    suspend fun getHistoryLocations(): List<LocationSuggestion>
}

class UserHistorySuggestionSourceImpl(
    private val tripRepository: TripRepository
) : UserHistorySuggestionSource {

    override suspend fun getHistoryLocations(): List<LocationSuggestion> {
        return try {
            val trips = tripRepository.getAllTrips().first()
            trips
                .filter { it.deletedAt == null && it.name.isNotBlank() }
                .distinctBy { "${it.name.trim().lowercase()}|${it.destination.trim().lowercase()}" }
                .map { trip ->
                    LocationSuggestion(
                        name = trip.name.trim(),
                        destination = trip.destination.trim(),
                        category = LocationCategory.inferFromName(trip.name),
                        aliases = emptyList(),
                        isFromHistory = true
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
