package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.MomentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MomentDao {
    @Query("SELECT * FROM moments WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun getMomentsForTrip(tripId: Long): Flow<List<MomentEntity>>

    @Query("SELECT * FROM moments WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getMomentsForTripSync(tripId: Long): List<MomentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoment(moment: MomentEntity): Long

    @Query("DELETE FROM moments WHERE id = :id")
    suspend fun deleteMomentById(id: Long)

    @Query("SELECT COUNT(*) FROM moments WHERE tripId = :tripId")
    fun getMomentsCountForTrip(tripId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM moments")
    fun getTotalMomentsCount(): Flow<Int>
}
