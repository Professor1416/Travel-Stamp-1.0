package com.example.data.model

import java.util.UUID

data class TravelStamp(
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
)
