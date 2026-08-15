package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.data.util.DateUtils

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val destination: String,
    val date: String,
    val peopleCount: Int = 1,
    val description: String = "",
    val status: String = "UPCOMING",
    val stampEarned: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Trip {
        // Derive proper status:
        // A journey is COMPLETED only if marked completed AND the trip date is not in the future.
        // If the date is strictly in the future, it is always UPCOMING and stamp cannot be earned.
        val resolvedStatus = when {
            DateUtils.isFutureDate(date) -> TripStatus.UPCOMING
            status == "COMPLETED" && (stampEarned || completedAt != null) -> TripStatus.COMPLETED
            status == "COMPLETED" -> TripStatus.COMPLETED
            status == "ACTIVE" || status == "IN_PROGRESS" -> TripStatus.IN_PROGRESS
            else -> TripStatus.IN_PROGRESS
        }
        val resolvedStampEarned = resolvedStatus == TripStatus.COMPLETED && (stampEarned || completedAt != null)

        return Trip(
            id = id,
            name = name,
            destination = destination,
            date = date,
            peopleCount = peopleCount,
            description = description,
            status = resolvedStatus,
            stampEarned = resolvedStampEarned,
            completedAt = if (resolvedStatus == TripStatus.COMPLETED) completedAt else null,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(trip: Trip): TripEntity {
            return TripEntity(
                id = trip.id,
                name = trip.name,
                destination = trip.destination,
                date = trip.date,
                peopleCount = trip.peopleCount,
                description = trip.description,
                status = trip.status.name,
                stampEarned = trip.stampEarned,
                completedAt = trip.completedAt,
                createdAt = trip.createdAt
            )
        }
    }
}
