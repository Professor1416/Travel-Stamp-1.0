package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.TravelStampEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TravelStampDao {
    @Query("SELECT * FROM travel_stamps ORDER BY issuedAt DESC")
    fun getAllStamps(): Flow<List<TravelStampEntity>>

    @Query("SELECT * FROM travel_stamps WHERE tripId = :tripId LIMIT 1")
    fun getStampForTrip(tripId: Long): Flow<TravelStampEntity?>

    @Query("SELECT * FROM travel_stamps WHERE tripId = :tripId LIMIT 1")
    suspend fun getStampForTripSync(tripId: Long): TravelStampEntity?

    @Query("SELECT * FROM travel_stamps WHERE id = :id LIMIT 1")
    fun getStampById(id: Long): Flow<TravelStampEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStamp(stamp: TravelStampEntity): Long

    @Query("DELETE FROM travel_stamps WHERE id = :id")
    suspend fun deleteStampById(id: Long)

    @Query("SELECT COUNT(*) FROM travel_stamps")
    fun getStampsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM travel_stamps")
    suspend fun getStampsCountSync(): Int
}
