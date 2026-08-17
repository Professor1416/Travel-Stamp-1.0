package com.example.data.model

data class Moment(
    val id: Long = 0,
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val tripId: Long,
    val category: MomentCategory = MomentCategory.NOTE,
    val note: String = "",
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
