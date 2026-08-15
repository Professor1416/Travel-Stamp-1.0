package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.TravelStamp
import java.util.UUID

@Entity(
    tableName = "travel_stamps",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripId"], unique = true),
        Index(value = ["stampNumber"], unique = true),
        Index(value = ["uuid"], unique = true)
    ]
)
data class TravelStampEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val tripId: Long,
    val stampNumber: Long = 1L,
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
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val deletedAt: Long? = null,
    val reflectionNote: String? = null
) {
    fun toDomain(): TravelStamp = TravelStamp(
        id = id,
        uuid = uuid,
        tripId = tripId,
        stampNumber = stampNumber,
        stampCode = stampCode,
        title = title,
        destination = destination,
        dateText = dateText,
        peopleCount = peopleCount,
        momentsCount = momentsCount,
        inkColorHex = inkColorHex,
        stampStyle = stampStyle,
        inspectionText = inspectionText,
        issuedAt = issuedAt,
        createdAt = createdAt,
        completedAt = completedAt,
        deletedAt = deletedAt,
        reflectionNote = reflectionNote
    )

    companion object {
        fun fromDomain(stamp: TravelStamp): TravelStampEntity = TravelStampEntity(
            id = stamp.id,
            uuid = stamp.uuid,
            tripId = stamp.tripId,
            stampNumber = stamp.stampNumber,
            stampCode = stamp.stampCode,
            title = stamp.title,
            destination = stamp.destination,
            dateText = stamp.dateText,
            peopleCount = stamp.peopleCount,
            momentsCount = stamp.momentsCount,
            inkColorHex = stamp.inkColorHex,
            stampStyle = stamp.stampStyle,
            inspectionText = stamp.inspectionText,
            issuedAt = stamp.issuedAt,
            createdAt = stamp.createdAt,
            completedAt = stamp.completedAt,
            deletedAt = stamp.deletedAt,
            reflectionNote = stamp.reflectionNote
        )
    }
}
