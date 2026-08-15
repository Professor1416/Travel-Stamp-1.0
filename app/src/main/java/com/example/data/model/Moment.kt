package com.example.data.model

data class Moment(
    val id: Long = 0,
    val tripId: Long,
    val category: MomentCategory = MomentCategory.NOTE,
    val note: String = "",
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
