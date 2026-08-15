package com.example.data.repository

import com.example.data.local.dao.MomentDao
import com.example.data.local.entity.MomentEntity
import com.example.data.model.Moment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MomentRepository {
    fun getMomentsForTrip(tripId: Long): Flow<List<Moment>>
    suspend fun getMomentsForTripSync(tripId: Long): List<Moment>
    suspend fun addMoment(moment: Moment): Long
    suspend fun deleteMoment(id: Long)
    fun getMomentsCountForTrip(tripId: Long): Flow<Int>
    fun getTotalMomentsCount(): Flow<Int>
}

class MomentRepositoryImpl(
    private val momentDao: MomentDao
) : MomentRepository {

    override fun getMomentsForTrip(tripId: Long): Flow<List<Moment>> =
        momentDao.getMomentsForTrip(tripId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getMomentsForTripSync(tripId: Long): List<Moment> =
        momentDao.getMomentsForTripSync(tripId).map { it.toDomain() }

    override suspend fun addMoment(moment: Moment): Long =
        momentDao.insertMoment(MomentEntity.fromDomain(moment))

    override suspend fun deleteMoment(id: Long) =
        momentDao.deleteMomentById(id)

    override fun getMomentsCountForTrip(tripId: Long): Flow<Int> =
        momentDao.getMomentsCountForTrip(tripId)

    override fun getTotalMomentsCount(): Flow<Int> =
        momentDao.getTotalMomentsCount()
}
