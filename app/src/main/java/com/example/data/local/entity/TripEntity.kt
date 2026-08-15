package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Trip
import com.example.data.model.TripStatus

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val destination: String,
    val date: String,
    val peopleCount: Int = 1,
    val description: String = "",
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val stampInkColorHex: String = "#1E3A2F",
    val stampStyle: String = "MOUNTAIN",
    val reflectionNote: String? = null
) {
    fun toDomain(): Trip = Trip(
        id = id,
        name = name,
        destination = destination,
        date = date,
        peopleCount = peopleCount,
        description = description,
        status = if (status == "COMPLETED") TripStatus.COMPLETED else TripStatus.ACTIVE,
        createdAt = createdAt,
        completedAt = completedAt,
        stampInkColorHex = stampInkColorHex,
        stampStyle = stampStyle,
        reflectionNote = reflectionNote
    )

    companion object {
        fun fromDomain(trip: Trip): TripEntity = TripEntity(
            id = trip.id,
            name = trip.name,
            destination = trip.destination,
            date = trip.date,
            peopleCount = trip.peopleCount,
            description = trip.description,
            status = trip.status.name,
            createdAt = trip.createdAt,
            completedAt = trip.completedAt,
            stampInkColorHex = trip.stampInkColorHex,
            stampStyle = trip.stampStyle,
            reflectionNote = trip.reflectionNote
        )
    }
}
