package com.example.data.model

enum class TripStatus {
    ACTIVE,
    COMPLETED
}

data class Trip(
    val id: Long = 0,
    val name: String,
    val destination: String,
    val date: String,
    val peopleCount: Int = 1,
    val description: String = "",
    val status: TripStatus = TripStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val stampInkColorHex: String = "#1E3A2F",
    val stampStyle: String = "MOUNTAIN",
    val reflectionNote: String? = null
)
