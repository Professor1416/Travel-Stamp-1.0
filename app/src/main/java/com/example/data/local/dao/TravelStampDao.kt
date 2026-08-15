package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entity.StampSequenceEntity
import com.example.data.local.entity.TravelStampEntity
import kotlinx.coroutines.flow.Flow
import java.util.Locale

@Dao
interface TravelStampDao {
    @Query("SELECT * FROM travel_stamps ORDER BY stampNumber DESC, issuedAt DESC")
    fun getAllStamps(): Flow<List<TravelStampEntity>>

    @Query("SELECT * FROM travel_stamps ORDER BY stampNumber ASC")
    suspend fun getAllStampsListSync(): List<TravelStampEntity>

    @Query("SELECT * FROM travel_stamps WHERE tripId = :tripId LIMIT 1")
    fun getStampForTrip(tripId: Long): Flow<TravelStampEntity?>

    @Query("SELECT * FROM travel_stamps WHERE tripId = :tripId LIMIT 1")
    suspend fun getStampForTripSync(tripId: Long): TravelStampEntity?

    @Query("SELECT * FROM travel_stamps WHERE id = :id LIMIT 1")
    fun getStampById(id: Long): Flow<TravelStampEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStamp(stamp: TravelStampEntity): Long

    @Query("DELETE FROM travel_stamps WHERE id = :id")
    suspend fun deleteStampById(id: Long)

    @Query("SELECT COUNT(*) FROM travel_stamps")
    fun getStampsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM travel_stamps")
    suspend fun getStampsCountSync(): Int

    @Query("SELECT MAX(stampNumber) FROM travel_stamps")
    suspend fun getMaxStampNumber(): Long?

    @Query("SELECT lastAllocatedNumber FROM stamp_sequence WHERE id = 'STAMP_COUNTER'")
    suspend fun getLastAllocatedSequence(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setLastAllocatedSequence(sequence: StampSequenceEntity)

    /**
     * Atomically allocates the next sequential permanent stamp number.
     * Guaranteed to be monotonic and NEVER decrements or reuses numbers even if previous stamps are deleted.
     */
    @Transaction
    suspend fun allocateNextStampNumber(): Long {
        val lastSeq = getLastAllocatedSequence() ?: 0L
        val maxFromStamps = getMaxStampNumber() ?: 0L
        val currentMax = maxOf(lastSeq, maxFromStamps)
        val nextNumber = currentMax + 1L
        setLastAllocatedSequence(StampSequenceEntity(id = "STAMP_COUNTER", lastAllocatedNumber = nextNumber))
        return nextNumber
    }

    /**
     * Atomically issues the official stamp for a completed trip.
     * If a stamp already exists for this trip, it returns the existing stamp without allocating a new number.
     * Prevents race conditions, duplicates, and maintains 1:1 Trip-to-Stamp relationship.
     */
    @Transaction
    suspend fun issueOfficialStamp(
        tripId: Long,
        stampBuilder: (nextNumber: Long, formattedCode: String) -> TravelStampEntity
    ): TravelStampEntity {
        val existing = getStampForTripSync(tripId)
        if (existing != null) {
            return existing
        }

        val nextNumber = allocateNextStampNumber()
        val formattedCode = "#" + String.format(Locale.getDefault(), "%03d", nextNumber)
        val entity = stampBuilder(nextNumber, formattedCode)
        insertStamp(entity)
        return getStampForTripSync(tripId) ?: entity
    }
}
