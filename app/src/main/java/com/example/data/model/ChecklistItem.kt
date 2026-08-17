package com.example.data.model

data class ChecklistItem(
    val id: Long = 0,
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val tripId: Long,
    val text: String,
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)
