package com.example.data.repository

import com.example.data.local.dao.TravelStampDao
import com.example.data.local.entity.TravelStampEntity
import com.example.data.model.TravelStamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TravelStampRepository {
    fun getAllStamps(): Flow<List<TravelStamp>>
    fun getStampForTrip(tripId: Long): Flow<TravelStamp?>
    suspend fun getStampForTripSync(tripId: Long): TravelStamp?
    fun getStampById(id: Long): Flow<TravelStamp?>
    suspend fun issueStamp(stamp: TravelStamp): Long
    suspend fun deleteStamp(id: Long)
    fun getStampsCount(): Flow<Int>
    suspend fun getStampsCountSync(): Int
}

class TravelStampRepositoryImpl(
    private val stampDao: TravelStampDao
) : TravelStampRepository {

    override fun getAllStamps(): Flow<List<TravelStamp>> =
        stampDao.getAllStamps().map { entities -> entities.map { it.toDomain() } }

    override fun getStampForTrip(tripId: Long): Flow<TravelStamp?> =
        stampDao.getStampForTrip(tripId).map { it?.toDomain() }

    override suspend fun getStampForTripSync(tripId: Long): TravelStamp? =
        stampDao.getStampForTripSync(tripId)?.toDomain()

    override fun getStampById(id: Long): Flow<TravelStamp?> =
        stampDao.getStampById(id).map { it?.toDomain() }

    override suspend fun issueStamp(stamp: TravelStamp): Long =
        stampDao.insertStamp(TravelStampEntity.fromDomain(stamp))

    override suspend fun deleteStamp(id: Long) =
        stampDao.deleteStampById(id)

    override fun getStampsCount(): Flow<Int> =
        stampDao.getStampsCount()

    override suspend fun getStampsCountSync(): Int =
        stampDao.getStampsCountSync()
}
