package com.example.data.repository

import com.example.data.local.dao.TripDao
import com.example.data.local.entity.TripEntity
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TripRepository {
    fun getAllTrips(): Flow<List<Trip>>
    fun getActiveTrips(): Flow<List<Trip>>
    fun getCompletedTrips(): Flow<List<Trip>>
    fun getTripById(id: Long): Flow<Trip?>
    suspend fun getTripByIdSync(id: Long): Trip?
    suspend fun createTrip(trip: Trip): Long
    suspend fun updateTrip(trip: Trip)
    suspend fun finishTrip(
        tripId: Long,
        reflectionNote: String?,
        stampInkColorHex: String,
        stampStyle: String
    )
    suspend fun deleteTrip(id: Long)
    fun getCompletedTripsCount(): Flow<Int>
    fun getTotalTripsCount(): Flow<Int>
}

class TripRepositoryImpl(
    private val tripDao: TripDao
) : TripRepository {

    override fun getAllTrips(): Flow<List<Trip>> =
        tripDao.getAllTrips().map { entities -> entities.map { it.toDomain() } }

    override fun getActiveTrips(): Flow<List<Trip>> =
        tripDao.getActiveTrips().map { entities -> entities.map { it.toDomain() } }

    override fun getCompletedTrips(): Flow<List<Trip>> =
        tripDao.getCompletedTrips().map { entities -> entities.map { it.toDomain() } }

    override fun getTripById(id: Long): Flow<Trip?> =
        tripDao.getTripById(id).map { it?.toDomain() }

    override suspend fun getTripByIdSync(id: Long): Trip? =
        tripDao.getTripByIdSync(id)?.toDomain()

    override suspend fun createTrip(trip: Trip): Long =
        tripDao.insertTrip(TripEntity.fromDomain(trip))

    override suspend fun updateTrip(trip: Trip) =
        tripDao.updateTrip(TripEntity.fromDomain(trip))

    override suspend fun finishTrip(
        tripId: Long,
        reflectionNote: String?,
        stampInkColorHex: String,
        stampStyle: String
    ) {
        val existing = tripDao.getTripByIdSync(tripId) ?: return
        val updated = existing.copy(
            status = TripStatus.COMPLETED.name,
            completedAt = System.currentTimeMillis(),
            reflectionNote = reflectionNote,
            stampInkColorHex = stampInkColorHex,
            stampStyle = stampStyle
        )
        tripDao.updateTrip(updated)
    }

    override suspend fun deleteTrip(id: Long) =
        tripDao.deleteTripById(id)

    override fun getCompletedTripsCount(): Flow<Int> =
        tripDao.getCompletedTripsCount()

    override fun getTotalTripsCount(): Flow<Int> =
        tripDao.getTotalTripsCount()
}
