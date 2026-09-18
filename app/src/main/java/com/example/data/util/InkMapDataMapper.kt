package com.example.data.util

import com.example.data.model.JourneyLocation
import com.example.data.model.TravelStamp
import com.example.data.model.InkMapData
import com.example.data.model.InkMapJourneyPoint
import com.example.data.model.InkMapMissingLocation

object InkMapDataMapper {

    /**
     * Converts raw stamps and locations into validated, correlated Ink Map data.
     * Only processes stamps that have a corresponding active parent trip.
     */
    fun mapInkMapData(
        stamps: List<TravelStamp>,
        locations: List<JourneyLocation>
    ): InkMapData {
        // Handle potential DB corruption: respect unique tripId invariant deterministically
        val distinctStamps = stamps
            .sortedWith(compareBy<TravelStamp> { it.stampNumber }.thenBy { it.id })
            .distinctBy { it.tripId }

        val locationsByTripId = locations.groupBy { it.tripId }

        val points = mutableListOf<InkMapJourneyPoint>()
        val missingLocations = mutableListOf<InkMapMissingLocation>()

        for (stamp in distinctStamps) {
            val assocLocations = locationsByTripId[stamp.tripId] ?: emptyList()
            val validLocations = assocLocations.filter { JourneyLocationValidator.isValid(it) }

            if (validLocations.isNotEmpty()) {
                // Points within same stamp: sortOrder ASC, then locationId ASC
                val sortedLocations = validLocations.sortedWith(
                    compareBy<JourneyLocation> { it.sortOrder }.thenBy { it.id }
                )
                for (loc in sortedLocations) {
                    points.add(
                        InkMapJourneyPoint(
                            journeyId = stamp.tripId,
                            stampId = stamp.id,
                            stampUuid = stamp.uuid,
                            stampNumber = stamp.stampNumber,
                            locationId = loc.id,
                            locationUuid = loc.uuid,
                            label = loc.label,
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            sortOrder = loc.sortOrder,
                            inkColorHex = stamp.inkColorHex
                        )
                    )
                }
            } else {
                missingLocations.add(
                    InkMapMissingLocation(
                        journeyId = stamp.tripId,
                        stampId = stamp.id,
                        stampUuid = stamp.uuid,
                        stampNumber = stamp.stampNumber,
                        title = stamp.title,
                        destination = stamp.destination
                    )
                )
            }
        }

        // Apply final deterministic ordering
        val finalPoints = points.sortedWith(
            compareBy<InkMapJourneyPoint> { it.stampNumber }
                .thenBy { it.stampId }
                .thenBy { it.sortOrder }
                .thenBy { it.locationId }
        )
        val finalMissing = missingLocations.sortedWith(
            compareBy<InkMapMissingLocation> { it.stampNumber }
                .thenBy { it.stampId }
        )

        return InkMapData(
            points = finalPoints,
            missingLocations = finalMissing
        )
    }
}
