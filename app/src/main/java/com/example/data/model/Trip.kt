package com.example.data.model

enum class TripStatus {
    UPCOMING,
    IN_PROGRESS,
    COMPLETED;

    // Backward-compatibility alias
    companion object {
        val ACTIVE get() = IN_PROGRESS
    }
}

data class Trip(
    val id: Long = 0,
    val name: String,
    val destination: String,
    val date: String,
    val peopleCount: Int = 1,
    val description: String = "",
    val status: TripStatus = TripStatus.UPCOMING,
    val stampEarned: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
