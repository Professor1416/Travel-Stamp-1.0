package com.example.data.repository

import com.example.data.local.dao.JourneyLocationDao
import com.example.data.local.entity.JourneyLocationEntity
import com.example.data.model.JourneyLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface JourneyLocationRepository {
    fun observeLocationsForTrip(tripId: Long): Flow<List<JourneyLocation>>
    suspend fun getLocationsForTripSync(tripId: Long): List<JourneyLocation>
    suspend fun getAllLocationsSync(): List<JourneyLocation>
    fun observeAllActiveLocations(): Flow<List<JourneyLocation>>
    suspend fun insertLocation(location: JourneyLocation): Long
    suspend fun updateLocation(location: JourneyLocation)
    suspend fun deleteLocationById(id: Long)
    suspend fun deleteLocationsForTrip(tripId: Long)
}

class JourneyLocationRepositoryImpl(
    private val journeyLocationDao: JourneyLocationDao
) : JourneyLocationRepository {

    override fun observeLocationsForTrip(tripId: Long): Flow<List<JourneyLocation>> {
        return journeyLocationDao.observeLocationsForTrip(tripId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getLocationsForTripSync(tripId: Long): List<JourneyLocation> {
        return journeyLocationDao.getLocationsForTripSync(tripId).map { it.toDomain() }
    }

    override suspend fun getAllLocationsSync(): List<JourneyLocation> {
        return journeyLocationDao.getAllLocationsSync().map { it.toDomain() }
    }

    override fun observeAllActiveLocations(): Flow<List<JourneyLocation>> {
        return journeyLocationDao.observeAllActiveLocations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertLocation(location: JourneyLocation): Long {
        return journeyLocationDao.insertLocation(JourneyLocationEntity.fromDomain(location))
    }

    override suspend fun updateLocation(location: JourneyLocation) {
        journeyLocationDao.updateLocation(JourneyLocationEntity.fromDomain(location))
    }

    override suspend fun deleteLocationById(id: Long) {
        journeyLocationDao.deleteLocationById(id)
    }

    override suspend fun deleteLocationsForTrip(tripId: Long) {
        journeyLocationDao.deleteLocationsForTrip(tripId)
    }
}
