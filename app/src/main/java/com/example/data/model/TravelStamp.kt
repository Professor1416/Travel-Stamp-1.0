package com.example.data.model

data class TravelStamp(
    val id: Long = 0,
    val tripId: Long,
    val stampCode: String,
    val title: String,
    val destination: String,
    val dateText: String,
    val peopleCount: Int,
    val momentsCount: Int,
    val inkColorHex: String = "#1E3A2F",
    val stampStyle: String = "MOUNTAIN",
    val inspectionText: String = "OFFICIALLY LOGGED • CERTIFIED JOURNEY",
    val issuedAt: Long = System.currentTimeMillis(),
    val reflectionNote: String? = null
)
