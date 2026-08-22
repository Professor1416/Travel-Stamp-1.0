package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.HyperlinkUtils
import com.example.data.model.Moment
import com.example.data.model.MomentCategory
import java.util.UUID

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
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class MomentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val tripId: Long,
    val category: String = "NOTE",
    val note: String = "",
    val hyperlinksJson: String? = null,
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
) {
    fun toDomain(): Moment = Moment(
        id = id,
        uuid = uuid,
        tripId = tripId,
        category = MomentCategory.fromName(category),
        note = note,
        hyperlinks = HyperlinkUtils.parseFromJson(hyperlinksJson),
        imageUri = imageUri,
        timestamp = timestamp,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    companion object {
        fun fromDomain(moment: Moment): MomentEntity = MomentEntity(
            id = moment.id,
            uuid = moment.uuid,
            tripId = moment.tripId,
            category = moment.category.name,
            note = moment.note,
            hyperlinksJson = if (moment.hyperlinks.isNotEmpty()) HyperlinkUtils.serializeToJson(moment.hyperlinks) else null,
            imageUri = moment.imageUri,
            timestamp = moment.timestamp,
            createdAt = moment.createdAt,
            updatedAt = moment.updatedAt,
            deletedAt = moment.deletedAt
        )
    }
}
