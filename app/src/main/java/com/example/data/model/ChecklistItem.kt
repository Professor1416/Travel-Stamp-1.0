package com.example.data.model

data class ChecklistItem(
    val id: Long = 0,
    val tripId: Long,
    val text: String,
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0
)
