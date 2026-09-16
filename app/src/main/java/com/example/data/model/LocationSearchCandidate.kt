package com.example.data.model

data class LocationSearchCandidate(
    val providerResultId: String?,
    val label: String,
    val secondaryLabel: String?,
    val latitude: Double,
    val longitude: Double,
    val category: String?
)
