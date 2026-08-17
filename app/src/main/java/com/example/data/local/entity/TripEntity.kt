package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.data.util.DateUtils
import java.util.UUID

@Entity(
    tableName = "trips",
    indices = [
        Index(value = ["uuid"], unique = true)
    ]
)
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val destination: String,
    val date: String,
    val peopleCount: Int = 1,
    val description: String = "",
    val status: String = "UPCOMING",
    val stampEarned: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
) {
    fun toDomain(): Trip {
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
            uuid = uuid,
            name = name,
            destination = destination,
            date = date,
            peopleCount = peopleCount,
            description = description,
            status = resolvedStatus,
            stampEarned = resolvedStampEarned,
            completedAt = if (resolvedStatus == TripStatus.COMPLETED) completedAt else null,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt
        )
    }

    companion object {
        fun fromDomain(trip: Trip): TripEntity {
            return TripEntity(
                id = trip.id,
                uuid = trip.uuid,
                name = trip.name,
                destination = trip.destination,
                date = trip.date,
                peopleCount = trip.peopleCount,
                description = trip.description,
                status = trip.status.name,
                stampEarned = trip.stampEarned,
                completedAt = trip.completedAt,
                createdAt = trip.createdAt,
                updatedAt = trip.updatedAt,
                deletedAt = trip.deletedAt
            )
        }
    }
}
