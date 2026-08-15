package com.example.data.repository

import com.example.data.local.dao.TravelStampDao
import com.example.data.local.entity.TravelStampEntity
import com.example.data.model.TravelStamp
import com.example.data.util.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TravelStampRepository {
    fun getAllStamps(): Flow<List<TravelStamp>>
    fun getStampForTrip(tripId: Long): Flow<TravelStamp?>
    suspend fun getStampForTripSync(tripId: Long): TravelStamp?
    fun getStampById(id: Long): Flow<TravelStamp?>
    suspend fun issueStamp(stamp: TravelStamp): Long
    suspend fun issueOfficialStampForTrip(
        tripId: Long,
        title: String,
        destination: String,
        dateText: String,
        peopleCount: Int,
        momentsCount: Int,
        inkColorHex: String,
        stampStyle: String,
        reflectionNote: String?,
        completedAt: Long
    ): TravelStamp?
    suspend fun deleteStamp(id: Long)
    fun getStampsCount(): Flow<Int>
    suspend fun getStampsCountSync(): Int
    suspend fun allocateNextStampNumber(): Long
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

    override suspend fun issueStamp(stamp: TravelStamp): Long {
        if (DateUtils.isFutureDate(stamp.dateText)) {
            return -1L
        }
        return stampDao.insertStamp(TravelStampEntity.fromDomain(stamp))
    }

    override suspend fun issueOfficialStampForTrip(
        tripId: Long,
        title: String,
        destination: String,
        dateText: String,
        peopleCount: Int,
        momentsCount: Int,
        inkColorHex: String,
        stampStyle: String,
        reflectionNote: String?,
        completedAt: Long
    ): TravelStamp? {
        // Business logic validation: Never issue a stamp for a future trip
        if (DateUtils.isFutureDate(dateText)) {
            return null
        }

        val entity = stampDao.issueOfficialStamp(tripId) { nextNumber, formattedCode ->
            TravelStampEntity(
                tripId = tripId,
                stampNumber = nextNumber,
                stampCode = formattedCode,
                title = title,
                destination = destination,
                dateText = dateText,
                peopleCount = peopleCount,
                momentsCount = momentsCount,
                inkColorHex = inkColorHex,
                stampStyle = stampStyle,
                reflectionNote = reflectionNote,
                issuedAt = completedAt,
                completedAt = completedAt
            )
        }
        return entity.toDomain()
    }

    override suspend fun deleteStamp(id: Long) =
        stampDao.deleteStampById(id)

    override fun getStampsCount(): Flow<Int> =
        stampDao.getStampsCount()

    override suspend fun getStampsCountSync(): Int =
        stampDao.getStampsCountSync()

    override suspend fun allocateNextStampNumber(): Long =
        stampDao.allocateNextStampNumber()
}
