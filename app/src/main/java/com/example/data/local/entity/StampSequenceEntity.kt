package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Dedicated persistent sequence table for Travel Stamp numbers.
 * Guarantees that official stamp numbers are monotonic, permanent, and NEVER decremented or reused
 * even if trips or stamps are deleted from the database.
 */
@Entity(tableName = "stamp_sequence")
data class StampSequenceEntity(
    @PrimaryKey
    val id: String = "STAMP_COUNTER",
    val lastAllocatedNumber: Long = 0L
)
