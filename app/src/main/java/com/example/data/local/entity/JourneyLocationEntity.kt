package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.JourneyLocation
import java.util.UUID

@Entity(
    tableName = "journey_locations",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class JourneyLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val tripId: Long,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val sortOrder: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): JourneyLocation = JourneyLocation(
        id = id,
        uuid = uuid,
        tripId = tripId,
        label = label,
        latitude = latitude,
        longitude = longitude,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(location: JourneyLocation): JourneyLocationEntity = JourneyLocationEntity(
            id = location.id,
            uuid = location.uuid,
            tripId = location.tripId,
            label = location.label,
            latitude = location.latitude,
            longitude = location.longitude,
            sortOrder = location.sortOrder,
            createdAt = location.createdAt,
            updatedAt = location.updatedAt
        )
    }
}
