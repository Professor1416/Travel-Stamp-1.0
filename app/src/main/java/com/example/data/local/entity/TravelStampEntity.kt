package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.TravelStamp

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
    indices = [Index(value = ["tripId"])]
)
data class TravelStampEntity(
    @PrimaryKey(autoGenerate = true)
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
) {
    fun toDomain(): TravelStamp = TravelStamp(
        id = id,
        tripId = tripId,
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
        reflectionNote = reflectionNote
    )

    companion object {
        fun fromDomain(stamp: TravelStamp): TravelStampEntity = TravelStampEntity(
            id = stamp.id,
            tripId = stamp.tripId,
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
            reflectionNote = stamp.reflectionNote
        )
    }
}
