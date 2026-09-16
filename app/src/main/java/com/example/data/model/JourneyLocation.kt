package com.example.data.model

import java.util.UUID

data class JourneyLocation(
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val tripId: Long,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val sortOrder: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
