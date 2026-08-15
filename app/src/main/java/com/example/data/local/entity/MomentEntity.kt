package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.Moment
import com.example.data.model.MomentCategory

@Entity(
    tableName = "moments",
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
data class MomentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val category: String = "NOTE",
    val note: String = "",
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): Moment = Moment(
        id = id,
        tripId = tripId,
        category = MomentCategory.fromName(category),
        note = note,
        imageUri = imageUri,
        timestamp = timestamp
    )

    companion object {
        fun fromDomain(moment: Moment): MomentEntity = MomentEntity(
            id = moment.id,
            tripId = moment.tripId,
            category = moment.category.name,
            note = moment.note,
            imageUri = moment.imageUri,
            timestamp = moment.timestamp
        )
    }
}
