package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.ChecklistItem

@Entity(
    tableName = "checklist_items",
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
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val text: String,
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0
) {
    fun toDomain(): ChecklistItem = ChecklistItem(
        id = id,
        tripId = tripId,
        text = text,
        isCompleted = isCompleted,
        sortOrder = sortOrder
    )

    companion object {
        fun fromDomain(item: ChecklistItem): ChecklistItemEntity = ChecklistItemEntity(
            id = item.id,
            tripId = item.tripId,
            text = item.text,
            isCompleted = item.isCompleted,
            sortOrder = item.sortOrder
        )
    }
}
