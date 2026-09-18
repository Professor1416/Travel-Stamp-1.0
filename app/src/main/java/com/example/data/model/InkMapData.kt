package com.example.data.model

data class InkMapJourneyPoint(
    val journeyId: Long,
    val stampId: Long,
    val stampUuid: String,
    val stampNumber: Long,

    val locationId: Long,
    val locationUuid: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val sortOrder: Int,

    val inkColorHex: String
)

data class InkMapMissingLocation(
    val journeyId: Long,
    val stampId: Long,
    val stampUuid: String,
    val stampNumber: Long,

    val title: String,
    val destination: String
)

data class InkMapData(
    val points: List<InkMapJourneyPoint>,
    val missingLocations: List<InkMapMissingLocation>
)
