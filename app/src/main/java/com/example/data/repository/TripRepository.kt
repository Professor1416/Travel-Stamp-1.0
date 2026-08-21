package com.example.data.repository

import android.content.Context
import com.example.data.local.dao.MomentDao
import com.example.data.local.dao.TripDao
import com.example.data.local.entity.TripEntity
import com.example.data.model.Trip
import com.example.data.model.TripStatus
import com.example.data.util.DateUtils
import com.example.ui.util.PhotoUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TripRepository {
    fun getAllTrips(): Flow<List<Trip>>
    fun getActiveTrips(): Flow<List<Trip>>
    fun getCompletedTrips(): Flow<List<Trip>>
    fun observeRecentJourneys(): Flow<List<Trip>>
    fun observeUpcomingJourneys(): Flow<List<Trip>>
    fun getTripById(id: Long): Flow<Trip?>
    suspend fun getTripByIdSync(id: Long): Trip?
    suspend fun createTrip(trip: Trip): Long
    suspend fun updateTrip(trip: Trip)
    suspend fun finishTrip(
        tripId: Long,
        reflectionNote: String? = null,
        stampInkColorHex: String = "#8B1E0F",
        stampStyle: String = "CIRCULAR"
    ): Boolean
    suspend fun deleteTrip(id: Long)
    fun getCompletedTripsCount(): Flow<Int>
    fun getTotalTripsCount(): Flow<Int>
}

class TripRepositoryImpl(
    private val tripDao: TripDao,
    private val momentDao: MomentDao? = null,
    private val context: Context? = null
) : TripRepository {

    override fun getAllTrips(): Flow<List<Trip>> =
        tripDao.getAllTrips().map { entities ->
            entities.map { it.toDomain() }
                .filter { it.deletedAt == null }
                .sortedWith(
                    compareByDescending<Trip> { DateUtils.getEpochDay(it.date, it.createdAt) }
                        .thenByDescending { it.createdAt }
                        .thenBy { it.id }
                )
        }

    override fun getActiveTrips(): Flow<List<Trip>> =
        tripDao.getAllTrips().map { entities ->
            entities.map { it.toDomain() }
                .filter { it.status != TripStatus.COMPLETED && it.deletedAt == null }
                .sortedWith(
                    compareBy<Trip> { DateUtils.getEpochDay(it.date, it.createdAt) }
                        .thenBy { it.id }
                )
        }

    override fun getCompletedTrips(): Flow<List<Trip>> =
        tripDao.getAllTrips().map { entities ->
            entities.map { it.toDomain() }
                .filter { it.status == TripStatus.COMPLETED && !DateUtils.isFutureDate(it.date) && it.deletedAt == null }
                .sortedWith(
                    compareByDescending<Trip> { DateUtils.getEpochDay(it.date, it.createdAt) }
                        .thenByDescending { it.createdAt }
                        .thenBy { it.id }
                )
        }

    override fun observeRecentJourneys(): Flow<List<Trip>> = getCompletedTrips()

    override fun observeUpcomingJourneys(): Flow<List<Trip>> = getActiveTrips()

    override fun getTripById(id: Long): Flow<Trip?> =
        tripDao.getTripById(id).map { it?.toDomain() }

    override suspend fun getTripByIdSync(id: Long): Trip? =
        tripDao.getTripByIdSync(id)?.toDomain()

    override suspend fun createTrip(trip: Trip): Long {
        val initialStatus = if (DateUtils.isFutureDate(trip.date)) {
            TripStatus.UPCOMING
        } else {
            TripStatus.IN_PROGRESS
        }
        val entity = TripEntity.fromDomain(trip.copy(status = initialStatus, stampEarned = false, completedAt = null))
        return tripDao.insertTrip(entity)
    }

    override suspend fun updateTrip(trip: Trip) {
        val entity = TripEntity.fromDomain(trip)
        tripDao.updateTrip(entity)
    }

    override suspend fun finishTrip(
        tripId: Long,
        reflectionNote: String?,
        stampInkColorHex: String,
        stampStyle: String
    ): Boolean {
        val existing = tripDao.getTripByIdSync(tripId) ?: return false
        
        // Critical validation: Reject if trip date is strictly in the future
        if (DateUtils.isFutureDate(existing.date)) {
            return false
        }

        val updated = existing.copy(
            status = TripStatus.COMPLETED.name,
            stampEarned = true,
            completedAt = System.currentTimeMillis()
        )
        tripDao.updateTrip(updated)
        return true
    }

    override suspend fun deleteTrip(id: Long) {
        val moments = momentDao?.getMomentsForTripSync(id) ?: emptyList()
        val imageUris = moments.mapNotNull { it.imageUri }.filter { it.isNotBlank() }

        tripDao.deleteTripById(id)

        if (context != null && momentDao != null) {
            for (uri in imageUris) {
                val remainingUsage = momentDao.getImageUriUsageCount(uri)
                if (remainingUsage == 0) {
                    PhotoUtils.safeDeleteInternalImage(context, uri)
                }
            }
        }
    }

    override fun getCompletedTripsCount(): Flow<Int> =
        tripDao.getCompletedTripsCount()

    override fun getTotalTripsCount(): Flow<Int> =
        tripDao.getTotalTripsCount()
}
