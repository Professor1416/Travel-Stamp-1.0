package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.Trip
import com.example.data.model.TripReminderPreset
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
    val startTimeMinutes: Int? = null,
    val peopleCount: Int = 1,
    val description: String = "",
    val status: String = "UPCOMING",
    val stampEarned: Boolean = false,
    val completedAt: Long? = null,
    val reminderEnabled: Boolean = false,
    val reminderPreset: String = "ONE_DAY_BEFORE",
    val reminderTimeMinutes: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
) {
    fun toDomain(): Trip {
        val resolvedStatus = when {
            status == "COMPLETED" -> TripStatus.COMPLETED
            DateUtils.isFutureDate(date) -> TripStatus.UPCOMING
            status == "ACTIVE" || status == "IN_PROGRESS" -> TripStatus.IN_PROGRESS
            else -> TripStatus.IN_PROGRESS
        }
        val resolvedStampEarned = resolvedStatus == TripStatus.COMPLETED && (stampEarned || completedAt != null)

        val validStartTime = startTimeMinutes?.takeIf { it in 0..1439 }
        val validReminderTime = reminderTimeMinutes?.takeIf { it in 0..1439 }

        return Trip(
            id = id,
            uuid = uuid,
            name = name,
            destination = destination,
            date = date,
            startTimeMinutes = validStartTime,
            peopleCount = peopleCount,
            description = description,
            status = resolvedStatus,
            stampEarned = resolvedStampEarned,
            completedAt = if (resolvedStatus == TripStatus.COMPLETED) completedAt else null,
            reminderEnabled = reminderEnabled,
            reminderPreset = TripReminderPreset.fromString(reminderPreset),
            reminderTimeMinutes = validReminderTime,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt
        )
    }

    companion object {
        fun fromDomain(trip: Trip): TripEntity {
            val validStartTime = trip.startTimeMinutes?.takeIf { it in 0..1439 }
            val validReminderTime = trip.reminderTimeMinutes?.takeIf { it in 0..1439 }
            return TripEntity(
                id = trip.id,
                uuid = trip.uuid,
                name = trip.name,
                destination = trip.destination,
                date = trip.date,
                startTimeMinutes = validStartTime,
                peopleCount = trip.peopleCount,
                description = trip.description,
                status = trip.status.name,
                stampEarned = trip.stampEarned,
                completedAt = trip.completedAt,
                reminderEnabled = trip.reminderEnabled,
                reminderPreset = trip.reminderPreset.name,
                reminderTimeMinutes = validReminderTime,
                createdAt = trip.createdAt,
                updatedAt = trip.updatedAt,
                deletedAt = trip.deletedAt
            )
        }
    }
}
