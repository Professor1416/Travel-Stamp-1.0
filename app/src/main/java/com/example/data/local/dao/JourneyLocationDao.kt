package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.JourneyLocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JourneyLocationDao {
    @Query("SELECT * FROM journey_locations WHERE tripId = :tripId ORDER BY sortOrder ASC, id ASC")
    fun observeLocationsForTrip(tripId: Long): Flow<List<JourneyLocationEntity>>

    @Query("SELECT * FROM journey_locations WHERE tripId = :tripId ORDER BY sortOrder ASC, id ASC")
    suspend fun getLocationsForTripSync(tripId: Long): List<JourneyLocationEntity>

    @Query("SELECT * FROM journey_locations ORDER BY id ASC")
    suspend fun getAllLocationsSync(): List<JourneyLocationEntity>

    @Query("""
        SELECT loc.* FROM journey_locations AS loc
        INNER JOIN trips AS t ON loc.tripId = t.id
        WHERE t.deletedAt IS NULL
        ORDER BY loc.tripId ASC, loc.sortOrder ASC, loc.id ASC
    """)
    fun observeAllActiveLocations(): Flow<List<JourneyLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: JourneyLocationEntity): Long

    @Update
    suspend fun updateLocation(location: JourneyLocationEntity)

    @Query("DELETE FROM journey_locations WHERE id = :id")
    suspend fun deleteLocationById(id: Long)

    @Query("DELETE FROM journey_locations WHERE tripId = :tripId")
    suspend fun deleteLocationsForTrip(tripId: Long)
}
